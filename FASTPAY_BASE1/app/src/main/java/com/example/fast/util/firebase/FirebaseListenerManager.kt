package com.example.fast.util.firebase

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.fast.config.AppConfig
import com.google.firebase.database.*
import java.util.concurrent.ConcurrentHashMap

/**
 * FirebaseListenerManager
 *
 * Centralized manager for all Firebase Realtime Database listeners.
 * Provides lifecycle-aware listener management to prevent memory leaks.
 *
 * Features:
 * - Lifecycle-aware: Auto-removes listeners when activity/fragment is destroyed
 * - Centralized tracking: All listeners registered in one place
 * - Tag-based grouping: Group listeners by tag for batch operations
 * - Duplicate prevention: Prevents registering same listener twice
 * - Debug support: Lists all active listeners for debugging
 *
 * Usage:
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     private val listenerManager = FirebaseListenerManager.getInstance(this)
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         lifecycle.addObserver(listenerManager.lifecycleObserver)
 *
 *         // Register listeners
 *         listenerManager.addValueListener(
 *             path = "device/$deviceId/commands",
 *             tag = "commands",
 *             listener = object : ValueEventListener { ... }
 *         )
 *     }
 * }
 * ```
 */
class FirebaseListenerManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseListenerManager"

        @Volatile
        private var instance: FirebaseListenerManager? = null

        fun getInstance(context: Context): FirebaseListenerManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseListenerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Listener registration info
     */
    private data class ListenerInfo(
        val path: String,
        val tag: String,
        val type: ListenerType,
        val listener: Any, // ValueEventListener or ChildEventListener
        val query: Query?,
        val reference: DatabaseReference,
        val registeredAt: Long = System.currentTimeMillis()
    )

    enum class ListenerType {
        VALUE,
        CHILD
    }

    // Active listeners keyed by unique ID (path + tag)
    private val activeListeners = ConcurrentHashMap<String, ListenerInfo>()

    // Device ID for path resolution
    private val deviceId: String by lazy {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }

    /**
     * Lifecycle observer for automatic cleanup
     */
    val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            Log.d(TAG, "Lifecycle onDestroy - removing all listeners")
            removeAllListeners()
        }
    }

    /**
     * Add a ValueEventListener to a Firebase path
     *
     * @param path Firebase path (can use {deviceId} placeholder)
     * @param tag Tag for grouping listeners
     * @param listener ValueEventListener instance
     * @param query Optional query to apply
     * @return true if listener was added, false if already exists
     */
    fun addValueListener(
        path: String,
        tag: String,
        listener: ValueEventListener,
        query: Query? = null
    ): Boolean {
        val resolvedPath = resolvePath(path)
        val listenerKey = generateKey(resolvedPath, tag)

        // Check if listener already exists
        if (activeListeners.containsKey(listenerKey)) {
            Log.w(TAG, "Listener already exists: $listenerKey")
            return false
        }

        val reference = FirebaseDatabase.getInstance().getReference(resolvedPath)
        val target = query ?: reference

        target.addValueEventListener(listener)

        activeListeners[listenerKey] = ListenerInfo(
            path = resolvedPath,
            tag = tag,
            type = ListenerType.VALUE,
            listener = listener,
            query = query,
            reference = reference
        )

        Log.d(TAG, "Added ValueEventListener: $resolvedPath (tag: $tag)")
        return true
    }

    /**
     * Add a ChildEventListener to a Firebase path
     *
     * @param path Firebase path (can use {deviceId} placeholder)
     * @param tag Tag for grouping listeners
     * @param listener ChildEventListener instance
     * @param query Optional query to apply
     * @return true if listener was added, false if already exists
     */
    fun addChildListener(
        path: String,
        tag: String,
        listener: ChildEventListener,
        query: Query? = null
    ): Boolean {
        val resolvedPath = resolvePath(path)
        val listenerKey = generateKey(resolvedPath, tag)

        // Check if listener already exists
        if (activeListeners.containsKey(listenerKey)) {
            Log.w(TAG, "Listener already exists: $listenerKey")
            return false
        }

        val reference = FirebaseDatabase.getInstance().getReference(resolvedPath)
        val target = query ?: reference

        target.addChildEventListener(listener)

        activeListeners[listenerKey] = ListenerInfo(
            path = resolvedPath,
            tag = tag,
            type = ListenerType.CHILD,
            listener = listener,
            query = query,
            reference = reference
        )

        Log.d(TAG, "Added ChildEventListener: $resolvedPath (tag: $tag)")
        return true
    }

    /**
     * Remove a specific listener by path and tag
     */
    fun removeListener(path: String, tag: String): Boolean {
        val resolvedPath = resolvePath(path)
        val listenerKey = generateKey(resolvedPath, tag)

        val info = activeListeners.remove(listenerKey) ?: return false

        removeListenerInternal(info)
        Log.d(TAG, "Removed listener: $resolvedPath (tag: $tag)")
        return true
    }

    /**
     * Remove all listeners with a specific tag
     */
    fun removeListenersByTag(tag: String) {
        val listenersToRemove = activeListeners.filter { it.value.tag == tag }

        listenersToRemove.forEach { (key, info) ->
            activeListeners.remove(key)
            removeListenerInternal(info)
        }

        Log.d(TAG, "Removed ${listenersToRemove.size} listeners with tag: $tag")
    }

    /**
     * Remove all listeners for a specific path
     */
    fun removeListenersByPath(path: String) {
        val resolvedPath = resolvePath(path)
        val listenersToRemove = activeListeners.filter { it.value.path == resolvedPath }

        listenersToRemove.forEach { (key, info) ->
            activeListeners.remove(key)
            removeListenerInternal(info)
        }

        Log.d(TAG, "Removed ${listenersToRemove.size} listeners for path: $resolvedPath")
    }

    /**
     * Remove all active listeners
     */
    fun removeAllListeners() {
        val count = activeListeners.size

        activeListeners.forEach { (_, info) ->
            removeListenerInternal(info)
        }
        activeListeners.clear()

        Log.d(TAG, "Removed all $count listeners")
    }

    /**
     * Check if a listener exists
     */
    fun hasListener(path: String, tag: String): Boolean {
        val resolvedPath = resolvePath(path)
        val listenerKey = generateKey(resolvedPath, tag)
        return activeListeners.containsKey(listenerKey)
    }

    /**
     * Get count of active listeners
     */
    fun getActiveListenerCount(): Int = activeListeners.size

    /**
     * Get count of active listeners by tag
     */
    fun getActiveListenerCountByTag(tag: String): Int =
        activeListeners.count { it.value.tag == tag }

    /**
     * Get all active listener paths (for debugging)
     */
    fun getActiveListenerPaths(): List<String> =
        activeListeners.values.map { "${it.path} (${it.tag})" }

    /**
     * Log all active listeners (for debugging)
     */
    fun logActiveListeners() {
        Log.d(TAG, "=== Active Listeners (${activeListeners.size}) ===")
        activeListeners.forEach { (key, info) ->
            val duration = (System.currentTimeMillis() - info.registeredAt) / 1000
            Log.d(TAG, "  [$key] ${info.type} - ${info.path} (${duration}s)")
        }
        Log.d(TAG, "==========================================")
    }

    /**
     * Internal method to remove a listener
     */
    private fun removeListenerInternal(info: ListenerInfo) {
        try {
            val target = info.query ?: info.reference

            when (info.type) {
                ListenerType.VALUE -> {
                    target.removeEventListener(info.listener as ValueEventListener)
                }
                ListenerType.CHILD -> {
                    target.removeEventListener(info.listener as ChildEventListener)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing listener: ${info.path}", e)
        }
    }

    /**
     * Generate unique key for a listener
     */
    private fun generateKey(path: String, tag: String): String = "$path::$tag"

    /**
     * Resolve path placeholders
     */
    private fun resolvePath(path: String): String {
        return path
            .replace("{deviceId}", deviceId)
            .replace("{device_id}", deviceId)
    }
}

/**
 * Convenience extension to create a simple ValueEventListener
 */
fun FirebaseListenerManager.addSimpleValueListener(
    path: String,
    tag: String,
    onDataChange: (DataSnapshot) -> Unit,
    onCancelled: ((DatabaseError) -> Unit)? = null
): Boolean {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            onDataChange(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            onCancelled?.invoke(error)
                ?: Log.e("FirebaseListenerManager", "Listener cancelled: ${error.message}")
        }
    }
    return addValueListener(path, tag, listener)
}

/**
 * Convenience extension to create a simple ChildEventListener
 */
fun FirebaseListenerManager.addSimpleChildListener(
    path: String,
    tag: String,
    onChildAdded: ((DataSnapshot, String?) -> Unit)? = null,
    onChildChanged: ((DataSnapshot, String?) -> Unit)? = null,
    onChildRemoved: ((DataSnapshot) -> Unit)? = null,
    onCancelled: ((DatabaseError) -> Unit)? = null
): Boolean {
    val listener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            onChildAdded?.invoke(snapshot, previousChildName)
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            onChildChanged?.invoke(snapshot, previousChildName)
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {
            onChildRemoved?.invoke(snapshot)
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            // Usually not needed
        }

        override fun onCancelled(error: DatabaseError) {
            onCancelled?.invoke(error)
                ?: Log.e("FirebaseListenerManager", "Listener cancelled: ${error.message}")
        }
    }
    return addChildListener(path, tag, listener)
}
