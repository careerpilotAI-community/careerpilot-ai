package ai.careerpilot.document;

import ai.careerpilot.security.AccountDeletionJournal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable, retryable worker for owner-requested resume deletion.
 * Storage operations are idempotent; metadata is scrubbed only after absence is verified.
 */
@Component
public class ResumeDeletionWorker {
    private static final int MAX_ATTEMPTS = 8;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final DocumentStorage storage;
    private final AccountDeletionJournal deletionJournal;
    private final boolean enabled;
    private final boolean schedulingEnabled;

    public ResumeDeletionWorker(JdbcTemplate jdbc, TransactionTemplate transactions,
            DocumentStorage storage, AccountDeletionJournal deletionJournal,
            @Value("${app.documents.cleanup.enabled:true}") boolean enabled,
            @Value("${app.documents.cleanup.scheduling-enabled:true}") boolean schedulingEnabled) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.storage = storage;
        this.deletionJournal = deletionJournal;
        this.enabled = enabled;
        this.schedulingEnabled = schedulingEnabled;
    }

    @Scheduled(fixedDelayString = "${app.documents.cleanup.fixed-delay-ms:15000}")
    public void scheduledCleanup() {
        if (!enabled || !schedulingEnabled) return;
        for (int i = 0; i < 10 && processNextJob(); i++) {
            // Bounded batch: yield to application requests and other scheduled work.
        }
    }

    /** Processes at most one eligible job; public for deterministic integration testing. */
    public boolean processNextJob() {
        if (!enabled) return false;
        DeletionJob job = transactions.execute(status -> claimNext());
        if (job == null) return false;
        try {
            // Record the owner-scoped tombstone outside PostgreSQL before touching storage.
            deletionJournal.recordAssetDeletion(job.operationId(), job.ownerId(), job.assetId());
            storage.remove(job.storageKey());
            if (storage.exists(job.storageKey())) {
                throw new IllegalStateException("Storage object remains after delete.");
            }
            transactions.executeWithoutResult(status -> finalizeDeletion(job));
        } catch (Exception failure) {
            recordFailure(job, failure);
        }
        return true;
    }

    private DeletionJob claimNext() {
        var jobs = jdbc.query("""
            SELECT j.operation_id, j.asset_id, j.owner_id, a.storage_key
            FROM resume_deletion_jobs j
            JOIN resume_assets a ON a.id = j.asset_id AND a.user_id = j.owner_id
            WHERE (
                (j.job_status = 'PENDING' AND j.next_attempt_at <= CURRENT_TIMESTAMP)
                OR
                (j.job_status = 'PROCESSING'
                    AND j.processing_started_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
            )
              AND a.status = 'DELETING'
              AND a.storage_key IS NOT NULL
            ORDER BY j.requested_at
            LIMIT 1
            FOR UPDATE OF j SKIP LOCKED
            """, (rs, row) -> new DeletionJob(
                rs.getObject("operation_id", UUID.class),
                rs.getObject("asset_id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getString("storage_key")));
        if (jobs.isEmpty()) return null;
        DeletionJob job = jobs.get(0);
        jdbc.update("""
            UPDATE resume_deletion_jobs
            SET job_status='PROCESSING',
                attempt_count=attempt_count+1,
                processing_started_at=CURRENT_TIMESTAMP
            WHERE operation_id=?
            """, job.operationId());
        return job;
    }

    private void finalizeDeletion(DeletionJob job) {
        try {
            if (storage.exists(job.storageKey())) {
                throw new IllegalStateException("Storage object presence could not be cleared.");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Storage absence verification failed.", ex);
        }
        int updated = jdbc.update("""
            UPDATE resume_assets
            SET status='DELETED',
                original_filename=NULL,
                media_type=NULL,
                checksum_sha256=NULL,
                storage_key=NULL,
                scanner_result=NULL,
                size_bytes=0,
                deletion_requested_at=NULL,
                deletion_reason=NULL
            WHERE id=? AND status='DELETING' AND storage_key=?
            """, job.assetId(), job.storageKey());
        if (updated != 1) throw new IllegalStateException("Deletion asset state changed unexpectedly.");
        jdbc.update("""
            UPDATE resume_deletion_jobs
            SET job_status='COMPLETED',
                completed_at=CURRENT_TIMESTAMP,
                processing_started_at=NULL,
                last_error_code=NULL
            WHERE operation_id=? AND job_status='PROCESSING'
            """, job.operationId());
    }

    private void recordFailure(DeletionJob job, Exception failure) {
        transactions.executeWithoutResult(status -> {
            Integer attempts = jdbc.queryForObject(
                "SELECT attempt_count FROM resume_deletion_jobs WHERE operation_id=?",
                Integer.class, job.operationId());
            int count = attempts == null ? MAX_ATTEMPTS : attempts;
            boolean exhausted = count >= MAX_ATTEMPTS;
            long delaySeconds = Math.min(21600L, 30L * (1L << Math.min(Math.max(count - 1, 0), 10)));
            jdbc.update("""
                UPDATE resume_deletion_jobs
                SET job_status=?,
                    next_attempt_at=?,
                    processing_started_at=NULL,
                    last_error_code='STORAGE_DELETE_FAILED'
                WHERE operation_id=? AND job_status='PROCESSING'
                """,
                exhausted ? "DEAD_LETTER" : "PENDING",
                Timestamp.from(Instant.now().plusSeconds(delaySeconds)),
                job.operationId());
        });
    }

    private record DeletionJob(UUID operationId, UUID assetId, UUID ownerId, String storageKey) {}
}
