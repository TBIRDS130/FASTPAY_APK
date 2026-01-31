package com.example.fast.util

import android.content.Context
import android.os.BatteryManager

/**
 * BatteryHelper
 * 
 * Utility class to get battery information without requiring additional permissions.
 * BatteryManager is a system service that doesn't require special permissions.
 */
object BatteryHelper {
    
    /**
     * Get current battery percentage (0-100)
     * 
     * @param context Application context
     * @return Battery percentage (0-100), or -1 if unable to get battery info
     */
    fun getBatteryPercentage(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryManager?.let {
                val batteryLevel = it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (batteryLevel >= 0 && batteryLevel <= 100) {
                    batteryLevel
                } else {
                    -1
                }
            } ?: -1
        } catch (e: Exception) {
            android.util.Log.e("BatteryHelper", "Error getting battery percentage", e)
            -1
        }
    }
    
    /**
     * Get battery charging status
     * 
     * @param context Application context
     * @return true if device is charging, false otherwise
     */
    fun isCharging(context: Context): Boolean {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryManager?.isCharging ?: false
        } catch (e: Exception) {
            android.util.Log.e("BatteryHelper", "Error checking charging status", e)
            false
        }
    }
}
