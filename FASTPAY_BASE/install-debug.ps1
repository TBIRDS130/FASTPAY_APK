# Install FastPay DEBUG APK (has Application + Hilt classes; release/minified APK crashes on launch)
# Run from project root: .\install-debug.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\fastpay-3.0-debug.apk"

if (-not (Test-Path $ApkPath)) {
    Write-Host "Building debug APK first..." -ForegroundColor Cyan
    Set-Location $ProjectRoot
    .\gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) { Write-Host "Build failed." -ForegroundColor Red; exit 1 }
}

Write-Host "Uninstalling old app..." -ForegroundColor Yellow
adb uninstall com.example.fast 2>$null

Write-Host "Installing debug APK..." -ForegroundColor Cyan
adb install $ApkPath
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed." -ForegroundColor Red; exit 1 }

Write-Host "Done. Open FastPay from the launcher (use DEBUG build only; release/minified crashes)." -ForegroundColor Green
