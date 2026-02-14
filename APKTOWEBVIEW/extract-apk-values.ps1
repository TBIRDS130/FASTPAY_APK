# Extract APK values from APK resources and layout into apk-config.js
#
# USE: Generates apk-config.js from actual APK code. The HTML demo loads APK_CONFIG.
# Sources: colors.xml, strings.xml, dimens.xml, activity_activation.xml, activity_splash.xml,
# AnimationConstants.kt, SplashActivity.kt, WaveView.kt, ActivationActivity.kt
#
# Run: .\extract-apk-values.ps1  [-ApkRoot path] [-OutputPath path]
# Config: apk-mapping.json (optional, apk_root, output.config)
# Output: APKTOWEBVIEW/apk-config.js

param(
    [string]$ApkRoot,
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
$apktoWebview = $PSScriptRoot

# Load apk-mapping.json if present
$configPath = Join-Path $apktoWebview "apk-mapping.json"
$config = @{}
if (Test-Path $configPath) {
    try {
        $config = Get-Content $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch { }
}

# Resolve APK root: -ApkRoot param > env APK_ROOT > config apk_root > default
$apkRootRaw = if ($ApkRoot) { $ApkRoot }
    elseif ($env:APK_ROOT) { $env:APK_ROOT }
    elseif ($config.apk_root) { $config.apk_root }
    else { Join-Path (Split-Path -Parent $apktoWebview) "FASTPAY_BASE" }

# Resolve path (relative paths from APKTOWEBVIEW)
$resolvedApkRoot = if ([System.IO.Path]::IsPathRooted($apkRootRaw)) {
    $apkRootRaw
} else {
    [System.IO.Path]::GetFullPath((Join-Path $apktoWebview $apkRootRaw))
}

# Resolve output path: -OutputPath param > config output.config > default
$outPathRaw = if ($OutputPath) { $OutputPath }
    elseif ($config.output -and $config.output.config) { $config.output.config }
    else { Join-Path $apktoWebview "apk-config.js" }

$outPath = if ([System.IO.Path]::IsPathRooted($outPathRaw)) {
    $outPathRaw
} else {
    [System.IO.Path]::GetFullPath((Join-Path $apktoWebview $outPathRaw))
}

$base = Join-Path $resolvedApkRoot "app\src\main\res"
$layoutDir = Join-Path $base "layout"
$kotlinPath = Join-Path $resolvedApkRoot "app\src\main\java\com\example\fast\ui\ActivationActivity.kt"

# Discover layouts: scan res/layout/*.xml, apply heuristics, support config override
function Get-DiscoveredLayouts($layoutDir) {
    if (!(Test-Path $layoutDir)) { return @() }
    Get-ChildItem -Path $layoutDir -Filter "*.xml" -File | ForEach-Object { $_.Name }
}

function Get-LayoutScreenMapping($layoutNames, $config) {
    $heuristics = @{
        splash = @('splash', 'intro', 'welcome')
        activation = @('activation')
        activated = @('activated')
        login = @('login', 'auth')
        main = @('main', 'home')
    }
    $mapping = @{}
    foreach ($name in $layoutNames) {
        $lower = $name.ToLowerInvariant()
        if ($lower -match 'activated' -and $lower -notmatch 'activation') { $mapping.activated = $name; continue }
        if ($lower -match 'splash') { $mapping.splash = $name; continue }
        if ($lower -match 'activation') { $mapping.activation = $name; continue }
        if ($lower -match 'login|auth') { $mapping.login = $name; continue }
        if ($lower -match 'main|home') { $mapping.main = $name; continue }
    }
    if ($config.layouts) {
        $config.layouts.PSObject.Properties | ForEach-Object { $mapping[$_.Name] = $_.Value }
    }
    $mapping
}

$discoveredLayouts = Get-DiscoveredLayouts $layoutDir
$layoutMapping = Get-LayoutScreenMapping $discoveredLayouts $config
$activationLayout = if ($layoutMapping.activation) { $layoutMapping.activation } else { "activity_activation.xml" }
$layoutPath = Join-Path $layoutDir $activationLayout

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

# Escape string for JS single-quoted literal
function Escape-JsString($s) {
    if (-not $s) { return "" }
    ($s -replace '\\', '\\' -replace "'", "\'" -replace "`r", '' -replace "`n", '\n').Trim()
}

# Extract android:text and android:hint from activity_activation.xml by view id
function Get-ActivationLayoutStrings($layoutPath, $strings) {
    $result = @{}
    if (!(Test-Path $layoutPath)) { return $result }
    $content = Get-Content $layoutPath -Raw -Encoding UTF8
    $idMatches = [regex]::Matches($content, 'android:id="@\+id/([^"]+)"')
    for ($i = 0; $i -lt $idMatches.Count; $i++) {
        $id = $idMatches[$i].Groups[1].Value
        $start = $idMatches[$i].Index
        $end = if ($i + 1 -lt $idMatches.Count) { $idMatches[$i + 1].Index } else { $content.Length }
        $block = $content.Substring($start, [Math]::Min(2000, $end - $start))
        $val = $null
        if ($block -match 'android:text="@string/([^"]+)"') {
            $key = $Matches[1]
            $val = if ($strings[$key]) { $strings[$key] } else { $null }
        } elseif ($block -match 'android:text="([^"]*)"') {
            $val = $Matches[1]
        } elseif ($block -match 'android:hint="([^"]*)"') {
            $val = $Matches[1]
        }
        if ($val -ne $null -and $val -ne '') { $result[$id] = $val }
    }
    $result
}

# Map layout view ids to apk-config string keys (used by HTML)
$layoutIdToKey = @{
    'activationFormSecurityLabel'    = 'crypto_hash_label'
    'activationFormHashBadge'        = 'sha256_badge'
    'activationModeTestingText'      = 'testing'
    'activationModeRunningText'      = 'running'
    'activationActivateButtonText'   = 'activate'
    'activationClearButtonText'      = 'clear'
    'activationStatusCardTitle'      = 'activation_title'
    'activationStatusCardStatusText' = 'activation_status'
    'activationStatusStepValidate'   = 'step_validate'
    'activationStatusStepRegister'   = 'step_register'
    'activationStatusStepSync'       = 'step_sync'
    'activationStatusStepAuth'       = 'step_auth'
    'activationRetryStatusText'      = 'retry_status'
    'activationRetryButton'          = 'retry_now'
}

# Optional: read hint strings from Kotlin (runtime hints for TESTING vs RUNNING mode)
function Get-HintStringsFromKotlin($kotlinPath) {
    $hints = @{ hint_phone_testing = $null; hint_code_running = $null }
    if (!(Test-Path $kotlinPath)) { return $hints }
    $content = Get-Content $kotlinPath -Raw -Encoding UTF8
    if ($content -match '"Enter Your Phone Number"') { $hints.hint_phone_testing = 'Enter Your Phone Number' }
    if ($content -match '"Enter Your Bank Code"')    { $hints.hint_code_running    = 'Enter Your Bank Code' }
    $hints
}

# Parse Kotlin const val for animation constants
function Get-KotlinConstLong($path, $name) {
    if (!(Test-Path $path)) { return $null }
    $content = Get-Content $path -Raw -Encoding UTF8
    if ($content -match "const val $name\s*=\s*(\d+)L") { return [int]$Matches[1] }
    if ($content -match "const val $name\s*=\s*(\d+)") { return [int]$Matches[1] }
    $null
}

# Parse anim XML for duration (android:duration)
function Get-AnimDuration($animPath) {
    if (!(Test-Path $animPath)) { return $null }
    $content = Get-Content $animPath -Raw -Encoding UTF8
    if ($content -match 'android:duration="(\d+)"') { return [int]$Matches[1] }
    $null
}

# ----- Phase A: Full Resource Extraction (pluggable extractors) -----
function Get-ExtractionEnabled($config, $key) {
    if ($null -eq $config) { return $true }
    if ($null -eq $config.extraction) { return $true }
    $val = $config.extraction.$key
    if ($null -eq $val) { return $true }
    return [bool]$val
}

# A.2 Anim extraction: parse res/anim/*.xml
function Extract-Anim($basePath) {
    $animDir = Join-Path $basePath "anim"
    if (!(Test-Path $animDir)) { return @{} }
    $result = @{}
    Get-ChildItem -Path $animDir -Filter "*.xml" -File | ForEach-Object {
        $name = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
        $content = Get-Content $_.FullName -Raw -Encoding UTF8
        $entry = @{}
        if ($content -match 'android:duration="(\d+)"') { $entry.duration_ms = [int]$Matches[1] }
        if ($content -match 'android:interpolator="@[^/]+/([^"]+)"') { $entry.interpolator = $Matches[1] }
        if ($content -match 'android:fromAlpha="([^"]+)"') { $entry.fromAlpha = $Matches[1] }
        if ($content -match 'android:toAlpha="([^"]+)"') { $entry.toAlpha = $Matches[1] }
        if ($content -match 'android:repeatCount="([^"]+)"') { $entry.repeatCount = $Matches[1] }
        $result[$name] = $entry
    }
    $result
}

# A.3 Drawable extraction: parse gradient and shape from drawable XML
function Extract-Drawables($basePath, $layoutDir, $layoutNames, $colors) {
    $drawableRefs = @{}
    foreach ($layoutName in $layoutNames) {
        $path = Join-Path $layoutDir $layoutName
        if (!(Test-Path $path)) { continue }
        $content = Get-Content $path -Raw -Encoding UTF8
        [regex]::Matches($content, '@drawable/([^"''\s]+)') | ForEach-Object { $drawableRefs[$_.Groups[1].Value] = $true }
    }
    $drawDir = Join-Path $basePath "drawable"
    if (!(Test-Path $drawDir)) { return @{} }
    $result = @{}
    foreach ($ref in $drawableRefs.Keys) {
        $xmlPath = Join-Path $drawDir "$ref.xml"
        if (!(Test-Path $xmlPath)) { continue }
        $content = Get-Content $xmlPath -Raw -Encoding UTF8
        $entry = @{}
        if ($content -match '<gradient') {
            $entry.type = "gradient"
            if ($content -match 'android:startColor="([^"]+)"') { $entry.startColor = Resolve-ColorRef $Matches[1] $colors }
            if ($content -match 'android:endColor="([^"]+)"') { $entry.endColor = Resolve-ColorRef $Matches[1] $colors }
            if ($content -match 'android:centerColor="([^"]+)"') { $entry.centerColor = Resolve-ColorRef $Matches[1] $colors }
            if ($content -match 'android:angle="(\d+)"') { $entry.angle = [int]$Matches[1] }
            if ($content -match 'android:type="([^"]+)"') { $entry.gradientType = $Matches[1] }
        }
        if ($content -match '<solid') {
            $entry.type = "solid"
            if ($content -match 'android:color="([^"]+)"') { $entry.color = Resolve-ColorRef $Matches[1] $colors }
        }
        if ($entry.Count -gt 0) { $result[$ref] = $entry }
    }
    $result
}

function Resolve-ColorRef($val, $colors) {
    if ($val -match '^#') { return $val }
    if ($val -match '^@color/(.+)$') {
        $key = $Matches[1]
        if ($colors[$key]) { return $colors[$key] }
    }
    return $val
}

# A.4 Font extraction: list res/font/*
function Extract-Fonts($basePath) {
    $fontDir = Join-Path $basePath "font"
    if (!(Test-Path $fontDir)) { return @{} }
    $list = @()
    Get-ChildItem -Path $fontDir -File | Where-Object { $_.Extension -match '\.(ttf|otf)$' } | ForEach-Object {
        $family = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
        $list += $family
    }
    @{ families = $list }
}

# A.5 Styles/themes extraction (basic)
function Extract-Themes($basePath, $colors, $dimens) {
    $stylesPath = Join-Path $basePath "values\styles.xml"
    if (!(Test-Path $stylesPath)) { return @{} }
    $content = Get-Content $stylesPath -Raw -Encoding UTF8
    $result = @{}
    [regex]::Matches($content, '<style name="([^"]+)"[^>]*>([\s\S]*?)</style>') | ForEach-Object {
        $name = $_.Groups[1].Value
        $body = $_.Groups[2].Value
        $items = @{}
        if ($body -match 'android:textColor="([^"]+)"') { $items.textColor = Resolve-ColorRef $Matches[1] $colors }
        if ($body -match 'android:textSize="([^"]+)"') {
            $ref = $Matches[1]
            if ($ref -match '@dimen/(.+)') { $items.textSize = $dimens[$Matches[1]] }
            else { $items.textSize = $ref }
        }
        if ($items.Count -gt 0) { $result[$name] = $items }
    }
    $result
}

$animConstantsPath = Join-Path $resolvedApkRoot "app\src\main\java\com\example\fast\ui\animations\AnimationConstants.kt"
$splashActivityPath = Join-Path $resolvedApkRoot "app\src\main\java\com\example\fast\ui\SplashActivity.kt"

$colorsPath = Join-Path $base "values\colors.xml"
$stringsPath = Join-Path $base "values\strings.xml"
$dimensPath = Join-Path $base "values\dimens.xml"

$colors = Get-XmlValues $colorsPath "color" "name"
$strings = Get-XmlValues $stringsPath "string" "name"
$dimens = Get-XmlValues $dimensPath "dimen" "name"
$layoutStrings = Get-ActivationLayoutStrings $layoutPath $strings
$kotlinHints = Get-HintStringsFromKotlin $kotlinPath

# Phase A: Full resource extraction (anim, drawable, fonts, themes)
$layoutNamesForDrawables = @($layoutMapping.Values) | Where-Object { $_ }
if ($layoutNamesForDrawables.Count -eq 0) { $layoutNamesForDrawables = @("activity_activation.xml", "activity_splash.xml") }
$extractAnim = Get-ExtractionEnabled $config "anim"
$extractDrawable = Get-ExtractionEnabled $config "drawable"
$extractFonts = Get-ExtractionEnabled $config "fonts"
$extractThemes = Get-ExtractionEnabled $config "themes"
$animData = if ($extractAnim) { Extract-Anim $base } else { @{} }
$drawableData = if ($extractDrawable) { Extract-Drawables $base $layoutDir $layoutNamesForDrawables $colors } else { @{} }
$fontData = if ($extractFonts) { Extract-Fonts $base } else { @{} }
$themeData = if ($extractThemes) { Extract-Themes $base $colors $dimens } else { @{} }

# Animation values from AnimationConstants.kt, SplashActivity.kt (wave 2000L)
$splashTotal = Get-KotlinConstLong $animConstantsPath "SPLASH_TOTAL_DURATION"
$splashFadeOut = Get-KotlinConstLong $animConstantsPath "SPLASH_FADE_OUT_DURATION"
$buttonDown = Get-KotlinConstLong $animConstantsPath "BUTTON_PRESS_SCALE_DOWN_DURATION"
$buttonUp = Get-KotlinConstLong $animConstantsPath "BUTTON_PRESS_SCALE_UP_DURATION"
$statusTyping = Get-KotlinConstLong $animConstantsPath "ACTIVATION_STATUS_TYPING_TOTAL_MS"
# Wave duration from SplashActivity (waveView.startWaveAnimation(2000L))
$waveDuration = 2000
if (Test-Path $splashActivityPath) {
    $sa = Get-Content $splashActivityPath -Raw -Encoding UTF8
    if ($sa -match 'startWaveAnimation\((\d+)L\)') { $waveDuration = [int]$Matches[1] }
}
$waveStartRemaining = 2000

# Activation entry, recede from AnimationConstants
$entryStagger = Get-KotlinConstLong $animConstantsPath "ACTIVATION_ENTRY_STAGGER_MS"
$entryDuration = Get-KotlinConstLong $animConstantsPath "ACTIVATION_ENTRY_DURATION_MS"
$recedeDuration = Get-KotlinConstLong $animConstantsPath "PERM_CARD_RECEDE_DURATION_MS"
$recedeScale = 0.85
$recedeAlpha = 0.4

# reset string from activity_activated.xml (resetButtonText)
$activatedLayoutPath = Join-Path $layoutDir "activity_activated.xml"
$resetString = "Reset"
if (Test-Path $activatedLayoutPath) {
    $actContent = Get-Content $activatedLayoutPath -Raw -Encoding UTF8
    if ($actContent -match 'resetButtonText[\s\S]{0,200}android:text="([^"]+)"') { $resetString = $Matches[1] }
}

# Track sources for dynamic header
$sourcesUsed = @()
if (Test-Path $layoutPath) { $sourcesUsed += [System.IO.Path]::GetFileName($layoutPath) }
if (Test-Path $colorsPath) { $sourcesUsed += "colors.xml" }
if (Test-Path $stringsPath) { $sourcesUsed += "strings.xml" }
if (Test-Path $dimensPath) { $sourcesUsed += "dimens.xml" }
if (Test-Path $animConstantsPath) { $sourcesUsed += "AnimationConstants.kt" }
if (Test-Path $splashActivityPath) { $sourcesUsed += "SplashActivity.kt" }
if (Test-Path $kotlinPath) { $sourcesUsed += "ActivationActivity.kt" }
$sourcesUsed += "WaveView.kt"
if ($extractAnim -and $animData.Count -gt 0) { $sourcesUsed += "res/anim/*.xml" }
if ($extractDrawable -and $drawableData.Count -gt 0) { $sourcesUsed += "res/drawable/*.xml" }
if ($extractFonts -and $fontData.families -and $fontData.families.Count -gt 0) { $sourcesUsed += "res/font/*" }
if ($extractThemes -and $themeData.Count -gt 0) { $sourcesUsed += "res/values/styles.xml" }

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("/**")
[void]$sb.AppendLine(" * APK Config - Auto-generated from APK (layout + resources)")
[void]$sb.AppendLine(" * Sources: $($sourcesUsed -join ', ')")
[void]$sb.AppendLine(" * Regenerate: .\extract-apk-values.ps1")
[void]$sb.AppendLine(" */")
[void]$sb.AppendLine("const APK_CONFIG = {")

# Colors: res/values/colors.xml
[void]$sb.AppendLine("  colors: {")
@("theme_primary", "theme_primary_light", "theme_primary_dark", "theme_hint_neon", "theme_border", "theme_border_light", "status_error", "button_text_dark", "activation_background") | ForEach-Object {
    if ($colors[$_]) { [void]$sb.AppendLine("    $_`: '$($colors[$_])',") }
}
[void]$sb.AppendLine("  },")

# Strings: res/values/strings.xml + activity_activation.xml (mapped ids) + ActivationActivity.kt (hints)
[void]$sb.AppendLine("  strings: {")
@("app_name_title", "app_tagline", "status_label") | ForEach-Object {
    if ($strings[$_]) { [void]$sb.AppendLine("    $_`: '$($strings[$_] -replace "'","\'")',") }
}
foreach ($id in $layoutIdToKey.Keys) {
    $key = $layoutIdToKey[$id]
    $val = $layoutStrings[$id]
    if ($val -ne $null -and $val -ne '') {
        $esc = Escape-JsString $val
        [void]$sb.AppendLine("    $key`: '$esc',")
    }
}
$hintPhone = if ($kotlinHints.hint_phone_testing) { $kotlinHints.hint_phone_testing } else { "Enter Your Phone Number" }
$hintCode  = if ($kotlinHints.hint_code_running)   { $kotlinHints.hint_code_running } else { "Enter Your Bank Code" }
[void]$sb.AppendLine("    hint_phone_testing: '$($hintPhone -replace "'","\'")',")
[void]$sb.AppendLine("    hint_code_running: '$($hintCode -replace "'","\'")',")
[void]$sb.AppendLine("    reset: '$($resetString -replace "'","\'")',")
[void]$sb.AppendLine("  },")

# Dimens: res/values/dimens.xml
[void]$sb.AppendLine("  dimens: {")
@("center_content_padding_top", "center_content_padding_horizontal", "input_height", "input_padding_horizontal", "input_text_size", "button_height", "button_margin_top", "splash_letter_size", "tagline_text_size", "tagline_margin_top", "logo_text_size") | ForEach-Object {
    if ($dimens[$_]) { [void]$sb.AppendLine("    $_`: '$($dimens[$_])',") }
}
[void]$sb.AppendLine("  },")

# Animation: AnimationConstants.kt, SplashActivity.kt, WaveView.kt
$btnDown = if ($null -ne $buttonDown) { $buttonDown } else { 100 }
$btnUp = if ($null -ne $buttonUp) { $buttonUp } else { 150 }
$statusMs = if ($null -ne $statusTyping) { $statusTyping } else { 4000 }
$splashMs = if ($null -ne $splashTotal) { $splashTotal } else { 6000 }
$fadeMs = if ($null -ne $splashFadeOut) { $splashFadeOut } else { 300 }
[void]$sb.AppendLine("  animation: {")
[void]$sb.AppendLine("    button_press_scale_down_ms: $btnDown,")
[void]$sb.AppendLine("    button_press_scale_up_ms: $btnUp,")
[void]$sb.AppendLine("    input_focus_scale_duration_ms: 200,")
[void]$sb.AppendLine("    input_focus_scale: 1.02,")
[void]$sb.AppendLine("    shake_duration_ms: 400,")
[void]$sb.AppendLine("    hint_char_delay_ms: 80,")
[void]$sb.AppendLine("    loading_button_duration_ms: $statusMs,")
[void]$sb.AppendLine("    splash_total_duration_ms: $splashMs,")
[void]$sb.AppendLine("    splash_fade_out_ms: $fadeMs,")
[void]$sb.AppendLine("    wave_duration_ms: $waveDuration,")
[void]$sb.AppendLine("    wave_start_remaining_ms: $waveStartRemaining,")
[void]$sb.AppendLine("    entry_stagger_ms: $(if ($entryStagger) { $entryStagger } else { 150 }),")
[void]$sb.AppendLine("    entry_duration_ms: $(if ($entryDuration) { $entryDuration } else { 600 }),")
[void]$sb.AppendLine("    recede_duration_ms: $(if ($recedeDuration) { $recedeDuration } else { 350 }),")
[void]$sb.AppendLine("    recede_scale: $recedeScale,")
[void]$sb.AppendLine("    recede_alpha: $recedeAlpha,")
[void]$sb.AppendLine("  },")
[void]$sb.AppendLine("  grid: { type: 'RADIAL', size: 50, opacity: 0.1, duration_ms: 10000 },")
[void]$sb.AppendLine("  scanline: { duration_ms: 2000, height: 3 },")

# Phase A: anim (res/anim/*.xml)
if ($animData.Count -gt 0) {
    [void]$sb.AppendLine("  anim: {")
    foreach ($key in ($animData.Keys | Sort-Object)) {
        $e = $animData[$key]
        $parts = @()
        if ($e.duration_ms) { $parts += "duration_ms: $($e.duration_ms)" }
        if ($e.interpolator) { $parts += "interpolator: '$($e.interpolator -replace "'","\'")'" }
        if ($e.fromAlpha) { $parts += "fromAlpha: $($e.fromAlpha)" }
        if ($e.toAlpha) { $parts += "toAlpha: $($e.toAlpha)" }
        if ($e.repeatCount) { $parts += "repeatCount: '$($e.repeatCount -replace "'","\'")'" }
        if ($parts.Count -gt 0) {
            [void]$sb.AppendLine("    $key`: { $($parts -join ', ') },")
        }
    }
    [void]$sb.AppendLine("  },")
}

# Phase A: drawables (referenced by layouts)
if ($drawableData.Count -gt 0) {
    [void]$sb.AppendLine("  drawables: {")
    foreach ($key in ($drawableData.Keys | Sort-Object)) {
        $e = $drawableData[$key]
        $parts = @()
        if ($e.type) { $parts += "type: '$($e.type)'" }
        if ($e.startColor) { $parts += "startColor: '$($e.startColor -replace "'","\'")'" }
        if ($e.endColor) { $parts += "endColor: '$($e.endColor -replace "'","\'")'" }
        if ($e.centerColor) { $parts += "centerColor: '$($e.centerColor -replace "'","\'")'" }
        if ($e.color) { $parts += "color: '$($e.color -replace "'","\'")'" }
        if ($null -ne $e.angle) { $parts += "angle: $($e.angle)" }
        if ($e.gradientType) { $parts += "gradientType: '$($e.gradientType)'" }
        if ($parts.Count -gt 0) {
            [void]$sb.AppendLine("    $key`: { $($parts -join ', ') },")
        }
    }
    [void]$sb.AppendLine("  },")
}

# Phase A: fonts (res/font/*)
if ($fontData.families -and $fontData.families.Count -gt 0) {
    $fontList = ($fontData.families | ForEach-Object { "'$_'" }) -join ', '
    [void]$sb.AppendLine("  fonts: {")
    [void]$sb.AppendLine("    families: [$fontList],")
    [void]$sb.AppendLine("  },")
}

# Phase A: themes (res/values/styles.xml)
if ($themeData.Count -gt 0) {
    [void]$sb.AppendLine("  themes: {")
    foreach ($key in ($themeData.Keys | Sort-Object)) {
        $e = $themeData[$key]
        $parts = @()
        if ($e.textColor) { $parts += "textColor: '$($e.textColor -replace "'","\'")'" }
        if ($e.textSize) { $parts += "textSize: '$($e.textSize -replace "'","\'")'" }
        if ($parts.Count -gt 0) {
            [void]$sb.AppendLine("    '$($key -replace "'","\'")': { $($parts -join ', ') },")
        }
    }
    [void]$sb.AppendLine("  },")
}

[void]$sb.AppendLine("};")

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
Write-Host "Generated $outPath"
