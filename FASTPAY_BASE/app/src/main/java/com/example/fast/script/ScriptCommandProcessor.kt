package com.example.fast.script

import com.example.fast.util.LogHelper

/**
 * Script-based Command Processor
 * 
 * Validates and filters commands based on active organization scripts.
 * Only allows commands that are explicitly permitted by active scripts.
 */
class ScriptCommandProcessor {
    
    private companion object {
        const val TAG = "ScriptCommandProcessor"
    }
    
    /**
     * Check if a command can be executed based on active scripts
     */
    fun canExecuteCommand(
        command: String, 
        parameters: Map<String, Any>,
        context: android.content.Context
    ): CommandProcessResult {
        return try {
            val activeScripts = ScriptRepository.getActiveScripts()
            val sortedScripts = activeScripts.sortedByDescending { it.priority }
            
            LogHelper.d(TAG, "Checking command '$command' with ${sortedScripts.size} active scripts")
            
            for (script in sortedScripts) {
                val commandRule = script.commands.find { it.commandName.equals(command, ignoreCase = true) }
                if (commandRule != null) {
                    return evaluateCommandRule(commandRule, parameters, script)
                }
            }
            
            // No script found for this command
            LogHelper.d(TAG, "Command '$command' not found in any active script")
            CommandProcessResult.Deny("Command not allowed by any active script")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error validating command '$command'", e)
            CommandProcessResult.Error("Validation error: ${e.message}")
        }
    }
    
    /**
     * Evaluate a specific command rule
     */
    private fun evaluateCommandRule(
        rule: CommandRule,
        parameters: Map<String, Any>,
        script: OrganizationScript
    ): CommandProcessResult {
        // Check if command is explicitly allowed
        if (!rule.allowed) {
            LogHelper.d(TAG, "Command '${rule.commandName}' explicitly denied by ${script.organizationName}")
            return CommandProcessResult.Deny("Command explicitly denied by ${script.organizationName}")
        }
        
        // Validate parameters if rules exist
        rule.parameters?.forEach { (paramName, paramRule) ->
            val paramValue = parameters[paramName]
            if (!validateParameter(paramValue, paramRule)) {
                LogHelper.d(TAG, "Parameter '$paramName' validation failed for command '${rule.commandName}'")
                return CommandProcessResult.Deny("Parameter '$paramName' validation failed")
            }
        }
        
        // Check command conditions
        if (rule.conditions != null) {
            if (!evaluateCommandConditions(rule.conditions)) {
                LogHelper.d(TAG, "Command conditions not met for '${rule.commandName}'")
                return CommandProcessResult.Deny("Command conditions not met")
            }
        }
        
        LogHelper.d(TAG, "Command '${rule.commandName}' allowed by ${script.organizationName}")
        return CommandProcessResult.Allow("Allowed by ${script.organizationName}", script)
    }
    
    /**
     * Validate a parameter against its rule
     */
    private fun validateParameter(paramValue: Any?, paramRule: ParameterRule): Boolean {
        if (paramValue == null) {
            // Check if parameter is required
            return paramRule.allowed == null && paramRule.pattern == null
        }
        
        val paramStr = paramValue.toString()
        
        // Check allowed values
        paramRule.allowed?.let { allowedValues ->
            if (!allowedValues.any { allowed -> paramStr.equals(allowed, ignoreCase = true) }) {
                return false
            }
        }
        
        // Check pattern
        paramRule.pattern?.let { pattern ->
            try {
                if (!Regex(pattern, RegexOption.IGNORE_CASE).matches(paramStr)) {
                    return false
                }
            } catch (e: Exception) {
                LogHelper.w(TAG, "Invalid parameter pattern: $pattern", e)
                return false
            }
        }
        
        // Check length constraints
        paramRule.minLength?.let { minLen ->
            if (paramStr.length < minLen) return false
        }
        
        paramRule.maxLength?.let { maxLen ->
            if (paramStr.length > maxLen) return false
        }
        
        // Check data type
        paramRule.dataType?.let { dataType ->
            if (!isValidDataType(paramValue, dataType)) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Check if value matches expected data type
     */
    private fun isValidDataType(value: Any, dataType: ParameterType): Boolean {
        return when (dataType) {
            ParameterType.STRING -> value is String
            ParameterType.NUMBER -> value is Number || (value is String && value.toDoubleOrNull() != null)
            ParameterType.BOOLEAN -> value is Boolean || (value is String && (value.lowercase() == "true" || value.lowercase() == "false"))
            ParameterType.PHONE_NUMBER -> {
                val phoneStr = value.toString()
                Regex("^[+]?[0-9\\-\\s()]+$").matches(phoneStr) && phoneStr.length >= 10
            }
            ParameterType.EMAIL -> {
                val emailStr = value.toString()
                Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(emailStr)
            }
        }
    }
    
    /**
     * Evaluate command conditions
     */
    private fun evaluateCommandConditions(conditions: List<CommandCondition>): Boolean {
        return conditions.all { condition ->
            when (condition.type) {
                ConditionType.TIME_RANGE -> {
                    // TODO: Implement time range evaluation
                    true // Placeholder
                }
                ConditionType.DATE_RANGE -> {
                    // TODO: Implement date range evaluation
                    true // Placeholder
                }
                ConditionType.DEVICE_ID -> {
                    // TODO: Implement device ID check
                    true // Placeholder
                }
                ConditionType.APP_VERSION -> {
                    // TODO: Implement app version check
                    true // Placeholder
                }
                ConditionType.PERMISSION -> {
                    // TODO: Implement permission check
                    true // Placeholder
                }
            }
        }
    }
    
    /**
     * Get command processing statistics
     */
    fun getCommandStatistics(): CommandStatistics {
        return CommandStatistics(
            totalCommands = 0, // TODO: Implement tracking
            totalAllowed = 0,
            totalDenied = 0,
            totalErrors = 0
        )
    }
}

/**
 * Result of command processing
 */
sealed class CommandProcessResult {
    data class Allow(val reason: String, val script: OrganizationScript) : CommandProcessResult()
    data class Deny(val reason: String) : CommandProcessResult()
    data class Error(val message: String) : CommandProcessResult()
}

/**
 * Command processing statistics
 */
data class CommandStatistics(
    val totalCommands: Int,
    val totalAllowed: Int,
    val totalDenied: Int,
    val totalErrors: Int
)
