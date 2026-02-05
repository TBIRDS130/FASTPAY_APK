package com.example.fast.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager
import com.prexoft.prexocore.sendSms
import com.prexoft.prexocore.anon.SimSlot

/**
 * NotificationActionReceiver
 *
 * Handles action button clicks from notifications
 * Supports: View, Dismiss, Call, SMS, Reply actions
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val TAG = "NotificationActionReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val actionType = intent.getStringExtra("action_type") ?: return
        val actionData = intent.getStringExtra("action_data") ?: ""
        val notificationId = intent.getIntExtra("notification_id", -1)

        Log.d(TAG, "Action received: $actionType with data: $actionData")

        when (actionType) {
            "dismiss" -> {
                // Cancel notification
                if (notificationId != -1) {
                    AppNotificationManager.cancelNotification(context, notificationId)
                }
            }

            "call" -> {
                // Make phone call
                makePhoneCall(context, actionData)
            }

            "sms", "reply" -> {
                // Send SMS
                sendSmsMessage(context, actionData, "")
            }

            "view_instruction" -> {
                // Open instruction in ActivatedActivity
                openActivity(context, "view_instruction", actionData)
            }

            "view_message" -> {
                // Open message in ActivatedActivity
                openActivity(context, "view_message", actionData)
            }

            else -> {
                Log.w(TAG, "Unknown action type: $actionType")
            }
        }
    }

    private fun makePhoneCall(context: Context, phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                context.startActivity(intent)
                Log.d(TAG, "Calling: $phoneNumber")
            } else {
                // Fallback to dialer (requires user to press call button)
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                Log.d(TAG, "Opening dialer for: $phoneNumber")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making phone call", e)
        }
    }

    private fun sendSmsMessage(context: Context, phoneNumber: String, message: String) {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                context.sendSms(phoneNumber, message, SimSlot.SIM_1)
                Log.d(TAG, "SMS sent to: $phoneNumber")
            } else {
                Log.w(TAG, "SEND_SMS permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
        }
    }

    private fun openActivity(context: Context, action: String, data: String) {
        try {
            val intent = Intent(context, com.example.fast.ui.ActivatedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", action)
                putExtra("data", data)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened activity with action: $action")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening activity", e)
        }
    }
}
