-- Durable account deletion orchestration. owner_id is intentionally not a foreign key:
-- the account row must be removable while this queue record remains retryable.
ALTER TABLE resume_upload_cleanup_jobs
    ADD COLUMN owner_id UUID REFERENCES users (id);

-- Legacy attempts cannot be attributed because earlier schema versions did not store ownership.
-- They are safe to purge immediately: accepted uploads have already nulled their storage key.
UPDATE resume_upload_cleanup_jobs
SET job_status = CASE WHEN job_status = 'IN_PROGRESS' THEN 'PENDING' ELSE job_status END,
    reason_code = CASE WHEN job_status = 'IN_PROGRESS' THEN 'UPLOAD_FAILED' ELSE reason_code END,
    expires_at = CURRENT_TIMESTAMP,
    next_attempt_at = CURRENT_TIMESTAMP
WHERE owner_id IS NULL AND storage_key IS NOT NULL;

CREATE INDEX ix_resume_upload_cleanup_owner
    ON resume_upload_cleanup_jobs (owner_id)
    WHERE owner_id IS NOT NULL AND storage_key IS NOT NULL;

CREATE TABLE account_deletion_jobs (
    operation_id UUID PRIMARY KEY,
    owner_id UUID,
    job_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMPTZ,
    last_error_code VARCHAR(40),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_account_deletion_job_status
        CHECK (job_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'DEAD_LETTER')),
    CONSTRAINT ck_account_deletion_job_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_account_deletion_job_owner_state CHECK (
        (job_status = 'COMPLETED' AND owner_id IS NULL)
        OR (job_status <> 'COMPLETED' AND owner_id IS NOT NULL)
    )
);

CREATE INDEX ix_account_deletion_jobs_due
    ON account_deletion_jobs (next_attempt_at, requested_at)
    WHERE job_status IN ('PENDING', 'PROCESSING');

-- Metadata-only, non-user-identifying audit record; expires after the approved 12 months.
CREATE TABLE account_deletion_audit_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    outcome_code VARCHAR(24) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_deletion_audit_event_type
        CHECK (event_type = 'ACCOUNT_DELETION_COMPLETED'),
    CONSTRAINT ck_account_deletion_audit_outcome
        CHECK (outcome_code = 'COMPLETED')
);

CREATE INDEX ix_account_deletion_audit_expiry
    ON account_deletion_audit_events (expires_at);
