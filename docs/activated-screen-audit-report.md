# Activated Screen Audit Report (FASTPAY_BASE)

**Date:** 2025-02-09
**Scope:** `activity_activated.xml` and related Kotlin vs parity spec and HTML demo.
**Reference:** [APKTOWEBVIEW/docs/parity-audit.md](APKTOWEBVIEW/docs/parity-audit.md), [APKTOWEBVIEW/demos/output.html](APKTOWEBVIEW/demos/output.html) (#viewActivated).

---

## Parity spec (from repo)

- **Layout inventory:** activity_activated.xml → screen "activated" = **logo, status, Reset button**.
- **HTML mapping:** #viewActivated = **Status box, Reset button** (and header with logo/tagline).
- **HTML demo:** Shows header (logo + tagline), **status label** ("STATUS"), **status value** ("ACTIVATED"), and **Reset** button.

---

## Current APK (activity_activated.xml)

- **Header:** logo (textView11, `@string/app_name_title`), tagline (textView12, `@string/app_tagline`) — matches.
- **Phone card:** Present (code, date, ANIM) — beyond parity minimum; kept per “keep full screen.”
- **Status card:** Contains only:
  - `statusValue` (TextView, default "PENDING") — value only.
  - `permissionStatusText` (TextView, "Permissions: --" / "Permissions: OK") — extra line.
  - **No "STATUS" label** — no TextView with `@string/status_label` above the value.
- **Device info, SMS card, TEST + Reset:** Present; Reset button uses hardcoded `android:text="Reset"`.
- **strings.xml:** Has `status_label`; **no `reset` string** (extract script reads "Reset" from layout).

---

## Mismatches identified

### 1. Missing STATUS label on Activated screen (parity)

| Item | Spec / HTML | APK |
|------|-------------|-----|
| Status label | "STATUS" above status value (parity audit, output.html #activatedStatusLabel) | Not present; status card has only `statusValue` and `permissionStatusText`. |

**Recommendation:** Add a TextView above `statusValue` in the status card with `android:text="@string/status_label"` (and an appropriate style/size) so the Activated screen shows a visible "STATUS" label like the Activation screen and the HTML demo.

---

### 2. Reset button text not from strings (consistency / i18n)

| Item | Spec / HTML | APK |
|------|-------------|-----|
| Reset caption | apk-config has `reset: 'Reset'` (from layout) | Layout has `android:text="Reset"` (hardcoded). No `<string name="reset">` in strings.xml. |

**Recommendation:** Add `<string name="reset">Reset</string>` to [FASTPAY_BASE/app/src/main/res/values/strings.xml](FASTPAY_BASE/app/src/main/res/values/strings.xml) and set the Reset button to `android:text="@string/reset"` in [activity_activated.xml](FASTPAY_BASE/app/src/main/res/layout/activity_activated.xml) for consistency with other buttons and future i18n.

---

### 3. Optional: other hardcoded strings (i18n)

The following are hardcoded in `activity_activated.xml`; consider moving to strings.xml if you standardize on resource-based copy:

- "Permissions: --" / "Permissions: OK" (also set in code in ActivatedActivity).
- "SMS Messages", "No messages yet", "Waiting for first SMS...", "TEST", "00:00", "ANIM", "TESTING CODE", "ABCDE-12345", "01-01 / 00:00" (many are placeholders or set in code).

No change required for parity; only if you want all user-facing text in resources.

---

## Summary

| # | Mismatch | Severity | Fix |
|---|----------|----------|-----|
| 1 | No "STATUS" label above status value on Activated screen | **Parity** | Add TextView with `@string/status_label` in status card. |
| 2 | Reset button text hardcoded | **Consistency** | Add `string name="reset"` and use `@string/reset` in layout. |
| 3 | Other hardcoded strings | Optional | Move to strings.xml if i18n required. |

Implementing **1** and **2** will align FASTPAY_BASE with the parity spec and the HTML demo for the Activated screen.
