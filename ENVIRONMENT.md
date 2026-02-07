# Development environment

**Root directory:** Always use **FASTPAY_APK** as the project root (terminal `cd`, IDE “Open Folder”).

For full setup, tools, build requirements, CI, and Cursor/agent testing, see:

- **[docs/07-environment.md](docs/07-environment.md)** – Development environment (complete)

Quick reference:

- **Build debug (from root):** `.\scripts\test-debug.ps1` or `.\scripts\test-debug.ps1 FASTPAY_BASE`
- **Build release (from root):** `.\scripts\build-version.ps1`
- **From FASTPAY_BASE:** `.\gradlew.bat assembleDebug`, `.\gradlew.bat installDebug`, `.\gradlew.bat assembleRelease`
- **Activation logs:** `adb logcat -s ActivationActivity:D`

All documentation: **[docs/README.md](docs/README.md)**
