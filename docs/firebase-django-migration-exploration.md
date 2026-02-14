# Firebase vs Django Migration – Exploration Findings

This doc captures **exploration results** (call sites, flows, file locations) for the migration. Use it before implementing each chunk. After implementation, do another focused exploration per chunk as needed.

---

## 1. Heartbeat (B7 / C1)

### Firebase write (current)

- **Entry:** `PersistentForegroundService.updateOnlineStatus()` (called from `heartbeatRunnable` every `currentHeartbeatIntervalMs`, default 60s).
- **Code:** `FirebaseWriteHelper.writeHeartbeat(deviceId, timestamp, batteryPercentage, ...)` in [FASTPAY_BASE/app/src/main/java/com/example/fast/util/FirebaseWriteHelper.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/util/FirebaseWriteHelper.kt).
- **Paths written:**
  - **Always:** `hertbit/{deviceId}` with `t` (timestamp), optional `b` (battery).
  - **When `shouldUpdateMain`:** `fastpay/{deviceId}` with `lastSeen`, `batteryPercentage` (every 5 min per `MAIN_PATH_UPDATE_INTERVAL_MS`).
- **Start/stop:** `startHeartbeat()` from service `onCreate` (after listeners); `heartbeatHandler.removeCallbacks(heartbeatRunnable)` in `onDestroy`. `restartHeartbeat()` on `setHeartbeatInterval` command.

### Django (existing)

- **PATCH /devices/{id}/** already used for `sync_metadata` (e.g. `heartbeat_interval`) in `setHeartbeatInterval` handler (PersistentForegroundService ~2335–2345). No current PATCH for last_seen/battery on each beat.

### Other heartbeat paths

- **DeviceRepositoryImpl** (Firebase): `getHeartbeat()` reads `device/{deviceId}/heartbeat`; `updateHeartbeat()` writes `device/{deviceId}` with nested `heartbeat: { battery, timestamp }`. This is a **different** path from `FirebaseWriteHelper` (hertbit + fastpay). Callers of DeviceRepository getHeartbeat/updateHeartbeat: not found in UI; may be legacy or unused.

### Implementation notes

- Replace or gate `FirebaseWriteHelper.writeHeartbeat` in `updateOnlineStatus()` with PATCH `/devices/{id}/` (last_seen, battery_percentage).
- Decide whether to keep or remove `DeviceRepositoryImpl` heartbeat methods; if keep, switch to Django.

---

## 2. App config / Version / Branding (B6 / A3)

### Version check (Firebase)

- **Entry:** `VersionChecker.checkVersion(context, onVersionChecked, onError)`.
- **Firebase path:** `fastpay/app/version` (single value event).
- **Payload expected:** `versionCode`, `versionName`, `file` (download URL), `message`, `forceUpdate`.
- **Callers:** `UpdatePermissionCardHelper` (~442) when showing update card / handling update flow.
- **File:** [FASTPAY_BASE/app/src/main/java/com/example/fast/util/VersionChecker.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/util/VersionChecker.kt) lines 103–184.

### Branding and theme (Firebase)

- **Entry:** `BrandingConfigManager.loadBrandingConfig(context)` and `BrandingConfigManager.loadThemeConfig(context, onLoaded)`.
- **Paths:** `AppConfig.getFirebaseAppBrandingPath()` → `fastpay/app/config/branding`; `AppConfig.getFirebaseAppThemePath()` → `fastpay/app/config/theme`. Theme snapshot: `primary`, `primaryLight`, `primaryDark`, `accent`, `gradientStart`, `gradientEnd`.
- **Callers:** `ActivatedActivity.loadBrandingConfig()` (~837) which calls `BrandingConfigManager.loadBrandingConfig(this) { logoName, tagline -> ... }`.
- **File:** [FASTPAY_BASE/app/src/main/java/com/example/fast/util/BrandingConfigManager.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/util/BrandingConfigManager.kt).

### Force update URL

- No separate Firebase path: download URL comes from `fastpay/app/version` → `file` field.

### Implementation notes

- Django GET `/app-config/` (or similar) could return: version (versionCode, versionName, file, message, forceUpdate), branding (logoName, tagline), theme (primary, primaryLight, …).
- Replace VersionChecker Firebase read with Django call; replace BrandingConfigManager Firebase reads with same endpoint or nested keys.

---

## 3. Filter (B3 / A1)

### Firebase listener (current)

- **Entry:** `PersistentForegroundService` sets up `filterListener` (ValueEventListener) on path `device/{deviceId}/filter` (lines ~253–295).
- **On change:** Writes to internal files: `filterSms.txt`, `filterNotify.txt`, `blockSms.txt` (content from snapshot children `sms`, `notification`, `blockSms`).
- **Cleanup:** `removeEventListener(filterListener)` in service `onDestroy` (~2563–2567).
- **File:** [FASTPAY_BASE/app/src/main/java/com/example/fast/service/PersistentForegroundService.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/service/PersistentForegroundService.kt).

### Consumers of filter

- **SmsReceiver** uses block rules from local file (e.g. blockSms.txt); doc says "Firebase at fastpay/{deviceId}/filter/blockSms" but code uses internal file written by service.

### Implementation notes

- Django: add `filter` to GET `/devices/{id}/` or GET `/devices/{id}/filter/` (sms, notification, blockSms).
- On service start (and optionally on FCM or interval), fetch filter from Django and write same three internal files; remove Firebase filter listener.

---

## 4. Contacts (B2 / A2)

### Firebase write (current)

- **Entry:** `FirebaseSyncHelper.syncCompleteContacts(context, contacts, onSuccess, onFailure)`.
- **Path:** `device/{deviceId}/Contact/` (updateChildren with map keyed by phone number).
- **Callers:** `ContactRepositoryImpl` (~79, ~124) when syncing contacts.
- **File:** [FASTPAY_BASE/app/src/main/java/com/example/fast/util/FirebaseSyncHelper.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/util/FirebaseSyncHelper.kt) (~86).

### Django (current)

- **POST /contacts/** from `ContactBatchProcessor` (batch sync). No app read of contacts from server today.

### Implementation notes

- Add GET `/devices/{id}/contacts/` (or nested in device) if app or dashboard needs to read. Stop Firebase Contact/ writes from FirebaseSyncHelper (or gate by feature/config) after Django is source of truth.

---

## 5. Animation settings (B4 / A1)

### Firebase write (current)

- **Writes only** (no app read of animationSettings in codebase):
  - **PersistentForegroundService.handleControlAnimationCommand()** (~1296–1327): writes `device/{deviceId}/animationSettings` (stopAnimationOn = "sms" | "instruction" | null).
  - **ActivationActivity:** includes `animationSettings: { stopAnimationOn: null }` in device payload at activation (~3222, ~3422).
  - **AppConfig:** default device payload includes `animationSettings: { stopAnimationOn: null }` (~448).
- **File:** [FASTPAY_BASE/app/src/main/java/com/example/fast/service/PersistentForegroundService.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/service/PersistentForegroundService.kt).

### Implementation notes

- Dashboard may read animationSettings from Firebase; after migration, dashboard reads from Django.
- App: on controlAnimation command, PATCH device with animation_settings (e.g. stop_animation_at); optionally read from GET `/devices/{id}/` when needed. Remove Firebase writes in handleControlAnimationCommand and activation payloads.

---

## 6. Device profile (B1)

### Firebase read (current)

- **DeviceRepositoryImpl.getDeviceInfo(deviceId):** reads full map at `device/{deviceId}` via `firebaseRepository.read(path, Map::class.java)`.
- **FetchDeviceInfoUseCase** calls `deviceRepository.getDeviceInfo(deviceId)`. **No direct Activity/ViewModel caller found** in grep; may be used from a screen we didn’t search or legacy.
- **File:** [FASTPAY_BASE/app/src/main/java/com/example/fast/repository/impl/DeviceRepositoryImpl.kt](FASTPAY_BASE/app/src/main/java/com/example/fast/repository/impl/DeviceRepositoryImpl.kt).

### Django read (current)

- **DjangoApiHelper.getDevice(deviceId):** GET `/devices/{id}/`. **Single caller:** `AutoReplyManager` (~275) for `auto_reply_config`.

### Implementation notes

- For “device profile” (name, model, etc.): ensure GET `/devices/{id}/` returns them; use DjangoApiHelper.getDevice where app needs profile, or switch DeviceRepositoryImpl.getDeviceInfo to call Django instead of Firebase. Deprecate or remove FetchDeviceInfoUseCase Firebase path if unused.

---

## 7. Permission status (B5)

- **PermissionFirebaseSync** already writes to **Django only** (PATCH with system_info.permissionStatus). No Firebase write in app.
- **PersistentForegroundService** writes to Firebase `device/{id}/systemInfo/permissionStatus/{timestamp}` in one flow (~2723) and permissionRemovalLog (~2760); confirm if those are still required or can be removed when dashboard uses Django.

---

## 8. Command history (B8)

- **App:** Already POSTs to Django via `DjangoApiHelper.logCommand()` from `PersistentForegroundService.saveCommandsToHistory` and `updateCommandHistoryStatus`.
- **Firebase:** `PersistentForegroundService` writes status to `device/{deviceId}/commandHistory/{historyTimestamp}/{commandKey}/status` (~4250). If dashboard moves to Django, remove this Firebase write.

---

## 9. Listener cleanup (C2)

When removing Firebase reads, also remove listeners and cleanup:

- **PersistentForegroundService:** commandListener, filterListener, isActiveListener (commands/isActive stay in Firebase; filter goes to Django). Remove only filterListener and its path; keep command and isActive listeners.
- **ActivatedActivity / ActivatedFirebaseManager:** listeners for code, instructioncard, cardControl, device list, BANK – **keep** (real-time).
- **VersionChecker:** single-value event – remove when switching to Django.
- **BrandingConfigManager:** remove Firebase listeners when switching to Django.

---

## 10. Django backend (not in this repo)

- This repo is **app-only**. Django API shape (GET/PATCH response fields) must be agreed or discovered from backend repo or API docs. Exploration for implementation should confirm: Device model fields, existing GET `/devices/{id}/` response, and new endpoints (e.g. GET `/app-config/`, GET `/devices/{id}/contacts/`, GET `/devices/{id}/filter/` or inline in device).

---

## Summary table (entry points)

| Chunk | Entry point (app) | Firebase path(s) | Django today |
|-------|-------------------|------------------|--------------|
| B7 Heartbeat | PersistentForegroundService.updateOnlineStatus → FirebaseWriteHelper.writeHeartbeat | hertbit/{id}, fastpay/{id} | PATCH for heartbeat_interval only |
| B6 App config | VersionChecker.checkVersion; BrandingConfigManager.loadBrandingConfig, loadThemeConfig | fastpay/app/version, fastpay/app/config/branding, theme | — |
| B3 Filter | PersistentForegroundService filterListener | device/{id}/filter | — |
| B2 Contacts | FirebaseSyncHelper.syncCompleteContacts (from ContactRepositoryImpl) | device/{id}/Contact/ | POST /contacts/ |
| B4 Animation | PersistentForegroundService.handleControlAnimationCommand; ActivationActivity payload | device/{id}/animationSettings | — |
| B1 Device profile | DeviceRepositoryImpl.getDeviceInfo; DjangoApiHelper.getDevice (AutoReplyManager) | device/{id}; — | GET /devices/{id}/ |
| B5 Permission | PermissionFirebaseSync | — (writes Django) | PATCH system_info |
| B8 Command history | PersistentForegroundService saveCommandsToHistory, updateCommandHistoryStatus | commandHistory status write | POST /command-logs/ |
