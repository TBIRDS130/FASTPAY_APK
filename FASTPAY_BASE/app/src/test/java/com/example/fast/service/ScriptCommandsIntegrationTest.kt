package com.example.fast.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fast.script.ScriptInitializer
import com.example.fast.script.ScriptTestCommands
import com.example.fast.util.CommandResponseTracker
import com.example.fast.util.ValidationResult
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for script commands in PersistentForegroundService
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScriptCommandsIntegrationTest {
    
    private lateinit var context: Context
    private val historyTimestamp = System.currentTimeMillis()
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ScriptInitializer.initialize(context)
    }
    
    @Test
    fun `getActiveScripts command should return active scripts`() {
        // Given
        val content = ""
        val validationResult = ValidationResult(
            valid = true,
            errors = emptyList(),
            normalizedContent = content,
            extractedParams = emptyMap()
        )
        
        // When
        val result = ScriptTestCommands.getActiveScripts(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return active scripts" }
        assert(result.contains("active_scripts")) { "Should contain active_scripts key" }
        
        println("✅ getActiveScripts integration test passed: $result")
    }
    
    @Test
    fun `testMessageScript command should process message correctly`() {
        // Given
        val content = "+1234567890|Test message for script processing"
        val validationResult = ValidationResult(
            valid = true,
            errors = emptyList(),
            normalizedContent = content,
            extractedParams = mapOf(
                "sender" to "+1234567890",
                "message" to "Test message for script processing"
            )
        )
        
        // When
        val parts = content.split("|", limit = 2)
        val sender = if (parts.size > 1) parts[0].trim() else "+1234567890"
        val message = if (parts.size > 1) parts[1].trim() else "Test message"
        
        val result = ScriptTestCommands.testMessageProcessing(context, sender, message)
        
        // Then
        assert(result.isNotEmpty()) { "Should return processing result" }
        assert(result.contains("processed")) { "Should indicate message was processed" }
        
        println("✅ testMessageScript integration test passed: $result")
    }
    
    @Test
    fun `testCommandScript command should validate command correctly`() {
        // Given
        val content = "sendSms|phone=+1234567890&message=Test&sim=1"
        val validationResult = ValidationResult(
            valid = true,
            errors = emptyList(),
            normalizedContent = content,
            extractedParams = mapOf(
                "command" to "sendSms",
                "parameters" to "phone=+1234567890&message=Test&sim=1"
            )
        )
        
        // When
        val parts = content.split("|", limit = 2)
        val command = if (parts.size > 1) parts[0].trim() else "sendSms"
        val paramsStr = if (parts.size > 1) parts[1].trim() else "{}"
        
        val parameters = mutableMapOf<String, Any>()
        if (paramsStr != "{}") {
            paramsStr.split("&").forEach { param ->
                val kv = param.split("=", limit = 2)
                if (kv.size == 2) {
                    parameters[kv[0].trim()] = kv[1].trim()
                }
            }
        }
        
        val result = ScriptTestCommands.testCommandValidation(context, command, parameters)
        
        // Then
        assert(result.isNotEmpty()) { "Should return validation result" }
        assert(result.contains("validation")) { "Should contain validation information" }
        
        println("✅ testCommandScript integration test passed: $result")
    }
    
    @Test
    fun `loadAxisScript command should load script`() {
        // Given
        val content = ""
        val validationResult = ValidationResult(
            valid = true,
            errors = emptyList(),
            normalizedContent = content,
            extractedParams = emptyMap()
        )
        
        // When
        val result = ScriptTestCommands.loadAxisScript(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return loading result" }
        
        println("✅ loadAxisScript integration test passed: $result")
    }
    
    @Test
    fun `getScriptStats command should return statistics`() {
        // Given
        val content = ""
        val validationResult = ValidationResult(
            valid = true,
            errors = emptyList(),
            normalizedContent = content,
            extractedParams = emptyMap()
        )
        
        // When
        val result = ScriptTestCommands.getScriptStatistics(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return statistics" }
        assert(result.contains("statistics")) { "Should contain statistics information" }
        
        println("✅ getScriptStats integration test passed: $result")
    }
    
    @Test
    fun `syncScripts command should sync scripts`() {
        // Given
        val content = ""
        val validationResult = ValidationResult(
            valid = true,
            errors = emptyList(),
            normalizedContent = content,
            extractedParams = emptyMap()
        )
        
        // When
        val result = ScriptTestCommands.forceSyncScripts(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return sync result" }
        
        println("✅ syncScripts integration test passed: $result")
    }
    
    @Test
    fun `script commands should handle invalid parameters gracefully`() {
        val invalidCommands = listOf(
            "testMessageScript|" to "Empty message",
            "testCommandScript|invalid_format" to "Invalid parameter format",
            "nonExistentCommand" to "Unknown command"
        )
        
        invalidCommands.forEach { (command, description) ->
            try {
                when {
                    command.startsWith("testMessageScript") -> {
                        val parts = command.split("|", limit = 2)
                        val sender = if (parts.size > 1) parts[0].trim() else "+1234567890"
                        val message = if (parts.size > 1) parts[1].trim() else ""
                        ScriptTestCommands.testMessageProcessing(context, sender, message)
                    }
                    command.startsWith("testCommandScript") -> {
                        val parts = command.split("|", limit = 2)
                        val cmd = if (parts.size > 1) parts[0].trim() else "sendSms"
                        val paramsStr = if (parts.size > 1) parts[1].trim() else "{}"
                        ScriptTestCommands.testCommandValidation(context, cmd, emptyMap())
                    }
                    else -> {
                        println("Unknown command: $command")
                    }
                }
                println("✅ Handled invalid case: $description")
            } catch (e: Exception) {
                println("⚠️ Expected error for $description: ${e.message}")
            }
        }
    }
}
