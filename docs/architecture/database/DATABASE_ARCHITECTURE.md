# CareerPilot AI — Database Architecture

**Status:** Approved for documentation; design remains subject to review. No database or application implementation is included in this change.  
**Last updated:** 2026-11-12  
**Related:** [Target Architecture](../ARCHITECTURE.md) · [Project Roadmap](../PROJECT_ROADMAP.md) · [System Design Diagrams](../SYSTEM_DESIGN_DIAGRAMS.md)

## 1. Purpose and decisions

| Concern | Decision |
|---|---|
| Relational engine | **PostgreSQL 17** |
| Schema migration tool | **Flyway** |
| Local developer database | **Docker Compose** running PostgreSQL 17 |
| Application tenancy | Shared database and schema; tenant-owned rows identified by immutable user ID |
| File contents | Hybrid document-storage adapter; PostgreSQL stores metadata and ownership, not resume/document binaries |
| Production topology | Not selected; use a managed or self-hosted PostgreSQL service appropriate to deployment |

PostgreSQL 17 is a supported community major release with a published support end date of 8 November 2029. Keep it on the latest available minor release and schedule a major-version review well before end of support. A future major-version upgrade requires a planned compatibility, backup, and migration exercise. See the [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/).

Docker Compose is a local-development convenience only. Native PostgreSQL is an acceptable local alternative. Production environments must use their platform's supported database operations, not assume the local Compose file is a production solution.

## 2. Logical architecture

- One PostgreSQL database per environment is the baseline.
- The initial tenancy model is a shared database and schema with tenant-owned rows. Each authenticated user is a tenant boundary.
- Application services obtain the authenticated user ID from the server-side session principal. The client never chooses the effective owner ID.
- Every user-owned table stores a non-null user_id ownership column.
- Repository queries and mutations scope by both record identifier and trusted user_id.
- Foreign keys, including composite owner-aware foreign keys where one user-owned record references another, prevent cross-user relationships.
- Private document bytes remain in local/object storage. The database holds opaque storage keys and metadata.

The design deliberately avoids one database or schema per user for the initial product. That model increases provisioning, migration, and operational overhead without a current requirement for physical tenant separation.

## 3. Conceptual entity model

This is an initial logical model, not a final physical schema. Entity names, columns, nullability, indexes, status values, and retention rules must be confirmed before the first migration is written.

### 3.1 Core entities

| Entity | Purpose | Tenant ownership / key relationships |
|---|---|---|
| app_user | Login identity, normalized email, password hash, account state, timestamps | Root identity; immutable primary key |
| user_profile | Career summary, location preferences, role preferences, profile facts | One-to-one with app_user; owned by that user |
| resume | Logical resume record and user-visible metadata | Owned by one user; one resume can have versions |
| resume_version | Immutable original or tailored version metadata and storage reference | Same owner as parent resume; references document metadata/storage key |
| job_listing | Job title, company, location, source URL, description snapshot, observed date | User-owned in MVP; deduplication scoped to user |
| job_application | User's application state for a job, target resume version, notes, timestamps | Owned by one user; references same-user job and optional resume version |
| application_activity | Timeline events such as saved, applied, interview, rejected, or status change | Owned by one user and linked to that user's application |
| document_asset | File metadata, opaque storage key, size, media type, checksum, lifecycle state | Owned by one user; no public URL as authorization |
| interview_practice_session | Practice session metadata, role/job context, timestamps | Owned by one user; may reference same-user job/application |
| generated_artifact | Generated draft metadata and provenance | Owned by one user; references only same-user source records and document assets |

Account/session storage is related but intentionally not fixed here. The browser session design is approved; the concrete Spring Session/session repository and its schema lifecycle remain a separate decision. Framework-managed session tables must not be mixed casually with application-owned Flyway migrations without documenting ownership and upgrade behavior.

### 3.2 Entity relationship diagram

~~~mermaid
erDiagram
    APP_USER ||--o| USER_PROFILE : has
    APP_USER ||--o{ RESUME : owns
    RESUME ||--o{ RESUME_VERSION : versions
    APP_USER ||--o{ DOCUMENT_ASSET : owns
    RESUME_VERSION }o--|| DOCUMENT_ASSET : stored_as
    APP_USER ||--o{ JOB_LISTING : saves
    APP_USER ||--o{ JOB_APPLICATION : owns
    JOB_LISTING ||--o{ JOB_APPLICATION : applied_to
    RESUME_VERSION o|--o{ JOB_APPLICATION : used_for
    JOB_APPLICATION ||--o{ APPLICATION_ACTIVITY : records
    APP_USER ||--o{ INTERVIEW_PRACTICE_SESSION : owns
    JOB_LISTING o|--o{ INTERVIEW_PRACTICE_SESSION : context_for
    APP_USER ||--o{ GENERATED_ARTIFACT : owns
    DOCUMENT_ASSET o|--o{ GENERATED_ARTIFACT : output_file
~~~

The ERD communicates conceptual cardinality only. In the physical design, owner-aware relationships should use composite uniqueness and foreign keys such as UNIQUE(id, user_id) on a parent and FOREIGN KEY(parent_id, user_id) on its child. This prevents a row owned by User A from referencing a parent owned by User B, even if application code makes a mistake.

### 3.3 Ownership and lifecycle rules

- Use generated immutable primary keys (UUID is the proposed default) for externally referenced records; do not expose sequential IDs as a security boundary.
- Use created_at and updated_at timestamps in UTC for mutable entities. Add explicit lifecycle timestamps where needed.
- Normalize account email for lookup and enforce uniqueness on the normalized value.
- Keep original resume versions immutable. Tailored versions are new records and preserve source/provenance relationships.
- Keep job description snapshots and observed timestamps so source-page changes do not silently rewrite application evidence.
- Keep user-owned job listings distinct in the MVP. A future shared job catalogue would need a separate global entity plus a user-owned saved-job relationship.
- Store status as constrained values (or reference data) and validate transitions in application services.
- Avoid storing generated document bytes or resume contents in relational columns unless separately reviewed.

## 4. Multi-user isolation and database security

### 4.1 Mandatory application-layer controls

1. Resolve caller identity from the authenticated server-side session.
2. Build a trusted request/user context on the backend.
3. Every repository read/update/delete for user-owned data includes the trusted user_id predicate.
4. Every create operation sets user_id from the trusted context, not the request body.
5. Verify referenced records (job, resume, document, application) belong to the same user before linking them.
6. Apply the same owner check to document download, replacement, and deletion. An opaque storage key is not proof of authorization.
7. Return a consistent not-found/denied response that does not disclose another user's record existence.
8. Test direct object reference manipulation, guessed IDs, changed URL parameters, cross-user links, and concurrent update paths.

### 4.2 Database constraints and privileges

- Make user_id non-null on every tenant-owned table.
- Add foreign keys from user_id to app_user.
- Add owner-aware composite foreign keys for relationships between tenant-owned records.
- Add indexes beginning with user_id for common tenant-scoped access patterns, then validate query plans.
- Use a dedicated least-privilege runtime database role. It must not own the database/schema, create roles, bypass row security, or have superuser privileges.
- Use a separate migration role with DDL privileges. Runtime credentials must not be reused for migration administration.
- Keep credentials out of source control and frontend configuration. Require TLS for remote database connections.

### 4.3 Row-Level Security (RLS) decision

PostgreSQL RLS is a candidate defense-in-depth layer, not yet approved as an implementation requirement. It can reduce the impact of a missed application filter, but must be designed with the actual connection pool and transaction model.

Before enabling RLS, prove that:
- the backend sets authenticated tenant context transaction-locally for every transaction;
- connection reuse cannot leak one user's context into another request;
- table owners, migration roles, and privileged roles have understood bypass behavior;
- policies cover all tenant-owned tables and are tested for SELECT, INSERT, UPDATE, and DELETE;
- background jobs and administrative workflows establish explicit, auditable tenant context.

Do not enable partial RLS policies and assume isolation is complete. Until a reviewed RLS design is adopted, backend owner-scoped access plus database constraints are mandatory.

## 5. Indexing and integrity strategy

Initial index candidates, to be confirmed against actual query patterns:
- Unique index on normalized account email.
- (user_id, created_at DESC) for user-owned timeline/list views.
- (user_id, status, updated_at DESC) for application tracker filtering.
- (user_id, observed_at DESC) for saved job recency.
- Unique (id, user_id) constraints on parent records referenced by composite owner-aware foreign keys.
- Index foreign-key columns used for joins and deletes; PostgreSQL does not automatically create an index on every referencing foreign-key column.

Prefer constraints for invariants (NOT NULL, UNIQUE, CHECK, FOREIGN KEY) and application validation for user-facing messages and workflow rules. Avoid premature indexes; validate with representative data and EXPLAIN/ANALYZE before tuning.

## 6. Flyway migration strategy

### 6.1 Ownership and layout

Flyway is the single versioned schema-change mechanism for application-owned tables. Proposed layout:

~~~text
src/main/resources/db/migration/
  V1__create_identity_and_profile.sql
  V2__create_resume_and_document_metadata.sql
  V3__create_job_tracking.sql
  V4__create_interview_and_generated_artifact.sql
~~~

The examples show naming and possible grouping only; no migration files are created in this documentation milestone. Exact grouping and numbering are set when the physical schema is approved.

- Use versioned SQL migrations with a clear, immutable sequence.
- Use repeatable migrations only for suitable replaceable objects (e.g. reviewed views/functions), not as a substitute for versioned table changes.
- Treat committed/applied migrations as immutable. Correct mistakes with a new forward migration; do not edit a migration already applied to a shared environment.
- Keep schema changes and relevant integration tests in the same pull request.
- Review destructive changes separately and use expand/migrate/contract when old and new application versions must coexist.
- Use Flyway validation/checksums to detect unexpected migration drift. Do not casually use repair to hide a failed or modified migration.

Flyway maintains a schema history table recording applied migrations and checksums. See [Flyway migration concepts](https://documentation.red-gate.com/flyway/flyway-concepts/migrations) and [schema history table](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table).

### 6.2 Execution policy

- A single controlled migration step runs before the new application version receives traffic.
- Do not let every application replica race to run migrations during a multi-instance deployment.
- CI must validate migration naming, run migrations against an empty PostgreSQL 17 database, and run upgrade tests from the previous supported schema.
- Take or verify a recoverable backup before high-risk/destructive production migrations.
- Production runtime role has no DDL privileges; migration role is supplied only to the migration job.
- Migration failure stops rollout. Do not start application instances against a partially migrated schema.
- Test forward upgrade and application compatibility during rolling deployment where applicable.

### 6.3 Transaction and rollback policy

PostgreSQL supports transactional DDL for many operations, but not every operation can run inside a transaction. Review each migration for transaction behavior, lock duration, table rewrite risk, and availability impact.

Do not promise automatic down-migration or assume a schema rollback restores deleted data. Prefer a corrective forward migration. For destructive/data-loss events, restore from backup or use point-in-time recovery according to the incident plan. Any exceptional non-transactional migration needs an explicit runbook and recovery plan.

### 6.4 Compatibility decisions still open

At implementation time, select mutually compatible versions of:
- Flyway core and its PostgreSQL database support module;
- PostgreSQL JDBC driver;
- Spring Boot Flyway integration and PostgreSQL runtime driver.

Pin versions through project dependency management and verify the current Flyway PostgreSQL module/artifact and licensing/edition requirements. Do not invent dependency versions before repository build files and the compatibility matrix are reviewed.

## 7. Local PostgreSQL with Docker Compose

The local developer experience will provide a Compose service based on the PostgreSQL 17 image line, with:
- a named persistent volume for the database data directory;
- a health check using PostgreSQL readiness, not just container process state;
- database port bound to loopback only by default;
- credentials supplied through an ignored local environment file or developer environment, never committed;
- a non-superuser application role for normal application access;
- a separate migration role where practical;
- no exposed database port in deployed environments unless the operator has a deliberate secure need.

Compose should not include a production password, a public database binding, or a latest image tag. Pin to the PostgreSQL 17 major line and apply current 17.x minor/security updates deliberately. Persisted data must live in a named volume; deleting a container is not a database reset. Volume deletion is destructive and must be explicit.

Native PostgreSQL remains supported for developers who do not use Docker. The backend should connect through ordinary JDBC configuration so Compose, native, self-hosted, and managed databases do not require separate application code.

## 8. Backup, restore, and disaster recovery

Database backups and document-storage backups are separate but coordinated responsibilities. A database backup alone cannot restore uploaded resume files; a file backup alone cannot restore metadata, ownership, and application history.

### 8.1 Baseline strategy

- **Logical backup:** periodic pg_dump in a documented, consistent format for portable exports and recovery testing.
- **Physical/PITR capability:** for production deployments with meaningful recovery requirements, use PostgreSQL base backups plus continuous WAL archiving or a managed service's equivalent point-in-time recovery capability.
- **Encryption:** encrypt backups in transit and at rest; protect encryption keys separately from the backup destination.
- **Off-site copy:** keep at least one protected copy outside the primary database host/failure domain.
- **Access:** restrict backup read/restore permissions; audit access and never place production dumps in the public repository.
- **Document files:** back up configured local/object storage using versioning or snapshots where available, with retention and deletion policy aligned to database records.
- **Consistency:** coordinate database and file backup/restore procedures. Use immutable object keys or versioned files where possible so a database snapshot does not point to a subsequently overwritten object.
- **Retention:** define daily/weekly/monthly retention based on cost, privacy, and recovery requirements; do not keep personal data indefinitely by default.
- **Monitoring:** alert operators on failed backup jobs, missing WAL/archive continuity, insufficient storage, and backup age beyond policy.

### 8.2 Restore and verification

- Maintain a documented restore runbook for each deployment type.
- Regularly restore into an isolated environment; a successful backup command is not proof of recoverability.
- Verify schema version, record counts/sanity checks, ownership relationships, document references, and application startup after restore.
- Test database and document restoration as one recovery exercise.
- Document how account deletion and retention policies interact with backups, including when deleted personal data ages out of retained backups.
- Keep recovery credentials and encryption keys available to authorized operators, but separate from backup data.

### 8.3 RPO and RTO

Recovery Point Objective (RPO) and Recovery Time Objective (RTO) are not yet selected. Set them according to deployment user expectations, operating budget, and service criticality before public production use. A local single-user installation may accept manual backup and longer recovery; a hosted multi-user service needs an automated schedule and tested recovery target. Do not advertise a recovery guarantee until measured in restore exercises.

## 9. Data privacy, retention, and operational controls

- Minimize collected data and document the purpose for each sensitive field.
- Never log passwords, session identifiers, resume contents, access tokens, or full document storage keys.
- Use TLS for remote connections and encrypted storage/backups appropriate to the deployment.
- Keep production, test, and development databases separate; do not copy production personal data into development by default.
- Use synthetic or sanitized fixtures for tests.
- Define account export, deletion, retention, legal hold, and backup-expiry behavior before public multi-user release.
- Database deletion must not leave private document objects orphaned indefinitely; deletion workflows need retryable cleanup and reconciliation.
- Use health checks and monitoring without exposing SQL details, credentials, or personal data.

## 10. Review checklist and implementation gate

The following decisions remain open and require review before the first database migration or production data is created:

1. Confirm the MVP entity list and exact fields, relationships, status values, and deletion semantics.
2. Confirm UUID generation strategy, normalized email rules, timestamps, and timezone conventions.
3. Approve indexes and composite owner-aware foreign keys against initial query patterns.
4. Decide whether and how to add PostgreSQL RLS after validating connection-pool transaction context.
5. Select compatible Flyway/JDBC/Spring Boot dependency versions and define migration-job ownership.
6. Confirm Compose file location, local secret-loading convention, image update policy, and native-install instructions.
7. Select backup tooling, schedule, retention, encryption/key management, off-site destination, and RPO/RTO.
8. Define document backup consistency, retention, account deletion, and restore ordering.
9. Define database observability, connection pool limits, TLS, and least-privilege role provisioning.
10. Review PostgreSQL 17 support lifecycle and schedule the next major upgrade decision.

**No database implementation is part of Branch 11.** Do not create Flyway scripts, entity classes, repositories, Docker Compose YAML, database containers, or live databases until this design and its open operational decisions are reviewed and explicitly approved for implementation.
