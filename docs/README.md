# FastPay Android Application – Documentation

This folder contains the complete documentation for the **FASTPAY_APK** repository and the **FastPay** Android application (maintained in `FASTPAY_BASE`).

---

## Documentation Index

| # | Document | Description |
|---|----------|-------------|
| 1 | [Overview](01-overview.md) | Repo structure, app purpose, quick start |
| 2 | [Build and Run](02-build-and-run.md) | Building, installing, signing, output locations |
| 3 | [Architecture](03-architecture.md) | App architecture, modules, manifest, source layout |
| 3a | [UIManager reference](ui-manager-and-elements.md) | Splash/Activation/Activated UIManager API, rule, elements, call flows; [audit](ui-manager-audit-route-through.md) |
| 4 | [Django API](04-django-api.md) | Backend API usage, endpoints, when each is called |
| 5 | [Firebase](05-firebase.md) | Firebase Realtime Database structure and paths |
| 6 | [Remote Commands](06-remote-commands.md) | Firebase commands reference (sendSms, fetchDeviceInfo, etc.) |
| 7 | [Environment](07-environment.md) | Dev environment, tools, CI, Cursor/agent testing |
| 8 | [Keystore](08-keystore.md) | Release signing and keystore setup |
| 9 | [Code Style](09-code-style.md) | Kotlin and project coding standards |
| 10 | [APK ↔ HTML Sync](10-sync-spec.md) | Keeping activation UI HTML in sync with APK resources |
| 11 | [Instruction Card Tests](11-instruction-card-tests.md) | Remote instruction card test cases |
| 12 | [Naming Refactor](12-naming-refactor.md) | Layout ID naming reference |
| 13 | [API Calls by UI](13-api-calls-by-ui.md) | Django API call sites mapped to screens and background flow |

---

## Doc ↔ Source Sync

- **Rule:** When editing code that affects architecture, APIs, or paths, update the corresponding docs (see `.cursor/rules/docs-source-sync.mdc`).
- **Mismatch report:** [DOC_SOURCE_MISMATCH_REPORT.md](DOC_SOURCE_MISMATCH_REPORT.md) – last check vs source; update when fixing drift.

---

## Other References

- **APKTOWEBVIEW:** `APKTOWEBVIEW/` – Design tokens, extract script, HTML demos (activation UI, card transitions). Run `npm run build:tokens` or `npm run extract` from there.
- **Archive:** [archive/](archive/) – Older one-off docs (e.g. REWARD, Cursor agent test results).
- **Scripts:** Repo root `scripts/` – build, install scripts (see [Environment](07-environment.md)).

---

## Repo Layout (Quick Reference)

```
FASTPAY_APK/                 ← Repo root (open this in IDE)
├── docs/                    ← This documentation
├── scripts/                 ← Build and install scripts
├── APKTOWEBVIEW/            ← Tokens, extract, HTML demos (npm run build:tokens, extract)
├── APKFILE/                 ← APK output (debug/release copies; gitignored)
├── FASTPAY_BASE/            ← Android app (Gradle project)
│   ├── app/                 ← Application module
│   ├── build.gradle.kts
│   └── ...
├── README.md                ← Project root readme
└── ENVIRONMENT.md           ← Shortcut to environment setup (see docs/07)
```
