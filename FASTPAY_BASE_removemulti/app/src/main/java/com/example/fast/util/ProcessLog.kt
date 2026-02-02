package com.example.fast.util

import com.example.fast.BuildConfig

/**
 * Debug-only logging for process/flow start and stop.
 * Use a single tag so you can filter all process logs with:
 *   adb logcat -s ProcessLog:D
 *
 * Format: [START] processName  /  [STOP] processName
 * Covers: activity lifecycle, animations, input, validation, navigation.
 */
object ProcessLog {

    private const val TAG = "ProcessLog"

    @JvmStatic
    fun start(processName: String) {
        if (BuildConfig.DEBUG) {
            LogHelper.d(TAG, "[START] $processName")
        }
    }

    @JvmStatic
    fun stop(processName: String) {
        if (BuildConfig.DEBUG) {
            LogHelper.d(TAG, "[STOP] $processName")
        }
    }
}
