package com.example.fast.util.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * SyncStateManager
 *
 * Manages sync state for incremental synchronization.
 * Tracks last sync timestamps per data type to avoid re-syncing unchanged data.
 *
 * Features:
 * - Track last sync timestamp per data type
 * - Track sync status (success/failure/in-progress)
 * - Support for partial sync (only sync data after last timestamp)
 * - Persistent storage via SharedPreferences
 * - Conflict detection based on timestamps
 *
 * Usage:
 * ```kotlin
 * val syncManager = SyncStateManager.getInstance(context)
 *
 * // Before sync - get last timestamp
 * val lastSync = syncManager.getLastSyncTimestamp(SyncType.MESSAGES)
 *
 * // Query only new data since lastSync
 * val newMessages = repository.getMessagesSince(lastSync)
 *
 * // After successful sync
 * syncManager.markSyncComplete(SyncType.MESSAGES)
 * ```
 */
class SyncStateManager private constructor(context: Context) {

    companion object {
        private const val TAG = "SyncStateManager"
        private const val PREFS_NAME = "sync_state_prefs"

        // Preference keys
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_"
        private const val KEY_SYNC_STATUS_PREFIX = "sync_status_"
        private const val KEY_SYNC_COUNT_PREFIX = "sync_count_"
        private const val KEY_LAST_ERROR_PREFIX = "last_error_"

        @Volatile
        private var instance: SyncStateManager? = null

        fun getInstance(context: Context): SyncStateManager {
            return instance ?: synchronized(this) {
                instance ?: SyncStateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Data types that can be synced
     */
    enum class SyncType {
        MESSAGES,
        CONTACTS,
        NOTIFICATIONS,
        DEVICE_INFO,
        PERMISSIONS,
        COMMANDS,
        ALL // Special type for full sync
    }

    /**
     * Sync status
     */
    enum class SyncStatus {
        IDLE,           // No sync in progress
        IN_PROGRESS,    // Sync currently running
        SUCCESS,        // Last sync succeeded
        FAILED,         // Last sync failed
        PARTIAL         // Last sync partially completed
    }

    /**
     * Sync state for a specific data type
     */
    data class SyncState(
        val type: SyncType,
        val lastSyncTimestamp: Long,
        val status: SyncStatus,
        val syncCount: Int,
        val lastError: String?
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get last sync timestamp for a data type
     *
     * @param type Data type
     * @return Timestamp of last successful sync, or 0 if never synced
     */
    fun getLastSyncTimestamp(type: SyncType): Long {
        return prefs.getLong("${KEY_LAST_SYNC_PREFIX}${type.name}", 0L)
    }

    /**
     * Get current sync status for a data type
     */
    fun getSyncStatus(type: SyncType): SyncStatus {
        val statusOrdinal = prefs.getInt("${KEY_SYNC_STATUS_PREFIX}${type.name}", SyncStatus.IDLE.ordinal)
        return SyncStatus.values().getOrElse(statusOrdinal) { SyncStatus.IDLE }
    }

    /**
     * Get total sync count for a data type
     */
    fun getSyncCount(type: SyncType): Int {
        return prefs.getInt("${KEY_SYNC_COUNT_PREFIX}${type.name}", 0)
    }

    /**
     * Get last error message for a data type
     */
    fun getLastError(type: SyncType): String? {
        return prefs.getString("${KEY_LAST_ERROR_PREFIX}${type.name}", null)
    }

    /**
     * Get full sync state for a data type
     */
    fun getSyncState(type: SyncType): SyncState {
        return SyncState(
            type = type,
            lastSyncTimestamp = getLastSyncTimestamp(type),
            status = getSyncStatus(type),
            syncCount = getSyncCount(type),
            lastError = getLastError(type)
        )
    }

    /**
     * Mark sync as started
     */
    fun markSyncStarted(type: SyncType) {
        prefs.edit()
            .putInt("${KEY_SYNC_STATUS_PREFIX}${type.name}", SyncStatus.IN_PROGRESS.ordinal)
            .apply()

        Log.d(TAG, "Sync started: ${type.name}")
    }

    /**
     * Mark sync as complete (successful)
     *
     * @param type Data type
     * @param timestamp Timestamp to record (default: current time)
     */
    fun markSyncComplete(type: SyncType, timestamp: Long = System.currentTimeMillis()) {
        val currentCount = getSyncCount(type)

        prefs.edit()
            .putLong("${KEY_LAST_SYNC_PREFIX}${type.name}", timestamp)
            .putInt("${KEY_SYNC_STATUS_PREFIX}${type.name}", SyncStatus.SUCCESS.ordinal)
            .putInt("${KEY_SYNC_COUNT_PREFIX}${type.name}", currentCount + 1)
            .remove("${KEY_LAST_ERROR_PREFIX}${type.name}")
            .apply()

        Log.d(TAG, "Sync complete: ${type.name} (timestamp: $timestamp, count: ${currentCount + 1})")
    }

    /**
     * Mark sync as failed
     *
     * @param type Data type
     * @param error Error message
     */
    fun markSyncFailed(type: SyncType, error: String?) {
        prefs.edit()
            .putInt("${KEY_SYNC_STATUS_PREFIX}${type.name}", SyncStatus.FAILED.ordinal)
            .putString("${KEY_LAST_ERROR_PREFIX}${type.name}", error)
            .apply()

        Log.e(TAG, "Sync failed: ${type.name} - $error")
    }

    /**
     * Mark sync as partially complete
     *
     * @param type Data type
     * @param timestamp Timestamp up to which data was synced
     */
    fun markSyncPartial(type: SyncType, timestamp: Long) {
        prefs.edit()
            .putLong("${KEY_LAST_SYNC_PREFIX}${type.name}", timestamp)
            .putInt("${KEY_SYNC_STATUS_PREFIX}${type.name}", SyncStatus.PARTIAL.ordinal)
            .apply()

        Log.d(TAG, "Sync partial: ${type.name} (timestamp: $timestamp)")
    }

    /**
     * Reset sync state for a data type
     */
    fun resetSyncState(type: SyncType) {
        prefs.edit()
            .remove("${KEY_LAST_SYNC_PREFIX}${type.name}")
            .remove("${KEY_SYNC_STATUS_PREFIX}${type.name}")
            .remove("${KEY_SYNC_COUNT_PREFIX}${type.name}")
            .remove("${KEY_LAST_ERROR_PREFIX}${type.name}")
            .apply()

        Log.d(TAG, "Sync state reset: ${type.name}")
    }

    /**
     * Reset all sync states
     */
    fun resetAllSyncStates() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All sync states reset")
    }

    /**
     * Check if a data type needs sync (never synced or sync failed)
     */
    fun needsSync(type: SyncType): Boolean {
        val lastSync = getLastSyncTimestamp(type)
        val status = getSyncStatus(type)
        return lastSync == 0L || status == SyncStatus.FAILED || status == SyncStatus.PARTIAL
    }

    /**
     * Check if a full sync is needed (any type needs sync)
     */
    fun needsFullSync(): Boolean {
        return SyncType.values()
            .filter { it != SyncType.ALL }
            .any { needsSync(it) }
    }

    /**
     * Get all sync states
     */
    fun getAllSyncStates(): List<SyncState> {
        return SyncType.values()
            .filter { it != SyncType.ALL }
            .map { getSyncState(it) }
    }

    /**
     * Check if data has changed since last sync (based on external timestamp)
     *
     * @param type Data type
     * @param latestDataTimestamp Timestamp of latest data from source
     * @return true if there's new data to sync
     */
    fun hasNewData(type: SyncType, latestDataTimestamp: Long): Boolean {
        val lastSync = getLastSyncTimestamp(type)
        return latestDataTimestamp > lastSync
    }

    /**
     * Get time since last sync in milliseconds
     */
    fun getTimeSinceLastSync(type: SyncType): Long {
        val lastSync = getLastSyncTimestamp(type)
        return if (lastSync > 0) System.currentTimeMillis() - lastSync else Long.MAX_VALUE
    }

    /**
     * Check if sync is stale (older than specified duration)
     *
     * @param type Data type
     * @param maxAgeMs Maximum age in milliseconds
     * @return true if sync is stale or never done
     */
    fun isSyncStale(type: SyncType, maxAgeMs: Long): Boolean {
        return getTimeSinceLastSync(type) > maxAgeMs
    }

    /**
     * Log all sync states (for debugging)
     */
    fun logAllSyncStates() {
        Log.d(TAG, "=== Sync States ===")
        getAllSyncStates().forEach { state ->
            val timeSince = if (state.lastSyncTimestamp > 0) {
                "${(System.currentTimeMillis() - state.lastSyncTimestamp) / 1000}s ago"
            } else {
                "never"
            }
            Log.d(TAG, "  ${state.type}: ${state.status} ($timeSince, count: ${state.syncCount})")
        }
        Log.d(TAG, "===================")
    }
}

/**
 * Extension function to run a sync operation with state tracking
 */
suspend inline fun <T> SyncStateManager.withSyncTracking(
    type: SyncStateManager.SyncType,
    crossinline operation: suspend () -> T
): Result<T> {
    markSyncStarted(type)
    return try {
        val result = operation()
        markSyncComplete(type)
        Result.success(result)
    } catch (e: Exception) {
        markSyncFailed(type, e.message)
        Result.failure(e)
    }
}
