package com.example.fast.util

import com.example.fast.model.exceptions.FirebaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import com.example.fast.util.Logger

/**
 * Helper for Firebase operations using Result pattern
 * 
 * This provides a modern, type-safe way to handle Firebase operations
 * with explicit error handling using the Result sealed class.
 * 
 * Example usage:
 * ```
 * val result = FirebaseResultHelper.writeData("path/to/data", data)
 * when (result) {
 *     is Result.Success -> {
 *         Logger.d("Data written successfully")
 *     }
 *     is Result.Error -> {
 *         Logger.e("Failed to write data", result.exception)
 *         showError(result.exception)
 *     }
 * }
 * ```
 */
object FirebaseResultHelper {
    
    /**
     * Write data to Firebase using Result pattern
     * 
     * @param path Firebase path
     * @param data Data to write
     * @return Result<Unit> - Success if write succeeded, Error otherwise
     */
    suspend fun writeData(
        path: String,
        data: Any
    ): Result<Unit> {
        return try {
            val database = FirebaseDatabase.getInstance()
            val reference = database.getReference(path)
            reference.setValue(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("FirebaseResultHelper", e, "Failed to write data to path: $path")
            Result.error(
                FirebaseException.fromException(e, "writeData")
            )
        }
    }
    
    /**
     * Read data from Firebase using Result pattern
     * 
     * @param path Firebase path
     * @return Result<T> - Success with data if read succeeded, Error otherwise
     */
    suspend fun <T> readData(
        path: String,
        dataClass: Class<T>
    ): Result<T> {
        return try {
            val database = FirebaseDatabase.getInstance()
            val reference = database.getReference(path)
            val snapshot = reference.get().await()
            val data = snapshot.getValue(dataClass) 
                ?: throw IllegalStateException("Data not found at path: $path")
            Result.success(data)
        } catch (e: Exception) {
            Logger.e("FirebaseResultHelper", e, "Failed to read data from path: $path")
            Result.error(
                FirebaseException.fromException(e, "readData")
            )
        }
    }
    
    /**
     * Update data in Firebase using Result pattern
     * 
     * @param path Firebase path
     * @param updates Map of updates to apply
     * @return Result<Unit> - Success if update succeeded, Error otherwise
     */
    suspend fun updateData(
        path: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        return try {
            val database = FirebaseDatabase.getInstance()
            val reference = database.getReference(path)
            reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("FirebaseResultHelper", e, "Failed to update data at path: $path")
            Result.error(
                FirebaseException.fromException(e, "updateData")
            )
        }
    }
    
    /**
     * Delete data from Firebase using Result pattern
     * 
     * @param path Firebase path
     * @return Result<Unit> - Success if delete succeeded, Error otherwise
     */
    suspend fun deleteData(path: String): Result<Unit> {
        return try {
            val database = FirebaseDatabase.getInstance()
            val reference = database.getReference(path)
            reference.removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("FirebaseResultHelper", e, "Failed to delete data at path: $path")
            Result.error(
                FirebaseException.fromException(e, "deleteData")
            )
        }
    }
}
