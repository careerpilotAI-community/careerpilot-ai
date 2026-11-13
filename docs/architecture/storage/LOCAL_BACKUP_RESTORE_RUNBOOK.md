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
