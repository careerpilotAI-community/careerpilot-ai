param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
)

$ErrorActionPreference = "Stop"

$backupPath = Join-Path $RepositoryRoot "scripts/backup/backup-local.ps1"
$restorePath = Join-Path $RepositoryRoot "scripts/backup/restore-local.ps1"
foreach ($path in @($backupPath, $restorePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required backup/restore script not found: $path"
    }
    $tokens = $null
    $parseErrors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$parseErrors) | Out-Null
    if ($parseErrors.Count -gt 0) {
        $messages = ($parseErrors | ForEach-Object { "{0}:{1} {2}" -f $_.Extent.StartLineNumber, $_.Extent.StartColumnNumber, $_.Message }) -join "; "
        throw "PowerShell syntax validation failed for $path : $messages"
    }
}

$backup = Get-Content -LiteralPath $backupPath -Raw
$restore = Get-Content -LiteralPath $restorePath -Raw

$checks = @(
    @{ Name = "backup uses collision-resistant IDs"; Pass = $backup.Contains("[guid]::NewGuid()") },
    @{ Name = "backup refuses an existing staging directory"; Pass = $backup.Contains("refusing to remove or reuse it") },
    @{ Name = "backup validates the private storage layout"; Pass = $backup.Contains("Assert-DocumentTree") -and $backup.Contains("quarantine") -and $backup.Contains("available") },
    @{ Name = "backup validates reparse points"; Pass = $backup.Contains("Assert-NoReparsePoints") },
    @{ Name = "backup checks other PostgreSQL sessions"; Pass = $backup.Contains("pg_stat_activity") },
    @{ Name = "backup checks unresolved deletion queues"; Pass = $backup.Contains("resume_deletion_jobs") -and $backup.Contains("account_deletion_jobs") -and $backup.Contains("resume_upload_cleanup_jobs") },
    @{ Name = "backup restores PGPASSWORD"; Pass = $backup.Contains('$previousPgPassword') -and $backup.Contains("Remove-Item Env:PGPASSWORD") },
    @{ Name = "restore requires isolated-database confirmation"; Pass = $restore.Contains('[switch]$ConfirmIsolatedDatabase') -and $restore.Contains('if (-not $ConfirmIsolatedDatabase)') },
    @{ Name = "restore refuses existing document destination"; Pass = $restore.Contains('if (Test-Path -LiteralPath $TargetDocumentRoot)') },
    @{ Name = "restore refuses existing journal destination"; Pass = $restore.Contains('if (Test-Path -LiteralPath $TargetJournalFile)') },
    @{ Name = "restore does not use robocopy mirror"; Pass = $restore -notmatch '(?im)^\s*&\s*robocopy\.exe[^\r\n]*/MIR' },
    @{ Name = "restore does not invoke pg_restore clean"; Pass = $restore -notmatch '(?im)^\s*&\s*pg_restore\.exe[^\r\n]*--clean' },
    @{ Name = "restore validates manifest path containment"; Pass = $restore.Contains('Test-PathWithin $candidate $BackupDirectory') },
    @{ Name = "restore rejects reparse points"; Pass = $restore.Contains("Assert-NoReparsePoints") },
    @{ Name = "restore supports existing legacy recovery-point names"; Pass = $restore.Contains('\d{6}Z|\d{9}Z-') },
    @{ Name = "restore checks manifest inventory and hashes"; Pass = $restore.Contains("actualFiles") -and $restore.Contains("Get-FileHash") },
    @{ Name = "restore verifies database target is empty"; Pass = $restore.Contains('$objectCountSql') -and $restore.Contains("Target database is not empty") },
    @{ Name = "restore reconciles document metadata and files"; Pass = $restore.Contains("Restored assets reconciled") -and $restore.Contains("checksum_sha256") },
    @{ Name = "restore restores PGPASSWORD"; Pass = $restore.Contains('$previousPgPassword') -and $restore.Contains("Remove-Item Env:PGPASSWORD") }
)

$failed = @($checks | Where-Object { -not $_.Pass })
$checks | ForEach-Object {
    $status = if ($_.Pass) { "PASS" } else { "FAIL" }
    Write-Output ("{0}: {1}" -f $status, $_.Name)
}
if ($failed.Count -gt 0) {
    throw "$($failed.Count) backup/restore safety checks failed."
}
Write-Output "PowerShell syntax and static safety checks passed. No backup, restore, database, or filesystem mutation was performed."
