# Activated Screen – Elements Displayed on Start and by Remote Command

**Scope:** FASTPAY_BASE `ActivatedActivity` and related layout/overlays.
**Reference:** [activity_activated.xml](FASTPAY_BASE/app/src/main/res/layout/activity_activated.xml), [06-remote-commands.md](06-remote-commands.md), [RemoteCardHandler.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/ui/card/RemoteCardHandler.kt).

---

## 1. Elements displayed on start (when ActivatedActivity opens)

**Main content (6 areas):** header, phone card, status card, device info, SMS card, test/reset buttons.
**Single source of truth:** `ActivatedUIManager.showMainContent()` sets visibility, alpha, and scale for these; `ActivatedActivity` calls it from `forceAllCardsVisible()`, `runCardFlipSafetyNet()`, and `checkPermissionsAndUpdateUI()` and does not duplicate that logic.

These are shown as part of the main screen or conditionally right after open (same session).

| Element | Layout ID / Source | Notes |
|--------|---------------------|--------|
| **Header** | `headerSection`, `textView11` (logo), `textView12` (tagline) | Logo/tagline from branding or strings. |
| **Phone card** | `phoneCardWrapper`, `phoneCard` | Testing code, activation code, date/time, ANIM button. |
| **Status card** | `statusCard`, `statusLabel`, `statusValue`, `permissionStatusText` | STATUS label, bank status value, permissions line. |
| **Device info** | `deviceInfoColumn`, `deviceIdText`, `versionCodeText` | Device ID and version (set in code). |
| **SMS card** | `smsCard`, `smsHeader`, `smsHeaderLabel`, `smsCountBadge`, `smsCardContentContainer` | SMS list (front) or instruction (back); default is SMS. |
| **SMS content** | `smsContentFront`, `smsRecyclerView`, `smsEmptyState` | Shown by default. |
| **Instruction content (back)** | `instructionContentBack`, `instructionWebView` | Shown when user flips card (or by remote). |
| **Test / Reset buttons** | `testButtonsContainer`, `testButtonCard`, `testButtonText`, `testButtonTimer`, `resetButtonCard`, `resetButtonText` | TEST and Reset at bottom. |
| **Progress overlay** | `progressBar` | Full-screen dim + spinner; shown during loading (e.g. sync), then hidden. |
| **Activation master card overlay** | `activationMasterCardOverlay` (include) | Permission/update prompt when entering from Activation; shown by logic, then dismissed. |
| **Wipe line overlay** | `wipeLineOverlay` | Used for transition from ActivationActivity; then hidden. |

**Chain order (layout + code):**
`topMarginGuide` → `headerSection` → `phoneCardWrapper` → `statusCard` → `smsCard` → `deviceInfoColumn` → `testButtonsContainer`.

---

## 2. Elements displayed by remote command (or card control)

These appear when the backend/Firebase sends a command or updates a path; they may overlay or replace part of the main screen.

| Trigger | Element / UI | Command / path | Notes |
|--------|----------------|----------------|--------|
| **showCard** (Firebase command) | SMS side or Instruction side of `smsCard` | `commands/showCard` = `sms` \| `instruction` | PersistentForegroundService writes `cardControl/showCard`; ActivatedActivity/ActivatedFirebaseManager listen and flip the card or show instruction. |
| **cardControl/showCard** (Firebase path) | Same as above | `device/{id}/cardControl/showCard` = `sms` \| `instruction` | Direct write; app shows SMS or instruction side of the main SMS card. |
| **cardControl/animation** (Firebase path) | Same card flip / animation | `device/{id}/cardControl/animation` = `{ "type": "sms" \| "instruction" \| "flip" }` | Triggers flip or animation. |
| **Instruction card (Firebase)** | In-card, prompt, or fullscreen | `device/{id}/instructioncard/` (html, css, mode, etc.) | **mode 0:** in-card (`instructionContentBack` WebView). **mode 1:** prompt overlay (`instructionPromptOverlay`). **mode 2:** fullscreen (`instructionFullScreenOverlay`). |
| **FCM / Remote card (SHOW_CARD)** | MultipurposeCard (overlay or fullscreen) | FCM data / Firebase → `CardCoordinator.show()` | Types: **message**, **permission**, **default_sms**, **notification_access**, **battery_optimization**, **update**, **webview**, **confirm**, **input**. Can be overlay on current activity or fullscreen (MultipurposeCardActivity). |

**Overlays in activity_activated.xml used by remote/instruction:**

| Overlay | Layout ID | When shown |
|---------|------------|------------|
| Instruction prompt (mode 1) | `instructionPromptOverlay`, `instructionPromptWebView` | Instruction card mode = prompt. |
| Instruction fullscreen (mode 2) | `instructionFullScreenOverlay`, `instructionFullScreenWebView` | Instruction card mode = fullscreen. |
| Activation master card (permission/update) | `activationMasterCardOverlay` | After activation or when backend triggers permission/update prompt. |

**Remote card types (FCM/Firebase → RemoteCardHandler):**
message, permission, default_sms, notification_access, battery_optimization, update, webview, confirm, input.

---

## 3. Quick reference

- **On start:** Header, phone card, status card (with STATUS label), device info, SMS card (SMS by default), TEST + Reset. Optional: progress overlay, activation master card overlay, wipe overlay (then hidden).
- **Remote:** showCard / cardControl → which side of SMS card (SMS vs instruction). Instruction card mode → in-card, prompt overlay, or fullscreen overlay. FCM/RemoteCard → MultipurposeCard (message, permission, update, webview, etc.) as overlay or fullscreen.
