# APK Demo Parity Audit

HTML demos aim for **visual and behavioral parity** with the APK, not just flow.

## Layout Inventory Template

For each APK layout used by the demo:

| Layout | Screen | Views / Effects | Extracted? | In HTML? | Deferred? |
|--------|--------|-----------------|------------|----------|-----------|
| activity_splash.xml | splash | VideoView, GridBackgroundView, ScanlineView, WaveView, logo, tagline | partial | partial | Grid, Scanline, Video |
| activity_activation.xml | activation | header, main-card, status-card, prompt-overlay | yes | yes | |
| activity_activated.xml | activated | logo, status, Reset button | yes | yes | |

## Per-View Checklist

Before marking a demo "complete":

1. [ ] List all views/effects from the corresponding APK layout
2. [ ] For each view: extracted into apk-config.js? implemented in HTML? or explicitly deferred with reason
3. [ ] Confirm extract script pulls all required values from those layouts/code
4. [ ] Rebuild apk-config.js and re-test the demo

## APK Layout → HTML Section Mapping

| APK Layout | HTML Section | Notes |
|------------|--------------|-------|
| activity_splash.xml | #viewSplash, .wave-spread | WaveView → colour spread; VideoView, GridBackgroundView, ScanlineView deferred |
| activity_activation.xml | #viewActivation | Full flow: header, main-card, status-card, prompt-overlay |
| activity_activated.xml | #viewActivated | Status box, Reset button |

## Resource Scope

- **res/anim/**: fade_in, neon_pulse, pulse, slide_up – extracted to `apk_config.anim` (duration_ms, interpolator, etc.)
- **res/animator/**: (none in FASTPAY_BASE)
- **res/drawable/**: Referenced by layouts – extracted to `apk_config.drawables` (gradient, solid)
- **res/values/**: colors, strings, dimens, styles, themes – colors/strings/dimens extracted; themes optional
- **res/font/**: aoboshi_one, inter, inter_semibold – extracted to `apk_config.fonts.families`

## Config for Different APKs

To run audit on another APK, set `apk_root` in apk-mapping.json or pass `-ApkRoot`. Layout heuristics auto-detect splash/activation/activated; override with `layouts` in config if needed.

## Future / Deferred

- **ViewStub**: Not yet parsed. ViewStub elements inflate a layout at runtime; support would require parsing the `layout` attr and merging the inflated layout when the stub is visible. Documented for future implementation.
