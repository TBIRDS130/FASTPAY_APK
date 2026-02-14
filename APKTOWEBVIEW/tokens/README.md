# Design tokens (optional)

**Optional feature:** The APK uses `res/values` and layout as before. This folder is for an optional token-based flow that only affects the **HTML activation demo**.

Run from APKTOWEBVIEW or repo root:

```bash
npm run build:tokens
```

Style Dictionary generates `build/tokens/android/*.xml` (for reference) and **`apk-config.js` in APKTOWEBVIEW**. The HTML demo (`demos/output.html`) loads it. The APK is not modified.

## Structure

| File | Contents |
|------|----------|
| `color/theme.json` | Theme colors (theme_primary, theme_border, activation_background, etc.) |
| `size/activation.json` | Activation dimensions (input_height, button_height, etc.) |
| `string/activation.json` | Activation copy (app_name_title, steps, buttons, hints) |
| `animation/activation.json` | Animation timings (button press, shake, hint delay, etc.) |

## Output

- **Web:** `apk-config.js` in APKTOWEBVIEW (used by demos/).
- **Android:** `build/tokens/android/*.xml` for reference only; not copied into the app.
