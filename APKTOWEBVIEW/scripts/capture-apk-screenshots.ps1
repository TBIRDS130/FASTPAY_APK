# Capture APK screenshots from connected device/emulator (Phase F.1)
# Requires: adb, device/emulator running the APK
# Run: .\capture-apk-screenshots.ps1
# Output: baselines/apk/*.png (or outputs/apk/*.png)

param(
    [string]$OutputDir = "baselines\apk",
    [switch]$UseOutputs
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$apktoWebview = Split-Path -Parent $scriptDir
$outDir = if ($UseOutputs) { Join-Path $apktoWebview "outputs\apk" } else { Join-Path $apktoWebview $OutputDir }
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$devices = adb devices 2>$null | Where-Object { $_ -match '\tdevice$' }
if ($devices.Count -eq 0) {
    Write-Warning "No device/emulator connected. Run: adb devices"
    Write-Host "To capture APK baselines:"
    Write-Host "  1. Start emulator or connect device"
    Write-Host "  2. Install and run FastPay APK"
    Write-Host "  3. Navigate to Splash, Activation, Activated"
    Write-Host "  4. Run: adb exec-out screencap -p > screenshot.png"
    exit 1
}

Write-Host "Capturing APK screenshot..."
$tmp = [System.IO.Path]::GetTempFileName() + ".png"
$proc = Start-Process -FilePath "adb" -ArgumentList "exec-out","screencap","-p" -RedirectStandardOutput $tmp -Wait -NoNewWindow -PassThru
if ($proc.ExitCode -ne 0) { Write-Error "adb screencap failed"; exit 1 }
$dest = Join-Path $outDir "apk-screenshot.png"
Copy-Item $tmp $dest -Force
Remove-Item $tmp -ErrorAction SilentlyContinue
Write-Host "Saved: $dest"
Write-Host "Rename/crop per layout (splash, activation, activated) for visual regression baselines."
