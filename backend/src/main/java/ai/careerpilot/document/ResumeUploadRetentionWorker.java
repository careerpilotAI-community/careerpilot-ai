package ai.careerpilot.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Purges rejected, failed, and abandoned upload objects after their approved
 * 24-hour retention window. Successful uploads are excluded by their completed ledger state.
 */
@Component
public class ResumeUploadRetentionWorker {
    private static final int MAX_ATTEMPTS = 8;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final DocumentStorage storage;
    private final boolean enabled;
    private final boolean schedulingEnabled;

    public ResumeUploadRetentionWorker(JdbcTemplate jdbc, TransactionTemplate transactions,
            DocumentStorage storage,
            @Value("${app.documents.retention.enabled:true}") boolean enabled,
            @Value("${app.documents.retention.scheduling-enabled:true}") boolean schedulingEnabled) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.storage = storage;
        this.enabled = enabled;
        this.schedulingEnabled = schedulingEnabled;
    }

    @Scheduled(fixedDelayString = "${app.documents.retention.fixed-delay-ms:60000}")
    public void scheduledCleanup() {
        if (!enabled || !schedulingEnabled) return;
        for (int i = 0; i < 10 && processNextJob(); i++) {
            // Bounded batch; the durable queue resumes on the next schedule tick.
        }
    }

    /** Processes at most one due cleanup record; public for deterministic integration tests. */
    public boolean processNextJob() {
        if (!enabled) return false;
        CleanupJob job = transactions.execute(status -> claimNext());
        if (job == null) return false;
        try {
            storage.remove(job.storageKey());
            if (storage.exists(job.storageKey())) {
                throw new IllegalStateException("Upload object remains after cleanup.");
            }
            transactions.executeWithoutResult(status -> complete(job));
        } catch (Exception failure) {
            recordFailure(job);
        }
        return true;
    }

    private CleanupJob claimNext() {
        var jobs = jdbc.query("""
            SELECT operation_id, storage_key
            FROM resume_upload_cleanup_jobs
            WHERE (
                (job_status='PENDING' AND expires_at <= CURRENT_TIMESTAMP AND next_attempt_at <= CURRENT_TIMESTAMP)
                OR
                (job_status='PROCESSING'
                    AND processing_started_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes')
            )
            ORDER BY expires_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, (rs, row) -> new CleanupJob(
                rs.getObject("operation_id", UUID.class),
                rs.getString("storage_key")));
        if (jobs.isEmpty()) return null;
        CleanupJob job = jobs.get(0);
        jdbc.update("""
            UPDATE resume_upload_cleanup_jobs
            SET job_status='PROCESSING',
                attempt_count=attempt_count+1,
                processing_started_at=CURRENT_TIMESTAMP
            WHERE operation_id=?
            """, job.operationId());
        return job;
    }

    private void complete(CleanupJob job) {
        try {
            if (storage.exists(job.storageKey())) {
                throw new IllegalStateException("Upload storage absence could not be verified.");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Upload storage verification failed.", ex);
        }
        jdbc.update("""
            UPDATE resume_upload_cleanup_jobs
            SET job_status='COMPLETED',
                completed_at=CURRENT_TIMESTAMP,
                processing_started_at=NULL,
                last_error_code=NULL
            WHERE operation_id=? AND job_status='PROCESSING'
            """, job.operationId());
    }

    private void recordFailure(CleanupJob job) {
        transactions.executeWithoutResult(status -> {
            Integer attempts = jdbc.queryForObject(
                "SELECT attempt_count FROM resume_upload_cleanup_jobs WHERE operation_id=?",
                Integer.class, job.operationId());
            int count = attempts == null ? MAX_ATTEMPTS : attempts;
            boolean exhausted = count >= MAX_ATTEMPTS;
            long delaySeconds = Math.min(21600L, 30L * (1L << Math.min(Math.max(count - 1, 0), 10)));
            jdbc.update("""
                UPDATE resume_upload_cleanup_jobs
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

    private record CleanupJob(UUID operationId, String storageKey) {}
}
