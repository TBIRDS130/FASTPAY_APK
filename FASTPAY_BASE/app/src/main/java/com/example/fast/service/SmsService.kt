package com.example.fast.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

/**
 * SmsService - Required for app to be set as default SMS app
 *
 * This service allows the app to be selected as the default SMS app.
 * When set as default, the app receives SMS broadcasts more reliably
 * and with higher priority, which helps with bulk message handling.
 *
 * The actual SMS processing is handled by SmsReceiver, which uses
 * optimized batching to prevent slowdowns with large message volumes.
 */
class SmsService : Service() {

    private val binder = LocalBinder()
    private val TAG = "SmsService"

    inner class LocalBinder : Binder() {
        fun getService(): SmsService = this@SmsService
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "SmsService bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SmsService created - App can now be set as default SMS app")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle SMS if needed (though SmsReceiver handles most cases)
        return START_STICKY
    }
}
