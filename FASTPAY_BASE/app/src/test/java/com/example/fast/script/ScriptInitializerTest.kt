package com.example.fast.script

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test cases for ScriptInitializer functionality
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScriptInitializerTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun `initialize should setup script system`() {
        // When
        try {
            ScriptInitializer.initialize(context)
            println("✅ ScriptInitializer.initialize() completed without errors")
        } catch (e: Exception) {
            println("❌ ScriptInitializer.initialize() failed: ${e.message}")
            throw e
        }
    }
    
    @Test
    fun `initialize should be idempotent`() {
        // When - Initialize twice
        ScriptInitializer.initialize(context)
        ScriptInitializer.initialize(context)
        
        // Then - Should not throw errors
        println("✅ ScriptInitializer.initialize() is idempotent")
    }
    
    @Test
    fun `initialize should setup default scripts`() {
        // When
        ScriptInitializer.initialize(context)
        
        // Then - Verify default scripts are available
        val activeScripts = ScriptTestCommands.getActiveScripts(context)
        assert(activeScripts.isNotEmpty()) { "Default scripts should be available after initialization" }
        
        println("✅ Default scripts initialized successfully: $activeScripts")
    }
}
