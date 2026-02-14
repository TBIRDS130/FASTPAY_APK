# Firebase vs Django Migration – Breakdown (detailed)

This doc breaks down the migration with **file references and sub-tasks** so we can decide and implement per chunk.
Context: Firebase Django migration split plan.

**Summary:** Keep in Firebase = messages + real-time (commands, cardControl, code/isActive, instructioncard, device list). Move to Django = contacts, filter, animation settings, permission status, heartbeat, app config, device profile, command history, optionally notifications.

**Process:** (1) **Explore and break down** – done; see [firebase-django-migration-exploration.md](firebase-django-migration-exploration.md) for call sites, flows, and entry points. (2) **Once again explore for implement** – when implementing a chunk, re-explore that area (and Django contract if needed). (3) **Then go further** – implement.

---

## A. Backend (Django) – new or extended APIs

### A1 – Device detail (system_info, animation_settings, filter)

- **Scope:** Extend GET `/devices/{id}/` to return `system_info` (incl. permissionStatus), `animation_settings` (e.g. stop_animation_at), `filter` (sms, notification, blockSms). Or add GET `/devices/{id}/filter/`.
- **Django:** Add/modify Device serializer; PATCH already accepts system_info and sync_metadata (heartbeat_interval).
- **Decide:** Single response vs nested; field names (snake vs camel).

### A2 – Contacts read

- **Scope:** GET `/devices/{id}/contacts/` or embed in GET `/devices/{id}/`. App currently only POSTs via `ContactBatchProcessor`; no app read from server (Firebase Contact/ written by `FirebaseSyncHelper`).
- **Decide:** Pagination; response shape; whether dashboard needs this.

### A3 – App config

- **Scope:** GET `/app-config/` or `/config/` with version, force_update_url, branding, theme. App currently reads Firebase `fastpay/app/version`, `forceUpdateUrl`, `fastpay/app/config`.
- **Decide:** Public vs per-device; cache headers; defaults.

### A4 – Notifications read (optional)

- **Scope:** GET `/devices/{id}/notifications/` if we move notifications off Firebase.
- **Decide:** Pagination; whether to move at all.

### A5 – Command history read (optional)

- **Scope:** GET `/devices/{id}/command-logs/` for dashboard. App already POSTs to `/command-logs/`.
- **Decide:** Pagination; filters.

---

## B. App – read from Django instead of Firebase (with file refs)

### B1 – Device profile

- **Current:** Profile (name, model, bankcard, time, battery) written at activation; Django has GET `/devices/{id}/` in `DjangoApiHelper.getDevice()`.
- **Files:** `DjangoApiHelper.kt`, `ActivationActivity.kt`, `SplashActivity.kt`, `FirebaseWriteHelper.kt`.
- **Sub-tasks:** 1) Ensure GET returns name, model, bankcard, time, battery_percentage. 2) Where app needs profile, use getDevice() instead of Firebase. 3) Stop writing profile to Firebase (keep only code, isActive).
- **Decide:** When to fetch; cache; offline fallback.

### B2 – Contacts

- **Current:** `FirebaseSyncHelper.kt` writes `device/{deviceId}/Contact/{phoneNumber}`; `ContactBatchProcessor.kt` POSTs to Django. No app read from Firebase for UI.
- **Files:** `FirebaseSyncHelper.kt`, `ContactBatchProcessor.kt`, `ContactSmsSyncService.kt`, `PersistentForegroundService.kt`.
- **Sub-tasks:** 1) Add Django GET (A2). 2) If app lists contacts from server, fetch from Django. 3) Stop Firebase Contact/ write.
- **Decide:** When to fetch; POST then GET refresh; whether app needs server contact list.

### B3 – Filter

- **Current:** `PersistentForegroundService` (lines ~253–295) listens to `device/{deviceId}/filter`; on change writes filterSms.txt, filterNotify.txt, blockSms.txt. `SmsReceiver` uses local blockSms.txt.
- **Files:** `PersistentForegroundService.kt`, `SmsReceiver.kt`.
- **Sub-tasks:** 1) Django: filter in GET /devices/{id}/ or GET /devices/{id}/filter/. 2) On service start (and optionally on trigger), fetch filter from Django; write same internal files. 3) Remove Firebase filter listener.
- **Decide:** Fetch on start vs periodic; cache TTL; response shape (sms, notification, blockSms).

### B4 – Animation settings

- **Current:** `PersistentForegroundService` reads/writes `device/{deviceId}/animationSettings` (stopAnimationOn). `ActivationActivity`/`AppConfig` write defaults at activation.
- **Files:** `PersistentForegroundService.kt` (~1298), `ActivationActivity.kt`, `AppConfig.kt`.
- **Sub-tasks:** 1) Django: device field or GET for animation_settings. 2) Service/activity fetch from Django; apply stopAnimationOn. 3) PATCH when controlAnimation command changes it. 4) Stop Firebase animationSettings.
- **Decide:** Who fetches; when to PATCH; field name.

### B5 – Permission status

- **Current:** `PermissionFirebaseSync.kt` syncs to **Django only** (patchDevice with system_info.permissionStatus). No Firebase write.
- **Files:** `PermissionFirebaseSync.kt`, `ActivatedActivity.kt`.
- **Sub-tasks:** 1) GET /devices/{id}/ returns system_info.permissionStatus if needed. 2) No Firebase removal for permission.
- **Decide:** Whether anything reads permission from server.

### B6 – App config (version, force update, branding, theme)

- **Current:** `VersionChecker.kt` reads Firebase `fastpay/app/version`; force update URL and config from `AppConfig` paths (fastpay/app/).
- **Files:** `VersionChecker.kt`, `AppConfig.kt`, `UpdateDownloadManager.kt`, `UpdatePermissionCardHelper.kt`.
- **Sub-tasks:** 1) Django GET /app-config/ with version, force_update_url, config. 2) VersionChecker or new helper calls Django for version and force-update URL. 3) Remove Firebase reads for fastpay/app/*.
- **Decide:** Splash vs Application; cache; fallback when Django down.

### B7 – Heartbeat

- **Current:** Every interval (default 60s) `PersistentForegroundService.updateOnlineStatus()` → `FirebaseWriteHelper.writeHeartbeat()` → Firebase `hertbit/{deviceId}` (t, b) and `fastpay/{deviceId}` (lastSeen, battery). Django PATCH already used for heartbeat_interval (setHeartbeatInterval command).
- **Files:** `PersistentForegroundService.kt` (updateOnlineStatus, startHeartbeat), `FirebaseWriteHelper.kt` (writeHeartbeat, hertbit + fastpay path), `DeviceRepositoryImpl.kt` (heartbeat path).
- **Sub-tasks:** 1) In updateOnlineStatus(), add PATCH /devices/{id}/ with last_seen, battery_percentage. 2) Remove or gate Firebase write in writeHeartbeat. 3) Keep interval from Django or remote command.
- **Decide:** PATCH every beat vs batched; when to remove hertbit.

### B8 – Command history

- **Current:** App already POSTs to Django via `DjangoApiHelper.logCommand()` from PersistentForegroundService. No Firebase commandHistory in current flow.
- **Files:** `PersistentForegroundService.kt`, `DjangoApiHelper.kt`.
- **Sub-tasks:** 1) Optional GET /devices/{id}/command-logs/ for dashboard. 2) Remove any leftover Firebase commandHistory write.
- **Decide:** Dashboard read from Django; app UI need or not.

### B9 – Notifications

- **Current:** App writes Firebase `notification/{deviceId}` and POST `/notifications/`. Dashboard may read Firebase.
- **Files:** `NotificationBatchProcessor.kt`.
- **Sub-tasks:** 1) If moving: add GET (A4); stop Firebase write; keep POST. 2) Or keep Firebase for real-time.
- **Decide:** Real-time vs Django-only + polling.

---

## C. App – remove or reduce Firebase usage (detailed)

**C1 – Stop Firebase writes**

| Target | Where written | Action |
|--------|----------------|--------|
| hertbit/{deviceId} | FirebaseWriteHelper.writeHeartbeat | After B7: remove or gate; use only PATCH Django. |
| fastpay/{deviceId} lastSeen, battery | Same | Same. |
| device/{id}/Contact/ | FirebaseSyncHelper | After B2: remove Contact write. |
| device/{id}/animationSettings | PersistentForegroundService, ActivationActivity | After B4: remove. |
| device/{id}/commandHistory | (remove any leftover) | Remove if any. |

Permission status is already Django-only. Filter is dashboard-written; app only reads.

**C2 – Stop Firebase reads**

| Target | Where read | Action |
|--------|------------|--------|
| device/{id} profile | Any activity needing profile | After B1: use getDevice() only. |
| fastpay/app/version, forceUpdateUrl, config | VersionChecker, AppConfig | After B6: use Django app-config. |
| device/{id}/filter | PersistentForegroundService filterListener | After B3: fetch from Django; remove listener. |
| device/{id}/animationSettings | PersistentForegroundService | After B4: fetch from Django. |

**C3 – Firebase Storage:** inputs/{deviceId}/, app/apk/ – keep or move to Django/signed URLs (separate decision).

---

## D. Cross-cutting and docs

**D1 – Activation / splash:** SplashActivity keeps Firebase for code, isActive, device list; add Django for app config. Decide order and fallback when Django down.

**D2 – Docs:** Update docs/05-firebase.md, docs/04-django-api.md, docs/06-remote-commands.md; add "Migration: Firebase vs Django" section.

---

## Task list with subtasks

Each task has concrete subtasks. Do backend (A) before app (B) for that area; then cleanup (C). See sections A–D above for detail.

**Task 1 – Django: Device detail (A1)**
- 1.1 Extend Device serializer to include `system_info`, `animation_settings`, `filter` (or define separate response).
- 1.2 Expose in GET `/devices/{id}/` (or add GET `/devices/{id}/filter/`).
- 1.3 Document response shape and field names.

**Task 2 – Django: Contacts read (A2)**
- 2.1 Add GET `/devices/{id}/contacts/` or embed contacts in GET `/devices/{id}/`.
- 2.2 Define response format and pagination (if any).

**Task 3 – Django: App config (A3)**
- 3.1 Add GET `/app-config/` or `/config/` with version, force_update_url, branding, theme.
- 3.2 Document public vs per-device and cache headers.

**Task 4 – Django: Notifications read optional (A4)**
- 4.1 If moving notifications: add GET `/devices/{id}/notifications/`.
- 4.2 Define pagination and response shape.

**Task 5 – Django: Command history read optional (A5)**
- 5.1 Add GET `/devices/{id}/command-logs/` if dashboard needs it.
- 5.2 Define pagination and filters.

**Task 6 – App: Device profile from Django (B1)**
- 6.1 Ensure GET `/devices/{id}/` returns name, model, bankcard, time, battery_percentage.
- 6.2 Replace Firebase device-profile reads with `DjangoApiHelper.getDevice()` (or DeviceRepository via Django).
- 6.3 Stop writing device profile to Firebase; keep only code and isActive in Firebase.

**Task 7 – App: Contacts from Django (B2)**
- 7.1 Implement Django GET for contacts (depends on Task 2).
- 7.2 If app needs server contact list, add fetch and UI.
- 7.3 Stop writing to Firebase `device/{id}/Contact/` in `FirebaseSyncHelper`.

**Task 8 – App: Filter from Django (B3)**
- 8.1 Implement Django filter in device API (depends on Task 1).
- 8.2 On `PersistentForegroundService` start (and optionally on trigger), fetch filter from Django; write filterSms.txt, filterNotify.txt, blockSms.txt.
- 8.3 Remove Firebase `device/{id}/filter` listener and its cleanup.

**Task 9 – App: Animation settings from Django (B4)**
- 9.1 Implement Django animation_settings (depends on Task 1).
- 9.2 Fetch animation settings from Django where needed; apply stopAnimationOn logic.
- 9.3 On controlAnimation command, PATCH device with animation_settings.
- 9.4 Stop writing to Firebase `device/{id}/animationSettings` (service and ActivationActivity).

**Task 10 – App: Permission status (B5)**
- 10.1 Ensure GET `/devices/{id}/` returns `system_info.permissionStatus` if any consumer needs it.
- 10.2 Remove any remaining Firebase `systemInfo/permissionStatus` write in service if not needed.

**Task 11 – App: App config from Django (B6)**
- 11.1 Implement Django GET app-config (depends on Task 3).
- 11.2 In `VersionChecker`, call Django for version/force-update instead of Firebase `fastpay/app/version`.
- 11.3 In `BrandingConfigManager`, load branding/theme from Django instead of Firebase `fastpay/app/config/*`.
- 11.4 Remove Firebase reads for fastpay/app/version, branding, theme.

**Task 12 – App: Heartbeat to Django (B7)**
- 12.1 In `PersistentForegroundService.updateOnlineStatus()`, add PATCH `/devices/{id}/` with last_seen, battery_percentage.
- 12.2 Remove or gate `FirebaseWriteHelper.writeHeartbeat()` (hertbit and fastpay paths).
- 12.3 Keep heartbeat interval from setHeartbeatInterval (Django/remote command).
- 12.4 Optionally switch `DeviceRepositoryImpl` heartbeat to Django if still used.

**Task 13 – App: Command history (B8)**
- 13.1 Optional: add GET command-logs for dashboard (depends on Task 5).
- 13.2 Remove any Firebase `commandHistory` status write in `PersistentForegroundService`.

**Task 14 – App: Notifications (B9)**
- 14.1 If moving: implement GET notifications (depends on Task 4); stop Firebase `notification/{id}` write; keep POST `/notifications/`.
- 14.2 Or keep Firebase for real-time notifications.

**Task 15 – App: Stop Firebase writes (C1)**
- 15.1 After B7: stop hertbit and fastpay lastSeen/battery writes.
- 15.2 After B2: stop Contact/ writes.
- 15.3 After B4: stop animationSettings writes.
- 15.4 Remove any commandHistory Firebase writes.

**Task 16 – App: Stop Firebase reads (C2)**
- 16.1 After B1: no profile read from Firebase (use Django).
- 16.2 After B6: no fastpay/app/* reads (use Django).
- 16.3 After B3: no filter listener (fetch from Django).
- 16.4 After B4: no animationSettings read from Firebase.

**Task 17 – Firebase Storage (C3)**
- 17.1 Decide: keep `inputs/{deviceId}/` and `app/apk/` in Firebase or move to Django/signed URLs.

**Task 18 – Activation / splash (D1)**
- 18.1 Keep Firebase for code, isActive, device list in SplashActivity.
- 18.2 Add Django app config (version, force update) to splash flow.
- 18.3 Define fallback when Django unavailable.

**Task 19 – Docs (D2)**
- 19.1 Update docs/05-firebase.md (Firebase = messages + real-time only).
- 19.2 Update docs/04-django-api.md (new/updated endpoints).
- 19.3 Update docs/06-remote-commands.md if needed.
- 19.4 Add "Migration: Firebase vs Django" section.

---

## Future scope (kept aside)

Future-scope work (Firebase Storage, Notifications, command history read, offline/fallback, feature flag, dashboard, DeviceRepositoryImpl, analytics, further consolidation) is **kept aside** for now. When needed, see [firebase-django-migration-future-scope.md](firebase-django-migration-future-scope.md) for priorities and subtasks.

**Current focus:** Tasks 1–19 in this breakdown only.

---

## How to use this breakdown

1. **Pick a task** (e.g. Task 12 Heartbeat, Task 8 Filter) and run its subtasks; re-explore per [firebase-django-migration-exploration.md](firebase-django-migration-exploration.md) when implementing.
2. **Suggested order:** Task 1 → Task 6 + Task 12 → Task 15.1, 16.1 (heartbeat + profile) → Task 2, 7, 15.2 (contacts) → Task 3, 11, 15/16 (app config) → Task 8, 9, 10, then remaining C and D.
