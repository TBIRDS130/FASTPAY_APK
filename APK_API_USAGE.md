# FastPay APK v3.0 – API Usage List

**Base URL:** `https://api.fastpaygaming.com` (from BuildConfig, overridable via `.env`)

**APK Version:** 3.0 (versionCode 30)

---

## Django API Endpoints Used

| # | Method | Endpoint | Purpose | Used In |
|---|--------|----------|---------|---------|
| 1 | POST | `/devices/` | Register device | SplashActivity, ActivationActivity |
| 2 | PATCH | `/devices/{deviceId}/` | Update device (sync_metadata, system_info, is_active) | PersistentForegroundService, PermissionFirebaseSync, DeviceInfoCollector, AutoReplyManager |
| 3 | GET | `/devices/{deviceId}/` | Get device config (auto_reply, etc.) | AutoReplyManager |
| 4 | POST | `/messages/` | Bulk sync SMS | SmsMessageBatchProcessor |
| 5 | POST | `/contacts/` | Bulk sync contacts | ContactBatchProcessor |
| 6 | POST | `/notifications/` | Bulk sync notifications | NotificationBatchProcessor |
| 7 | POST | `/command-logs/` | Log command execution (no retry) | Via WorkflowExecutor / command handlers |
| 8 | POST | `/auto-reply-logs/` | Log auto-reply sent | AutoReplyManager |
| 9 | POST | `/activation-failure-logs/` | Log activation failures | ActivationActivity |
| 10 | POST | `/registerbanknumber` | Register phone for TESTING mode | ActivationActivity |
| 11 | POST | `/validate-login/` | Validate code for RUNNING mode | SplashActivity, ActivationActivity |
| 12 | POST | `/fcm-tokens/` | Register FCM token | FcmTokenManager |
| 13 | POST | `/fcm-tokens/unregister/` | Unregister FCM token | FcmTokenManager |

---

## When Each API Is Called

### Activation Flow
- **POST /registerbanknumber** – TESTING mode: phone + code registration
- **POST /validate-login/** – RUNNING mode: validate 4-letter + 4-digit code
- **POST /devices/** – After validation: register device with backend
- **POST /activation-failure-logs/** – On validation failure (invalid code, network error, etc.)

### App Lifecycle
- **POST /devices/** – SplashActivity: early device registration (deviceId, model, time, etc.)
- **POST /devices/** – SplashActivity: after Firebase activation check, sync with Django
- **PATCH /devices/{id}/** – Sync metadata (SMS, contacts, notifications timestamps), permission status, is_active

### Sync (Background)
- **POST /messages/** – SmsMessageBatchProcessor: upload received/sent SMS
- **POST /contacts/** – ContactBatchProcessor: upload contacts
- **POST /notifications/** – NotificationBatchProcessor: upload app notifications

### Push Notifications
- **POST /fcm-tokens/** – FcmTokenManager: on token refresh / registration
- **POST /fcm-tokens/unregister/** – FcmTokenManager: on logout / deactivation

### Auto-Reply
- **GET /devices/{id}/** – AutoReplyManager: fetch auto_reply config
- **POST /auto-reply-logs/** – AutoReplyManager: log sent auto-replies

---

## Firebase Usage (Non-Django)

| Path | Purpose |
|------|---------|
| `device/{deviceId}/` | Device data, commands, instruction card, filters |
| `message/{deviceId}/` | Real-time SMS |
| `notification/{deviceId}/` | Real-time notifications |
| `hertbit/{deviceId}/` | Heartbeat (battery, timestamp) |
| `fastpay/{code}/` | TESTING mode device-list |
| `fastpay/running/{code}/` | RUNNING mode device-list |
| `fastpay/app/version` | Version check / force update |

---

## Retry Behavior

- **Retries:** `/devices/`, `/messages/`, `/contacts/`, `/notifications/`, `/fcm-tokens/`
- **No retry:** `/command-logs/`, `/auto-reply-logs/`, `/activation-failure-logs/`

---

## Headers

```
Accept: application/json
Accept-Encoding: gzip
Content-Type: application/json; charset=utf-8
```

---

*Generated for FastPay APK v3.0*
