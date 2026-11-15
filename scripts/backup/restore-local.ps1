param(
    [Parameter(Mandatory = $true)][string]$BackupPath,
    [Parameter(Mandatory = $true)][string]$CurrentDeletionJournal,
    [Parameter(Mandatory = $true)][string]$TargetDocumentRoot,
    [Parameter(Mandatory = $true)][string]$TargetJournalFile,
    [Parameter(Mandatory = $true)][string]$TargetDatabase,
    [Parameter(Mandatory = $true)][string]$BackendDirectory,
    [string]$DatabaseUser = "careerpilot",
    [string]$DatabaseHost = "127.0.0.1",
    [int]$DatabasePort = 5432,
    [switch]$ConfirmIsolatedDatabase,
    [switch]$ReplayDeletionJournal
)

$ErrorActionPreference = "Stop"

function Test-PathWithin([string]$Child, [string]$Parent) {
    $childPath = [IO.Path]::GetFullPath($Child).TrimEnd("\")
    $parentPath = [IO.Path]::GetFullPath($Parent).TrimEnd("\")
    return $childPath.Equals($parentPath, [StringComparison]::OrdinalIgnoreCase) -or
        $childPath.StartsWith($parentPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
}
function Get-RelativePath([string]$BasePath, [string]$TargetPath) {
    $base = [IO.Path]::GetFullPath($BasePath).TrimEnd("\") + "\"
    $target = [IO.Path]::GetFullPath($TargetPath)
    if (-not $target.StartsWith($base, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Cannot calculate a relative path outside the base directory."
    }
    return $target.Substring($base.Length)
}
function Assert-NoReparsePoints([string]$Path) {
    $root = Get-Item -LiteralPath $Path -Force
    $items = @($root)
    if ($root.PSIsContainer) {
        $items += @(Get-ChildItem -LiteralPath $Path -Force -Recurse -ErrorAction Stop)
    }
    foreach ($item in $items) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Reparse points and symbolic links are not allowed in recovery paths."
        }
    }
}
function Assert-Manifest([string]$BackupDirectory, $Manifest) {
    if ($Manifest.schemaVersion -ne 1 -or $Manifest.maximumBackupErasureDays -ne 28 -or
        $Manifest.dailyRecoveryPoints -ne 7 -or $Manifest.weeklyRecoveryPoints -ne 4 -or
        $Manifest.kind -notin @("daily", "weekly")) {
        throw "Unsupported backup manifest or retention policy."
    }
    if ($Manifest.backupId -ne (Split-Path -Leaf $BackupDirectory) -or
        $Manifest.backupId -notmatch '^(daily|weekly)-\d{8}T(\d{6}Z|\d{9}Z-[0-9a-f]{8})$' -or
        -not $Manifest.backupId.StartsWith(([string]$Manifest.kind) + "-", [StringComparison]::OrdinalIgnoreCase)) {
        throw "Backup identity does not match the selected recovery-point directory or backup kind."
    }
    if ($Manifest.documentSnapshot -ne "documents/" -or
        $Manifest.deletionJournalSnapshot -ne "account-deletions.jsonl" -or
        [string]::IsNullOrWhiteSpace([string]$Manifest.database)) {
        throw "Backup manifest metadata is incomplete or unsupported."
    }
    $created = [DateTimeOffset]::Parse($Manifest.createdUtc)
    if ($created -gt [DateTimeOffset]::UtcNow.AddMinutes(5) -or
        $created -lt [DateTimeOffset]::UtcNow.AddDays(-28)) {
        throw "Recovery point is outside the permitted 28-day age window."
    }
    if ($null -eq $Manifest.files -or @($Manifest.files).Count -lt 2) {
        throw "Backup manifest file inventory is missing."
    }

    $expected = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in @($Manifest.files)) {
        $relative = [string]$entry.path
        if ([string]::IsNullOrWhiteSpace($relative) -or $relative.Contains("\") -or
            $relative.StartsWith("/") -or $relative -match '(^|/)\.\.?(/|$)' -or
            $relative -match '^[A-Za-z]:' -or $relative -match '[\x00-\x1f]') {
            throw "Manifest contains an unsafe relative file path."
        }
        if (-not $expected.Add($relative)) { throw "Manifest contains duplicate file paths." }
        if ($relative -notmatch '^(careerpilot\.dump|account-deletions\.jsonl|documents/(quarantine|available)/[0-9a-fA-F-]{36})$') {
            throw "Manifest contains a file outside the supported backup inventory."
        }
        if ([string]$entry.sha256 -notmatch '^[0-9a-fA-F]{64}$' -or
            $null -eq $entry.bytes -or [long]$entry.bytes -lt 0) {
            throw "Manifest contains invalid file integrity metadata."
        }
        $candidate = [IO.Path]::GetFullPath((Join-Path $BackupDirectory $relative.Replace("/", [IO.Path]::DirectorySeparatorChar)))
        if (-not (Test-PathWithin $candidate $BackupDirectory)) { throw "Manifest file path escapes the selected backup directory." }
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw "A manifest-listed backup file is missing." }
        $item = Get-Item -LiteralPath $candidate -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or $item.Length -ne [long]$entry.bytes) {
            throw "Backup file type or size does not match its manifest."
        }
        if ((Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash -ne $entry.sha256) {
            throw "Backup integrity check failed for a manifest-listed file."
        }
    }

    foreach ($required in @("careerpilot.dump", "account-deletions.jsonl")) {
        if (-not $expected.Contains($required)) { throw "Required backup artifact is absent from the manifest." }
    }
    foreach ($area in @("quarantine", "available")) {
        if (-not (Test-Path -LiteralPath (Join-Path $BackupDirectory "documents/$area") -PathType Container)) {
            throw "The coordinated document snapshot is missing a required storage area."
        }
    }
    $actualDirectories = @(Get-ChildItem -LiteralPath $BackupDirectory -Directory -Recurse -Force |
        ForEach-Object { Get-RelativePath $BackupDirectory $_.FullName.Replace("\", "/") } |
        Sort-Object)
    $allowedDirectories = @("documents", "documents/available", "documents/quarantine")
    if (($actualDirectories -join "|") -ne ($allowedDirectories -join "|")) {
        throw "Backup contains missing or unexpected directories."
    }
    $actualFiles = @(Get-ChildItem -LiteralPath $BackupDirectory -File -Recurse -Force |
        Where-Object { $_.FullName -ne (Join-Path $BackupDirectory "backup-manifest.json") } |
        ForEach-Object { Get-RelativePath $BackupDirectory $_.FullName.Replace("\", "/") })
    if ($actualFiles.Count -ne $expected.Count) { throw "Backup contains missing or unlisted files." }
    foreach ($actual in $actualFiles) {
        if (-not $expected.Contains($actual)) { throw "Backup contains a file not listed in its manifest." }
    }
}
function Invoke-Psql([string]$Sql, [switch]$TuplesOnly) {
    $psqlArgs = @("--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--host=$DatabaseHost", "--port=$DatabasePort",
        "--username=$DatabaseUser", "--dbname=$TargetDatabase", "--command", $Sql)
    if ($TuplesOnly) {
        $psqlArgs = @("--no-psqlrc", "--tuples-only", "--no-align", "--set", "ON_ERROR_STOP=1",
            "--host=$DatabaseHost", "--port=$DatabasePort", "--username=$DatabaseUser", "--dbname=$TargetDatabase", "--command", $Sql)
    }
    $output = & psql.exe @psqlArgs
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL restore replay or verification failed." }
    return $output
}

foreach ($path in @($BackupPath, $CurrentDeletionJournal, $TargetDocumentRoot, $TargetJournalFile, $BackendDirectory)) {
    if (-not [IO.Path]::IsPathFullyQualified($path)) { throw "All paths must be absolute." }
}
if (-not $ConfirmIsolatedDatabase) {
    throw "Confirm that TargetDatabase is a dedicated isolated recovery database using -ConfirmIsolatedDatabase."
}
if (-not $ReplayDeletionJournal) {
    throw "Restore requires -ReplayDeletionJournal so current deletion tombstones are applied before access."
}
if (-not (Test-Path -LiteralPath $CurrentDeletionJournal -PathType Leaf)) {
    throw "The latest independent deletion journal is required. Never replay only the older journal snapshot bundled with a backup."
}
if (-not (Test-Path -LiteralPath (Join-Path $BackendDirectory "mvnw.cmd") -PathType Leaf)) {
    throw "BackendDirectory must contain the repository Maven Wrapper."
}
if (-not $env:DATABASE_PASSWORD) { throw "Set DATABASE_PASSWORD in the environment before restoring." }
foreach ($tool in @("pg_restore.exe", "psql.exe", "robocopy.exe")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is required and must be on PATH." }
}
if ($TargetDatabase -notmatch '(?i)(restore|recovery|drill)' -or
    $TargetDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "TargetDatabase must be a valid, clearly isolated name containing restore, recovery, or drill."
}
if ($DatabaseHost -notin @("127.0.0.1", "localhost", "::1")) {
    throw "Restore is restricted to a loopback PostgreSQL host. Remote database restore is not supported by this local recovery script."
}

$backupFull = (Resolve-Path -LiteralPath $BackupPath).Path
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$targetDocumentFull = [IO.Path]::GetFullPath($TargetDocumentRoot).TrimEnd("\")
$targetJournalFull = [IO.Path]::GetFullPath($TargetJournalFile)
$currentJournalFull = (Resolve-Path -LiteralPath $CurrentDeletionJournal).Path
$targetJournalParent = Split-Path -Parent $targetJournalFull
$backendFull = (Resolve-Path -LiteralPath $BackendDirectory).Path

if (Test-Path -LiteralPath $TargetDocumentRoot) {
    throw "TargetDocumentRoot must not exist. Restore creates a new isolated directory and never mirrors into or overwrites an existing directory."
}
if (Test-Path -LiteralPath $TargetJournalFile) {
    throw "TargetJournalFile must not exist. Restore creates a new isolated journal copy and never overwrites an existing file."
}
if (-not (Test-Path -LiteralPath $targetJournalParent -PathType Container)) {
    throw "TargetJournalFile parent directory must already exist and be independently prepared."
}
if ((Test-PathWithin $targetDocumentFull $repoRoot) -or (Test-PathWithin $targetJournalFull $repoRoot) -or
    (Test-PathWithin $currentJournalFull $repoRoot) -or (Test-PathWithin $backupFull $repoRoot) -or
    (Test-PathWithin $targetDocumentFull $backupFull) -or (Test-PathWithin $backupFull $targetDocumentFull) -or
    (Test-PathWithin $targetJournalFull $backupFull) -or (Test-PathWithin $backupFull $targetJournalFull) -or
    (Test-PathWithin $currentJournalFull $backupFull) -or (Test-PathWithin $currentJournalFull $targetDocumentFull) -or
    (Test-PathWithin $targetDocumentFull $currentJournalFull) -or (Test-PathWithin $targetJournalFull $targetDocumentFull) -or
    (Test-PathWithin $targetDocumentFull $targetJournalFull) -or
    $targetJournalFull.Equals($currentJournalFull, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Restore targets, source journal, backup media, and repository must be isolated from one another."
}

Assert-NoReparsePoints $backupFull
Assert-NoReparsePoints $currentJournalFull
$manifestPath = Join-Path $backupFull "backup-manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Backup manifest is missing." }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
Assert-Manifest $backupFull $manifest
if ($TargetDatabase -eq $manifest.database) { throw "TargetDatabase must not be the source database." }

$dump = Join-Path $backupFull "careerpilot.dump"
$sourceDocuments = Join-Path $backupFull "documents"
$previousPgPassword = $env:PGPASSWORD
$createdDocumentRoot = $false
$createdTargetJournal = $false
$restoreCompleted = $false
try {
    $env:PGPASSWORD = $env:DATABASE_PASSWORD

    # The target DB must exist, be local, and be empty. No --clean option is used.
    $identitySql = "SELECT current_database() || '|' || COALESCE(inet_server_addr()::text,'local-socket')"
    $identity = & psql.exe --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$TargetDatabase --command $identitySql
    if ($LASTEXITCODE -ne 0) { throw "Unable to connect to the isolated target database." }
    $identityParts = "$identity".Trim() -split '\|'
    if ($identityParts.Count -ne 2 -or $identityParts[0] -ne $TargetDatabase -or
        $identityParts[1] -notin @("127.0.0.1", "::1", "local-socket")) {
        throw "Connected PostgreSQL server identity did not match the required local target."
    }
    $objectCountSql = "SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%'"
    $objectCount = & psql.exe --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$TargetDatabase --command $objectCountSql
    if ($LASTEXITCODE -ne 0 -or [int]("$objectCount".Trim()) -ne 0) {
        throw "Target database is not empty or could not be verified. No database objects were changed."
    }

    if (-not (Get-Command "Get-BitLockerVolume" -ErrorAction SilentlyContinue)) {
        throw "BitLocker status cannot be verified for restored data paths."
    }
    foreach ($path in @($backupFull, $currentJournalFull, (Split-Path -Parent $targetDocumentFull), $targetJournalParent)) {
        $resolved = (Resolve-Path -LiteralPath $path).Path
        $drive = [IO.Path]::GetPathRoot($resolved)
        $volume = Get-BitLockerVolume -MountPoint $drive -ErrorAction Stop
        if ($volume.ProtectionStatus -ne "On") {
            throw "Backup, current deletion journal, and restore target volumes must be BitLocker protected."
        }
    }

    & pg_restore.exe --exit-on-error --single-transaction --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$TargetDatabase --no-owner --no-acl $dump
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed with exit code $LASTEXITCODE." }

    New-Item -ItemType Directory -Path $TargetDocumentRoot -ErrorAction Stop | Out-Null
    $createdDocumentRoot = $true
    New-Item -ItemType Directory -Path (Join-Path $TargetDocumentRoot "quarantine") | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $TargetDocumentRoot "available") | Out-Null
    & robocopy.exe $sourceDocuments $TargetDocumentRoot /E /COPY:DAT /DCOPY:DAT /R:2 /W:2 /XJ
    if ($LASTEXITCODE -ge 8) { throw "Document restore failed with robocopy exit code $LASTEXITCODE." }

    Copy-Item -LiteralPath $currentJournalFull -Destination $targetJournalFull -ErrorAction Stop
    $createdTargetJournal = $true

    # Apply Flyway migrations without an HTTP listener or scheduled cleanup workers.
    $oldDatabaseUrl = $env:DATABASE_URL
    $oldStorageRoot = $env:DOCUMENT_STORAGE_ROOT
    $oldJournalFile = $env:ACCOUNT_DELETION_JOURNAL_FILE
    $oldWebType = $env:SPRING_MAIN_WEB_APPLICATION_TYPE
    $oldResumeCleanup = $env:DOCUMENT_CLEANUP_SCHEDULING_ENABLED
    $oldUploadRetention = $env:DOCUMENT_RETENTION_SCHEDULING_ENABLED
    $oldAccountDeletion = $env:ACCOUNT_DELETION_SCHEDULING_ENABLED
    $oldSchedulingEnabled = $env:APP_SCHEDULING_ENABLED
    try {
        $env:DATABASE_URL = "jdbc:postgresql://" + $DatabaseHost + ":" + $DatabasePort + "/" + $TargetDatabase
        $env:DOCUMENT_STORAGE_ROOT = $TargetDocumentRoot
        $env:ACCOUNT_DELETION_JOURNAL_FILE = $TargetJournalFile
        $env:SPRING_MAIN_WEB_APPLICATION_TYPE = "none"
        $env:DOCUMENT_CLEANUP_SCHEDULING_ENABLED = "false"
        $env:DOCUMENT_RETENTION_SCHEDULING_ENABLED = "false"
        $env:ACCOUNT_DELETION_SCHEDULING_ENABLED = "false"
        $env:APP_SCHEDULING_ENABLED = "false"
        Push-Location $backendFull
        try {
            & .\mvnw.cmd -DskipTests spring-boot:run
            if ($LASTEXITCODE -ne 0) { throw "Migration-only application startup failed with exit code $LASTEXITCODE." }
        }
        finally { Pop-Location }
    }
    finally {
        $env:DATABASE_URL = $oldDatabaseUrl
        $env:DOCUMENT_STORAGE_ROOT = $oldStorageRoot
        $env:ACCOUNT_DELETION_JOURNAL_FILE = $oldJournalFile
        $env:SPRING_MAIN_WEB_APPLICATION_TYPE = $oldWebType
        $env:DOCUMENT_CLEANUP_SCHEDULING_ENABLED = $oldResumeCleanup
        $env:DOCUMENT_RETENTION_SCHEDULING_ENABLED = $oldUploadRetention
        $env:ACCOUNT_DELETION_SCHEDULING_ENABLED = $oldAccountDeletion
        $env:APP_SCHEDULING_ENABLED = $oldSchedulingEnabled
    }

    $journalOperationIds = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $entries = Get-Content -LiteralPath $TargetJournalFile | Where-Object { $_.Trim().Length -gt 0 } | ForEach-Object {
        $entry = $_ | ConvertFrom-Json
        $parsedOperation = [guid]::Empty
        $parsedOwner = [guid]::Empty
        $parsedAsset = [guid]::Empty
        if ($entry.schemaVersion -ne 1 -or -not [guid]::TryParse($entry.operationId, [ref]$parsedOperation) -or
            -not [guid]::TryParse($entry.ownerId, [ref]$parsedOwner)) {
            throw "Deletion journal contains an invalid or unsupported record; restore halted."
        }
        if (-not $journalOperationIds.Add($parsedOperation.ToString())) {
            throw "Deletion journal contains a duplicate operation ID; restore halted."
        }
        $recordedAt = [DateTimeOffset]::MinValue
        if (-not [DateTimeOffset]::TryParse([string]$entry.recordedAt, [ref]$recordedAt)) {
            throw "Deletion journal contains an invalid timestamp; restore halted."
        }
        if ($entry.deletionType -eq "ASSET") {
            if (-not [guid]::TryParse($entry.assetId, [ref]$parsedAsset)) { throw "Asset tombstone is missing a valid asset ID." }
        } elseif ($entry.deletionType -ne "ACCOUNT" -or $null -ne $entry.assetId) {
            throw "Deletion journal event type is invalid; restore halted."
        }
        [pscustomobject]@{ operationId = $parsedOperation; ownerId = $parsedOwner; deletionType = $entry.deletionType; assetId = $parsedAsset }
    }

    foreach ($entry in $entries) {
        $owner = $entry.ownerId.ToString()
        if ($entry.deletionType -eq "ACCOUNT") {
            $keys = Invoke-Psql "SELECT storage_key FROM resume_assets WHERE user_id='$owner'::uuid AND storage_key IS NOT NULL UNION SELECT storage_key FROM resume_upload_cleanup_jobs WHERE owner_id='$owner'::uuid AND storage_key IS NOT NULL" -TuplesOnly
        } else {
            $asset = $entry.assetId.ToString()
            $keys = Invoke-Psql "SELECT storage_key FROM resume_assets WHERE id='$asset'::uuid AND user_id='$owner'::uuid AND storage_key IS NOT NULL" -TuplesOnly
        }
        foreach ($key in $keys) {
            $value = "$key".Trim()
            if ($value -notmatch '^[0-9a-fA-F-]{36}$') { throw "Unexpected non-opaque storage key in restored database; restore halted." }
            foreach ($area in @("quarantine", "available")) {
                $objectPath = Join-Path (Join-Path $TargetDocumentRoot $area) $value
                if (Test-Path -LiteralPath $objectPath) { Remove-Item -LiteralPath $objectPath -Force }
            }
        }

        if ($entry.deletionType -eq "ASSET") {
            $asset = $entry.assetId.ToString()
            $sql = @"
BEGIN;
DELETE FROM resume_deletion_jobs WHERE operation_id='$([string]$entry.operationId)'::uuid AND owner_id='$owner'::uuid;
DELETE FROM resume_assets WHERE id='$asset'::uuid AND user_id='$owner'::uuid;
COMMIT;
"@
        } else {
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
        }
        Invoke-Psql $sql | Out-Null
    }

    # Reconcile every remaining database asset against exactly one private object.
    $assetRows = Invoke-Psql "SELECT id::text || '|' || storage_key || '|' || status || '|' || checksum_sha256 || '|' || size_bytes::text FROM resume_assets WHERE storage_key IS NOT NULL" -TuplesOnly
    $referenced = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($row in $assetRows) {
        $parts = "$row".Trim() -split '\|'
        if ($parts.Count -ne 5 -or $parts[1] -notmatch '^[0-9a-fA-F-]{36}$' -or $parts[3] -notmatch '^[0-9a-fA-F]{64}$') {
            throw "Restored asset metadata contains an invalid storage key or checksum."
        }
        $key = $parts[1]
        if (-not $referenced.Add($key)) { throw "Multiple restored assets reference the same storage object." }
        $available = Join-Path (Join-Path $TargetDocumentRoot "available") $key
        $quarantine = Join-Path (Join-Path $TargetDocumentRoot "quarantine") $key
        $hasAvailable = Test-Path -LiteralPath $available -PathType Leaf
        $hasQuarantine = Test-Path -LiteralPath $quarantine -PathType Leaf
        if ($hasAvailable -eq $hasQuarantine) { throw "A restored asset must have exactly one matching private object." }
        $object = if ($hasAvailable) { $available } else { $quarantine }
        $item = Get-Item -LiteralPath $object -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            $item.Length -ne [long]$parts[4] -or
            (Get-FileHash -LiteralPath $object -Algorithm SHA256).Hash -ne $parts[3]) {
            throw "Restored asset object does not match its database size or SHA-256 checksum."
        }
        if ($parts[2] -eq "AVAILABLE" -and -not $hasAvailable) {
            throw "An AVAILABLE asset is not in the available storage area."
        }
    }
    foreach ($area in @("available", "quarantine")) {
        foreach ($object in Get-ChildItem -LiteralPath (Join-Path $TargetDocumentRoot $area) -File -Force) {
            if (-not $referenced.Contains($object.Name)) {
                throw "Unreferenced document object found after deletion replay; recovery halted for manual review."
            }
        }
    }

    $restoreCompleted = $true
    Write-Output "Restore and deletion-journal replay completed. Keep the application stopped until ownership and authenticated access checks pass."
    Write-Output "Recovery point: $($manifest.backupId) ($($manifest.createdUtc))"
    Write-Output "Deletion tombstones replayed: $($entries.Count)"
    Write-Output "Restored assets reconciled: $($referenced.Count)"
}
finally {
    if ($null -eq $previousPgPassword) {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    } else {
        $env:PGPASSWORD = $previousPgPassword
    }
    if (-not $restoreCompleted) {
        if ($createdTargetJournal -and (Test-Path -LiteralPath $TargetJournalFile)) {
            Remove-Item -LiteralPath $TargetJournalFile -Force
        }
        if ($createdDocumentRoot -and (Test-Path -LiteralPath $TargetDocumentRoot)) {
            Remove-Item -LiteralPath $TargetDocumentRoot -Recurse -Force
        }
    }
}