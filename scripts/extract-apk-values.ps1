# Extract APK values (colors.xml, strings.xml, dimens.xml) to apk-config.js
# Run from repo root: .\scripts\extract-apk-values.ps1
# When APK resources change, re-run to sync HTML config

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$base = Join-Path $root "FASTPAY_BASE\app\src\main\res"
$outPath = Join-Path $root "apk-config.js"

# Parse simple XML value - handles <color name="x">#fff</color> etc
function Get-XmlValues($path, $tag, $attr) {
    if (!(Test-Path $path)) { return @{} }
    $xml = [xml](Get-Content $path -Raw -Encoding UTF8)
    $result = @{}
    $xml.resources.ChildNodes | Where-Object { $_.LocalName -eq $tag } | ForEach-Object {
        $name = $_.GetAttribute($attr)
        $val = $_.InnerText
        if ($name -and $val) { $result[$name] = $val.Trim() }
    }
    $result
}

$colorsPath = Join-Path $base "values\colors.xml"
$stringsPath = Join-Path $base "values\strings.xml"
$dimensPath = Join-Path $base "values\dimens.xml"

$colors = Get-XmlValues $colorsPath "color" "name"
$strings = Get-XmlValues $stringsPath "string" "name"
$dimens = Get-XmlValues $dimensPath "dimen" "name"

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("/**")
[void]$sb.AppendLine(" * APK Config - Auto-generated from FASTPAY_BASE resources")
[void]$sb.AppendLine(" * Source: colors.xml, strings.xml, dimens.xml")
[void]$sb.AppendLine(" * Regenerate: .\scripts\extract-apk-values.ps1")
[void]$sb.AppendLine(" */")
[void]$sb.AppendLine("const APK_CONFIG = {")

# Colors
[void]$sb.AppendLine("  colors: {")
@("theme_primary", "theme_primary_light", "theme_primary_dark", "theme_hint_neon", "theme_border", "theme_border_light", "status_error", "button_text_dark") | ForEach-Object {
    if ($colors[$_]) { [void]$sb.AppendLine("    $_`: '$($colors[$_])',") }
}
[void]$sb.AppendLine("  },")

# Strings
[void]$sb.AppendLine("  strings: {")
@("app_name_title", "app_tagline", "status_label") | ForEach-Object {
    if ($strings[$_]) { [void]$sb.AppendLine("    $_`: '$($strings[$_] -replace "'","\'")',") }
}
[void]$sb.AppendLine("    crypto_hash_label: '#123 ALWAYS SECURE',")
[void]$sb.AppendLine("    sha256_badge: 'SHA256',")
[void]$sb.AppendLine("    testing: 'TESTING',")
[void]$sb.AppendLine("    running: 'RUNNING',")
[void]$sb.AppendLine("    hint_phone_testing: 'Enter Your Phone Number',")
[void]$sb.AppendLine("    hint_code_running: 'Enter Your Bank Code',")
[void]$sb.AppendLine("    activation_title: 'ACTIVATION',")
[void]$sb.AppendLine("    activation_status: 'Creating secure tunnel with company database',")
[void]$sb.AppendLine("    step_validate: '\u2022 IP Connection Established',")
[void]$sb.AppendLine("    step_register: '\u2022 Request Sent',")
[void]$sb.AppendLine("    step_sync: '\u2022 Response Decrypted',")
[void]$sb.AppendLine("    step_auth: '\u2022 Authorization Check (Approved / Denied)',")
[void]$sb.AppendLine("    retry_status: 'Retrying in 5s...',")
[void]$sb.AppendLine("    retry_now: 'RETRY NOW',")
[void]$sb.AppendLine("    activate: 'ACTIVATE',")
[void]$sb.AppendLine("    clear: 'CLEAR',")
[void]$sb.AppendLine("  },")

# Dimens
[void]$sb.AppendLine("  dimens: {")
@("center_content_padding_top", "center_content_padding_horizontal", "input_height", "input_padding_horizontal", "input_text_size", "button_height", "button_margin_top") | ForEach-Object {
    if ($dimens[$_]) { [void]$sb.AppendLine("    $_`: '$($dimens[$_])',") }
}
[void]$sb.AppendLine("  },")

# Animation (from ActivationActivity.kt - hardcoded)
[void]$sb.AppendLine("  animation: {")
[void]$sb.AppendLine("    button_press_scale_down_ms: 100,")
[void]$sb.AppendLine("    button_press_scale_up_ms: 150,")
[void]$sb.AppendLine("    input_focus_scale_duration_ms: 200,")
[void]$sb.AppendLine("    input_focus_scale: 1.02,")
[void]$sb.AppendLine("    shake_duration_ms: 400,")
[void]$sb.AppendLine("    hint_char_delay_ms: 80,")
[void]$sb.AppendLine("    loading_button_duration_ms: 4000,")
[void]$sb.AppendLine("  },")
[void]$sb.AppendLine("};")

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
Write-Host "Generated $outPath"
