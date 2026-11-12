CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email)) AND length(email) > 0),
    CONSTRAINT ck_users_account_status CHECK (
        account_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DISABLED', 'DELETION_PENDING', 'DELETED')
    )
);

CREATE TABLE career_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    display_name VARCHAR(160),
    professional_summary TEXT,
    experience_years NUMERIC(4,1),
    preferences_schema_version INTEGER NOT NULL DEFAULT 1,
    preferences_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_career_profiles_user UNIQUE (user_id),
    CONSTRAINT uq_career_profiles_owner_id UNIQUE (user_id, id),
    CONSTRAINT fk_career_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_career_profiles_experience CHECK (experience_years IS NULL OR experience_years >= 0),
    CONSTRAINT ck_career_profiles_preferences_version CHECK (preferences_schema_version > 0),
    CONSTRAINT ck_career_profiles_preferences_object CHECK (jsonb_typeof(preferences_json) = 'object')
);

CREATE TABLE profile_skills (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    skill_name VARCHAR(120) NOT NULL,
    normalized_skill_name VARCHAR(120) NOT NULL,
    proficiency VARCHAR(32),
    years_experience NUMERIC(4,1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_profile_skills_owner_normalized UNIQUE (user_id, normalized_skill_name),
    CONSTRAINT uq_profile_skills_owner_id UNIQUE (user_id, id),
    CONSTRAINT fk_profile_skills_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_profile_skills_name CHECK (length(btrim(skill_name)) > 0),
    CONSTRAINT ck_profile_skills_normalized CHECK (
        normalized_skill_name = lower(btrim(normalized_skill_name)) AND length(normalized_skill_name) > 0
    ),
    CONSTRAINT ck_profile_skills_years CHECK (years_experience IS NULL OR years_experience >= 0)
);

CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(240) NOT NULL,
    company_name VARCHAR(240) NOT NULL,
    company_website VARCHAR(2048),
    location VARCHAR(240),
    employment_type VARCHAR(64),
    work_mode VARCHAR(64),
    description TEXT,
    source_url VARCHAR(2048),
    source_name VARCHAR(160),
    observed_at TIMESTAMPTZ,
    posted_at DATE,
    closing_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_jobs_owner_id UNIQUE (user_id, id),
    CONSTRAINT fk_jobs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_jobs_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_jobs_company CHECK (length(btrim(company_name)) > 0),
    CONSTRAINT ck_jobs_dates CHECK (posted_at IS NULL OR closing_at IS NULL OR closing_at >= posted_at)
);

CREATE INDEX ix_jobs_owner_created ON jobs (user_id, created_at DESC);
CREATE INDEX ix_jobs_owner_title ON jobs (user_id, lower(title));

CREATE TABLE applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    job_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SAVED',
    applied_date DATE,
    notes TEXT,
    next_action_date DATE,
    interview_at TIMESTAMPTZ,
    interview_timezone VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_applications_owner_job UNIQUE (user_id, job_id),
    CONSTRAINT uq_applications_owner_id UNIQUE (user_id, id),
    CONSTRAINT fk_applications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_applications_owned_job FOREIGN KEY (user_id, job_id) REFERENCES jobs (user_id, id),
    CONSTRAINT ck_applications_status CHECK (
        status IN ('SAVED', 'PREPARING', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN', 'CLOSED')
    )
);

CREATE INDEX ix_applications_owner_status_updated ON applications (user_id, status, updated_at DESC);
CREATE INDEX ix_applications_owner_next_action ON applications (user_id, next_action_date)
    WHERE next_action_date IS NOT NULL;
