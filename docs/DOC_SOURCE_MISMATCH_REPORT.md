# Doc ↔ Source Mismatch Report

**Generated:** Check run against `FASTPAY_BASE` source. Update this report when fixing mismatches.

---

## Mismatches Found

### 1. Django API default base URL

| Location | Docs say | Source says |
|----------|----------|-------------|
| `docs/04-django-api.md` | `https://api.fastpaygaming.com` | `https://api-staging.fastpaygaming.com/api` |

**Source:** `FASTPAY_BASE/app/build.gradle.kts` line 64, `AppConfig.kt` line 166.

**Fix:** ✅ Fixed – docs/04-django-api.md updated.

---

### 2. ContactSmsSyncService not in Manifest

| Location | Docs say | Reality |
|----------|----------|---------|
| `docs/03-architecture.md` | Lists `ContactSmsSyncService` as a Service | Was missing from manifest |

**Fix:** ✅ Fixed – ContactSmsSyncService added to AndroidManifest.xml.

---

### 3. docs/03-architecture.md – Source layout incomplete

Docs list a subset of util classes. Actual `util/` has many more: `LogHelper`, `MessageAnalyticsManager`, `MessageForwarder`, `MessageTemplateEngine`, `NetworkUtils`, `NotificationBatchProcessor`, `PermissionFirebaseSync`, `PermissionManager`, `PermissionSyncHelper`, `Result.kt`, `SmsMessageBatchProcessor`, `SmsQueryHelper`, `SmsTestHelper`, `SyncStateManager`, `UpdateDownloadManager`, `VersionChecker`, `WorkflowExecutor`, `UIOptimizationHelper`, etc.

**Fix:** ✅ Doc updated – docs/03-architecture.md util line now references full list in util/.

---

## Verified (no mismatch)

- **Firebase paths:** `device/{deviceId}/`, `message/{deviceId}/`, `fastpay/{code}/`, `fastpay/running/{code}/` – match `AppConfig.kt`.
- **Manifest activities:** SplashActivity, ActivationActivity, ActivatedActivity, FirebaseCallLogActivity, MultipurposeCardActivity, MultipurposeCardTestActivity – all present.
- **Manifest receivers:** SmsReceiver, TestSmsReceiver, MmsReceiver, BootCompletedReceiver, ScheduledSmsReceiver, NotificationActionReceiver – all present.
- **Koin:** Used (libs.versions.toml, KoinModules.kt) – docs correct.
- **Namespace/applicationId:** `com.example.fast` – correct.
- **SDK:** min 27, target/compile 36 – correct.

---

## How to re-check

From repo root, run:

```powershell
# Django URL
Select-String -Path "FASTPAY_BASE\app\build.gradle.kts" -Pattern "DJANGO_API"

# Manifest services
Select-String -Path "FASTPAY_BASE\app\src\main\AndroidManifest.xml" -Pattern "android:name=" | Where-Object { $_ -match "service" }

# Firebase base path
Select-String -Path "FASTPAY_BASE\app\src\main\java\com\example\fast\config\AppConfig.kt" -Pattern "FIREBASE_BASE_PATH|getFirebaseDevicePath"
```

Or use Cursor search: grep for class names, paths, and config values in both `docs/` and `FASTPAY_BASE/`.
