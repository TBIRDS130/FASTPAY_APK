# TEST BUILD: Build debug APK, install on connected device, or show install command.
# Uses Gradle incremental build: unchanged parts come from cache, only changed parts rebuild (saves time).
# Usage: .\scripts\test-build.ps1 [VersionFolder] [-InstallOnly]
#   VersionFolder: e.g. FASTPAY_BASE (default)
#   -InstallOnly: skip build; install existing APK from version\app\build\outputs\apk\debug\ if present
# Cursor/agent can run this to test changes (build + install + logcat hint).

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path $Root)) {
    Write-Error "Repo root not found: $Root"
    exit 1
}
Set-Location $Root

$InstallOnly = $false
$version = $null
foreach ($a in $args) {
    if ($a -eq "-InstallOnly") { $InstallOnly = $true } else { $version = $a }
}
if (-not $version) { $version = "FASTPAY_BASE" }

$versionPath = Join-Path $Root $version
# CLI build uses build_cli to avoid R.jar lock in app/build (IDE/Defender)
$apkDir = Join-Path $versionPath "app\build_cli\outputs\apk\debug"
$apkDirFallback = Join-Path $versionPath "app\build\outputs\apk\debug"
$apkfileDir = Join-Path $Root "APKFILE"
$apk = Get-ChildItem -Path $apkDir -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apk) { $apk = Get-ChildItem -Path $apkDirFallback -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1 }
$apkInApkfile = Get-ChildItem -Path $apkfileDir -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($apkInApkfile) { $apk = $apkInApkfile }

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
    if (-not (Test-Path $versionPath)) {
        Write-Error "Version path not found: $versionPath"
        exit 1
    }
    Write-Host "[2/3] Building debug APK ($version) (incremental; cache used for unchanged parts)..." -ForegroundColor Cyan
    Set-Location $versionPath
    # Stop daemon and remove R.jar intermediate dir so Gradle can recreate it (avoids "Couldn't delete R.jar" lock).
    & .\gradlew.bat --stop 2>$null
    Start-Sleep -Seconds 2
    $rJarDir = "app\build\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug"
    if (Test-Path $rJarDir) {
        Remove-Item -Recurse -Force $rJarDir -ErrorAction SilentlyContinue
    }
    # -PcliBuildDir: build to app/build_cli to avoid R.jar lock in app/build (IDE/Defender)
    & .\gradlew.bat --no-daemon -PcliBuildDir assembleDebug copyDebugApk
    if ($LASTEXITCODE -ne 0) {
        Write-Host "First build attempt failed. Retrying once after stopping daemon and clearing R.jar dir..." -ForegroundColor Yellow
        & .\gradlew.bat --stop 2>$null
        Start-Sleep -Seconds 2
        if (Test-Path $rJarDir) {
            Remove-Item -Recurse -Force $rJarDir -ErrorAction SilentlyContinue
        }
        & .\gradlew.bat --no-daemon -PcliBuildDir assembleDebug copyDebugApk
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed after retry. If R.jar is locked: close Android Studio (or File -> Close Project), then run this script again." -ForegroundColor Red
        Write-Host "Or try: Android Studio Build -> Build APK(s), or run with -InstallOnly if APK exists." -ForegroundColor Red
        # Do not exit: still show install command below if APK exists or for when device is connected later
    }
    $apk = Get-ChildItem -Path $apkDir -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $apk) { $apk = Get-ChildItem -Path $apkDirFallback -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1 }
    $apkInApkfile = Get-ChildItem -Path $apkfileDir -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($apkInApkfile) { $apk = $apkInApkfile }
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
    Write-Host "[3/3] No APK found. Build first or build from Android Studio." -ForegroundColor Yellow
} else {
    Write-Host "[3/3] No device connected. Skipped install." -ForegroundColor Gray
}

# When no device was connected (or install skipped), show command to install when device is ready
if (-not $doInstall -or (-not $apk -and $doInstall)) {
    $apkForCmd = Get-ChildItem -Path $apkfileDir -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $apkForCmd) { $apkForCmd = Get-ChildItem -Path $apkDir -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1 }
    if (-not $apkForCmd) { $apkForCmd = Get-ChildItem -Path $apkDirFallback -Filter "dfastpay-*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1 }
    if ($apkForCmd) {
        Write-Host ""
        Write-Host "When device is connected, install with:" -ForegroundColor Cyan
        Write-Host "  adb install -r `"$($apkForCmd.FullName)`"" -ForegroundColor White
        Write-Host "Or from repo root:" -ForegroundColor Gray
        Write-Host "  .\scripts\test-build.ps1 -InstallOnly" -ForegroundColor White
    } else {
        Write-Host ""
        Write-Host "When device is connected and APK exists, install with:" -ForegroundColor Cyan
        Write-Host "  adb install -r `"$apkfileDir\dfastpay-*.apk`"" -ForegroundColor White
        Write-Host "Or: .\scripts\test-build.ps1 -InstallOnly" -ForegroundColor Gray
    }
}

Write-Host "Done." -ForegroundColor Green
