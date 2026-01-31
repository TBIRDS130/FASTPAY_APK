package com.example.fast.repository

import com.example.fast.util.Result

/**
 * Repository for Firebase operations
 * 
 * Abstracts Firebase Realtime Database and Storage operations.
 * Provides a clean interface for:
 * - Reading from Firebase
 * - Writing to Firebase
 * - Updating Firebase data
 * - Deleting from Firebase
 */
interface FirebaseRepository : Repository {
    
    /**
     * Read data from Firebase path
     * 
     * @param path Firebase path
     * @param dataClass Class type to deserialize to
     * @return Result containing data or error
     */
    suspend fun <T> read(path: String, dataClass: Class<T>): Result<T?>
    
    /**
     * Write data to Firebase path
     * 
     * @param path Firebase path
     * @param data Data to write
     * @return Result indicating success or error
     */
    suspend fun write(path: String, data: Any): Result<Unit>
    
    /**
     * Update data at Firebase path
     * 
     * @param path Firebase path
     * @param updates Map of updates to apply
     * @return Result indicating success or error
     */
    suspend fun update(path: String, updates: Map<String, Any?>): Result<Unit>
    
    /**
     * Delete data at Firebase path
     * 
     * @param path Firebase path
     * @return Result indicating success or error
     */
    suspend fun delete(path: String): Result<Unit>
    
    /**
     * Check if path exists in Firebase
     * 
     * @param path Firebase path
     * @return Result containing boolean or error
     */
    suspend fun exists(path: String): Result<Boolean>
    
    /**
     * Listen to real-time changes at a path
     * 
     * @param path Firebase path
     * @param callback Callback for data changes
     * @return Result containing listener reference or error
     */
    suspend fun listen(path: String, callback: (Any?) -> Unit): Result<Any>
    
    /**
     * Stop listening to a path
     * 
     * @param listener Listener reference from listen()
     * @return Result indicating success or error
     */
    suspend fun stopListening(listener: Any): Result<Unit>
}
