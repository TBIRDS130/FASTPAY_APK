# Build a FastPay version from FASTPAY_APK root.
# Usage: .\scripts\build-version.ps1 [VersionFolder]
#   VersionFolder: e.g. FASTPAY_BASE (default if only one version exists)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$version = $args[0]
if (-not $version) {
    $folders = @(Get-ChildItem -Directory -Filter "FASTPAY_*" | Where-Object { Test-Path (Join-Path $_.FullName "gradlew.bat") })
    if ($folders.Count -eq 0) {
        Write-Host "No FASTPAY_* version folder with gradlew found." -ForegroundColor Red
        exit 1
    }
    if ($folders.Count -eq 1) {
        $version = $folders[0].Name
    } else {
        Write-Host "Available versions: $($folders.Name -join ', ')"
        Write-Host "Usage: .\scripts\build-version.ps1 <VersionFolder>"
        exit 0
    }
}

$versionPath = Join-Path $Root $version
if (-not (Test-Path (Join-Path $versionPath "gradlew.bat"))) {
    Write-Host "Not a Gradle project: $versionPath" -ForegroundColor Red
    exit 1
}

Write-Host "Building $version (assembleRelease)..." -ForegroundColor Cyan
Set-Location $versionPath
& .\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Copy to repo releases/ if the project defines copyReleaseApk (e.g. FASTPAY_BASE1)
& .\gradlew.bat copyReleaseApk 2>$null
$hasCopyTask = ($LASTEXITCODE -eq 0)

Write-Host "Done. APK outputs:" -ForegroundColor Green
Write-Host "  - $version\app\build\outputs\apk\release\fastpay-*.apk" -ForegroundColor Gray
if ($hasCopyTask) {
    Write-Host "  - releases\fastpay-*.apk (repo root)" -ForegroundColor Gray
}
