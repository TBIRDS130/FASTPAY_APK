package com.example.fast.repository

import com.example.fast.model.SmsConversation
import com.example.fast.util.Result

/**
 * Repository for SMS operations
 * 
 * Abstracts SMS data access and provides a clean interface for:
 * - Reading SMS messages
 * - Sending SMS messages
 * - Managing SMS conversations
 * - Querying SMS data
 */
interface SmsRepository : Repository {
    
    /**
     * Get all SMS conversations
     * 
     * @return Result containing list of conversations or error
     */
    suspend fun getAllConversations(): Result<List<SmsConversation>>
    
    /**
     * Get conversation for a specific phone number
     * 
     * @param phoneNumber Phone number to get conversation for
     * @return Result containing conversation or error
     */
    suspend fun getConversation(phoneNumber: String): Result<SmsConversation?>
    
    /**
     * Send an SMS message
     * 
     * @param phoneNumber Recipient phone number
     * @param message Message text
     * @return Result indicating success or error
     */
    suspend fun sendSms(phoneNumber: String, message: String): Result<Unit>
    
    /**
     * Get messages for a conversation
     * 
     * @param phoneNumber Phone number
     * @param limit Maximum number of messages to retrieve
     * @return Result containing list of messages or error
     */
    suspend fun getMessages(phoneNumber: String, limit: Int = 100): Result<List<Any>>
    
    /**
     * Mark message as read
     * 
     * @param messageId Message ID
     * @return Result indicating success or error
     */
    suspend fun markAsRead(messageId: Long): Result<Unit>
    
    /**
     * Delete a message
     * 
     * @param messageId Message ID
     * @return Result indicating success or error
     */
    suspend fun deleteMessage(messageId: Long): Result<Unit>
    
    /**
     * Search messages by query
     * 
     * @param query Search query
     * @return Result containing matching messages or error
     */
    suspend fun searchMessages(query: String): Result<List<Any>>
    
    /**
     * Sync messages to Firebase
     * 
     * @param deviceId Device ID
     * @return Result indicating success or error
     */
    suspend fun syncToFirebase(deviceId: String): Result<Unit>
}
