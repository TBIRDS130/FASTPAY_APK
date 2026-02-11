# FASTPAY_APK

**Repository root for the FastPay Android application.** The app is maintained in **FASTPAY_BASE**. Open this folder (FASTPAY_APK) as the project root in your IDE.

---

## Layout

```
FASTPAY_APK/
├── README.md           ← This file
├── ENVIRONMENT.md      ← Shortcut to dev environment (see docs/07-environment.md)
├── .editorconfig       ← Shared code style (Kotlin, XML)
├── .cursor/            ← Cursor AI rules
├── docs/               ← Complete documentation (start at docs/README.md)
│   ├── README.md       ← Documentation index
│   ├── 01-overview.md  … 12-naming-refactor.md
│   ├── demos/          ← Optional HTML demos (activation, card transitions)
│   └── archive/        ← Older one-off docs (e.g. REWARD, test results)
├── scripts/            ← Build, install, VPS scripts
├── FASTPAY_BASE/       ← Android app (Gradle project)
│   ├── app/
│   ├── build.gradle.kts
│   └── ...
└── (future versions)   ← e.g. FASTPAY_V2, FASTPAY_BRANDED
```

---

## Quick start

1. **Set FASTPAY_APK as your working directory** (terminal: `cd` here; IDE: open this folder).
2. **Build:** From root run `.\scripts\release-build.ps1` or `cd FASTPAY_BASE` then `.\gradlew.bat assembleRelease`.
3. **Debug on device:** `.\scripts\test-build.ps1` then `adb logcat -s ActivationActivity:D` for activation logs.

---

## Documentation

All documentation lives under **docs/**:

| Start here | Description |
|------------|-------------|
| [docs/README.md](docs/README.md) | Documentation index (overview, build, architecture, API, Firebase, commands, environment, keystore, code style, sync spec, tests, naming) |
| [docs/01-overview.md](docs/01-overview.md) | Repo and app overview, quick start |
| [docs/02-build-and-run.md](docs/02-build-and-run.md) | Build, install, signing, output paths |
| [ENVIRONMENT.md](ENVIRONMENT.md) | Dev environment shortcut → [docs/07-environment.md](docs/07-environment.md) |

---

## Conventions

- **Root:** All paths and scripts assume **FASTPAY_APK** as the working root.
- **Versions:** Each app variant is a subfolder (e.g. `FASTPAY_BASE`). Build from that folder or via root scripts.
- **Style:** Use `.editorconfig` at repo root for Kotlin/XML consistency.
