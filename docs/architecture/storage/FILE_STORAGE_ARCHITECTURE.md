# CareerPilot AI — File Storage Architecture

**Status:** Branch 29 decisions approved and documented; documentation only. No upload, download, scanner, storage adapter, bucket, or filesystem implementation is created by this milestone.  
**Last updated:** 2026-11-12  
**Related:** [Target Architecture](../../ARCHITECTURE.md) · [Database Architecture](../database/DATABASE_ARCHITECTURE.md) · [System Design Diagrams](../SYSTEM_DESIGN_DIAGRAMS.md)

## 1. Goals and approved direction

CareerPilot AI stores private user documents such as original resumes, tailored resumes, cover letters, and other application artifacts. The storage design must work in local, self-hosted, and cloud deployments without changing product-service code.

| Concern | Proposed decision |
|---|---|
| Storage abstraction | Application-owned `DocumentStorage` interface; no feature code calls filesystem or cloud SDKs directly |
| Local mode | Local filesystem adapter using a configured private data root |
| Hosted mode | S3-compatible object-storage adapter behind the same interface |
| Metadata | PostgreSQL 17 stores owner, opaque storage key, checksum, media type, size, state, and timestamps; never store document binaries by default |
| Access | Backend-authorized upload/download/delete; no public objects or permanent public URLs |
| Object naming | Server-generated opaque identifiers; never use the original filename as a path or object key |
| Lifecycle | Quarantine → validation/scanning → available; failed or rejected objects remain inaccessible and are cleaned up |
| Deployment selection | Explicit server-side deployment configuration; no client-controlled storage selection or silent fallback |

The architecture remains hybrid as approved. A deployment selects one configured primary adapter at startup. Storage migration between adapters is an explicit administrative operation, not automatic failover.

## 2. Logical components and upload lifecycle

1. Browser sends a multipart upload to the backend over HTTPS.
2. Backend authenticates the session, applies CSRF protection, authorizes the target user-owned resource, and enforces request and file limits.
3. Backend streams bytes to a private quarantine location using a generated opaque key. Do not buffer unbounded files in memory.
4. Validation checks extension allowlist, declared media type, file signature/content, size, and supported format. Treat client-provided MIME type and filename as untrusted hints.
5. Malware scanning runs through a pluggable scanner boundary. A scanner error, timeout, unavailable scanner, or indeterminate result is fail-closed for hosted production: the file remains quarantined and cannot be downloaded or processed by AI.
6. On successful validation and scan, the backend promotes the object to its final private key/location and records status AVAILABLE in PostgreSQL.
7. API returns only safe metadata and an application resource identifier, never a filesystem path, bucket credential, or permanent object URL.

A database row in PENDING, QUARANTINED, SCANNING, REJECTED, DELETING, or FAILED state is never downloadable. Only AVAILABLE records can be served, and every read still requires owner authorization.

## 3. Storage interface and adapter contract

Proposed conceptual operations:

- `put(key, stream, metadata)` — write a new object without overwriting an existing key.
- `open(key)` — stream object bytes to an authorized backend response.
- `delete(key)` — idempotently delete an object.
- `exists(key)` / `head(key)` — verify object presence and metadata.
- `copy(sourceKey, destinationKey)` — optional promotion operation where supported; otherwise use safe stream-copy.
- `health()` — report adapter readiness without exposing credentials or private paths.

The contract must define streaming behavior, maximum size, content length handling, checksum calculation, timeouts, retry semantics, and exception mapping. It must not return vendor SDK types to application services.

### Adapter responsibilities

**Local filesystem adapter**
- Store beneath one configured absolute data root outside the web root, source tree, and public static assets.
- Resolve and normalize paths under the configured root; reject traversal, symlink escape, and unexpected file types.
- Use restrictive directory/file permissions and atomic temporary-file promotion where supported.
- Do not expose the root path to API responses or logs.
- Require a persistent mounted volume for containerized hosted deployments; container ephemeral storage is not durable storage.

**Object-storage adapter**
- Use a private bucket/container with public access and anonymous listing disabled.
- Use a dedicated least-privilege service identity scoped to the required bucket/prefix and operations.
- Require TLS; enable provider-side encryption at rest and versioning where available and appropriate.
- Keep credentials in server-side secret management, never in frontend bundles, repository files, or database rows.
- Keep object keys opaque and separate from original filenames.
- Do not grant browser access to bucket credentials. Backend streaming is the default. If short-lived presigned URLs are considered later for scale, require a separate security review, short expiry, exact-object scope, and authorization before URL issuance.

## 4. File acceptance and content safety

### Approved initial policy (Branch 29)

The following defaults were explicitly approved for the first implementation slice. They define product/design constraints; they do not authorize upload implementation.

- Allowed user-upload formats for the first release: PDF and DOCX.
- Legacy DOC, macro-enabled Office formats, executable files, archives, HTML, SVG, scripts, and arbitrary binary uploads are rejected unless a future reviewed use case requires them.
- Maximum size: 10 MiB per file. Enforce the limit at ingress/reverse proxy, framework multipart parser, application service, and storage adapter boundaries; configuration may tighten the limit but must not silently raise the approved product ceiling.
- Per-user storage quota and total deployment quota remain open; do not assume unlimited storage.
- Validate extension, detected file signature, parsed structure, and actual content. Never trust filename extension or Content-Type alone.
- Reject malformed, encrypted/password-protected, corrupt, polyglot, or unsupported files unless a reviewed workflow explicitly supports them.
- Normalize and length-limit display filenames; store them as metadata only. Escape them on display and use safe Content-Disposition handling on download.
- Calculate a cryptographic checksum (SHA-256 proposed) while streaming. A checksum detects corruption; it does not prove a file is safe.
- Avoid parsing or rendering uploaded documents in a privileged process. Use patched libraries, resource/time limits, and isolated processing where practical.

The PDF/DOCX allowlist and 10 MiB per-file ceiling are approved initial product defaults. Any increase or additional format requires a separate review and documented rationale.

## 5. Malware scanning and quarantine

A scanner adapter should permit a local scanner (for example, ClamAV) or a hosted scanning service without coupling application services to a vendor.

- Uploads are quarantined and inaccessible until validation and scanning succeed.
- Scan the actual bytes, not only the filename or MIME header.
- Record scan state, scanner engine/version, scan timestamp, and a safe result code; do not log file contents.
- Scanner unavailable, timeout, error, or unknown verdict means remain quarantined; do not silently mark clean.
- Re-scan files when policy or scanner signatures materially change, according to an operational policy.
- Define an operator-only process to inspect/retry/quarantine/delete files; operators must not casually access user document content.
- Local single-user development may provide an explicitly labelled scanner-disabled development profile using synthetic files only. This profile must not be available to hosted production or mistaken for a secure deployment.
- AI extraction, preview generation, indexing, or document conversion must occur only after the file is accepted and scanned.

## 6. Authorization and privacy controls

- Derive user identity from the authenticated server-side session. Never accept effective owner ID from the browser.
- Every upload, metadata read, download, replacement, and delete is authorized against the owning user and the associated resume/job/application.
- Use the trusted owner ID plus document ID in repository queries. Do not load by document ID alone and authorize later.
- Verify parent-child ownership before linking a document to a resume version or generated artifact.
- Random opaque IDs reduce guessability but are not authorization.
- Return consistent not-found/denied behavior for cross-user requests; test IDOR and cross-tenant attempts.
- Protect upload endpoints with CSRF tokens, same-site cookie policy, origin checks where appropriate, and rate/size limits.
- Use HTTPS for all remote traffic. Encrypt storage and backups at rest; document key ownership, rotation, and recovery.
- Never log document bytes, resume text, session cookies, secrets, raw storage keys, or full sensitive filenames.
- Keep audit events for upload, successful/failed scan, download, replacement, deletion, and administrative recovery without recording document contents.

## 7. Metadata and database lifecycle

PostgreSQL stores metadata, not file bytes. The conceptual `document_asset` row should include:

- `id` — opaque UUID primary key;
- `user_id` — non-null owner, protected by owner-aware constraints;
- `storage_provider` — logical adapter identifier, not credentials;
- `storage_key` — opaque internal key, never exposed to clients;
- `original_filename` — sanitized display metadata only;
- `media_type` — server-detected type;
- `size_bytes` — verified byte count;
- `checksum_sha256` — computed during upload;
- `status` — constrained lifecycle state;
- `scan_status`, `scanned_at`, and safe scanner result metadata;
- `created_at`, `updated_at`, and optional `deleted_at`.

Do not put secrets, signed URLs, credentials, or absolute local paths in this table. Use owner-aware foreign keys to associate resume versions and generated artifacts.

### Cross-system consistency

PostgreSQL transactions cannot atomically commit an object-store write. Use an explicit state machine and compensating cleanup:

- Create a pending metadata record and opaque key.
- Stream to quarantine; verify byte count and checksum.
- Scan and promote the object.
- Mark metadata AVAILABLE only after the final object is verified.
- On failure, mark FAILED/REJECTED and schedule idempotent cleanup.
- Run a reconciliation task to detect orphaned objects, missing objects, and stale pending rows.

Deletion should first make the record inaccessible (DELETING/tombstone), then remove the object, then finalize metadata deletion or retention state. Retries must be safe. Never leave an object public while cleanup is pending.

## 8. Replacement, versioning, and downloads

- Treat resume versions as immutable. A new upload or tailored resume creates a new document asset/version; do not overwrite an original resume in place.
- Replacements create a new asset, verify it fully, then update the user-visible pointer in a controlled transaction. Preserve the prior version according to retention policy.
- Stream downloads through the backend by default after checking session, owner, asset state, and parent relationship.
- Set safe Content-Type, Content-Disposition attachment by default, X-Content-Type-Options: nosniff, and cache controls that prevent shared/public caching of private documents.
- Do not inline-render untrusted active content from the application's authenticated origin.
- Support range requests only if required and safely authorized; do not let them bypass ownership or lifecycle checks.

## 9. Backup, retention, and migration

- Back up document storage independently from PostgreSQL, but coordinate restore points and procedures. Database backups alone do not contain document bytes.
- For local mode, document the data-root backup and restore process; do not rely on a container writable layer.
- For object storage, evaluate versioning, lifecycle policies, replication, and provider backup guarantees; versioning is not a substitute for independent recovery.
- Encrypt backups, restrict access, keep protected off-site copies where appropriate, and periodically perform restore drills.
- A restore test must verify metadata-to-object consistency, checksums, ownership, and application download behavior.
- Define retention for originals, tailored copies, rejected/quarantined uploads, deleted assets, and backup copies. Align cleanup with account deletion and privacy requirements.
- For adapter migration, use a controlled copy-and-verify process: inventory metadata, copy objects, verify checksums, switch configuration/pointers, monitor, then retire old objects after a rollback window. Never delete source objects before verification and a successful cutover.

## 10. Abuse prevention and operations

- Apply per-user and per-IP upload rate limits and concurrency limits.
- Enforce file size at ingress, framework, service, and adapter boundaries.
- Protect against decompression bombs by rejecting archives in the MVP; bound CPU, memory, parser recursion, and processing time.
- Monitor storage capacity, failed uploads, scan backlog, scanner health, orphan cleanup, object-not-found errors, and backup age.
- Alert on unusual download volume and repeated rejected uploads without logging personal document content.
- Ensure logs and metrics contain no private filenames, resume contents, credentials, or signed URLs.
- Document incident response for leaked object credentials, public bucket misconfiguration, malware detection after upload, and accidental deletion.

## 11. Branch 30 implementation status

Branch 30 adds the first local filesystem adapter and a synchronous upload path. It stores PDF/DOCX bytes outside PostgreSQL and outside the repository, validates actual PDF structure with PDFBox, checks DOCX package parts/XML with external entities disabled and bounded expansion, computes SHA-256, and exposes only owner-authorized metadata and streaming downloads. ClamAV INSTREAM is the default scanner; errors fail closed. The local Spring profile includes a scanner bypass only for synthetic fixtures carrying an explicit test marker. Never use that profile for real personal documents.

The implementation currently enforces a 10 MiB file ceiling, 100 MiB per-user quota, and 2 GiB deployment quota. Quota checks are serialized with a PostgreSQL advisory transaction lock. Originals and tailored assets are separate immutable records; no in-place replacement or delete endpoint is exposed.

This is not hosted-production readiness. Backups/restores, encryption and key management, retention/account deletion, storage reconciliation, rate/concurrency controls, operational monitoring, and Windows ACL hardening remain open. The local filesystem root must be backed up and access-controlled by the operator.

## 12. Branch 29 decision record and remaining gates

### Approved decisions

| Area | Branch 29 decision |
|---|---|
| Initial accepted formats | PDF and DOCX only; reject legacy DOC, macro-enabled formats, archives, executables, HTML, SVG, scripts and arbitrary binaries |
| Maximum file size | 10 MiB per file; enforce at every ingress and storage boundary |
| Initial storage | Private local filesystem beneath a configured absolute data root outside the repository, web root and public assets |
| Future storage | S3-compatible object-storage adapter behind the same application-owned interface; provider selection is deployment configuration |
| File identity | Server-generated opaque UUID identifiers and storage keys; never derive paths/keys from user filenames |
| Database | PostgreSQL stores metadata and opaque keys only; document bytes remain outside the database |
| Download | Backend-authorized streaming; no public URLs, browser bucket credentials or permanent signed URLs |
| Malware scanning | Scanner abstraction; quarantine until validation and a clean scan; hosted deployments fail closed if scanner is unavailable or verdict is indeterminate |
| Local development | A clearly isolated scanner-disabled profile may be used only for local single-user development with synthetic test files; never for hosted deployment or real sensitive documents |
| Versioning | Original assets are immutable; tailored outputs and subsequent uploads are separate immutable assets/versions |
| Ownership | Derive owner from authenticated server-side session and enforce owner checks for every metadata and binary operation |

### Lifecycle and relationship rules

- A logical resume is a user-owned collection/label. It may reference one immutable original asset and multiple tailored or later-version assets.
- Each binary asset has its own opaque ID, owner, resume relationship, asset kind (`ORIGINAL` or `TAILORED`), sanitized display filename, detected media type, verified size, SHA-256 checksum, storage adapter/key, lifecycle state, scan result metadata and timestamps.
- Upload lifecycle: `PENDING` → `QUARANTINED` → `SCANNING` → `AVAILABLE`; validation/scan failures become `REJECTED` or `FAILED`. Deletion transitions through `DELETING` before storage cleanup. Only `AVAILABLE` assets may be downloaded or supplied to AI processing.
- Replacement never overwrites an existing object. A new asset is fully validated, scanned and verified before the user-visible current-version pointer changes. Previous versions remain intact pending the retention policy.
- All file operations require authenticated owner scope and parent-child ownership validation. UUID unpredictability is not authorization.
- Cross-system consistency uses idempotent cleanup and reconciliation for stale pending rows, orphaned objects and missing objects. A database transaction is not assumed to atomically commit filesystem/object-store writes.
- Downloads stream through the backend after authorization and state checks, with attachment disposition by default, `X-Content-Type-Options: nosniff`, and private/no-store cache controls.

### Decisions still open before implementation/release

1. Per-user and deployment-wide storage quotas and upload rate/concurrency limits.
2. Concrete scanner selection, deployment packaging, signature-update operations and scanner runbook.
3. Local data-root default, permission model, Docker volume mapping and user-visible location/disclosure.
4. Encryption-at-rest and key ownership/rotation/recovery requirements for local storage and backups.
5. Retention periods for originals, tailored assets, rejected/quarantined files, deleted assets and backup copies; account-deletion semantics and backup expiry.
6. Backup location, encryption, retention, coordinated PostgreSQL + file snapshots, and tested restore objectives.
7. Operational monitoring/alert thresholds, incident response owner and recovery runbooks.
8. Whether generated outputs beyond PDF/DOCX require a later allowlist extension.

The initial hosted deployment must not proceed until the open security and operational items relevant to that deployment are resolved. A local implementation may proceed only under a separately approved Branch 30 implementation scope and must preserve the hosted fail-closed boundary.

**Implementation record:** Branch 29 remains a documentation-only decision record and introduced no runtime implementation. Branch 30, separately approved, adds the first local filesystem upload/list/download slice described above. Its implementation does not close the outstanding hosted-deployment security and operations gates.


## 13. Branch 31 — lifecycle, recovery, and local deployment gates

Branch 31 is documentation only; it changes no runtime behavior. The detailed policy and acceptance criteria are in [Resume Lifecycle & Recovery Readiness](RESUME_LIFECYCLE_RECOVERY_READINESS.md).

Key requirements before enabling lifecycle features:
- Deletion immediately makes an asset inaccessible through a tombstone/DELETING state; physical cleanup is idempotent and retryable.
- Retention periods and account-deletion semantics must be approved before cleanup jobs are implemented.
- Reconciliation begins report-only and must never auto-promote unknown or unscanned objects.
- PostgreSQL and document binaries require coordinated backups and a successful isolated restore drill with checksum and ownership verification.
- Encryption-at-rest, key custody/rotation/recovery, encrypted backups, and Windows ACLs require explicit verification.
- Scanner version/signature metadata, health monitoring, rate/concurrency controls, and incident runbooks remain future implementation work.

### Local readiness classification

1. **Synthetic-data code/build validation:** can be performed with synthetic fixtures and must not be represented as real-resume readiness.
2. **Real personal resume use:** not approved until the default non-`local` profile, reachable ClamAV scanner, persistent data root outside the repository, verified Windows ACLs, coordinated backup/restore drill, encryption decision, privacy-safe logging, and owner-isolation tests are implemented and verified.
3. **Hosted/public deployment:** not ready; requires a separate security and operational review.

Do not use the local scanner-bypass profile for real resumes. Do not claim recoverability until a restore drill passes.

## 14. Branch 34 — owner-initiated asset deletion foundation

Branch 34 introduces the first runtime deletion boundary for individual resume assets:
- DELETE /api/resumes/{id} derives the owner from the authenticated session and requires the existing CSRF protection.
- A PostgreSQL row lock serializes concurrent deletion requests. The transaction changes only an AVAILABLE asset to DELETING and inserts a durable PENDING cleanup job in the same transaction.
- The operation ID makes repeated requests idempotent; a repeated request for the same owner's DELETING asset returns the existing operation.
- List and download paths exclude DELETING assets immediately. Cross-user and unknown asset IDs return the same not-found response.
- The binary is not removed in this milestone. A later worker milestone consumes the durable job and performs bounded, retryable physical cleanup. Do not report deletion as physically complete until that worker verifies absence.

The schema change is additive (Flyway V4); existing V1–V3 migrations are unchanged. No account-deletion, retention scheduler, backup, encryption, or cleanup-worker behavior is included.


## 13. Branch 35 — durable physical deletion worker

Branch 35 consumes the durable jobs created by Branch 34:

- A scheduled worker claims one eligible job at a time using PostgreSQL row locking and `SKIP LOCKED`; separate application instances do not process the same fresh claim concurrently.
- Claims increment the attempt count and record a processing timestamp. A claim left PROCESSING for more than five minutes is eligible for recovery after a worker interruption.
- The storage adapter's `remove` operation is idempotent across both quarantine and available locations. The worker then calls `exists`; a successful delete response alone is not accepted as proof of absence.
- Only after absence is verified does one database transaction mark the asset DELETED, scrub the filename, media type, checksum, storage key, scanner result and byte size, and mark the job COMPLETED.
- Failures use a safe fixed error code and exponential retry delay (30 seconds up to six hours). After eight attempts the job enters DEAD_LETTER and requires operator investigation; it is not reported as deleted.
- A partially removed object is safe to retry. If metadata finalization fails after binary removal, the durable PROCESSING job is reclaimed after the lease timeout; the repeated remove/absence check then permits finalization.
- The asset's owner-scoped tombstone and minimal job record remain so duplicate API calls cannot recreate deletion work. The document-identifying metadata and binary are removed after verification.

**Scope boundary:** this worker processes explicit user deletion requests only. The 24-hour rejected/abandoned-upload retention sweep, account deletion, backup deletion replay, storage reconciliation, and operator tooling remain separate milestones. DEAD_LETTER is a failure state, not successful deletion.
