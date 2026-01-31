package com.example.fast.util

import android.util.Log
import com.example.fast.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await

/**
 * Firebase Storage Helper
 * Provides utilities for working with Firebase Storage URLs
 */
object FirebaseStorageHelper {
    private const val TAG = "FirebaseStorageHelper"
    
    /**
     * Check if a URL is a Firebase Storage path (not a direct HTTP/HTTPS URL)
     * Firebase Storage paths start with "gs://" or are relative paths like "app/apk/FastPay-v2.7.apk"
     */
    fun isFirebaseStoragePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        
        val trimmedPath = path.trim()
        
        // Check if it's a gs:// URL (Firebase Storage URL)
        if (trimmedPath.startsWith("gs://")) {
            return true
        }
        
        // Check if it's a Firebase Storage path (doesn't start with http:// or https://)
        if (!trimmedPath.startsWith("http://") && !trimmedPath.startsWith("https://")) {
            // If it contains common Firebase Storage path patterns, assume it's a storage path
            return trimmedPath.contains("/") || trimmedPath.endsWith(".apk")
        }
        
        return false
    }
    
    /**
     * Get download URL from Firebase Storage path
     * 
     * @param storagePath Firebase Storage path (e.g., "app/apk/FastPay-v2.7.apk" or "gs://bucket/app/apk/FastPay-v2.7.apk")
     * @param onSuccess Callback with download URL
     * @param onError Callback with error message
     */
    fun getDownloadUrl(
        storagePath: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Remove gs:// prefix if present
            val bucket = BuildConfig.FIREBASE_STORAGE_BUCKET.ifBlank { "fastpay-9d825.appspot.com" }
            val withoutScheme = storagePath.removePrefix("gs://")
            val cleanPath = withoutScheme.removePrefix("$bucket/")
            
            // Get Firebase Storage reference
            val storageRef = Firebase.storage.reference.child(cleanPath)
            
            // Get download URL
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                val downloadUrl = uri.toString()
                Log.d(TAG, "✅ Firebase Storage download URL obtained: $downloadUrl")
                onSuccess(downloadUrl)
            }.addOnFailureListener { exception ->
                val errorMessage = "Failed to get Firebase Storage download URL: ${exception.message}"
                Log.e(TAG, errorMessage, exception)
                onError(errorMessage)
            }
        } catch (e: Exception) {
            val errorMessage = "Error processing Firebase Storage path: ${e.message}"
            Log.e(TAG, errorMessage, e)
            onError(errorMessage)
        }
    }
    
    /**
     * Get download URL from Firebase Storage path (synchronous with coroutines)
     * Note: This requires coroutine context
     */
    suspend fun getDownloadUrlSuspend(storagePath: String): String? {
        return try {
            // Remove gs:// prefix if present
            val bucket = BuildConfig.FIREBASE_STORAGE_BUCKET.ifBlank { "fastpay-9d825.appspot.com" }
            val withoutScheme = storagePath.removePrefix("gs://")
            val cleanPath = withoutScheme.removePrefix("$bucket/")
            
            // Get Firebase Storage reference
            val storageRef = Firebase.storage.reference.child(cleanPath)
            
            // Get download URL
            val uri = storageRef.downloadUrl.await()
            val downloadUrl = uri.toString()
            Log.d(TAG, "✅ Firebase Storage download URL obtained: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Firebase Storage download URL: ${e.message}", e)
            null
        }
    }
    
    /**
     * Resolve download URL - handles both direct URLs and Firebase Storage paths
     * 
     * @param pathOrUrl Either a direct HTTP/HTTPS URL or a Firebase Storage path
     * @param onSuccess Callback with resolved download URL
     * @param onError Callback with error message
     */
    fun resolveDownloadUrl(
        pathOrUrl: String?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (pathOrUrl.isNullOrBlank()) {
            onError("Download path/URL is empty")
            return
        }
        
        val trimmed = pathOrUrl.trim()
        
        // If it's already a direct HTTP/HTTPS URL, use it directly
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Log.d(TAG, "Using direct URL: $trimmed")
            onSuccess(trimmed)
            return
        }
        
        // Otherwise, treat it as Firebase Storage path and get download URL
        Log.d(TAG, "Resolving Firebase Storage path: $trimmed")
        getDownloadUrl(trimmed, onSuccess, onError)
    }
}

