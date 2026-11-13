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

Assert-AbsolutePath $BackupRoot "BackupRoot"
Assert-AbsolutePath $DocumentRoot "DocumentRoot"
Assert-AbsolutePath $JournalFile "JournalFile"
if (-not $MaintenanceConfirmed) {
    throw "Stop CareerPilot API and upload/deletion workers, then rerun with -MaintenanceConfirmed. PostgreSQL and filesystem snapshots must be coordinated."
}
foreach ($tool in @("pg_dump.exe", "robocopy.exe")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is required and must be on PATH." }
}
if (-not (Test-Path -LiteralPath $DocumentRoot -PathType Container)) { throw "DocumentRoot does not exist." }
if (-not (Test-Path -LiteralPath $JournalFile -PathType Leaf)) { throw "The independent deletion journal file does not exist." }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null
$backupFull = (Resolve-Path $BackupRoot).Path
$documentsFull = (Resolve-Path $DocumentRoot).Path
$journalFull = (Resolve-Path $JournalFile).Path
$journalDirectory = (Resolve-Path (Split-Path -Parent $JournalFile)).Path
if (Test-PathWithin $backupFull $repoRoot -or Test-PathWithin $documentsFull $repoRoot -or
    Test-PathWithin $journalDirectory $repoRoot) {
    throw "Backup, document, and journal paths must be outside the Git repository."
}
if ((Test-PathWithin $backupFull $documentsFull) -or (Test-PathWithin $documentsFull $backupFull) -or
    (Test-PathWithin $backupFull $journalDirectory) -or (Test-PathWithin $journalDirectory $backupFull) -or
    (Test-PathWithin $documentsFull $journalDirectory) -or (Test-PathWithin $journalDirectory $documentsFull)) {
    throw "Backup, document, and deletion-journal locations must be separate, independent paths."
}

$kind = if ($Weekly) { "weekly" } else { "daily" }
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$backupId = "$kind-$stamp"
$stage = Join-Path $backupFull ".$backupId-staging"
$final = Join-Path $backupFull $backupId
if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage "documents") | Out-Null

try {
    $dumpPath = Join-Path $stage "careerpilot.dump"
    & pg_dump.exe --host=$DatabaseHost --port=$DatabasePort --username=$DatabaseUser --dbname=$Database --format=custom --no-owner --no-acl --file=$dumpPath
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE." }

    & robocopy.exe $documentsFull (Join-Path $stage "documents") /E /COPY:DAT /DCOPY:DAT /R:2 /W:2 /XJ
    if ($LASTEXITCODE -ge 8) { throw "Document copy failed with robocopy exit code $LASTEXITCODE." }

    Copy-Item -LiteralPath $journalFull -Destination (Join-Path $stage "account-deletions.jsonl")
    $files = Get-ChildItem -LiteralPath $stage -File -Recurse | ForEach-Object {
        [pscustomobject]@{
            path = [IO.Path]::GetRelativePath($stage, $_.FullName).Replace("\", "/")
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
    Move-Item -LiteralPath $stage -Destination $final

    # Enforce the approved maximum age, then retain the newest 7 daily and 4 weekly sets.
    $all = Get-ChildItem -LiteralPath $backupFull -Directory | Where-Object { $_.Name -match '^(daily|weekly)-\d{8}T\d{6}Z$' }
    $cutoff = (Get-Date).ToUniversalTime().AddDays(-28)
    foreach ($item in $all) {
        if ($item.LastWriteTimeUtc -lt $cutoff) { Remove-Item -LiteralPath $item.FullName -Recurse -Force }
    }
    foreach ($category in @("daily", "weekly")) {
        $limit = if ($category -eq "daily") { 7 } else { 4 }
        $sets = Get-ChildItem -LiteralPath $backupFull -Directory |
            Where-Object { $_.Name.StartsWith("$category-") } |
            Sort-Object Name -Descending
        $sets | Select-Object -Skip $limit | ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }
    }
    Write-Output "Backup created: $final"
}
catch {
    if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    throw
}
