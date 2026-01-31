package com.example.fast.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String? = null,
    val message: String? = null,
    val forceUpdate: Boolean = false
)

object VersionChecker {
    private const val TAG = "VersionChecker"
    private const val FIREBASE_TIMEOUT_MS = 10000L  // 10 seconds timeout for Firebase
    
    /**
     * Validate if the download URL from Firebase is valid
     * Checks for:
     * - Non-null and non-blank
     * - Valid URI format
     * - HTTP or HTTPS scheme
     */
    fun isValidDownloadUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        return try {
            val uri = Uri.parse(url.trim())
            val scheme = uri.scheme?.lowercase()
            scheme != null && (scheme == "http" || scheme == "https") && uri.host != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validate and clean download URL from Firebase
     * Returns null if URL is invalid, otherwise returns trimmed URL
     */
    private fun validateAndCleanUrl(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        val trimmedUrl = url.trim()
        return if (isValidDownloadUrl(trimmedUrl)) {
            trimmedUrl
        } else {
            null
        }
    }
    
    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }
    
    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }
    
    /**
     * Check version from Firebase (primary source)
     * 
     * Strategy:
     * - Firebase-only version check
     * - Supports force update via forceUpdate flag
     * - If Firebase check fails or times out, call onError (silent fail - app continues)
     * 
     * Firebase Path: fastpay/app/version
     * Expected structure:
     * {
     *   "versionCode": 10,
     *   "versionName": "2.0",
     *   "file": "https://...",
     *   "message": "Update available",
     *   "forceUpdate": true
     * }
     */
    fun checkVersion(
        context: Context,
        onVersionChecked: (AppVersionInfo?) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val firebaseTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var firebaseCompleted = false
        
        // Set timeout for Firebase (10 seconds)
        val timeoutRunnable = Runnable {
            if (!firebaseCompleted) {
                firebaseCompleted = true
                val error = Exception("Firebase version check timed out after ${FIREBASE_TIMEOUT_MS}ms")
                Log.w(TAG, "Firebase version check timed out")
                onError(error)
            }
        }
        firebaseTimeoutHandler.postDelayed(timeoutRunnable, FIREBASE_TIMEOUT_MS)
        
        try {
            Firebase.database.reference.child("fastpay/app/version")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (firebaseCompleted) {
                            Log.d(TAG, "Firebase response received but already timed out, ignoring")
                            return
                        }
                        
                        firebaseCompleted = true
                        firebaseTimeoutHandler.removeCallbacks(timeoutRunnable)
                        
                        if (!snapshot.exists()) {
                            Log.w(TAG, "Firebase version data not found")
                            onVersionChecked(null)
                            return
                        }
                        
                        try {
                            val requiredVersionCode = snapshot.child("versionCode").getValue(Int::class.java) ?: 0
                            val requiredVersionName = snapshot.child("versionName").getValue(String::class.java) ?: ""
                            val downloadUrl = snapshot.child("file").getValue(String::class.java)
                            val message = snapshot.child("message").getValue(String::class.java)
                            val forceUpdate = snapshot.child("forceUpdate").getValue(Boolean::class.java) ?: false
                            
                            val versionInfo = AppVersionInfo(
                                versionCode = requiredVersionCode,
                                versionName = requiredVersionName,
                                downloadUrl = downloadUrl,
                                message = message,
                                forceUpdate = forceUpdate
                            )
                            
                            Log.d(TAG, "✅ Version check successful from Firebase: versionCode=$requiredVersionCode, forceUpdate=$forceUpdate")
                            onVersionChecked(versionInfo)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing Firebase version data", e)
                            onError(e)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        if (firebaseCompleted) {
                            return
                        }
                        
                        firebaseCompleted = true
                        firebaseTimeoutHandler.removeCallbacks(timeoutRunnable)
                        
                        Log.w(TAG, "Firebase version check cancelled: ${error.message}")
                        onError(Exception("Firebase version check cancelled: ${error.message}"))
                    }
                })
        } catch (e: Exception) {
            if (firebaseCompleted) {
                return
            }
            firebaseCompleted = true
            firebaseTimeoutHandler.removeCallbacks(timeoutRunnable)
            Log.e(TAG, "Firebase version check failed", e)
            onError(e)
        }
    }
    
    fun isUpdateRequired(context: Context, requiredVersionCode: Int): Boolean {
        val currentVersionCode = getCurrentVersionCode(context)
        return currentVersionCode < requiredVersionCode
    }
}

