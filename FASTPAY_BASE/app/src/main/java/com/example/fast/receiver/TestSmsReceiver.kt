package com.example.fast.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * TestSmsReceiver - Receives test SMS broadcasts and forwards to SmsReceiver
 *
 * This receiver has no permission requirement, so it can receive test broadcasts
 * sent via context.sendBroadcast(). It then forwards to SmsReceiver for processing.
 */
class TestSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.fast.TEST_SMS_ACTION") {
            Log.d("TestSmsReceiver", "📡 TEST SMS BROADCAST RECEIVED!")
            Log.d("TestSmsReceiver", "   Action: ${intent.action}")

            val sender = intent.getStringExtra("sender") ?: ""
            val body = intent.getStringExtra("body") ?: ""
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

            Log.d("TestSmsReceiver", "   Sender: $sender, Body: $body")
            Log.d("TestSmsReceiver", "📤 Forwarding to SmsReceiver for processing...")

            // Forward to SmsReceiver for processing
            SmsReceiver.processTestMessage(
                context = context,
                senderPhoneNumber = sender,
                messageBody = body,
                timestamp = timestamp
            )

            Log.d("TestSmsReceiver", "✅ Test broadcast forwarded successfully")
        }
    }
}
