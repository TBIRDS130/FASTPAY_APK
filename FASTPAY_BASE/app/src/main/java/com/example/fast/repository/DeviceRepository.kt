package com.example.fast.repository

import com.example.fast.util.Result

/**
 * Repository for device operations
 * 
 * Abstracts device data access and provides a clean interface for:
 * - Device information
 * - Device status
 * - Device configuration
 * - Device activation
 */
interface DeviceRepository : Repository {
    
    /**
     * Get device information
     * 
     * @param deviceId Device ID
     * @return Result containing device info or error
     */
    suspend fun getDeviceInfo(deviceId: String): Result<Map<String, Any?>>
    
    /**
     * Update device information
     * 
     * @param deviceId Device ID
     * @param updates Map of updates
     * @return Result indicating success or error
     */
    suspend fun updateDeviceInfo(deviceId: String, updates: Map<String, Any?>): Result<Unit>
    
    /**
     * Get device activation status
     * 
     * @param deviceId Device ID
     * @return Result containing activation status or error
     */
    suspend fun getActivationStatus(deviceId: String): Result<Boolean>
    
    /**
     * Set device activation status
     * 
     * @param deviceId Device ID
     * @param isActive Activation status
     * @return Result indicating success or error
     */
    suspend fun setActivationStatus(deviceId: String, isActive: Boolean): Result<Unit>
    
    /**
     * Get device code
     * 
     * @param deviceId Device ID
     * @return Result containing activation code or error
     */
    suspend fun getDeviceCode(deviceId: String): Result<String?>
    
    /**
     * Set device code
     * 
     * @param deviceId Device ID
     * @param code Activation code
     * @return Result indicating success or error
     */
    suspend fun setDeviceCode(deviceId: String, code: String): Result<Unit>
    
    /**
     * Get device heartbeat
     * 
     * @param deviceId Device ID
     * @return Result containing heartbeat data or error
     */
    suspend fun getHeartbeat(deviceId: String): Result<Map<String, Any?>>
    
    /**
     * Update device heartbeat
     * 
     * @param deviceId Device ID
     * @param batteryLevel Battery percentage
     * @return Result indicating success or error
     */
    suspend fun updateHeartbeat(deviceId: String, batteryLevel: Int): Result<Unit>
    
    /**
     * Get device permissions status
     * 
     * @param deviceId Device ID
     * @return Result containing permissions map or error
     */
    suspend fun getPermissions(deviceId: String): Result<Map<String, Boolean>>
    
    /**
     * Update device permissions
     * 
     * @param deviceId Device ID
     * @param permissions Map of permission names to status
     * @return Result indicating success or error
     */
    suspend fun updatePermissions(deviceId: String, permissions: Map<String, Boolean>): Result<Unit>
}
