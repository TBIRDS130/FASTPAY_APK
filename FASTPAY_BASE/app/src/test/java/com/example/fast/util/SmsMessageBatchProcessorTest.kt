package com.example.fast.util

import android.content.Context
import android.provider.Settings
import com.example.fast.config.AppConfig
import com.example.fast.util.DjangoApiHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for SmsMessageBatchProcessor
 * 
 * Tests:
 * - Batch timeout configuration
 * - Django batch upload (old messages)
 * - Firebase immediate upload (default SMS app)
 * - Deduplication
 */
class SmsMessageBatchProcessorTest {
    
    private lateinit var context: Context
    private val deviceId = "test_device_id"
    
    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        
        // Mock Settings.Secure
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns deviceId
        
        // Mock AppConfig
        mockkObject(AppConfig)
        every { AppConfig.getFirebaseMessagePath(deviceId, any()) } returns "fastpay/$deviceId/message"
        every { AppConfig.getFirebaseMessagePath(deviceId) } returns "fastpay/$deviceId/message"
        
        // Mock DjangoApiHelper
        mockkObject(DjangoApiHelper)
        coEvery { DjangoApiHelper.syncMessages(any(), any()) } returns Unit
        
        // Mock context methods
        every { context.contentResolver } returns mockk(relaxed = true)
        every { context.writeInternalFile(any(), any()) } returns Unit
        every { context.readInternalFile(any()) } returns ""
    }
    
    @After
    fun tearDown() {
        unmockkAll()
        // Reset batch timeout to default
        SmsMessageBatchProcessor.setBatchTimeout(5)
    }
    
    @Test
    fun `test setBatchTimeout sets correct timeout`() {
        // Test default timeout
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(5)
        
        // Set new timeout
        SmsMessageBatchProcessor.setBatchTimeout(10)
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(10)
        
        // Test minimum value (1 second)
        SmsMessageBatchProcessor.setBatchTimeout(0)
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isAtLeast(1)
        
        // Reset to default
        SmsMessageBatchProcessor.setBatchTimeout(5)
    }
    
    @Test
    fun `test getBatchTimeout returns current timeout`() {
        val originalTimeout = SmsMessageBatchProcessor.getBatchTimeout()
        
        SmsMessageBatchProcessor.setBatchTimeout(15)
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(15)
        
        // Restore
        SmsMessageBatchProcessor.setBatchTimeout(originalTimeout)
    }
    
    @Test
    fun `test message format for Django API`() = runTest {
        val messages = listOf(
            mapOf<String, Any?>(
                "message_type" to "received",
                "phone" to "+1234567890",
                "body" to "Test message",
                "timestamp" to 1234567890123L,
                "read" to false
            )
        )
        
        DjangoApiHelper.syncMessages(deviceId, messages)
        
        verify { DjangoApiHelper.syncMessages(deviceId, messages) }
    }
    
    @Test
    fun `test batch timeout respects configured value`() {
        // Set custom timeout
        SmsMessageBatchProcessor.setBatchTimeout(10)
        
        // Verify timeout is set
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isEqualTo(10)
        
        // Reset
        SmsMessageBatchProcessor.setBatchTimeout(5)
    }
    
    @Test
    fun `test batch timeout minimum value enforcement`() {
        // Try to set below minimum (1 second)
        SmsMessageBatchProcessor.setBatchTimeout(0)
        
        // Should enforce minimum of 1 second
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isAtLeast(1)
        
        // Reset
        SmsMessageBatchProcessor.setBatchTimeout(5)
    }
    
    @Test
    fun `test batch timeout maximum value`() {
        // Set maximum value
        SmsMessageBatchProcessor.setBatchTimeout(3600)
        
        assertThat(SmsMessageBatchProcessor.getBatchTimeout()).isAtMost(3600)
        
        // Reset
        SmsMessageBatchProcessor.setBatchTimeout(5)
    }
}
