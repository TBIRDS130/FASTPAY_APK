package com.example.fast.domain.usecase

import com.example.fast.model.SmsConversation
import com.example.fast.repository.SmsRepository
import com.example.fast.util.Result
import javax.inject.Inject

/**
 * Use case for getting all SMS conversations
 * 
 * Encapsulates the business logic for fetching conversations:
 * - Gets conversations from repository
 * - Handles errors
 */
class GetAllConversationsUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) : NoParamsUseCase<Result<List<SmsConversation>>>() {
    
    override suspend fun execute(): Result<List<SmsConversation>> {
        return smsRepository.getAllConversations()
    }
}
