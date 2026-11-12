# Branch 32 — Resume Retention, Deletion & Security Policy Decisions

**Status:** Documentation-only policy decision record; no runtime behavior or security control is implemented by this milestone.
**Baseline:** Branch 31 merge `cc562edf954ddd7df2f9c58091bd0974d959207a`.
**Target:** Local-first Windows deployment; hosted deployment remains separately gated.

## 1. Purpose, scope, and decision status

Translate the Branch 31 lifecycle and recovery requirements into implementable policy choices for resume assets and account deletion.

This document distinguishes:
- **Existing behavior:** verified in the merged Branch 30 implementation.
- **Recommended default:** the proposed product/security default for owner approval.
- **Required decision:** must be explicitly accepted or changed before implementation.
- **Implementation requirement:** mandatory engineering behavior once the policy is approved.

This branch changes documentation only. It does not add endpoints, migrations, scheduled jobs, encryption, backup automation, UI, or configuration.

### Existing implementation baseline

Branch 30 stores PDF/DOCX assets in a private local filesystem root and metadata in PostgreSQL. Assets are immutable, owner-scoped, checksummed, scanned before availability, and downloadable only while status is AVAILABLE. There is currently no delete endpoint, retention worker, account-deletion workflow, backup/restore automation, encryption-at-rest/key management, or Windows ACL verification. The current V3 schema permits AVAILABLE/FAILED only; it does not implement tombstones or DELETING. The local profile uses a synthetic-fixture-only scanner bypass and must not be used for real resumes.

## 2. Resume retention policy

### Proposed policy

| Asset category | Recommended default | Required decision |
|---|---|---|
| Active original resume | Retain until user deletes it or deletes their account | Approve indefinite-until-user-action retention, or define an inactivity expiry |
| Active tailored resume | Same as its parent/original lifecycle; user may delete independently | Approve independent deletion and parent-child semantics |
| Rejected/malware-positive upload | Keep inaccessible only as long as operationally necessary; purge within 24 hours after terminal verdict | Approve 24-hour maximum, or choose shorter period |
| Failed/incomplete upload or abandoned quarantine item | Automatically purge after 24 hours; retry cleanup and retain only a minimal non-content failure record | Approve 24-hour maximum |
| User-requested deletion | Immediately revoke access; physical deletion follows asynchronously | Approve whether a grace period exists; recommended no grace period for individual assets |
| Minimal audit/security event | Retain metadata-only events for a defined period; never retain document bytes or extracted text in the audit record | Approve a concrete duration and applicable legal requirements |

Do not start a retention clock for an active asset based only on upload age. Any inactivity-based expiry must include advance notice, recovery opportunity, and explicit product approval.

### Implementation requirements

- Retention rules must be category-specific, configurable, documented, and testable; no hard-coded unexplained durations.
- Store timestamps needed to calculate expiry and the policy/version that produced the expiry decision.
- Use UTC timestamps and deterministic boundary semantics.
- A scheduled worker must be bounded, restartable, idempotent, observable, and safe under concurrent upload/deletion.
- A policy change must not silently shorten an existing user's retention without an approved migration/notice plan.
- Never expire or delete data under a legal hold; holds require a documented authority, reason code, access restriction, review date, and release process. Legal-hold behavior itself requires legal/privacy approval.

## 3. User-initiated asset deletion

### Recommended behavior

1. Authenticate the user through the existing server-side session and enforce CSRF.
2. Resolve the asset using both asset ID and authenticated owner ID; never trust a client-supplied owner.
3. In one database transaction, transition the asset to a non-downloadable `DELETING` tombstone and record request time, reason code, and operation ID.
4. From that point forward, list/download/AI-processing paths must deny access to the asset. Return a consistent not-found response for unauthorized or unavailable assets.
5. An idempotent cleanup worker removes the binary from quarantine/available storage, verifies absence, then finalizes metadata according to the approved audit policy.
6. If physical deletion fails, keep the tombstone, retry with bounded backoff, and surface an operator-safe alert. Never restore availability.
7. Report deletion complete only when the binary is absent and metadata is in its approved terminal state.

A database transaction cannot atomically commit a filesystem delete. Use an outbox/job record or equivalent durable operation record so a crash between metadata transition and file removal cannot lose the cleanup request.

### Required invariants

- `DELETING`, deleted, rejected, failed, quarantined, and unknown states are never downloadable or eligible for AI processing.
- Deletion is idempotent and safe to repeat.
- Do not physically delete before access is revoked in PostgreSQL.
- Do not expose storage keys, paths, or document content in API responses or logs.
- Do not allow a stale upload or reconciliation task to resurrect a deleted asset.
- A tailored asset may be deleted independently; deleting an original must not silently delete tailored descendants unless the user has approved the cascade policy. Recommended default: show and delete the selected asset only, with explicit separate selection for related assets.

## 4. Account deletion

### Recommended user-visible semantics

Account deletion is a two-phase workflow:
- **Request accepted:** disable authentication and revoke all active sessions immediately; mark the account and owned resources as pending deletion.
- **Deletion completed:** all active application records and document binaries have been purged or irreversibly anonymized according to the approved retention and legal policy, and the backup-expiry schedule is disclosed.

Recommended default is no user-facing grace period after confirmation. A short operational retry window may exist internally, but it must not restore account access or make data available.

### Deletion scope matrix

| Data class | Required behavior |
|---|---|
| Credentials, sessions, CSRF/session state | Revoke sessions immediately; remove credentials and account authentication records during account purge |
| Resume metadata and binaries | Tombstone immediately, block all reads, then delete binary and metadata |
| Job/application/profile data | Delete or irreversibly anonymize according to user-facing account-deletion promise |
| Derived AI artifacts, extracted text, embeddings, caches | Delete when attributable to the account; do not retain resume-derived content as a hidden copy |
| Temporary files and quarantine | Delete; include abandoned and partial uploads |
| Application logs and security audit | Remove personal/document content; retain only minimum lawful, non-content security events where necessary |
| Backups | Mark deletion for propagation; backups age out under a documented maximum backup-retention period; restored backups must replay deletion records before serving traffic |
| Legal hold | Exception only with approved legal basis, strict access controls, scope minimization, and disclosure where permitted |

Account deletion must be owner-scoped and idempotent. Do not physically remove the account row before durable deletion work for its dependent data is recorded. Define foreign-key ordering and anonymization behavior before writing migrations.

**Required owner decision:** confirm whether application/job history is deleted or anonymized, whether a confirmation step is required, whether any grace period is offered, and what lawful audit exceptions apply.

## 5. Backup retention and deletion propagation

### Recommended local-first default

- Backups contain both PostgreSQL metadata and document binaries as a coordinated recovery set.
- Encrypt every backup archive and restrict access to the local operator.
- Keep at least one independent backup copy, separate from the active data root.
- Recommended initial retention proposal: 7 daily recovery points and 4 weekly recovery points, subject to storage capacity and user approval.
- Document the maximum age at which a deleted asset could still exist in any recoverable backup. Do not promise immediate erasure from offline backups.
- Backups must not be mounted as live application storage or served through the web application.

### Deletion propagation protocol

1. Record asset/account deletion in a durable deletion ledger or tombstone table before cleanup.
2. Apply deletion to active PostgreSQL metadata and filesystem objects.
3. Preserve only the minimum tombstone/ledger information needed to prevent resurrection; do not retain document contents or unnecessary personal identifiers.
4. Ensure each backup set has a creation timestamp and recovery-point identity.
5. On restore, restore database and binary storage into an isolated environment, replay deletion records newer than the recovery point, reconcile metadata and objects, then permit application startup.
6. Expire backup sets containing deleted data according to the published maximum backup-retention period.
7. Verify expiry by inventory and record evidence without exposing document names or contents.

If the deletion ledger itself is restored from an older recovery point, replay must also consult a newer protected deletion journal or equivalent source. A backup restore must never silently resurrect deleted accounts/assets.

**Required owner decision:** approve or replace the proposed 7-daily/4-weekly schedule and set a concrete maximum backup-retention/erasure window. The schedule is a proposal, not a currently configured backup policy.

## 6. Encryption-at-rest decision

### Recommended default for the first local Windows deployment

Use OS-managed full-volume encryption for the dedicated data volume and for the volume containing local backup archives (for example, BitLocker on supported Windows editions). Keep the application filesystem adapter unchanged. This is the lowest-complexity local-first baseline and avoids introducing custom cryptographic formats or application-managed key material in the first release.

This choice protects powered-off/lost-device scenarios; it does not protect files from a compromised or already logged-in Windows account, malware running as that account, or an authorized process. Therefore it does not replace restrictive NTFS ACLs, session authorization, private storage roots, or encrypted independent backups.

### Hosted/future profile

Before hosted deployment, require provider-side encryption at rest for private object storage and database volumes, encrypted backups, TLS in transit, least-privilege service identities, and a documented key custody/rotation/recovery model. Application-level envelope encryption may be evaluated later if the threat model requires per-object cryptographic erasure or stronger separation.

### Required decision

Approve OS-managed volume encryption as the local default, or explicitly select application-level envelope encryption. Do not claim encryption readiness until the actual Windows volume and backup destination are verified as encrypted and recovery keys are safely escrowed outside the encrypted device.

## 7. Key-management defaults

For the recommended local volume-encryption approach:
- Windows/platform-managed key storage; do not store recovery keys in the repository, application database, document root, or the same unencrypted backup.
- Recovery key escrow must be available to the owner independently of the encrypted device.
- Limit recovery-key access to the owner and explicitly authorized recovery administrators.
- Verify recovery-key availability before relying on encryption; do not test recovery by risking the only key.
- Rotate/reissue recovery material after suspected exposure, device ownership change, or relevant security incident.
- Document device replacement, account recovery, lost-key, and suspected-compromise procedures.

For any later application-level encryption:
- use a vetted platform/library implementation and authenticated encryption; never invent a cipher, mode, key derivation scheme, or file format;
- separate key-encryption keys from encrypted data and backups;
- define key IDs/versioning, rotation, re-encryption, revocation, recovery, and compromise response;
- fail closed when the required key is unavailable; never silently write plaintext;
- ensure deletion/cryptographic erasure claims are backed by key inventory and backup analysis.

## 8. Audit, privacy, and observability

Record only the minimum operational evidence: opaque asset/account operation ID, event type, UTC timestamp, actor category, bounded reason/result code, and completion state. Avoid raw filenames where not necessary, resume contents, extracted text, credentials, session identifiers, raw storage keys, and backup secrets.

Metrics may include counts and durations for pending deletions, cleanup failures, expired quarantine items, backup age, restore validation, and deletion propagation lag. Avoid labels containing user IDs, email addresses, filenames, or document-derived values.

## 9. Implementation requirements and acceptance criteria

Implementation must be a separate explicitly approved milestone. At minimum it must deliver:

### Schema and API
- Migration for lifecycle states, deletion timestamps/reason codes, account-deletion state, durable cleanup jobs/outbox, and minimal deletion ledger.
- Owner-scoped asset deletion endpoint with CSRF and authorization tests.
- Account-deletion request/status workflow with immediate session revocation.
- No client-controlled owner identifiers; no public or permanent object URLs.
- Backward-compatible migration and rollback/recovery plan.

### Workers and storage
- Idempotent, bounded, restartable retention and deletion workers.
- Durable retries and dead-letter/operator visibility without sensitive payloads.
- Filesystem deletion that rejects traversal/symlink escapes and verifies absence.
- Report-only reconciliation first; no automatic promotion or deletion of unknown objects.
- Backup/restore scripts covering PostgreSQL and document root together.
- Restore process replays deletion ledger before application access.
- Backup encryption, access restrictions, expiry, and independent-copy verification.

### Security and tests
- Cross-user asset deletion and account-deletion denial tests.
- Pending deletion immediately blocks download/list/AI access.
- Crash/failure tests at each boundary: DB tombstone, filesystem delete, metadata finalization, backup restore, ledger replay.
- Repeated requests and worker retries are idempotent.
- Tests prove deleted assets do not reappear after restoring an older backup.
- No resume contents, credentials, tokens, or raw storage keys in logs.
- Windows ACL verification documented and tested on the target host.
- Encryption and recovery-key checks documented with evidence.
- Scanner remains fail-closed; synthetic local scanner bypass is never used for real resumes.

### Acceptance gate
Do not enable real-resume use until the approved retention policy is implemented, deletion and account-deletion tests pass, Windows ACLs are verified, encrypted storage and encrypted backups are verified, and a coordinated isolated restore drill proves deletion replay and checksum/ownership integrity.

## 10. Decisions required before implementation

The project owner must explicitly approve or revise:
1. Active original/tailored assets retained until user deletion/account deletion.
2. 24-hour maximum retention for rejected and abandoned quarantine uploads.
3. No grace period for individual asset deletion; immediate access revocation.
4. Account deletion semantics for application history (delete vs irreversible anonymization) and any confirmation/grace period.
5. Proposed backup schedule (7 daily + 4 weekly) and maximum backup-erasure window.
6. OS-managed Windows volume encryption as local default versus application-level envelope encryption.
7. Minimal security-audit retention duration and any legal-hold exceptions.

Until these are decided, they remain proposals and must not be implemented as assumed policy.

## 11. Branch 32 exit criteria

- Existing Branch 30 runtime behavior accurately distinguished from future requirements.
- Retention categories and proposed durations recorded with explicit approval status.
- User deletion and account deletion semantics specified.
- Backup retention and deletion propagation specified, including restore replay.
- Local encryption boundary and key-management defaults recommended.
- Hosted encryption requirements kept separate.
- Implementation requirements and testable acceptance criteria documented.
- Roadmap and storage architecture reconciled.
- No application code, migrations, dependencies, configuration, or runtime behavior changed.
