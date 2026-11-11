# Branch 16 — Local-First Implementation Readiness

**Status:** Proposed readiness milestone; documentation only  
**Date:** 2026-11-12  
**Parent baseline:** Branch 15 / PR #22 — Architecture Consolidation Review  
**Decision recorded:** Local-first is the first end-to-end implementation and validation target.

## 1. Purpose and boundary

This milestone translates the merged architecture into a practical, testable local-first delivery plan. It closes the deployment-target choice and defines the next decisions, implementation order, acceptance criteria, and explicit approval gates.

This document does **not** authorize application implementation. No application code, dependencies, database migrations, authentication endpoints, storage adapters, AI integrations, or deployment infrastructure are to be added under this milestone.

## 2. Approved direction

The following direction is approved for the first validation target:

- **Developer environment:** Windows local development.
- **Frontend:** existing React 19 + Vite + TypeScript foundation; exact versions must be verified from repository manifests before implementation.
- **Backend:** existing Java 21 + Spring Boot 3.5.x + Maven foundation; exact versions must be verified from repository manifests before implementation.
- **Database:** PostgreSQL 17, run locally through Docker Compose.
- **Schema migration:** Flyway.
- **Document storage:** local filesystem for the first local milestone, behind a storage boundary that does not prevent a later private-object-storage adapter.
- **AI:** Ollama local provider for the first local AI integration milestone.
- **Authentication:** server-side browser sessions with Secure/HttpOnly cookie policy appropriate to the local profile; CSRF protection remains required. Mobile authentication is deferred.
- **Product safety:** AI-generated content remains a draft; the user approves consequential actions.
- **Privacy invariant:** no silent local-to-cloud AI fallback. Cloud processing of personal documents or profile data requires clear disclosure and affirmative consent.
- **Future deployment modes:** self-hosted and cloud remain architectural goals, but are not acceptance claims for this first milestone.

“Local-first” describes the first development and validation target. It does not mean production-grade security, backup, availability, or public-launch readiness.

## 3. First implementation milestone proposed

### Milestone 1A — Local persistence foundation

Implement only after the user separately approves implementation:

1. Verify the current frontend/backend manifests, repository layout, Java/Maven versions, and existing CI checks.
2. Provide a reproducible local PostgreSQL 17 service using Docker Compose and environment-based configuration.
3. Add Flyway with a pinned version compatible with the verified Spring Boot/JDBC stack.
4. Define and migrate only the minimum persistence schema needed for the first approved product slice.
5. Add repository-level integration tests against PostgreSQL (not H2 as a substitute for PostgreSQL behavior).
6. Document startup, shutdown, clean reset for development, migration behavior, and troubleshooting.

**Proposed exclusion:** do not bundle authentication, resume uploads, AI provider integration, cloud deployment, or broad job-search automation into Milestone 1A. Those require their own approved gates.

### Candidate next product slice after persistence

The first user-facing slice should be selected before schema implementation. A narrow candidate is a signed-in user's own profile and manually managed job-application records. This is a proposal only; it is not approved scope and must not be used to pre-empt the identity/security gate.

## 4. Readiness decisions still required

These decisions must be resolved before the relevant code is written. Proposed defaults below are recommendations, not approved policy.

| Decision area | Recommendation for discussion | Required before |
|---|---|---|
| MVP slice | Start with persistence, then a minimal profile + manual job tracker | Schema and API scope |
| Local prerequisites | Windows 10/11, supported Docker Desktop + Compose, JDK 21, Maven wrapper; verify against actual repo | Local setup implementation |
| Configuration | Environment variables; checked-in examples contain placeholders only; secrets excluded from Git | Any integration |
| Database schema | Agree entities, ownership model, uniqueness, indexes, deletion semantics and timestamps | First migration |
| Migration policy | Forward-fix migrations; no automatic destructive rollback in shared environments | Flyway implementation |
| Backup/recovery | Define local backup/restore procedure and test database + document restore together | Persistence readiness claim |
| Identity | Decide registration mode, email verification, password hashing policy, login throttling, session store/timeouts, CSRF and local cookie profile | Any user/account endpoint |
| Authorization | Enforce owner-scoped access in service layer and database query patterns; cross-user tests block release | Any user-owned record |
| Documents | Defer uploads in 1A; later decide file types, size/quotas, malware scanning, storage encryption, retention and restore | Upload implementation |
| AI | Defer AI in 1A; later select Ollama model/hardware baseline, cloud provider terms, consent UX, model allowlist and quotas | AI implementation |
| CI/security | Define required build, test, dependency and secret-scanning checks based on available repository workflows | Merge/release gates |
| Release boundary | Separate local developer success from public-launch and hosted-deployment readiness | Any readiness claim |

## 5. Milestone 1A acceptance criteria

Milestone 1A is complete only when all applicable criteria are demonstrated:

- [ ] A clean Windows development setup can follow the documented prerequisites.
- [ ] PostgreSQL 17 starts through the documented Compose workflow and exposes no unnecessary public interface.
- [ ] Application configuration uses environment variables; no credentials or personal data are committed.
- [ ] Backend connects to PostgreSQL using the documented local profile.
- [ ] Flyway applies versioned migrations on a fresh database and records migration history.
- [ ] A second startup does not reapply already successful migrations.
- [ ] Invalid migration state or database unavailability produces a clear, safe failure.
- [ ] Integration tests execute against PostgreSQL and cover repository persistence behavior.
- [ ] Tests verify uniqueness, constraints, and relevant transaction behavior for the implemented schema.
- [ ] Backup and restore instructions are tested for the database data included in this milestone.
- [ ] CI runs the agreed build and test checks.
- [ ] Documentation clearly states what is not yet implemented.

No criterion may be marked complete solely because a document or pull request is merged.

## 6. Security and privacy invariants for all later milestones

1. Every user-owned record must be authorized against the authenticated user on the server; client-supplied owner IDs are never trusted.
2. Cross-user access tests must cover read, update, delete, list, and indirect/nested resource access.
3. Browser session cookies must use Secure in HTTPS deployments, HttpOnly, an appropriate SameSite policy, and narrow path/domain scope.
4. State-changing browser requests must be protected against CSRF.
5. Secrets must not be committed, logged, exposed to the frontend, or included in sample configuration.
6. Uploads must not be treated as safe based only on extension or client-provided MIME type.
7. Cloud AI must never be invoked as an implicit fallback from local mode.
8. Personal data sent to a cloud provider requires an explicit, informed user action and a documented provider/data-retention review.
9. AI output cannot submit applications, contact recruiters, or perform other consequential actions without user approval.
10. Local development success must not be represented as public-launch security approval.

## 7. Proposed implementation sequence

1. Review and approve this readiness document and the remaining decision table.
2. Verify the actual repository manifests, current foundation, CI workflows, and architecture-document consistency.
3. Agree the first product slice and final persistence schema.
4. Approve Milestone 1A implementation explicitly.
5. Implement PostgreSQL + Flyway + Compose + integration tests in a separate implementation branch/PR.
6. Review test evidence and local setup before marking persistence ready.
7. Resolve identity/session/CSRF/authorization decisions before implementing user accounts or user-owned APIs.
8. Implement the selected profile/job-tracker slice only after identity and ownership gates pass.
9. Plan document storage and AI as separate milestones with their own privacy and operational approvals.
10. Validate local end-to-end behavior before any self-hosted or cloud deployment claim.

## 8. Approval record

- [x] First deployment target: **Local-first**.
- [ ] MVP user-facing slice approved.
- [ ] Local development prerequisites and supported Windows/Docker baseline verified.
- [ ] Database schema and migration policy approved.
- [ ] Backup/restore expectations approved.
- [ ] Identity, session, CSRF, password and account lifecycle decisions approved.
- [ ] Authorization and cross-user test plan approved.
- [ ] CI/security gates approved.
- [ ] Milestone 1A acceptance criteria approved.
- [ ] Explicit authorization to begin Milestone 1A implementation received.

**Gate:** Branch 16 is documentation-only. The local-first target is approved, but implementation must remain paused until the outstanding readiness decisions are reviewed and the user explicitly authorizes Milestone 1A.
