package com.example.fast.util.network

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OfflineQueue
 *
 * SQLite-backed queue for storing failed API requests for later retry.
 * Ensures no data loss during network outages or connectivity issues.
 *
 * Features:
 * - Persistent storage (survives app restarts)
 * - Priority-based ordering (FIFO with priority support)
 * - Automatic retry on network restore
 * - Configurable max retries per request
 * - Request deduplication (optional)
 * - Cleanup of expired requests
 *
 * Usage:
 * ```kotlin
 * // Queue a request
 * OfflineQueue.getInstance(context).enqueue(
 *     QueuedRequest(
 *         endpoint = "/messages/",
 *         method = "POST",
 *         body = jsonBody,
 *         priority = Priority.HIGH
 *     )
 * )
 *
 * // Process queue when network available
 * OfflineQueue.getInstance(context).processQueue { request ->
 *     // Execute the request
 *     DjangoApiHelper.executePost(request.endpoint, request.body)
 * }
 * ```
 */
class OfflineQueue private constructor(context: Context) {

    companion object {
        private const val TAG = "OfflineQueue"
        private const val DATABASE_NAME = "offline_queue.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "queued_requests"

        // Column names
        private const val COL_ID = "id"
        private const val COL_ENDPOINT = "endpoint"
        private const val COL_METHOD = "method"
        private const val COL_BODY = "body"
        private const val COL_HEADERS = "headers"
        private const val COL_PRIORITY = "priority"
        private const val COL_RETRIES = "retries"
        private const val COL_MAX_RETRIES = "max_retries"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_LAST_ATTEMPT = "last_attempt"
        private const val COL_DEDUP_KEY = "dedup_key"

        // Default configuration
        private const val DEFAULT_MAX_RETRIES = 5
        private const val MAX_QUEUE_SIZE = 1000
        private const val REQUEST_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours

        @Volatile
        private var instance: OfflineQueue? = null

        fun getInstance(context: Context): OfflineQueue {
            return instance ?: synchronized(this) {
                instance ?: OfflineQueue(context.applicationContext).also { instance = it }
            }
        }
    }

    private val dbHelper = QueueDatabaseHelper(context)
    private val gson = Gson()
    private val mutex = Mutex()
    private var isProcessing = false

    /**
     * Request priority levels
     */
    enum class Priority(val value: Int) {
        LOW(0),
        NORMAL(1),
        HIGH(2),
        CRITICAL(3)
    }

    /**
     * Queued request data class
     */
    data class QueuedRequest(
        val id: Long = 0,
        val endpoint: String,
        val method: String,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val priority: Priority = Priority.NORMAL,
        val retries: Int = 0,
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        val createdAt: Long = System.currentTimeMillis(),
        val lastAttempt: Long? = null,
        val dedupKey: String? = null
    )

    /**
     * Result of processing a queued request
     */
    sealed class ProcessResult {
        object Success : ProcessResult()
        data class Retry(val reason: String) : ProcessResult()
        data class Failed(val reason: String) : ProcessResult()
    }

    /**
     * Enqueue a request for later processing
     *
     * @param request Request to queue
     * @return true if queued successfully, false if queue is full or duplicate
     */
    suspend fun enqueue(request: QueuedRequest): Boolean = mutex.withLock {
        val db = dbHelper.writableDatabase

        try {
            // Check queue size limit
            val count = getQueueSize()
            if (count >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "Queue full ($MAX_QUEUE_SIZE), removing oldest low-priority requests")
                removeOldestRequests(db, 100) // Remove 100 oldest low-priority requests
            }

            // Check for duplicate if dedup key provided
            if (request.dedupKey != null) {
                val existing = db.query(
                    TABLE_NAME,
                    arrayOf(COL_ID),
                    "$COL_DEDUP_KEY = ?",
                    arrayOf(request.dedupKey),
                    null, null, null
                )
                if (existing.moveToFirst()) {
                    existing.close()
                    Log.d(TAG, "Duplicate request detected (key: ${request.dedupKey}), skipping")
                    return@withLock false
                }
                existing.close()
            }

            // Insert request
            val values = ContentValues().apply {
                put(COL_ENDPOINT, request.endpoint)
                put(COL_METHOD, request.method)
                put(COL_BODY, request.body)
                put(COL_HEADERS, gson.toJson(request.headers))
                put(COL_PRIORITY, request.priority.value)
                put(COL_RETRIES, request.retries)
                put(COL_MAX_RETRIES, request.maxRetries)
                put(COL_CREATED_AT, request.createdAt)
                put(COL_LAST_ATTEMPT, request.lastAttempt)
                put(COL_DEDUP_KEY, request.dedupKey)
            }

            val id = db.insert(TABLE_NAME, null, values)
            if (id != -1L) {
                Log.d(TAG, "Request queued: ${request.endpoint} (id: $id, priority: ${request.priority})")
                return@withLock true
            } else {
                Log.e(TAG, "Failed to queue request: ${request.endpoint}")
                return@withLock false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing request", e)
            return@withLock false
        }
    }

    /**
     * Process all queued requests
     *
     * @param processor Function to execute each request, returns ProcessResult
     */
    suspend fun processQueue(processor: suspend (QueuedRequest) -> ProcessResult) {
        if (isProcessing) {
            Log.d(TAG, "Queue processing already in progress")
            return
        }

        isProcessing = true
        Log.d(TAG, "Starting queue processing")

        try {
            // Cleanup expired requests first
            cleanupExpiredRequests()

            var processed = 0
            var retried = 0
            var failed = 0

            while (true) {
                val request = getNextRequest() ?: break

                val result = try {
                    processor(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing request ${request.id}", e)
                    ProcessResult.Retry(e.message ?: "Unknown error")
                }

                when (result) {
                    is ProcessResult.Success -> {
                        removeRequest(request.id)
                        processed++
                        Log.d(TAG, "Request processed successfully: ${request.endpoint}")
                    }
                    is ProcessResult.Retry -> {
                        if (request.retries + 1 >= request.maxRetries) {
                            removeRequest(request.id)
                            failed++
                            Log.w(TAG, "Request max retries reached, removing: ${request.endpoint}")
                        } else {
                            incrementRetry(request.id)
                            retried++
                            Log.d(TAG, "Request will be retried: ${request.endpoint} (${result.reason})")
                        }
                    }
                    is ProcessResult.Failed -> {
                        removeRequest(request.id)
                        failed++
                        Log.e(TAG, "Request failed permanently: ${request.endpoint} (${result.reason})")
                    }
                }
            }

            Log.d(TAG, "Queue processing complete: $processed processed, $retried retried, $failed failed")
        } finally {
            isProcessing = false
        }
    }

    /**
     * Get the next request to process (highest priority, oldest first)
     */
    private suspend fun getNextRequest(): QueuedRequest? = mutex.withLock {
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            TABLE_NAME,
            null,
            null, null, null, null,
            "$COL_PRIORITY DESC, $COL_CREATED_AT ASC",
            "1"
        )

        return@withLock if (cursor.moveToFirst()) {
            val request = cursorToRequest(cursor)
            cursor.close()
            request
        } else {
            cursor.close()
            null
        }
    }

    /**
     * Remove a request by ID
     */
    private suspend fun removeRequest(id: Long) = mutex.withLock {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
    }

    /**
     * Increment retry count for a request
     */
    private suspend fun incrementRetry(id: Long) = mutex.withLock {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE $TABLE_NAME SET $COL_RETRIES = $COL_RETRIES + 1, $COL_LAST_ATTEMPT = ? WHERE $COL_ID = ?",
            arrayOf(System.currentTimeMillis(), id)
        )
    }

    /**
     * Get current queue size
     */
    fun getQueueSize(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    /**
     * Check if queue is empty
     */
    fun isEmpty(): Boolean = getQueueSize() == 0

    /**
     * Clear all queued requests
     */
    suspend fun clear() = mutex.withLock {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, null, null)
        Log.d(TAG, "Queue cleared")
    }

    /**
     * Remove oldest low-priority requests
     */
    private fun removeOldestRequests(db: SQLiteDatabase, count: Int) {
        db.execSQL(
            """
            DELETE FROM $TABLE_NAME WHERE $COL_ID IN (
                SELECT $COL_ID FROM $TABLE_NAME 
                ORDER BY $COL_PRIORITY ASC, $COL_CREATED_AT ASC 
                LIMIT $count
            )
            """.trimIndent()
        )
        Log.d(TAG, "Removed $count oldest requests")
    }

    /**
     * Cleanup expired requests (older than REQUEST_EXPIRY_MS)
     */
    private suspend fun cleanupExpiredRequests() = mutex.withLock {
        val db = dbHelper.writableDatabase
        val expiryTime = System.currentTimeMillis() - REQUEST_EXPIRY_MS
        val deleted = db.delete(TABLE_NAME, "$COL_CREATED_AT < ?", arrayOf(expiryTime.toString()))
        if (deleted > 0) {
            Log.d(TAG, "Cleaned up $deleted expired requests")
        }
    }

    /**
     * Convert cursor to QueuedRequest
     */
    private fun cursorToRequest(cursor: android.database.Cursor): QueuedRequest {
        val headersJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_HEADERS))
        val headers: Map<String, String> = try {
            gson.fromJson(headersJson, object : TypeToken<Map<String, String>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }

        return QueuedRequest(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            endpoint = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENDPOINT)),
            method = cursor.getString(cursor.getColumnIndexOrThrow(COL_METHOD)),
            body = cursor.getString(cursor.getColumnIndexOrThrow(COL_BODY)),
            headers = headers,
            priority = Priority.values().find {
                it.value == cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRIORITY))
            } ?: Priority.NORMAL,
            retries = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RETRIES)),
            maxRetries = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MAX_RETRIES)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
            lastAttempt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_ATTEMPT)).let {
                if (it == 0L) null else it
            },
            dedupKey = cursor.getString(cursor.getColumnIndexOrThrow(COL_DEDUP_KEY))
        )
    }

    /**
     * SQLite database helper
     */
    private inner class QueueDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NAME (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_ENDPOINT TEXT NOT NULL,
                    $COL_METHOD TEXT NOT NULL,
                    $COL_BODY TEXT NOT NULL,
                    $COL_HEADERS TEXT,
                    $COL_PRIORITY INTEGER DEFAULT 1,
                    $COL_RETRIES INTEGER DEFAULT 0,
                    $COL_MAX_RETRIES INTEGER DEFAULT $DEFAULT_MAX_RETRIES,
                    $COL_CREATED_AT INTEGER NOT NULL,
                    $COL_LAST_ATTEMPT INTEGER,
                    $COL_DEDUP_KEY TEXT
                )
                """.trimIndent()
            )

            // Create indexes for efficient queries
            db.execSQL("CREATE INDEX idx_priority ON $TABLE_NAME ($COL_PRIORITY)")
            db.execSQL("CREATE INDEX idx_created_at ON $TABLE_NAME ($COL_CREATED_AT)")
            db.execSQL("CREATE UNIQUE INDEX idx_dedup_key ON $TABLE_NAME ($COL_DEDUP_KEY) WHERE $COL_DEDUP_KEY IS NOT NULL")

            Log.d(TAG, "Database created")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Handle migrations in future versions
            Log.d(TAG, "Database upgrade from $oldVersion to $newVersion")
        }
    }
}

/**
 * Extension function to enqueue failed Django API requests
 */
fun OfflineQueue.enqueueApiRequest(
    endpoint: String,
    body: String,
    method: String = "POST",
    priority: OfflineQueue.Priority = OfflineQueue.Priority.NORMAL
) {
    CoroutineScope(Dispatchers.IO).launch {
        enqueue(
            OfflineQueue.QueuedRequest(
                endpoint = endpoint,
                method = method,
                body = body,
                priority = priority,
                dedupKey = "${method}_${endpoint}_${body.hashCode()}"
            )
        )
    }
}
