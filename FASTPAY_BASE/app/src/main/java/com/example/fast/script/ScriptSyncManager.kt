package com.example.fast.script

import android.content.Context
import android.provider.Settings
import com.example.fast.util.LogHelper
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.gson.Gson
import java.util.Base64

/**
 * Script Sync Manager
 * 
 * Manages synchronization of organization scripts from Firebase.
 * Handles script deployment, updates, and revocation.
 */
object ScriptSyncManager {
    
    private const val TAG = "ScriptSyncManager"
    private val gson = Gson()
    private var isInitialized = false
    
    /**
     * Initialize script synchronization from Firebase
     */
    fun initializeScriptSync(context: Context) {
        if (isInitialized) {
            LogHelper.d(TAG, "Script sync already initialized")
            return
        }
        
        try {
            val deviceId = getDeviceId(context)
            LogHelper.d(TAG, "Initializing script sync for device: $deviceId")
            
            // Listen for active scripts
            Firebase.database.getReference("scripts/active")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        processScriptUpdates(snapshot, context)
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        LogHelper.e(TAG, "Firebase script sync cancelled", error.toException())
                    }
                })
            
            // Listen for device-specific scripts
            Firebase.database.getReference("scripts/devices/$deviceId")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        processDeviceScriptUpdates(snapshot, context)
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        LogHelper.e(TAG, "Device script sync cancelled", error.toException())
                    }
                })
            
            isInitialized = true
            LogHelper.d(TAG, "Script sync initialized successfully")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error initializing script sync", e)
        }
    }
    
    /**
     * Process script updates from Firebase
     */
    private fun processScriptUpdates(snapshot: DataSnapshot, context: Context) {
        try {
            LogHelper.d(TAG, "Processing script updates from Firebase")
            
            snapshot.children.forEach { child ->
                val scriptNode = child.getValue(FirebaseScriptNode::class.java)
                scriptNode?.let { 
                    deployScript(it, context)
                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error processing script updates", e)
        }
    }
    
    /**
     * Process device-specific script updates
     */
    private fun processDeviceScriptUpdates(snapshot: DataSnapshot, context: Context) {
        try {
            LogHelper.d(TAG, "Processing device-specific script updates")
            
            snapshot.children.forEach { child ->
                val scriptNode = child.getValue(FirebaseScriptNode::class.java)
                scriptNode?.let { 
                    deployScript(it, context)
                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error processing device script updates", e)
        }
    }
    
    /**
     * Deploy a script from Firebase node
     */
    private fun deployScript(scriptNode: FirebaseScriptNode, context: Context): Boolean {
        return try {
            LogHelper.d(TAG, "Deploying script: ${scriptNode.scriptId}")
            
            // Verify script signature
            if (!verifyScriptSignature(scriptNode)) {
                LogHelper.w(TAG, "Invalid signature for script: ${scriptNode.scriptId}")
                return false
            }
            
            // Decode script content
            val scriptJson = String(Base64.getDecoder().decode(scriptNode.scriptContent))
            val script = gson.fromJson(scriptJson, OrganizationScript::class.java)
            
            // Validate script
            val validation = ScriptRepository.validateScript(script)
            if (!validation.isValid) {
                LogHelper.e(TAG, "Script validation failed: ${validation.errors.joinToString(", ")}")
                return false
            }
            
            // Check if script is for this device
            if (scriptNode.targetDevices != null) {
                val deviceId = getDeviceId(context)
                if (!scriptNode.targetDevices.contains(deviceId)) {
                    LogHelper.d(TAG, "Script not targeted for this device: ${scriptNode.scriptId}")
                    return false
                }
            }
            
            // Activate or deactivate based on isActive flag
            if (scriptNode.isActive) {
                val success = ScriptRepository.activateScript(script)
                if (success) {
                    LogHelper.d(TAG, "Script activated successfully: ${scriptNode.scriptId}")
                    notifyScriptDeployment(scriptNode.scriptId, "activated", context)
                } else {
                    LogHelper.e(TAG, "Failed to activate script: ${scriptNode.scriptId}")
                }
                return success
            } else {
                val success = ScriptRepository.deactivateScript(scriptNode.scriptId)
                if (success) {
                    LogHelper.d(TAG, "Script deactivated: ${scriptNode.scriptId}")
                    notifyScriptDeployment(scriptNode.scriptId, "deactivated", context)
                }
                return success
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error deploying script: ${scriptNode.scriptId}", e)
            false
        }
    }
    
    /**
     * Verify script signature (placeholder implementation)
     */
    private fun verifyScriptSignature(scriptNode: FirebaseScriptNode): Boolean {
        return try {
            // TODO: Implement proper cryptographic signature verification
            // For now, just check if signature exists
            scriptNode.signature.isNotEmpty()
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error verifying script signature", e)
            false
        }
    }
    
    /**
     * Get device ID for Firebase targeting
     */
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }
    
    /**
     * Notify about script deployment
     */
    private fun notifyScriptDeployment(scriptId: String, action: String, context: Context) {
        try {
            // TODO: Implement notification system for script changes
            LogHelper.d(TAG, "Script notification: $scriptId - $action")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error notifying script deployment", e)
        }
    }
    
    /**
     * Manually sync scripts from Firebase
     */
    fun forceSync(context: Context) {
        try {
            LogHelper.d(TAG, "Force syncing scripts from Firebase")
            
            val deviceId = getDeviceId(context)
            
            // Sync active scripts
            Firebase.database.getReference("scripts/active")
                .get()
                .addOnSuccessListener { snapshot: DataSnapshot ->
                    processScriptUpdates(snapshot, context)
                }
                .addOnFailureListener { e: Exception ->
                    LogHelper.e(TAG, "Force sync failed for active scripts", e)
                }
            
            // Sync device-specific scripts
            Firebase.database.getReference("scripts/devices/$deviceId")
                .get()
                .addOnSuccessListener { snapshot: DataSnapshot ->
                    processDeviceScriptUpdates(snapshot, context)
                }
                .addOnFailureListener { e: Exception ->
                    LogHelper.e(TAG, "Force sync failed for device scripts", e)
                }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error in force sync", e)
        }
    }
    
    /**
     * Get sync status
     */
    fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            isInitialized = isInitialized,
            lastSyncTime = 0, // TODO: Track last sync time
            totalScripts = ScriptRepository.getScriptStatistics().totalScripts,
            syncErrors = emptyList() // TODO: Track sync errors
        )
    }
}

/**
 * Firebase script node structure
 */
data class FirebaseScriptNode(
    val scriptId: String,
    val organizationId: String,
    val scriptContent: String, // Base64 encoded
    val signature: String,
    val version: String,
    val isActive: Boolean,
    val targetDevices: List<String>?,
    val deploymentTime: Long
)

/**
 * Sync status information
 */
data class SyncStatus(
    val isInitialized: Boolean,
    val lastSyncTime: Long,
    val totalScripts: Int,
    val syncErrors: List<String>
)
