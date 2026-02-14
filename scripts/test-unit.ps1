# Run unit tests (no device). From repo root.
# Usage: .\scripts\test-unit.ps1 [VersionFolder]
#   VersionFolder: e.g. FASTPAY_BASE (default)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path $Root)) {
    Write-Error "Repo root not found: $Root"
    exit 1
}
Set-Location $Root

$version = $args[0]
if (-not $version) { $version = "FASTPAY_BASE" }

$versionPath = Join-Path $Root $version
if (-not (Test-Path (Join-Path $versionPath "gradlew.bat"))) {
    Write-Host "Not a Gradle project: $versionPath" -ForegroundColor Red
    exit 1
}

Write-Host "Running unit tests ($version)..." -ForegroundColor Cyan
Set-Location $versionPath
& .\gradlew.bat testDebugUnitTest --no-daemon
$exitCode = $LASTEXITCODE
Set-Location $Root
exit $exitCode
