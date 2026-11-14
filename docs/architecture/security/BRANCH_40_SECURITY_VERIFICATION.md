# Branch 40 — Security & Privacy Verification

**Status:** Verification milestone in progress. Automated tests are added on the Branch 40 working branch; host-specific security checks remain unverified.  
**Scope:** Regression tests and evidence boundaries for existing security controls. This milestone does not add application features or claim deployment readiness.

## 1. Automated scanner verification

The backend test suite now exercises the ClamAV INSTREAM protocol adapter against a local deterministic TCP stub:

- An explicit `OK` verdict is accepted as clean.
- An explicit `FOUND` verdict is returned as infected.
- Unknown/error verdicts are rejected.
- A connection closed without a verdict is rejected.

The tests do not require a live ClamAV daemon and do not prove that a target machine has ClamAV installed, updated, reachable, or correctly configured. A separate operational scan against the deployed daemon remains necessary.

The local development scanner tests establish that the bypass accepts only the exact synthetic-fixture marker within the first 4 KiB and rejects ordinary content or a marker beyond that inspected prefix. This is a development-only test-fixture gate, not malware scanning.

## 2. Existing lifecycle and authorization regression coverage

The existing PostgreSQL-backed integration suite already covers the following behaviors:

- Unauthenticated access and state-changing requests without CSRF are rejected.
- Resume download is owner-scoped.
- Scanner unavailability fails the upload request rather than accepting the document.
- Rejected and abandoned upload cleanup observes its retention deadline and verifies storage absence.
- Individual deletion immediately revokes access and queues durable cleanup; the worker verifies absence before scrubbing metadata.
- Account deletion requires confirmation and credentials, revokes sessions, and removes owned data only after storage cleanup verification.

Branch 40 should preserve these tests and add narrowly scoped regressions only where an actual coverage gap is demonstrated. A passing test suite is evidence for the tested cases only, not a complete security assessment.

## 3. Privacy-safe deletion journal

The journal is intentionally metadata-only: opaque operation, owner and optional asset identifiers; deletion type; schema version; and timestamp. Existing journal tests assert idempotency, retention pruning, and absence of email addresses, passwords, resume content, filenames, and storage keys.

This is not a blanket proof that every application log, exception, metric, or future diagnostic path is free of personal data. Production log review and a controlled log-capture test remain separate evidence items.

## 4. Windows ACL and encryption verification boundary

Java POSIX permission tests do not verify Windows NTFS access control. The local storage and deletion-journal constructors rely on inherited Windows ACLs when POSIX permissions are unsupported.

The target Windows host must be inspected separately for:
- The configured document root and all descendants.
- The quarantine and available directories.
- The deletion-journal directory, journal file, and lock file.
- The configured backup root and all recovery-point contents.
- Effective explicit and inherited allow/deny entries, including the actual application runtime identity.
- Access for SYSTEM and local Administrators, and denial for unrelated standard users.

The same host inspection must establish that every volume containing application data, backups, and the deletion journal is protected by the approved OS-managed full-volume encryption policy. Recovery-key escrow and retrieval are separate checks. No host-specific ACL, BitLocker, escrow, or recovery result is claimed by CI.

## 5. Evidence still required after CI

| Evidence | What a passing CI run can establish | What remains outside CI |
|---|---|---|
| ClamAV protocol behavior | Deterministic verdict and error handling against a stub | Live daemon health, signatures, network and host configuration |
| Local scanner bypass | Synthetic marker allow/deny behavior | Safety of any real-resume workflow |
| Lifecycle regression tests | Tested authorization, deletion and cleanup paths | Full threat-model coverage and production concurrency behavior |
| Privacy journal tests | Current journal serialization excludes tested personal fields | All application logs, metrics, exception paths and host log retention |
| Windows ACLs | Nothing about the target host's effective NTFS permissions | Manual/effective-access inspection on the configured host |
| Volume encryption and recovery | Nothing about actual BitLocker state or escrow | BitLocker status, independent escrow retrieval and disposable-volume recovery |
| Backup restore | Script/build checks only, if run | Full isolated PostgreSQL + filesystem restore and deletion replay |

## 6. Readiness decision

Branch 40 does not close the local readiness gates.

- **Synthetic-data local testing:** pending until Branch 41 completes its integrated local validation checklist.
- **Real personal-resume use:** NOT SAFE / not approved until all required host, scanner, ACL, encryption, recovery, privacy, authorization, and restore evidence is reviewed.
- **Hosted/public deployment:** NOT READY; it requires its own deployment, secrets, TLS, storage, scanning, monitoring, and recovery controls.

Do not interpret merged documentation or green CI as approval to upload a real personal resume.
