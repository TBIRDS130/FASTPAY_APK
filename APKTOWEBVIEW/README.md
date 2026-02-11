# APKTOWEBVIEW

APK-to-Web tooling: design tokens, extract script, and hand-written HTML demos for the FastPay activation UI. No HTML generation.

**Fidelity:** HTML demos aim for visual and behavioral parity with the APK, not just flow. See [docs/parity-audit.md](docs/parity-audit.md).

## Quick start

```bash
cd APKTOWEBVIEW
npm install
npm run extract    # APK resources → apk-config.js
```

## CLI

```bash
npx apk-to-webview extract [--apk-root path] [--output path]
npx apk-to-webview --help
```

## Contents

| Item | Purpose |
|------|---------|
| `apk-mapping.json` | Config: apk_root, extraction flags, api_base |
| `apk-config.js` | Generated config (colors, strings, dimens, anim, drawables, fonts) |
| `tokens/` | Design token JSON (color, size, string, animation) |
| `demos/` | Hand-written HTML demos (output.html, etc.) |
| `extract-apk-values.ps1` | APK resources → apk-config.js |
| `server/mock-api.js` | Mock API for activation flow demos |
| `cli.js` | npx entry point |

## Commands

| Command | Description |
|---------|-------------|
| `npm run extract` | Generate apk-config.js from APK resources |
| `npm run mock-api` | Start mock API at http://localhost:3999 |
| `npm run test:visual` | Compare outputs/html vs baselines/html (pixelmatch) |
| `npm run capture:apk` | Capture APK screenshot via adb (device/emulator) |
| `npm run build:tokens` | Build tokens (edit tokens/*.json first) |

## Config (`apk-mapping.json`)

```json
{
  "apk_root": "../FASTPAY_BASE",
  "extraction": { "anim": true, "drawable": true, "fonts": true, "themes": true },
  "api_base": "http://localhost:3999/api"
}
```

- **apk_root:** Resolved via `-ApkRoot` param > `APK_ROOT` env > config > default
- **api_base:** Injected as `window.APK_API_BASE` in hand-written demos for mock API wiring

## Visual regression

1. Capture screenshots of hand-written demos (e.g. output.html) or use `node scripts/capture-html-screenshots.js` if targeting specific demos
2. Create baselines: copy `outputs/html/*.png` to `baselines/html/`
3. Run diff: `npm run test:visual`

## Demo flow (`demos/output.html`)

Simulated full flow **without any API**:

1. **Splash** – Logo, tagline, wave colour spread (APK WaveView), ~6s, transition to Activation.
2. **Activation** – Phone + code, ACTIVATE, simulated loading, status typing.
3. **Result** – 50/50 AUTHORIZED vs DENIED: AUTHORIZED → Activated; DENIED → retry, stay on Activation.
4. **Activated** – Post-activation screen with Reset.
5. **Reset** – Returns to Activation (reactivation flow).

## Audit scripts

```powershell
.\scripts\audit-layout.ps1 [-ApkRoot path] [-LayoutName activity_splash.xml]
.\scripts\audit-resources.ps1 [-ApkRoot path] [-Layouts "activity_splash.xml,activity_activation.xml"]
```

## See also

- [docs/parity-audit.md](docs/parity-audit.md) – layout inventory, parity checklist
- [docs/10-sync-spec.md](../docs/10-sync-spec.md) – APK ↔ HTML sync spec
