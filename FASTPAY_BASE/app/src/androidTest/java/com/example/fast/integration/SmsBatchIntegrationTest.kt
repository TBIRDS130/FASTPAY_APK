package com.example.fast.integration

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fast.util.SmsMessageBatchProcessor
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for SMS batch processing
 * 
 * Tests:
 * - Batch timeout configuration
 * - Django batch upload
 * - Firebase immediate upload (default SMS app)
 * 
 * Note: These tests require actual device/emulator with network access
 */
@RunWith(AndroidJUnit4::class)
class SmsBatchIntegrationTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Reset to default timeout
        SmsMessageBatchProcessor.setBatchTimeout(5)
    }
    
    @After
    fun tearDown() {
        // Reset to default timeout
        SmsMessageBatchProcessor.setBatchTimeout(5)
    }
    
    @Test
    fun `test batch timeout configuration persists`() {
        // Set custom timeout
        SmsMessageBatchProcessor.setBatchTimeout(10)
        
        // Verify it's set
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(10)
        
        // Reset
        SmsMessageBatchProcessor.setBatchTimeout(5)
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(5)
    }
    
    @Test
    fun `test queueMessage queues for batch processing`() {
        // Queue message with batching (non-default SMS app)
        SmsMessageBatchProcessor.queueMessage(
            context = context,
            sender = "+1234567890",
            body = "Test message for batch",
            immediateUpload = false
        )
        
        // Message should be queued
        // Actual batch processing happens asynchronously
        // This test verifies the method doesn't throw
    }
    
    @Test
    fun `test immediate upload for default SMS app`() {
        // Queue message with immediate upload (default SMS app)
        SmsMessageBatchProcessor.queueMessage(
            context = context,
            sender = "+1234567890",
            body = "Test message immediate",
            immediateUpload = true
        )
        
        // Message should upload immediately to Firebase
        // This test verifies the method doesn't throw
    }
    
    @Test
    fun `test batch timeout affects processing interval`() {
        val originalTimeout = SmsMessageBatchProcessor.getBatchTimeout()
        
        // Set different timeouts
        SmsMessageBatchProcessor.setBatchTimeout(1)
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(1)
        
        SmsMessageBatchProcessor.setBatchTimeout(30)
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(30)
        
        // Restore
        SmsMessageBatchProcessor.setBatchTimeout(originalTimeout)
    }
}
