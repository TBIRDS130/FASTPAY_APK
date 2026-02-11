# Architecture

## High-Level Runtime Flow

1. **Activation & registration** – Device is activated (TESTING or RUNNING mode); data is written to Firebase and Django.
2. **Remote commands** – Commands at `device/{deviceId}/commands/` are executed by `PersistentForegroundService`; results are sent to Django.
3. **Sync** – Messages, contacts, notifications, and device info are synced to Django (and Firebase where applicable).

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.2, JVM 17 |
| UI | ViewBinding, Activities, custom views |
| DI | Koin |
| Backend | Django API (OkHttp, Gson), Firebase Realtime DB + Storage, FCM |
| Async | Kotlin Coroutines |
| Logging | Timber, app Logger; DebugLogger (copyable logs, init in FastPayApplication) |

## Source Layout (FASTPAY_BASE/app/src/main)

```
java/com/example/fast/
├── FastPayApplication.kt     # Koin, Firebase, Logger, DebugLogger, Crashlytics
├── config/AppConfig.kt       # API URLs, Firebase paths
├── core/
│   ├── base/                 # BaseActivity, BaseRepository, BaseViewModel
│   ├── error/                # FastPayException, FirebaseException, etc.
│   └── result/Result.kt      # Result<T> success/failure
├── di/KoinModules.kt         # Dependency injection
├── domain/usecase/           # ActivateDevice, FetchDeviceInfo, SendSms, SyncContacts
├── model/                    # ChatMessage, Contact, Instruction, SmsConversation (no exceptions; use core/error)
├── repository/               # Interfaces + impl/ (Contact, Device, Firebase, Sms)
├── ui/                       # Activities, ViewModels, animations, card UI (CardCoordinator = single entry for cards; RemoteCardHandler.buildSpec)
├── service/                  # PersistentForegroundService, SmsService, FCM, etc.
├── receiver/                 # Sms, Mms, Boot, ScheduledSms, NotificationAction
├── notification/             # AppNotificationManager, NotificationActionReceiver
├── adapter/SmsMessageAdapter.kt
├── workers/                  # BackupMessagesWorker, ExportMessagesWorker
└── util/                     # Firebase, DjangoApiHelper, SMS/contact utils (see util/ for full list: SmsMessageBatchProcessor, ContactBatchProcessor, PermissionFirebaseSync, WorkflowExecutor, etc.)
```

**Single source of truth:** Exceptions live only in `core/error/`; the `Result<T>` type lives only in `core/result/Result.kt`. Do not add exception or Result types in `util` or `model`.

## ActivatedActivity entry and visibility

ActivatedActivity can be entered from **SplashActivity** (with shared-element transition), **ActivationActivity** (with shared-element transition, or with card-flip transition, or without transition). Visibility and animation are coordinated as follows:

- **Single owner for “after branding” state:** `ActivatedUIManager.setupUIAfterBranding()` is the place that decides whether to show all elements immediately (`isTransitioningFromSplash == true`) or to hide then animate in (`hideElementsForAnimation()`). Do not overwrite that state from elsewhere (e.g. avoid calling `forceAllCardsVisible()` when `isTransitioningFromSplash` is false, or a posted runnable can set alpha=1 and break the fade-in).
- **Card-flip entry:** When `useCardFlipEntry` is true, visibility is driven by `setupCardFlipTransition()` and its completion; `setupUI()` does not call `forceAllCardsVisible()`.
- **Main-chain visibility:** The six main content elements (header, phone card, status card, device info, SMS card, test/reset buttons) are owned by `ActivatedUIManager.showMainContent()`; `ActivatedActivity` does not set their visibility/alpha/scale elsewhere.

**UIManager (Splash, Activation, Activated):** Each of the three main screens has one UIManager in `ui/<screen>/` that owns main-content visibility, alpha, and scale. Activities and other code must not set these outside the manager (see [ui-manager-and-elements.md](ui-manager-and-elements.md) for the rule and exceptions).

| Screen     | UIManager              | Key methods |
|------------|------------------------|-------------|
| Splash     | `SplashUIManager`       | `prepareForTransition()` |
| Activation | `ActivationUIManager`   | `showDefaultContent()`, `showStatusHideKeypad()`, `setRetryVisible()`, `applyState()`, `showStatusTextOnly()`, `ensureHeaderVisible()`, `resetFormCardAppearance()`, `setPhoneInputState()`, `resetActivateButtons()`, `setTestButtonStates()`, `hideStatusStepRegisterBuffer()`, `prepareForEntry()` |
| Activated  | `ActivatedUIManager`    | `setupUIAfterBranding()`, `ensureHeaderVisible()`, `ensurePhoneCardVisibleForTransition()`, `showMainContent()`, `hideMainContentForOverlay()`, `runWipeDownEntryAnimation()`, `runArrivalAnimation()`, `showSmsSide()`, `showInstructionSide()`, `setPermissionStatusVisible()` |

Full API, elements, call flows, and audit: [ui-manager-and-elements.md](ui-manager-and-elements.md) and [ui-manager-audit-route-through.md](ui-manager-audit-route-through.md).

## Main Components (Manifest)

- **Activities:** SplashActivity (launcher), ActivationActivity, ActivatedActivity, FirebaseCallLogActivity, MultipurposeCardActivity.
- **Services:** PersistentForegroundService (dataSync), SmsService (default SMS), HeadlessSmsSendService, ContactSmsSyncService, NotificationReceiver (NotificationListener), FcmMessageService.
- **Receivers:** SmsReceiver, TestSmsReceiver, MmsReceiver, BootCompletedReceiver, ScheduledSmsReceiver, NotificationActionReceiver.
- **Provider:** FileProvider (APK installs).

## MultipurposeCard (expectation)

MultipurposeCard is **usable at any point in the application**. Each card has **3 animation phases**: **born → stay → dead**. Entry: [CardCoordinator](FASTPAY_BASE/app/src/main/java/com/example/fast/ui/card/CardCoordinator.kt); spec: [MultipurposeCardSpec](FASTPAY_BASE/app/src/main/java/com/example/fast/ui/card/MultipurposeCardSpec.kt).

| Phase | Spec | What you control |
|-------|------|-------------------|
| **Born** | birth | How it appears: BirthSpec.entranceAnimation (FlipIn, FadeIn, ScaleIn). From where: originView, recedeViews. Shape: width, height (CardSize), PlacementSpec. |
| **Stay** | fillUp | Content while visible: FillUpSpec — Text (title, body, typing) or WebView (html/url, JS bridge). |
| **Dead** | death | Dismiss animation: DeathSpec — FlipOut, FadeOut, ScaleDown, SlideOut, ShrinkInto(targetView). |

**canIgnore:** If true, touching outside the card (on the overlay) starts the death animation and finishes; if false, outside touch has no effect.

Purpose (buttons/actions) is separate; see PurposeSpec in MultipurposeCardSpec.kt.

## Build Configuration

- **Namespace / applicationId:** `com.example.fast`
- **SDK:** min 27, target/compile 36, buildTools 36.0.0
- **BuildConfig:** `DJANGO_API_BASE_URL`, `FIREBASE_STORAGE_BUCKET` (from `.env` when present)

For build details see [Build and Run](02-build-and-run.md); for API and Firebase see [Django API](04-django-api.md) and [Firebase](05-firebase.md).
