# Keystore Setup

## Overview

Keystore credentials are loaded from a `keystore.properties` file in **FASTPAY_BASE/** that is excluded from version control. They are not stored in `build.gradle.kts`.

## Setup Steps

### 1. Create keystore.properties

Copy the template in FASTPAY_BASE:

```bash
cd FASTPAY_BASE
cp keystore.properties.template keystore.properties
```

### 2. Fill in credentials

Edit `keystore.properties`:

```properties
KEYSTORE_FILE=release.keystore
KEYSTORE_PASSWORD=your_actual_password
KEY_ALIAS=your_actual_alias
KEY_PASSWORD=your_actual_key_password
```

- `KEYSTORE_FILE` can be relative to the project (e.g. `release.keystore`) or an absolute path.

### 3. Verify .gitignore

Ensure `keystore.properties` is in `.gitignore`. This file must **never** be committed.

### 4. Build

```bash
./gradlew assembleRelease
```

## Troubleshooting

- **Build fails with "keystore.properties not found"** – Required for release builds; create it as above.
- **Build fails with "keystore file not found"** – Check `KEYSTORE_FILE` path (relative to project or absolute).
- **Authentication errors** – Verify `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

## Security

- Never commit `keystore.properties`.
- Store the keystore file in a secure location; back it up (loss means you cannot update the app on Play).
- In CI, use secrets (e.g. GitHub Actions secrets) instead of committing the file.
