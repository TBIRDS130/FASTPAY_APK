package com.example.fast.domain.usecase

import android.content.Context
import com.example.fast.repository.ContactRepository
import com.example.fast.util.Result
import javax.inject.Inject

/**
 * Use case for syncing contacts to Firebase
 * 
 * Encapsulates the business logic for contact synchronization:
 * - Gets device ID
 * - Syncs contacts via repository
 * - Handles errors
 */
class SyncContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val context: Context
) : NoParamsUseCase<Result<Unit>>() {
    
    @android.annotation.SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""
    }
    
    override suspend fun execute(): Result<Unit> {
        val deviceId = getDeviceId()
        return contactRepository.syncToFirebase(deviceId)
    }
}
