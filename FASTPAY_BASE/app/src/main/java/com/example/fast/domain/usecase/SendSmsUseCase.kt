package com.example.fast.domain.usecase

import com.example.fast.repository.SmsRepository
import com.example.fast.core.result.Result

/**
 * Use case for sending SMS messages
 *
 * Encapsulates the business logic for sending SMS:
 * - Validates input
 * - Sends SMS via repository
 * - Handles errors
 */
class SendSmsUseCase constructor(
    private val smsRepository: SmsRepository
) : UseCase<SendSmsUseCase.Params, Result<Unit>>() {

    data class Params(
        val phoneNumber: String,
        val message: String
    )

    override suspend fun execute(parameters: Params): Result<Unit> {
        return smsRepository.sendSms(parameters.phoneNumber, parameters.message)
    }
}
