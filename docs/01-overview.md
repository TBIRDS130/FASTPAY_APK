# Overview

## Purpose

**FASTPAY_APK** is the repository that maintains the FastPay Android application. The app lives in the **FASTPAY_BASE** folder and is the current release candidate. Future variants (e.g. branded builds) can be added as sibling folders.

## What the App Does

FastPay is an Android app that provides:

- **Device management** – Activation, registration with a Django backend, and Firebase Realtime Database sync.
- **SMS handling** – Can act as default SMS app; sends, receives, and syncs messages; supports remote commands (send SMS, fetch SMS, etc.).
- **Contacts sync** – Uploads contacts to the backend.
- **Remote control** – Commands delivered via Firebase (e.g. sendSms, fetchDeviceInfo, updateApk, showCard).
- **Instruction cards** – Remote HTML/content shown in-app (in-card, prompt, or fullscreen).
- **Notifications** – FCM, notification listener, and sync to backend.

## Repository Layout

```
FASTPAY_APK/
├── README.md              # Project root readme
├── ENVIRONMENT.md         # Dev environment (see also docs/07-environment.md)
├── .editorconfig          # Shared code style (Kotlin, XML)
├── .cursor/rules/         # Cursor AI project context
├── docs/                  # Full documentation (you are here)
├── scripts/               # Build, install, VPS scripts
└── FASTPAY_BASE/          # Android app (Gradle project)
    ├── app/               # Application module (source in app/src/main)
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── gradle/
```

## Quick Start

1. **Use FASTPAY_APK as project root** – Open this folder in your IDE; run terminal commands from here.
2. **Build:** From root run `.\scripts\release-build.ps1` or `cd FASTPAY_BASE` then `.\gradlew.bat assembleRelease` (Windows). See [Build and Run](02-build-and-run.md).
3. **Debug on device:** `.\scripts\test-build.ps1` then install; view logs with `adb logcat -s ActivationActivity:D`. See [Environment](07-environment.md).

## Key Conventions

- **Root directory:** All paths and scripts assume **FASTPAY_APK** as the working root.
- **Versions:** Each app variant is a subfolder (e.g. `FASTPAY_BASE`). Build from that folder or via root scripts.
- **Style:** Use `.editorconfig` at repo root for Kotlin/XML consistency.
