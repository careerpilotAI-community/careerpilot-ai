param(
    [Parameter(Mandatory = $true)][string]$BackupRoot,
    [Parameter(Mandatory = $true)][string]$DocumentRoot,
    [Parameter(Mandatory = $true)][string]$JournalFile,
    [string]$Database = "careerpilot",
    [string]$DatabaseUser = "careerpilot",
    [string]$DatabaseHost = "127.0.0.1",
    [int]$DatabasePort = 5432,
    [switch]$Weekly,
    [switch]$MaintenanceConfirmed
)

$ErrorActionPreference = "Stop"

function Assert-AbsolutePath([string]$Path, [string]$Name) {
    if (-not [IO.Path]::IsPathFullyQualified($Path)) { throw "$Name must be an absolute path." }
}
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
            throw "Reparse points and symbolic links are not allowed in backup source paths."
        }
    }
}
function Assert-DocumentTree([string]$Root) {
    foreach ($area in @("quarantine", "available")) {
        $areaPath = Join-Path $Root $area
        if (-not (Test-Path -LiteralPath $areaPath -PathType Container)) {
            throw "DocumentRoot must contain both quarantine and available directories."
        }
    }
    foreach ($entry in Get-ChildItem -LiteralPath $Root -Force) {
        if (-not $entry.PSIsContainer -or $entry.Name -notin @("quarantine", "available")) {
            throw "DocumentRoot may contain only the quarantine and available storage areas."
        }
    }
    foreach ($area in @("quarantine", "available")) {
        foreach ($entry in Get-ChildItem -LiteralPath (Join-Path $Root $area) -Force -Recurse) {
            if ($entry.PSIsContainer -or $entry.Name -notmatch '^[0-9a-fA-F-]{36}$') {
                throw "Document storage must contain only flat opaque UUID-named files."
            }
        }
    }
}

Assert-AbsolutePath $BackupRoot "BackupRoot"
Assert-AbsolutePath $DocumentRoot "DocumentRoot"
Assert-AbsolutePath $JournalFile "JournalFile"
if (-not $MaintenanceConfirmed) {
    throw "Maintenance confirmation is required. Stop the API and all upload, retention, and deletion workers, then rerun with -MaintenanceConfirmed. This switch is an operator attestation, not an automatic process check."
}
foreach ($tool in @("pg_dump.exe", "psql.exe", "robocopy.exe")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is required and must be on PATH." }
}
if (-not $env:DATABASE_PASSWORD) { throw "Set DATABASE_PASSWORD in the environment before backup." }
if (-not (Test-Path -LiteralPath $DocumentRoot -PathType Container)) { throw "DocumentRoot does not exist." }
if (-not (Test-Path -LiteralPath $JournalFile -PathType Leaf)) { throw "The independent deletion journal file does not exist." }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$backupCandidate = [IO.Path]::GetFullPath($BackupRoot).TrimEnd("\")
$documentsFull = (Resolve-Path $DocumentRoot).Path
$journalFull = (Resolve-Path $JournalFile).Path
$journalDirectory = (Resolve-Path (Split-Path -Parent $JournalFile)).Path

# Validate the requested backup location before creating it, so an invalid path
# inside the repository or overlapping private storage causes no filesystem mutation.
if ((Test-PathWithin $backupCandidate $repoRoot) -or (Test-PathWithin $documentsFull $repoRoot) -or
    (Test-PathWithin $journalDirectory $repoRoot)) {
    throw "Backup, document, and journal paths must be outside the Git repository."
}
if ((Test-PathWithin $backupCandidate $documentsFull) -or (Test-PathWithin $documentsFull $backupCandidate) -or
    (Test-PathWithin $backupCandidate $journalDirectory) -or (Test-PathWithin $journalDirectory $backupCandidate) -or
    (Test-PathWithin $documentsFull $journalDirectory) -or (Test-PathWithin $journalDirectory $documentsFull)) {
    throw "Backup, document, and deletion-journal locations must be separate, independent paths."
}

New-Item -ItemType Directory -Force -Path $backupCandidate | Out-Null
Assert-NoReparsePoints $backupCandidate
$backupFull = (Resolve-Path $backupCandidate).Path
Assert-NoReparsePoints $backupFull
Assert-NoReparsePoints $documentsFull
Assert-NoReparsePoints $journalFull
Assert-DocumentTree $documentsFull

if ((Test-PathWithin $backupFull $repoRoot) -or (Test-PathWithin $backupFull $documentsFull) -or
    (Test-PathWithin $backupFull $journalDirectory)) {
    throw "Resolved backup path must remain outside the repository and separate from application storage and journal paths."
}
if (-not (Get-Command "Get-BitLockerVolume" -ErrorAction SilentlyContinue)) {
    throw "BitLocker status cannot be verified. Use an encrypted Windows volume and verify it before backup."
}
foreach ($path in @($backupFull, $documentsFull, $journalDirectory)) {
    $drive = [IO.Path]::GetPathRoot($path)
    $volume = Get-BitLockerVolume -MountPoint $drive -ErrorAction Stop
    if ($volume.ProtectionStatus -ne "On") {
        throw "Backup, application-data, and deletion-journal volumes must have BitLocker protection enabled."
    }
}
if ((Test-PathWithin $backupFull $documentsFull) -or (Test-PathWithin $documentsFull $backupFull) -or
    (Test-PathWithin $backupFull $journalDirectory) -or (Test-PathWithin $journalDirectory $backupFull) -or
    (Test-PathWithin $documentsFull $journalDirectory) -or (Test-PathWithin $journalDirectory $documentsFull)) {
    throw "Backup, document, and deletion-journal locations must be separate, independent paths."
}

$previousPgPassword = $env:PGPASSWORD
$stage = $null
$stageCreated = $false
try {
    $env:PGPASSWORD = $env:DATABASE_PASSWORD

    # The current psql connection is excluded. Any other session can indicate that the
    # API, a worker, or another operator is still connected; fail closed for consistency.
    $maintenanceSql = "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()"
    $otherSessions = & psql.exe --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$Database --command $maintenanceSql
    if ($LASTEXITCODE -ne 0) { throw "Unable to verify database maintenance state; backup refused." }
    if ([int]("$otherSessions".Trim()) -gt 0) {
        throw "Other PostgreSQL sessions are connected to the source database. Stop the API/workers and retry."
    }

    # Never snapshot unresolved deletion work: its tombstone must first reach the sidecar journal.
    $pendingSql = "SELECT (SELECT COUNT(*) FROM resume_deletion_jobs WHERE job_status <> 'COMPLETED') + (SELECT COUNT(*) FROM account_deletion_jobs WHERE owner_id IS NOT NULL) + (SELECT COUNT(*) FROM resume_upload_cleanup_jobs WHERE job_status IN ('IN_PROGRESS','PENDING','PROCESSING','DEAD_LETTER'))"
    $pending = & psql.exe --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$Database --command $pendingSql
    if ($LASTEXITCODE -ne 0) { throw "Unable to verify deletion queues; backup refused." }
    if ([int]("$pending".Trim()) -gt 0) { throw "Unresolved individual/account deletion or upload-cleanup jobs exist. Resolve all jobs, then retry backup." }

    $kind = if ($Weekly) { "weekly" } else { "daily" }
    $stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssfffZ")
    $backupId = "$kind-$stamp-$([guid]::NewGuid().ToString('N').Substring(0,8))"
    $stage = Join-Path $backupFull ".$backupId-staging"
    $final = Join-Path $backupFull $backupId
    if (Test-Path -LiteralPath $stage) { throw "Unique staging path unexpectedly already exists; refusing to remove or reuse it." }
    if (Test-Path -LiteralPath $final) { throw "Final backup path already exists; refusing to overwrite it." }
    New-Item -ItemType Directory -Path $stage -ErrorAction Stop | Out-Null
    $stageCreated = $true
    New-Item -ItemType Directory -Path (Join-Path $stage "documents") | Out-Null

    $dumpPath = Join-Path $stage "careerpilot.dump"
    & pg_dump.exe --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$Database --format=custom --no-owner --no-acl --file=$dumpPath
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE." }

    & robocopy.exe $documentsFull (Join-Path $stage "documents") /E /COPY:DAT /DCOPY:DAT /R:2 /W:2 /XJ
    if ($LASTEXITCODE -ge 8) { throw "Document copy failed with robocopy exit code $LASTEXITCODE." }

    Copy-Item -LiteralPath $journalFull -Destination (Join-Path $stage "account-deletions.jsonl")
    $files = Get-ChildItem -LiteralPath $stage -File -Recurse | ForEach-Object {
        [pscustomobject]@{
            path = Get-RelativePath $stage $_.FullName.Replace("\", "/")
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            bytes = $_.Length
        }
    }
    $manifest = [ordered]@{
        schemaVersion = 1
        backupId = $backupId
        createdUtc = (Get-Date).ToUniversalTime().ToString("o")
        kind = $kind
        database = $Database
        documentSnapshot = "documents/"
        deletionJournalSnapshot = "account-deletions.jsonl"
        dailyRecoveryPoints = 7
        weeklyRecoveryPoints = 4
        maximumBackupErasureDays = 28
        files = @($files)
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $stage "backup-manifest.json") -Encoding UTF8
    Move-Item -LiteralPath $stage -Destination $final -ErrorAction Stop
    $stageCreated = $false

    # Enforce the approved maximum age, then retain the newest 7 daily and 4 weekly sets.
    $all = Get-ChildItem -LiteralPath $backupFull -Directory | Where-Object {
        $_.Name -match '^(daily|weekly)-\d{8}T(\d{6}Z|\d{9}Z-[0-9a-f]{8})$'
    }
    $cutoff = (Get-Date).ToUniversalTime().AddDays(-28)
    foreach ($item in $all) {
        if ($item.LastWriteTimeUtc -lt $cutoff) { Remove-Item -LiteralPath $item.FullName -Recurse -Force }
    }
    foreach ($category in @("daily", "weekly")) {
        $limit = if ($category -eq "daily") { 7 } else { 4 }
        $sets = Get-ChildItem -LiteralPath $backupFull -Directory |
            Where-Object { $_.Name -match "^$category-\d{8}T(\d{6}Z|\d{9}Z-[0-9a-f]{8})$" } |
            Sort-Object Name -Descending
        $sets | Select-Object -Skip $limit | ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }
    }
    Write-Output "Backup created: $final"
}
catch {
    if ($stageCreated -and $stage -and (Test-Path -LiteralPath $stage)) {
        Remove-Item -LiteralPath $stage -Recurse -Force
    }
    throw
}
finally {
    if ($null -eq $previousPgPassword) {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    } else {
        $env:PGPASSWORD = $previousPgPassword
    }
}
