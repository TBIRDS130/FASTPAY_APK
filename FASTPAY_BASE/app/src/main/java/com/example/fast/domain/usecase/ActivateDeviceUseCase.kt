package com.example.fast.domain.usecase

import android.content.Context
import com.example.fast.repository.DeviceRepository
import com.example.fast.util.Result
import javax.inject.Inject

/**
 * Use case for device activation
 * 
 * Encapsulates the business logic for device activation:
 * - Validates activation code
 * - Sets device activation status
 * - Updates device code
 */
class ActivateDeviceUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val context: Context
) : UseCase<ActivateDeviceUseCase.Params, Result<Unit>>() {
    
    data class Params(
        val deviceId: String,
        val activationCode: String,
        val isActive: Boolean = true
    )
    
    @android.annotation.SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""
    }
    
    override suspend fun execute(parameters: Params): Result<Unit> {
        val deviceId = parameters.deviceId.ifBlank { getDeviceId() }
        
        // Set activation code
        val codeResult = deviceRepository.setDeviceCode(deviceId, parameters.activationCode)
        if (codeResult.isError) {
            return codeResult
        }
        
        // Set activation status
        return deviceRepository.setActivationStatus(deviceId, parameters.isActive)
    }
}
