package com.example.fast.util

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * DefaultSmsAppHelper
 *
 * Utility to check if app is default SMS app and request user to set it as default.
 *
 * Benefits of being default SMS app:
 * - Higher priority for SMS broadcasts
 * - More reliable message delivery
 * - Better handling of bulk messages (5000+)
 * - Direct access to SMS_DELIVER_ACTION
 */
object DefaultSmsAppHelper {

    private const val TAG = "DefaultSmsAppHelper"

    /**
     * Check if this app is currently the default SMS app
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        return try {
            val packageName = context.packageName
            val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context)
            val isDefault = defaultSmsApp == packageName

            Log.d(TAG, "Is default SMS app: $isDefault (default: $defaultSmsApp, current: $packageName)")
            isDefault
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default SMS app", e)
            false
        }
    }

    /**
     * Request user to set this app as default SMS app
     * Opens system settings dialog
     */
    fun requestDefaultSmsApp(context: Context) {
        try {
            val intent: Intent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ - Use role manager
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager == null) {
                    Log.e(TAG, "RoleManager is null - cannot create request intent")
                    throw IllegalStateException("RoleManager service not available")
                }
                val roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                if (roleIntent == null) {
                    Log.e(TAG, "createRequestRoleIntent returned null")
                    throw IllegalStateException("Failed to create role request intent")
                }
                roleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                roleIntent
            } else {
                // Android 9 and below - Use Telephony intent
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }

            if (intent == null) {
                Log.e(TAG, "Intent is null - cannot start activity")
                throw IllegalStateException("Intent is null")
            }

            // Resolve the intent to check if there's an activity that can handle it
            val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo == null) {
                Log.e(TAG, "No activity found to handle default SMS app selection intent")
                throw IllegalStateException("No activity found to handle intent")
            }

            // Use applicationContext to ensure we can start from service
            val appContext = context.applicationContext
            appContext.startActivity(intent)
            Log.d(TAG, "Opened default SMS app selection dialog successfully")
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "Activity not found for default SMS app selection", e)
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening default SMS app settings", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error opening default SMS app settings", e)
            throw e
        }
    }

    /**
     * Get the package name of current default SMS app
     */
    fun getDefaultSmsAppPackage(context: Context): String? {
        return try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default SMS app package", e)
            null
        }
    }
}
