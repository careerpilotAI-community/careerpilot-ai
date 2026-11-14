param(
    [string]$OutputPath = (Join-Path $env:TEMP "careerpilot-synthetic-resume.pdf"),
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    throw "OutputPath must be a non-empty path."
}

$fullPath = [IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $fullPath
if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
if ((Test-Path -LiteralPath $fullPath) -and -not $Force) {
    throw "Output already exists. Choose another path or pass -Force to replace this synthetic fixture."
}

# The local development scanner accepts only this exact marker in the first 4 KiB.
# The marker is a legal PDF comment. This file contains no real personal information.
$encoding = [Text.Encoding]::ASCII
$pdf = "%PDF-1.4`n%CAREERPILOT-SYNTHETIC-TEST-FIXTURE`n"
$offsets = [System.Collections.Generic.List[int]]::new()

$objects = @(
    "1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj`n",
    "2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj`n",
    "3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> /Contents 4 0 R >>`nendobj`n",
    "4 0 obj`n<< /Length 0 >>`nstream`n`nendstream`nendobj`n"
)

foreach ($object in $objects) {
    $offsets.Add($encoding.GetByteCount($pdf))
    $pdf += $object
}

$xrefOffset = $encoding.GetByteCount($pdf)
$pdf += "xref`n0 5`n"
$pdf += "0000000000 65535 f `n"
foreach ($offset in $offsets) {
    $pdf += ("{0:0000000000} 00000 n `n" -f $offset)
}
$pdf += "trailer`n<< /Size 5 /Root 1 0 R >>`n"
$pdf += "startxref`n$xrefOffset`n%%EOF`n"

[IO.File]::WriteAllBytes($fullPath, $encoding.GetBytes($pdf))
Write-Output "Synthetic PDF created: $fullPath"
Write-Output "Fixture marker is present in the first 4 KiB; no personal data is included."
