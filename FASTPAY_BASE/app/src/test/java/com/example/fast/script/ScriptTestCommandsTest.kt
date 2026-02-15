package com.example.fast.script

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test cases for ScriptTestCommands functionality
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScriptTestCommandsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Initialize script system for testing
        ScriptInitializer.initialize(context)
    }
    
    @Test
    fun `getActiveScripts should return script list`() {
        // When
        val result = ScriptTestCommands.getActiveScripts(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return at least one script" }
        assert(result.contains("active_scripts")) { "Should contain active_scripts key" }
        println("✅ getActiveScripts test passed: $result")
    }
    
    @Test
    fun `testMessageProcessing should handle test message`() {
        // Given
        val sender = "+1234567890"
        val message = "Test message for script processing"
        
        // When
        val result = ScriptTestCommands.testMessageProcessing(context, sender, message)
        
        // Then
        assert(result.isNotEmpty()) { "Should return processing result" }
        assert(result.contains("processed")) { "Should indicate message was processed" }
        println("✅ testMessageProcessing test passed: $result")
    }
    
    @Test
    fun `testCommandValidation should handle command validation`() {
        // Given
        val command = "sendSms"
        val parameters = mapOf<String, Any>(
            "phone" to "+1234567890",
            "message" to "Test message",
            "sim" to 1
        )
        
        // When
        val result = ScriptTestCommands.testCommandValidation(context, command, parameters)
        
        // Then
        assert(result.isNotEmpty()) { "Should return validation result" }
        assert(result.contains("validation")) { "Should contain validation information" }
        println("✅ testCommandValidation test passed: $result")
    }
    
    @Test
    fun `loadAxisScript should load script successfully`() {
        // When
        val result = ScriptTestCommands.loadAxisScript(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return loading result" }
        println("✅ loadAxisScript test passed: $result")
    }
    
    @Test
    fun `getScriptStatistics should return stats`() {
        // When
        val result = ScriptTestCommands.getScriptStatistics(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return statistics" }
        assert(result.contains("statistics")) { "Should contain statistics information" }
        println("✅ getScriptStatistics test passed: $result")
    }
    
    @Test
    fun `forceSyncScripts should sync scripts`() {
        // When
        val result = ScriptTestCommands.forceSyncScripts(context)
        
        // Then
        assert(result.isNotEmpty()) { "Should return sync result" }
        println("✅ forceSyncScripts test passed: $result")
    }
    
    @Test
    fun `testMessageProcessing with different senders`() {
        val testCases = listOf(
            "+1234567890" to "Normal message",
            "UNKNOWN" to "Unknown sender message",
            "SPAM_DETECTED" to "Spam message"
        )
        
        testCases.forEach { (sender, message) ->
            val result = ScriptTestCommands.testMessageProcessing(context, sender, message)
            assert(result.isNotEmpty()) { "Should handle sender: $sender" }
            println("✅ Message processing test for $sender: $result")
        }
    }
    
    @Test
    fun `testCommandValidation with different commands`() {
        val testCommands = listOf(
            "sendSms" to mapOf("phone" to "+1234567890", "message" to "Test"),
            "showNotification" to mapOf("title" to "Test", "message" to "Test"),
            "updateApk" to mapOf("url" to "https://example.com/app.apk"),
            "invalidCommand" to emptyMap<String, Any>()
        )
        
        testCommands.forEach { (command, params) ->
            val result = ScriptTestCommands.testCommandValidation(context, command, params)
            assert(result.isNotEmpty()) { "Should validate command: $command" }
            println("✅ Command validation test for $command: $result")
        }
    }
}
