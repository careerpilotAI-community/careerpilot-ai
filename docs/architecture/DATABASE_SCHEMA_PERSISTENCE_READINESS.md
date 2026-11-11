# Branch 18 — Database Schema & Persistence Readiness

**Status:** Proposed physical data design; documentation only  
**Date:** 2026-11-12  
**Parent:** Branch 17 / PR #24 — Approved MVP Product Requirements  
**Approved platform direction:** PostgreSQL 17, Flyway, Docker Compose, local-first validation

## 1. Purpose and boundary

Translate the approved MVP domain model into a reviewable relational design, tenant-isolation strategy, migration policy, and local backup/restore plan. This is a design proposal, not a migration specification ready to execute.

**No implementation is authorized by this branch.** Do not add SQL migrations, entities, repositories, API endpoints, dependencies, Docker changes, or application code. Physical names and constraints below require review before a first Flyway migration is written.

## 2. Design principles

- PostgreSQL is the source of truth for structured application data.
- Every private business record is owned by exactly one user.
- The authenticated server-side session determines the owner; client-supplied owner identifiers are never trusted.
- Relational constraints reinforce, but do not replace, application authorization.
- Resume binaries are stored outside the database in private local storage; PostgreSQL stores metadata and opaque storage keys only.
- Original resume assets are immutable. Tailored outputs are separate assets/versions.
- Use UTC instants for audit timestamps; preserve an explicit IANA timezone where a user-entered local event time must be interpreted.
- Prefer explicit foreign keys, check constraints, and indexes over implicit application-only assumptions.
- Avoid premature global Company/Skill catalogs that introduce cross-user data leakage or complex deduplication.

## 3. Proposed entity inventory

| Table / aggregate | Purpose | Ownership |
|---|---|---|
| users | Login identity, normalized email, password hash, account state and audit timestamps | Root identity |
| career_profiles | One profile per user; summary and career preferences | user_id |
| profile_skills | User-entered skill names and optional proficiency/years | user_id |
| jobs | User-saved job listing and source snapshot | user_id |
| applications | User's tracking record and current status for a saved job | user_id |
| interview_events | Interview schedule and notes for an application | user_id |
| resumes | Logical resume collection/label and original filename metadata | user_id |
| resume_assets | Immutable stored binary/version metadata, checksum, media type and storage key | user_id |
| ai_suggestion_runs | User-requested local analysis run and references to selected inputs | user_id |
| ai_suggestions | Individual structured match observations or wording proposals | user_id |

### Relationships

- users 1 — 0..1 career_profiles.
- users 1 — many profile_skills, jobs, applications, resumes, resume_assets, ai_suggestion_runs and ai_suggestions.
- jobs 1 — many applications is not assumed. Recommended initial rule: **one current application per user/job**, enforced by a unique constraint on (user_id, job_id). Re-application history requires a later explicit product decision.
- applications 1 — many interview_events.
- resumes 1 — many resume_assets; each resume may identify one original asset and multiple later assets.
- ai_suggestion_runs reference one job and optionally a resume and/or profile snapshot; each run has many ai_suggestions.
- Company is initially a text/value snapshot on jobs (company_name and optional company_website), not a global shared table.
- Skills are initially user-scoped strings in profile_skills, not a global taxonomy.

## 4. Proposed column and constraint outline

This is logical design guidance; exact SQL types, lengths, defaults and naming convention are to be finalized in the migration review.

### users
- id: generated primary key (UUID recommended for portable identifiers).
- email: normalized, required, unique.
- password_hash: required; never store plaintext or reversible passwords.
- status: constrained account lifecycle value (e.g. ACTIVE, DISABLED, PENDING_VERIFICATION only if verification is adopted).
- created_at, updated_at: required UTC timestamps.
- Do not put profile/job data in the identity row.

### career_profiles
- id, user_id (unique FK to users), display_name, professional_summary, preferred_roles, preferred_locations, work_modes, experience/education/certification data as structured JSON only where query needs do not justify normalization; created_at, updated_at.
- unique(user_id) ensures one profile per user.
- JSON fields require a documented shape/version if selected; avoid opaque blobs for frequently filtered fields.

### profile_skills
- id, user_id, skill_name, optional proficiency, optional years_experience, created_at.
- Index (user_id, normalized_skill_name); decide duplicate/case-folding policy.
- No cross-user shared skill record.

### jobs
- id, user_id, title, company_name, location, employment_type, work_mode, description, source_url, source_name, observed_at, posted_at, closing_at, created_at, updated_at.
- Index (user_id, created_at desc), (user_id, status/follow-up fields only if those fields are adopted), and a partial/normalized source URL uniqueness strategy only after duplicate semantics are approved.
- A job is a user's private saved snapshot; it is not a canonical globally shared job listing.

### applications
- id, user_id, job_id, status, applied_at, notes, next_action_at, created_at, updated_at.
- Composite FK (user_id, job_id) references a unique (user_id, id) key on jobs, preventing a user's application from pointing to another user's job.
- Unique (user_id, job_id) for one current application per job.
- Check constraint for approved status values: SAVED, PREPARING, APPLIED, INTERVIEWING, OFFER, REJECTED, WITHDRAWN, CLOSED.
- Index (user_id, status), (user_id, next_action_at), and (user_id, applied_at desc).

### interview_events
- id, user_id, application_id, starts_at, ends_at (optional), timezone_id, interview_type (optional), notes, created_at, updated_at.
- Composite FK (user_id, application_id) ensures ownership matches.
- Check ends_at > starts_at when end time is supplied.
- Index (user_id, starts_at).

### resumes and resume_assets
- resumes: id, user_id, label, created_at, updated_at.
- resume_assets: id, user_id, resume_id, version_label, original_filename, storage_key, media_type_detected, size_bytes, sha256_checksum, asset_kind (ORIGINAL or TAILORED), created_at.
- Composite FK (user_id, resume_id) prevents cross-owner references.
- Unique storage_key; storage_key is opaque and never a public URL.
- Original assets are immutable; replacing content creates a new asset/version.
- Do not store binary resume contents in PostgreSQL for the initial local architecture.
- Upload type/size allowlist, scanning/quarantine, retention, deletion and encryption policy must be approved before upload implementation.

### ai_suggestion_runs and ai_suggestions
- ai_suggestion_runs: id, user_id, job_id, optional resume_id, provider (initially OLLAMA_LOCAL), model_name, model_version (if available), status, created_at, completed_at, error_code (safe non-sensitive code).
- ai_suggestions: id, user_id, run_id, suggestion_type, content, rationale, source_references (structured references to user/job facts), review_state, created_at, reviewed_at.
- Composite owner-aware FKs for job, resume and run references.
- Do not persist full prompts, raw resume text, credentials, or sensitive provider diagnostics by default. Retention and user deletion behavior must be agreed.
- Suggestions are drafts; review_state is not an authorization to submit applications or contact recruiters.

## 5. Multi-user isolation strategy

### Required database pattern

For every user-owned table:
1. Include non-null user_id.
2. Add an index beginning with user_id for common owner-scoped reads.
3. Use composite unique keys and composite foreign keys for relationships between owned records (for example, (user_id, id)).
4. Ensure child records carry the same user_id and cannot reference another owner's parent.
5. Scope reads, updates and deletes by both record ID and authenticated user ID in the service/repository query.
6. Derive user_id from the authenticated server-side session, never request JSON, URL, or form fields.

PostgreSQL Row-Level Security may be evaluated as defense in depth in a later design review. It is not assumed as the sole authorization boundary in this proposal; connection pooling and session context must be designed safely if adopted.

### Required isolation tests

- User A cannot list, read, update, delete, download, or infer User B's private records.
- User A cannot create an application referencing User B's job.
- User A cannot attach User B's resume to an AI run.
- Nested routes and guessed UUIDs do not bypass ownership checks.
- Dashboard aggregates and search results include only the authenticated user's rows.
- Background tasks, exports, and future scheduled jobs carry explicit owner context.
- Tests cover both service authorization and database relationship constraints.

## 6. Migration strategy

- Use versioned Flyway SQL migrations under the backend's agreed migration directory.
- Pin Flyway version compatible with the verified Spring Boot/PostgreSQL dependency set.
- Migration names follow one convention, e.g. V1__create_identity_and_profile.sql; confirm exact naming and directory with the existing backend layout before implementation.
- Migrations are immutable after being applied to a shared environment. Correct mistakes with a new forward migration.
- Never use automatic destructive rollback as the normal deployment strategy.
- Validate migrations against a fresh PostgreSQL 17 database and an existing database upgraded from the previous version.
- CI should run Flyway validation/migration and PostgreSQL integration tests.
- Destructive changes require a documented expand/migrate/contract plan and backup verification.
- No production credentials or personal data in migration fixtures.

### Suggested migration grouping (not approved executable SQL)

1. V1 — identity and profile foundations (only after identity policy is decided).
2. V2 — profile skills and saved jobs.
3. V3 — applications and interview events.
4. V4 — resume metadata/assets (only after upload/storage policy).
5. V5 — AI suggestion runs and suggestions (only after AI privacy/retention policy).

The first persistence milestone may intentionally implement a smaller subset. Do not create placeholder tables for every future feature merely to match this proposed sequence.

## 7. Local backup and restore proposal

### Local developer environment

- Use PostgreSQL custom-format logical backups (pg_dump) for the database.
- Store backups outside the repository and outside the public/static web root.
- If resume files exist, back up the private storage directory together with the database backup; document that the two snapshots must be coordinated.
- Keep secrets/configuration separate from backups and never commit them.
- Define a user-controlled backup location, retention count/period, and encryption approach before calling backup behavior complete.
- Provide a restore procedure into a fresh local database and verify Flyway history and representative records.
- Test restore, not merely backup creation.
- Docker volumes are persistence, not backup.

### Minimum recovery validation

1. Stop application writes.
2. Create a database dump and a coordinated copy of private document storage, if present.
3. Restore the database to a clean PostgreSQL 17 instance.
4. Restore the matching document snapshot to the configured private storage path.
5. Verify application records, ownership relationships, resume asset checksums, and file availability.
6. Restart the application and confirm health/read access.
7. Record recovery time and any manual steps.

Cloud/self-hosted backup, off-site copies, encryption-key recovery, retention compliance and disaster recovery objectives are separate deployment decisions.

## 8. Indexing and data lifecycle

- Start with owner-leading indexes for list/filter/sort access patterns; validate with EXPLAIN on realistic sample data.
- Add indexes only for known query paths; account for write/storage overhead.
- Use database constraints for required fields, enumerated statuses, uniqueness and referential integrity.
- Define soft-delete versus hard-delete behavior before implementation. Recommendation: avoid soft-deleting every table by default; choose explicit lifecycle semantics per aggregate.
- Account deletion must define cascade/anonymize/retention behavior across profile, jobs, applications, resumes, AI suggestions, backups and logs.
- Backups can retain deleted data until expiry; this must be disclosed and a backup-expiry policy documented.
- Use created_at/updated_at consistently; audit history beyond timestamps is a separate requirement.

## 9. Open decisions required before first migration

| Decision | Proposed default | Why approval is needed |
|---|---|---|
| Identifier | UUID primary keys | Affects all FKs and API identifiers |
| One application per job | Unique (user_id, job_id) | Re-application/history behavior |
| Company model | Snapshot company fields on each user-owned job | Avoids global data and deduplication complexity |
| Skill model | User-scoped skill strings | Avoids premature taxonomy |
| Profile flexible fields | Relational columns for searchable data; versioned JSON for low-query structured details | Queryability and schema evolution |
| Resume versioning | Immutable resume_assets under a resume aggregate | Preserves original and tailored history |
| Binary storage | Private local filesystem; opaque storage key in DB | Storage boundary and backup coordination |
| Delete policy | Explicit per aggregate; no blanket cascade | Retention and user expectations |
| Time handling | UTC instants + IANA timezone for user-entered schedules | Correct display and DST behavior |
| RLS | Defer; evaluate as defense in depth | Requires safe per-request context with connection pooling |
| Backup | pg_dump + coordinated file copy; user-configurable retention | Recovery and privacy expectations |
| Initial persistence slice | User/profile + jobs/applications only after identity gate | Keeps first migration bounded |

## 10. Persistence milestone acceptance criteria

- [ ] Existing backend layout, dependency versions and current migration state are verified.
- [ ] Entity ownership and relationships are approved.
- [ ] Physical schema, key strategy, uniqueness and deletion semantics are approved.
- [ ] Owner-aware composite foreign keys and authorization query conventions are specified.
- [ ] Cross-user and cross-owner-reference tests are specified.
- [ ] Migration naming, location, version pinning and forward-only policy are approved.
- [ ] Fresh-install and upgrade migration validation are specified.
- [ ] PostgreSQL 17 integration-test strategy is agreed.
- [ ] Local backup and restore procedure is tested, including file storage when applicable.
- [ ] Retention, account deletion and backup expiry are documented.
- [ ] Explicit authorization to implement the persistence milestone is received.

## 11. Approval record and implementation gate

- [x] PostgreSQL 17 selected as database direction in Branch 11.
- [x] Flyway selected as migration direction in Branch 11.
- [x] Docker Compose selected for local database service in Branch 11.
- [x] Multi-user owner isolation required by approved MVP.
- [ ] Proposed physical schema and open decisions approved.
- [ ] Backup/restore and retention policy approved.
- [ ] Persistence milestone acceptance criteria approved.
- [ ] Explicit implementation authorization received.

**Gate:** Branch 18 is documentation-only. Approval of this design does not authorize SQL migrations, backend entities, repositories, application code, or any other implementation. A separate explicit implementation approval is required.
