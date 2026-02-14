# UIManager: Complete Reference

All three main UI screens (Splash, Activation, Activated) use the same structure: **elements** (what views) and **UIManager** (when/how to show them). The manager is the single place that sets main-content visibility, alpha, and scale.

---

## Rule

**The UIManager decides what to show.** Do not set main-content visibility, alpha, or scale outside the UIManager. Exceptions: documented animation paths (e.g. during-splash logo/tagline animation) or code that only calls the manager. See [ui-manager-audit-route-through.md](ui-manager-audit-route-through.md) for the audit and routing checklist.

---

## Overview

| Screen     | UIManager class           | Source file                          | Binding / elements |
|------------|---------------------------|--------------------------------------|--------------------|
| Splash     | `SplashUIManager`         | `ui/splash/SplashUIManager.kt`       | `logoView`, `taglineView` (passed in) |
| Activation | `ActivationUIManager`     | `ui/activation/ActivationUIManager.kt` | `ActivityActivationBinding` |
| Activated  | `ActivatedUIManager`      | `ui/activated/ActivatedUIManager.kt` | `ActivityActivatedBinding` |

- **Elements** – The main views/areas for that screen. Defined by the layout and the binding (or views passed into the manager). The manager operates on them; it does not define what they are.
- **Manager** – One class per screen. Orchestrates visibility, alpha, and state. Single source of truth for “when” and “how” the elements are shown or updated.

---

## SplashUIManager

**Package:** `com.example.fast.ui.splash`
**File:** `FASTPAY_BASE/app/src/main/java/com/example/fast/ui/splash/SplashUIManager.kt`

**Constructor:** `SplashUIManager(logoView: TextView, taglineView: TextView)`
**Elements:** Logo and tagline `TextView`s; the activity resolves them from the layout and passes them in.

### Public API

| Method | Description | When to call |
|--------|-------------|--------------|
| `prepareForTransition()` | Sets `transitionName` for both views; `visibility = VISIBLE`, `alpha = 1f`, `scaleX`/`scaleY = 1f`, `translationY = 0f` for a single defined state. | Before starting the shared-element transition in both navigation paths. **Activity must cancel any running logo/tagline animators before calling.** |

### Exceptions

- **During-splash animation** – `SplashActivity` may set logo/tagline `alpha` and `scale` in `animateNeonLogo` / `animateTagline`; that is in-splash animation, not “what to show for transition.” The manager owns only the state **ready for transition** via `prepareForTransition()`.

---

## ActivationUIManager

**Package:** `com.example.fast.ui.activation`
**File:** `FASTPAY_BASE/app/src/main/java/com/example/fast/ui/activation/ActivationUIManager.kt`

**Constructor:** `ActivationUIManager(binding: ActivityActivationBinding)`
**Elements (from binding):** Header section, logo text, tagline text, content container, utility card, status card container, utility content keyboard, retry container, step views (validate, register, sync, auth, result, register buffer).

**Enums (same package):** `ActivationState` (Idle, Validating, Registering, Syncing, Success, Fail), `ActivationErrorType` (Validation, Network, Firebase, DjangoApi, DeviceId, Unknown).

### Public API

| Method | Description | When to call |
|--------|-------------|--------------|
| `showDefaultContent()` | Header, logo, tagline, utility card, and status card container VISIBLE; keypad GONE. | From `ActivationActivity.onCreate` so default layout is correct before entry animation and setup. |
| `showStatusHideKeypad()` | Keypad GONE, status card container VISIBLE. | Before starting wipe-up-then-flip (e.g. before `runWipeUpThenFlip`) so status is shown and keypad hidden. |
| `setRetryVisible(visible: Boolean)` | Sets retry container visibility. | From activity when clearing retry (`false`) or scheduling auto-retry (`true`). |
| `applyState(state, errorType, errorMessage, hasPendingRetry)` | Applies visibility/alpha for the given state (steps, status text, retry container, keypad, etc.). | From `updateActivationUI` whenever activation state or error changes. Activity keeps overlay show/hide, typing, haptics, shake. |
| `showStatusTextOnly()` | Steps container and title hidden; status text visible (for success transition). | From activity when showing status card content for success before transition. |
| `ensureHeaderVisible()` | Header section, logo, tagline VISIBLE and opaque. | Before transition; activity may set text. |
| `resetFormCardAppearance()` | Form card alpha 1f, scale 1f. | When stopping card animation; activity keeps elevation/translation/rotation. |
| `setPhoneInputState(visible, alpha?, scale?)` | Phone input visibility and optional alpha/scale. | All phone input visibility/alpha/scale changes (glitch effects, hide for character reveal, reset). |
| `resetActivateButtons()` | Activate and clear buttons visible, alpha 1f, scale 1f. | When resetting activate/clear button state. |
| `setTestButtonStates(testingAlpha, runningAlpha)` | TESTING and RUNNING button alphas. | When switching login type (testing vs running). |
| `hideStatusStepRegisterBuffer()` | Register buffer step view GONE. | Before starting activate (e.g. from “Register” tap). |
| `prepareForEntry(isTransitioningFromSplash, onReady)` | If from Splash: sets header and content alpha/translation and calls `onReady`. Else: runs `ActivationAnimationHelper.runEntryAnimation` then `onReady`. | From `showActivationUI`; activity then runs hint animation and other setup in `onReady`. |

### Exceptions

- **Keypad show animation** – `ActivationAnimationHelper.showKeypadWithAnimation` may set keypad visibility; documented as acceptable. Can be moved behind a manager method later.
- **Overlay show/hide** – Activity may animate the status card container for overlay; `applyState` already sets state visibility.

---

## ActivatedUIManager

**Package:** `com.example.fast.ui.activated`
**File:** `FASTPAY_BASE/app/src/main/java/com/example/fast/ui/activated/ActivatedUIManager.kt`

**Constructor:** `ActivatedUIManager(binding: ActivityActivatedBinding, isTransitioningFromSplash: Boolean)`
**Elements (from binding):** Header section, phone card wrapper, phone card, status card, device info column, SMS card (SMS front, instruction back), test/reset buttons container and cards. Root `main` for edge-to-edge insets.

### Public API

| Method | Description | When to call |
|--------|-------------|--------------|
| `setupUIAfterBranding(hasInstructionCard)` | If from Splash: shows all elements immediately. If from Activation: hides then animates in (header visible; cards/buttons fade in). Also sets edge-to-edge insets. | Once from `setupUI()` after branding is loaded. |
| `ensureHeaderVisible()` | Header section and logo/tagline VISIBLE, alpha 1f. | After branding load; activity sets logo/tagline text. |
| `ensurePhoneCardVisibleForTransition()` | Phone card `visibility = VISIBLE`, `alpha = 1f`. | From shared-element transition listener `onTransitionStart`; activity keeps `setBackgroundResource`. |
| `showMainContent()` | All six main areas (header, phone card, status card, device info, SMS card, test/reset buttons) VISIBLE, alpha 1f, scale 1f; SMS front visible, instruction back GONE. | Whenever the main screen should be fully visible: after card-flip completion, permission/update flows, MultipurposeCard onComplete, safety nets, force-all-visible. |
| `showElementsImmediately(hasInstructionCard)` | Delegates to `showMainContent()`. | When an immediate show (no animation) is requested. |
| `ensureElementsVisible(hasInstructionCard)` | Delegates to `showMainContent()`. | Safety check / force visible. |
| `hideMainContentForOverlay()` | Hides all main content (header, cards, buttons) so only overlay is visible. | Before showing MultipurposeCard or CardCoordinator overlay; restore with `showMainContent()` in onComplete. |
| `runWipeDownEntryAnimation(onComplete)` | Wipe-down entry: elements slide up from off-screen and fade in; calls `onComplete` when done. | From `setupWipeDownTransitionForReturnFromUpdate` (return from MultipurposeCardActivity). |
| `runArrivalAnimation(onComplete)` | One-by-one arrival (header → phone → status → SMS → buttons); then `showMainContent()` and `onComplete`. | From wipe-line flow after overlay hides. |
| `showSmsSide(showEmptyState)` | SMS card visible; SMS front visible, instruction back hidden; empty state or list per flag. | When showing SMS side (setup, after test message, etc.). |
| `showInstructionSide()` | SMS card visible; instruction back visible, SMS front hidden. | When showing instruction side. |
| `setPermissionStatusVisible(visible)` | Permission status text visibility. | When updating “Permissions: OK” / “Permissions: Missing”. |

### Exceptions

- **Instruction prompt / fullscreen** – Activity may animate header/phone/SMS scale/rotation for 3D effect; overlay show/hide stays in activity.

---

## Call flow summary

- **Splash:** Activity sets logo/tagline text; before navigate, cancel logo/tagline animators then call `splashUIManager?.prepareForTransition()`.
- **Activation:** onCreate → `activationUIManager.showDefaultContent()`; show UI → `activationUIManager.prepareForEntry(...)`; state changes → `activationUIManager.applyState(...)`; before wipe-up → `activationUIManager.showStatusHideKeypad()`; retry clear/schedule → `activationUIManager.setRetryVisible(...)`; status/header/form/phone/buttons/buffer → use `showStatusTextOnly`, `ensureHeaderVisible`, `resetFormCardAppearance`, `setPhoneInputState`, `resetActivateButtons`, `setTestButtonStates`, `hideStatusStepRegisterBuffer` as needed.
- **Activated:** After branding → `uiManager.setupUIAfterBranding(...)`; branding load → `uiManager.ensureHeaderVisible()`; transition start → `uiManager.ensurePhoneCardVisibleForTransition()`; before overlay → `uiManager.hideMainContentForOverlay()`; on overlay dismiss → `uiManager.showMainContent()`; wipe-down return → `uiManager.runWipeDownEntryAnimation(...)`; after wipe line → `uiManager.runArrivalAnimation(...)`; SMS/instruction side → `uiManager.showSmsSide(...)` / `uiManager.showInstructionSide()`; permission text → `uiManager.setPermissionStatusVisible(...)`.

---

## Reference

- Layouts: `activity_splash.xml`, `activity_activation.xml`, `activity_activated.xml`.
- [03-architecture.md](03-architecture.md) – high-level flow, ActivatedActivity entry and visibility.
- [ui-manager-audit-route-through.md](ui-manager-audit-route-through.md) – audit of “route through UIManager” and implementation checklist.
- [activated-screen-elements.md](activated-screen-elements.md) – detailed Activated screen element list.
