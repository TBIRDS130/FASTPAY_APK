package com.example.fast.util

import android.util.Log
import com.example.fast.BuildConfig

/**
 * Safe logging utility that prevents debug logs from appearing in production builds.
 * 
 * Security Benefits:
 * - Prevents sensitive information from being logged in production
 * - Reduces log noise in production builds
 * - Improves performance by skipping log operations in release builds
 * 
 * Usage:
 * - Replace Log.d() with LogHelper.d()
 * - Replace Log.e() with LogHelper.e() for errors (always logged)
 * - Replace Log.w() with LogHelper.w() for warnings (always logged)
 * - Replace Log.i() with LogHelper.i() for info (always logged)
 */
object LogHelper {
    
    /**
     * Log debug messages - only in debug builds
     * @param tag Log tag (typically class name)
     * @param message Log message
     */
    @JvmStatic
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Log debug messages with throwable - only in debug builds
     * @param tag Log tag
     * @param message Log message
     * @param throwable Exception/error to log
     */
    @JvmStatic
    fun d(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message, throwable)
        }
    }
    
    /**
     * Log error messages - always logged (even in production)
     * Errors are important for production debugging via crash reporting
     * @param tag Log tag
     * @param message Log message
     */
    @JvmStatic
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
    
    /**
     * Log error messages with throwable - always logged
     * @param tag Log tag
     * @param message Log message
     * @param throwable Exception/error to log
     */
    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
    
    /**
     * Log warning messages - always logged
     * Warnings are important for production monitoring
     * @param tag Log tag
     * @param message Log message
     */
    @JvmStatic
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    /**
     * Log warning messages with throwable - always logged
     * @param tag Log tag
     * @param message Log message
     * @param throwable Exception/error to log
     */
    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }
    
    /**
     * Log info messages - always logged
     * Info messages are important for production monitoring
     * @param tag Log tag
     * @param message Log message
     */
    @JvmStatic
    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
    
    /**
     * Log info messages with throwable - always logged
     * @param tag Log tag
     * @param message Log message
     * @param throwable Exception/error to log
     */
    @JvmStatic
    fun i(tag: String, message: String, throwable: Throwable?) {
        Log.i(tag, message, throwable)
    }
    
    /**
     * Log verbose messages - only in debug builds
     * @param tag Log tag
     * @param message Log message
     */
    @JvmStatic
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }
    
    /**
     * Log verbose messages with throwable - only in debug builds
     * @param tag Log tag
     * @param message Log message
     * @param throwable Exception/error to log
     */
    @JvmStatic
    fun v(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message, throwable)
        }
    }
}
