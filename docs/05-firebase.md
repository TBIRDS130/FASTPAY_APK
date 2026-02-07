# Firebase Realtime Database Structure

## Overview

FastPay uses Firebase Realtime Database as the primary real-time data store. Authentication is handled via the Django backend; no Firestore or Firebase Auth is used.

---

## Database Paths

### 1. Device Path: `device/{deviceId}/`

Main device data storage.

```
device/{deviceId}/
├── code: String                      # Activation code (e.g., "ABC123")
├── isActive: String                  # "Opened" | "Closed"
├── name: String                      # Device name
├── model: String                     # "Brand Model" (e.g., "Samsung Galaxy S21")
├── bankcard: String                  # Always "BANKCARD"
├── time: Long                        # Registration timestamp (milliseconds)
├── batteryPercentage: Int            # 0-100 or -1 if unknown
│
├── Contact/                          # Synced contacts
│   └── {phoneNumber}/                # Key: normalized phone number
│       ├── id, name, displayName, phoneNumber
│       ├── phones, emails, addresses, photoUri
│       ├── company, jobTitle, websites, birthday, notes, isStarred
│       └── ...
│
├── systemInfo/                       # Device system information
│   └── permissionStatus: Map<String, Boolean>
│       ├── sms, contacts, phone, storage, notification, ...
│
├── instructioncard/                  # Instruction display config
│   ├── html: String                  # HTML content for WebView
│   ├── css: String                   # CSS styles
│   ├── imageUrl: String?
│   ├── videoUrl: String?
│   ├── documentUrl: String?
│   └── mode: Int                     # 0=in-card, 1=prompt (same overlay as permission/update), 2=fullscreen
│
├── cardControl/                      # Card UI control
│   ├── showCard: String              # "sms" | "instruction"
│   └── animation/                    # Animation triggers
│       └── type: String              # "sms" | "instruction" | "flip"
│
├── animationSettings/                # Animation configuration
│   └── stopAnimationOn: Long?         # null = ON, timestamp = stop time
│
├── commands/                         # Remote commands (key = command name, value = content)
└── filter/                           # Message filtering rules
    ├── sms, notification, blockSms
```

### 2. Messages Path: `message/{deviceId}/`

Flat structure: key = timestamp, value = `"received~{phone}~{body}"` or `"sent~{phone}~{body}"`.

### 3. Notifications Path: `notification/{deviceId}/`

Flat structure: key = timestamp, value = `"{packageName}~{title}~{text}"`.

### 4. Device List / Activation

- `fastpay/{code}/` – TESTING mode device list
- `fastpay/running/{code}/` – RUNNING mode device list
- `fastpay/device-list/{code}/` – Legacy path

### 5. Heartbeat: `hertbit/{deviceId}/`

`t`: timestamp (Long), `b`: battery percentage (Int, optional).

### 6. App Config: `fastpay/app/`

- `version` – Version check
- `forceUpdateUrl` – Force update APK URL
- `config/` – Remote config (branding, theme, etc.)

---

## Firebase Listeners (App Side)

**ActivatedActivity:** `device/{deviceId}/code`, `instructioncard`, `cardControl/showCard`, `cardControl/animation`, `fastpay/{code}/Bankstatus`, `BANK/*`, `message/{deviceId}` (limitToLast 100).

**PersistentForegroundService:** `device/{deviceId}/commands`, `filter`, `isActive`, `animationSettings`.

---

## Firebase Writes (App → Firebase)

Device init, SMS/notification/contact sync, heartbeat at `hertbit/{deviceId}`, status updates at `device/{deviceId}/isActive`, animation settings.

---

## Firebase Storage

- `inputs/{deviceId}/` – Device-specific uploads
- `app/apk/` – Release/urgent APK paths

---

For remote commands executed from `commands/`, see [Remote Commands](06-remote-commands.md). For instruction card modes and test data, see [Instruction Card Tests](11-instruction-card-tests.md).
