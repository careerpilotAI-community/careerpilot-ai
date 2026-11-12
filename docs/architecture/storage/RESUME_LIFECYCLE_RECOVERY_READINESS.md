# Branch 31 — Resume Lifecycle & Recovery Readiness

**Status:** Approved documentation/design milestone; no runtime implementation.
**Baseline:** Branch 30 merge `f377796f410a2eacb15806702d626c532bb93e7a`.
**Deployment target:** Local-first Windows developer environment initially. This document does not authorize hosted deployment.

## 1. Purpose and scope

Define safe lifecycle, recovery, retention, and operational requirements for private resume assets before adding deletion, AI processing, or deployment automation.

In scope:
- lifecycle and deletion semantics;
- retention and account-deletion policy requirements;
- object/metadata reconciliation;
- coordinated PostgreSQL and file backup/restore;
- encryption-at-rest and key-management requirements;
- Windows filesystem ACL requirements;
- scanner observability, upload abuse controls, monitoring, and runbooks;
- explicit local deployment readiness gates.

Out of scope:
- application code, migrations, endpoints, UI, configuration changes, or dependencies;
- implementing deletion, retention jobs, backup jobs, encryption, or reconciliation;
- hosted deployment approval;
- changing Branch 30 behavior or approved file-format/size/ownership constraints.

## 2. Asset lifecycle and deletion

Treat every binary asset as immutable. A new upload or tailored document creates a new asset; never overwrite an existing binary.

Required lifecycle:
- Upload: `PENDING → QUARANTINED → SCANNING → AVAILABLE`.
- Validation or malware failure: `REJECTED` or `FAILED`, with a bounded diagnostic code and timestamp; never expose rejected bytes.
- Deletion request: atomically make the asset inaccessible and transition to `DELETING` before attempting physical removal.
- Cleanup success: record a terminal deletion state or remove metadata according to the approved retention policy.
- Cleanup failure: retain a non-downloadable tombstone and retry idempotently; do not restore visibility.

Authorization and consistency:
- Derive owner identity from the authenticated server-side session.
- Check owner and parent-child relationships for every metadata and binary operation.
- Only `AVAILABLE` assets may be downloaded or supplied to AI processing.
- A database transaction cannot atomically commit a filesystem operation. Use explicit operation states, idempotency keys, and reconciliation.
- Preserve audit timestamps and non-sensitive reason codes; never log document bytes, private contents, credentials, or unnecessary filenames.

## 3. Retention and account deletion

No retention duration is approved by this branch. Before implementation, product/privacy review must decide and document:
- retention for active originals and tailored versions;
- expiry for rejected/quarantined uploads and failed partial uploads;
- user-initiated deletion behavior and grace period, if any;
- account deletion behavior across metadata, binaries, logs, backups, and derived AI artifacts;
- backup expiry and the maximum time until deleted data ages out of recoverable backups;
- legal hold or support exceptions, if applicable, with access controls and disclosure.

Required design properties:
- deletion must be idempotent and resumable after process interruption;
- pending deletion must immediately block download and AI access;
- report completion only after both metadata and binary cleanup are reconciled;
- backups must not silently reintroduce deleted data into active service after restore; replay deletion/tombstone records before enabling access;
- avoid retaining document content in logs, caches, temporary directories, or test artifacts.

## 4. Storage reconciliation

Build a future, read-only-first reconciliation process with explicit dry-run output before enabling repair.

Detect at minimum:
- metadata rows whose expected object is missing;
- unreferenced objects in quarantine or available storage;
- stale `PENDING`, `QUARANTINED`, `SCANNING`, or `DELETING` records;
- checksum or size mismatches;
- objects in the wrong lifecycle area;
- metadata whose state is not consistent with physical storage.

Safety requirements:
- scope every finding to an opaque asset ID and owner ID; do not expose contents;
- default to report-only; repairs require an explicit policy and auditable action;
- never automatically promote an unscanned object or make an unknown object downloadable;
- quarantine unexpected objects; do not delete them until a retention/recovery policy permits it;
- make repair idempotent, bounded, restartable, and safe under concurrent uploads/deletions;
- record counts, reason codes, timestamps, and operation IDs without sensitive payloads.

## 5. Backup and restore

PostgreSQL metadata and filesystem binaries are separate recovery domains and must be backed up as a coordinated set.

Local-mode requirements:
- store the configured document data root outside the Git repository and outside disposable container writable layers;
- use a named/persistent volume or host directory with explicit mapping; document the exact location and permissions;
- back up PostgreSQL and the document root using a consistent snapshot/maintenance procedure;
- encrypt backup archives and restrict access to the user/operator;
- maintain at least one independent copy separate from the active data directory;
- document backup age, retention, restore prerequisites, and recovery steps.

Restore drill acceptance:
1. Restore the database and document directory into an isolated location.
2. Verify schema/migration compatibility and metadata-to-object presence.
3. Recompute SHA-256 checksums and compare with stored metadata.
4. Confirm ownership boundaries and that no asset becomes public.
5. Start the application against the restored data and verify authenticated list/download behavior.
6. Verify pending deletion/tombstone records are replayed before enabling normal access.
7. Record duration, failures, missing objects, and corrective actions.

Do not claim recoverability until a restore drill passes. A database-only backup is insufficient.

## 6. Encryption and key management

Before real sensitive documents are used beyond an isolated developer machine, choose and document:
- encryption-at-rest boundary (filesystem/volume or application-level envelope encryption);
- key generation, storage, access control, rotation, backup, and recovery;
- separation of encryption keys from encrypted backups;
- behavior when a key is unavailable or compromised;
- secure deletion/cryptographic-erasure implications;
- supported local and future hosted deployment profiles.

Do not invent a custom cryptographic format. Prefer OS/platform or well-reviewed managed encryption mechanisms. Encryption does not replace filesystem access controls or encrypted backups.

## 7. Windows storage permissions

Branch 30 applies POSIX permissions where supported; its code explicitly leaves Windows ACL protection to the operator. Before local use with real resumes:
- choose a dedicated absolute data root outside the repository and web root;
- grant access only to the Windows account running the backend and required administrators;
- remove inherited broad access where appropriate and verify effective ACLs on root, quarantine, available, and files;
- ensure the frontend/dev-server account cannot serve the directory as static content;
- ensure Docker volume mapping does not broaden access or place files in a disposable layer;
- verify permissions after restart, file creation, backup, and restore.

A successful application startup does not prove ACL correctness. Do not use real personal resumes until effective ACLs have been inspected and validated.

## 8. Scanner and operational controls

For non-test uploads, keep ClamAV scanning enabled and fail closed on unavailable, timeout, malformed, or indeterminate verdicts. The local synthetic-fixture bypass is for tests only and must never be used with real resumes.

Future operational requirements:
- record scanner engine/signature version, scan timestamp, verdict, and bounded failure code;
- define signature update and scanner health checks;
- apply per-user and per-IP rate limits and concurrency bounds;
- monitor storage capacity, failed uploads, scan failures, stale quarantine items, reconciliation findings, and backup age;
- alert on unusual download/rejection patterns without logging private content;
- document incident response for malware found after upload, data exposure, lost keys, corruption, and accidental deletion.

## 9. Local deployment readiness gate

Local deployment is not a single binary state. Distinguish:

### A. Safe code/build validation (synthetic data only)
May proceed after the merged repository's current CI checks pass and a clean local setup is verified. Use synthetic fixtures only. Do not place real resumes in the application.

### B. Local use with real personal resumes
Not approved by this design document. Before marking ready, all must be verified:
- default non-`local` profile is used;
- ClamAV is running, reachable, and returns a clean verdict; no scanner bypass;
- PostgreSQL and document data root are persistent and outside the repository;
- Windows ACLs are inspected and restricted;
- backup of database and document root exists and an isolated restore drill passes;
- encryption-at-rest and encrypted-backup decision is implemented and verified;
- no sensitive document data is written to logs or public/static paths;
- authenticated upload/list/download and cross-user denial tests pass;
- operational recovery instructions are tested.

### C. Hosted/public deployment
Explicitly not ready. Requires a separate deployment threat model, secrets/key management, scanner operations, backup/restore, retention/account deletion, monitoring, rate/concurrency controls, and security review.

## 10. Branch 31 exit criteria

- Lifecycle and deletion behavior specified.
- Retention/account-deletion decisions and unresolved policy owners identified.
- Reconciliation detection and safe repair principles specified.
- Coordinated backup/restore and restore-drill acceptance criteria specified.
- Encryption/key-management requirements specified.
- Windows ACL requirements specified.
- Scanner operations, abuse controls, monitoring, and runbook requirements specified.
- Local synthetic-data and real-resume readiness gates separated.
- Roadmap and storage architecture reconciled with Branch 30 implementation.
- No application code or runtime behavior changed.

**Important:** Branch 31 is a design milestone, not a declaration that local real-resume use or hosted deployment is ready. The project owner must be explicitly notified when a later implementation and verification milestone satisfies the relevant local readiness gate.
