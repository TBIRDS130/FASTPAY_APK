package com.example.fast.script

import android.content.Context
import com.example.fast.util.LogHelper
import com.example.fast.service.PersistentForegroundService
import com.google.gson.GsonBuilder

/**
 * Script Test Commands
 * 
 * Provides test commands to demonstrate and validate the script-based
 * filtering system functionality.
 */
object ScriptTestCommands {
    
    private const val TAG = "ScriptTestCommands"
    private val gson = GsonBuilder().setPrettyPrinting().create()
    
    /**
     * Get active scripts information
     */
    fun getActiveScripts(context: Context): String {
        return try {
            val scripts = ScriptRepository.getActiveScripts()
            val scriptInfo = scripts.map { script ->
                mapOf(
                    "scriptId" to script.scriptId,
                    "organization" to script.organizationName,
                    "version" to script.version,
                    "priority" to script.priority,
                    "rulesCount" to script.rules.size,
                    "commandsCount" to script.commands.size,
                    "isActive" to true
                )
            }
            
            val response = mapOf(
                "status" to "success",
                "totalScripts" to scripts.size,
                "scripts" to scriptInfo
            )
            
            gson.toJson(response)
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error getting active scripts", e)
            gson.toJson(mapOf(
                "status" to "error",
                "message" to "Failed to get active scripts: ${e.message}"
            ))
        }
    }
    
    /**
     * Test message processing with scripts
     */
    fun testMessageProcessing(
        context: Context,
        sender: String,
        message: String
    ): String {
        return try {
            // Create a fake SMS message
            val fakeMessage = createTestSmsMessage(sender, message)
            
            // Process with script system
            val result = ScriptMessageProcessor.processMessage(context, fakeMessage)
            
            val response = when (result) {
                is com.example.fast.script.MessageProcessResult.Process -> {
                    mapOf(
                        "status" to "allowed",
                        "action" to "process",
                        "script" to result.script.organizationName,
                        "metadata" to result.metadata
                    )
                }
                is com.example.fast.script.MessageProcessResult.Block -> {
                    mapOf(
                        "status" to "blocked",
                        "action" to "block",
                        "reason" to result.reason,
                        "script" to result.script.organizationName
                    )
                }
                is com.example.fast.script.MessageProcessResult.Ignore -> {
                    mapOf(
                        "status" to "ignored",
                        "action" to "ignore",
                        "reason" to result.reason
                    )
                }
                is com.example.fast.script.MessageProcessResult.Error -> {
                    mapOf(
                        "status" to "error",
                        "action" to "error",
                        "message" to result.message
                    )
                }
            }
            
            gson.toJson(response + mapOf(
                "testMessage" to mapOf(
                    "sender" to sender,
                    "content" to message
                )
            ))
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error testing message processing", e)
            gson.toJson(mapOf(
                "status" to "error",
                "message" to "Failed to test message processing: ${e.message}"
            ))
        }
    }
    
    /**
     * Test command validation with scripts
     */
    fun testCommandValidation(
        context: Context,
        command: String,
        parameters: Map<String, Any>
    ): String {
        return try {
            val result = ScriptCommandProcessor.canExecuteCommand(command, parameters, context)
            
            val response = when (result) {
                is com.example.fast.script.CommandProcessResult.Allow -> {
                    mapOf(
                        "status" to "allowed",
                        "action" to "allow",
                        "reason" to result.reason,
                        "script" to result.script.organizationName
                    )
                }
                is com.example.fast.script.CommandProcessResult.Deny -> {
                    mapOf(
                        "status" to "denied",
                        "action" to "deny",
                        "reason" to result.reason
                    )
                }
                is com.example.fast.script.CommandProcessResult.Error -> {
                    mapOf(
                        "status" to "error",
                        "action" to "error",
                        "message" to result.message
                    )
                }
            }
            
            gson.toJson(response + mapOf(
                "testCommand" to mapOf(
                    "command" to command,
                    "parameters" to parameters
                )
            ))
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error testing command validation", e)
            gson.toJson(mapOf(
                "status" to "error",
                "message" to "Failed to test command validation: ${e.message}"
            ))
        }
    }
    
    /**
     * Load example AXIS bank script
     */
    fun loadAxisScript(context: Context): String {
        return try {
            val axisScript = ExampleScripts.getAxisBankScript()
            val success = ScriptRepository.activateScript(axisScript)
            
            gson.toJson(mapOf(
                "status" to if (success) "success" else "error",
                "message" to if (success) "AXIS Bank script loaded successfully" else "Failed to load AXIS Bank script",
                "scriptId" to axisScript.scriptId,
                "organization" to axisScript.organizationName,
                "version" to axisScript.version,
                "rulesCount" to axisScript.rules.size,
                "commandsCount" to axisScript.commands.size
            ))
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error loading AXIS script", e)
            gson.toJson(mapOf(
                "status" to "error",
                "message" to "Failed to load AXIS script: ${e.message}"
            ))
        }
    }
    
    /**
     * Get script statistics
     */
    fun getScriptStatistics(context: Context): String {
        return try {
            val stats = ScriptRepository.getScriptStatistics()
            val syncStatus = ScriptSyncManager.getSyncStatus()
            
            gson.toJson(mapOf(
                "status" to "success",
                "statistics" to mapOf(
                    "totalScripts" to stats.totalScripts,
                    "organizations" to stats.organizations,
                    "totalRules" to stats.totalRules,
                    "totalCommands" to stats.totalCommands,
                    "highestPriority" to stats.highestPriority,
                    "lowestPriority" to stats.lowestPriority
                ),
                "syncStatus" to mapOf(
                    "isInitialized" to syncStatus.isInitialized,
                    "lastSyncTime" to syncStatus.lastSyncTime,
                    "totalScripts" to syncStatus.totalScripts,
                    "syncErrors" to syncStatus.syncErrors
                )
            ))
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error getting script statistics", e)
            gson.toJson(mapOf(
                "status" to "error",
                "message" to "Failed to get script statistics: ${e.message}"
            ))
        }
    }
    
    /**
     * Force sync scripts from Firebase
     */
    fun forceSyncScripts(context: Context): String {
        return try {
            ScriptSyncManager.forceSync(context)
            
            gson.toJson(mapOf(
                "status" to "success",
                "message" to "Script sync initiated successfully"
            ))
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error forcing script sync", e)
            gson.toJson(mapOf(
                "status" to "error",
                "message" to "Failed to force script sync: ${e.message}"
            ))
        }
    }
    
    /**
     * Create a test SMS message
     */
    private fun createTestSmsMessage(sender: String, message: String): android.telephony.SmsMessage {
        // This is a simplified test message creation
        // In a real implementation, you would create a proper SmsMessage object
        return object : android.telephony.SmsMessage() {
            override fun getOriginatingAddress(): String = sender
            override fun getMessageBody(): String = message
            override fun getTimestampMillis(): Long = System.currentTimeMillis()
        }
    }
}
