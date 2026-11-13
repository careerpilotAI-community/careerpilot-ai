-- Durable tracking starts before any upload bytes are written to private storage.
-- Successful uploads complete this record in the same transaction as the asset row.
CREATE TABLE resume_upload_cleanup_jobs (
    operation_id UUID PRIMARY KEY,
    storage_key VARCHAR(80) UNIQUE,
    job_status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
    reason_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMPTZ,
    last_error_code VARCHAR(40),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_resume_upload_cleanup_status
        CHECK (job_status IN ('IN_PROGRESS', 'PENDING', 'PROCESSING', 'COMPLETED', 'DEAD_LETTER')),
    CONSTRAINT ck_resume_upload_cleanup_reason
        CHECK (reason_code IN ('UPLOAD_IN_PROGRESS', 'UPLOAD_REJECTED', 'UPLOAD_FAILED', 'UPLOAD_ACCEPTED')),
    CONSTRAINT ck_resume_upload_cleanup_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_resume_upload_cleanup_key_state CHECK (
        (job_status = 'COMPLETED' AND storage_key IS NULL)
        OR (job_status <> 'COMPLETED' AND storage_key IS NOT NULL)
    )
);

CREATE INDEX ix_resume_upload_cleanup_due
    ON resume_upload_cleanup_jobs (expires_at, next_attempt_at, created_at)
    WHERE job_status IN ('IN_PROGRESS', 'PENDING', 'PROCESSING');
