package com.example.fast.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * HeadlessSmsSendService - Required for default SMS app functionality
 *
 * This service handles the "Respond via Message" intent that appears
 * during phone calls, allowing users to quickly send SMS messages
 * without leaving the call screen.
 *
 * This is a required component for an app to be eligible as the
 * default SMS app on Android.
 */
class HeadlessSmsSendService : Service() {

    private val TAG = "HeadlessSmsSendService"

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "HeadlessSmsSendService bound")
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HeadlessSmsSendService started - handling respond via message")
        // Handle quick response calls
        // The actual SMS sending is typically handled by the main SMS app UI
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeadlessSmsSendService created")
    }
}
