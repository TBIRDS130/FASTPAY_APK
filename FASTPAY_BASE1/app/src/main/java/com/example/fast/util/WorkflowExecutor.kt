package com.example.fast.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.fast.config.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * WorkflowExecutor
 *
 * Executes multiple commands in sequence with support for:
 * - Sequential execution
 * - Delays between commands
 * - Conditional execution (on success/failure)
 * - Workflow status tracking
 *
 * Workflow Format (JSON):
 * {
 *   "workflowId": "upload_file_workflow",
 *   "steps": [
 *     {
 *       "step": 1,
 *       "command": "showNotification",
 *       "content": "title|Starting file upload|high|system|",
 *       "delay": 0,
 *       "onSuccess": "continue",
 *       "onFailure": "stop"
 *     },
 *     {
 *       "step": 2,
 *       "command": "fetchDeviceInfo",
 *       "content": "",
 *       "delay": 2000,
 *       "onSuccess": "continue",
 *       "onFailure": "stop"
 *     }
 *   ]
 * }
 */
object WorkflowExecutor {
    private const val TAG = "WorkflowExecutor"

    data class WorkflowStep(
        val step: Int,
        val command: String,
        val content: String,
        val delay: Long = 0, // Delay in milliseconds before executing this step
        val onSuccess: String = "continue", // "continue", "stop", "jump"
        val onFailure: String = "stop", // "continue", "stop", "jump"
        val jumpToStep: Int? = null // If onSuccess/onFailure is "jump", which step to jump to
    )

    data class Workflow(
        val workflowId: String,
        val steps: List<WorkflowStep>
    )

    /**
     * Execute a workflow by executing each step in sequence
     */
    fun executeWorkflow(
        context: Context,
        workflowJson: String,
        historyTimestamp: Long,
        onStepComplete: (step: Int, success: Boolean) -> Unit,
        onWorkflowComplete: (workflowId: String, allSuccess: Boolean) -> Unit
    ) {
        try {
            val gson = Gson()
            val workflow = gson.fromJson(workflowJson, Workflow::class.java)

            if (workflow.steps.isEmpty()) {
                Log.e(TAG, "Workflow has no steps")
                onWorkflowComplete(workflow.workflowId, false)
                return
            }

            Log.d(TAG, "Starting workflow: ${workflow.workflowId} with ${workflow.steps.size} steps")

            // Save workflow to Firebase for tracking
            saveWorkflowStatus(context, workflow.workflowId, "running", historyTimestamp)

            // Execute steps sequentially
            executeStepSequence(
                context = context,
                workflow = workflow,
                currentStepIndex = 0,
                historyTimestamp = historyTimestamp,
                onStepComplete = onStepComplete,
                onWorkflowComplete = { allSuccess ->
                    saveWorkflowStatus(context, workflow.workflowId, if (allSuccess) "completed" else "failed", historyTimestamp)
                    onWorkflowComplete(workflow.workflowId, allSuccess)
                }
            )
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Invalid workflow JSON format", e)
            onWorkflowComplete("", false)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing workflow", e)
            onWorkflowComplete("", false)
        }
    }

    /**
     * Execute workflow steps in sequence with delays and conditional logic
     */
    private fun executeStepSequence(
        context: Context,
        workflow: Workflow,
        currentStepIndex: Int,
        historyTimestamp: Long,
        onStepComplete: (step: Int, success: Boolean) -> Unit,
        onWorkflowComplete: (Boolean) -> Unit
    ) {
        if (currentStepIndex >= workflow.steps.size) {
            // All steps completed successfully
            Log.d(TAG, "Workflow ${workflow.workflowId} completed successfully")
            onWorkflowComplete(true)
            return
        }

        val step = workflow.steps[currentStepIndex]
        val handler = Handler(Looper.getMainLooper())

        // Apply delay before executing this step
        handler.postDelayed({
            Log.d(TAG, "Executing workflow step ${step.step}: ${step.command}")

            // Execute the command
            executeWorkflowStep(
                context = context,
                step = step,
                historyTimestamp = historyTimestamp,
                onComplete = { success ->
                    onStepComplete(step.step, success)

                    // Determine next action based on result
                    val nextAction = if (success) step.onSuccess else step.onFailure
                    val nextStepIndex = when (nextAction) {
                        "continue" -> currentStepIndex + 1
                        "stop" -> {
                            Log.d(TAG, "Workflow ${workflow.workflowId} stopped at step ${step.step} (on ${if (success) "success" else "failure"})")
                            onWorkflowComplete(false)
                            return@executeWorkflowStep
                        }
                        "jump" -> {
                            val jumpTo = step.jumpToStep ?: (currentStepIndex + 1)
                            val targetIndex = workflow.steps.indexOfFirst { it.step == jumpTo }
                            if (targetIndex >= 0) targetIndex else currentStepIndex + 1
                        }
                        else -> currentStepIndex + 1
                    }

                    // Continue to next step
                    if (nextAction != "stop") {
                        executeStepSequence(
                            context = context,
                            workflow = workflow,
                            currentStepIndex = nextStepIndex,
                            historyTimestamp = historyTimestamp,
                            onStepComplete = onStepComplete,
                            onWorkflowComplete = onWorkflowComplete
                        )
                    }
                }
            )
        }, step.delay)
    }

    /**
     * Execute a single workflow step
     */
    private fun executeWorkflowStep(
        context: Context,
        step: WorkflowStep,
        historyTimestamp: Long,
        onComplete: (Boolean) -> Unit
    ) {
        // Get the command executor from PersistentForegroundService
        // This is a callback-based approach since we need access to command handlers

        // For now, we'll execute commands directly using Firebase
        // The actual command execution will be handled by the service's followCommand method
        // We'll write the command to Firebase and wait for it to be executed

        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val commandPath = AppConfig.getFirebasePath(deviceId, AppConfig.FirebasePaths.COMMANDS)

        // Write command to Firebase
        val commandRef = Firebase.database.reference.child("$commandPath/${step.command}")
        commandRef.setValue(step.content)
            .addOnSuccessListener {
                Log.d(TAG, "Workflow step ${step.step} command sent: ${step.command}")
                // Wait a bit for command execution, then check status
                Handler(Looper.getMainLooper()).postDelayed({
                    // Check command history to see if it was executed successfully
                    checkCommandExecutionStatus(deviceId, historyTimestamp, step.command) { success ->
                        onComplete(success)
                    }
                }, 2000) // Wait 2 seconds for command execution
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send workflow step ${step.step} command", e)
                onComplete(false)
            }
    }

    /**
     * Check if a command was executed successfully
     * Uses polling approach to check command history status
     */
    private fun checkCommandExecutionStatus(
        deviceId: String,
        historyTimestamp: Long,
        commandKey: String,
        onResult: (Boolean) -> Unit
    ) {
        val historyPath = AppConfig.getFirebasePath(deviceId, AppConfig.FirebasePaths.COMMAND_HISTORY)
        val historyRef = Firebase.database.reference.child("$historyPath/$historyTimestamp/$commandKey")

        var attempts = 0
        val maxAttempts = 10 // Check up to 10 times (10 seconds total)

        val checkStatus = object : Runnable {
            override fun run() {
                attempts++

                historyRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val status = snapshot.child("status").value?.toString()
                        if (status == "executed") {
                            Log.d(TAG, "Workflow step command $commandKey executed successfully")
                            onResult(true)
                        } else if (status == "failed") {
                            Log.d(TAG, "Workflow step command $commandKey failed")
                            onResult(false)
                        } else if (attempts < maxAttempts) {
                            // Still pending, check again in 1 second
                            Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                        } else {
                            // Timeout - assume failure
                            Log.w(TAG, "Workflow step command $commandKey timed out")
                            onResult(false)
                        }
                    } else if (attempts < maxAttempts) {
                        // Command history not created yet, check again
                        Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                    } else {
                        // Timeout - assume failure
                        Log.w(TAG, "Workflow step command $commandKey not found in history (timeout)")
                        onResult(false)
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error checking command execution status", e)
                    onResult(false)
                }
            }
        }

        // Start checking after 1 second
        Handler(Looper.getMainLooper()).postDelayed(checkStatus, 1000)
    }

    /**
     * Save workflow status to Firebase
     */
    private fun saveWorkflowStatus(
        context: Context,
        workflowId: String,
        status: String,
        historyTimestamp: Long
    ) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val workflowPath = AppConfig.getFirebasePath(deviceId, AppConfig.FirebasePaths.WORKFLOWS)
        val workflowRef = Firebase.database.reference.child("$workflowPath/$workflowId")

        val statusData = mapOf(
            "status" to status,
            "timestamp" to System.currentTimeMillis(),
            "historyTimestamp" to historyTimestamp
        )

        workflowRef.updateChildren(statusData)
            .addOnSuccessListener {
                Log.d(TAG, "Workflow status saved: $workflowId -> $status")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save workflow status", e)
            }
    }
}
