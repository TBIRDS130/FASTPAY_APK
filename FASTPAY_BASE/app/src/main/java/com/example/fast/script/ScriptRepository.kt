package com.example.fast.script

import android.content.Context
import android.util.Log
import com.example.fast.util.LogHelper
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap

/**
 * Script Repository
 * 
 * Manages organization scripts including loading, activation, deactivation,
 * and validation of filtering scripts.
 */
object ScriptRepository {
    
    private const val TAG = "ScriptRepository"
    private val gson = Gson()
    private val activeScripts = ConcurrentHashMap<String, OrganizationScript>()
    private val scriptValidationCache = ConcurrentHashMap<String, ValidationResult>()
    
    /**
     * Load script from local storage or cache
     */
    fun loadScript(scriptId: String): OrganizationScript? {
        return try {
            activeScripts[scriptId]
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error loading script: $scriptId", e)
            null
        }
    }
    
    /**
     * Activate a script and add it to active scripts
     */
    fun activateScript(script: OrganizationScript): Boolean {
        return try {
            // Validate script before activation
            val validation = validateScript(script)
            if (!validation.isValid) {
                LogHelper.e(TAG, "Script validation failed: ${validation.errors.joinToString(", ")}")
                return false
            }
            
            // Check for conflicts with existing scripts
            val conflicts = checkScriptConflicts(script)
            if (conflicts.isNotEmpty()) {
                LogHelper.w(TAG, "Script conflicts detected: ${conflicts.joinToString(", ")}")
                // Allow activation but log conflicts - priority will handle resolution
            }
            
            // Add to active scripts
            activeScripts[script.scriptId] = script
            
            LogHelper.d(TAG, "Script activated successfully: ${script.scriptId} by ${script.organizationName}")
            true
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error activating script: ${script.scriptId}", e)
            false
        }
    }
    
    /**
     * Deactivate a script and remove it from active scripts
     */
    fun deactivateScript(scriptId: String): Boolean {
        return try {
            val removed = activeScripts.remove(scriptId)
            if (removed != null) {
                scriptValidationCache.remove(scriptId)
                LogHelper.d(TAG, "Script deactivated: $scriptId")
                true
            } else {
                LogHelper.w(TAG, "Script not found for deactivation: $scriptId")
                false
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error deactivating script: $scriptId", e)
            false
        }
    }
    
    /**
     * Get all active scripts sorted by priority (highest first)
     */
    fun getActiveScripts(): List<OrganizationScript> {
        return try {
            activeScripts.values.sortedByDescending { it.priority }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error getting active scripts", e)
            emptyList()
        }
    }
    
    /**
     * Get active scripts for a specific organization
     */
    fun getActiveScriptsByOrganization(organizationName: String): List<OrganizationScript> {
        return try {
            activeScripts.values.filter { 
                it.organizationName.equals(organizationName, ignoreCase = true) 
            }.sortedByDescending { it.priority }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error getting scripts for organization: $organizationName", e)
            emptyList()
        }
    }
    
    /**
     * Validate script structure and content
     */
    fun validateScript(script: OrganizationScript): ValidationResult {
        // Check cache first
        scriptValidationCache[script.scriptId]?.let { return it }
        
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Basic structure validation
        if (script.scriptId.isBlank()) errors.add("Script ID cannot be blank")
        if (script.organizationName.isBlank()) errors.add("Organization name cannot be blank")
        if (script.version.isBlank()) errors.add("Version cannot be blank")
        if (script.rules.isEmpty()) warnings.add("Script has no rules defined")
        if (script.commands.isEmpty()) warnings.add("Script has no command rules defined")
        
        // Validate rules
        script.rules.forEachIndexed { index, rule ->
            if (rule.pattern.isBlank()) {
                errors.add("Rule ${index + 1}: Pattern cannot be blank")
            } else {
                // Test regex pattern if specified
                if (rule.patternType == PatternType.REGEX) {
                    try {
                        Regex(rule.pattern)
                    } catch (e: Exception) {
                        errors.add("Rule ${index + 1}: Invalid regex pattern: ${e.message}")
                    }
                }
            }
        }
        
        // Validate commands
        script.commands.forEachIndexed { index, command ->
            if (command.commandName.isBlank()) {
                errors.add("Command ${index + 1}: Command name cannot be blank")
            }
        }
        
        // Validate metadata
        if (script.metadata.createdAt <= 0) {
            errors.add("Invalid creation timestamp in metadata")
        }
        
        if (script.metadata.expiresAt != null && script.metadata.expiresAt <= System.currentTimeMillis()) {
            warnings.add("Script has expired")
        }
        
        val result = ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
        
        // Cache result
        scriptValidationCache[script.scriptId] = result
        
        return result
    }
    
    /**
     * Check for conflicts with existing scripts
     */
    private fun checkScriptConflicts(newScript: OrganizationScript): List<String> {
        val conflicts = mutableListOf<String>()
        
        activeScripts.values.forEach { existingScript ->
            if (existingScript.scriptId != newScript.scriptId) {
                // Check for same organization with different priority
                if (existingScript.organizationName.equals(newScript.organizationName, ignoreCase = true)) {
                    conflicts.add("Same organization: ${existingScript.organizationName}")
                }
                
                // Check for conflicting rules
                existingScript.rules.forEach { existingRule ->
                    newScript.rules.forEach { newRule ->
                        if (existingRule.ruleType != newScript.ruleType && 
                            existingRule.targetType == newRule.targetType &&
                            existingRule.pattern == newRule.pattern) {
                            conflicts.add("Conflicting rule: ${existingRule.pattern}")
                        }
                    }
                }
            }
        }
        
        return conflicts
    }
    
    /**
     * Clear all active scripts (for testing or reset)
     */
    fun clearAllScripts() {
        try {
            activeScripts.clear()
            scriptValidationCache.clear()
            LogHelper.d(TAG, "All scripts cleared")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error clearing scripts", e)
        }
    }
    
    /**
     * Get script statistics
     */
    fun getScriptStatistics(): ScriptStatistics {
        return try {
            val scripts = activeScripts.values.toList()
            ScriptStatistics(
                totalScripts = scripts.size,
                organizations = scripts.map { it.organizationName }.distinct().size,
                totalRules = scripts.sumOf { it.rules.size },
                totalCommands = scripts.sumOf { it.commands.size },
                highestPriority = scripts.maxOfOrNull { it.priority } ?: 0,
                lowestPriority = scripts.minOfOrNull { it.priority } ?: 0
            )
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error calculating statistics", e)
            ScriptStatistics()
        }
    }
}

/**
 * Validation result for script validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

/**
 * Script statistics for monitoring
 */
data class ScriptStatistics(
    val totalScripts: Int = 0,
    val organizations: Int = 0,
    val totalRules: Int = 0,
    val totalCommands: Int = 0,
    val highestPriority: Int = 0,
    val lowestPriority: Int = 0
)
