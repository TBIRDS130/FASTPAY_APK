package com.example.fast.repository.impl

import com.example.fast.model.exceptions.FirebaseException
import com.example.fast.repository.FirebaseRepository
import com.example.fast.util.FirebaseResultHelper
import com.example.fast.util.Logger
import com.example.fast.util.Result
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FirebaseRepository
 * 
 * Provides concrete implementation of Firebase operations using
 * FirebaseResultHelper and Result pattern.
 */
@Singleton
class FirebaseRepositoryImpl @Inject constructor() : FirebaseRepository {
    
    private val database: DatabaseReference
        get() = Firebase.database.reference
    
    override suspend fun <T> read(path: String, dataClass: Class<T>): Result<T?> {
        return try {
            val snapshot = database.child(path).get().await()
            val value = snapshot.getValue(dataClass)
            Result.success(value)
        } catch (e: Exception) {
            Logger.e("FirebaseRepository", e, "Failed to read from path: $path")
            Result.error(FirebaseException.fromException(e, "read"))
        }
    }
    
    override suspend fun write(path: String, data: Any): Result<Unit> {
        return FirebaseResultHelper.writeData(path, data)
    }
    
    override suspend fun update(path: String, updates: Map<String, Any?>): Result<Unit> {
        return FirebaseResultHelper.updateData(path, updates)
    }
    
    override suspend fun delete(path: String): Result<Unit> {
        return FirebaseResultHelper.deleteData(path)
    }
    
    override suspend fun exists(path: String): Result<Boolean> {
        return try {
            val snapshot = database.child(path).get().await()
            Result.success(snapshot.exists())
        } catch (e: Exception) {
            Logger.e("FirebaseRepository", e, "Failed to check existence of path: $path")
            Result.error(FirebaseException.fromException(e, "exists"))
        }
    }
    
    override suspend fun listen(path: String, callback: (Any?) -> Unit): Result<Any> {
        return try {
            val reference = database.child(path)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.value)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Logger.e("FirebaseRepository", "Listener cancelled for path: $path", 
                        FirebaseException(error.message, error.toException(), "listen"))
                }
            }
            reference.addValueEventListener(listener)
            Result.success(listener)
        } catch (e: Exception) {
            Logger.e("FirebaseRepository", e, "Failed to start listener for path: $path")
            Result.error(FirebaseException.fromException(e, "listen"))
        }
    }
    
    override suspend fun stopListening(listener: Any): Result<Unit> {
        return try {
            if (listener is ValueEventListener) {
                // Note: In a real implementation, we'd need to track the reference
                // For now, this is a placeholder
                Logger.w("FirebaseRepository", "stopListening called - implementation needed")
                Result.success(Unit)
            } else {
                Result.error(FirebaseException("Invalid listener type", null, "stopListening"))
            }
        } catch (e: Exception) {
            Logger.e("FirebaseRepository", e, "Failed to stop listener")
            Result.error(FirebaseException.fromException(e, "stopListening"))
        }
    }
}
