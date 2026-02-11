# Build and Run

## Output Locations

| Build Type | Path | Filename |
|------------|------|----------|
| **Debug** | `FASTPAY_BASE/app/build/outputs/apk/debug/` | `dfastpay-411-DDMM-HHMM.apk` |
| **Release** | `FASTPAY_BASE/app/build/outputs/apk/release/` | `rfastpay-411-DDMM-HHMM.apk` |
| **Both (copy)** | `APKFILE/` (repo root) | Same filenames; old APKs kept |

APKs are copied to `APKFILE/` when you run `test-build` or `release-build` (runs `copyDebugApk` / `copyReleaseApk`). Timestamp is IST (UTC+5:30).

## Build Commands

### From repo root

```powershell
# TEST BUILD (debug)
.\scripts\test-build.ps1
# RELEASE BUILD (release, clean + no cache)
.\scripts\release-build.ps1
```

**Ubuntu/Linux/macOS (from repo root):**
```bash
# TEST BUILD (debug)
bash scripts/test-build.sh
# RELEASE BUILD (release, clean + no cache)
bash scripts/release-build.sh
```
Use `-InstallOnly` to skip build and only install. Optional: pass `FASTPAY_BASE` to target that version folder.

### Design tokens and extract (optional – HTML demo in APKTOWEBVIEW)

**Use:** Optional. All APK-to-web tooling lives in `APKTOWEBVIEW/`. Run from repo root or from `APKTOWEBVIEW/`:

```bash
# From repo root
npm run build:tokens
npm run extract

# Or from APKTOWEBVIEW/
cd APKTOWEBVIEW
npm run build:tokens    # tokens/**/*.json → apk-config.js
npm run extract         # APK resources → apk-config.js
```

- **Output:** `APKTOWEBVIEW/apk-config.js` (used by `APKTOWEBVIEW/demos/output.html`).
- See `APKTOWEBVIEW/README.md` and `docs/10-sync-spec.md`.

### From FASTPAY_BASE

```powershell
cd FASTPAY_BASE

# Debug (for testing)
.\gradlew.bat assembleDebug

# Install debug on device
.\gradlew.bat installDebug

# Release (signed)
.\gradlew.bat assembleRelease

# Release + copy to APKFILE/
.\gradlew.bat copyReleaseApk
```

### In Android Studio

- **Build → Build Bundle(s) / APK(s) → Build APK(s)** → Debug APK
- **Build → Generate Signed Bundle / APK** → Release APK (uses keystore)

## Version and Naming

APK filenames: `{d|r}fastpay-{versionCode}-{DDMM-HHMM}.apk` (e.g. `dfastpay-411-0802-1430.apk`, `rfastpay-411-0802-1430.apk`). Timestamp uses IST (Asia/Kolkata).

- **Version name:** `FASTPAY_BASE/app/build.gradle.kts` → `defaultConfig.versionName` (e.g. `4.1.1`)
- **Version code:** `defaultConfig.versionCode` (e.g. `411`)

## Release Signing

Release builds use `keystore.properties` in `FASTPAY_BASE/` (see [Keystore](08-keystore.md)):

```properties
KEYSTORE_FILE=path/to/release.keystore
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=your_alias
KEY_PASSWORD=your_key_password
```

If the file or keystore is missing, release may fall back to debug signing. Use `keystore.properties.template` as reference.

## Build Environment

- **JDK:** 17 (Temurin recommended)
- **Gradle:** 8.13 (wrapper in FASTPAY_BASE)
- **Android:** compileSdk/targetSdk 36, minSdk 27, buildTools 36.0.0

Optional build-time config: `FASTPAY_BASE/.env` only (read by Gradle from project root). Copy from `FASTPAY_BASE/.env.example` and set `DJANGO_API_BASE_URL` and `FIREBASE_STORAGE_BUCKET` if needed. See [Environment](07-environment.md).

## Testing

**Unit tests** (no device):

- From repo root: `.\scripts\test-unit.ps1` or `.\scripts\test-unit.ps1 FASTPAY_BASE` (Windows); `bash scripts/test-unit.sh` (Linux/macOS).
- From FASTPAY_BASE: `.\gradlew.bat testDebugUnitTest` (Windows) or `./gradlew testDebugUnitTest` (Linux/macOS).
- Key test classes: `ActivationCodeTest`, `ActivatedViewModelTest`, `PersistentForegroundServiceCommandTest`, `DjangoApiHelperTest` (see `app/src/test/`).

**Instrumented tests** (device or emulator):

- From FASTPAY_BASE: `.\gradlew.bat connectedDebugAndroidTest` (Windows) or `./gradlew connectedDebugAndroidTest` (Linux/macOS). Use `--info` to see test stdout.
- Workflow tests: `ActivationToActivatedFlowTest` (Splash → Activation/Activated), `PermissionFlowTest` (context/permission checks); see `app/src/androidTest/java/com/example/fast/integration/`.

**API for tests:** Debug builds use the staging API by default (`BuildConfig.DJANGO_API_BASE_URL` = `https://api-staging.fastpaygaming.com/api` unless overridden by `FASTPAY_BASE/.env`). Unit tests typically mock the API; instrumented tests may hit staging.
