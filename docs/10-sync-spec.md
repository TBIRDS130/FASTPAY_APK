# APK ↔ HTML Sync Spec

**Purpose:** When the APK (ActivationActivity and related resources) is updated, use this spec to keep the HTML activation demo in sync. Demo file: `docs/demos/fastpay-activation-ui.html` (or root `fastpay-activation-ui.html` if kept there). Config can be regenerated with `scripts/extract-apk-values.ps1` → `apk-config.js`.

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
- [ ] Run `scripts/extract-apk-values.ps1` to regenerate `apk-config.js` if using it

---

## 9. Extract Script

From repo root:

```powershell
.\scripts\extract-apk-values.ps1
```

Creates `apk-config.js` from `colors.xml`, `strings.xml`, `dimens.xml`. HTML can optionally load it and use `APK_CONFIG` for values.

---

## 10. Optional Improvements

- CI check: compare APK resources vs `apk-config.js`, fail if out of sync.
- Pre-commit: run extract script when APK resource files change.
- Single HTML that always loads `apk-config.js` so one source of truth.
