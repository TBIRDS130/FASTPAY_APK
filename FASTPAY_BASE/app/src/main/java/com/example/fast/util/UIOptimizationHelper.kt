package com.example.fast.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

/**
 * UI Optimization Helper
 * Provides utilities for optimizing UI updates and reducing unnecessary redraws
 */
object UIOptimizationHelper {
    
    private val debounceHandlers = ConcurrentHashMap<String, Handler>()
    private val debounceRunnables = ConcurrentHashMap<String, Runnable>()
    
    /**
     * Debounce function calls to prevent excessive UI updates
     * 
     * @param key Unique identifier for the debounce operation
     * @param delayMs Delay in milliseconds before executing the action
     * @param action The action to execute after debounce delay
     * 
     * Example:
     * UIOptimizationHelper.debounce("updateMessages", 100) {
     *     updateMessageDisplay()
     * }
     */
    fun debounce(key: String, delayMs: Long, action: () -> Unit) {
        // Remove existing handler and runnable for this key
        val existingRunnable = debounceRunnables[key]
        if (existingRunnable != null) {
            debounceHandlers[key]?.removeCallbacks(existingRunnable)
        }
        
        // Create new runnable
        val runnable = Runnable { action() }
        debounceRunnables[key] = runnable
        
        // Create or get handler
        val handler = debounceHandlers.getOrPut(key) {
            Handler(Looper.getMainLooper())
        }
        
        // Post delayed
        handler.postDelayed(runnable, delayMs)
    }
    
    /**
     * Cancel a pending debounced action
     */
    fun cancelDebounce(key: String) {
        val existingRunnable = debounceRunnables[key]
        if (existingRunnable != null) {
            debounceHandlers[key]?.removeCallbacks(existingRunnable)
        }
        debounceRunnables.remove(key)
    }
    
    /**
     * Clear all debounce operations (useful for cleanup)
     */
    fun clearAllDebounces() {
        debounceHandlers.values.forEach { it.removeCallbacksAndMessages(null) }
        debounceHandlers.clear()
        debounceRunnables.clear()
    }
    
    /**
     * Batch multiple UI updates into a single operation
     * 
     * @param key Unique identifier for the batch operation
     * @param delayMs Delay in milliseconds before executing batched actions
     * @param action The action to batch
     */
    fun batch(key: String, delayMs: Long, action: () -> Unit) {
        debounce(key, delayMs, action)
    }
    
    /**
     * Throttle function calls to limit execution frequency
     * 
     * @param key Unique identifier for the throttle operation
     * @param delayMs Minimum delay between executions
     * @param action The action to throttle
     */
    fun throttle(key: String, delayMs: Long, action: () -> Unit) {
        val handler = debounceHandlers.getOrPut(key) {
            Handler(Looper.getMainLooper())
        }
        
        val runnable = debounceRunnables[key]
        if (runnable == null) {
            // First call - execute immediately
            val newRunnable = Runnable {
                action()
                debounceRunnables.remove(key)
            }
            debounceRunnables[key] = newRunnable
            handler.post(newRunnable)
            
            // Schedule removal after delay
            handler.postDelayed({
                debounceRunnables.remove(key)
            }, delayMs)
        }
        // Subsequent calls within delay are ignored
    }
}
