# FASTPAY_APK – development environment

Basic, helpful, time-saving setup for multiple FastPay versions. **Root directory:** always use `FASTPAY_APK` as the project root (terminal `cd`, IDE “Open Folder”).

---

## What’s in place

| Tool / file        | Purpose |
|--------------------|--------|
| **README.md**      | Project root and layout (FASTPAY_APK, version folders). |
| **.editorconfig**   | Shared style: Kotlin/XML 4 spaces, UTF-8, trim trailing whitespace, final newline. Works in VS Code, Android Studio, Cursor. |
| **.cursor/rules/** | Cursor AI rules so the agent knows FASTPAY_APK is root and where versions live. |
| **scripts/**       | Root-level scripts (e.g. build any version from root). |
| **Optional:** `.pre-commit-config.yaml` | Run checks before each commit (see below). |

---

## Recommended tools (time-saving, modern)

- **IDE:** Android Studio or Cursor/VS Code with Android extensions. Open **FASTPAY_APK** (repo root), not only `FASTPAY_BASE`.
- **EditorConfig:** Already in repo; enable in your editor so indentation and line endings stay consistent across versions.
- **Gradle:** Use existing `gradlew` inside each version folder (e.g. `FASTPAY_BASE/gradlew`). Root script `scripts/build-version.ps1` can drive this from the repo root.
- **Lint / format (optional):** In each Android project you can add **ktlint** and/or **detekt** via Gradle; run manually or from CI. Pre-commit can run them on staged files (see below).
- **CI:** FASTPAY_BASE already has `.github/workflows/android-ci.yml`. Reuse the same pattern for new version folders.

---

## Optional: pre-commit (run checks before commit)

If you use **Git** and want automatic checks:

1. Install [pre-commit](https://pre-commit.com/) (e.g. `pip install pre-commit` or `brew install pre-commit`).
2. From **FASTPAY_APK** root run: `pre-commit install`.
3. The repo’s `.pre-commit-config.yaml` runs:
   - Trailing whitespace and end-of-file fixers (fast, universal).
   - Optional: ktlint/detekt on staged Kotlin files (if you add those tools to the Android project).

Pre-commit is optional; the rest of the environment works without it.

---

## Adding a new version

1. Copy `FASTPAY_BASE` (or your template) to a new folder, e.g. `FASTPAY_V2` or `FASTPAY_BRANDED`.
2. In the new folder: change `applicationId`, `versionCode`, `versionName`, and any branding (strings, icons, colors).
3. Use the same `.editorconfig` and (if you use it) pre-commit; they apply repo-wide.
4. Add a workflow under `.github/workflows/` for the new version if you want CI.

---

---

## Cursor / Agent testing (win–win)

So the **agent can test** the same way you do (build, install, logs), use this section. Paths and commands are explicit.

| Step | Command / path |
|------|-----------------|
| **Project root** | `C:\Users\samsm\AndroidStudioProjects\FASTPAY_APK` (or repo root) |
| **Android project** | `FASTPAY_BASE` (main app; run Gradle from here) |
| **Build debug** | `cd FASTPAY_BASE` then `.\gradlew.bat assembleDebug` |
| **Install on device** | `.\gradlew.bat installDebug` (device must be connected; run from `FASTPAY_BASE`) |
| **One script (build + install)** | From repo root: `.\scripts\test-debug.ps1` or `.\scripts\test-debug.ps1 FASTPAY_BASE` |
| **View activation logs** | `adb logcat -s ActivationActivity:D` (tag used in ActivationActivity) |
| **Clean (if needed)** | `.\gradlew.bat --stop` then `.\gradlew.bat clean assembleDebug` (from `FASTPAY_BASE`; clean can fail if files are locked) |

**Agent:** For tasks that need “test on device”, run `.\scripts\test-debug.ps1` from repo root (or build + install from `FASTPAY_BASE`), then suggest the user run `adb logcat -s ActivationActivity:D` to verify. If Gradle clean fails due to file locks, suggest building from Android Studio or running `gradlew --stop` and retrying.

---

## One-line summary

**Set FASTPAY_APK as the directory** (root). Use **.editorconfig** for style, **scripts/** for building from root, and optionally **pre-commit** for automated checks. Add new versions as sibling folders of `FASTPAY_BASE`.
