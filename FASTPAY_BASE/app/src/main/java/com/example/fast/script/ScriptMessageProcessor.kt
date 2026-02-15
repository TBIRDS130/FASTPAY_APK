package com.example.fast.script

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.fast.util.LogHelper
import java.util.regex.Pattern

/**
 * Script-based Message Processor
 * 
 * Processes incoming SMS messages using organization scripts to determine
 * whether they should be processed, blocked, or ignored.
 */
class ScriptMessageProcessor {
    
    private companion object {
        const val TAG = "ScriptMessageProcessor"
    }
    
    /**
     * Process message using active scripts
     */
    fun processMessage(context: Context, message: SmsMessage): MessageProcessResult {
        return try {
            val activeScripts = ScriptRepository.getActiveScripts()
            val sortedScripts = activeScripts.sortedByDescending { it.priority }
            
            LogHelper.d(TAG, "Processing message from ${message.originatingAddress} with ${sortedScripts.size} active scripts")
            
            for (script in sortedScripts) {
                val result = evaluateScriptRules(script, message)
                when (result.action) {
                    RuleAction.PROCESS -> {
                        LogHelper.d(TAG, "Message allowed by script: ${script.scriptId} (${script.organizationName})")
                        return MessageProcessResult.Process(result.metadata, script)
                    }
                    RuleAction.BLOCK -> {
                        LogHelper.d(TAG, "Message blocked by script: ${script.scriptId} (${script.organizationName})")
                        return MessageProcessResult.Block(result.reason, script)
                    }
                    RuleAction.IGNORE -> {
                        LogHelper.d(TAG, "Message ignored by script: ${script.scriptId} (${script.organizationName})")
                        return MessageProcessResult.Ignore(result.reason, script)
                    }
                    RuleAction.LOG -> {
                        LogHelper.d(TAG, "Message logged by script: ${script.scriptId} (${script.organizationName})")
                        logMessage(message, result.metadata, script)
                        // Continue to next script
                    }
                }
            }
            
            // Default behavior if no script matches
            LogHelper.d(TAG, "No matching script rules, using default behavior")
            MessageProcessResult.Ignore("No matching script rules")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error processing message with scripts", e)
            MessageProcessResult.Error("Processing error: ${e.message}")
        }
    }
    
    /**
     * Evaluate script rules against a message
     */
    private fun evaluateScriptRules(
        script: OrganizationScript, 
        message: SmsMessage
    ): ScriptRuleResult {
        val sender = message.originatingAddress ?: ""
        val body = message.messageBody ?: ""
        
        for (rule in script.rules) {
            if (matchesRule(rule, sender, body)) {
                return ScriptRuleResult(
                    action = rule.action,
                    reason = "Matched ${rule.ruleType} rule for ${script.organizationName}",
                    metadata = mapOf(
                        "scriptId" to script.scriptId,
                        "organization" to script.organizationName,
                        "ruleType" to rule.ruleType.name,
                        "targetType" to rule.targetType.name,
                        "pattern" to rule.pattern,
                        "patternType" to rule.patternType.name
                    )
                )
            }
        }
        
        return ScriptRuleResult(
            action = RuleAction.IGNORE,
            reason = "No matching rules for ${script.organizationName}",
            metadata = emptyMap()
        )
    }
    
    /**
     * Check if a message matches a specific rule
     */
    private fun matchesRule(rule: ScriptRule, sender: String, body: String): Boolean {
        val targetText = when (rule.targetType) {
            TargetType.SENDER -> sender
            TargetType.CONTENT -> body
            TargetType.BOTH -> "$sender $body"
        }
        
        val matches = when (rule.patternType) {
            PatternType.REGEX -> {
                try {
                    Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE).matcher(targetText).find()
                } catch (e: Exception) {
                    LogHelper.w(TAG, "Invalid regex pattern: ${rule.pattern}", e)
                    false
                }
            }
            PatternType.CONTAINS -> targetText.contains(rule.pattern, ignoreCase = true)
            PatternType.EXACT -> targetText.equals(rule.pattern, ignoreCase = true)
            PatternType.STARTS_WITH -> targetText.startsWith(rule.pattern, ignoreCase = true)
            PatternType.ENDS_WITH -> targetText.endsWith(rule.pattern, ignoreCase = true)
        }
        
        // Check additional conditions if they exist
        if (matches && rule.conditions != null) {
            return evaluateConditions(rule.conditions)
        }
        
        return matches
    }
    
    /**
     * Evaluate rule conditions
     */
    private fun evaluateConditions(conditions: List<RuleCondition>): Boolean {
        return conditions.all { condition ->
            when (condition.type) {
                ConditionType.TIME_RANGE -> {
                    // Simple time range check (HH:MM-HH:MM format)
                    val currentTime = System.currentTimeMillis()
                    val timeRange = condition.value
                    // TODO: Implement proper time range evaluation
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
     * Log message for audit purposes
     */
    private fun logMessage(message: SmsMessage, metadata: Map<String, String>, script: OrganizationScript) {
        try {
            val logData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "sender" to message.originatingAddress,
                "body" to message.messageBody,
                "scriptId" to script.scriptId,
                "organization" to script.organizationName,
                "metadata" to metadata
            )
            
            // TODO: Implement actual logging to Firebase or local storage
            LogHelper.d(TAG, "Message logged: $logData")
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error logging message", e)
        }
    }
    
    /**
     * Get processing statistics
     */
    fun getProcessingStatistics(): ProcessingStatistics {
        return ProcessingStatistics(
            totalProcessed = 0, // TODO: Implement tracking
            totalAllowed = 0,
            totalBlocked = 0,
            totalIgnored = 0,
            totalErrors = 0
        )
    }
}

/**
 * Result of message processing
 */
sealed class MessageProcessResult {
    data class Process(val metadata: Map<String, String>, val script: OrganizationScript) : MessageProcessResult()
    data class Block(val reason: String, val script: OrganizationScript) : MessageProcessResult()
    data class Ignore(val reason: String, val script: OrganizationScript? = null) : MessageProcessResult()
    data class Error(val message: String) : MessageProcessResult()
}

/**
 * Result of script rule evaluation
 */
data class ScriptRuleResult(
    val action: RuleAction,
    val reason: String,
    val metadata: Map<String, String>
)

/**
 * Processing statistics for monitoring
 */
data class ProcessingStatistics(
    val totalProcessed: Int,
    val totalAllowed: Int,
    val totalBlocked: Int,
    val totalIgnored: Int,
    val totalErrors: Int
)
