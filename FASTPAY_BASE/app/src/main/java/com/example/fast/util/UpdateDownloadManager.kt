package com.example.fast.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Enhanced Download Manager for App Updates
 * Handles download progress tracking, speed calculation, and error handling
 * 
 * Improvements:
 * - Robust file path resolution across Android versions
 * - Stores expected file path for fallback
 * - Better error handling and recovery
 * - Supports both COLUMN_LOCAL_URI and COLUMN_LOCAL_FILENAME
 */
class UpdateDownloadManager(private val context: Context) {
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressRunnable: Runnable? = null
    private var isDownloading = false
    private var expectedFilePath: File? = null // Store expected file path for fallback
    private var currentDownloadUrl: String? = null // Store current download URL for error reporting
    
    private val TAG = "UpdateDownloadManager"
    
    /**
     * Download progress callback
     */
    interface DownloadProgressCallback {
        fun onProgress(progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String)
        fun onComplete(file: File)
        fun onError(error: String)
        fun onCancelled()
    }
    
    /**
     * Start download with progress tracking
     */
    fun startDownload(
        downloadUrl: String,
        versionCode: Int,
        callback: DownloadProgressCallback
    ): Long {
        if (isDownloading) {
            callback.onError("Download already in progress")
            return -1
        }
        
        try {
            // Validate and normalize URL
            val normalizedUrl = normalizeDownloadUrl(downloadUrl.trim())
            if (normalizedUrl == null) {
                Log.e(TAG, "Invalid download URL: $downloadUrl")
                callback.onError("Invalid download URL format: $downloadUrl")
                return -1
            }
            
            // Store URL for error reporting
            currentDownloadUrl = normalizedUrl
            
            Log.d(TAG, "Starting download from URL: $normalizedUrl")
            
            // Get storage directory
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            downloadsDir.mkdirs()
            
            // Create file with version code
            val fileName = "FastPay_Update_v${versionCode}.apk"
            val file = File(downloadsDir, fileName)
            
            // Store expected file path for fallback
            expectedFilePath = file
            
            // Delete old file if exists
            if (file.exists()) {
                file.delete()
            }
            
            // Create download request with modern approach
            val request = DownloadManager.Request(Uri.parse(normalizedUrl))
                .setTitle("FastPay Update")
                .setDescription("Downloading new version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            // Set destination - use modern method for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ requires using MediaStore or app-specific directory
                request.setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            } else {
                // For older versions, use setDestinationUri (still works)
                try {
                    request.setDestinationUri(Uri.fromFile(file))
                } catch (e: Exception) {
                    // Fallback: use setDestinationInExternalFilesDir
                    Log.w(TAG, "setDestinationUri failed, using fallback: ${e.message}")
                    request.setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                }
            }
            
            // Enqueue download
            downloadId = downloadManager.enqueue(request)
            isDownloading = true
            
            Log.d(TAG, "Download started - ID: $downloadId, File: ${file.absolutePath}")
            
            // Register completion receiver
            registerCompletionReceiver(callback)
            
            // Start progress tracking with a small delay to ensure download is queued
            handler.postDelayed({
                startProgressTracking(callback)
            }, 200)
            
            return downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
            callback.onError("Failed to start download: ${e.message}")
            return -1
        }
    }
    
    /**
     * Register broadcast receiver for download completion
     */
    private fun registerCompletionReceiver(callback: DownloadProgressCallback) {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                if (id == downloadId) {
                    handleDownloadComplete(id, callback)
                    unregisterReceiver()
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(downloadReceiver, filter)
        }
    }
    
    /**
     * Start progress tracking
     */
    private fun startProgressTracking(callback: DownloadProgressCallback) {
        var lastDownloadedBytes = 0L
        var lastUpdateTime = System.currentTimeMillis()
        
        progressRunnable = object : Runnable {
            override fun run() {
                if (!isDownloading) return
                
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        
                        // Get bytes info (available for all statuses)
                        val downloadedBytes = try {
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            )
                        } catch (e: Exception) {
                            0L
                        }
                        
                        val totalBytes = try {
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            )
                        } catch (e: Exception) {
                            -1L // -1 means unknown
                        }
                        
                        when (status) {
                            DownloadManager.STATUS_PENDING -> {
                                // Download is queued but not started yet
                                callback.onProgress(0, 0, if (totalBytes > 0) totalBytes else 0, "Queued...")
                                handler.postDelayed(this, 500)
                            }
                            DownloadManager.STATUS_RUNNING -> {
                                // Calculate progress
                                val progress = if (totalBytes > 0) {
                                    ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    // If total bytes unknown, show indeterminate progress
                                    if (downloadedBytes > 0) {
                                        // Show some progress based on downloaded bytes
                                        minOf(99, (downloadedBytes / (1024 * 1024)).toInt()) // Max 99% if size unknown
                                    } else {
                                        0
                                    }
                                }
                                
                                // Calculate speed
                                val currentTime = System.currentTimeMillis()
                                val timeDelta = (currentTime - lastUpdateTime) / 1000.0 // seconds
                                val bytesDelta = downloadedBytes - lastDownloadedBytes
                                val speed = if (timeDelta > 0 && bytesDelta > 0) {
                                    formatSpeed(bytesDelta / timeDelta)
                                } else {
                                    "Calculating..."
                                }
                                
                                lastDownloadedBytes = downloadedBytes
                                lastUpdateTime = currentTime
                                
                                // Always call progress callback to update UI
                                Log.d(TAG, "Progress: $progress%, Downloaded: $downloadedBytes, Total: $totalBytes, Speed: $speed")
                                callback.onProgress(progress, downloadedBytes, if (totalBytes > 0) totalBytes else 0, speed)
                                
                                // Continue tracking
                                handler.postDelayed(this, 500) // Update every 500ms
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                // Handled by broadcast receiver, but ensure progress is 100%
                                callback.onProgress(100, downloadedBytes, if (totalBytes > 0) totalBytes else downloadedBytes, "Complete")
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                                )
                                
                                // Try to get HTTP status code if available
                                val httpStatus = try {
                                    val statusIndex = cursor.getColumnIndex("http_status")
                                    if (statusIndex >= 0) {
                                        cursor.getInt(statusIndex)
                                    } else {
                                        -1
                                    }
                                } catch (e: Exception) {
                                    -1
                                }
                                
                                // Try to get error message from server response
                                val serverMessage = try {
                                    val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                                    if (titleIndex >= 0) {
                                        cursor.getString(titleIndex)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                                
                                val errorMessage = getErrorMessage(reason, httpStatus, serverMessage, currentDownloadUrl)
                                isDownloading = false
                                Log.e(TAG, "Download failed - URL: $currentDownloadUrl, Reason: $reason, HTTP Status: $httpStatus, Message: $serverMessage")
                                callback.onError(errorMessage)
                            }
                            DownloadManager.STATUS_PAUSED -> {
                                // Paused - continue tracking and show current progress
                                val progress = if (totalBytes > 0) {
                                    ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                callback.onProgress(progress, downloadedBytes, if (totalBytes > 0) totalBytes else 0, "Paused")
                                handler.postDelayed(this, 1000)
                            }
                        }
                    } else {
                        // Cursor is empty - download might not have started yet
                        Log.w(TAG, "Download cursor is empty, retrying...")
                        handler.postDelayed(this, 500)
                    }
                    cursor.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error tracking progress", e)
                    handler.postDelayed(this, 1000) // Retry after delay
                }
            }
        }
        
        // Start tracking immediately
        handler.post(progressRunnable!!)
    }
    
    /**
     * Handle download completion with robust file path resolution
     */
    private fun handleDownloadComplete(downloadId: Long, callback: DownloadProgressCallback) {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val file = resolveDownloadedFile(cursor)
                    
                    if (file != null && file.exists()) {
                        isDownloading = false
                        Log.d(TAG, "Download complete - File: ${file.absolutePath}, Size: ${file.length()} bytes")
                        callback.onComplete(file)
                    } else {
                        val errorMsg = "Downloaded file not found. Expected: ${expectedFilePath?.absolutePath}"
                        Log.e(TAG, errorMsg)
                        callback.onError(errorMsg)
                    }
                } else {
                    val reason = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    )
                    
                    // Try to get HTTP status code if available
                    val httpStatus = try {
                        val statusIndex = cursor.getColumnIndex("http_status")
                        if (statusIndex >= 0) {
                            cursor.getInt(statusIndex)
                        } else {
                            -1
                        }
                    } catch (e: Exception) {
                        -1
                    }
                    
                    // Try to get error message from server response
                    val serverMessage = try {
                        val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        if (titleIndex >= 0) {
                            cursor.getString(titleIndex)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                    
                    Log.e(TAG, "Download failed - URL: $currentDownloadUrl, Reason: $reason, HTTP Status: $httpStatus, Message: $serverMessage")
                    callback.onError(getErrorMessage(reason, httpStatus, serverMessage, currentDownloadUrl))
                }
            } else {
                callback.onError("Download completed but cursor is empty")
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling download complete", e)
            callback.onError("Error processing download: ${e.message}")
        }
    }
    
    /**
     * Resolve downloaded file path using multiple methods for maximum compatibility
     */
    private fun resolveDownloadedFile(cursor: Cursor): File? {
        // Method 1: Try COLUMN_LOCAL_URI (works on most Android versions)
        try {
            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (localUriIndex >= 0) {
                val localUriString = cursor.getString(localUriIndex)
                if (localUriString != null) {
                    try {
                        val uri = Uri.parse(localUriString)
                        val file = when {
                            uri.scheme == "file" -> {
                                // file:// URI
                                File(uri.path ?: "")
                            }
                            uri.scheme == "content" -> {
                                // content:// URI - try to get file path
                                getFileFromContentUri(uri)
                            }
                            else -> {
                                // Try parsing as path directly
                                File(localUriString)
                            }
                        }
                        
                        if (file != null && file.exists()) {
                            Log.d(TAG, "Resolved file via COLUMN_LOCAL_URI: ${file.absolutePath}")
                            return file
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse COLUMN_LOCAL_URI: $localUriString", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading COLUMN_LOCAL_URI", e)
        }
        
        // Method 2: Try COLUMN_LOCAL_FILENAME (Android 7.0+)
        try {
            val localFilenameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
            if (localFilenameIndex >= 0) {
                val localFilename = cursor.getString(localFilenameIndex)
                if (localFilename != null) {
                    val file = File(localFilename)
                    if (file.exists()) {
                        Log.d(TAG, "Resolved file via COLUMN_LOCAL_FILENAME: ${file.absolutePath}")
                        return file
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading COLUMN_LOCAL_FILENAME", e)
        }
        
        // Method 3: Fallback to expected file path (we stored this when starting download)
        if (expectedFilePath != null && expectedFilePath!!.exists()) {
            Log.d(TAG, "Resolved file via expected path fallback: ${expectedFilePath!!.absolutePath}")
            return expectedFilePath
        }
        
        // Method 4: Try to find file in downloads directory by name pattern
        try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            
            if (downloadsDir != null && downloadsDir.exists()) {
                val files = downloadsDir.listFiles { file ->
                    file.name.startsWith("FastPay_Update") && file.name.endsWith(".apk")
                }
                
                // Get the most recently modified file
                files?.maxByOrNull { it.lastModified() }?.let { file ->
                    Log.d(TAG, "Resolved file via directory search: ${file.absolutePath}")
                    return file
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching downloads directory", e)
        }
        
        Log.e(TAG, "Could not resolve downloaded file path")
        return null
    }
    
    /**
     * Get file from content URI (for Android 10+)
     */
    private fun getFileFromContentUri(uri: Uri): File? {
        return try {
            // Try to get file path from content URI
            val filePath = uri.path
            if (filePath != null) {
                // Remove /external_files/Download prefix if present
                val cleanPath = filePath.removePrefix("/external_files/Download/")
                    .removePrefix("/external_files/")
                    .removePrefix("/Download/")
                
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                
                if (downloadsDir != null) {
                    val file = File(downloadsDir, cleanPath)
                    if (file.exists()) {
                        return file
                    }
                }
            }
            
            // Fallback: try to open input stream and copy (not ideal but works)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error getting file from content URI", e)
            null
        }
    }
    
    /**
     * Cancel download
     */
    fun cancelDownload() {
        if (downloadId != -1L && isDownloading) {
            try {
                downloadManager.remove(downloadId)
                isDownloading = false
                progressRunnable?.let { handler.removeCallbacks(it) }
                unregisterReceiver()
                expectedFilePath = null
                currentDownloadUrl = null
                Log.d(TAG, "Download cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling download", e)
            }
        }
    }
    
    /**
     * Unregister receiver
     */
    private fun unregisterReceiver() {
        try {
            downloadReceiver?.let { context.unregisterReceiver(it) }
            downloadReceiver = null
        } catch (e: Exception) {
            // Already unregistered
            Log.d(TAG, "Receiver already unregistered")
        }
    }
    
    /**
     * Normalize and validate download URL
     * Ensures URL is properly formatted and validates scheme
     */
    private fun normalizeDownloadUrl(url: String): String? {
        if (url.isBlank()) {
            Log.e(TAG, "Download URL is empty")
            return null
        }
        
        try {
            val uri = Uri.parse(url)
            
            // Check if URL has a valid scheme
            if (uri.scheme == null || uri.scheme !in listOf("http", "https")) {
                Log.e(TAG, "Invalid URL scheme: ${uri.scheme}")
                return null
            }
            
            // Uri.parse() and Uri.Builder already handle encoding correctly
            // Just rebuild to ensure proper formatting
            val builder = uri.buildUpon()
            val normalizedUri = builder.build()
            
            Log.d(TAG, "Normalized URL: $normalizedUri (original: $url)")
            return normalizedUri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error normalizing URL: $url", e)
            return null
        }
    }
    
    /**
     * Format download speed
     */
    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> {
                String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024))
            }
            bytesPerSecond >= 1024 -> {
                String.format("%.2f KB/s", bytesPerSecond / 1024)
            }
            else -> {
                String.format("%.0f B/s", bytesPerSecond)
            }
        }
    }
    
    /**
     * Get error message from download reason with HTTP status code support
     */
    private fun getErrorMessage(reason: Int, httpStatus: Int = -1, serverMessage: String? = null, downloadUrl: String? = null): String {
        // If we have HTTP status code, include it in the error message
        val httpStatusText = if (httpStatus > 0) {
            when (httpStatus) {
                400 -> "HTTP 400 Bad Request"
                401 -> "HTTP 401 Unauthorized"
                403 -> "HTTP 403 Forbidden"
                404 -> "HTTP 404 Not Found"
                500 -> "HTTP 500 Server Error"
                else -> "HTTP $httpStatus"
            }
        } else {
            null
        }
        
        val baseMessage = when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot be resumed"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error occurred"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> {
                if (httpStatusText != null) {
                    "HTTP data error: $httpStatusText"
                } else {
                    "HTTP data error"
                }
            }
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> {
                if (httpStatusText != null) {
                    "Unhandled HTTP error: $httpStatusText"
                } else {
                    "Unhandled HTTP error"
                }
            }
            DownloadManager.ERROR_UNKNOWN -> "Unknown error occurred"
            else -> "Download failed (Error code: $reason)"
        }
        
        // Append HTTP status if available and not already included
        val message = if (httpStatusText != null && !baseMessage.contains(httpStatusText)) {
            "$baseMessage - $httpStatusText"
        } else {
            baseMessage
        }
        
        // Append server message if available
        var finalMessage = if (serverMessage != null && serverMessage.isNotBlank()) {
            "$message - $serverMessage"
        } else {
            message
        }
        
        // For HTTP 400 errors, add helpful message
        if (httpStatus == 400) {
            finalMessage += "\n\nPossible causes:\n"
            finalMessage += "- Invalid URL format or missing parameters\n"
            finalMessage += "- File path not found on server\n"
            if (downloadUrl != null) {
                finalMessage += "- URL: $downloadUrl"
            }
        }
        
        return finalMessage
    }
    
    /**
     * Cleanup old update files
     */
    companion object {
        fun cleanupOldUpdates(context: Context, keepLatest: Int = 1) {
            try {
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                
                downloadsDir?.listFiles()?.filter { 
                    it.name.startsWith("FastPay_Update") && it.name.endsWith(".apk")
                }?.sortedByDescending { it.lastModified() }
                    ?.drop(keepLatest)
                    ?.forEach { 
                        it.delete()
                        Log.d("UpdateDownloadManager", "Cleaned up old update: ${it.name}")
                    }
            } catch (e: Exception) {
                Log.e("UpdateDownloadManager", "Error cleaning up old updates", e)
            }
        }
        
        /**
         * Format file size for display
         */
        fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> {
                    String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                }
                bytes >= 1024 * 1024 -> {
                    String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                }
                bytes >= 1024 -> {
                    String.format("%.2f KB", bytes / 1024.0)
                }
                else -> {
                    "$bytes B"
                }
            }
        }
    }
}
