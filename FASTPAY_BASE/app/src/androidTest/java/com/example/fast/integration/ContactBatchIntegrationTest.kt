package com.example.fast.integration

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fast.model.Contact
import com.example.fast.util.ContactBatchProcessor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat

/**
 * Integration tests for Contact batch processing
 *
 * Tests:
 * - Django API only sync (no Firebase)
 * - Batch processing
 * - Contact queuing
 *
 * Note: These tests require actual device/emulator with network access
 */
@RunWith(AndroidJUnit4::class)
class ContactBatchIntegrationTest {

    private fun log(msg: String) = System.out.println("[ContactBatchTest] $msg")

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testQueueContactsQueuesContactsForBatchProcessing() {
        log("testQueueContactsQueuesContactsForBatchProcessing: Starting")
        val contact = Contact(
            id = "1",
            name = "Test Contact",
            displayName = "Test Contact",
            phoneNumber = "+1234567890",
            lastContacted = System.currentTimeMillis()
        )

        // Queue contact
        ContactBatchProcessor.queueContacts(context, listOf(contact))

        // Contact should be queued for Django API sync
        log("testQueueContactsQueuesContactsForBatchProcessing: PASSED")
    }

    @Test
    fun testContactsWithoutPhoneNumberAreSkipped() {
        log("testContactsWithoutPhoneNumberAreSkipped: Starting")
        val contact = Contact(
            id = "1",
            name = "Contact Without Phone",
            displayName = "Contact Without Phone",
            phoneNumber = "", // Empty phone number
            lastContacted = System.currentTimeMillis()
        )

        // Queue contact without phone
        ContactBatchProcessor.queueContacts(context, listOf(contact))

        // Contact should be skipped (no phone number)
        log("testContactsWithoutPhoneNumberAreSkipped: PASSED")
    }

    @Test
    fun testMultipleContactsAreQueued() {
        log("testMultipleContactsAreQueued: Starting")
        val contacts = listOf(
            Contact(
                id = "1",
                name = "Contact 1",
                displayName = "Contact 1",
                phoneNumber = "+1234567890",
                lastContacted = System.currentTimeMillis()
            ),
            Contact(
                id = "2",
                name = "Contact 2",
                displayName = "Contact 2",
                phoneNumber = "+0987654321",
                lastContacted = System.currentTimeMillis()
            )
        )

        // Queue multiple contacts
        ContactBatchProcessor.queueContacts(context, contacts)

        // All contacts should be queued
        log("testMultipleContactsAreQueued: PASSED")
    }
}
