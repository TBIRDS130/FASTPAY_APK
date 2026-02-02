# Build debug APK, install on connected device, and show how to view logs.
# Usage: .\scripts\test-debug.ps1 [VersionFolder] [-InstallOnly]
#   VersionFolder: e.g. FASTPAY_BASE (default)
#   -InstallOnly: skip build; install existing APK from version\app\build\outputs\apk\debug\ if present
# Cursor/agent can run this to test changes (build + install + logcat hint).

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$InstallOnly = $false
$version = $null
foreach ($a in $args) {
    if ($a -eq "-InstallOnly") { $InstallOnly = $true } else { $version = $a }
}
if (-not $version) { $version = "FASTPAY_BASE" }

$versionPath = Join-Path $Root $version
$apkDir = Join-Path $versionPath "app\build\outputs\apk\debug"
$apk = Get-ChildItem -Path $apkDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1

Write-Host "[1/3] Checking for connected device..." -ForegroundColor Cyan
$devices = adb devices 2>$null | Where-Object { $_ -match "device$" }
if (-not $devices -or ($devices | Measure-Object -Line).Lines -le 1) {
    Write-Host "No device/emulator connected. Run: adb devices" -ForegroundColor Yellow
    $doInstall = $false
} else {
    $doInstall = $true
}

if ($InstallOnly -and $apk) {
    Write-Host "[2/3] Skipping build (-InstallOnly). Using: $($apk.Name)" -ForegroundColor Cyan
} elseif (-not $InstallOnly) {
    if (-not (Test-Path (Join-Path $versionPath "gradlew.bat"))) {
        Write-Host "Not a Gradle project: $versionPath" -ForegroundColor Red
        exit 1
    }
    Write-Host "[2/3] Building debug APK ($version)..." -ForegroundColor Cyan
    Set-Location $versionPath
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed. Try: Android Studio Build -> Build APK(s), or run with -InstallOnly if APK exists." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    $apk = Get-ChildItem -Path $apkDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
}

if ($doInstall -and $apk) {
    Write-Host "[3/3] Installing on device: $($apk.FullName)" -ForegroundColor Cyan
    adb install -r $apk.FullName
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Install failed." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "Installed. Launch app: adb shell am start -n com.example.fast/.ui.SplashActivity" -ForegroundColor Gray
    Write-Host "View activation debug logs:" -ForegroundColor Green
    Write-Host "  adb logcat -s ActivationActivity:D" -ForegroundColor White
    Write-Host "Or app logs: adb logcat | findstr /i fast" -ForegroundColor Gray
} elseif ($doInstall -and -not $apk) {
    Write-Host "[3/3] No APK found at $apkDir. Build first or build from Android Studio." -ForegroundColor Yellow
} else {
    Write-Host "[3/3] Skipped install (no device). APK: $apkDir\*.apk" -ForegroundColor Gray
}

Write-Host "Done." -ForegroundColor Green
