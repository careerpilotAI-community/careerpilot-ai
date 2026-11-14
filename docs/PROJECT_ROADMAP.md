# CareerPilot AI — Product Roadmap

**Status:** PostgreSQL persistence (Branch 26), identity/session security (Branch 27), core career tracker (Branch 28), and secure resume management (Branch 30) merged; Branch 31 lifecycle/recovery design documented  
**Last updated:** 2026-11-14  
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
- **Lifecycle/recovery after Branch 36:** Branch 37 implements confirmed account deletion with immediate session revocation and verified document cleanup. Branch 38 is now implementing a separate sidecar deletion journal, coordinated local backup/restore scripts, and restore-time replay. Storage reconciliation, key recovery, Ollama integration, AI job matching/suggestions, and integrated local release validation remain future work. Branch 36 implements the approved 24-hour rejected/failed/abandoned-upload cleanup.

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

Branch 31 is a documentation-only design milestone. It defines immutable asset lifecycle and tombstone deletion, retention/account-deletion decisions still requiring product/privacy approval, read-only-first storage reconciliation, coordinated PostgreSQL + filesystem backups, restore-drill acceptance, encryption/key-management requirements, Windows ACL verification, scanner observability, abuse controls, and explicit local readiness gates. See [Resume Lifecycle & Recovery Readiness](architecture/storage/RESUME_LIFECYCLE_RECOVERY_READINESS.md).

**Local deployment status:** synthetic-data build validation is distinct from use with real personal resumes. Real-resume local use is not yet approved until the non-local profile, live ClamAV scanning, persistent private data root, verified Windows ACLs, coordinated backup/restore drill, encryption decision, privacy-safe logging, and authorization tests are implemented and verified. Hosted/public deployment remains not ready. The project owner must be explicitly notified when a later implementation and verification milestone satisfies the real-resume gate.

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
- OS-managed Windows volume encryption is the local default for application-data and backup volumes, with independent recovery-key escrow.
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

## 19. Branch 37 — confirmed account deletion

Branch 37 implements the approved account-deletion workflow:

- The authenticated owner must submit the exact account email and current password, with CSRF protection, before deletion is accepted.
- The account is immediately marked DELETION_PENDING; subsequent password authentication is disabled, all JDBC sessions indexed to the account are revoked, and the current request session is invalidated.
- A durable account-deletion job is queued. The worker removes every attributable resume binary and rejected/abandoned upload object, independently verifies storage absence, and only then deletes the account's resume metadata, tracker records, profile, skills, and user row.
- The worker uses bounded batches, PostgreSQL row locking, stale-lease recovery, exponential retry, and a DEAD_LETTER state after eight failed attempts. A DEAD_LETTER account remains inaccessible and is not reported as deleted.
- Upload-cleanup records now carry an owner ID until completion, then scrub it. Legacy unowned rejected/abandoned upload objects remain covered by the 24-hour retention worker.
- Completed deletion leaves only a non-user-identifying metadata audit event with a 12-month expiry. The durable job scrubs its owner ID on completion.

**Important boundaries:** the endpoint returns accepted when deletion is queued, not when physical deletion is complete. Only a COMPLETED job means the worker verified document absence and removed relational data. DEAD_LETTER jobs require operator investigation. Backup copies are not yet replayed or erased by this branch; Branch 38 adds a protected restore journal and local restore procedure. Real personal-resume use remains NOT SAFE / not approved, and hosted/public deployment remains NOT READY.

## 20. Branch 38 — backup, restore, and deletion propagation

Branch 38 introduces the first local Windows implementation of the approved recovery policy:

- Account and individual resume deletion workers write opaque sidecar tombstones (operation UUID, owner UUID, deletion type, optional asset UUID, schema version, UTC time) to a journal outside PostgreSQL and document storage before removing binaries. The account-deletion service also records the request after commit; unresolved jobs block backup creation.
- Journal writes are append-only, deduplicated by operation UUID, flushed to disk, and protected by a separate lock file. Journal compaction removes entries after 29 days to cover the approved 28-day recovery-point window plus the boundary.
- A local backup script creates coordinated PostgreSQL, document-root, and journal snapshots with a SHA-256 manifest; it requires an explicit maintenance pause and BitLocker-protected source volumes.
- Backup retention is capped at 7 daily and 4 weekly recovery points and 28 days. The script does not configure Task Scheduler or prove a restore.
- An isolated restore script validates the manifest, rejects recovery points older than 28 days, restores the database and files, runs Flyway in non-web mode with cleanup schedulers disabled, and replays the latest independent deletion journal before application access.
- Replay removes attributable quarantine/available files, sessions, resume/upload records, tracker data, profile/skills, and the user row. Replay is idempotent and halts on invalid journal records or integrity/database errors.

See [Local Backup & Restore Runbook](architecture/storage/LOCAL_BACKUP_RESTORE_RUNBOOK.md).

**Not yet verified:** PowerShell scripts have not been exercised on the target Windows host; no actual PostgreSQL + filesystem restore drill has passed; no scheduled tasks, independent off-device copy, recovery-key escrow, or effective NTFS ACL inspection is claimed. A successful CI build is not a restore-drill pass. Synthetic-data local testing remains pending; real personal-resume use remains NOT SAFE / not approved; hosted/public deployment remains NOT READY.


## 21. Branch 39 — local encryption and recovery-key management

Branch 39 is a documentation and operational-security milestone based on the owner-approved Branch 33 decision. It preserves the existing local filesystem adapter and does not introduce application-level encryption, custom cryptography, a new key database, or a new document format.

- The local profile requires OS-managed full-volume encryption (BitLocker where supported) for the application-data and backup volumes. The independent deletion-journal path must also reside on an encrypted volume; it remains separate from document and backup paths.
- BitLocker recovery protectors are generated and managed by Windows. Recovery passwords must be escrowed independently of the encrypted computer and retrievable by the owner/authorized recovery administrator.
- The recovery inventory contains non-secret volume/protector identifiers and verification evidence only. Recovery passwords and key packages must never enter Git, PostgreSQL, document storage, application logs, or an unencrypted backup.
- Rotation means adding and verifying a replacement recovery-password protector before removing an exposed/obsolete protector. This is not a claim of data-encryption-key rotation or re-encryption.
- Windows NTFS ACLs must be inspected on the target host; Java POSIX permission fallbacks do not establish Windows access control.
- Controlled recovery-key validation uses a disposable synthetic-data BitLocker test volume. The full CareerPilot restore drill remains a separate test.

See [File Storage Architecture](architecture/storage/FILE_STORAGE_ARCHITECTURE.md) and [Local Backup & Restore Runbook](architecture/storage/LOCAL_BACKUP_RESTORE_RUNBOOK.md).

**Not yet verified:** target-host Windows edition and BitLocker state; escrow retrieval; recovery-protector rotation; controlled test-volume recovery; effective NTFS ACLs; and the full isolated PostgreSQL + document restore drill. This documentation milestone does not make synthetic-data testing ready or authorize real personal-resume use. Synthetic-data local testing remains pending; real personal-resume use remains NOT SAFE / not approved; hosted/public deployment remains NOT READY.

**Next milestones remain separate:** Branch 40 verifies Windows ACLs, scanner fail-closed behavior, privacy-safe logs, and lifecycle security regressions. Branch 41 performs integrated synthetic-data local validation and records a separate real-resume sign-off only after every required gate is evidenced.

## 22. Branch 40 — security and privacy verification

Branch 40 adds automated regression coverage and an explicit evidence boundary for existing controls. It does not introduce application features or change runtime behavior.

- Adds deterministic tests for ClamAV INSTREAM clean, infected, unknown, and connection-closed verdict handling using a local TCP stub.
- Adds tests proving the local development scanner accepts only the synthetic fixture marker within its inspected prefix.
- Retains the existing PostgreSQL-backed lifecycle and authorization regression suite for CSRF, owner-scoped access, fail-closed scanner outages, upload retention, individual deletion, and account deletion.
- Records the current metadata-only deletion-journal privacy assertions and distinguishes them from a complete application-log review.
- Defines target-host NTFS ACL, BitLocker, recovery-key escrow, live ClamAV, and full restore evidence that cannot be established by CI.

See [Branch 40 Security & Privacy Verification](architecture/security/BRANCH_40_SECURITY_VERIFICATION.md).

**Readiness remains unchanged:** synthetic-data local testing is pending; real personal-resume use remains NOT SAFE / not approved; hosted/public deployment remains NOT READY. Branch 41 is the separate integrated local validation milestone and must not be treated as passed until its host-level and restore evidence is recorded.


## 23. Branch 41 — integrated synthetic-data local validation

Branch 41 defines the operator-run validation procedure for the first integrated local workflow after storage, lifecycle, backup, encryption, and security verification milestones.

- Adds a synthetic-only PDF fixture generator whose PDF comment contains the exact marker accepted by the local development scanner. The generated fixture contains no personal information and is intended only for the `local` Spring profile.
- Documents clean local build/test commands, PostgreSQL and backend startup, synthetic authentication and resume lifecycle checks, cross-user denial, scanner behavior, host-level ACL/encryption/recovery evidence, and an isolated PostgreSQL + filesystem restore with current deletion-journal replay.
- Separates evidence that can be collected in CI from checks requiring the owner's Windows host, Docker daemon, recovery escrow, and operator-run restore drill.
- Does not change application runtime behavior, database schema, frontend behavior, scanner policy, or deployment architecture.

See [Branch 41 Integrated Synthetic-Data Local Validation](implementation/BRANCH_41_SYNTHETIC_LOCAL_VALIDATION.md) and [Synthetic Resume Fixture Generator](../scripts/validation/New-SyntheticResume.ps1).

**Readiness is not granted by this branch or a green CI run.** At creation, target-host and end-to-end operator evidence is NOT VERIFIED. Synthetic-data local validation remains pending until the complete runbook passes. Real personal-resume use remains NOT SAFE / not approved. Hosted/public deployment remains NOT READY.
