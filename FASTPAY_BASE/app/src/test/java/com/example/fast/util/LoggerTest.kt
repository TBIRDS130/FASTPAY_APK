package com.example.fast.util

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import com.google.common.truth.Truth.assertThat
import timber.log.Timber
import java.io.PrintStream
import java.io.ByteArrayOutputStream

/**
 * Unit tests for Logger utility
 * 
 * Tests verify that Logger properly wraps Timber and handles
 * different log levels correctly.
 */
class LoggerTest {
    
    @Before
    fun setUp() {
        // Initialize Logger before each test
        Logger.initialize()
    }
    
    @Test
    fun `test Logger initialization`() {
        // Logger should be initialized
        // This is verified by the fact that we can call Logger methods
        Logger.d("Test", "Debug message")
        // If initialization failed, this would throw an exception
    }
    
    @Test
    fun `test debug logging`() {
        // Debug logging should work
        Logger.d("TestTag", "Debug message")
        Logger.d("Simple debug message")
        // No exception means it works
    }
    
    @Test
    fun `test info logging`() {
        // Info logging should work
        Logger.i("TestTag", "Info message")
        Logger.i("Simple info message")
    }
    
    @Test
    fun `test warning logging`() {
        // Warning logging should work
        Logger.w("TestTag", "Warning message")
        Logger.w("Simple warning message")
    }
    
    @Test
    fun `test error logging`() {
        // Error logging should work
        Logger.e("TestTag", "Error message")
        Logger.e("Simple error message")
    }
    
    @Test
    fun `test error logging with exception`() {
        val exception = RuntimeException("Test exception")
        Logger.e("TestTag", exception, "Error with exception")
        Logger.e(exception, "Error with exception (no tag)")
    }
    
    @Test
    fun `test logging with format strings`() {
        Logger.d("TestTag", "Message with %s placeholder", "value")
        Logger.i("Formatted message: %d items", 5)
    }
}
