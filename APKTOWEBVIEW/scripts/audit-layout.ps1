# Audit layout: parse layout XML for view inventory (id, class, purpose)
# Run: .\audit-layout.ps1 [-ApkRoot path] [-LayoutName name]
# Config: apk-mapping.json (apk_root)
# Output: view inventory to console

param(
    [string]$ApkRoot,
    [string]$LayoutName
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

$layoutDir = Join-Path $resolvedApkRoot "app\src\main\res\layout"

function Get-ViewInventory($layoutPath) {
    if (!(Test-Path $layoutPath)) { return @() }
    $content = Get-Content $layoutPath -Raw -Encoding UTF8
    $inventory = @()
    # Match views: <Tag android:id="@+id/xxx" or <com.example.ClassName android:id="..."
    $pattern = '<([\w.]+)[^>]*android:id="@\+id/([^"]+)"'
    [regex]::Matches($content, $pattern) | ForEach-Object {
        $class = $_.Groups[1].Value
        $id = $_.Groups[2].Value
        $inventory += [PSCustomObject]@{ Id = $id; Class = $class }
    }
    $inventory
}

if ($LayoutName) {
    $layoutPath = Join-Path $layoutDir $LayoutName
    if (!(Test-Path $layoutPath)) { Write-Error "Layout not found: $layoutPath"; exit 1 }
    Write-Host "Layout: $LayoutName"
    Write-Host "---"
    Get-ViewInventory $layoutPath | Format-Table -AutoSize
} else {
    $layouts = Get-ChildItem -Path $layoutDir -Filter "*.xml" -File -ErrorAction SilentlyContinue
    foreach ($f in $layouts) {
        Write-Host "`nLayout: $($f.Name)"
        Write-Host ("-" * 40)
        Get-ViewInventory $f.FullName | Format-Table -AutoSize
    }
}
