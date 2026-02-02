package com.example.fast.ui.activated

import android.content.Context
import android.util.Log
import com.example.fast.service.PersistentForegroundService
import com.example.fast.util.LogHelper

/**
 * Manages foreground service lifecycle for ActivatedActivity
 */
class ActivatedServiceManager(private val context: Context) {

    /**
     * Check if service is running
     */
    fun isServiceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == PersistentForegroundService::class.java.name
        }
    }

    /**
     * Ensure the foreground service is running
     */
    fun ensureServiceRunning() {
        if (!isServiceRunning()) {
            try {
                PersistentForegroundService.start(context)
                LogHelper.d("ActivatedServiceManager", "Service started - was not running")
            } catch (e: Exception) {
                LogHelper.e("ActivatedServiceManager", "Error starting service", e)
                // Retry after a short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        PersistentForegroundService.start(context)
                    } catch (retryException: Exception) {
                        LogHelper.e("ActivatedServiceManager", "Retry failed to start service", retryException)
                    }
                }, 2000) // Retry after 2 seconds
            }
        }
    }
}
