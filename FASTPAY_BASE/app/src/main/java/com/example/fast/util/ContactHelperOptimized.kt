package com.example.fast.util

import android.content.Context
import android.util.Log
import com.example.fast.model.Contact

/**
 * ContactHelperOptimized
 * 
 * Optimized wrapper around ContactHelper with caching support.
 * Reduces redundant database queries by caching contact data.
 */
object ContactHelperOptimized {
    private const val TAG = "ContactHelperOptimized"
    
    // In-memory cache
    private var cachedContacts: List<Contact>? = null
    private var cacheTimestamp: Long = 0
    private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    
    /**
     * Get all contacts with optional caching
     * 
     * @param context Application context
     * @param forceRefresh If true, bypasses cache and fetches fresh data
     * @return List of contacts
     */
    fun getAllContacts(context: Context, forceRefresh: Boolean = false): List<Contact> {
        val now = System.currentTimeMillis()
        
        // Check if cache is valid and not forcing refresh
        if (!forceRefresh && cachedContacts != null && (now - cacheTimestamp) < CACHE_VALIDITY_MS) {
            Log.d(TAG, "Returning cached contacts: ${cachedContacts?.size} contacts")
            return cachedContacts!!
        }
        
        // Fetch fresh contacts
        Log.d(TAG, "Fetching contacts from device${if (forceRefresh) " (forced refresh)" else ""}...")
        val contacts = ContactHelper.getAllContacts(context)
        
        // Update cache
        cachedContacts = contacts
        cacheTimestamp = now
        
        Log.d(TAG, "Fetched ${contacts.size} contacts and updated cache")
        return contacts
    }
    
    /**
     * Clear the contact cache
     * Useful when contacts might have changed
     */
    fun clearCache() {
        cachedContacts = null
        cacheTimestamp = 0
        Log.d(TAG, "Contact cache cleared")
    }
    
    /**
     * Check if cache is valid
     */
    fun isCacheValid(): Boolean {
        val now = System.currentTimeMillis()
        return cachedContacts != null && (now - cacheTimestamp) < CACHE_VALIDITY_MS
    }
}

