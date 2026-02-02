# APK ↔ HTML Sync Spec

**Purpose:** When APK (ActivationActivity) is updated, use this spec to sync `fastpay-activation-ui.html` so the HTML demo stays in sync with the latest code.

---

## 1. Source File Mapping

| APK File | HTML Section | Notes |
|----------|--------------|-------|
| `FASTPAY_BASE/.../activity_activation.xml` | Entire layout structure | Root: activationRootLayout → activationHeaderSection, activationContentScrollView, activationStatusCardContainer |
| `FASTPAY_BASE/.../values/colors.xml` | CSS `:root` variables | See §2 Colors |
| `FASTPAY_BASE/.../values/strings.xml` | Text content | See §3 Strings |
| `FASTPAY_BASE/.../values/dimens.xml` | CSS sizes | See §4 Dimensions |
| `FASTPAY_BASE/.../ActivationActivity.kt` | JavaScript flow, animations | See §5 Flow & §6 Animations |

---

## 2. Colors (colors.xml)

| APK color | Value | HTML variable |
|-----------|-------|---------------|
| theme_primary | #00ff88 | --neon |
| theme_primary_light | #00ff88 | --neon-dim base |
| theme_primary_dark | #00cc6a | (for darker shades) |
| theme_hint_neon | #8000ff88 | placeholder/hint |
| theme_border | #4D00ff88 | --neon-border |
| theme_border_light | #8000ff88 | --neon-shimmer |
| status_error | #F44336 | input error, shake |
| button_text_dark | #000000 | activate button text |

**Crypto card background:** `crypto_hash_card_background.xml` → dark base #051a1a1a, stroke #3300ff88, shimmer #1500ff88

---

## 3. Strings (strings.xml, Constants.kt)

| APK Source | Key / Constant | Value |
|------------|----------------|-------|
| strings.xml | app_name_title | FASTPAY |
| strings.xml | app_tagline | The Real Gaming Platform |
| layout XML | cryptoHashLabel | #123 ALWAYS SECURE |
| layout XML | sha256Badge | SHA256 |
| layout XML | testingButton | TESTING |
| layout XML | runningButton | RUNNING |
| layout XML | editTextText2 hint | Input Number |
| ActivationActivity | getHintTextForCurrentMode() TESTING | Enter Your Phone Number |
| ActivationActivity | getHintTextForCurrentMode() RUNNING | Enter Your Bank Code |
| layout XML | activationProgressTitle | ACTIVATION |
| layout XML | activationProgressStatus | Creating secure tunnel with company database |
| layout XML | activationStepValidate | • IP Connection Established |
| layout XML | activationStepRegister | • Request Sent |
| layout XML | activationStepSync | • Response Decrypted |
| layout XML | activationStepAuth | • Authorization Check (Approved / Denied) |
| layout XML | activationRetryStatus | Retrying in 5s... |
| layout XML | activationRetryNow | RETRY NOW |
| layout XML | textView3 | ACTIVATE |
| layout XML | clearButtonText | CLEAR |

---

## 4. Dimensions (dimens.xml)

| APK dimen | Value | HTML equivalent |
|-----------|-------|-----------------|
| logo_text_size | 38sp | ~1.25rem (layout uses 48sp in activation) |
| tagline_text_size | 12sp | 0.75rem |
| tagline_margin_top | 8dp | 8px |
| tagline_letter_spacing | 0.3 | 0.03em |
| tagline_alpha | 0.7 | opacity 0.7 |
| center_content_padding_top | 32dp | 32px |
| center_content_padding_horizontal | 16dp | 16px |
| input_height | 72dp | min-height ~48px |
| input_padding_horizontal | 16dp | 16px |
| input_text_size | 18sp | 1.125rem |
| button_height | 64dp | padding 14px 20px |
| button_margin_top | 32dp | 32px |
| crypto card padding | 16dp | 16px |
| spacing between rows | 12dp | 12px |

**Note:** activity_activation uses logo textSize 48sp (not logo_text_size 38sp).

---

## 5. Activation Flow (ActivationActivity.kt)

1. **Click Activate** → `animateButtonPress(cardView7)` → scale down 100ms, scale up 150ms overshoot
2. **Validation** (TESTING: 10 digits, RUNNING: 4 letters + 4 digits)
   - Fail → `shakeView(cardView6)`, text red 200ms, `restoreInputAfterValidationFail()`
3. **Success** → `updateActivationState(Validating)`, `startStatusTypingSequence()`, `disableInputForActivation()`
4. **Button animation** → `startActivationButtonsAnimation(4000ms)` – Activate 1.08x/1.04x, Clear 0.92x
5. **Status card** → `showProgressOverlayAnimated()` – overlay fades in 200ms
6. **Typing** → `startStatusTypingSequence()` – char delay ~4000/totalChars, min 20ms
7. **Steps** → Validate, Register, Sync, Auth highlighted in sequence (alpha 0.5→1)
8. **Result** → `activationStepResult` – Approved/Denied

---

## 6. Animation Timings (ActivationActivity.kt)

| Animation | Duration | Interpolator | APK Reference |
|-----------|----------|--------------|---------------|
| Button press scale down | 100ms | - | animateButtonPress |
| Button press scale up | 150ms | OvershootInterpolator | animateButtonPress |
| Input focus scale | 200ms | - | cardView6 1.02x |
| Shake | 400ms | DecelerateInterpolator | shakeView |
| Error text red | 200ms | - | restoreInputAfterValidationFail |
| Progress overlay fade in | 200ms | DecelerateInterpolator | showProgressOverlayAnimated |
| Loading button scale | 4000ms | DecelerateInterpolator | startActivationButtonsAnimation |
| Hint typing per char | 80ms | - | animateHintText |
| Status typing per char | 4000/totalChars, min 20ms | - | startStatusTypingSequence |

---

## 7. Layout Structure (activity_activation.xml)

```
activationRootLayout (ConstraintLayout)
├── activationGridBackground
├── activationScanlineOverlay
├── activationHeaderSection (Logo + Tagline)
│   ├── activationLogoText (FASTPAY logo)
│   └── activationTaglineText (tagline)
├── activationContentScrollView (ScrollView)
│   └── activationContentContainer (LinearLayout)
│       ├── activationFormCardOuterBorder (outer dashed border)
│       │   └── activationFormCard (inner card)
│       │       ├── activationFormSecurityLabel + activationFormHashBadge
│       │       ├── activationModeSelector (TESTING | RUNNING + divider)
│       │       ├── activationInputCard (input container) → activationPhoneInput
│       │       └── button row (activationActivateButton, activationClearButton)
│       └── activationStatusCardContainer (visibility gone)
│           └── activationStatusCard
│               ├── activationStatusCardTitle
│               ├── activationStatusCardStatusText
│               ├── activationStatusCardStepsContainer (Validate, Register, Sync, Auth, Result)
│               └── activationRetryContainer
```

---

## 8. Sync Checklist (when APK updates)

- [ ] **Colors:** Check `colors.xml` – update `:root` in HTML
- [ ] **Strings:** Check `strings.xml`, `Constants.kt`, layout XML – update text in HTML
- [ ] **Dimensions:** Check `dimens.xml`, layout – update CSS sizes
- [ ] **Flow:** Check `ActivationActivity.kt` `activate()`, `processPhoneActivation`, `processCodeActivation`
- [ ] **Animations:** Check `animateButtonPress`, `shakeView`, `setupInputFieldAnimation`, `animateHintText`, `startStatusTypingSequence`, `startActivationButtonsAnimation`
- [ ] **Layout:** Check `activity_activation.xml` – add/remove elements, adjust structure
- [ ] **Run** `scripts/extract-apk-values.ps1` if available – regenerates config from APK

---

## 9. Extract Script & Config

**Regenerate config after APK resource changes:**
```powershell
.\scripts\extract-apk-values.ps1
```

Creates `apk-config.js` from `colors.xml`, `strings.xml`, `dimens.xml`.

**Optional:** Add `<script src="apk-config.js"></script>` to HTML and use `APK_CONFIG` for values. HTML currently uses inline values with comments pointing to APK source.

---

## 10. Better Idea for Easy Sync

**Current approach:** Manual sync using APK_SYNC_SPEC checklist + optional extract script.

**Alternative for future:**
1. **CI check** – GitHub Action that compares APK resources vs apk-config.js, fails if out of sync.
2. **Pre-commit hook** – Run extract script before commit when APK resource files change.
3. **Single HTML with config** – HTML always loads apk-config.js; developers run extract script after APK updates.
