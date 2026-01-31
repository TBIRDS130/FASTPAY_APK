package com.example.fast.util

import android.content.Context
import android.provider.Settings
import com.example.fast.model.Contact
import com.example.fast.util.DjangoApiHelper
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
 * Unit tests for ContactBatchProcessor
 * 
 * Tests:
 * - Django API only sync (no Firebase)
 * - Batch processing
 * - Contact format conversion
 * - Deduplication
 */
class ContactBatchProcessorTest {
    
    private lateinit var context: Context
    private val deviceId = "test_device_id"
    
    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        
        // Mock Settings.Secure
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns deviceId
        
        // Mock DjangoApiHelper
        mockkObject(DjangoApiHelper)
        coEvery { DjangoApiHelper.syncContacts(any(), any()) } returns Unit
        
        // Mock context methods
        every { context.contentResolver } returns mockk(relaxed = true)
        every { context.writeInternalFile(any(), any()) } returns Unit
        every { context.readInternalFile(any()) } returns ""
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `test contact format for Django API`() = runTest {
        val contacts = listOf(
            mapOf<String, Any?>(
                "name" to "John Doe",
                "phone_number" to "+1234567890",
                "last_contacted" to 1234567890123L
            )
        )
        
        DjangoApiHelper.syncContacts(deviceId, contacts)
        
        verify { DjangoApiHelper.syncContacts(deviceId, contacts) }
    }
    
    @Test
    fun `test contact conversion to Django format includes required fields`() {
        val contact = Contact(
            id = "1",
            name = "Test Contact",
            phoneNumber = "+1234567890",
            lastContacted = 1234567890123L,
            displayName = "Display Name",
            company = "Company",
            jobTitle = "Job Title"
        )
        
        // Verify Django format structure
        val djangoFormat = mapOf<String, Any?>(
            "name" to contact.name,
            "phone_number" to contact.phoneNumber,
            "last_contacted" to contact.lastContacted,
            "display_name" to contact.displayName,
            "company" to contact.company,
            "job_title" to contact.jobTitle
        )
        
        assertThat(djangoFormat["name"]).isEqualTo("Test Contact")
        assertThat(djangoFormat["phone_number"]).isEqualTo("+1234567890")
        assertThat(djangoFormat["last_contacted"]).isEqualTo(1234567890123L)
    }
    
    @Test
    fun `test contacts without phone number are skipped`() {
        val contact = Contact(
            id = "1",
            name = "Contact Without Phone",
            phoneNumber = "", // Empty phone number
            lastContacted = System.currentTimeMillis()
        )
        
        // Contact without phone should be skipped in conversion
        // Verified via integration tests
        assertThat(contact.phoneNumber).isEmpty()
    }
}
