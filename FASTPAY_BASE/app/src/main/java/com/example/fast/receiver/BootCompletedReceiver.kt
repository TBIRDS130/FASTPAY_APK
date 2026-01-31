package com.example.fast.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.fast.service.PersistentForegroundService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Automatically restart the foreground service after device reboot
            PersistentForegroundService.start(context)
        }
    }
}
