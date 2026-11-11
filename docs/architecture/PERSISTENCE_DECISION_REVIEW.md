# Branch 19 — Persistence Decisions & Implementation Gate

**Status:** Decision proposal; documentation only  
**Parent:** Branch 18 — Database Schema & Persistence Readiness  
**Target:** Resolve the open decisions required before the first Flyway migration.

## 1. Purpose and scope

Branch 18 established the proposed relational model and operational approach. This document turns its unresolved items into explicit recommendations for review. It does not create executable SQL or authorize implementation.

## 2. Recommended decisions

| Topic | Recommendation | Reason / consequence |
|---|---|---|
| Primary identifiers | UUID primary keys | Portable across local, self-hosted and cloud deployments; avoid exposing sequential IDs |
| Application uniqueness | One active/current application per user and saved job; retain withdrawn/rejected/closed records and allow a new application only through an explicit re-application workflow | Avoid silently overwriting history; requires a clarified uniqueness model (partial unique index or separate application attempts) before SQL |
| Company data | Store company name and optional website as a snapshot on each user's saved job | Avoid shared global company records and cross-user leakage in MVP |
| Skills | User-scoped skill strings; normalize whitespace/case for duplicate detection | Simple MVP; defer global taxonomy |
| Profile flexible data | Relational columns for searchable/filterable values; versioned JSON only for low-query structured sections | Keep common queries maintainable |
| Resume versioning | Immutable binary assets; original and tailored assets remain distinct | Preserve source truth and auditability |
| Resume binary storage | Private local filesystem behind a storage interface; DB stores opaque storage key and metadata | Supports local-first while preserving future adapter path |
| Delete semantics | Explicit per aggregate; hard-delete private records on user-requested deletion where legally/operationally permitted; document backup expiry separately | Avoid accidental blanket cascades and hidden retention |
| Time | Store instants in UTC; retain IANA timezone for user-entered schedules | Correct rendering across timezones/DST |
| PostgreSQL RLS | Defer for MVP; require service-layer owner checks, owner-aware FKs and cross-user tests; revisit if database access patterns justify RLS | RLS with pooled connections requires rigorously safe per-transaction context |
| Local backup | User-configurable private backup directory; PostgreSQL custom-format dump plus coordinated resume-file snapshot; document encryption and retention before release | DB-only backup cannot recover resume binaries |
| Initial schema slice | Identity/profile, saved jobs, applications; defer interview events, resume metadata and AI history until their policy gates are approved | Reduce first migration complexity |

## 3. Important clarification: application history

A simple unique constraint on (user_id, job_id) conflicts with preserving multiple application attempts. Recommended MVP behavior:

- Keep a single application record per user/job while it is the current tracking item.
- Preserve status changes through updated status and timestamps; do not create duplicate applications for each status change.
- If the user reapplies, make that a deliberate action that creates a new attempt or archives the previous attempt.
- Before implementation, choose one physical representation: (A) application_attempts child table, or (B) separate application records with a partial unique index for one current attempt. Recommendation: option A if re-application history is in MVP; otherwise defer reapplication and retain the simpler unique key.

No silent destructive overwrite of application history.

## 4. Ownership and authorization requirements

- Authenticated server-side session is the only source of user identity.
- Every owner-scoped query filters by authenticated user ID.
- Client-supplied user IDs are ignored for authorization.
- Composite owner-aware foreign keys prevent cross-owner references.
- File downloads, exports, AI runs, dashboard aggregates and background tasks must carry owner context.
- UUIDs are not treated as authorization.
- RLS is not a substitute for service authorization or tests.

## 5. Data lifecycle and privacy requirements

Before resume upload or AI history is implemented, define:
- Allowed file types, maximum size, content validation, malware scanning/quarantine and safe download headers.
- Private storage permissions and encryption-at-rest expectations for each deployment mode.
- Retention/deletion behavior for original and tailored resumes, extracted text, AI prompts/results, logs and backups.
- Whether AI prompt/response content is persisted; default recommendation is not to persist raw prompt text or full extracted resume content.
- Account deletion behavior and the maximum period deleted data may remain in backups.
- User-visible disclosure for local storage location and backup responsibility.

## 6. Migration and test gates

First migration must:
- Be reviewed as SQL before merge and tested on a clean PostgreSQL 17 database.
- Include required PK, FK, unique, check constraints and owner-leading indexes.
- Be validated by Flyway in CI; applied migrations are immutable.
- Include deterministic non-sensitive test fixtures only.
- Have an upgrade test from the immediately previous schema once an upgrade path exists.

Required automated tests:
- Cross-user list/read/update/delete/download denial.
- Cross-owner FK insertion rejection.
- Authenticated owner derived from session, not request body.
- Application status transitions and duplicate/re-application behavior.
- Resume metadata cannot reference another user's asset.
- Backup restore verifies database records and associated files/checksums.

## 7. Explicit approvals requested

Please review and approve or amend these decisions:
1. UUID identifiers.
2. User-scoped company snapshots and skill strings.
3. Application-history behavior (simple one-current-record MVP vs explicit re-application history).
4. Immutable resume assets and private filesystem storage.
5. UTC timestamps plus IANA timezone.
6. Defer RLS for MVP while enforcing owner checks, composite FKs and cross-user tests.
7. User-configurable local backup with coordinated DB + file snapshots.
8. Defer resume/AI tables until their privacy and retention gates are resolved.

## 8. Implementation boundary

This PR is documentation only. It does not add migrations, entities, repositories, services, endpoints, dependencies, Docker changes, or UI. Even approval of these decisions is not implementation authorization. A separate explicit instruction is required to begin implementation.
