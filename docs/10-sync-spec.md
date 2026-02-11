# APK ↔ HTML Sync Spec

**Purpose:** When the APK (ActivationActivity and related resources) is updated, use this spec to keep the HTML activation demo in sync. Demo file: `APKTOWEBVIEW/demos/output.html` (hand-written; no HTML generation). Config: run `npm run extract` or `npm run build:tokens` from `APKTOWEBVIEW/` → `APKTOWEBVIEW/apk-config.js`.

---

## 1. Source File Mapping

| APK File | HTML Section | Notes |
|----------|--------------|-------|
| `FASTPAY_BASE/.../activity_activation.xml` | Layout structure | Root: activationRootLayout → activationHeaderSection, activationContentScrollView, activationStatusCardContainer |
| `FASTPAY_BASE/.../values/colors.xml` | CSS `:root` variables | §2 Colors |
| `FASTPAY_BASE/.../values/strings.xml` | Text content | §3 Strings |
| `FASTPAY_BASE/.../values/dimens.xml` | CSS sizes | §4 Dimensions |
| `FASTPAY_BASE/.../ActivationActivity.kt` | Flow, animations | §5 Flow & §6 Animations |

---

## 2. Colors (colors.xml)

| APK color | HTML variable / usage |
|-----------|------------------------|
| theme_primary | --neon |
| theme_primary_light | --neon-dim base |
| theme_primary_dark | darker shades |
| theme_hint_neon | placeholder/hint |
| theme_border | --neon-border |
| theme_border_light | --neon-shimmer |
| status_error | input error, shake |
| button_text_dark | activate button text |

Crypto card: `crypto_hash_card_background.xml` → dark base, stroke, shimmer.

---

## 3. Strings (strings.xml, Constants.kt)

Map `app_name_title`, `app_tagline`, `cryptoHashLabel`, `sha256Badge`, `testingButton`, `runningButton`, input hint, activation progress title/status/steps, retry text, activate/clear button text from APK to HTML.

---

## 4. Dimensions (dimens.xml)

Map logo/tagline sizes, padding, input height, button height/margin, crypto card padding, spacing from APK to CSS (dp → px or rem).

---

## 5. Activation Flow (ActivationActivity.kt)

Click Activate → button animation → validation (TESTING: 10 digits, RUNNING: 4 letters + 4 digits) → on fail: shake, red text; on success: status card, typing sequence, step highlights, result (Approved/Denied).

---

## 6. Animation Timings

Button press (scale down 100ms, up 150ms overshoot), input focus 200ms, shake 400ms, error text 200ms, progress overlay fade 200ms, loading button scale 4000ms, typing per-char delay from code.

---

## 7. Layout Structure (activity_activation.xml)

activationRootLayout → activationGridBackground, activationScanlineOverlay, activationHeaderSection (logo + tagline), activationContentScrollView → activationContentContainer (form card with mode selector, input, buttons; status card container with steps and retry).

---

## 8. Sync Checklist (when APK updates)

- [ ] Colors: `colors.xml` → `:root` in HTML
- [ ] Strings: `strings.xml`, Constants, layout → HTML text
- [ ] Dimensions: `dimens.xml`, layout → CSS
- [ ] Flow: `ActivationActivity.kt` activate, processPhoneActivation, processCodeActivation
- [ ] Animations: animateButtonPress, shakeView, setupInputFieldAnimation, typing, status card
- [ ] Layout: `activity_activation.xml` structure
- [ ] Run `npm run extract` (from APKTOWEBVIEW) to regenerate `apk-config.js` if using it

---

## 9. Optional: design tokens (HTML demo only)

From repo root:

```bash
npm run build:tokens
```

**Optional.** Edit `APKTOWEBVIEW/tokens/**/*.json` (color, size, string, animation). Run `npm run build:tokens` from APKTOWEBVIEW. Style Dictionary generates `apk-config.js` in APKTOWEBVIEW. The **HTML demo** (`APKTOWEBVIEW/demos/`) uses it; the **APK is unchanged**. See `APKTOWEBVIEW/README.md`.

## 9b. Extract script (APK as source for apk-config.js)

```powershell
.\scripts\extract-apk-values.ps1
```

Builds `apk-config.js` from APK layout and resources. Use when not using token-first.

---

## 10. Optional Improvements

- CI check: compare APK resources vs `apk-config.js`, fail if out of sync.
- Pre-commit: run extract script when APK resource files change.
- Single HTML that always loads `apk-config.js` so one source of truth.

---

## 11. Proven global approach (design tokens)

The **industry-standard, proven approach** for keeping app and web in sync is **design tokens as the single source of truth**, not the app.

### How it works globally

1. **Design Tokens (W3C / DTCG format)**
   Design decisions (colors, spacing, typography, strings) are stored in **platform-agnostic JSON** (e.g. `tokens/color.json`, `tokens/size.json`), often with `$type` and `$value`. See [Design Tokens Community Group](https://www.designtokens.org/tr/2025.10/format/) (format spec).

2. **Style Dictionary**
   A **translation tool** reads that token JSON and **generates** platform outputs:
   - Android: `colors.xml`, `dimens.xml`, (and optionally strings)
   - Web: CSS variables, JS modules
   - iOS, Compose, etc.
   So the flow is: **tokens (JSON) → Android + Web**. One source, many outputs. [Style Dictionary](https://styledictionary.com/) is the widely used open-source choice and is referenced in the DTCG spec.

3. **Figma / design tools**
   Many teams also sync tokens **from** Figma (or other design tools) into the same token JSON, so design and code share one vocabulary.

### Our current approach vs proven

| | **Proven (token-first)** | **Our current (Android-first)** |
|--|--------------------------|----------------------------------|
| Source of truth | Token JSON files | Android layout + `values/` XML |
| Direction | Tokens → Android, tokens → Web | Android → extract → `apk-config.js` → HTML |
| Tooling | Style Dictionary (npm) | Custom `extract-apk-values.ps1` (PowerShell) |
| HTML | Uses generated JS/CSS from tokens | Uses `apk-config.js` generated from APK |

Our setup works and keeps the HTML demo in sync by re-running the extract script after APK changes. It does **not** follow the usual “tokens first” flow.

### Options if we want to align with the proven approach

- **Option A – Token-first (recommended long-term)**
  Introduce a `tokens/` directory with JSON (e.g. DTCG-style or simple key/value). Use **Style Dictionary** to:
  - Generate `colors.xml`, `dimens.xml`, and optionally string resources for Android.
  - Generate a JS (or CSS) bundle for the HTML demo.
  Then both the APK and the HTML consume **generated** files; no “extract from APK” step. Single source = tokens.

- **Option B – Keep Android as source, use standard converters**
  Keep Android as the source of truth but replace the custom PowerShell script with a **Node** build step that uses established npm packages:
  - **android-string-resource** (`asr2js`) for `strings.xml` → JSON/JS.
  - A small parser or existing lib for `colors.xml` / `dimens.xml` → JSON.
  Output the same `apk-config.js` (or a DTCG-like format) so the HTML stays unchanged. This uses proven converters instead of custom parsing.

- **Option C – Keep current script**
  Continue with `extract-apk-values.ps1` reading layout + resources. No new dependencies; script already builds `apk-config.js` from live APK code so the HTML does not need edits when the APK changes.

**Optional:** Design tokens in `tokens/**/*.json` can be built with `npm run build:tokens` to update only `apk-config.js` for the HTML demo; the APK is not modified.
