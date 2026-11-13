ALTER TABLE resume_assets
    DROP CONSTRAINT ck_resume_assets_status;

ALTER TABLE resume_assets
    ADD CONSTRAINT ck_resume_assets_status
    CHECK (status IN ('AVAILABLE', 'FAILED', 'DELETING'));

ALTER TABLE resume_assets
    ADD COLUMN deletion_requested_at TIMESTAMPTZ,
    ADD COLUMN deletion_reason VARCHAR(40);

ALTER TABLE resume_assets
    ADD CONSTRAINT ck_resume_assets_deletion_metadata
    CHECK (
        (status = 'DELETING' AND deletion_requested_at IS NOT NULL AND deletion_reason = 'USER_REQUEST')
        OR
        (status <> 'DELETING' AND deletion_requested_at IS NULL AND deletion_reason IS NULL)
    );

CREATE TABLE resume_deletion_jobs (
    operation_id UUID PRIMARY KEY,
    asset_id UUID NOT NULL UNIQUE,
    owner_id UUID NOT NULL,
    job_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_code VARCHAR(40),
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_resume_deletion_job_asset_owner
        FOREIGN KEY (owner_id, asset_id) REFERENCES resume_assets (user_id, id),
    CONSTRAINT ck_resume_deletion_job_status
        CHECK (job_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'DEAD_LETTER')),
    CONSTRAINT ck_resume_deletion_job_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX ix_resume_deletion_jobs_pending
    ON resume_deletion_jobs (next_attempt_at, requested_at)
    WHERE job_status IN ('PENDING', 'PROCESSING');
