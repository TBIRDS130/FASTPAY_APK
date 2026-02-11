# Development Environment

**Root directory:** Always use **FASTPAY_APK** as the project root (terminal `cd`, IDE “Open Folder”).

---

## What’s in Place

| Tool / file | Purpose |
|-------------|---------|
| **README.md** | Project root and layout (FASTPAY_APK, version folders). |
| **.editorconfig** | Shared style: Kotlin/XML 4 spaces, UTF-8, trim trailing whitespace, final newline. Works in VS Code, Android Studio, Cursor. |
| **.cursor/rules/** | Cursor AI rules so the agent knows FASTPAY_APK is root and where versions live. |
| **scripts/** | Root-level scripts (build, install, VPS, etc.). |
| **.pre-commit-config.yaml** | Optional: run checks before each commit. |

---

## Recommended Tools

- **IDE:** Android Studio or Cursor/VS Code with Android extensions. Open **FASTPAY_APK** (repo root), not only `FASTPAY_BASE`.
- **EditorConfig:** Enable in your editor for consistent indentation and line endings.
- **Gradle:** Use `gradlew` inside each version folder (e.g. `FASTPAY_BASE/gradlew`). Root script `scripts/release-build.ps1` can drive builds from repo root.
- **CI:** FASTPAY_BASE has `.github/workflows/android-ci.yml`. Reuse the same pattern for new version folders.

---

## APK Build Environment

| Requirement | Version / value |
|-------------|-----------------|
| **JDK** | **17** (Temurin recommended) |
| **Gradle** | **8.13** (wrapper in FASTPAY_BASE) |
| **Android Gradle Plugin** | **8.13.1** |
| **Kotlin** | **2.2.10** |
| **compileSdk / targetSdk** | **36** |
| **buildToolsVersion** | **36.0.0** |
| **minSdk** | **27** |

**Android SDK:** Install via Android Studio SDK Manager or [command-line tools](https://developer.android.com/studio#command-tools). Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) if not using Android Studio.

**Build from repo root:**

| Platform | Debug (build + install) | Release |
|----------|--------------------------|--------|
| **Windows** | TEST: `.\scripts\test-build.ps1` (incremental) | RELEASE: `.\scripts\release-build.ps1` (clean, no cache; same install flow) |
| **Ubuntu/Linux/macOS** | TEST: `bash scripts/test-build.sh` | RELEASE: `bash scripts/release-build.sh` (same flow as Windows) |

**Release signing:** Create `FASTPAY_BASE/keystore.properties` (see [Keystore](08-keystore.md)). Optional build-time config: `FASTPAY_BASE/.env` only; copy from `FASTPAY_BASE/.env.example`. See [Build and Run](02-build-and-run.md).

**Staging API:** Debug builds default to `https://api-staging.fastpaygaming.com/api` for `DJANGO_API_BASE_URL`; override in `FASTPAY_BASE/.env` if needed. Unit and instrumented tests use this by default.

---

## Optional: Pre-commit

1. Install [pre-commit](https://pre-commit.com/) (e.g. `pip install pre-commit`).
2. From **FASTPAY_APK** root: `pre-commit install`.
3. The repo’s `.pre-commit-config.yaml` runs trailing-whitespace and EOF fixers; optionally ktlint/detekt if added to the project.

---

## Adding a New Version

1. Copy `FASTPAY_BASE` to a new folder (e.g. `FASTPAY_V2`, `FASTPAY_BRANDED`).
2. Change `applicationId`, `versionCode`, `versionName`, and branding (strings, icons, colors).
3. Use the same `.editorconfig` and pre-commit; they apply repo-wide.
4. Add a workflow under `.github/workflows/` for the new version if you want CI.

---

## Cursor / Agent Testing

| Step | Command / path |
|------|-----------------|
| **Project root** | `FASTPAY_APK` (repo root) |
| **Android project** | `FASTPAY_BASE` |
| **Build debug** | `cd FASTPAY_BASE` then `.\gradlew.bat assembleDebug` |
| **Install on device** | `.\gradlew.bat installDebug` (from FASTPAY_BASE) |
| **One script (build + install)** | From repo root: `.\scripts\test-build.ps1` or `.\scripts\test-build.ps1 FASTPAY_BASE`. Incremental build; retries once on failure; if no device, prints `adb install -r "..."`; use `-InstallOnly` to install only. |
| **View activation logs** | `adb logcat -s ActivationActivity:D` |
| **View debug logs (full)** | `adb logcat -s FastPay:D ActivationActivity:D ActivatedActivity:D PersistentForegroundService:D SplashActivity:D DebugLogger:D` |
| **Copy logs from device** | Long-press logo on Activated screen → copies DebugLogger buffer to clipboard |
| **Clean (if needed)** | Build scripts stop the Gradle daemon before each build to avoid R.jar locks. If locks persist: close Android Studio (or the project), then `.\gradlew.bat --stop` and run the script again. Manual clean: from FASTPAY_BASE run `.\gradlew.bat --stop` then `.\gradlew.bat clean assembleDebug`. |

**Agent:** For “test on device”, run `.\scripts\test-build.ps1` from repo root, then suggest `adb logcat -s ActivationActivity:D` to verify. The script stops the Gradle daemon before each build to avoid R.jar locks and retries once on failure; if locks still occur, suggest closing Android Studio and retrying. When no device is connected, it outputs the install command for later.

**PowerShell invocation:** Use `Set-Location path; .\scripts\test-build.ps1` when chaining (do not use `&&`—unsupported in PowerShell 5.x).
