# Django API

## Base Configuration

**Base URL:** From BuildConfig `DJANGO_API_BASE_URL` (default `https://api-staging.fastpaygaming.com/api`, overridable via `.env`).

**Default Headers:**
```
Accept: application/json
Accept-Encoding: gzip
Content-Type: application/json; charset=utf-8
```

**Timeouts:** Connection 15s, Read 30s, Write 30s.

**Retry:** Max 3 retries, exponential backoff from 1000ms; retry on HTTP ≥ 500 and network failures.

---

## Endpoints Used by the APK

| # | Method | Endpoint | Purpose | Used In |
|---|--------|----------|---------|---------|
| 1 | POST | `/devices/` | Register device | SplashActivity, ActivationActivity |
| 2 | PATCH | `/devices/{deviceId}/` | Update device (sync_metadata, system_info, is_active) | PersistentForegroundService, PermissionFirebaseSync, DeviceInfoCollector, AutoReplyManager |
| 3 | GET | `/devices/{deviceId}/` | Get device config (auto_reply, etc.) | AutoReplyManager |
| 4 | POST | `/messages/` | Bulk sync SMS | SmsMessageBatchProcessor |
| 5 | POST | `/contacts/` | Bulk sync contacts | ContactBatchProcessor |
| 6 | POST | `/notifications/` | Bulk sync notifications | NotificationBatchProcessor |
| 7 | POST | `/command-logs/` | Log command execution (no retry) | Command handlers |
| 8 | POST | `/auto-reply-logs/` | Log auto-reply sent | AutoReplyManager |
| 9 | POST | `/activation-failure-logs/` | Log activation failures | ActivationActivity |
| 10 | POST | `/registerbanknumber` | Register phone for TESTING mode | ActivationActivity |
| 11 | POST | `/validate-login/` | Validate code for RUNNING mode | SplashActivity, ActivationActivity |
| 12 | POST | `/fcm-tokens/` | Register FCM token | FcmTokenManager |
| 13 | POST | `/fcm-tokens/unregister/` | Unregister FCM token | FcmTokenManager |

---

## When Each API Is Called

- **Activation:** POST /registerbanknumber (TESTING), POST /validate-login/ (RUNNING), POST /devices/ after success, POST /activation-failure-logs/ on failure.
- **App lifecycle:** POST /devices/ from SplashActivity; PATCH /devices/{id}/ for metadata and permission sync.
- **Sync:** POST /messages/, /contacts/, /notifications/ from batch processors.
- **FCM:** POST /fcm-tokens/, /fcm-tokens/unregister/ from FcmTokenManager.
- **Auto-reply:** GET /devices/{id}/ for config; POST /auto-reply-logs/ when sending.

**Retries:** Used for `/devices/`, `/messages/`, `/contacts/`, `/notifications/`, `/fcm-tokens/`. No retry for `/command-logs/`, `/auto-reply-logs/`, `/activation-failure-logs/`.

---

## Request/Response Shapes (Summary)

### POST /devices/
Request: `device_id`, `model`, `phone`, `code`, `is_active`, `last_seen`, `battery_percentage`, `current_phone`, `current_identifier`, `time`, `bankcard`, `system_info`, `app_version_code`, `app_version_name`. Response: 201/200.

### PATCH /devices/{deviceId}/
Request: `sync_metadata`, `system_info`, etc. Response: 200.

### GET /devices/{deviceId}/
Response: device config including `auto_reply_config` (enabled, message).

### POST /messages/
Request: array of `{ device_id, message_type, phone, body, timestamp, read }`. Response: 201.

### POST /contacts/
Request: array of contact objects with device_id, name, phone_number, etc. Response: 201.

### POST /notifications/
Request: array of notification objects. Response: 201.

### POST /command-logs/
Request: `device_id`, `command`, `value`, `status`, `received_at`, `executed_at`, `error_message`. No retry.

### POST /registerbanknumber (TESTING)
Request: `phone`, `code`, `device_id`, `model`, `app_version_code`, `app_version_name`.

### POST /validate-login/ (RUNNING)
Request: `code`, `device_id`. Response (valid): `{ "valid": true, "approved": true }`; invalid: 401/404.

### POST /fcm-tokens/
Request: `device_id`, `fcm_token`, `platform`, `model`. Response: 201.

---

For full request/response examples and Django model definitions, see the original API contract with the backend team. Implementation: `DjangoApiHelper.kt`, call sites as in the table above.

**API calls by screen/flow:** For a UI-oriented map of where each endpoint is triggered (Splash, Activation, Activated, background), see [API Calls by UI](13-api-calls-by-ui.md).

---

## Migration: API contract for Tasks 1–6 (backend to implement)

The app expects or will use the following once implemented. **Backend (Django) is in another repo;** this section is the contract for Tasks 1–6.

### Task 1 – GET /devices/{id}/ extended (device detail)

Response should include (in addition to existing fields such as `auto_reply_config`):

- **system_info** (object, optional): e.g. `permissionStatus` (map of permission name to boolean).
- **animation_settings** (object, optional): e.g. `stop_animation_at` or `stopAnimationOn` (`"sms"` | `"instruction"` | null).
- **filter** (object, optional): `sms` (string), `notification` (string), `blockSms` (string).

Alternatively, filter can be a separate GET `/devices/{id}/filter/` with the same shape.

### Task 6.1 – GET /devices/{id}/ device profile fields

Response should include for device profile (app uses these when available):

- **name** (string, optional)
- **model** (string)
- **bankcard** (string, optional)
- **time** (number, optional, ms)
- **battery_percentage** (number, optional, 0–100 or -1)

### Task 2 – GET /devices/{id}/contacts/

- **Endpoint:** GET `/devices/{id}/contacts/` (or contacts nested in GET `/devices/{id}/`).
- **Response:** List of contact objects (e.g. id, name, phone_number, etc.); pagination (limit/offset or cursor) if needed.

### Task 3 – GET /app-config/ or /config/

- **Endpoint:** GET `/app-config/` or `/config/` (public or per-device).
- **Response:** At least: **version** (object with versionCode, versionName, file, message, forceUpdate), **force_update_url** (string, optional), **branding** (e.g. logoName, tagline), **theme** (e.g. primary, primaryLight, primaryDark, accent, gradientStart, gradientEnd as color strings).
- **Caching:** Optional cache headers for clients.

### Task 4 – GET /devices/{id}/notifications/ (optional)

- **Endpoint:** GET `/devices/{id}/notifications/`.
- **Response:** List of notification objects; pagination if needed.

### Task 5 – GET /devices/{id}/command-logs/ (optional)

- **Endpoint:** GET `/devices/{id}/command-logs/`.
- **Response:** List of command log entries; pagination and filters (e.g. by status, date) if needed.
