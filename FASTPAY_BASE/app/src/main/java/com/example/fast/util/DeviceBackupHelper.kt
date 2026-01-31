package com.example.fast.util

import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

/**
 * DeviceBackupHelper
 * 
 * Utility for backing up device data when code conflicts occur during activation.
 * Handles complete device data backup and restore operations.
 */
object DeviceBackupHelper {
    
    private const val TAG = "DeviceBackupHelper"
    
    /**
     * Backup complete device data to Firebase backup path
     * Used when device is being re-activated with a different code
     * 
     * @param deviceId Device ID to backup
     * @param oldCode Previous activation code
     * @param mode Activation mode: "testing" or "running"
     * @param onComplete Callback with success status
     */
    fun backupDeviceData(deviceId: String, oldCode: String, mode: String, onComplete: (Boolean) -> Unit) {
        try {
            if (deviceId.isBlank()) {
                LogHelper.e(TAG, "Device ID is blank, cannot backup")
                onComplete(false)
                return
            }
            
            if (oldCode.isBlank()) {
                LogHelper.e(TAG, "Old code is blank, cannot backup")
                onComplete(false)
                return
            }
            
            val devicePath = AppConfig.getFirebaseDevicePath(deviceId)
            val deviceRef = Firebase.database.reference.child(devicePath)
            
            LogHelper.d(TAG, "Starting backup for device: $deviceId with old code: $oldCode")
            
            // Read complete device data
            deviceRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        LogHelper.w(TAG, "Device data does not exist, nothing to backup")
                        onComplete(true) // Not an error - device doesn't exist yet
                        return
                    }
                    
                    try {
                        // Get complete snapshot as Map
                        val deviceData = snapshot.getValue() as? Map<*, *>
                        
                        if (deviceData == null || deviceData.isEmpty()) {
                            LogHelper.w(TAG, "Device data is empty, nothing to backup")
                            onComplete(true) // Not an error - no data to backup
                            return
                        }
                        
                        // Convert to mutable map for Firebase
                        val backupData = mutableMapOf<String, Any>()
                        deviceData.forEach { (key, value) ->
                            if (key is String && value != null) {
                                backupData[key] = value
                            }
                        }
                        
                        // Add backup metadata
                        backupData["_backupMetadata"] = mapOf(
                            "originalDeviceId" to deviceId,
                            "oldCode" to oldCode,
                            "backupTimestamp" to System.currentTimeMillis(),
                            "backupReason" to "code_conflict",
                            "mode" to mode
                        )
                        
                        // Save to backup path: device-backups/{mode}-{code}
                        val backupPath = AppConfig.getFirebaseDeviceBackupPath(oldCode, mode)
                        
                        Firebase.database.reference.child(backupPath)
                            .setValue(backupData)
                            .addOnSuccessListener {
                                LogHelper.d(TAG, "✅ Device data backed up successfully: $backupPath")
                                onComplete(true)
                            }
                            .addOnFailureListener { e ->
                                LogHelper.e(TAG, "❌ Failed to backup device data to: $backupPath", e)
                                onComplete(false)
                            }
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "❌ Error processing device data for backup", e)
                        onComplete(false)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e(TAG, "❌ Backup cancelled: ${error.message}")
                    onComplete(false)
                }
            })
        } catch (e: Exception) {
            LogHelper.e(TAG, "❌ Error initiating backup", e)
            onComplete(false)
        }
    }
    
    /**
     * Restore device data from backup (if needed for recovery)
     * 
     * @param backupPath Firebase path to backup data
     * @param deviceId Target device ID to restore to
     * @param onComplete Callback with success status
     */
    fun restoreDeviceData(backupPath: String, deviceId: String, onComplete: (Boolean) -> Unit) {
        try {
            if (backupPath.isBlank()) {
                LogHelper.e(TAG, "Backup path is blank, cannot restore")
                onComplete(false)
                return
            }
            
            if (deviceId.isBlank()) {
                LogHelper.e(TAG, "Device ID is blank, cannot restore")
                onComplete(false)
                return
            }
            
            val backupRef = Firebase.database.reference.child(backupPath)
            
            LogHelper.d(TAG, "Starting restore from backup: $backupPath to device: $deviceId")
            
            // Read backup data
            backupRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        LogHelper.e(TAG, "Backup data does not exist at: $backupPath")
                        onComplete(false)
                        return
                    }
                    
                    try {
                        // Get backup data as Map
                        val backupData = snapshot.getValue() as? Map<*, *>
                        
                        if (backupData == null || backupData.isEmpty()) {
                            LogHelper.e(TAG, "Backup data is empty")
                            onComplete(false)
                            return
                        }
                        
                        // Convert to mutable map, excluding backup metadata
                        val deviceData = mutableMapOf<String, Any>()
                        backupData.forEach { (key, value) ->
                            if (key is String && value != null && key != "_backupMetadata") {
                                deviceData[key] = value
                            }
                        }
                        
                        // Restore to device path
                        val devicePath = AppConfig.getFirebaseDevicePath(deviceId)
                        
                        Firebase.database.reference.child(devicePath)
                            .setValue(deviceData)
                            .addOnSuccessListener {
                                LogHelper.d(TAG, "✅ Device data restored successfully: $devicePath")
                                onComplete(true)
                            }
                            .addOnFailureListener { e ->
                                LogHelper.e(TAG, "❌ Failed to restore device data to: $devicePath", e)
                                onComplete(false)
                            }
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "❌ Error processing backup data for restore", e)
                        onComplete(false)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e(TAG, "❌ Restore cancelled: ${error.message}")
                    onComplete(false)
                }
            })
        } catch (e: Exception) {
            LogHelper.e(TAG, "❌ Error initiating restore", e)
            onComplete(false)
        }
    }
}
