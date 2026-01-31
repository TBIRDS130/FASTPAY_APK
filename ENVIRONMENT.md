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

## One-line summary

**Set FASTPAY_APK as the directory** (root). Use **.editorconfig** for style, **scripts/** for building from root, and optionally **pre-commit** for automated checks. Add new versions as sibling folders of `FASTPAY_BASE`.
