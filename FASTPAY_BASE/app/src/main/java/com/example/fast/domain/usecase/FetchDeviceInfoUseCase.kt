package com.example.fast.domain.usecase

import android.content.Context
import com.example.fast.repository.DeviceRepository
import com.example.fast.util.Result
import javax.inject.Inject

/**
 * Use case for fetching device information
 * 
 * Encapsulates the business logic for fetching device info:
 * - Gets device ID
 * - Fetches device info from repository
 * - Handles errors
 */
class FetchDeviceInfoUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val context: Context
) : NoParamsUseCase<Result<Map<String, Any?>>>() {
    
    @android.annotation.SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""
    }
    
    override suspend fun execute(): Result<Map<String, Any?>> {
        val deviceId = getDeviceId()
        return deviceRepository.getDeviceInfo(deviceId)
    }
}
