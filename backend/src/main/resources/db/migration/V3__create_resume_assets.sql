CREATE TABLE resume_assets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    asset_kind VARCHAR(16) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    scanner_result VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_assets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_resume_assets_owner_id UNIQUE (user_id, id),
    CONSTRAINT uq_resume_assets_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_resume_assets_kind CHECK (asset_kind IN ('ORIGINAL', 'TAILORED')),
    CONSTRAINT ck_resume_assets_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT ck_resume_assets_status CHECK (status IN ('AVAILABLE', 'FAILED')),
    CONSTRAINT ck_resume_assets_scanner CHECK (scanner_result IN ('CLEAN', 'DEV_BYPASS'))
);
CREATE INDEX ix_resume_assets_owner_created ON resume_assets (user_id, created_at DESC);
