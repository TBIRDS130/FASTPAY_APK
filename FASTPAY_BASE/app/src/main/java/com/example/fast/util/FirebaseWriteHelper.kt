package com.example.fast.util

import com.google.firebase.Firebase
import com.google.firebase.database.database

/**
 * FirebaseWriteHelper
 * 
 * Unified utility for writing data to Firebase Realtime Database.
 * Supports both lightweight heartbeat path and main device path writes
 * without compromising functionality.
 * 
 * Features:
 * - Consistent error handling and logging
 * - Support for both setValue() and updateChildren() operations
 * - Optional success/failure callbacks
 * - Tag-based logging for better debugging
 */
object FirebaseWriteHelper {
    
    /**
     * Write mode for Firebase operations
     */
    enum class WriteMode {
        /** Replace entire node with new data (setValue) */
        SET,
        /** Update only specified fields (updateChildren) */
        UPDATE
    }
    
    /**
     * Write data to Firebase at the specified path
     * 
     * @param path Firebase path (e.g., "hertbit/{deviceId}" or "fastpay/{deviceId}")
     * @param data Data to write (Map, String, Number, Boolean, etc.)
     * @param mode Write mode: SET (setValue) or UPDATE (updateChildren)
     * @param tag Log tag for debugging (default: "FirebaseWriteHelper")
     * @param onSuccess Optional callback on successful write
     * @param onFailure Optional callback on failed write
     */
    fun write(
        path: String,
        data: Any,
        mode: WriteMode = WriteMode.SET,
        tag: String = "FirebaseWriteHelper",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        try {
            // Track Firebase write call
            val method = when (mode) {
                WriteMode.SET -> "setValue"
                WriteMode.UPDATE -> "updateChildren"
            }
            FirebaseCallTracker.trackWrite(path, method, data)
            
            val ref = Firebase.database.reference.child(path)
            
            val task = when (mode) {
                WriteMode.SET -> ref.setValue(data)
                WriteMode.UPDATE -> {
                    // For UPDATE mode, data must be a Map
                    if (data is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        ref.updateChildren(data as Map<String, Any>)
                    } else {
                        LogHelper.e(tag, "UPDATE mode requires Map data, got: ${data::class.simpleName}")
                        FirebaseCallTracker.updateCallResponse(path = path, success = false, error = "UPDATE mode requires Map data")
                        onFailure?.invoke(IllegalArgumentException("UPDATE mode requires Map data"))
                        return
                    }
                }
            }
            
            task.addOnSuccessListener {
                LogHelper.d(tag, "Successfully wrote to path: $path (mode: $mode)")
                FirebaseCallTracker.updateCallResponse(path = path, success = true)
                onSuccess?.invoke()
            }.addOnFailureListener { e ->
                LogHelper.e(tag, "Failed to write to path: $path (mode: $mode)", e)
                FirebaseCallTracker.updateCallResponse(path = path, success = false, error = e.message)
                onFailure?.invoke(e)
            }
        } catch (e: Exception) {
            LogHelper.e(tag, "Error preparing write to path: $path", e)
            FirebaseCallTracker.updateCallResponse(path = path, success = false, error = e.message)
            onFailure?.invoke(e)
        }
    }
    
    /**
     * Write data to Firebase using setValue (replaces entire node)
     * 
     * Convenience method for SET mode writes
     */
    fun setValue(
        path: String,
        data: Any,
        tag: String = "FirebaseWriteHelper",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        write(path, data, WriteMode.SET, tag, onSuccess, onFailure)
    }
    
    /**
     * Update data in Firebase using updateChildren (updates only specified fields)
     * 
     * Convenience method for UPDATE mode writes
     * 
     * @param path Firebase path
     * @param updates Map of field paths to values to update
     */
    fun updateChildren(
        path: String,
        updates: Map<String, Any>,
        tag: String = "FirebaseWriteHelper",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        write(path, updates, WriteMode.UPDATE, tag, onSuccess, onFailure)
    }
    
    /**
     * Write heartbeat data to both lightweight path and main device path
     * 
     * This method handles the dual-path write strategy:
     * - Lightweight heartbeat path: Written every heartbeat interval
     * - Main device path: Written less frequently for backward compatibility
     * 
     * @param deviceId Device ID
     * @param timestamp Current timestamp
     * @param batteryPercentage Battery percentage (optional, only written if changed significantly)
     * @param lastBatteryPercentage Last battery percentage for change detection
     * @param shouldUpdateMain Whether to update main device path (based on time interval)
     * @param tag Log tag for debugging
     * @param onHeartbeatSuccess Optional callback when heartbeat path write succeeds
     * @param onMainPathSuccess Optional callback when main path write succeeds
     * @return Updated battery percentage (if written) or previous value
     */
    fun writeHeartbeat(
        deviceId: String,
        timestamp: Long,
        batteryPercentage: Int,
        lastBatteryPercentage: Int,
        shouldUpdateMain: Boolean,
        tag: String = "FirebaseWriteHelper",
        onHeartbeatSuccess: (() -> Unit)? = null,
        onMainPathSuccess: (() -> Unit)? = null
    ): Int {
        // Prepare lightweight heartbeat data
        val heartbeatPath = "hertbit/$deviceId"
        val heartbeatData = mutableMapOf<String, Any>("t" to timestamp)
        
        // Only include battery if it changed significantly (±1%) or first time
        val shouldWriteBattery = batteryPercentage >= 0 && (
            lastBatteryPercentage < 0 || 
            kotlin.math.abs(batteryPercentage - lastBatteryPercentage) >= 1
        )
        
        var updatedBatteryPercentage = lastBatteryPercentage
        if (shouldWriteBattery) {
            heartbeatData["b"] = batteryPercentage
            updatedBatteryPercentage = batteryPercentage
        }
        
        // Write to lightweight heartbeat path (always)
        setValue(
            path = heartbeatPath,
            data = heartbeatData,
            tag = tag,
            onSuccess = {
                LogHelper.d(tag, "Heartbeat updated (lightweight path)")
                onHeartbeatSuccess?.invoke()
            },
            onFailure = { e ->
                LogHelper.e(tag, "Failed to update heartbeat path", e)
            }
        )
        
        // Write to main device path (if needed for backward compatibility)
        if (shouldUpdateMain) {
            val devicePath = "fastpay/$deviceId"
            val deviceUpdates = mutableMapOf<String, Any>("lastSeen" to timestamp)
            if (batteryPercentage >= 0) {
                deviceUpdates["batteryPercentage"] = batteryPercentage
            }
            
            updateChildren(
                path = devicePath,
                updates = deviceUpdates,
                tag = tag,
                onSuccess = {
                    LogHelper.d(tag, "Updated main device path (backward compatibility)")
                    onMainPathSuccess?.invoke()
                },
                onFailure = { e ->
                    LogHelper.e(tag, "Failed to update main device path", e)
                }
            )
        }
        
        return updatedBatteryPercentage
    }
}
