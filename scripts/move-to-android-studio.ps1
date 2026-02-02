# Move/copy FASTPAY_APK to AndroidStudioProjects for a better build environment.
# Run from repo root or scripts folder.
#
# Usage: .\scripts\move-to-android-studio.ps1 [-Move] [-Destination <path>]
#   -Move       Move instead of copy (removes source after successful copy)
#   -Destination  Custom destination (default: $env:USERPROFILE\AndroidStudioProjects\FASTPAY_APK)

param(
    [switch]$Move,
    [string]$Destination = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ProjectName = Split-Path -Leaf $Root

if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path (Join-Path $env:USERPROFILE "AndroidStudioProjects") $ProjectName
}

$Source = $Root
$DestParent = Split-Path -Parent $Destination

# Validate source
if (-not (Test-Path $Source)) {
    Write-Host "Source not found: $Source" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path (Join-Path $Source "FASTPAY_BASE"))) {
    Write-Host "Not a FASTPAY_APK project (FASTPAY_BASE folder missing)." -ForegroundColor Red
    exit 1
}

# Check destination
if (Test-Path $Destination) {
    Write-Host "Destination already exists: $Destination" -ForegroundColor Yellow
    Write-Host "Delete it first, or choose a different path with -Destination." -ForegroundColor Yellow
    exit 1
}

# Ensure parent exists
if (-not (Test-Path $DestParent)) {
    New-Item -ItemType Directory -Path $DestParent -Force | Out-Null
    Write-Host "Created: $DestParent" -ForegroundColor Gray
}

Write-Host "Source:      $Source" -ForegroundColor Cyan
Write-Host "Destination: $Destination" -ForegroundColor Cyan
Write-Host ""

if ($Move) {
    Write-Host "Moving project (this may take a moment)..." -ForegroundColor Cyan
    Move-Item -Path $Source -Destination $Destination -Force
    Write-Host "Done. Project moved to:" -ForegroundColor Green
} else {
    Write-Host "Copying project (this may take a moment)..." -ForegroundColor Cyan
    Copy-Item -Path $Source -Destination $Destination -Recurse -Force
    Write-Host "Done. Project copied to:" -ForegroundColor Green
}

Write-Host "  $Destination" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Open Android Studio → File → Open → $Destination"
Write-Host "  2. Build APK from the new location"
if ($Move) {
    Write-Host "  3. Close this terminal (current path no longer exists) or cd to the new path"
} else {
    Write-Host "  3. Remove the old copy when satisfied: $Source"
}
