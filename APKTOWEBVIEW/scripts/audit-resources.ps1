# Audit resources: list anim/, drawable/, values/ referenced by layouts
# Run: .\audit-resources.ps1 [-ApkRoot path] [-Layouts name1,name2,...]
# Config: apk-mapping.json (apk_root)
# Output: resource inventory to console

param(
    [string]$ApkRoot,
    [string]$Layouts
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$apktoWebview = Split-Path -Parent $scriptDir

# Load config
$configPath = Join-Path $apktoWebview "apk-mapping.json"
$config = @{}
if (Test-Path $configPath) {
    try { $config = Get-Content $configPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { }
}

# Resolve APK root
$apkRootRaw = if ($ApkRoot) { $ApkRoot }
    elseif ($env:APK_ROOT) { $env:APK_ROOT }
    elseif ($config.apk_root) { $config.apk_root }
    else { Join-Path (Split-Path -Parent $apktoWebview) "FASTPAY_BASE" }

$resolvedApkRoot = if ([System.IO.Path]::IsPathRooted($apkRootRaw)) {
    $apkRootRaw
} else {
    [System.IO.Path]::GetFullPath((Join-Path $apktoWebview $apkRootRaw))
}

$resDir = Join-Path $resolvedApkRoot "app\src\main\res"
$layoutDir = Join-Path $resDir "layout"

$layoutNames = if ($Layouts) { $Layouts -split ',' } else {
    (Get-ChildItem -Path $layoutDir -Filter "*.xml" -File -ErrorAction SilentlyContinue).Name
}

$refs = @{
    anim = @()
    drawable = @()
    color = @()
    string = @()
    dimen = @()
}

foreach ($name in $layoutNames) {
    $path = Join-Path $layoutDir $name
    if (!(Test-Path $path)) { continue }
    $content = Get-Content $path -Raw -Encoding UTF8
    [regex]::Matches($content, '@anim/([^"''\s]+)') | ForEach-Object { $refs.anim += $_.Groups[1].Value }
    [regex]::Matches($content, '@drawable/([^"''\s]+)') | ForEach-Object { $refs.drawable += $_.Groups[1].Value }
    [regex]::Matches($content, '@color/([^"''\s]+)') | ForEach-Object { $refs.color += $_.Groups[1].Value }
    [regex]::Matches($content, '@string/([^"''\s]+)') | ForEach-Object { $refs.string += $_.Groups[1].Value }
    [regex]::Matches($content, '@dimen/([^"''\s]+)') | ForEach-Object { $refs.dimen += $_.Groups[1].Value }
}

Write-Host "Resource inventory (referenced by layouts)"
Write-Host ("=" * 50)
Write-Host "`nAnim:"; ($refs.anim | Sort-Object -Unique) -join ", "
Write-Host "`nDrawable:"; ($refs.drawable | Sort-Object -Unique) -join ", "
Write-Host "`nColor:"; ($refs.color | Sort-Object -Unique) -join ", "
Write-Host "`nString:"; ($refs.string | Sort-Object -Unique) -join ", "
Write-Host "`nDimen:"; ($refs.dimen | Sort-Object -Unique) -join ", "

# List actual files in res/anim, res/animator, res/drawable, res/font
Write-Host "`n--- Files in res/anim ---"
$animDir = Join-Path $resDir "anim"
if (Test-Path $animDir) {
    Get-ChildItem -Path $animDir -Filter "*.xml" -File | ForEach-Object { Write-Host $_.Name }
}
Write-Host "`n--- Files in res/animator ---"
$animatorDir = Join-Path $resDir "animator"
if (Test-Path $animatorDir) {
    Get-ChildItem -Path $animatorDir -Filter "*.xml" -File | ForEach-Object { Write-Host $_.Name }
} else { Write-Host "(none)" }
Write-Host "`n--- Files in res/font ---"
$fontDir = Join-Path $resDir "font"
if (Test-Path $fontDir) {
    Get-ChildItem -Path $fontDir -File | ForEach-Object { Write-Host $_.Name }
} else { Write-Host "(none)" }
