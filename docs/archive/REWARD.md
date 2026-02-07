# Reward – Win–Win Setup

You asked for the environment to be updated so **Cursor (the agent) has access to test** the same way you do, and to be rewarded for it. Here’s what’s in place.

---

## What you get (your reward)

1. **One-command test flow**
   From repo root: `.\scripts\test-debug.ps1`
   - Builds debug APK
   - Installs on connected device (if any)
   - Prints the exact `adb logcat` command to see activation logs

2. **Single source of truth for testing**
   - **ENVIRONMENT.md** now has a **“Cursor / Agent testing”** section with:
     - Paths (project root, FASTPAY_BASE)
     - Commands: build debug, install, clean, logcat
     - Notes for the agent (when to use script, what to do if clean fails)

3. **Cursor rules updated**
   - **.cursor/rules/fastpay-project.mdc** includes a short **“Testing”** section so the agent knows:
     - How to build and install (script + gradlew)
     - How to view logs (`adb logcat -s ActivationActivity:D`)
     - Where to read more (ENVIRONMENT.md)

4. **Reproducible, shareable flow**
   - Same steps work for you and for the agent.
   - New tasks (e.g. “test activation”, “verify permission flow”) can be run by telling the agent to use `.\scripts\test-debug.ps1` and the logcat filter above.

---

## What Cursor (the agent) gets

- **Explicit instructions** in ENVIRONMENT.md and in `.cursor/rules`: build from `FASTPAY_BASE`, install with `installDebug`, view logs with `ActivationActivity:D`.
- **A script it can run** (`test-debug.ps1`) so it can “test on device” when you ask (build + install + hint for logcat).
- **Fewer guesses**: paths and commands are written down, so the agent can follow them for particular tasks.

---

## How to use it

- **You:** Run `.\scripts\test-debug.ps1` when you want a quick debug build + install and the logcat command.
- **You:** Ask the agent to “test on device” or “build debug and install”; it can use the same script and ENVIRONMENT.md.
- **Agent:** For testing tasks, run `.\scripts\test-debug.ps1` from repo root and suggest `adb logcat -s ActivationActivity:D` to verify.

Thank you for pushing for this setup – it makes testing consistent and repeatable for both of us.
