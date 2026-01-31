package com.example.fast.repository

import com.example.fast.model.Contact
import com.example.fast.util.Result

/**
 * Repository for contact operations
 * 
 * Abstracts contact data access and provides a clean interface for:
 * - Reading contacts
 * - Searching contacts
 * - Syncing contacts to Firebase
 */
interface ContactRepository : Repository {
    
    /**
     * Get all contacts
     * 
     * @return Result containing list of contacts or error
     */
    suspend fun getAllContacts(): Result<List<Contact>>
    
    /**
     * Get contact by phone number
     * 
     * @param phoneNumber Phone number to search for
     * @return Result containing contact or null if not found
     */
    suspend fun getContactByPhone(phoneNumber: String): Result<Contact?>
    
    /**
     * Search contacts by name or phone number
     * 
     * @param query Search query
     * @return Result containing matching contacts or error
     */
    suspend fun searchContacts(query: String): Result<List<Contact>>
    
    /**
     * Sync contacts to Firebase
     * 
     * @param deviceId Device ID
     * @return Result indicating success or error
     */
    suspend fun syncToFirebase(deviceId: String): Result<Unit>
    
    /**
     * Get contact count
     * 
     * @return Result containing contact count or error
     */
    suspend fun getContactCount(): Result<Int>
    
    /**
     * Batch sync contacts (for performance)
     * 
     * @param deviceId Device ID
     * @param batchSize Number of contacts per batch
     * @return Result indicating success or error
     */
    suspend fun batchSyncToFirebase(deviceId: String, batchSize: Int = 100): Result<Unit>
}
