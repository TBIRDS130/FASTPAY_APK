# Clean and build FastPay. Works around Windows/OneDrive file locking on gradlew clean.
# Usage: .\scripts\clean-build.ps1 [VersionFolder]
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
        Write-Host "Usage: .\scripts\clean-build.ps1 <VersionFolder>"
        exit 0
    }
}

$versionPath = Join-Path $Root $version
$appBuild = Join-Path $versionPath "app\build"
$gradlew = Join-Path $versionPath "gradlew.bat"

if (-not (Test-Path $gradlew)) {
    Write-Host "Not a Gradle project: $versionPath" -ForegroundColor Red
    exit 1
}

Write-Host "Stopping Gradle daemon..." -ForegroundColor Cyan
Set-Location $versionPath
& .\gradlew.bat --stop 2>$null

Write-Host "Force-cleaning app/build (workaround for Windows/OneDrive locks)..." -ForegroundColor Cyan
if (Test-Path $appBuild) {
    cmd /c "attrib -r -s -h `"$appBuild\*.*`" /s /d 2>nul & rd /s /q `"$appBuild`""
    if (Test-Path $appBuild) {
        Write-Host "Could not delete $appBuild. Close Android Studio, IDEs, and pause OneDrive sync, then retry." -ForegroundColor Red
        exit 1
    }
}
Write-Host "Clean done." -ForegroundColor Green

Write-Host "Building $version (assembleRelease)..." -ForegroundColor Cyan
& .\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Done. APK under $version\app\build\outputs\apk\release\" -ForegroundColor Green
