package com.example.fast.util

import android.util.Log
import com.example.fast.BuildConfig
import timber.log.Timber

/**
 * Unified logging utility for FastPay application
 * 
 * This class provides a single interface for logging throughout the application.
 * It wraps Timber and provides consistent logging behavior across debug and release builds.
 * 
 * Usage:
 * ```
 * Logger.d("Debug message")
 * Logger.i("Info message")
 * Logger.w("Warning message")
 * Logger.e("Error message", exception)
 * ```
 * 
 * In debug builds: All logs are shown
 * In release builds: Only WARN and ERROR logs are shown (DEBUG and INFO are removed)
 */
object Logger {
    
    private var isInitialized = false
    
    /**
     * Initialize the logger
     * Must be called in Application.onCreate() before any logging
     */
    fun initialize() {
        if (isInitialized) {
            return
        }
        
        if (BuildConfig.DEBUG) {
            // Debug build: Show all logs with full stack traces
            Timber.plant(Timber.DebugTree())
        } else {
            // Release build: Only log WARN and ERROR, strip DEBUG and INFO
            Timber.plant(ReleaseTree())
        }
        
        isInitialized = true
    }
    
    /**
     * Log a debug message
     * Only shown in debug builds
     */
    fun d(message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.d(message, *args)
        } else {
            // Fallback to Android Log if not initialized
            Log.d("FastPay", String.format(message, *args))
        }
    }
    
    /**
     * Log a debug message with tag
     * Only shown in debug builds
     */
    fun d(tag: String, message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.tag(tag).d(message, *args)
        } else {
            Log.d(tag, String.format(message, *args))
        }
    }
    
    /**
     * Log an info message
     * Only shown in debug builds
     */
    fun i(message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.i(message, *args)
        } else {
            Log.i("FastPay", String.format(message, *args))
        }
    }
    
    /**
     * Log an info message with tag
     * Only shown in debug builds
     */
    fun i(tag: String, message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.tag(tag).i(message, *args)
        } else {
            Log.i(tag, String.format(message, *args))
        }
    }
    
    /**
     * Log a warning message
     * Shown in both debug and release builds
     */
    fun w(message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.w(message, *args)
        } else {
            Log.w("FastPay", String.format(message, *args))
        }
    }
    
    /**
     * Log a warning message with tag
     * Shown in both debug and release builds
     */
    fun w(tag: String, message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.tag(tag).w(message, *args)
        } else {
            Log.w(tag, String.format(message, *args))
        }
    }
    
    /**
     * Log a warning message with exception
     * Shown in both debug and release builds
     */
    fun w(throwable: Throwable, message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.w(throwable, message, *args)
        } else {
            Log.w("FastPay", String.format(message, *args), throwable)
        }
    }
    
    /**
     * Log an error message
     * Shown in both debug and release builds
     */
    fun e(message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.e(message, *args)
        } else {
            Log.e("FastPay", String.format(message, *args))
        }
    }
    
    /**
     * Log an error message with tag
     * Shown in both debug and release builds
     */
    fun e(tag: String, message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.tag(tag).e(message, *args)
        } else {
            Log.e(tag, String.format(message, *args))
        }
    }
    
    /**
     * Log an error message with exception
     * Shown in both debug and release builds
     */
    fun e(throwable: Throwable, message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.e(throwable, message, *args)
        } else {
            Log.e("FastPay", String.format(message, *args), throwable)
        }
    }
    
    /**
     * Log an error message with tag and exception
     * Shown in both debug and release builds
     */
    fun e(tag: String, throwable: Throwable, message: String, vararg args: Any?) {
        if (isInitialized) {
            Timber.tag(tag).e(throwable, message, *args)
        } else {
            Log.e(tag, String.format(message, *args), throwable)
        }
    }
    
    /**
     * Custom Tree for release builds that filters out DEBUG and INFO logs
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Only log WARN and ERROR in release builds
            if (priority == Log.WARN || priority == Log.ERROR) {
                // Use Android Log directly for release builds (simpler, no formatting overhead)
                when (priority) {
                    Log.WARN -> Log.w(tag ?: "FastPay", message, t)
                    Log.ERROR -> Log.e(tag ?: "FastPay", message, t)
                }
            }
            // DEBUG and INFO are silently ignored in release builds
        }
    }
}
