# Build and Run

## Output Locations

| Build Type | Path | Filename |
|------------|------|----------|
| **Debug** | `FASTPAY_BASE/app/build/outputs/apk/debug/` | `fastpay-3.0-debug.apk` |
| **Release** | `FASTPAY_BASE/app/build/outputs/apk/release/` | `fastpay-3.0-release.apk` |
| **Release (convenience)** | `releases/` (repo root) | `fastpay-3.0-release.apk` |

The release APK is copied to `releases/` when you run the `copyReleaseApk` task or `build-version.ps1`.

## Build Commands

### From repo root

```powershell
# Build release and copy to releases/
.\scripts\build-version.ps1

# Build and install debug on connected device
.\scripts\test-debug.ps1
# or: .\scripts\test-debug.ps1 FASTPAY_BASE

# Clean + release (when builds fail or files are locked)
.\scripts\clean-build.ps1
```

### From FASTPAY_BASE

```powershell
cd FASTPAY_BASE

# Debug (for testing)
.\gradlew.bat assembleDebug

# Install debug on device
.\gradlew.bat installDebug

# Release (signed)
.\gradlew.bat assembleRelease

# Release + copy to repo releases/
.\gradlew.bat copyReleaseApk
```

### build-apk.ps1 (inside FASTPAY_BASE)

Smart builder with Java auto-detect, lock recovery, and clean:

```powershell
cd FASTPAY_BASE
.\build-apk.ps1 -BuildType debug    # Debug APK
.\build-apk.ps1 -Release            # Release (with clean)
.\build-apk.ps1 -Clean              # Clean + build
.\build-apk.ps1 -Quick              # Quick compile check
```

Use `-Clean` or `-Release` when Gradle locks or build fails.

### In Android Studio

- **Build → Build Bundle(s) / APK(s) → Build APK(s)** → Debug APK
- **Build → Generate Signed Bundle / APK** → Release APK (uses keystore)

## Version and Naming

APK filenames: `fastpay-{versionName}-{debug|release}.apk`

- **Version name:** `FASTPAY_BASE/app/build.gradle.kts` → `defaultConfig.versionName` (e.g. `3.0`)
- **Version code:** `defaultConfig.versionCode` (e.g. `30`)

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

Optional: `FASTPAY_BASE/.env` (or repo root `.env`) for build-time config (e.g. `DJANGO_API_BASE_URL`, `FIREBASE_STORAGE_BUCKET`). See [Environment](07-environment.md).
