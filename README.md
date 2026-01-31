# FASTPAY_APK

**Project root for all FastPay Android app versions.**

- **Root directory:** `FASTPAY_APK` (this repo). All paths and scripts assume this as the working root.
- **Version folders:** Each variant lives as a subfolder (e.g. `FASTPAY_BASE`). Open the repo root in your IDE.

## Layout

```
FASTPAY_APK/
├── README.md           # This file – project root
├── ENVIRONMENT.md      # Dev environment, tools, time-saving setup
├── .editorconfig       # Shared code style (Kotlin, XML, etc.)
├── .cursor/            # Cursor AI rules and context
├── scripts/            # Root-level helpers (build any version, etc.)
├── FASTPAY_BASE/       # Base FastPay app (current release candidate)
│   ├── app/
│   ├── build.gradle.kts
│   └── ...
└── (future versions)   # e.g. FASTPAY_V2, FASTPAY_BRANDED, etc.
```

## Quick start

1. **Set FASTPAY_APK as your working directory** (terminal: `cd` here; IDE: open this folder as project root).
2. Build a version: from root run `.\scripts\build-version.ps1` or `cd FASTPAY_BASE && .\gradlew assembleRelease`.
3. See **ENVIRONMENT.md** for EditorConfig, optional pre-commit, and recommended tools.
