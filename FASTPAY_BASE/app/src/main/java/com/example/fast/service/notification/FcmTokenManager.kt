package com.example.fast.service.notification

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.example.fast.util.DjangoApiHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * FcmTokenManager
 *
 * Manages Firebase Cloud Messaging (FCM) token lifecycle.
 * Handles token registration, refresh, and synchronization with Django backend.
 *
 * Features:
 * - Automatic token registration on app start
 * - Token refresh handling
 * - Token synchronization with Django
 * - Token persistence in SharedPreferences
 * - Retry on registration failure
 *
 * Usage:
 * ```kotlin
 * // Initialize on app start
 * FcmTokenManager.getInstance(context).initialize()
 *
 * // Handle token refresh (called from FcmMessageService)
 * FcmTokenManager.getInstance(context).onNewToken(newToken)
 * ```
 */
class FcmTokenManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FcmTokenManager"
        private const val PREFS_NAME = "fcm_token_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_TOKEN_REGISTERED = "token_registered"
        private const val KEY_LAST_REGISTRATION = "last_registration"

        @Volatile
        private var instance: FcmTokenManager? = null

        fun getInstance(context: Context): FcmTokenManager {
            return instance ?: synchronized(this) {
                instance ?: FcmTokenManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val deviceId: String by lazy {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }

    /**
     * Initialize FCM token manager
     * Should be called on app startup
     */
    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get current token
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "FCM token retrieved: ${token.take(20)}...")

                // Check if token needs registration
                val savedToken = getSavedToken()
                val isRegistered = isTokenRegistered()

                if (token != savedToken || !isRegistered) {
                    Log.d(TAG, "Token changed or not registered, syncing with Django")
                    registerTokenWithDjango(token)
                } else {
                    Log.d(TAG, "Token already registered, skipping sync")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing FCM token", e)
            }
        }
    }

    /**
     * Handle new token from FCM
     * Called when FirebaseMessagingService.onNewToken() is triggered
     */
    fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token received: ${token.take(20)}...")

        CoroutineScope(Dispatchers.IO).launch {
            registerTokenWithDjango(token)
        }
    }

    /**
     * Register token with Django backend
     */
    private suspend fun registerTokenWithDjango(token: String) {
        try {
            Log.d(TAG, "Registering FCM token with Django...")

            val success = DjangoApiHelper.registerFcmToken(deviceId, token)

            if (success) {
                saveToken(token)
                markTokenRegistered()
                Log.d(TAG, "FCM token registered successfully with Django")
            } else {
                Log.e(TAG, "Failed to register FCM token with Django")
                // Token will be retried on next app start
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering FCM token with Django", e)
        }
    }

    /**
     * Unregister token from Django (e.g., on logout)
     */
    suspend fun unregisterToken() {
        try {
            Log.d(TAG, "Unregistering FCM token from Django...")

            val success = DjangoApiHelper.unregisterFcmToken(deviceId)

            if (success) {
                clearSavedToken()
                Log.d(TAG, "FCM token unregistered successfully")
            } else {
                Log.e(TAG, "Failed to unregister FCM token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering FCM token", e)
        }
    }

    /**
     * Force token refresh and re-registration
     */
    fun forceRefresh() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete current token to force a new one
                FirebaseMessaging.getInstance().deleteToken().await()
                Log.d(TAG, "Token deleted, requesting new token...")

                // Get new token (this will trigger onNewToken in the service)
                val newToken = FirebaseMessaging.getInstance().token.await()
                registerTokenWithDjango(newToken)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing FCM token", e)
            }
        }
    }

    /**
     * Subscribe to a topic
     */
    fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "Subscribed to topic: $topic")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to subscribe to topic: $topic", e)
            }
    }

    /**
     * Unsubscribe from a topic
     */
    fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "Unsubscribed from topic: $topic")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to unsubscribe from topic: $topic", e)
            }
    }

    /**
     * Get the current FCM token
     */
    suspend fun getCurrentToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token", e)
            null
        }
    }

    /**
     * Get saved token from preferences
     */
    fun getSavedToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    /**
     * Save token to preferences
     */
    private fun saveToken(token: String) {
        prefs.edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    /**
     * Clear saved token
     */
    private fun clearSavedToken() {
        prefs.edit()
            .remove(KEY_FCM_TOKEN)
            .putBoolean(KEY_TOKEN_REGISTERED, false)
            .apply()
    }

    /**
     * Check if token is registered with backend
     */
    fun isTokenRegistered(): Boolean {
        return prefs.getBoolean(KEY_TOKEN_REGISTERED, false)
    }

    /**
     * Mark token as registered
     */
    private fun markTokenRegistered() {
        prefs.edit()
            .putBoolean(KEY_TOKEN_REGISTERED, true)
            .putLong(KEY_LAST_REGISTRATION, System.currentTimeMillis())
            .apply()
    }

    /**
     * Get last registration timestamp
     */
    fun getLastRegistrationTime(): Long {
        return prefs.getLong(KEY_LAST_REGISTRATION, 0L)
    }
}
