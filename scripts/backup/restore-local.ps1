param(
    [Parameter(Mandatory = $true)][string]$BackupPath,
    [Parameter(Mandatory = $true)][string]$CurrentDeletionJournal,
    [Parameter(Mandatory = $true)][string]$TargetDocumentRoot,
    [Parameter(Mandatory = $true)][string]$TargetJournalFile,
    [Parameter(Mandatory = $true)][string]$TargetDatabase,
    [string]$DatabaseUser = "careerpilot",
    [string]$DatabaseHost = "127.0.0.1",
    [int]$DatabasePort = 5432,
    [switch]$ReplayDeletionJournal
)

$ErrorActionPreference = "Stop"

foreach ($path in @($BackupPath, $CurrentDeletionJournal, $TargetDocumentRoot, $TargetJournalFile)) {
    if (-not [IO.Path]::IsPathFullyQualified($path)) { throw "All paths must be absolute." }
}
if (-not (Test-Path -LiteralPath $CurrentDeletionJournal -PathType Leaf)) {
    throw "The latest independent deletion journal is required. Never replay only the journal snapshot bundled with an older backup."
}
if (-not (Get-Command "pg_restore.exe" -ErrorAction SilentlyContinue)) { throw "pg_restore.exe is required and must be on PATH." }
if (-not (Get-Command "psql.exe" -ErrorAction SilentlyContinue)) { throw "psql.exe is required and must be on PATH." }
$backup = (Resolve-Path -LiteralPath $BackupPath).Path
$manifestPath = Join-Path $backup "backup-manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Backup manifest is missing." }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or $manifest.maximumBackupErasureDays -ne 28) { throw "Unsupported backup manifest or retention policy." }
$created = [DateTimeOffset]::Parse($manifest.createdUtc)
if ($created -lt [DateTimeOffset]::UtcNow.AddDays(-28)) {
    throw "This recovery point is older than the approved 28-day deletion-propagation window. Refusing an unsafe restore."
}
foreach ($file in $manifest.files) {
    $candidate = Join-Path $backup $file.path
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw "Backup file is missing: $($file.path)" }
    $actual = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
    if ($actual -ne $file.sha256) { throw "Backup integrity check failed: $($file.path)" }
}

$dump = Join-Path $backup "careerpilot.dump"
$sourceDocuments = Join-Path $backup "documents"
if (-not (Test-Path $sourceDocuments -PathType Container)) { throw "A coordinated document snapshot is required." }
if (-not $ReplayDeletionJournal) {
    throw "Restore is isolated by default. Rerun with -ReplayDeletionJournal to replay current tombstones before starting the application."
}

New-Item -ItemType Directory -Force -Path $TargetDocumentRoot | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $TargetJournalFile) | Out-Null

# Restore only into a dedicated, isolated database. The application must remain stopped.
& pg_restore.exe --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$TargetDatabase --clean --if-exists --no-owner --no-acl $dump
if ($LASTEXITCODE -ne 0) { throw "pg_restore failed with exit code $LASTEXITCODE." }

& robocopy.exe $sourceDocuments $TargetDocumentRoot /MIR /COPY:DAT /DCOPY:DAT /R:2 /W:2 /XJ
if ($LASTEXITCODE -ge 8) { throw "Document restore failed with robocopy exit code $LASTEXITCODE." }
# Use the latest independent sidecar journal, not the stale copy bundled with the selected recovery point.
Copy-Item -LiteralPath $CurrentDeletionJournal -Destination $TargetJournalFile -Force

function Invoke-Psql([string]$Sql, [switch]$TuplesOnly) {
    $args = @("--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--host=$DatabaseHost", "--port=$DatabasePort",
        "--username=$DatabaseUser", "--dbname=$TargetDatabase", "--command", $Sql)
    if ($TuplesOnly) { $args = @("--no-psqlrc", "--tuples-only", "--no-align", "--set", "ON_ERROR_STOP=1",
        "--host=$DatabaseHost", "--port=$DatabasePort", "--username=$DatabaseUser", "--dbname=$TargetDatabase", "--command", $Sql) }
    $output = & psql.exe @args
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL deletion-journal replay failed." }
    return $output
}

$entries = Get-Content -LiteralPath $TargetJournalFile | Where-Object { $_.Trim().Length -gt 0 } | ForEach-Object {
    $entry = $_ | ConvertFrom-Json
    $parsedOperation = [guid]::Empty
    $parsedOwner = [guid]::Empty
    if ($entry.schemaVersion -ne 1 -or -not [guid]::TryParse($entry.operationId, [ref]$parsedOperation) -or
        -not [guid]::TryParse($entry.ownerId, [ref]$parsedOwner)) {
        throw "Deletion journal contains an invalid or unsupported record; restore halted."
    }
    [pscustomobject]@{ operationId = $parsedOperation; ownerId = $parsedOwner }
}

foreach ($entry in $entries) {
    $owner = $entry.ownerId.ToString()
    $keys = Invoke-Psql "SELECT storage_key FROM resume_assets WHERE user_id='$owner'::uuid AND storage_key IS NOT NULL UNION SELECT storage_key FROM resume_upload_cleanup_jobs WHERE owner_id='$owner'::uuid AND storage_key IS NOT NULL" -TuplesOnly
    foreach ($key in $keys) {
        $value = "$key".Trim()
        if ($value -notmatch '^[0-9a-fA-F-]{36}$') { throw "Unexpected non-opaque storage key in restored database; restore halted." }
        foreach ($area in @("quarantine", "available")) {
            $objectPath = Join-Path (Join-Path $TargetDocumentRoot $area) $value
            if (Test-Path -LiteralPath $objectPath) { Remove-Item -LiteralPath $objectPath -Force }
        }
    }

    $sql = @"
BEGIN;
DELETE FROM spring_session WHERE principal_name=(SELECT email FROM users WHERE id='$owner'::uuid);
DELETE FROM resume_deletion_jobs WHERE owner_id='$owner'::uuid;
DELETE FROM resume_assets WHERE user_id='$owner'::uuid;
DELETE FROM resume_upload_cleanup_jobs WHERE owner_id='$owner'::uuid;
DELETE FROM applications WHERE user_id='$owner'::uuid;
DELETE FROM jobs WHERE user_id='$owner'::uuid;
DELETE FROM profile_skills WHERE user_id='$owner'::uuid;
DELETE FROM career_profiles WHERE user_id='$owner'::uuid;
DELETE FROM users WHERE id='$owner'::uuid;
UPDATE account_deletion_jobs
SET job_status='COMPLETED', owner_id=NULL, completed_at=COALESCE(completed_at, CURRENT_TIMESTAMP),
    processing_started_at=NULL, last_error_code=NULL
WHERE owner_id='$owner'::uuid;
COMMIT;
"@
    Invoke-Psql $sql | Out-Null
}

Write-Output "Restore and deletion-journal replay completed. Keep the application stopped until checksum, ownership, and authenticated access checks pass."
Write-Output "Recovery point: $($manifest.backupId) ($($manifest.createdUtc))"
Write-Output "Deletion tombstones replayed: $($entries.Count)"
