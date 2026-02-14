# Instruction Card Test Cases

Remote update formats for testing all instruction card scenarios.

**Firebase Path:** `device/{deviceId}/instructioncard/`

---

## Mode Overview

| Mode | Name | Behavior |
|------|------|----------|
| `0` | In-Card | Content displays inside the instruction card (flip from SMS) |
| `1` | Prompt | Same overlay card and animation as permission/update: FlipIn from status card, recede (header, phone, status, SMS, test buttons), WebView content, Dismiss button; death = ShrinkInto(status card). |
| `2` | Fullscreen | Covers entire screen (screensaver/video mode) |

---

## Test Case 1: Text Only (Mode 0 - In Card)

Basic text instruction displayed in the card.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">ACTION REQUIRED</div><h2>Transfer Instructions</h2><p>Please transfer <strong>$500</strong> to the following account:</p><ul><li>Bank: Example Bank</li><li>Account: 1234567890</li><li>Reference: TX-001</li></ul><div class=\"status\"><span class=\"dot\"></span>Awaiting confirmation</div></div>",
  "css": "",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 0
}
```

---

## Test Case 2: Image Only (Mode 0 - In Card)

Display an image instruction (QR code, payment proof, etc.).

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">SCAN QR</div><h2>Payment QR Code</h2><p>Scan this code to complete payment</p></div>",
  "css": "",
  "imageUrl": "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=https://example.com/pay/12345",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 0
}
```

---

## Test Cases 3–17 (summary)

- **3:** Video only (mode 0)
- **4:** Text + image (mode 0)
- **5:** Text + video (mode 0)
- **6:** Custom CSS styling (mode 0)
- **7:** CSS animation (mode 0)
- **8:** Prompt mode – logo + card only (mode 1)
- **9:** Prompt mode with image (mode 1)
- **10:** Fullscreen video screensaver (mode 2)
- **11:** Fullscreen image screensaver (mode 2)
- **12:** Fullscreen CSS animation screensaver (mode 2)
- **13:** PDF document (mode 0)
- **14:** Document only (mode 0)
- **15:** Text + image + document (mode 0)
- **16:** Generic file download (mode 0)
- **17:** Return to normal (empty, mode 0)

Full JSON for each case is in the repo history or can be reconstructed from the pattern: `html`, `css`, `imageUrl`, `videoUrl`, `documentUrl`, `mode`.

---

## Card Control Commands

**Show Instruction Card:** Write `"instruction"` to `device/{deviceId}/cardControl/showCard`

**Show SMS Card:** Write `"sms"` to `device/{deviceId}/cardControl/showCard`

**Trigger Animation:** Write `"flip"` to `device/{deviceId}/cardControl/animation/type`

---

## Testing Workflow

1. Get device ID from Firebase or app logs.
2. In Firebase Realtime Database, go to `device/{deviceId}/instructioncard/`.
3. Paste test case JSON and observe device behavior.
4. Use cardControl paths to switch card visibility.

---

## Content Priority

1. HTML text → 2. Image → 3. Video → 4. Document (PDF viewer or download card). For fullscreen video (mode 2), leave HTML empty.

---

## Supported Media Types

| Field | Formats | Display |
|-------|---------|---------|
| imageUrl | jpg, png, gif, webp, svg | `<img>` |
| videoUrl | mp4, webm | `<video>` autoplay, loop, muted |
| documentUrl | PDF | Embedded viewer |
| documentUrl | Other | Download card |
