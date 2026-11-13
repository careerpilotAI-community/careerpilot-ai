package ai.careerpilot.security;

import ai.careerpilot.document.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Removes every private document before deleting the account's relational data.
 * The durable job remains retryable until storage absence has been verified.
 */
@Component
public class AccountDeletionWorker {
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionWorker.class);
    private static final int MAX_ATTEMPTS = 8;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final DocumentStorage storage;
    private final AccountDeletionJournal deletionJournal;
    private final boolean enabled;
    private final boolean schedulingEnabled;

    public AccountDeletionWorker(JdbcTemplate jdbc, TransactionTemplate transactions,
            DocumentStorage storage, AccountDeletionJournal deletionJournal,
            @Value("${app.account-deletion.enabled:true}") boolean enabled,
            @Value("${app.account-deletion.scheduling-enabled:true}") boolean schedulingEnabled) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.storage = storage;
        this.deletionJournal = deletionJournal;
        this.enabled = enabled;
        this.schedulingEnabled = schedulingEnabled;
    }

    @Scheduled(fixedDelayString = "${app.account-deletion.fixed-delay-ms:30000}")
    public void scheduledCleanup() {
        if (!enabled || !schedulingEnabled) return;
        try {
            deletionJournal.pruneExpired();
        } catch (IOException failure) {
            log.error("Account deletion journal retention failed; journal entries were not pruned.", failure);
        }
        jdbc.update("DELETE FROM account_deletion_audit_events WHERE expires_at <= CURRENT_TIMESTAMP");
        for (int i = 0; i < 5 && processNextJob(); i++) {
            // Keep each scheduler tick bounded.
        }
    }

    public boolean processNextJob() {
        if (!enabled) return false;
        DeletionJob job = transactions.execute(status -> claimNext());
        if (job == null) return false;
        try {
            // Also cover jobs created before the sidecar journal was introduced.
            deletionJournal.record(job.operationId(), job.ownerId());
            List<String> keys = jdbc.query("""
                SELECT storage_key FROM resume_assets
                WHERE user_id=? AND storage_key IS NOT NULL
                UNION
                SELECT storage_key FROM resume_upload_cleanup_jobs
                WHERE owner_id=? AND storage_key IS NOT NULL
                """, (rs, row) -> rs.getString(1), job.ownerId(), job.ownerId());
            for (String key : keys) {
                storage.remove(key);
                if (storage.exists(key)) {
                    throw new IllegalStateException("Account document remains after deletion.");
                }
            }
            transactions.executeWithoutResult(status -> finalizeDeletion(job));
        } catch (Exception failure) {
            recordFailure(job);
        }
        return true;
    }

    private DeletionJob claimNext() {
        var jobs = jdbc.query("""
            SELECT operation_id, owner_id
            FROM account_deletion_jobs
            WHERE (job_status='PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
               OR (job_status='PROCESSING'
                   AND processing_started_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
            ORDER BY requested_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, (rs, row) -> new DeletionJob(
                rs.getObject("operation_id", UUID.class),
                rs.getObject("owner_id", UUID.class)));
        if (jobs.isEmpty()) return null;
        DeletionJob job = jobs.get(0);
        jdbc.update("""
            UPDATE account_deletion_jobs
            SET job_status='PROCESSING',
                attempt_count=attempt_count+1,
                processing_started_at=CURRENT_TIMESTAMP
            WHERE operation_id=?
            """, job.operationId());
        return job;
    }

    private void finalizeDeletion(DeletionJob job) {
        String email = jdbc.queryForObject(
            "SELECT email FROM users WHERE id=? AND account_status='DELETION_PENDING'",
            String.class, job.ownerId());
        // Revoke any session created in a race before removing account-owned data.
        jdbc.update("DELETE FROM spring_session WHERE principal_name=?", email);

        jdbc.update("DELETE FROM resume_deletion_jobs WHERE owner_id=?", job.ownerId());
        jdbc.update("DELETE FROM resume_assets WHERE user_id=?", job.ownerId());
        jdbc.update("DELETE FROM resume_upload_cleanup_jobs WHERE owner_id=?", job.ownerId());
        jdbc.update("DELETE FROM applications WHERE user_id=?", job.ownerId());
        jdbc.update("DELETE FROM jobs WHERE user_id=?", job.ownerId());
        jdbc.update("DELETE FROM profile_skills WHERE user_id=?", job.ownerId());
        jdbc.update("DELETE FROM career_profiles WHERE user_id=?", job.ownerId());
        int deleted = jdbc.update(
            "DELETE FROM users WHERE id=? AND account_status='DELETION_PENDING'", job.ownerId());
        if (deleted != 1) throw new IllegalStateException("Account deletion state changed unexpectedly.");

        jdbc.update("""
            UPDATE account_deletion_jobs
            SET job_status='COMPLETED', owner_id=NULL, completed_at=CURRENT_TIMESTAMP,
                processing_started_at=NULL, last_error_code=NULL
            WHERE operation_id=? AND job_status='PROCESSING'
            """, job.operationId());
        jdbc.update("""
            INSERT INTO account_deletion_audit_events
                (event_id, event_type, outcome_code, occurred_at, expires_at)
            VALUES (?, 'ACCOUNT_DELETION_COMPLETED', 'COMPLETED',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '12 months')
            """, UUID.randomUUID());
    }

    private void recordFailure(DeletionJob job) {
        transactions.executeWithoutResult(status -> {
            Integer attempts = jdbc.queryForObject(
                "SELECT attempt_count FROM account_deletion_jobs WHERE operation_id=?",
                Integer.class, job.operationId());
            int count = attempts == null ? MAX_ATTEMPTS : attempts;
            boolean exhausted = count >= MAX_ATTEMPTS;
            long delaySeconds = Math.min(21600L, 30L * (1L << Math.min(Math.max(count - 1, 0), 10)));
            jdbc.update("""
                UPDATE account_deletion_jobs
                SET job_status=?, next_attempt_at=?, processing_started_at=NULL,
                    last_error_code='ACCOUNT_DATA_DELETE_FAILED'
                WHERE operation_id=? AND job_status='PROCESSING'
                """, exhausted ? "DEAD_LETTER" : "PENDING",
                Timestamp.from(Instant.now().plusSeconds(delaySeconds)), job.operationId());
        });
    }

    private record DeletionJob(UUID operationId, UUID ownerId) {}
}
