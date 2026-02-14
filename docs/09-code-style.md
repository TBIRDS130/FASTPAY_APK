# Code Style Guide

## Overview

Coding standards and best practices for the FastPay Android application. The project uses **Koin** for dependency injection (not Hilt).

## Kotlin Style

### Naming Conventions

- **Classes/Objects:** `PascalCase` (e.g. `MainActivity`, `FirebaseHelper`).
- **Functions:** `camelCase` (e.g. `getUserData()`, `sendSms()`).
- **Variables:** `camelCase`; use `val` over `var` where possible.
- **Constants:** `UPPER_SNAKE_CASE` or inside `object Constants`.

### Code Organization

- Imports grouped (stdlib, Android, androidx, project).
- Class order: companion object, properties, init/constructor, public functions, private functions.

### Formatting

- **Indentation:** 4 spaces (no tabs). Align with `.editorconfig`.
- **Line length:** Prefer ≤ 120 characters; break long lines sensibly.
- **Blank lines:** One between functions; two between top-level classes.

### Best Practices

- Prefer expression bodies: `fun getCount(): Int = items.size`.
- Use string templates: `"Hello, $name!"`.
- Use safe calls and `when` instead of long `if-else` chains.

## Android-Specific

### Activities

- Use ViewBinding; set content in `onCreate`. ViewModels via `by viewModels()` or Koin.

### ViewModels

- The project uses **Koin** for DI. ViewModels are provided in `KoinModules.kt` and injected (e.g. `by viewModel()`).

### Logging

- Use the app **Logger** (not `Log.d` or `println`) for non-copyable debug:
  - `Logger.d("Tag", "Debug message")`
  - `Logger.e("Tag", exception, "Error message")`
- Use **DebugLogger** for copyable logs (flow, animation, visibility, placement, screen snapshot); long-press logo on Activated screen to copy:
  - `DebugLogger.logFlow(screen, step, detail)`
  - `DebugLogger.logAnimation(step, detail)` / `logAnimationEnd(step, ms)`
  - `DebugLogger.logVisibility(component, state, extras)`
  - `DebugLogger.logPlacement(context, component, x, y, w, h, alpha, source)`
  - `DebugLogger.logScreenSnapshot(screen, elements)` — full screen state: one line with `element=value` pairs. Values: `V` (visible), `G` (gone), `I` (invisible); optional `(alpha)` e.g. `V(1.0)`; `smsSide`: `sms` | `instruction`; `utilitySide`: `keypad` | `status`.

### Error Handling

- Use only **core.error.*** for app exceptions (FastPayException, FirebaseException, etc.) and **core.result.Result** for operation results; do not add exception or Result types in `util` or `model`.
- Prefer **Result** pattern: `result.onSuccess { }.onError { }`. Use `Logger.e` in error paths.

## Documentation

- **KDoc** for public APIs: `@param`, `@return`, `@throws` where helpful.
- Inline comments explain *why*, not *what*.

## Testing

- Test names: descriptive (e.g. `test user login with valid credentials`).
- Structure: Arrange – Act – Assert.

## Resources

- Use string resources: `getString(R.string.welcome_message)`.
- Shared constants in `Constants.kt` or resources.

## Git Commit Messages

- Format: `type(scope): subject` (e.g. `feat(sms): Add bulk SMS`, `fix(firebase): Resolve timeout`).
- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`.

---

**Last Updated:** February 2026
