# Android Components Audit: Used vs Unused

This document lists every Android component declared in `FASTPAY_BASE/app/src/main/AndroidManifest.xml`, with **usage frequency** and **unused** components. Counts are from the FASTPAY_BASE codebase (Kotlin + XML).

---

## 1. Application

| Component | Declaration | References (Kotlin) | Notes |
|-----------|-------------|---------------------|--------|
| **FastPayApplication** | `android:name=".FastPayApplication"` | 1 file (self): init, logging | Entry point; used by system. |

---

## 2. Activities — Used (with frequency)

| Component | Manifest | Nav graph | Layout (tools:context) | Intent/startActivity refs | Files referencing | Notes |
|-----------|----------|-----------|--------------------------|----------------------------|-------------------|--------|
| **SplashActivity** | ✓ | ✓ (startDestination) | activity_splash.xml | 3 launch sites: PersistentForegroundService (1), ActivatedActivity (1), self → Activation/Activated | 4 files | Launcher; high usage. |
| **ActivationActivity** | ✓ | ✓ | activity_activation.xml | 4 launch sites: SplashActivity (1), MultipurposeCardActivity (1), PersistentForegroundService (1), self | 5+ files | Core flow; high usage. |
| **ActivatedActivity** | ✓ | ✓ | activity_activated.xml | 8+ launch sites: ActivationActivity (2), SplashActivity (2), MultipurposeCardHelper (2), NotificationActionReceiver (1), AppNotificationManager (2), PersistentForegroundService (1), MultipurposeCardActivity (1) | 10+ files | Main screen; **highest usage**. |
| **MultipurposeCardActivity** | ✓ | — | activity_multipurpose_card_fullscreen.xml | 10+ launch sites: ActivatedActivity, RemoteCardHandler, PermissionGateCardHelper, UpdatePermissionCardHelper (4), PersistentForegroundService (3), self (2) | 10+ files | Cards (update, permission, default SMS, message); **high usage**. |

---

## 3. Activities — Unused (no active launch)

| Component | Manifest | Nav graph | Launch from code | Notes |
|-----------|----------|-----------|-------------------|--------|
| **FirebaseCallLogActivity** | ✓ | ✓ | **None** (only commented-out in ActivatedActivity) | Declared and in nav graph but never started. Effectively **unused**. |

---

## 4. BroadcastReceivers — Used (with frequency)

| Component | Declaration | Referenced from code | Files | Notes |
|-----------|-------------|----------------------|-------|--------|
| **SmsReceiver** | ✓ (SMS_RECEIVED, SMS_DELIVER) | SmsTestHelper (broadcast + processTestMessage), TestSmsReceiver (forward), SmsService (comment) | 4 files | Core SMS handling; **high**. |
| **TestSmsReceiver** | ✓ (TEST_SMS_ACTION) | SmsTestHelper sends broadcast; forwards to SmsReceiver | 2 files | Test path only. |
| **MmsReceiver** | ✓ (WAP_PUSH_DELIVER) | Self only | 1 file | System-triggered; used. |
| **BootCompletedReceiver** | ✓ (BOOT_COMPLETED) | Self; starts PersistentForegroundService | 1 file | System-triggered; used. |
| **NotificationActionReceiver** | ✓ | AppNotificationManager (Intent to this receiver) | 2 files | Notification actions; used. |
| **ScheduledSmsReceiver** | ✓ | PersistentForegroundService (Intent to this receiver) | 2 files | Scheduled SMS; used. |

---

## 5. Services — Used (with frequency)

| Component | Declaration | Started/referenced from | Files | Notes |
|-----------|-------------|--------------------------|-------|--------|
| **PersistentForegroundService** | ✓ (foregroundServiceType=dataSync) | ActivationActivity, ActivatedActivity, BootCompletedReceiver, ActivatedServiceManager, self (restart); WorkflowExecutor (comment) | 8+ files | **Highest service usage**. |
| **ContactSmsSyncService** | ✓ | FcmMessageService (3), ActivationActivity (1), PermissionSyncHelper (3), FirebaseSyncHelper (comment) | 5+ files | Sync; high. |
| **NotificationReceiver** (NotificationListenerService) | ✓ | ActivationActivity (ComponentName), PermissionManager, PermissionFirebaseSync | 3 files | Notification access; used. |
| **FcmMessageService** | ✓ (Firebase MESSAGING_EVENT) | ActivatedActivity (IntentFilter action), FcmTokenManager (comment) | 2 files | FCM; system + in-app ref. |
| **SmsService** | ✓ (IMessagingService) | Self + package-info only | 2 files | **System-bound** (default SMS); no app start. |
| **HeadlessSmsSendService** | ✓ (RESPOND_VIA_MESSAGE) | Self + package-info only | 2 files | **System-bound**; no app start. |

---

## 6. ContentProvider — Used

| Component | Declaration | Referenced from | Files | Notes |
|-----------|-------------|-----------------|-------|--------|
| **FileProvider** (androidx) | ✓ (${applicationId}.fileprovider) | MultipurposeCardActivity, UpdatePermissionCardHelper | 2 files | APK install; used. |

---

## 7. Summary

### Used components (by appearance frequency, high → low)

1. **ActivatedActivity** — Most referenced (main UI, notifications, cards, activation flow).
2. **MultipurposeCardActivity** — Many launch paths (cards, permissions, update, default SMS, message).
3. **PersistentForegroundService** — Started from multiple activities and BootCompletedReceiver.
4. **ActivationActivity** — Core activation flow.
5. **SplashActivity** — Launcher and re-entry.
6. **ContactSmsSyncService** — Started from FCM and permission/sync flows.
7. **SmsReceiver** — SMS handling + test path.
8. **NotificationActionReceiver**, **NotificationReceiver** — Notifications and notification access.
9. **ScheduledSmsReceiver**, **BootCompletedReceiver**, **TestSmsReceiver**, **MmsReceiver** — Fewer refs but all used.
10. **FcmMessageService**, **FileProvider** — Used.
11. **SmsService**, **HeadlessSmsSendService** — Used by system only (no in-app Intent).

### Unused components

| Component | Type | Recommendation |
|-----------|------|----------------|
| **FirebaseCallLogActivity** | Activity | Remove from manifest and nav_graph if not needed, or add a launch path (e.g. debug menu). |

---

## 8. Verification

- **Manifest:** `FASTPAY_BASE/app/src/main/AndroidManifest.xml`
- **Navigation:** `FASTPAY_BASE/app/src/main/res/navigation/nav_graph.xml`
- Counts obtained by searching for component class names and `Intent(..., X::class.java)` / `startActivity` / `startService` / `startForegroundService` in `FASTPAY_BASE/app/src/main`.
