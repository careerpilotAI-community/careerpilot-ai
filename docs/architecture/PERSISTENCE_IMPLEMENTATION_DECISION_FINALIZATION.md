# Branch 25 — Persistence Implementation Decision Finalization

**Status:** Proposed decision record for review; documentation only  
**Base:** `main` at `81a050348572e9f89af092a3fd3420cf297a8b78` (Branch 24 / PR #31 merge)  
**Branch:** `branch-25-persistence-decision-finalization`  
**Scope:** Reconcile Branch 18's relational proposal, Branch 19's persistence recommendations, and the current target architecture into an implementation-ready decision record.

## 1. Purpose and boundary

Branch 18 defined the proposed relational model and Branch 19 recommended defaults. This branch makes the remaining choices explicit, identifies contradictions and dependencies, and presents a bounded first persistence slice for user review.

This is a decision proposal, not implementation authorization. It must not add SQL, Flyway migrations, entities, repositories, dependencies, Docker/Compose files, runtime configuration, endpoints, UI, or CI changes. Recommendations become approved decisions only after explicit review.

## 2. Source-of-truth hierarchy

Apply these rules when documents appear to differ:

1. Approved MVP requirements (Branch 17) define product behavior and exclusions.
2. Branch 18 defines the proposed relational model and integrity/isolation principles.
3. Branch 19 provides recommended persistence defaults, but any unresolved item remains a proposal.
4. The target architecture defines PostgreSQL 17, Flyway, Docker Compose, local-first validation, and multi-user ownership.
5. Identity/session policy is a separate gate. This branch does not approve registration, password, session, CSRF, or account-recovery implementation.

Do not treat an architecture recommendation as a user-approved product behavior where the product requirements leave a choice open.

## 3. Proposed decisions for review

| Decision area | Recommended decision | Reason / implementation consequence | Approval state |
|---|---|---|---|
| Database platform | PostgreSQL 17 for local-first and later deployment modes | Already the approved platform direction | Platform approved; operational details proposed |
| Primary identifiers | UUID primary keys for users and owned aggregates | Portable identifiers; not an authorization mechanism | Proposed default |
| Owner identity | Derive owner exclusively from authenticated server-side session; never accept client owner IDs as authority | Aligns persistence with the approved browser security model | Requirement approved; auth implementation separate |
| Ownership columns | Every user-owned row has non-null `user_id`; owner-leading indexes and owner-aware composite foreign keys | Prevents cross-user references at the relational layer | Proposed default |
| Authorization | Service/repository queries always scope by authenticated owner; UUIDs do not grant access | Database constraints supplement, not replace, authorization | Requirement approved |
| PostgreSQL RLS | Defer RLS for the MVP; revisit only with a reviewed transaction-local tenant-context design and pool-leakage tests | Avoid unsafe partial policies with pooled connections | Recommended deferral |
| Company representation | Store company name and optional website as a snapshot on each user's saved job; no global Company table in MVP | Avoids cross-user shared data and premature deduplication | Proposed default |
| Skills | Store user-scoped skill strings; normalize whitespace/case for duplicate detection; defer global taxonomy | Keeps MVP simple and private | Proposed default |
| Profile structure | Relational columns for searchable/filterable attributes; versioned JSON only for low-query structured sections with a documented shape | Balances queryability and flexibility | Proposed default; exact fields require schema review |
| Application attempts | One current application/tracking record per user/job in MVP; preserve status history through explicit fields/events only if separately approved; defer multiple re-application attempts | Branch 18's simple unique key conflicts with an unbounded attempt-history interpretation | Product decision requiring user approval |
| Job duplicates | Do not globally deduplicate jobs; avoid automatic merging; any per-user source-URL uniqueness rule must be validated against product behavior | Different vacancies can share URLs or be reposted | Proposed default |
| Interview events | Defer a separate interview-event table from the first migration; add after tracker behavior and timezone fields are confirmed | Reduces initial schema scope | Proposed deferral |
| Resume data | Defer resume/resume_asset tables and all binary upload behavior from the first persistence slice | File validation, scanning, encryption, retention and restore policy are not finalized | Required gate; not approved for first slice |
| AI history | Defer AI run/suggestion persistence and do not store raw prompts or extracted resume text by default | AI privacy, provenance and retention policy remain open | Required gate |
| Time | Store instants as PostgreSQL `timestamptz`/UTC; store an IANA timezone identifier for user-entered local schedules | Preserves correct rendering and daylight-saving behavior | Proposed default |
| Delete behavior | Define lifecycle per aggregate; no blanket cascade or blanket soft-delete policy; document backup expiry separately | Avoids accidental data loss and hidden retention | Required policy review |
| Database roles | Separate least-privilege runtime role from migration/DDL role; no runtime superuser/owner credentials | Limits blast radius | Proposed security requirement |
| Migration tool | Flyway versioned SQL migrations; applied migrations are immutable and corrected by forward migration | Reproducible schema history | Tool approved; exact dependency/version pending compatibility check |
| Migration baseline | Fresh local database starts at V1; do not enable `baselineOnMigrate` by default for a new empty database | Prevents silently skipping initial migrations | Proposed default; confirm existing DB state before implementation |
| Migration execution | One controlled migration step before application traffic; avoid concurrent per-replica migration races | Predictable rollout behavior | Proposed default |
| Local database | PostgreSQL 17 Compose service, named volume, health check, local-only port binding by default | Safe, repeatable local development | Platform approved; concrete Compose settings pending |
| Secrets | No real credentials in Git; use ignored local environment file or supported secret mechanism; commit placeholders only | Avoids credential leakage | Requirement approved |
| Backups | User-configurable private backup location; PostgreSQL custom-format dump plus coordinated private-file snapshot when files exist; document encryption and retention | DB-only backup cannot recover external resume binaries | Policy approval required before release |
| Test strategy | PostgreSQL integration tests against a real PostgreSQL 17 instance; prefer Testcontainers if compatible with CI, otherwise a CI PostgreSQL service | Avoids relying only on H2 semantics | Proposed approach; validate CI support |
| Fixtures | Deterministic, synthetic, non-sensitive fixtures only | Prevents personal data in tests | Proposed requirement |

## 4. First persistence slice proposed for Branch 26

Keep the first implementation narrow:

### Include
- PostgreSQL 17 local service and health check using Docker Compose.
- Flyway dependency/configuration aligned to the repository's verified Spring Boot 3.5.11 dependency management and a compatible PostgreSQL support module.
- Initial migration for the minimum identity/profile/job/application relational foundation, only after the account identity boundary and application-attempt policy are approved.
- UUID primary keys, normalized unique email, owner-aware keys/foreign keys, required constraints, and indexes justified by MVP queries.
- Repository integration tests against PostgreSQL 17, including cross-owner relationship rejection.
- Setup documentation and a clean-database migration verification.

### Exclude
- Registration/login/session/CSRF implementation (separate identity/security implementation gate).
- Resume binaries, resume metadata, scanning, and file storage.
- Interview-event scheduling table unless explicitly brought into the approved first slice.
- AI run/suggestion persistence.
- PostgreSQL RLS.
- Production/cloud database provisioning, managed backups, point-in-time recovery, and public deployment hardening.
- Job scraping, automatic applications, recruiter outreach, or other excluded MVP capabilities.

The exact first migration table list must be confirmed after the application-attempt choice and account identity boundary are approved. Do not create placeholder tables for every future feature.

## 5. Required implementation conventions

### Relational integrity and isolation
- Every owned row carries non-null `user_id`.
- Parent records referenced by owned children expose a unique owner-aware key such as `(user_id, id)`.
- Child foreign keys include `user_id` so a child cannot reference another user's parent.
- Owner-scoped reads, updates, and deletes include both record ID and authenticated owner ID.
- Add indexes for actual owner-scoped list/filter/sort queries and foreign-key access paths; avoid speculative indexes.
- Add database constraints for invariants and service validation for user-facing errors.

### Flyway
- Use a single documented migration location and one naming convention (for example, `V1__create_identity_and_profile.sql`).
- Review SQL in the same PR as related persistence code and tests.
- Never edit a migration after it has been applied to a shared environment.
- Prefer corrective forward migrations; no automatic destructive down-migration.
- Run Flyway validation and migration against a fresh PostgreSQL 17 database in CI.
- Any destructive change requires a reviewed expand/migrate/contract plan and recoverable backup.

### Docker Compose
- Use a pinned PostgreSQL 17 image tag/digest policy selected at implementation time; do not use `latest`.
- Use a named volume for local data persistence.
- Add a readiness health check and make backend startup depend on database health where Compose orchestration is used.
- Bind the database port to loopback by default for local development; document how to change it deliberately.
- Do not place production credentials or real secrets in Compose files or committed environment examples.
- Document that a named volume is persistence, not backup.

### Backup and restore
- Do not label backup behavior complete until both database and private files (if present) can be restored as a consistent set.
- Define backup location, encryption/key handling, retention, and account-deletion/backup-expiry behavior before release.
- Test restore into a clean PostgreSQL 17 instance and verify representative records, ownership relationships, and file checksums where applicable.
- RPO/RTO targets remain unset until the intended deployment and user expectations are defined.

## 6. Mandatory gates before Branch 26 implementation

- [ ] User approves or changes the application re-application policy (recommended: defer multiple attempts in MVP).
- [ ] User confirms the exact first migration scope and whether account identity rows are included in Branch 26 or coordinated with Branch 27.
- [ ] Confirm normalized email rules and case-folding/uniqueness behavior.
- [ ] Confirm profile fields that are relational versus versioned JSON.
- [ ] Confirm delete semantics for users, profiles, jobs, and applications; no blanket cascade.
- [ ] Confirm Flyway/PostgreSQL JDBC dependency compatibility against actual repository manifests; select pinned versions.
- [ ] Confirm migration location, naming, fresh-install baseline, and CI validation approach.
- [ ] Confirm Compose image pinning, loopback port, volume name, health check, and local secret handling.
- [ ] Confirm PostgreSQL integration-test approach and CI capability.
- [ ] Confirm backup location/retention/encryption policy for the local MVP release, including private-file coordination.
- [ ] Reconcile the approved decision record with Branch 18/19/architecture docs so contradictions are removed or explicitly superseded.
- [ ] Receive explicit approval for the specific Branch 26 implementation scope.

## 7. Decisions intentionally left to later gates

- Account registration mode, verification, password hashing parameters, reset/recovery, session timeout/store, CSRF token strategy, CORS origins, roles/admin provisioning, rate limits, and account export/deletion details: Branch 27 identity/security decision and implementation gate.
- Resume upload allowlist, size limits, scanning/quarantine, encryption, storage paths, retention, deletion, quotas, and backup coordination: document-storage gate.
- AI prompt/result persistence, model metadata, provenance, retention, and deletion: AI privacy gate.
- RLS: separate future review only if needed and safely supportable.
- Cloud/self-hosted database operations, TLS, managed backups, disaster recovery, and RPO/RTO: deployment-specific gate.

## 8. Review outcome template

When reviewing this proposal, record each decision as one of:
- **Approved** — may be used as a constraint for the next implementation plan.
- **Change requested** — revise the recommendation and explain the product/technical consequence.
- **Deferred** — explicitly excluded from the first persistence slice and assigned to a later gate.

No item is approved merely because this document is merged. A merged proposal records the reviewed plan; implementation still requires a separate, explicit user authorization.

## 9. Branch 25 deliverables and exit criteria

- One decision record consolidating Branch 18/19 proposals and their unresolved gates.
- Roadmap updated to reflect Branch 24 merged and Branch 25 decision review in progress.
- No application code, dependencies, migrations, Docker/Compose, runtime configuration, CI, or security behavior changed.
- PR review confirms the scope is coherent and does not silently authorize Branch 26.
