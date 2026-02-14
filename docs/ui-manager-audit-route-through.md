# Audit: Route "what to show" through UIManager

**Rule:** UIManager decides what to show (visibility, alpha, default and state-driven UI). Activities and other code must not set main-content visibility/alpha/scale except by calling the UIManager.

---

## Splash

**Main content:** logo, tagline (for transition).

| Location | What | Decision |
|----------|------|----------|
| `SplashActivity` animateNeonLogo / animateTagline | alpha, scale during splash | **OK** – during-splash animation; UIManager owns only "ready for transition" via `prepareForTransition()`. |
| `SplashActivity` (both nav paths) | transition names + visibility + alpha + scale + translationY | **Done** – via `splashUIManager?.prepareForTransition()`; activity cancels logo/tagline animators before calling. |
| `SplashActivity` onDestroy letterViews reset | letter view alpha/visibility in teardown | **OK** – teardown-only; not main-content "what to show" routing. |

No routing changes for Splash.

---

## Activation

**Main content:** header, content container, utility card, status card container, keypad, retry container, step views.

| Location | What | Action |
|----------|------|--------|
| **L202-203** (before `runWipeUpThenFlip`) | `utilityContentKeyboard.visibility = GONE`, `activationStatusCardContainer.visibility = VISIBLE` | Route through manager: add `ActivationUIManager.showStatusHideKeypad()` and call it here. |
| **L863-865** (onCreate) | `activationLogoText.visibility`, `activationTaglineText.visibility`, `activationHeaderSection.visibility = VISIBLE` | Add to `ActivationUIManager.showDefaultContent()`: set header + logo + tagline visibility VISIBLE. Remove from activity (keep text assignment). |
| **L721** (`clearActivationRetry`) | `activationRetryContainer.visibility = GONE` | Route through manager: add `ActivationUIManager.setRetryVisible(visible: Boolean)`, call `setRetryVisible(false)` here. |
| **L733, L738** (`scheduleAutoRetry`) | `activationRetryContainer.visibility = VISIBLE` | Call `activationUIManager.setRetryVisible(true)` here. |
| L442, 455 (show/hide progress overlay) | `activationStatusCardContainer` animated | Overlay show/hide stays in activity; `applyState` already sets state visibility. No change. |
| L1589 (showKeypadWithAnimation) | keypad visibility | Animation helper; could add `ActivationUIManager.showKeypadWithAnimation()` later. Document as acceptable for now. |

---

## Activated

**Main content:** header, phone card, status card, device info, SMS card, test/reset buttons.

| Location | What | Action |
|----------|------|--------|
| **L489-490** (shared element transition listener `onTransitionStart`) | `phoneCard.visibility = VISIBLE`, `phoneCard.alpha = 1f` | Route through manager: add `ActivatedUIManager.ensurePhoneCardVisibleForTransition()` and call from listener. Keep `setBackgroundResource` in activity. |
| L496-498, 506-509 (onTransitionEnd, onTransitionCancel) | `phoneCard.setBackgroundResource` | Not visibility; leave in activity. |
| L624-678 (`setupWipeDownTransitionForReturnFromUpdate`) | headerSection, phoneCardWrapper, smsCard translationY, alpha, visibility + animate | **Done:** Replaced with `ActivatedUIManager.runWipeDownEntryAnimation(onComplete)`. |
| L836-840 (`runOneByOneArrival`) | views passed to animator | **Done:** Replaced with `ActivatedUIManager.runArrivalAnimation(onComplete)`; arrival calls `showMainContent()` then onComplete. |
| L881-893 (inside arrival) | headerSection.alpha, visibility | **Done:** Owned by manager in `runArrivalAnimation`. |
| MultipurposeCard / CardCoordinator overlay | main content visibility | **Done:** Before each overlay show call `uiManager.hideMainContentForOverlay()`; in each card/overlay onComplete call `uiManager.showMainContent()`. |
| **SMS empty state / list** (L1812, 1973, 3513) | `smsEmptyState`, `smsRecyclerView` visibility | **Done:** Route through `uiManager.showSmsSide(showEmptyState)`. Call sites: loadInitialSmsMessages, Firebase listener submitList, test message post-delayed. |

**Documented exceptions (no change):** Card-flip dim/recede (L638–646); wipe line overlay; instruction overlays and smsFront/instructionBack (activity keeps overlay and card-face flip); phoneCard.alpha in morph end listener (morph-only, not main-content ownership); status label in-card animation (labelView alpha/scale); phoneCodeView visibility (inner element). See plan 1.0c audit table for full list.

---

## Implementation checklist

- [x] **ActivationUIManager:** Add `showStatusHideKeypad()` (keypad GONE, status container VISIBLE). Call from ActivationActivity before runWipeUpThenFlip.
- [x] **ActivationUIManager.showDefaultContent():** Set `activationHeaderSection`, `activationLogoText`, `activationTaglineText` visibility VISIBLE. ActivationActivity onCreate: removed direct visibility lines (keep text); showDefaultContent() called in onCreate.
- [x] **ActivationUIManager:** Add `setRetryVisible(visible: Boolean)`. ActivationActivity: call from clearActivationRetry (false) and scheduleAutoRetry (true).
- [x] **ActivatedUIManager:** Add `ensurePhoneCardVisibleForTransition()`. ActivatedActivity transition listener: call it instead of setting phoneCard.visibility/alpha directly.
- [x] **Docs:** In `docs/ui-manager-and-elements.md`, add rule: "UIManager decides what to show; do not set main-content visibility/alpha/scale outside the UIManager."
- [x] **1.0c FAULT:** SMS empty state / list visibility routed through `uiManager.showSmsSide(showEmptyState)` in ActivatedActivity (loadInitialSmsMessages, Firebase listener, test message callback).
- [x] **1.1:** ActivatedUIManager.hideElementsForAnimation() – deviceInfoColumn included (alpha 0 → animate in at 350 ms delay); withEndAction on last animation calls showMainContent().
- [x] **1.2:** Wipe-down return path – in setupWipeDownTransitionForReturnFromUpdate(), onComplete calls uiManager.showMainContent() before runWipeLineThenMasterCard so all six areas are visible before wipe line.

Optional (later): Move keypad show animation behind ActivationUIManager. Check items (stepResult.alpha, logoView.alpha, ActivationAnimationHelper inputCard/statusCard/utilityCard, SplashActivity view.alpha/scale) documented as exceptions or to route later.

---

## Complete UIManager API (quick reference)

**SplashUIManager** – `prepareForTransition()`.

**ActivationUIManager** – `showDefaultContent()`, `showStatusHideKeypad()`, `setRetryVisible(visible)`, `applyState(...)`, `showStatusTextOnly()`, `ensureHeaderVisible()`, `resetFormCardAppearance()`, `setPhoneInputState(visible, alpha?, scale?)`, `resetActivateButtons()`, `setTestButtonStates(testingAlpha, runningAlpha)`, `hideStatusStepRegisterBuffer()`, `prepareForEntry(...)`.

**ActivatedUIManager** – `setupUIAfterBranding(hasInstructionCard)`, `ensureHeaderVisible()`, `ensurePhoneCardVisibleForTransition()`, `showMainContent()`, `showElementsImmediately(...)`, `ensureElementsVisible(...)`, `hideMainContentForOverlay()`, `runWipeDownEntryAnimation(onComplete)`, `runArrivalAnimation(onComplete)`, `showSmsSide(showEmptyState)`, `showInstructionSide()`, `setPermissionStatusVisible(visible)`.

Full details: [ui-manager-and-elements.md](ui-manager-and-elements.md).
