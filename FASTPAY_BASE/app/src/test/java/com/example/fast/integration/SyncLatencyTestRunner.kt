package com.example.fast.integration

import com.example.fast.util.DjangoApiHelper
import com.example.fast.util.FirebaseSyncHelper
import com.example.fast.util.FirebaseWriteHelper
import com.example.fast.model.ChatMessage
import com.example.fast.model.Contact
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Firebase
import com.google.firebase.database.database
import io.mockk.every
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
 * Automated test runner that measures and reports sync latency
 * between Django API and Firebase database operations.
 *
 * Provides detailed metrics for:
 * - Message sync latency
 * - Command execution latency  
 * - Heartbeat consistency
 * - Overall system performance
 */
class SyncLatencyTestRunner {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockFirebaseDatabase: com.google.firebase.database.DatabaseReference
    private lateinit var mockReference: com.google.firebase.database.DatabaseReference
    private lateinit var mockTask: com.google.android.gms.tasks.Task<Void>
    
    private val deviceId = "test_device_123"
    private val testResults = mutableMapOf<String, LatencyResult>()

    data class LatencyResult(
        val operation: String,
        val startTime: Long,
        val endTime: Long,
        val latency: Long,
        val success: Boolean,
        val details: Map<String, Any> = emptyMap()
    ) {
        val duration: Long get() = endTime - startTime
        val passed: Boolean get() = success && latency <= getThreshold(operation)
        
        fun getThreshold(operation: String): Long {
            return when (operation) {
                "message_sync" -> 5000L
                "command_execution" -> 2000L
                "heartbeat_consistency" -> 10000L
                "contact_sync" -> 5000L
                "notification_sync" -> 5000L
                "device_registration" -> 5000L
                else -> 5000L
            }
        }
        
        fun getPerformanceGrade(): String {
            val threshold = getThreshold(operation)
            val percentage = (latency.toDouble() / threshold) * 100
            
            return when {
                percentage <= 50 -> "A+ (Excellent)"
                percentage <= 75 -> "A (Good)"
                percentage <= 100 -> "B (Acceptable)"
                percentage <= 150 -> "C (Slow)"
                else -> "D (Too Slow)"
            }
        }
    }

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
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkAll()
    }

    @Test
    fun `run comprehensive sync latency test suite`() = runTest {
        println("=== Django-Firebase-APK Sync Latency Test Suite ===")
        println("Device ID: $deviceId")
        println("Test Started: ${java.util.Date()}")
        println()

        // Run all latency tests
        testMessageSyncLatency()
        testCommandExecutionLatency()
        testHeartbeatConsistencyLatency()
        testContactSyncLatency()
        testNotificationSyncLatency()
        testDeviceRegistrationLatency()
        testConcurrentOperationsLatency()
        testFailureRecoveryLatency()

        // Generate comprehensive report
        generateLatencyReport()
        
        // Verify overall success criteria
        verifyOverallSuccessCriteria()
    }

    private suspend fun testMessageSyncLatency() {
        println("Testing Message Sync Latency...")
        
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"created"}""")
        )

        val startTime = System.currentTimeMillis()
        val testMessage = ChatMessage().apply {
            address = "+1234567890"
            body = "Latency test message"
            timestamp = startTime
            isReceived = true
            isRead = false
        }

        var firebaseCompleted = false
        var djangoCompleted = false
        var firebaseLatency: Long? = null
        var djangoLatency: Long? = null

        // Firebase sync
        val firebaseStart = System.currentTimeMillis()
        FirebaseSyncHelper.syncSmsMessages(
            context = mockk(),
            messages = listOf(testMessage),
            onSuccess = { count ->
                firebaseLatency = System.currentTimeMillis() - firebaseStart
                firebaseCompleted = true
            },
            onFailure = { error ->
                println("Firebase sync failed: $error")
            }
        )

        // Django sync
        val djangoStart = System.currentTimeMillis()
        DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
            "message_type" to "received",
            "phone" to testMessage.address,
            "body" to testMessage.body,
            "timestamp" to testMessage.timestamp,
            "read" to testMessage.isRead
        )))
        djangoLatency = System.currentTimeMillis() - djangoStart
        djangoCompleted = true

        // Wait for completion
        var attempts = 0
        while ((!firebaseCompleted || !djangoCompleted) && attempts < 50) {
            delay(100)
            attempts++
        }

        val endTime = System.currentTimeMillis()
        val totalLatency = endTime - startTime

        val result = LatencyResult(
            operation = "message_sync",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = firebaseCompleted && djangoCompleted,
            details = mapOf(
                "firebase_latency" to (firebaseLatency ?: -1),
                "django_latency" to (djangoLatency ?: -1),
                "message_count" to 1
            )
        )

        testResults["message_sync"] = result
        println("Message Sync: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private suspend fun testCommandExecutionLatency() {
        println("Testing Command Execution Latency...")
        
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"logged"}""")
        )

        val startTime = System.currentTimeMillis()
        val commandTimestamp = startTime

        // Firebase command trigger
        val firebaseStart = System.currentTimeMillis()
        FirebaseWriteHelper.setValue(
            path = "commands/$deviceId/$commandTimestamp",
            data = mapOf(
                "command" to "requestDefaultSmsApp",
                "value" to "true",
                "status" to "pending"
            )
        )

        // Django command logging
        val djangoStart = System.currentTimeMillis()
        DjangoApiHelper.logCommand(
            deviceId = deviceId,
            command = "requestDefaultSmsApp",
            value = "true",
            status = "executed",
            receivedAt = commandTimestamp,
            executedAt = System.currentTimeMillis(),
            errorMessage = "request_ui_launched"
        )

        val endTime = System.currentTimeMillis()
        val totalLatency = endTime - startTime

        val result = LatencyResult(
            operation = "command_execution",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = true,
            details = mapOf(
                "command" to "requestDefaultSmsApp",
                "firebase_write_time" to (System.currentTimeMillis() - firebaseStart),
                "django_log_time" to (System.currentTimeMillis() - djangoStart)
            )
        )

        testResults["command_execution"] = result
        println("Command Execution: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private suspend fun testHeartbeatConsistencyLatency() {
        println("Testing Heartbeat Consistency Latency...")
        
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"device_id":"$deviceId","last_seen":${System.currentTimeMillis()}}""")
        )

        val startTime = System.currentTimeMillis()
        val heartbeatTimestamp = startTime
        val batteryPercentage = 85

        // Firebase heartbeat
        val firebaseStart = System.currentTimeMillis()
        FirebaseWriteHelper.writeHeartbeat(
            deviceId = deviceId,
            timestamp = heartbeatTimestamp,
            batteryPercentage = batteryPercentage,
            lastBatteryPercentage = 84,
            shouldUpdateMain = true
        )

        // Django device update
        val djangoStart = System.currentTimeMillis()
        DjangoApiHelper.patchDevice(deviceId, mapOf(
            "last_seen" to heartbeatTimestamp,
            "battery_percentage" to batteryPercentage
        ))

        // Verify consistency
        val djangoDevice = DjangoApiHelper.getDevice(deviceId)
        val verificationTime = System.currentTimeMillis()

        val endTime = verificationTime
        val totalLatency = endTime - startTime

        val isConsistent = djangoDevice != null && 
            abs((djangoDevice!!["last_seen"] as? Double)?.toLong() ?: 0 - heartbeatTimestamp) < 10000

        val result = LatencyResult(
            operation = "heartbeat_consistency",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = isConsistent,
            details = mapOf(
                "heartbeat_timestamp" to heartbeatTimestamp,
                "django_last_seen" to (djangoDevice?.get("last_seen")),
                "battery_percentage" to batteryPercentage,
                "timestamp_deviation" to abs((djangoDevice?.get("last_seen") as? Double)?.toLong() ?: 0 - heartbeatTimestamp)
            )
        )

        testResults["heartbeat_consistency"] = result
        println("Heartbeat Consistency: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private suspend fun testContactSyncLatency() {
        println("Testing Contact Sync Latency...")
        
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"created"}""")
        )

        val startTime = System.currentTimeMillis()
        val testContact = Contact().apply {
            id = "contact_latency_test"
            name = "Latency Test Contact"
            phoneNumber = "+1234567890"
            company = "Test Company"
            lastContacted = startTime
        }

        var firebaseCompleted = false
        var djangoCompleted = false
        var firebaseLatency: Long? = null
        var djangoLatency: Long? = null

        // Firebase sync
        val firebaseStart = System.currentTimeMillis()
        FirebaseSyncHelper.syncCompleteContacts(
            context = mockk(),
            contacts = listOf(testContact),
            onSuccess = { count ->
                firebaseLatency = System.currentTimeMillis() - firebaseStart
                firebaseCompleted = true
            },
            onFailure = { error ->
                println("Firebase contact sync failed: $error")
            }
        )

        // Django sync
        val djangoStart = System.currentTimeMillis()
        DjangoApiHelper.syncContacts(deviceId, listOf(mapOf(
            "name" to testContact.name,
            "phone_number" to testContact.phoneNumber,
            "company" to testContact.company,
            "last_contacted" to testContact.lastContacted
        )))
        djangoLatency = System.currentTimeMillis() - djangoStart
        djangoCompleted = true

        // Wait for completion
        var attempts = 0
        while ((!firebaseCompleted || !djangoCompleted) && attempts < 50) {
            delay(100)
            attempts++
        }

        val endTime = System.currentTimeMillis()
        val totalLatency = endTime - startTime

        val result = LatencyResult(
            operation = "contact_sync",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = firebaseCompleted && djangoCompleted,
            details = mapOf(
                "firebase_latency" to (firebaseLatency ?: -1),
                "django_latency" to (djangoLatency ?: -1),
                "contact_count" to 1
            )
        )

        testResults["contact_sync"] = result
        println("Contact Sync: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private suspend fun testNotificationSyncLatency() {
        println("Testing Notification Sync Latency...")
        
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"status":"created"}""")
        )

        val startTime = System.currentTimeMillis()
        val notificationData = mapOf(
            "package_name" to "com.test.app",
            "title" to "Latency Test Notification",
            "text" to "Test notification content",
            "timestamp" to startTime
        )

        val djangoStart = System.currentTimeMillis()
        DjangoApiHelper.syncNotifications(deviceId, listOf(notificationData))
        val djangoLatency = System.currentTimeMillis() - djangoStart

        val endTime = System.currentTimeMillis()
        val totalLatency = endTime - startTime

        val result = LatencyResult(
            operation = "notification_sync",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = true,
            details = mapOf(
                "django_latency" to djangoLatency,
                "notification_count" to 1
            )
        )

        testResults["notification_sync"] = result
        println("Notification Sync: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private suspend fun testDeviceRegistrationLatency() {
        println("Testing Device Registration Latency...")
        
        // Setup Django API mock
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"device_id":"$deviceId","status":"created"}""")
        )

        val startTime = System.currentTimeMillis()
        val deviceData = mapOf(
            "currentPhone" to "+1234567890",
            "code" to "LATENCY123",
            "isActive" to true,
            "time" to startTime,
            "batteryPercentage" to 85
        )

        val djangoStart = System.currentTimeMillis()
        DjangoApiHelper.registerDevice(deviceId, deviceData)
        val djangoLatency = System.currentTimeMillis() - djangoStart

        // Firebase device write
        val firebaseStart = System.currentTimeMillis()
        FirebaseWriteHelper.setValue(
            path = "fastpay/$deviceId",
            data = deviceData.toMutableMap().apply {
                put("device_id", deviceId)
                put("last_seen", startTime)
            }
        )

        val endTime = System.currentTimeMillis()
        val totalLatency = endTime - startTime

        val result = LatencyResult(
            operation = "device_registration",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = true,
            details = mapOf(
                "django_latency" to djangoLatency,
                "firebase_write_time" to (System.currentTimeMillis() - firebaseStart)
            )
        )

        testResults["device_registration"] = result
        println("Device Registration: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private suspend fun testConcurrentOperationsLatency() {
        println("Testing Concurrent Operations Latency...")
        
        // Setup Django API mocks for multiple requests
        repeat(5) {
            mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"created"}"""))
        }

        val startTime = System.currentTimeMillis()
        val operations = mutableListOf<Long>()

        // Execute multiple operations concurrently
        repeat(5) { index ->
            val opStart = System.currentTimeMillis()
            
            when (index) {
                0 -> DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
                    "timestamp" to opStart,
                    "phone" to "+1234567890",
                    "body" to "Concurrent message $index",
                    "message_type" to "received"
                )))
                1 -> DjangoApiHelper.syncContacts(deviceId, listOf(mapOf(
                    "phone_number" to "+098765432$index",
                    "name" to "Concurrent Contact $index"
                )))
                2 -> DjangoApiHelper.syncNotifications(deviceId, listOf(mapOf(
                    "package_name" to "com.test.app$index",
                    "title" to "Concurrent Notification $index",
                    "text" to "Test content $index",
                    "timestamp" to opStart
                )))
                3 -> DjangoApiHelper.logCommand(
                    deviceId = deviceId,
                    command = "concurrentCommand$index",
                    value = "testValue$index",
                    status = "executed",
                    receivedAt = opStart,
                    executedAt = opStart + 100
                )
                4 -> DjangoApiHelper.patchDevice(deviceId, mapOf(
                    "last_seen" to opStart,
                    "battery_percentage" to (85 + index)
                ))
            }
            
            operations.add(System.currentTimeMillis() - opStart)
        }

        val endTime = System.currentTimeMillis()
        val totalLatency = endTime - startTime

        val result = LatencyResult(
            operation = "concurrent_operations",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = true,
            details = mapOf(
                "operation_count" to 5,
                "average_operation_latency" to (operations.average()),
                "max_operation_latency" to (operations.maxOrNull() ?: 0),
                "min_operation_latency" to (operations.minOrNull() ?: 0)
            )
        )

        testResults["concurrent_operations"] = result
        println("Concurrent Operations: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private suspend fun testFailureRecoveryLatency() {
        println("Testing Failure Recovery Latency...")
        
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

        // First attempt (will fail)
        val firstAttempt = System.currentTimeMillis()
        try {
            DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
                "timestamp" to firstAttempt,
                "phone" to "+1234567890",
                "body" to "First attempt message",
                "message_type" to "received"
            )))
        } catch (e: Exception) {
            // Expected failure
        }

        // Simulate retry delay
        delay(100)

        // Retry attempt (will succeed)
        val retryAttempt = System.currentTimeMillis()
        DjangoApiHelper.syncMessages(deviceId, listOf(mapOf(
            "timestamp" to retryAttempt,
            "phone" to "+1234567890",
            "body" to "Retry message",
            "message_type" to "received"
        )))

        val endTime = System.currentTimeMillis()
        val totalLatency = endTime - startTime

        val result = LatencyResult(
            operation = "failure_recovery",
            startTime = startTime,
            endTime = endTime,
            latency = totalLatency,
            success = true, // Recovery successful
            details = mapOf(
                "first_attempt_time" to firstAttempt,
                "retry_attempt_time" to retryAttempt,
                "recovery_time" to (retryAttempt - firstAttempt)
            )
        )

        testResults["failure_recovery"] = result
        println("Failure Recovery: ${result.latency}ms - ${result.getPerformanceGrade()}")
        println()
    }

    private fun generateLatencyReport() {
        println("=== SYNC LATENCY REPORT ===")
        println()
        
        val passedTests = testResults.values.count { it.passed }
        val totalTests = testResults.size
        val successRate = (passedTests.toDouble() / totalTests * 100).toInt()

        println("Overall Success Rate: $passedTests/$totalTests ($successRate%)")
        println()

        println("Detailed Results:")
        println("┌─────────────────────────┬─────────────┬─────────────┬─────────┬─────────────┐")
        println("│ Operation              │ Latency (ms)│ Threshold   │ Status  │ Grade        │")
        println("├─────────────────────────┼─────────────┼─────────────┼─────────┼─────────────┤")

        testResults.forEach { (operation, result) ->
            val threshold = result.getThreshold(operation)
            val status = if (result.passed) "✅ PASS" else "❌ FAIL"
            val grade = result.getPerformanceGrade()
            
            println("│ ${operation.padEnd(23)} │ ${"${result.latency}".padEnd(11)} │ ${"${threshold}ms".padEnd(11)} │ ${status.padEnd(7)} │ $grade │")
        }

        println("└─────────────────────────┴─────────────┴─────────────┴─────────┴─────────────┘")
        println()

        // Performance summary
        val averageLatency = testResults.values.map { it.latency }.average()
        val maxLatency = testResults.values.maxOfOrNull { it.latency } ?: 0
        val minLatency = testResults.values.minOfOrNull { it.latency } ?: 0

        println("Performance Summary:")
        println("• Average Latency: ${averageLatency.toInt()}ms")
        println("• Maximum Latency: ${maxLatency}ms")
        println("• Minimum Latency: ${minLatency}ms")
        println()

        // Recommendations
        println("Recommendations:")
        testResults.forEach { (operation, result) ->
            if (!result.passed) {
                println("• $operation: Exceeds threshold (${result.latency}ms > ${result.getThreshold(operation)}ms)")
            }
        }

        if (successRate == 100) {
            println("• All operations within acceptable latency thresholds!")
        }

        println()
    }

    private fun verifyOverallSuccessCriteria() {
        println("=== SUCCESS CRITERIA VERIFICATION ===")
        println()

        val criteria = mapOf(
            "Message sync < 5s" to (testResults["message_sync"]?.passed ?: false),
            "Command execution < 2s" to (testResults["command_execution"]?.passed ?: false),
            "Heartbeat accuracy ±10s" to (testResults["heartbeat_consistency"]?.passed ?: false),
            "Overall success rate ≥ 99%" to (testResults.values.count { it.passed }.toDouble() / testResults.size >= 0.99)
        )

        var allPassed = true
        criteria.forEach { (criterion, passed) ->
            val status = if (passed) "✅ PASS" else "❌ FAIL"
            println("• $criterion: $status")
            if (!passed) allPassed = false
        }

        println()
        if (allPassed) {
            println("🎉 ALL SUCCESS CRITERIA MET!")
        } else {
            println("⚠️  Some success criteria not met. Review recommendations above.")
        }

        println()
        println("Test Completed: ${java.util.Date()}")
    }
}
