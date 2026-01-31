package com.example.fast.util

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * PermissionChain - Robust permission request chain manager
 * 
 * Handles sequential permission requests with proper state management,
 * error handling, and crash prevention.
 */
class PermissionChain(
    private val activity: Activity,
    private val requestCode: Int = 100
) {
    companion object {
        private const val TAG = "PermissionChain"
        private const val REQUEST_DELAY_MS = 300L // Delay between permission requests
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val ACTIVITY_CHECK_DELAY_MS = 500L
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Chain state
    private var isActive = false
    private var currentIndex = 0
    private var missingPermissions = emptyList<String>()
    private var retryCount = 0
    private var isPaused = false
    
    // Callbacks
    private var onAllGranted: (() -> Unit)? = null
    private var onPermissionDenied: ((permission: String) -> Unit)? = null
    private var onChainError: ((error: String) -> Unit)? = null
    private var onChainComplete: (() -> Unit)? = null // Called when chain finishes processing all permissions (even if not all granted)
    
    // Pending runnables for cleanup
    private val pendingRunnables = mutableListOf<Runnable>()
    
    /**
     * Start the permission chain with specific permissions
     */
    fun start(
        permissionsToRequest: List<String>? = null, // If null, uses all missing permissions
        onAllGranted: () -> Unit,
        onPermissionDenied: ((permission: String) -> Unit)? = null,
        onChainError: ((error: String) -> Unit)? = null,
        onChainComplete: (() -> Unit)? = null
    ) {
        if (isActive) {
            Log.w(TAG, "Chain already active, ignoring start request")
            return
        }
        
        if (!isActivityValid()) {
            Log.e(TAG, "Cannot start chain - activity invalid")
            onChainError?.invoke("Activity is invalid")
            return
        }
        
        this.onAllGranted = onAllGranted
        this.onPermissionDenied = onPermissionDenied
        this.onChainError = onChainError
        this.onChainComplete = onChainComplete
        
        // Get missing permissions - use provided list or default to all missing
        val allMissing = if (permissionsToRequest != null) {
            permissionsToRequest.filter { permission ->
                ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
            }
        } else {
            PermissionManager.getMissingRuntimePermissions(activity)
        }
        
        missingPermissions = allMissing.filter { permission ->
            !PermissionManager.isPermanentlyDenied(activity, permission)
        }
        
        if (missingPermissions.isEmpty()) {
            if (allMissing.isEmpty()) {
                Log.d(TAG, "All permissions already granted")
                isActive = false
                onAllGranted.invoke()
            } else {
                Log.w(TAG, "All missing permissions are permanently denied (${allMissing.size} total)")
                isActive = false
                onChainComplete?.invoke() // Notify that chain can't proceed
            }
            return
        }
        
        Log.d(TAG, "Starting permission chain with ${missingPermissions.size} requestable permissions (${allMissing.size - missingPermissions.size} permanently denied): $missingPermissions")
        isActive = true
        currentIndex = 0
        retryCount = 0
        
        // Start chain after a small delay to ensure activity is ready
        requestNextPermission(0)
    }
    
    /**
     * Handle permission result from onRequestPermissionsResult
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        // Check if this is our request
        if (requestCode != this.requestCode || !isActive) {
            return false
        }
        
        if (!isActivityValid()) {
            Log.e(TAG, "Activity invalid when handling permission result")
            stop()
            return false
        }
        
        if (permissions.isEmpty() || grantResults.isEmpty()) {
            Log.e(TAG, "Invalid permission result arrays")
            handleError("Invalid permission result")
            return false
        }
        
        val permission = permissions[0]
        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Permission result: $permission = ${if (granted) "GRANTED" else "DENIED"}")
        
        if (granted) {
            // Permission granted - move to next
            retryCount = 0 // Reset retry count on success
            currentIndex++
            requestNextPermission(REQUEST_DELAY_MS)
        } else {
            // Permission denied
            onPermissionDenied?.invoke(permission)
            
            // Check if permanently denied
            val isPermanentlyDenied = PermissionManager.isPermanentlyDenied(activity, permission)
            if (isPermanentlyDenied) {
                Log.w(TAG, "Permission permanently denied: $permission - will skip in future cycles")
            }
            
            // Continue chain anyway (don't block on denied permissions)
            // If permanently denied, it will be skipped in the next cycle via requestNextPermission check
            currentIndex++
            requestNextPermission(REQUEST_DELAY_MS)
        }
        
        return true
    }
    
    /**
     * Request next permission in chain
     */
    private fun requestNextPermission(delayMs: Long = 0) {
        if (!isActive || isPaused) {
            return
        }
        
        // Cancel any pending requests
        cancelPendingRequests()
        
        val requestRunnable = Runnable {
            if (!isActive || !isActivityValid()) {
                Log.w(TAG, "Cannot request permission - chain inactive or activity invalid")
                stop()
                return@Runnable
            }
            
            // Re-check missing permissions (they might have changed)
            // Filter out permanently denied permissions to avoid getting stuck
            val allMissing = PermissionManager.getMissingRuntimePermissions(activity)
            val currentMissing = allMissing.filter { permission ->
                !PermissionManager.isPermanentlyDenied(activity, permission)
            }
            
            if (currentMissing.isEmpty()) {
                // All requestable permissions either granted or permanently denied
                if (allMissing.isEmpty()) {
                    // All permissions granted
                    Log.d(TAG, "All permissions granted - completing chain")
                    isActive = false
                    onAllGranted?.invoke()
                } else {
                    // Some permissions permanently denied - chain complete but not all granted
                    Log.w(TAG, "All requestable permissions processed - ${allMissing.size} permanently denied")
                    isActive = false
                    onChainComplete?.invoke()
                }
                return@Runnable
            }
            
            // Check if we've processed all requestable permissions
            // Reset index if we've gone past the filtered list
            if (currentIndex >= currentMissing.size) {
                currentIndex = 0 // Reset to start of filtered list
            }
            
            // Get next permission to request
            val permission = currentMissing[currentIndex]
            
            // Double-check if this permission is still not permanently denied (might have changed)
            val isPermanentlyDenied = PermissionManager.isPermanentlyDenied(activity, permission)
            
            if (isPermanentlyDenied) {
                // Permission is permanently denied - skip it and move to next
                Log.w(TAG, "Permission permanently denied (skipping): $permission")
                onPermissionDenied?.invoke(permission)
                currentIndex++
                // Recursively request next permission
                requestNextPermission(REQUEST_DELAY_MS)
                return@Runnable
            }
            
            Log.d(TAG, "Requesting permission ${currentIndex + 1}/${currentMissing.size}: $permission")
            
            try {
                ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
                // Don't increment index here - wait for result in handlePermissionResult
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permission: $permission", e)
                handleError("Failed to request permission: ${e.message}")
            }
        }
        
        pendingRunnables.add(requestRunnable)
        handler.postDelayed(requestRunnable, delayMs)
    }
    
    /**
     * Pause the chain (useful when activity is paused)
     */
    fun pause() {
        if (isActive) {
            Log.d(TAG, "Pausing permission chain")
            isPaused = true
        }
    }
    
    /**
     * Resume the chain (useful when activity is resumed)
     */
    fun resume() {
        if (isActive && isPaused) {
            Log.d(TAG, "Resuming permission chain")
            isPaused = false
            // Re-check permissions and continue
            val currentMissing = PermissionManager.getMissingRuntimePermissions(activity)
            if (currentMissing.isEmpty()) {
                isActive = false
                onAllGranted?.invoke()
            } else {
                // Continue from current position
                requestNextPermission(ACTIVITY_CHECK_DELAY_MS)
            }
        }
    }
    
    /**
     * Stop the chain
     */
    fun stop() {
        if (isActive) {
            Log.d(TAG, "Stopping permission chain")
            isActive = false
            isPaused = false
            cancelPendingRequests()
        }
    }
    
    /**
     * Check if chain is active
     */
    fun isChainActive(): Boolean = isActive
    
    /**
     * Cancel all pending requests
     */
    private fun cancelPendingRequests() {
        pendingRunnables.forEach { handler.removeCallbacks(it) }
        pendingRunnables.clear()
    }
    
    /**
     * Check if activity is valid for operations
     */
    private fun isActivityValid(): Boolean {
        return try {
            !activity.isFinishing && !activity.isDestroyed
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Handle chain error
     */
    private fun handleError(error: String) {
        Log.e(TAG, "Chain error: $error")
        stop()
        onChainError?.invoke(error)
    }
    
    /**
     * Cleanup - call when activity is being destroyed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up permission chain")
        stop()
        onAllGranted = null
        onPermissionDenied = null
        onChainError = null
    }
}
