# CareerPilot AI — Product Roadmap

**Status:** PostgreSQL persistence (Branch 26), identity/session security (Branch 27), core career tracker (Branch 28), and secure resume management (Branch 30) merged; Branch 31 lifecycle/recovery design documented  
**Last updated:** 2026-11-13  
**Scope:** Product direction and delivery phases; capabilities are plans unless explicitly marked complete.

## 1. Product vision

CareerPilot AI helps job seekers manage a structured, user-controlled career workflow: maintain career information, organize opportunities, prepare truthful application materials, and improve interview readiness. AI assists users; it does not make consequential career decisions invisibly.

## 2. Product principles

- **User control:** Users review and approve applications, messages, profile changes, and submissions.
- **Truthful representation:** Resume tailoring must not invent experience, skills, qualifications, or achievements.
- **Traceability:** Preserve job source URLs and observed dates; distinguish verified facts from generated suggestions.
- **Privacy by design:** Minimize collected data and protect resumes, contact details, and application history.
- **Reliability:** Prefer validated, recoverable workflows over opaque automation.
- **Portability:** One codebase is intended to support local, self-hosted, and cloud deployments.
- **Incremental delivery:** Validate a small useful workflow before adding broad automation.

## 3. Approved architecture direction

See [ARCHITECTURE.md](ARCHITECTURE.md) and the specialist architecture documents.

| Area | Direction |
|---|---|
| Deployment | Local-first initial validation; same codebase intended for local, self-hosted, and cloud |
| Frontend | React 19, Vite, TypeScript (exact manifest versions verified in Branch 22 audit) |
| Backend | Java 21, Spring Boot 3.5.11, Maven (exact manifest versions verified in Branch 22 audit) |
| Database | PostgreSQL 17 |
| Migrations | Flyway |
| Local database | Docker Compose; native PostgreSQL may remain an alternative |
| Tenancy | Multi-user with strict server-side owner-scoped authorization |
| Browser authentication | Server-side sessions with Secure/HttpOnly cookie policy and CSRF protection; mobile deferred |
| Document storage | Local filesystem first, behind an adapter boundary for future private object storage |
| AI | Local Ollama first; configurable cloud provider later, only with disclosure and affirmative consent |
| Career actions | AI output remains a draft; user approval required for consequential actions |

No silent local-to-cloud AI fallback is permitted. The local-first target is not a claim of production security, backup readiness, or public-launch readiness.

## 4. Approved MVP scope — Branch 17

The recommended MVP (Option A) is approved for product planning:

1. **User accounts and profile:** registration, login, logout, and editable career profile.
2. **Job application tracker:** manually create, view, edit, delete, and track applications.
3. **Resume management:** upload and manage multiple resume versions while preserving the original.
4. **Job management:** save job descriptions and associated company/job details.
5. **Application dashboard:** summarize application counts, statuses, and upcoming interview dates.
6. **AI assistance:** local Ollama-based job matching and resume suggestions, with user review and factual grounding.

### Explicitly outside MVP

- Automatic job scraping, crawling, or discovery.
- Automatic job-portal applications or submissions.
- Automatic recruiter emails, messages, or outreach.
- Advanced analytics, forecasting, or multi-agent orchestration.
- Mobile application/client and mobile-specific authentication.
- Cloud AI as an implicit fallback; any future cloud use requires separate provider, privacy, consent, and cost approval.

Detailed requirements, user journeys, feature boundaries, and the conceptual domain model are in [MVP Product Requirements](product/MVP_PRODUCT_REQUIREMENTS.md). The proposed physical schema and persistence gates are in [Database Schema & Persistence Readiness](architecture/DATABASE_SCHEMA_PERSISTENCE_READINESS.md); recommendations are recorded in [Persistence Decision Review](architecture/PERSISTENCE_DECISION_REVIEW.md); Branch 25 consolidates the implementation decision proposal in [Persistence Implementation Decision Finalization](architecture/PERSISTENCE_IMPLEMENTATION_DECISION_FINALIZATION.md). Identity/session security requirements are documented in [Identity, Session & Security Readiness](architecture/IDENTITY_SESSION_SECURITY_READINESS.md). The consolidated delivery sequence and gates are in [MVP Implementation Readiness](implementation/MVP_IMPLEMENTATION_READINESS.md). The repository baseline and build/quality findings are in [Branch 22 Build & Quality Baseline Audit](implementation/BUILD_QUALITY_BASELINE_AUDIT.md).

## 5. Delivery roadmap and gates

| Milestone | Outcome | Status / exit gate |
|---|---|---|
| 0 — Architecture direction | Target architecture and specialist designs | Documented; operational decisions remain open |
| 1 — Application foundation | Java/Spring Boot and React/Vite starter foundation | **Completed:** initial foundation merged in PR #7 |
| 2 — Product definition | MVP scope, requirements, boundaries, user journeys, conceptual domain model | **Scope approved; documentation prepared in Branch 17.** Merge/review does not authorize implementation |
| 3 — Database schema readiness | Physical relational design, multi-user isolation, migration policy, backup/restore and persistence acceptance criteria | **Merged in PR #25; design decisions consolidated in merged PR #26** |
| 4 — Persistence decision review | Resolve schema, ownership, application history, resume storage, retention, RLS and backup defaults | **Merged in PR #26; recommended persistence defaults recorded** |
| 5 — Identity and session security readiness | Account lifecycle, server-side sessions, CSRF/CORS, secrets, authorization and security tests | **Merged in PR #27; security design documented** |
| 6 — MVP implementation readiness | Consolidated delivery slices, verification checklist, quality gates and remaining decisions | **Merged in PR #28; implementation sequence documented** |
| 7 — Build & quality baseline audit | Verify actual repository manifests, starter code, tests, CI and reproducibility gaps | **Completed in Branch 22 audit; documentation only. No application code changed** |
| 8 — Build & quality baseline improvements | Deterministic builds, frontend lockfile, Maven Wrapper, CI quality checks and test conventions | **Completed and merged in PR #30 (merge commit `37f0f6d`); successful CI run recorded in PR #30. Branch protection/required-check enforcement remains unverified** |
| 8A — Post-merge readiness reconciliation | Align roadmap and readiness documents with merged repository state; preserve Branch 22 as historical evidence | **Completed and merged in PR #31** |
| 8B — Persistence implementation decision finalization | Reconcile persistence recommendations and implementation decisions | **Completed and merged in PR #32** |
| 9 — Persistence implementation | PostgreSQL 17, Flyway, Docker Compose, repository integration tests | **Completed and merged in PR #33** |
| 10 — Identity and session security implementation | Registration, login/logout, JDBC sessions, CSRF/CORS, session expiry and authentication UI | **Completed and merged in PR #34** |
| 11 — Core tracker | Profile, skills, saved jobs, applications, dashboard and owner isolation | **Completed and merged in PR #35; backend and frontend CI passed** |
| 12 — Resume/document management | Secure upload, versioning, storage adapter and scanning | **Branch 30 / PR #38 merged. Retention/deletion and recovery controls remain separate implementation milestones** |
| 13 — Local AI assistance | Ollama integration, job matching and resume suggestions | Future milestone; requires model/hardware baseline, grounding, privacy, safety and quota decisions |
| 14 — Integrated local MVP validation | End-to-end workflows, recovery, backup/restore and release checks | Future milestone after tracker, resume and AI slices |
| 15 — Later deployment modes | Self-hosted and cloud deployment guides/validation | Separate operational, security, privacy and cost review |

Milestones are planning gates, not delivery-date commitments. No feature is complete merely because its requirements or architecture document is merged.

## 6. Success measures

Initially measure only what is necessary and avoid collecting sensitive document content for analytics:

- Activation: users who complete a profile and save their first job.
- Workflow completion: users who progress from a saved job to a reviewed application draft.
- Usefulness: user-rated relevance of job matches and resume suggestions.
- Trust: unsupported claims in generated application content; target zero.
- Reliability: build success, API error rate, and recoverable workflow failures.
- Privacy: no secrets or sensitive resume/application content in logs.

Set numeric targets after a baseline exists.

## 7. Key risks and mitigations

| Risk | Mitigation |
|---|---|
| Cross-user data exposure | Server-side owner checks, owner-aware data model, and cross-user authorization tests |
| Unsafe browser sessions/CSRF | Secure cookie profiles, CSRF protection, session lifecycle controls, and tests |
| Fabricated resume claims | Ground AI suggestions in user-approved facts; require review |
| Exposure of personal documents | File validation, private storage, authorization, quotas, retention and recovery controls |
| Local AI resource constraints | Document supported model/hardware baseline and fail clearly when unavailable |
| Accidental consequential action | No automated submission or outreach in MVP; explicit user confirmation for later actions |
| Scope expansion | Enforce MVP exclusions and milestone approval gates |

## 8. Current implementation status

The repository's current implementation is materially beyond the historical Branch 22 starter audit:

- **Build baseline:** Branch 23 / PR #30 added deterministic Maven/npm builds, frontend lint/format/test tooling and CI quality gates. PR #30 records a successful permanent CI run; branch protection and required-check enforcement remain unverified.
- **Persistence:** Branch 26 / PR #33 added PostgreSQL 17.6 Compose support, Flyway V1/V2 migrations, JPA entities/repositories, owner-aware constraints and PostgreSQL Testcontainers coverage.
- **Identity/security:** Branch 27 / PR #34 added Argon2id password hashing, local-profile registration, server-side JDBC sessions, CSRF, explicit CORS, session rotation and expiry, and authentication UI. Email verification, password recovery, rate limiting and public registration hardening remain out of scope.
- **Core tracker:** Branch 28 / PR #35 is merged. It implements profile and skills management, owner-scoped job and application workflows, dashboard summaries, responsive React workspace integration and cross-user integration coverage. Backend Maven verification and frontend install/lint/format/tests/build passed in CI run `43177048994`.
- **Resume management:** Branch 30 / PR #38 is merged. It implements PDF/DOCX upload, private local filesystem storage, ClamAV scanning, quotas, owner-scoped metadata/list/download, and immutable original/tailored assets. It remains a local-first implementation, not hosted-production ready.
- **Lifecycle/recovery:** Branch 31 defines deletion, retention, reconciliation, coordinated backup/restore, encryption/key management, Windows ACL, scanner operations, and explicit local readiness gates. Branch 33 approved the policy defaults. Branch 34 adds immediate owner-scoped asset access revocation and a durable cleanup queue. Branch 35 adds verified physical cleanup, retry/recovery, and metadata scrubbing for explicit user deletions.
- **Not yet implemented:** account deletion, storage reconciliation, backup/restore automation and restore drills, encryption/key management, Ollama integration, AI job matching/suggestions, and integrated local release validation. Branch 36 implements the approved 24-hour rejected/failed/abandoned-upload cleanup.

The V1 database constraint defines application statuses as `SAVED`, `PREPARING`, `APPLIED`, `INTERVIEWING`, `OFFER`, `REJECTED`, `WITHDRAWN`, and `CLOSED`; application APIs and UI must use this exact vocabulary. The schema currently supports one current application per user/job; re-application history remains deferred.

Branch 22 remains a historical read-only audit of the pre-Branch-23 tree. Its findings must not be repeated as current-state claims. PR #30's recorded CI run is historical evidence, not a claim that Branch 28 has passed CI. Branch protection and required-check enforcement remain unverified through the available repository connection.

## 9. Branch 30 — Resume management implementation

Branch 30 implements the first local-first slice:
- PostgreSQL migration V3 stores owner-scoped metadata and opaque storage keys; binary files remain outside PostgreSQL.
- Private local filesystem storage under an absolute configurable data root, separate quarantine and available areas, generated UUID keys, restrictive POSIX permissions where supported, and atomic promotion.
- PDF and DOCX only; PDF parsing with PDFBox; DOCX ZIP/XML structural validation with external entities disabled, entry and expanded-size bounds; 10 MiB per-file ceiling.
- SHA-256 checksum, immutable original/tailored asset rows, owner-scoped list/download APIs, attachment-only downloads, nosniff, and private/no-store caching.
- ClamAV INSTREAM scanning is the default and any scanner error/unavailable/unknown verdict rejects the upload. The local profile bypass is restricted to synthetic fixtures containing the test marker; it is not suitable for real resumes or hosted deployments.
- Configurable defaults: 100 MiB per user and 2 GiB per deployment. PostgreSQL advisory transaction locking serializes quota checks during upload.
- The frontend supports upload, list, and authenticated download; no delete, replace-in-place, AI parsing, or automatic resume generation is included.

Operational limitations remain: no backup/restore automation, encryption-at-rest/key management, retention/account-deletion workflow, orphan reconciliation job, upload rate limiting, or hosted deployment readiness. Local storage permissions depend on the host OS; Windows ACLs must be configured on the private data root. Keep this feature local-only until those deployment controls are separately resolved.

## 10. Branch 31 — Resume lifecycle and recovery readiness

Branch 31 is a documentation-only design milestone. It defines immutable asset lifecycle and tombstone deletion, retention/account-deletion decisions still requiring product/privacy approval, read-only-first storage reconciliation, coordinated PostgreSQL + filesystem backups, restore-drill acceptance, encryption/key-management requirements, Windows ACL verification, scanner observability, abuse controls, and operational runbooks. See [Resume Lifecycle & Recovery Readiness](architecture/storage/RESUME_LIFECYCLE_RECOVERY_READINESS.md).

**Local deployment status:** synthetic-data build validation is distinct from use with real personal resumes. Real-resume local use is not yet approved until the non-local profile, live ClamAV scanning, persistent private data root, verified Windows ACLs, coordinated backup/restore drill, encryption decision, privacy-safe logging, and authorization tests are implemented and verified. Hosted/public deployment remains not ready. The project owner must be explicitly notified when a later implementation and verification milestone satisfies the local real-resume gate.

## 11. Change control

Record material scope or architecture changes in pull requests, explain the reason, and update this roadmap and affected specialist documents. Keep approved decisions distinct from recommendations and unresolved proposals.


## 14. Branch 32 — resume retention, deletion, and security policy

Branch 32 is a documentation-only policy milestone. See [Resume Retention, Deletion & Security Policy](architecture/storage/RESUME_RETENTION_DELETION_SECURITY_POLICY.md). It documented proposed defaults for active/rejected asset retention, user and account deletion, backup expiry/deletion propagation, Windows volume encryption, key recovery, and implementation acceptance criteria. The owner decisions were subsequently approved and recorded in Branch 33.

No retention/deletion behavior, account deletion, backup automation, encryption, key management, or runtime controls are implemented by Branch 32 or Branch 33. The real-resume local readiness gate from Branch 31 remains closed; hosted/public deployment remains not ready.

## 15. Branch 33 — approved resume retention and security policy

Branch 33 records the project owner's approval (2026-11-13) of all seven policy defaults in the specialist policy document:

- Active original/tailored resumes remain until user/account deletion; tailored assets are independently deletable.
- Rejected, failed, and abandoned uploads are purged within 24 hours.
- Individual deletion has no grace period: access is revoked immediately and physical cleanup is asynchronous.
- Account deletion requires confirmation, has no user-facing grace period, and deletes application/job history and attributable personal data, subject only to lawful audit exceptions.
- Backups retain 7 daily and 4 weekly recovery points, with a maximum 28-day deletion propagation/backup-erasure window and mandatory restore-time deletion replay.
- OS-managed Windows volume encryption is the local default for application data and backup volumes, with independent recovery-key escrow.
- Minimal metadata-only security audit events are retained for 12 months; legal holds require documented lawful basis and controls.

This is still a documentation-only approval milestone. It does not implement or verify any runtime control. Reconciled implementation sequence: Branch 34 individual deletion foundation; Branch 35 verified physical deletion worker; Branch 36 rejected/failed/abandoned-upload retention cleanup; Branch 37 account deletion; Branch 38 backup/restore and deletion propagation; Branch 39 local encryption/key recovery; Branch 40 security/privacy verification; Branch 41 integrated local readiness validation. Each requires its own focused PR and approval.

**Readiness:** synthetic-data local testing is not yet declared ready; real personal-resume use remains NOT SAFE / not approved; hosted/public deployment remains NOT READY. The synthetic-data and real-resume gates must be assessed and reported separately.


## 16. Branch 34 — resume deletion foundation

Branch 34 implements the first narrow runtime slice of the owner-approved deletion policy:
- Flyway V4 permits the DELETING asset state and records the request timestamp/reason.
- Owner-authenticated, CSRF-protected DELETE /api/resumes/{id} atomically tombstones an AVAILABLE asset and creates a durable PENDING cleanup job.
- Concurrent/repeated requests are serialized and idempotent; cross-user IDs return not found.
- List and download endpoints immediately stop exposing a DELETING asset.

**Important boundary:** Branch 34 does not delete the filesystem binary. The cleanup worker, retry/backoff, finalization and status monitoring are Branch 35. A PENDING job is not proof of physical deletion. Account deletion, retention scheduling, backup propagation and encryption remain future milestones. Real personal-resume use remains NOT SAFE; hosted/public deployment remains NOT READY.


## 17. Branch 35 — verified physical resume deletion

Branch 35 implements the durable cleanup worker for explicit individual resume deletion:
- Claims due PENDING jobs using row-level locking; stale PROCESSING claims can be recovered after five minutes.
- Removes both quarantine and available binaries idempotently, then independently verifies storage absence.
- Finalizes only after absence verification: asset state becomes DELETED, document-identifying metadata is scrubbed, and the durable job becomes COMPLETED.
- Retries failures with bounded exponential backoff; after eight attempts jobs enter DEAD_LETTER and are not represented as successful deletion.
- Adds PostgreSQL-backed integration coverage for physical removal, absence verification, metadata scrubbing, and job completion.

The worker does not implement the approved 24-hour rejected/abandoned-upload purge, account deletion, backup propagation/replay, storage reconciliation, or operator dead-letter tooling. A DEAD_LETTER item remains unresolved and requires investigation. This milestone does not close the real-personal-resume or hosted-deployment readiness gates.


## 18. Branch 36 — 24-hour rejected and abandoned upload retention

Branch 36 implements the owner-approved retention rule for upload attempts that do not become active resume assets:

- A durable cleanup record is committed before the application writes upload bytes to quarantine.
- A successful upload marks its cleanup record COMPLETED in the same transaction that persists the AVAILABLE asset.
- Rejected/failed attempts expire 24 hours after the terminal result; interrupted attempts expire 24 hours after their recorded start.
- A bounded scheduled worker claims only expired records, reclaims stale PROCESSING leases, removes both quarantine and available copies, and independently verifies absence before completion.
- Retries use bounded exponential backoff; after eight attempts a record enters DEAD_LETTER and remains an unresolved cleanup requiring operator investigation.
- Cleanup records contain only opaque storage keys and bounded reason/error codes; they do not retain filenames or document content.

**Scope boundary:** active original/tailored assets are not expired by this worker. Individual user deletion remains Branch 34/35. Account deletion, backup deletion replay, storage reconciliation, and operator dead-letter tooling remain separate milestones. This branch does not close the real-personal-resume or hosted-deployment readiness gates.
