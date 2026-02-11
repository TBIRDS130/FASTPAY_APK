# Development environment

**Root directory:** Always use **FASTPAY_APK** as the project root (terminal `cd`, IDE “Open Folder”).

For full setup, tools, build requirements, CI, and Cursor/agent testing, see:

- **[docs/07-environment.md](docs/07-environment.md)** – Development environment (complete)

Quick reference:

- **Build debug (from root):** `.\scripts\test-build.ps1` or `.\scripts\test-build.ps1 FASTPAY_BASE`. PowerShell: use `;` not `&&` when chaining (e.g. `Set-Location path; .\scripts\test-build.ps1`).
- **Build release (from root):** `.\scripts\release-build.ps1`
- **From FASTPAY_BASE:** `.\gradlew.bat assembleDebug`, `.\gradlew.bat installDebug`, `.\gradlew.bat assembleRelease`
- **Activation logs:** `adb logcat -s ActivationActivity:D`
- **Debug logs (full):** `adb logcat -s FastPay:D ActivationActivity:D ActivatedActivity:D PersistentForegroundService:D SplashActivity:D DebugLogger:D`
- **Copy logs from device:** Long-press logo on Activated screen

All documentation: **[docs/README.md](docs/README.md)**
