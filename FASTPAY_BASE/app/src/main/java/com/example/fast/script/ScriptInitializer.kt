package com.example.fast.script

import android.content.Context
import com.example.fast.util.LogHelper
import com.example.fast.service.PersistentForegroundService

/**
 * Script System Initializer
 * 
 * Initializes the script-based filtering system when the app starts.
 * Loads example scripts and sets up the script sync manager.
 */
object ScriptInitializer {
    
    private const val TAG = "ScriptInitializer"
    private var isInitialized = false
    
    /**
     * Initialize the script system
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            LogHelper.d(TAG, "Script system already initialized")
            return
        }
        
        try {
            LogHelper.d(TAG, "Initializing script-based filtering system")
            
            // Initialize script sync manager
            ScriptSyncManager.initializeScriptSync(context)
            
            // Load example scripts for demonstration
            loadExampleScripts()
            
            // Get script statistics
            val stats = ScriptRepository.getScriptStatistics()
            LogHelper.d(TAG, "Script system initialized: $stats")
            
            isInitialized = true
            LogHelper.d(TAG, "Script system initialization completed")
            
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error initializing script system", e)
        }
    }
    
    /**
     * Load example scripts for demonstration
     */
    private fun loadExampleScripts() {
        try {
            // Load AXIS Bank script as example
            val axisScript = ExampleScripts.getAxisBankScript()
            val axisSuccess = ScriptRepository.activateScript(axisScript)
            
            if (axisSuccess) {
                LogHelper.d(TAG, "AXIS Bank script loaded successfully")
            } else {
                LogHelper.e(TAG, "Failed to load AXIS Bank script")
            }
            
            // Load generic banking script as example
            val genericScript = ExampleScripts.getGenericBankScript("HDFC Bank")
            val genericSuccess = ScriptRepository.activateScript(genericScript)
            
            if (genericSuccess) {
                LogHelper.d(TAG, "Generic banking script loaded successfully")
            } else {
                LogHelper.e(TAG, "Failed to load generic banking script")
            }
            
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error loading example scripts", e)
        }
    }
    
    /**
     * Get initialization status
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }
    
    /**
     * Reset the script system
     */
    fun reset() {
        try {
            LogHelper.d(TAG, "Resetting script system")
            
            // Clear all scripts
            ScriptRepository.clearAllScripts()
            
            // Reset initialization flag
            isInitialized = false
            
            LogHelper.d(TAG, "Script system reset completed")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error resetting script system", e)
        }
    }
    
    /**
     * Get system status
     */
    fun getSystemStatus(): ScriptSystemStatus {
        return ScriptSystemStatus(
            isInitialized = isInitialized,
            syncStatus = ScriptSyncManager.getSyncStatus(),
            scriptStats = ScriptRepository.getScriptStatistics(),
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}

/**
 * Script system status information
 */
data class ScriptSystemStatus(
    val isInitialized: Boolean,
    val syncStatus: SyncStatus,
    val scriptStats: ScriptStatistics,
    val lastUpdateTime: Long
)
