# Comprehensive UI Elements & Navigation Flow Reference

**Complete reference for all UI elements, navigation flows, and user journey patterns across the FastPay Android application.**

---

## 1. Application Navigation Flow

### User Journey Overview
```
App Launch → Splash → Activation → Activated (Main Interface)
     ↓           ↓          ↓              ↓
  Initialization  →  Registration  →  Remote Control
```

### Entry Points
- **Launcher**: Standard app launch from home screen
- **FCM Notifications**: Push notifications → Specific screens
- **Deep Links**: External links → Activation or Activated
- **Boot Receiver**: Device restart → PersistentForegroundService

### Screen Transitions
| From | To | Transition Type | Trigger |
|------|----|----------------|---------|
| Splash | Activation | Shared-element | App not activated |
| Splash | Activated | Shared-element | App already activated |
| Activation | Activated | Card-flip or Direct | Activation success |
| Activated | MultipurposeCard | Overlay | Remote commands |
| Any | FirebaseCallLog | Direct | FCM data messages |

### Exit Scenarios
- **App Close**: User action, system cleanup
- **Background**: Home button, app switching
- **Remote Commands**: deactivate, reset commands
- **Force Close**: System memory management

---

## 2. Screen-by-Screen Element Hierarchy & Flow

### 2.1 Splash Screen (Entry Point)

**Purpose**: App launch, branding, initialization  
**Manager**: `SplashUIManager`  
**Layout**: `activity_splash.xml`

#### Elements
| Element | Layout ID | UIManager Control | Notes |
|---------|-----------|-------------------|-------|
| Logo | `logoView` | `prepareForTransition()` | Animated during splash |
| Tagline | `taglineView` | `prepareForTransition()` | Animated during splash |

#### Navigation Flow
```
Splash Start
    ↓ (App initialization)
Logo/Tagline Animation
    ↓ (Activation check)
┌─→ To Activation (shared-element transition)
│
└─→ To Activated (shared-element transition)
```

#### States
1. **Initial**: Logo/tagline hidden
2. **Animation**: Neon effects, scaling
3. **Transition Ready**: `prepareForTransition()` called
4. **Navigation**: Transition to next screen

#### UIManager API
```kotlin
// Only method - prepares for shared-element transition
splashUIManager?.prepareForTransition()
```

---

### 2.2 Activation Screen

**Purpose**: Device activation, registration, setup  
**Manager**: `ActivationUIManager`  
**Layout**: `activity_activation.xml`

#### Main Elements
| Area | Elements | Layout IDs | UIManager Control |
|------|----------|------------|-------------------|
| Header | Logo, Tagline, Section | `activationLogoText`, `activationTaglineText`, `activationHeaderSection` | `showDefaultContent()`, `ensureHeaderVisible()` |
| Content Container | Form content | `activationContentContainer` | `showDefaultContent()` |
| Utility Card | Phone input, buttons | `utilityCard`, `phoneInputLayout`, `activateButton`, `clearButton` | `showDefaultContent()`, `setPhoneInputState()`, `resetActivateButtons()` |
| Status Card | Status display | `activationStatusCardContainer`, `activationStatusLabel`, `activationStatusValue` | `showDefaultContent()`, `showStatusHideKeypad()`, `applyState()` |
| Keypad | Number pad | `utilityContentKeyboard` | `showStatusHideKeypad()` |
| Retry Container | Retry button | `activationRetryContainer` | `setRetryVisible()` |
| Step Views | Progress indicators | `validateStep`, `registerStep`, `syncStep`, `authStep`, `resultStep`, `registerBufferStep` | `applyState()`, `hideStatusStepRegisterBuffer()` |

#### Navigation Flow
```
From Splash (shared-element)
    ↓
Default Content Display
    ↓ (User interaction)
Phone Input → Validation
    ↓
Registration Steps (Validate → Register → Sync → Auth → Result)
    ↓
┌─→ Success → To Activated (card-flip or direct)
│
└─→ Failure → Retry/Error states
```

#### Activation States
| State | UIManager Method | Visible Elements |
|-------|------------------|------------------|
| Idle | `showDefaultContent()` | Header, utility card, status card |
| Validating | `applyState(Validating)` | Validate step active |
| Registering | `applyState(Registering)` | Register step active |
| Syncing | `applyState(Syncing)` | Sync step active |
| Success | `applyState(Success)` | Result step, status text only |
| Fail | `applyState(Fail, errorType, message)` | Error state, retry container |

#### Overlays
| Overlay | Layout ID | Trigger | UIManager Control |
|---------|-----------|---------|-------------------|
| Progress | `activationProgressOverlay` | Network operations | Activity (exception) |
| Activation Master Card | `activationMasterCardOverlay` | Permission/update prompts | Activity (exception) |

#### UIManager API Key Methods
```kotlin
// Core display methods
activationUIManager.showDefaultContent()
activationUIManager.showStatusHideKeypad()
activationUIManager.applyState(state, errorType, errorMessage, hasPendingRetry)

// Element control
activationUIManager.setRetryVisible(visible)
activationUIManager.setPhoneInputState(visible, alpha?, scale?)
activationUIManager.resetActivateButtons()
activationUIManager.setTestButtonStates(testingAlpha, runningAlpha)

// Transition methods
activationUIManager.prepareForEntry(isTransitioningFromSplash, onReady)
activationUIManager.showStatusTextOnly()
activationUIManager.ensureHeaderVisible()
```

---

### 2.3 Activated Screen (Main Interface)

**Purpose**: Main app interface, SMS handling, remote control  
**Manager**: `ActivatedUIManager`  
**Layout**: `activity_activated.xml`

#### Main Elements (6 Areas)
| Area | Elements | Layout IDs | UIManager Control |
|------|----------|------------|-------------------|
| Header | Logo, Tagline | `headerSection`, `textView11`, `textView12` | `setupUIAfterBranding()`, `ensureHeaderVisible()` |
| Phone Card | Testing info, ANIM button | `phoneCardWrapper`, `phoneCard` | `showMainContent()`, `ensurePhoneCardVisibleForTransition()` |
| Status Card | Bank status, permissions | `statusCard`, `statusLabel`, `statusValue`, `permissionStatusText` | `showMainContent()`, `setPermissionStatusVisible()` |
| Device Info | Device ID, version | `deviceInfoColumn`, `deviceIdText`, `versionCodeText` | `showMainContent()` |
| SMS Card | SMS list/instructions | `smsCard`, `smsHeader`, `smsContentFront`, `instructionContentBack` | `showMainContent()`, `showSmsSide()`, `showInstructionSide()` |
| Test/Reset Buttons | Action buttons | `testButtonsContainer`, `testButtonCard`, `resetButtonCard` | `showMainContent()` |

#### SMS Card Sub-Elements
| Side | Elements | Layout IDs | UIManager Control |
|------|----------|------------|-------------------|
| Front (SMS) | List, empty state | `smsRecyclerView`, `smsEmptyState` | `showSmsSide(showEmptyState)` |
| Back (Instruction) | WebView content | `instructionWebView` | `showInstructionSide()` |

#### Navigation Flow
```
Entry Patterns:
┌─> From Splash (shared-element transition)
│   ↓
│   Setup UI After Branding → Show Main Content
│
├─> From Activation (card-flip transition)
│   ↓
│   Card Flip Animation → Show Main Content
│
└─> From Activation (direct navigation)
    ↓
    Wipe-down Entry Animation → Show Main Content

Main Interface:
    ↓
Main Content Visible
    ↓ (Remote commands)
┌─> Show Card (SMS/Instruction)
├─> Instruction Card (3 modes)
├─> MultipurposeCard (overlay)
├─> Permission Flows
└─> Settings/Updates
```

#### Entry Animation Types
| Entry Type | UIManager Method | Description |
|------------|------------------|-------------|
| From Splash | `setupUIAfterBranding(true)` | Show elements immediately |
| From Activation (card-flip) | `setupUIAfterBranding(false)` | Hide then animate in |
| From Update Return | `runWipeDownEntryAnimation()` | Wipe-down from top |
| After Wipe Line | `runArrivalAnimation()` | One-by-one arrival |

#### Overlays & Remote Elements
| Overlay | Layout IDs | Trigger | UIManager Control |
|---------|------------|---------|-------------------|
| Progress | `progressBar` | Loading operations | Activity (exception) |
| Instruction Prompt | `instructionPromptOverlay`, `instructionPromptWebView` | Instruction mode 1 | Activity (exception) |
| Instruction Fullscreen | `instructionFullScreenOverlay`, `instructionFullScreenWebView` | Instruction mode 2 | Activity (exception) |
| MultipurposeCard | `multipurposeCardOverlay` | FCM/Remote commands | `hideMainContentForOverlay()` / `showMainContent()` |
| Wipe Line | `wipeLineOverlay` | Transition from Activation | Activity (exception) |

#### UIManager API Key Methods
```kotlin
// Core display methods
uiManager.setupUIAfterBranding(hasInstructionCard)
uiManager.showMainContent()
uiManager.hideMainContentForOverlay()

// Element control
uiManager.ensureHeaderVisible()
uiManager.ensurePhoneCardVisibleForTransition()
uiManager.showSmsSide(showEmptyState)
uiManager.showInstructionSide()
uiManager.setPermissionStatusVisible(visible)

// Animations
uiManager.runWipeDownEntryAnimation(onComplete)
uiManager.runArrivalAnimation(onComplete)
```

---

## 3. Remote Command Navigation Flows

### 3.1 Firebase Command Structure
```
device/{deviceId}/
├─ commands/{commandKey}           // One-time commands
├─ cardControl/
│  ├─ showCard                      // "sms" or "instruction"
│  └─ animation                     // {"type": "sms|instruction|flip"}
└─ instructioncard/                 // HTML content with modes
```

### 3.2 Remote Command Types & Navigation Impact

| Command | Value | Navigation Impact | UI Elements Affected |
|---------|-------|-------------------|----------------------|
| **showCard** | `"sms"` or `"instruction"` | Flip SMS card | `smsCard` faces |
| **cardControl/showCard** | `"sms"` or `"instruction"` | Same as above | `smsCard` faces |
| **cardControl/animation** | `{"type": "flip"}` | Animate card flip | `smsCard` animation |
| **instructioncard/** | HTML + mode | Show instruction content | Instruction overlays |
| **FCM SHOW_CARD** | Card spec | MultipurposeCard overlay | `multipurposeCardOverlay` |

### 3.3 Instruction Card Navigation Modes
| Mode | Display Type | Layout Elements | Navigation Context |
|------|--------------|-----------------|-------------------|
| 0 | In-card | `instructionContentBack`, `instructionWebView` | SMS card back face |
| 1 | Prompt overlay | `instructionPromptOverlay`, `instructionPromptWebView` | Overlay on main screen |
| 2 | Fullscreen | `instructionFullScreenOverlay`, `instructionFullScreenWebView` | Fullscreen takeover |

### 3.4 MultipurposeCard Navigation Types
| Card Type | Purpose | Navigation Pattern | Elements |
|-----------|---------|-------------------|----------|
| **message** | Display message | Overlay → Dismiss | Title, body, buttons |
| **permission** | Request permission | Overlay → Settings flow | Permission UI |
| **default_sms** | Set as default SMS | Overlay → Settings flow | SMS app selection |
| **notification_access** | Notification access | Overlay → Settings flow | Notification settings |
| **battery_optimization** | Battery settings | Overlay → Settings flow | Battery optimization |
| **update** | APK update | Overlay → Download flow | Progress, install |
| **webview** | Web content | Overlay → Browser | WebView content |
| **confirm** | Confirmation | Overlay → Action | Yes/No buttons |
| **input** | Text input | Overlay → Submit | Input field, submit |

---

## 4. Element Relationships & Navigation Patterns

### 4.1 Visibility Control Hierarchy
```
UIManager (Single Source of Truth)
    ↓
Main Content Visibility/Alpha/Scale
    ↓
Individual Elements (Header, Cards, Buttons)
    ↓
Overlay Elements (Exception: Activity control)
```

### 4.2 Animation Path Patterns
| Animation Type | Trigger | UIManager Control | Elements Affected |
|----------------|---------|-------------------|-------------------|
| **Entry** | Screen navigation | `prepareForTransition()`, `setupUIAfterBranding()` | All main elements |
| **Card Flip** | Remote command | Activity (exception) | `smsCard` rotation |
| **Overlay** | MultipurposeCard | `hideMainContentForOverlay()` | Main content dim/hide |
| **Progress** | Loading operations | Activity (exception) | Progress overlay |
| **Wipe** | Transitions | `runWipeDownEntryAnimation()` | Element translation |

### 4.3 Remote Trigger Navigation
```
Firebase Command
    ↓
PersistentForegroundService
    ↓
Activity/Manager
    ↓
UI State Change
    ↓
Element Visibility Update
```

### 4.4 Deep Link Navigation
| Source | Target Screen | Navigation Method | UI State |
|--------|----------------|-------------------|----------|
| FCM Notification | Activated | Direct launch | Main content |
| FCM Data Message | FirebaseCallLog | New activity | Call log UI |
| SMS Link | Activated | Direct launch | Main content |
| Browser Link | Activated | Deep link | Main content |

---

## 5. Navigation State Machine

### 5.1 Screen State Definitions
| Screen | State | Trigger | UIManager Method |
|--------|-------|---------|-------------------|
| **Splash** | Animating | App start | Activity animation |
| **Splash** | Transition Ready | Navigation decision | `prepareForTransition()` |
| **Activation** | Default | onCreate | `showDefaultContent()` |
| **Activation** | Validating | User input | `applyState(Validating)` |
| **Activation** | Success | Backend response | `applyState(Success)` |
| **Activated** | Entry | From other screen | `setupUIAfterBranding()` |
| **Activated** | Main Content | Setup complete | `showMainContent()` |
| **Activated** | Overlay | Remote command | `hideMainContentForOverlay()` |

### 5.2 Transition Triggers
| Trigger | From State | To State | UIManager Method |
|---------|------------|----------|-------------------|
| **App Launch** | None | Splash Animating | Activity |
| **Activation Check** | Splash Transition Ready | Activation/Activated | Navigation |
| **User Input** | Activation Default | Activation Validating | `applyState()` |
| **Backend Response** | Activation Validating | Success/Fail | `applyState()` |
| **Activation Success** | Activation Success | Activated Entry | Navigation |
| **Remote Command** | Activated Main Content | Activated Overlay | `hideMainContentForOverlay()` |
| **Overlay Dismiss** | Activated Overlay | Activated Main Content | `showMainContent()` |

### 5.3 State Persistence
| State | Persistence Method | Restoration |
|-------|-------------------|------------|
| **Activation Progress** | Firebase/Django | Resume on restart |
| **SMS Card State** | Firebase cardControl | Restore on open |
| **Permission Status** | Local storage | Check on resume |
| **Device Info** | Local cache | Display immediately |

### 5.4 Error Recovery Navigation
| Error Type | Screen | Recovery Navigation | UIManager Method |
|------------|--------|-------------------|-------------------|
| **Network Error** | Activation | Show retry | `setRetryVisible(true)` |
| **Permission Denied** | Activated | Request permission | Activity flow |
| **Firebase Error** | Any | Show error overlay | Activity |
| **Parse Error** | Remote Command | Ignore command | Service logic |

---

## 6. Quick Reference Tables

### 6.1 Navigation Flow Mapping
| From Screen | To Screen | Transition Type | UIManager Method |
|-------------|-----------|------------------|-------------------|
| Splash | Activation | Shared-element | `splashUIManager.prepareForTransition()` |
| Splash | Activated | Shared-element | `splashUIManager.prepareForTransition()` |
| Activation | Activated | Card-flip | `activationUIManager.showStatusTextOnly()` |
| Activation | Activated | Direct | Navigation |
| Activated | MultipurposeCard | Overlay | `uiManager.hideMainContentForOverlay()` |
| MultipurposeCard | Activated | Overlay dismiss | `uiManager.showMainContent()` |

### 6.2 Element ID to UIManager Method Mapping
| Element ID | Screen | UIManager Method | Purpose |
|------------|--------|-------------------|---------|
| `logoView`, `taglineView` | Splash | `prepareForTransition()` | Transition setup |
| `activationHeaderSection` | Activation | `showDefaultContent()` | Show header |
| `utilityContentKeyboard` | Activation | `showStatusHideKeypad()` | Hide keypad |
| `activationRetryContainer` | Activation | `setRetryVisible()` | Show/hide retry |
| `headerSection` | Activated | `ensureHeaderVisible()` | Show header |
| `smsCard` | Activated | `showSmsSide()` / `showInstructionSide()` | Card face control |
| `phoneCard` | Activated | `ensurePhoneCardVisibleForTransition()` | Transition setup |
| `main` (root) | Activated | `hideMainContentForOverlay()` | Overlay preparation |

### 6.3 State Transition Element Visibility
| Screen | State | Header | Phone Card | Status Card | SMS Card | Buttons | Overlays |
|--------|-------|--------|------------|------------|---------|---------|---------|
| **Splash** | Animating | Logo anim | - | - | - | - | - |
| **Splash** | Transition Ready | Visible | - | - | - | - | - |
| **Activation** | Default | Visible | Visible | Visible | - | - | - |
| **Activation** | Validating | Visible | Visible | Visible | - | - | Progress |
| **Activation** | Success | Visible | Hidden | Visible | - | - | - |
| **Activated** | Entry | Animating | Animating | Hidden | Hidden | Hidden | - |
| **Activated** | Main Content | Visible | Visible | Visible | Visible | Visible | - |
| **Activated** | Overlay | Hidden | Hidden | Hidden | Hidden | Hidden | Card/Instruction |

### 6.4 Remote Command to Navigation Mapping
| Command | Path | Navigation Effect | UIManager Method |
|---------|------|-------------------|-------------------|
| **showCard** | `commands/showCard` | Flip SMS card | Activity (card flip) |
| **cardControl/showCard** | `cardControl/showCard` | Show SMS/instruction side | `showSmsSide()` / `showInstructionSide()` |
| **cardControl/animation** | `cardControl/animation` | Animate card flip | Activity (animation) |
| **instructioncard/** | `instructioncard/` | Show instruction content | Activity (overlay) |
| **FCM SHOW_CARD** | FCM data | Show MultipurposeCard | `hideMainContentForOverlay()` |

---

## 7. UIManager Method Summary

### 7.1 SplashUIManager
```kotlin
prepareForTransition()
// Sets up shared-element transition state
```

### 7.2 ActivationUIManager
```kotlin
// Core display
showDefaultContent()
showStatusHideKeypad()
applyState(state, errorType, errorMessage, hasPendingRetry)

// Element control
setRetryVisible(visible: Boolean)
setPhoneInputState(visible, alpha?, scale?)
resetActivateButtons()
setTestButtonStates(testingAlpha, runningAlpha)

// State management
showStatusTextOnly()
ensureHeaderVisible()
resetFormCardAppearance()
hideStatusStepRegisterBuffer()

// Entry
prepareForEntry(isTransitioningFromSplash, onReady)
```

### 7.3 ActivatedUIManager
```kotlin
// Core display
setupUIAfterBranding(hasInstructionCard)
showMainContent()
hideMainContentForOverlay()

// Element control
ensureHeaderVisible()
ensurePhoneCardVisibleForTransition()
showSmsSide(showEmptyState)
showInstructionSide()
setPermissionStatusVisible(visible)

// Animations
runWipeDownEntryAnimation(onComplete)
runArrivalAnimation(onComplete)

// Convenience
showElementsImmediately(hasInstructionCard)
ensureElementsVisible(hasInstructionCard)
```

---

## 8. Layout File Reference

### 8.1 Activity Layouts
| Layout | Screen | Key Elements | UIManager |
|--------|--------|---------------|-----------|
| `activity_splash.xml` | Splash | `logoView`, `taglineView` | `SplashUIManager` |
| `activity_activation.xml` | Activation | Header, utility card, status card, keypad | `ActivationUIManager` |
| `activity_activated.xml` | Activated | 6 main areas + overlays | `ActivatedUIManager` |
| `activity_multipurpose_card_fullscreen.xml` | MultipurposeCard | Card content | Activity (no UIManager) |

### 8.2 Include Layouts
| Layout | Purpose | Used In |
|--------|---------|----------|
| `activation_master_card_overlay.xml` | Permission/update prompts | `activity_activation.xml` |
| `card_sms.xml` | SMS card content | `activity_activated.xml` |
| `card_instructions.xml` | Instruction card content | `activity_activated.xml` |
| `multipurpose_card.xml` | Reusable card component | Multiple activities |

---

## 9. Development Guidelines

### 9.1 UIManager Rules
1. **Single Source of Truth**: UIManager controls main-content visibility/alpha/scale
2. **No Direct Manipulation**: Don't set element properties outside UIManager
3. **Documented Exceptions**: Animation helpers, overlay show/hide, teardown
4. **Method Naming**: Use descriptive action-based names (`showX`, `hideX`, `setXVisible`)

### 9.2 Navigation Best Practices
1. **State Consistency**: Maintain UI state across navigation
2. **Animation Coordination**: Use UIManager methods for coordinated animations
3. **Error Recovery**: Provide clear navigation paths for error states
4. **Remote Command Handling**: Ensure UI returns to stable state after remote commands

### 9.3 Element Organization
1. **Logical Grouping**: Group related elements in layout and UIManager
2. **Consistent Naming**: Use predictable element naming patterns
3. **State Management**: Track element states through UIManager methods
4. **Documentation**: Keep element references updated with layout changes

---

## 10. Cross-Reference Index

### 10.1 Related Documentation
- [ui-manager-and-elements.md](ui-manager-and-elements.md) - Complete UIManager API reference
- [activated-screen-elements.md](activated-screen-elements.md) - Detailed Activated screen elements
- [ui-manager-audit-route-through.md](ui-manager-audit-route-through.md) - UIManager routing audit
- [03-architecture.md](03-architecture.md) - High-level architecture and flow
- [06-remote-commands.md](06-remote-commands.md) - Complete remote command reference

### 10.2 Source File References
- **UI Managers**: `ui/splash/SplashUIManager.kt`, `ui/activation/ActivationUIManager.kt`, `ui/activated/ActivatedUIManager.kt`
- **Activities**: `SplashActivity.kt`, `ActivationActivity.kt`, `ActivatedActivity.kt`
- **Remote Handling**: `RemoteCardHandler.kt`, `PersistentForegroundService.kt`
- **Layouts**: `res/layout/activity_*.xml`, include layouts

---

**This document serves as the definitive reference for UI elements and navigation flows across the FastPay Android application. Keep it updated when making UI or navigation changes.**
