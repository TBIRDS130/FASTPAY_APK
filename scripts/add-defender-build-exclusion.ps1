# Add Windows Defender exclusions for FastPay build folders (avoids R.jar / file locks during Gradle build).
# Must run as Administrator (right-click PowerShell -> "Run as administrator", then cd to repo and run).
# Usage: .\scripts\add-defender-build-exclusion.ps1
# Remove exclusions: run Remove-MpPreference -ExclusionPath "C:\path\to\build" (as Admin) or use Defender UI.

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path $Root)) {
    Write-Error "Repo root not found: $Root"
    exit 1
}

$buildDirs = @()
Get-ChildItem -Path $Root -Directory -Filter "FASTPAY_*" -ErrorAction SilentlyContinue | ForEach-Object {
    $appBuild = Join-Path $_.FullName "app\build"
    if (Test-Path (Join-Path $_.FullName "gradlew.bat")) {
        $buildDirs += $appBuild
    }
}
if ($buildDirs.Count -eq 0) {
    Write-Host "No FASTPAY_* Gradle version folders found under $Root" -ForegroundColor Yellow
    exit 0
}

try {
    $prefs = Get-MpPreference -ErrorAction Stop
} catch {
    Write-Host "Could not get Defender preferences. Run this script as Administrator." -ForegroundColor Red
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}

$existing = @($prefs.ExclusionPath)
$toAdd = @()
foreach ($d in $buildDirs) {
    $full = [System.IO.Path]::GetFullPath($d)
    if ($existing -contains $full) {
        Write-Host "Already excluded: $full" -ForegroundColor Gray
    } else {
        $toAdd += $full
    }
}

if ($toAdd.Count -eq 0) {
    Write-Host "All build folders are already excluded. Done." -ForegroundColor Green
    exit 0
}

foreach ($path in $toAdd) {
    try {
        Add-MpPreference -ExclusionPath $path -ErrorAction Stop
        Write-Host "Added exclusion: $path" -ForegroundColor Green
    } catch {
        Write-Host "Failed to add exclusion for $path" -ForegroundColor Red
        Write-Host "Run this script as Administrator (right-click PowerShell -> Run as administrator)." -ForegroundColor Yellow
        Write-Host "Error: $_" -ForegroundColor Red
        exit 1
    }
}

Write-Host "Done. Defender will no longer scan these build folders." -ForegroundColor Green
