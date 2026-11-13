# Branch 38 — Local backup, restore, and deletion propagation runbook

**Status:** Implementation is a first local Windows operational slice. It has not been exercised in a real restore drill.  
**Policy:** 7 daily + 4 weekly recovery points; maximum 28-day backup-erasure window.  
**Safety:** Real personal-resume use remains NOT SAFE / not approved until the complete readiness gates are verified.

## 1. What the backup set contains

Each recovery point is a single directory containing:

- careerpilot.dump: PostgreSQL custom-format dump.
- documents/: a copy of both private document storage areas (quarantine/ and available/).
- account-deletions.jsonl: a point-in-time copy of the sidecar deletion journal.
- backup-manifest.json: recovery-point identity, UTC creation time, type, policy values, file sizes, and SHA-256 hashes.

The manifest is an integrity check, not a digital signature or proof against a malicious operator. Store backup sets on a separate, access-controlled BitLocker-protected volume. Keep the live deletion journal on a separate protected path from both the active document root and the backup directory.

The application writes minimal JSON Lines tombstones for both account deletion and individual resume deletion before the worker removes binaries. Each entry contains only schema version, operation UUID, owner UUID, deletion type, optional asset UUID, and UTC timestamp—no email, filename, storage key, or document content. The journal is independent of PostgreSQL so a database recovery point cannot erase newer deletion events. Entries are compacted after 29 days to cover the approved 28-day backup window plus its boundary.

## 2. Configure independent paths

Set absolute Windows paths outside the Git repository, for example:

- DOCUMENT_STORAGE_ROOT: private active document root.
- ACCOUNT_DELETION_JOURNAL_FILE: independent sidecar journal file.
- BackupRoot: separate backup directory/volume.

Do not use the example paths as a security guarantee. Verify effective NTFS ACLs and BitLocker protection on the actual volumes. Do not put the journal inside the document root or backup root.

Set DATABASE_PASSWORD in the process environment. Do not place credentials in command arguments, scripts, or the repository.

## 3. Create a recovery point

1. Stop the CareerPilot API and all upload, retention, and deletion workers. This maintenance pause is mandatory because PostgreSQL and filesystem snapshots cannot be made atomically by the current local adapter.
2. Drain upload and deletion queues before maintenance, then stop the API and workers. The backup script refuses to run while any individual or account deletion job is pending, processing, or dead-lettered.
3. Run the daily backup:

   ```powershell
   $env:DATABASE_PASSWORD = "<load from your secure local secret store>"
   .\scripts\backup\backup-local.ps1 `
     -BackupRoot "E:\CareerPilotBackups" `
     -DocumentRoot "D:\CareerPilot\private-documents" `
     -JournalFile "D:\CareerPilotJournal\account-deletions.jsonl" `
     -MaintenanceConfirmed
   ```

4. Run the weekly version once per week by adding -Weekly.
5. Restart the API only after the backup completes successfully.

The script checks BitLocker protection for the backup, document, and journal volumes; refuses paths inside the Git repository or overlapping locations; uses pg_dump; copies the document tree; records SHA-256 hashes; and prunes to at most 7 daily and 4 weekly recovery points and 28 days. It does not create Windows Task Scheduler jobs. Configure daily and weekly scheduled tasks only after a successful supervised backup and restore drill; each task must stop the app/workers or use a separately validated quiesce mechanism.

A backup command succeeding is not a restore-drill pass.

## 4. Restore into an isolated environment

Never restore over the live database or active document root. Stop the application and prepare a separate, empty target database and isolated document/journal paths.

The selected backup directory is integrity-checked and rejected if older than 28 days. The restore process requires the current independent deletion journal, not merely the older journal snapshot stored inside the backup. This is essential: the journal bundled with a recovery point may predate a later deletion.

Example:

```powershell
$env:DATABASE_PASSWORD = "<load from your secure local secret store>"
.\scripts\backup\restore-local.ps1 `
  -BackupPath "E:\CareerPilotBackups\daily-20261114T020000Z" `
  -CurrentDeletionJournal "D:\CareerPilotJournal\account-deletions.jsonl" `
  -TargetDatabase "careerpilot_restore" `
  -TargetDocumentRoot "D:\CareerPilotRestore\documents" `
  -TargetJournalFile "D:\CareerPilotRestore\journal\account-deletions.jsonl" `
  -BackendDirectory "C:\path\to\careerpilot-ai\backend" `
  -ReplayDeletionJournal
```

The script restores PostgreSQL and document files, runs the backend in non-web migration-only mode with cleanup schedulers disabled, then replays every retained account tombstone. For account tombstones, replay removes both quarantine and available binaries and deletes the associated sessions, resumes, upload-cleanup records, tracker data, profile, skills, and user row. For asset tombstones, it removes only the named owner's asset binary and metadata. Replay is idempotent. Invalid journal records, unknown storage-key formats, database errors, or integrity mismatches stop the restore.

The script does not start the application. Keep it stopped until all verification below is complete.

## 5. Restore-drill acceptance checklist

- [ ] The database dump restores successfully into an isolated database.
- [ ] Flyway migrations complete without starting an HTTP listener or background workers.
- [ ] Every manifest file passes SHA-256 verification.
- [ ] The restored document tree is private and located outside the repository/web root.
- [ ] The latest independent deletion journal is replayed before application access.
- [ ] A synthetic account deleted after the selected backup is absent after replay.
- [ ] Its resume binaries and rejected/abandoned upload objects are absent from both storage areas.
- [ ] Its sessions, resumes, jobs, applications, career profile, and skills are absent.
- [ ] A non-deleted synthetic account remains intact and can authenticate.
- [ ] Owner-scoped list/download and cross-user denial checks pass.
- [ ] Restored available assets have matching SHA-256 values and metadata; no unexpected object is made available.
- [ ] Recovery duration, missing objects, failures, replay count, and corrective actions are recorded without personal data.

Record the recovery-point ID, backup age, restore start/end times, result, and operator. Do not include emails, filenames, document contents, storage keys, passwords, or journal contents in the drill report.

## 6. Important limitations

- The scripts are local Windows operator tools, not a hosted backup service or scheduler.
- BitLocker status checks are point-in-time checks; they do not escrow recovery keys or prove key recoverability. Branch 39 owns key recovery validation.
- SHA-256 manifests detect accidental corruption but are not signed; protect backup directories and manifests with NTFS ACLs and BitLocker.
- The current backup script requires maintenance mode. It does not promise online-consistent snapshots.
- The sidecar journal is a high-value recovery artifact. Keep it available and protected independently from backup media; do not roll it back to the journal copy bundled in a selected recovery point.
- Restore replay is an operator-run step. Never expose a restored application before the script and checklist have completed.
- Storage reconciliation, automated scheduling, independent-copy/off-site strategy, encryption-key escrow, Windows ACL verification, and the integrated readiness drill remain separate work.
- A DEAD_LETTER account- or asset-deletion job remains unresolved; backups are blocked until the job is resolved. Do not describe it as completed or erase its operational evidence.

**Readiness statement:** Branch 38 provides implementation and a testable procedure, not proof of recoverability. Synthetic-data restore validation remains pending. Real personal-resume use is still NOT SAFE / not approved, and hosted/public deployment remains NOT READY.


## 7. BitLocker recovery-key escrow and controlled recovery

Branch 33 approved OS-managed Windows full-volume encryption for the local application-data and backup volumes. Branch 39 does not add application-level encryption or a custom CareerPilot key store.

### Before relying on encryption

1. Confirm the target Windows edition/device supports the required BitLocker or Device Encryption controls.
2. Confirm encryption is fully complete and protection is On for the volume containing the active document root, backup root, and independent journal directory.
3. For each protected volume, identify its volume and recovery-protector identifiers. Record only non-secret identifiers, mount point, purpose, status, verification date, and operator in the recovery inventory.
4. Back up each recovery password outside the encrypted computer. Maintain an independently accessible second protected copy. Do not keep a recovery USB beside the computer, place a plaintext key file on the encrypted volume, or commit recovery material to this repository.
5. Confirm the owner can retrieve the escrowed recovery material through the intended recovery channel. Do not treat the existence of a file or a UI checkbox as proof of retrievability.

Microsoft documents the supported recovery-key backup destinations and cautions against storing a recovery USB with the computer. The 48-digit recovery password is not reproducible if all copies are lost.

### Recovery-protector rotation

1. Start rotation only when the volume is healthy and accessible and the owner has a verified recovery path.
2. Add a new BitLocker recovery-password protector using supported Windows management tooling.
3. Escrow the new recovery password independently and verify the new record against the correct volume/protector identifier.
4. Only after verification, remove the old or exposed protector. Never remove the only verified recovery protector.
5. Update the non-secret inventory and record the date, reason category, operator, and completion result. Do not record the recovery password in the audit record.
6. If compromise is suspected, treat the old recovery material as exposed until its protector has been removed and the replacement has been verified.

Replacing a recovery-password protector is not the same as changing the volume's underlying encryption key or re-encrypting its contents.

### Controlled recovery validation

- Validate recovery using a disposable BitLocker-protected test volume or a separately prepared recovery-test device with synthetic data. Do not force recovery on the only production volume as a test.
- Retrieve the escrowed recovery material through the documented independent channel and use it to unlock the test volume.
- Confirm the test data is readable, record the outcome and elapsed time, then securely dispose of the test volume/data according to the test procedure.
- A successful test-volume drill validates the escrow/retrieval procedure, not a real-device disaster recovery or the complete CareerPilot database/document restore.
- The full CareerPilot restore drill remains separate: restore into isolated targets, apply the latest independent deletion journal, and verify metadata/object consistency, checksums, ownership, and application download behavior.

### NTFS ACL verification

On the actual Windows host, inspect effective ACLs on the document root, its quarantine/available descendants, backup root, and journal directory. Use Windows access-control tooling (for example, `icacls` and PowerShell `Get-Acl`) and test access using a separate standard Windows account that must not be able to read the private data. Review both explicit and inherited entries. Do not rely on `icacls /verify` alone as proof that unauthorized users are denied access; it checks ACL structure, not the complete application threat model.

Do not run an automatic ACL rewrite as part of Branch 39. Any ACL mutation must be reviewed against the actual backend runtime identity and tested before use.

### Evidence record

Record only non-secret evidence: host/Windows edition, volume purpose and identifier, encryption/protection state, recovery-protector identifier, escrow verification date and outcome, ACL review outcome, recovery-test outcome, operator, and any exceptions. Never include recovery passwords, key files, key packages, document contents, or personal resume details.

**Current status:** no target-host BitLocker/escrow verification, NTFS ACL inspection, recovery test-volume drill, or full PostgreSQL + filesystem restore drill has yet been evidenced by this repository.