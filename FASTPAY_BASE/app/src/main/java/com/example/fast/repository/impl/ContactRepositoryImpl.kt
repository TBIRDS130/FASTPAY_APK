package com.example.fast.repository.impl

import android.content.Context
import com.example.fast.config.AppConfig
import com.example.fast.model.Contact
import com.example.fast.model.exceptions.FirebaseException
import com.example.fast.repository.ContactRepository
import com.example.fast.util.ContactHelperOptimized
import com.example.fast.util.FirebaseResultHelper
import com.example.fast.util.FirebaseSyncHelper
import com.example.fast.util.Logger
import com.example.fast.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ContactRepository
 * 
 * Provides concrete implementation of contact operations using
 * ContactHelperOptimized and Firebase.
 */
@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val context: Context
) : ContactRepository {
    
    override suspend fun getAllContacts(): Result<List<Contact>> {
        return try {
            val contacts = withContext(Dispatchers.IO) {
                ContactHelperOptimized.getAllContacts(context, forceRefresh = false)
            }
            Result.success(contacts)
        } catch (e: Exception) {
            Logger.e("ContactRepository", e, "Failed to get all contacts")
            Result.error(FirebaseException.fromException(e, "getAllContacts"))
        }
    }
    
    override suspend fun getContactByPhone(phoneNumber: String): Result<Contact?> {
        return try {
            val contacts = withContext(Dispatchers.IO) {
                ContactHelperOptimized.getAllContacts(context, forceRefresh = false)
            }
            val normalizedPhone = normalizePhoneNumber(phoneNumber)
            val contact = contacts.find { 
                normalizePhoneNumber(it.phoneNumber) == normalizedPhone
            }
            Result.success(contact)
        } catch (e: Exception) {
            Logger.e("ContactRepository", e, "Failed to get contact by phone: $phoneNumber")
            Result.error(FirebaseException.fromException(e, "getContactByPhone"))
        }
    }
    
    override suspend fun searchContacts(query: String): Result<List<Contact>> {
        return try {
            val contacts = withContext(Dispatchers.IO) {
                ContactHelperOptimized.getAllContacts(context, forceRefresh = false)
            }
            val normalizedQuery = query.lowercase()
            val filtered = contacts.filter { contact ->
                contact.name.lowercase().contains(normalizedQuery) ||
                contact.phoneNumber.lowercase().contains(normalizedQuery) ||
                contact.emails.any { it.address.lowercase().contains(normalizedQuery) }
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Logger.e("ContactRepository", e, "Failed to search contacts")
            Result.error(FirebaseException.fromException(e, "searchContacts"))
        }
    }
    
    override suspend fun syncToFirebase(deviceId: String): Result<Unit> {
        return try {
            val contacts = withContext(Dispatchers.IO) {
                ContactHelperOptimized.getAllContacts(context, forceRefresh = true)
            }
            
            withContext(Dispatchers.Main) {
                FirebaseSyncHelper.syncCompleteContacts(
                    context = context,
                    contacts = contacts,
                    onSuccess = { count ->
                        Logger.d("ContactRepository", "Synced $count contacts to Firebase")
                    },
                    onFailure = { error ->
                        Logger.e("ContactRepository", "Failed to sync contacts: $error")
                    }
                )
            }
            
            // Wait a bit for async operation (in real implementation, use callback or Flow)
            kotlinx.coroutines.delay(1000)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ContactRepository", e, "Failed to sync contacts to Firebase")
            Result.error(FirebaseException.fromException(e, "syncToFirebase"))
        }
    }
    
    override suspend fun getContactCount(): Result<Int> {
        return try {
            val contacts = withContext(Dispatchers.IO) {
                ContactHelperOptimized.getAllContacts(context, forceRefresh = false)
            }
            Result.success(contacts.size)
        } catch (e: Exception) {
            Logger.e("ContactRepository", e, "Failed to get contact count")
            Result.error(FirebaseException.fromException(e, "getContactCount"))
        }
    }
    
    override suspend fun batchSyncToFirebase(deviceId: String, batchSize: Int): Result<Unit> {
        return try {
            val contacts = withContext(Dispatchers.IO) {
                ContactHelperOptimized.getAllContacts(context, forceRefresh = true)
            }
            
            // Split into batches
            val batches = contacts.chunked(batchSize)
            
            batches.forEachIndexed { index, batch ->
                withContext(Dispatchers.Main) {
                    FirebaseSyncHelper.syncCompleteContacts(
                        context = context,
                        contacts = batch,
                        onSuccess = { count ->
                            Logger.d("ContactRepository", "Synced batch ${index + 1}/${batches.size}: $count contacts")
                        },
                        onFailure = { error ->
                            Logger.e("ContactRepository", "Failed to sync batch ${index + 1}: $error")
                        }
                    )
                }
                // Small delay between batches
                kotlinx.coroutines.delay(500)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ContactRepository", e, "Failed to batch sync contacts")
            Result.error(FirebaseException.fromException(e, "batchSyncToFirebase"))
        }
    }
    
    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "")
    }
}
