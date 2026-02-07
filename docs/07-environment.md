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
- **Gradle:** Use `gradlew` inside each version folder (e.g. `FASTPAY_BASE/gradlew`). Root script `scripts/build-version.ps1` can drive builds from repo root.
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
| **Windows** | `.\scripts\test-debug.ps1` or `.\scripts\test-debug.ps1 FASTPAY_BASE` | `.\scripts\build-version.ps1` |
| **Linux / macOS** | `cd FASTPAY_BASE && ./gradlew assembleDebug installDebug` | `cd FASTPAY_BASE && ./gradlew assembleRelease` |

**Release signing:** Create `FASTPAY_BASE/keystore.properties` (see [Keystore](08-keystore.md)). Optional: `FASTPAY_BASE/.env` or repo root `.env` for build-time config (e.g. API URLs).

**Low-memory / VPS (e.g. Hostinger):**
```bash
bash scripts/build-hostinger-low-memory.sh
```
Uses `-Xmx512m`, `--no-daemon`, `--max-workers=1` for release build from FASTPAY_BASE.

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

## Testing APK Built on VPS

APKs run on Android devices or emulators. After building on VPS:

**A. Download and install locally**
```bash
bash scripts/download-apk-from-vps.sh user@your-vps-ip
adb install -r fastpay-3.0-release.apk
```

**B. Install via wireless ADB from VPS**
1. On device: **Settings > Developer options > Wireless debugging** — ON, note IP:PORT.
2. On VPS (from FASTPAY_APK root):
```bash
export ANDROID_HOME=/opt/android-sdk
bash scripts/vps-install-via-adb.sh 192.168.1.100:5555
```

---

## Cursor / Agent Testing

| Step | Command / path |
|------|-----------------|
| **Project root** | `FASTPAY_APK` (repo root) |
| **Android project** | `FASTPAY_BASE` |
| **Build debug** | `cd FASTPAY_BASE` then `.\gradlew.bat assembleDebug` |
| **Install on device** | `.\gradlew.bat installDebug` (from FASTPAY_BASE) |
| **One script (build + install)** | From repo root: `.\scripts\test-debug.ps1` or `.\scripts\test-debug.ps1 FASTPAY_BASE` |
| **View activation logs** | `adb logcat -s ActivationActivity:D` |
| **Clean (if needed)** | `.\gradlew.bat --stop` then `.\gradlew.bat clean assembleDebug` (from FASTPAY_BASE; clean can fail if files are locked) |

**Agent:** For “test on device”, run `.\scripts\test-debug.ps1` from repo root, then suggest `adb logcat -s ActivationActivity:D` to verify. If Gradle clean fails due to file locks, suggest Android Studio or `gradlew --stop` and retry.
