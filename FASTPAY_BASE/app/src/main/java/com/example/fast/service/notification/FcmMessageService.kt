package com.example.fast.service.notification

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.fast.service.ContactSmsSyncService
import com.example.fast.ui.card.CardCoordinator
import com.example.fast.ui.card.RemoteCardHandler
import com.example.fast.util.sync.SyncStateManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FcmMessageService
 *
 * Firebase Cloud Messaging service for handling push notifications from Django.
 * Enables real-time updates without constant polling.
 *
 * Supported Message Types:
 * - sync: Trigger data synchronization
 * - command: Execute a remote command
 * - notification: Display a notification
 * - config: Update app configuration
 *
 * Message Format (from Django):
 * ```json
 * {
 *   "data": {
 *     "type": "sync",
 *     "sync_type": "messages",  // messages, contacts, notifications, all
 *     "priority": "high"
 *   }
 * }
 * ```
 *
 * Note: Must be registered in AndroidManifest.xml:
 * ```xml
 * <service
 *     android:name=".service.notification.FcmMessageService"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *     </intent-filter>
 * </service>
 * ```
 */
class FcmMessageService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmMessageService"

        // Message types
        const val TYPE_SYNC = "sync"
        const val TYPE_COMMAND = "command"
        const val TYPE_NOTIFICATION = "notification"
        const val TYPE_CONFIG = "config"
        const val TYPE_CARD = "card"  // Show a MultipurposeCard

        // Broadcast action for overlay card display
        const val ACTION_SHOW_CARD = "com.example.fast.SHOW_CARD"

        // Sync types
        const val SYNC_MESSAGES = "messages"
        const val SYNC_CONTACTS = "contacts"
        const val SYNC_NOTIFICATIONS = "notifications"
        const val SYNC_ALL = "all"

        // Data keys
        const val KEY_TYPE = "type"
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_COMMAND = "command"
        const val KEY_VALUE = "value"
        const val KEY_PRIORITY = "priority"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(20)}...")

        // Notify token manager
        FcmTokenManager.getInstance(applicationContext).onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Handle notification payload (if app is in foreground)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Message notification: ${notification.title} - ${notification.body}")
            handleNotificationMessage(notification)
        }
    }

    /**
     * Handle data message from Django
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data[KEY_TYPE] ?: return

        when (type) {
            TYPE_SYNC -> handleSyncMessage(data)
            TYPE_COMMAND -> handleCommandMessage(data)
            TYPE_NOTIFICATION -> handleNotificationPayload(data)
            TYPE_CONFIG -> handleConfigMessage(data)
            TYPE_CARD -> handleCardMessage(data)
            else -> Log.w(TAG, "Unknown message type: $type")
        }
    }

    /**
     * Handle sync trigger from Django
     * Initiates data synchronization based on sync_type
     */
    private fun handleSyncMessage(data: Map<String, String>) {
        val syncType = data[KEY_SYNC_TYPE] ?: SYNC_ALL
        val priority = data[KEY_PRIORITY] ?: "normal"

        Log.d(TAG, "Sync message received: type=$syncType, priority=$priority")

        CoroutineScope(Dispatchers.IO).launch {
            when (syncType) {
                SYNC_MESSAGES -> triggerMessageSync()
                SYNC_CONTACTS -> triggerContactSync()
                SYNC_NOTIFICATIONS -> triggerNotificationSync()
                SYNC_ALL -> triggerFullSync()
                else -> {
                    Log.w(TAG, "Unknown sync type: $syncType, triggering full sync")
                    triggerFullSync()
                }
            }
        }
    }

    /**
     * Handle command from Django
     * Commands are processed by PersistentForegroundService via Firebase
     */
    private fun handleCommandMessage(data: Map<String, String>) {
        val command = data[KEY_COMMAND] ?: return
        val value = data[KEY_VALUE]

        Log.d(TAG, "Command received via FCM: $command = $value")

        // Commands are typically handled via Firebase Realtime Database
        // This is a backup channel for critical commands
        // The command will be written to Firebase and picked up by the existing listener

        // Note: If needed, direct command execution can be implemented here
        // by broadcasting an intent to PersistentForegroundService
    }

    /**
     * Handle notification payload from data message
     */
    private fun handleNotificationPayload(data: Map<String, String>) {
        val title = data[KEY_TITLE] ?: "FastPay"
        val body = data[KEY_BODY] ?: return

        Log.d(TAG, "Notification payload: $title - $body")

        // Show notification using existing notification infrastructure
        // This allows Django to send custom notifications
    }

    /**
     * Handle config update from Django
     */
    private fun handleConfigMessage(data: Map<String, String>) {
        Log.d(TAG, "Config message received: $data")

        // Handle configuration updates from Django
        // e.g., update sync intervals, feature flags, etc.
    }

    /**
     * Handle card display message from Django
     *
     * Shows a MultipurposeCard either as an overlay on ActivatedActivity
     * or as a fullscreen activity based on display_mode.
     *
     * Expected data format:
     * ```json
     * {
     *   "type": "card",
     *   "card_type": "message|permission|update|webview|default_sms|...",
     *   "display_mode": "overlay|fullscreen",
     *   "title": "Card Title",
     *   "body": "Card body text",
     *   "html": "<html>...</html>",
     *   "primary_button": "OK",
     *   "secondary_button": "Cancel",
     *   "auto_dismiss_ms": "5000",
     *   "permissions": "sms,contacts"
     * }
     * ```
     */
    private fun handleCardMessage(data: Map<String, String>) {
        val displayMode = data[RemoteCardHandler.KEY_DISPLAY_MODE] ?: RemoteCardHandler.DISPLAY_MODE_FULLSCREEN
        val cardType = data[RemoteCardHandler.KEY_CARD_TYPE] ?: RemoteCardHandler.CARD_TYPE_MESSAGE

        Log.d(TAG, "Card message received: type=$cardType, mode=$displayMode")

        when (displayMode) {
            RemoteCardHandler.DISPLAY_MODE_OVERLAY -> {
                // Broadcast to ActivatedActivity to show as overlay
                val broadcastIntent = Intent(ACTION_SHOW_CARD).apply {
                    data.forEach { (key, value) -> putExtra(key, value) }
                }
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcastIntent)
                Log.d(TAG, "Sent overlay card broadcast")
            }

            else -> {
                // Single entry: fullscreen card via CardCoordinator
                try {
                    CardCoordinator.show(applicationContext, data, asOverlay = false)
                    Log.d(TAG, "Launched card fullscreen via CardCoordinator")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch card", e)
                }
            }
        }
    }

    /**
     * Handle notification payload (when app is in foreground)
     */
    private fun handleNotificationMessage(notification: RemoteMessage.Notification) {
        // When app is in foreground, we receive the notification here
        // and can decide how to display it
        Log.d(TAG, "Foreground notification: ${notification.title}")
    }

    /**
     * Trigger message sync
     */
    private fun triggerMessageSync() {
        Log.d(TAG, "Triggering message sync from FCM")

        val syncManager = SyncStateManager.getInstance(applicationContext)
        syncManager.markSyncStarted(SyncStateManager.SyncType.MESSAGES)

        // Start sync service for messages only
        val intent = Intent(applicationContext, ContactSmsSyncService::class.java).apply {
            putExtra("mode", "SMS")
        }
        try {
            applicationContext.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sync service", e)
            syncManager.markSyncFailed(SyncStateManager.SyncType.MESSAGES, e.message)
        }
    }

    /**
     * Trigger contact sync
     */
    private fun triggerContactSync() {
        Log.d(TAG, "Triggering contact sync from FCM")

        val syncManager = SyncStateManager.getInstance(applicationContext)
        syncManager.markSyncStarted(SyncStateManager.SyncType.CONTACTS)

        // Start sync service for contacts only
        val intent = Intent(applicationContext, ContactSmsSyncService::class.java).apply {
            putExtra("mode", "CONTACTS")
        }
        try {
            applicationContext.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sync service", e)
            syncManager.markSyncFailed(SyncStateManager.SyncType.CONTACTS, e.message)
        }
    }

    /**
     * Trigger notification sync
     */
    private fun triggerNotificationSync() {
        Log.d(TAG, "Triggering notification sync from FCM")

        val syncManager = SyncStateManager.getInstance(applicationContext)
        syncManager.markSyncStarted(SyncStateManager.SyncType.NOTIFICATIONS)

        // Flush notification batch processor
        com.example.fast.util.NotificationBatchProcessor.flush(applicationContext)

        syncManager.markSyncComplete(SyncStateManager.SyncType.NOTIFICATIONS)
    }

    /**
     * Trigger full sync (all data types)
     */
    private fun triggerFullSync() {
        Log.d(TAG, "Triggering full sync from FCM")

        val syncManager = SyncStateManager.getInstance(applicationContext)
        syncManager.markSyncStarted(SyncStateManager.SyncType.ALL)

        // Start full sync service
        val intent = Intent(applicationContext, ContactSmsSyncService::class.java).apply {
            putExtra("mode", "ALL")
        }
        try {
            applicationContext.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sync service", e)
            syncManager.markSyncFailed(SyncStateManager.SyncType.ALL, e.message)
        }
    }
}
