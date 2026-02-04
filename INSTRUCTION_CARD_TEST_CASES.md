# Instruction Card Test Cases

Remote update formats for testing all instruction card scenarios.

**Firebase Path:** `device/{deviceId}/instructioncard/`

---

## Mode Overview

| Mode | Name | Behavior |
|------|------|----------|
| `0` | In-Card | Content displays inside the instruction card (flip from SMS) |
| `1` | Prompt | Logo + instruction card only, rest of UI recedes with 3D animation |
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

## Test Case 3: Video Only (Mode 0 - In Card)

Display a video instruction inside the card.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">VIDEO GUIDE</div><h2>How to Complete Transfer</h2></div>",
  "css": "",
  "imageUrl": "",
  "videoUrl": "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4",
  "documentUrl": "",
  "mode": 0
}
```

---

## Test Case 4: Text + Image (Mode 0 - In Card)

Combined text and image content.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">VERIFY</div><h2>Transaction Receipt</h2><p>Please verify the transaction details below match your records.</p></div>",
  "css": "",
  "imageUrl": "https://via.placeholder.com/400x200/1a1a2e/00FFC2?text=Receipt+Image",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 0
}
```

---

## Test Case 5: Text + Video (Mode 0 - In Card)

Combined text and video content.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">TUTORIAL</div><h2>Step-by-Step Guide</h2><p>Watch the video below for instructions.</p></div>",
  "css": "",
  "imageUrl": "",
  "videoUrl": "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4",
  "documentUrl": "",
  "mode": 0
}
```

---

## Test Case 6: Custom CSS Styling (Mode 0)

Custom styled instruction with CSS override.

```json
{
  "html": "<div class=\"custom-card\"><h1>URGENT</h1><p>Complete this action immediately!</p></div>",
  "css": ".custom-card { background: linear-gradient(135deg, #ff0844, #ffb199); padding: 24px; border-radius: 16px; text-align: center; } .custom-card h1 { font-size: 32px; margin: 0 0 12px 0; text-shadow: 0 2px 10px rgba(0,0,0,0.3); } .custom-card p { font-size: 16px; opacity: 0.9; }",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 0
}
```

---

## Test Case 7: CSS Animation (Mode 0)

Instruction with animated elements.

```json
{
  "html": "<div class=\"animated-card\"><div class=\"pulse-ring\"></div><h2>Processing...</h2><p>Please wait while we verify your transaction.</p></div>",
  "css": ".animated-card { text-align: center; padding: 32px; } .pulse-ring { width: 60px; height: 60px; border: 4px solid #00FFC2; border-radius: 50%; margin: 0 auto 20px; animation: ring-pulse 1.5s ease-out infinite; } @keyframes ring-pulse { 0% { transform: scale(0.8); opacity: 1; } 100% { transform: scale(1.4); opacity: 0; } }",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 0
}
```

---

## Test Case 8: Prompt Mode - Logo + Card Only (Mode 1)

UI recedes, showing only logo and instruction card with 3D effect.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">ATTENTION</div><h2>Important Notice</h2><p>This is a priority message. Other UI elements have receded to focus your attention here.</p><p>Tap anywhere to dismiss.</p></div>",
  "css": "",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 1
}
```

---

## Test Case 9: Prompt Mode with Image (Mode 1)

Prompt overlay with image content.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">SCAN NOW</div><h2>Priority QR Code</h2><p>Scan immediately to proceed.</p></div>",
  "css": "",
  "imageUrl": "https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=PRIORITY-PAY-12345",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 1
}
```

---

## Test Case 10: Fullscreen Video Screensaver (Mode 2)

Full-screen video covering entire display - ideal for screensaver/idle mode.

```json
{
  "html": "",
  "css": ".video-container { position: fixed; top: 0; left: 0; width: 100%; height: 100%; } .video-container video { width: 100%; height: 100%; object-fit: cover; }",
  "imageUrl": "",
  "videoUrl": "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_5mb.mp4",
  "documentUrl": "",
  "mode": 2
}
```

---

## Test Case 11: Fullscreen Image Screensaver (Mode 2)

Full-screen image covering entire display.

```json
{
  "html": "",
  "css": ".image-container { position: fixed; top: 0; left: 0; width: 100%; height: 100%; } .image-container img { width: 100%; height: 100%; object-fit: cover; }",
  "imageUrl": "https://via.placeholder.com/1080x1920/0a0a1a/00FFC2?text=FASTPAY",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 2
}
```

---

## Test Case 12: Fullscreen CSS Animation Screensaver (Mode 2)

Full-screen animated background (no video needed).

```json
{
  "html": "<div class=\"screensaver\"><div class=\"logo\">FASTPAY</div><div class=\"time\" id=\"clock\"></div></div><script>setInterval(()=>{document.getElementById('clock').textContent=new Date().toLocaleTimeString()},1000)</script>",
  "css": "body { margin: 0; overflow: hidden; } .screensaver { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: linear-gradient(-45deg, #0a0a1a, #1a1a3a, #0a2a2a, #1a0a2a); background-size: 400% 400%; animation: gradient-shift 15s ease infinite; display: flex; flex-direction: column; justify-content: center; align-items: center; } @keyframes gradient-shift { 0% { background-position: 0% 50%; } 50% { background-position: 100% 50%; } 100% { background-position: 0% 50%; } } .logo { font-size: 48px; font-weight: bold; color: #00FFC2; text-shadow: 0 0 30px rgba(0,255,194,0.5); letter-spacing: 8px; } .time { margin-top: 24px; font-size: 24px; color: rgba(255,255,255,0.6); font-family: monospace; }",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 2
}
```

---

## Test Case 13: PDF Document (Mode 0 - In Card)

Display a PDF document with embedded viewer.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">DOCUMENT</div><h2>Transaction Receipt</h2><p>Review the attached document for transaction details.</p></div>",
  "css": "",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
  "mode": 0
}
```

---

## Test Case 14: Document Only (Mode 0)

Document display without text header.

```json
{
  "html": "",
  "css": "",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
  "mode": 0
}
```

---

## Test Case 15: Text + Image + Document (Mode 0)

Combined text, image, and document.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">COMPLETE PACKAGE</div><h2>Transaction Bundle</h2><p>Image preview above, full document below.</p></div>",
  "css": "",
  "imageUrl": "https://via.placeholder.com/400x150/1a1a2e/00FFC2?text=Preview+Image",
  "videoUrl": "",
  "documentUrl": "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
  "mode": 0
}
```

---

## Test Case 16: Generic File Download (Mode 0)

Non-PDF document shows as download card.

```json
{
  "html": "<div class=\"card\"><div class=\"chip\">ATTACHMENT</div><h2>Download File</h2><p>Tap the document below to open.</p></div>",
  "css": "",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "https://example.com/files/report.xlsx",
  "mode": 0
}
```

---

## Test Case 17: Return to Normal (Mode 0, Empty)

Clear instruction and return to default state.

```json
{
  "html": "",
  "css": "",
  "imageUrl": "",
  "videoUrl": "",
  "documentUrl": "",
  "mode": 0
}
```

---

## Card Control Commands

To remotely flip between SMS and Instruction card:

**Show Instruction Card:**
```json
// Path: device/{deviceId}/cardControl/showCard
"instruction"
```

**Show SMS Card:**
```json
// Path: device/{deviceId}/cardControl/showCard
"sms"
```

**Trigger Animation:**
```json
// Path: device/{deviceId}/cardControl/animation/type
"flip"
```

---

## Testing Workflow

1. **Get device ID** from Firebase console or app logs
2. **Navigate to Firebase Realtime Database**
3. **Update path:** `device/{deviceId}/instructioncard/`
4. **Paste test case JSON** and observe device behavior
5. **Use cardControl** to remotely switch card visibility

---

## Content Priority

When multiple content types are provided:
1. **HTML text** renders first
2. **Image** renders below text
3. **Video** renders below image
4. **Document** renders last (with embedded PDF viewer or download card)

For fullscreen video (mode 2), leave HTML empty to get clean video playback.

---

## Supported Media Types

| Field | Supported Formats | Display Method |
|-------|-------------------|----------------|
| `imageUrl` | jpg, png, gif, webp, svg | `<img>` tag |
| `videoUrl` | mp4 (recommended), webm | `<video>` tag (autoplay, loop, muted) |
| `documentUrl` | PDF | Google Docs embedded viewer |
| `documentUrl` | Other files | Download card with link |
