# APK Build Guide

Where to find built APKs and how to build them.

---

## Output Locations

| Build Type | Path | Filename |
|------------|------|----------|
| **Debug** | `FASTPAY_BASE/app/build/outputs/apk/debug/` | `fastpay-3.0-debug.apk` |
| **Release** | `FASTPAY_BASE/app/build/outputs/apk/release/` | `fastpay-3.0-release.apk` |
| **Release (convenience)** | `releases/` (repo root) | `fastpay-3.0-release.apk` |

The release APK is automatically copied to `releases/` when you run `copyReleaseApk` (or `build-version.ps1`).

---

## Build Commands

### From repo root

```powershell
# Build release and copy to releases/
.\scripts\build-version.ps1
```

### From FASTPAY_BASE

```powershell
cd FASTPAY_BASE

# Debug (for testing)
.\gradlew.bat assembleDebug

# Release (signed, minified)
.\gradlew.bat assembleRelease

# Release + copy to repo releases/
.\gradlew.bat copyReleaseApk
```

### In Android Studio

- **Build → Build Bundle(s) / APK(s) → Build APK(s)** → Debug APK
- **Build → Generate Signed Bundle / APK** → Release APK (uses keystore)

---

## Release Signing

Release builds require `keystore.properties` in `FASTPAY_BASE/`:

```properties
KEYSTORE_FILE=path/to/release.keystore
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=your_alias
KEY_PASSWORD=your_key_password
```

If missing, release builds will fail. Copy from `keystore.properties.template` if available.

---

## Version & Naming

APK filenames follow: `fastpay-{versionName}-{debug|release}.apk`

- Version name: `build.gradle.kts` → `defaultConfig.versionName` (currently `3.0`)
- Version code: `defaultConfig.versionCode` (currently `30`)
