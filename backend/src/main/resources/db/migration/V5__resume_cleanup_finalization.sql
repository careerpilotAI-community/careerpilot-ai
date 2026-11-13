ALTER TABLE resume_assets
    DROP CONSTRAINT ck_resume_assets_status;

ALTER TABLE resume_assets
    ADD CONSTRAINT ck_resume_assets_status
    CHECK (status IN ('AVAILABLE', 'FAILED', 'DELETING', 'DELETED'));

-- Once storage absence is verified, remove document-identifying metadata while
-- retaining only the minimum owner-scoped tombstone needed by the job/audit trail.
ALTER TABLE resume_assets
    ALTER COLUMN original_filename DROP NOT NULL,
    ALTER COLUMN media_type DROP NOT NULL,
    ALTER COLUMN checksum_sha256 DROP NOT NULL,
    ALTER COLUMN storage_key DROP NOT NULL,
    ALTER COLUMN scanner_result DROP NOT NULL;

ALTER TABLE resume_deletion_jobs
    ADD COLUMN processing_started_at TIMESTAMPTZ;

ALTER TABLE resume_assets
    ADD CONSTRAINT ck_resume_assets_deleted_metadata
    CHECK (
        status <> 'DELETED'
        OR (
            original_filename IS NULL
            AND media_type IS NULL
            AND checksum_sha256 IS NULL
            AND storage_key IS NULL
            AND scanner_result IS NULL
            AND size_bytes = 0
        )
    );
