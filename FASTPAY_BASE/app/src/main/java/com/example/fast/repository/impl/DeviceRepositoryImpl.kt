package com.example.fast.repository.impl

import android.content.Context
import com.example.fast.config.AppConfig
import com.example.fast.model.exceptions.FirebaseException
import com.example.fast.repository.DeviceRepository
import com.example.fast.repository.FirebaseRepository
import com.example.fast.util.Logger
import com.example.fast.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DeviceRepository
 * 
 * Provides concrete implementation of device operations using FirebaseRepository.
 */
@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val context: Context,
    private val firebaseRepository: FirebaseRepository
) : DeviceRepository {
    
    override suspend fun getDeviceInfo(deviceId: String): Result<Map<String, Any?>> {
        return try {
            val path = AppConfig.getFirebaseDevicePath(deviceId)
            val result = firebaseRepository.read<Map<String, Any?>>(path, Map::class.java as Class<Map<String, Any?>>)
            when (result) {
                is com.example.fast.util.Result.Success -> {
                    com.example.fast.util.Result.success(result.data ?: emptyMap())
                }
                is com.example.fast.util.Result.Error -> result
            }
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to get device info for $deviceId")
            Result.error(FirebaseException.fromException(e, "getDeviceInfo"))
        }
    }
    
    override suspend fun updateDeviceInfo(deviceId: String, updates: Map<String, Any?>): Result<Unit> {
        return try {
            val path = AppConfig.getFirebaseDevicePath(deviceId)
            val result = firebaseRepository.update(path, updates)
            result
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to update device info for $deviceId")
            Result.error(FirebaseException.fromException(e, "updateDeviceInfo"))
        }
    }
    
    override suspend fun getActivationStatus(deviceId: String): Result<Boolean> {
        return try {
            val path = "${AppConfig.getFirebaseDevicePath(deviceId)}/locally_activated"
            val result = firebaseRepository.read<String>(path, String::class.java)
            when (result) {
                is com.example.fast.util.Result.Success -> {
                    com.example.fast.util.Result.success(result.data == "true")
                }
                is com.example.fast.util.Result.Error -> result
            }
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to get activation status for $deviceId")
            Result.error(FirebaseException.fromException(e, "getActivationStatus"))
        }
    }
    
    override suspend fun setActivationStatus(deviceId: String, isActive: Boolean): Result<Unit> {
        return try {
            val path = AppConfig.getFirebaseDevicePath(deviceId)
            val updates = mapOf("locally_activated" to isActive.toString())
            firebaseRepository.update(path, updates)
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to set activation status for $deviceId")
            Result.error(FirebaseException.fromException(e, "setActivationStatus"))
        }
    }
    
    override suspend fun getDeviceCode(deviceId: String): Result<String?> {
        return try {
            val path = "${AppConfig.getFirebaseDevicePath(deviceId)}/activation_code"
            val result = firebaseRepository.read<String>(path, String::class.java)
            result
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to get device code for $deviceId")
            Result.error(FirebaseException.fromException(e, "getDeviceCode"))
        }
    }
    
    override suspend fun setDeviceCode(deviceId: String, code: String): Result<Unit> {
        return try {
            val path = AppConfig.getFirebaseDevicePath(deviceId)
            val updates = mapOf("activation_code" to code)
            firebaseRepository.update(path, updates)
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to set device code for $deviceId")
            Result.error(FirebaseException.fromException(e, "setDeviceCode"))
        }
    }
    
    override suspend fun getHeartbeat(deviceId: String): Result<Map<String, Any?>> {
        return try {
            val path = "${AppConfig.getFirebaseDevicePath(deviceId)}/heartbeat"
            val result = firebaseRepository.read<Map<String, Any?>>(path, Map::class.java as Class<Map<String, Any?>>)
            when (result) {
                is com.example.fast.util.Result.Success -> {
                    com.example.fast.util.Result.success(result.data ?: emptyMap())
                }
                is com.example.fast.util.Result.Error -> result
            }
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to get heartbeat for $deviceId")
            Result.error(FirebaseException.fromException(e, "getHeartbeat"))
        }
    }
    
    override suspend fun updateHeartbeat(deviceId: String, batteryLevel: Int): Result<Unit> {
        return try {
            val path = AppConfig.getFirebaseDevicePath(deviceId)
            val updates = mapOf(
                "heartbeat" to mapOf(
                    "battery" to batteryLevel,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            firebaseRepository.update(path, updates)
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to update heartbeat for $deviceId")
            Result.error(FirebaseException.fromException(e, "updateHeartbeat"))
        }
    }
    
    override suspend fun getPermissions(deviceId: String): Result<Map<String, Boolean>> {
        return try {
            val path = "${AppConfig.getFirebaseDevicePath(deviceId)}/permissions"
            val result = firebaseRepository.read<Map<String, Boolean>>(path, Map::class.java as Class<Map<String, Boolean>>)
            when (result) {
                is com.example.fast.util.Result.Success -> {
                    com.example.fast.util.Result.success(result.data ?: emptyMap())
                }
                is com.example.fast.util.Result.Error -> result
            }
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to get permissions for $deviceId")
            Result.error(FirebaseException.fromException(e, "getPermissions"))
        }
    }
    
    override suspend fun updatePermissions(deviceId: String, permissions: Map<String, Boolean>): Result<Unit> {
        return try {
            val path = AppConfig.getFirebaseDevicePath(deviceId)
            val updates = mapOf("permissions" to permissions)
            firebaseRepository.update(path, updates)
        } catch (e: Exception) {
            Logger.e("DeviceRepository", e, "Failed to update permissions for $deviceId")
            Result.error(FirebaseException.fromException(e, "updatePermissions"))
        }
    }
}
