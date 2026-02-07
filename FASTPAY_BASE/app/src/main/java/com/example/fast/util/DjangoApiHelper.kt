package com.example.fast.util

import android.os.Build
import com.example.fast.config.AppConfig
import com.example.fast.core.error.FastPayException
import com.example.fast.core.result.Result
import com.example.fast.util.network.RetryPolicy
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

/**
 * DjangoApiHelper
 *
 * Helper class for interacting with the Django backend API.
 *
 * Optimizations (v2.0):
 * - OkHttp for connection pooling and better performance
 * - GZIP compression for large request bodies (>1KB)
 * - Automatic retry with exponential backoff
 * - Improved error handling and logging
 * - Thread-safe singleton OkHttpClient
 *
 * @see RetryPolicy for retry configuration
 */
object DjangoApiHelper {

    private const val TAG = "DjangoApiHelper"
    private val gson = Gson()

    // Timeout configuration
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    // Compression threshold (compress if body > 1KB)
    private const val COMPRESSION_THRESHOLD_BYTES = 1024

    // Media types
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val GZIP_JSON_MEDIA_TYPE = "application/json".toMediaType()

    /**
     * Singleton OkHttpClient with connection pooling
     * Configured for optimal performance with Django backend
     */
    private val httpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            LogHelper.d(TAG, message)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                // Add default headers to all requests
                val request = chain.request().newBuilder()
                    .addHeader("Accept", AppConfig.ApiHeaders.ACCEPT)
                    .addHeader("Accept-Encoding", "gzip")
                    .build()
                chain.proceed(request)
            }
            .retryOnConnectionFailure(true)
            .build()
    }

    // BuildConfig check helper (avoids import issues)
    private object BuildConfig {
        val DEBUG: Boolean = com.example.fast.BuildConfig.DEBUG
    }

    /**
     * Compress string to GZIP bytes if over threshold
     * @return Pair(compressed bytes, wasCompressed)
     */
    private fun compressIfNeeded(json: String): Pair<ByteArray, Boolean> {
        val jsonBytes = json.toByteArray(Charsets.UTF_8)

        if (jsonBytes.size < COMPRESSION_THRESHOLD_BYTES) {
            return Pair(jsonBytes, false)
        }

        return try {
            val byteStream = ByteArrayOutputStream()
            GZIPOutputStream(byteStream).use { gzipStream ->
                gzipStream.write(jsonBytes)
            }
            val compressed = byteStream.toByteArray()

            // Only use compression if it actually reduces size
            if (compressed.size < jsonBytes.size) {
                LogHelper.d(TAG, "Compressed request: ${jsonBytes.size} -> ${compressed.size} bytes " +
                        "(${(100 - compressed.size * 100 / jsonBytes.size)}% reduction)")
                Pair(compressed, true)
            } else {
                Pair(jsonBytes, false)
            }
        } catch (e: Exception) {
            LogHelper.w(TAG, "Compression failed, using uncompressed body", e)
            Pair(jsonBytes, false)
        }
    }

    /**
     * Execute HTTP POST request with retry and optional compression
     */
    private suspend fun executePost(
        endpoint: String,
        jsonBody: String,
        useRetry: Boolean = true
    ): ApiResponse {
        val url = "${AppConfig.DJANGO_API_BASE_URL}$endpoint"

        val operation: suspend () -> ApiResponse = {
            withContext(Dispatchers.IO) {
                val (bodyBytes, isCompressed) = compressIfNeeded(jsonBody)

                val requestBody = bodyBytes.toRequestBody(
                    if (isCompressed) GZIP_JSON_MEDIA_TYPE else JSON_MEDIA_TYPE
                )

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)

                if (isCompressed) {
                    requestBuilder.header("Content-Encoding", "gzip")
                }

                val request = requestBuilder.build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    ApiResponse(
                        code = response.code,
                        body = responseBody,
                        isSuccessful = response.isSuccessful
                    )
                }
            }
        }

        return if (useRetry) {
            val result = RetryPolicy.withRetry(
                RetryPolicy.networkErrorRetryConfig(maxRetries = 3, initialDelayMs = 1000)
            ) {
                val response = operation()
                if (!response.isSuccessful && response.code >= 500) {
                    // Retry server errors
                    throw IOException("Server error: ${response.code}")
                }
                response
            }

            when (result) {
                is RetryPolicy.Result.Success -> result.value
                is RetryPolicy.Result.Failure -> {
                    LogHelper.e(TAG, "Request failed after ${result.attempts} attempts: ${result.exception.message}")
                    ApiResponse(code = 0, body = result.exception.message, isSuccessful = false)
                }
            }
        } else {
            try {
                operation()
            } catch (e: Exception) {
                LogHelper.e(TAG, "Request failed: ${e.message}", e)
                ApiResponse(code = 0, body = e.message, isSuccessful = false)
            }
        }
    }

    /**
     * Execute HTTP GET request with retry
     */
    private suspend fun executeGet(
        endpoint: String,
        useRetry: Boolean = true
    ): ApiResponse {
        val url = "${AppConfig.DJANGO_API_BASE_URL}$endpoint"

        val operation: suspend () -> ApiResponse = {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    ApiResponse(
                        code = response.code,
                        body = responseBody,
                        isSuccessful = response.isSuccessful
                    )
                }
            }
        }

        return if (useRetry) {
            val result = RetryPolicy.withRetry(
                RetryPolicy.networkErrorRetryConfig(maxRetries = 3, initialDelayMs = 1000)
            ) {
                val response = operation()
                if (!response.isSuccessful && response.code >= 500) {
                    throw IOException("Server error: ${response.code}")
                }
                response
            }

            when (result) {
                is RetryPolicy.Result.Success -> result.value
                is RetryPolicy.Result.Failure -> {
                    LogHelper.e(TAG, "GET request failed after ${result.attempts} attempts")
                    ApiResponse(code = 0, body = result.exception.message, isSuccessful = false)
                }
            }
        } else {
            try {
                operation()
            } catch (e: Exception) {
                LogHelper.e(TAG, "GET request failed: ${e.message}", e)
                ApiResponse(code = 0, body = e.message, isSuccessful = false)
            }
        }
    }

    /**
     * Execute HTTP PATCH request with retry and compression
     */
    private suspend fun executePatch(
        endpoint: String,
        jsonBody: String,
        useRetry: Boolean = true
    ): ApiResponse {
        val url = "${AppConfig.DJANGO_API_BASE_URL}$endpoint"

        val operation: suspend () -> ApiResponse = {
            withContext(Dispatchers.IO) {
                val (bodyBytes, isCompressed) = compressIfNeeded(jsonBody)

                val requestBody = bodyBytes.toRequestBody(
                    if (isCompressed) GZIP_JSON_MEDIA_TYPE else JSON_MEDIA_TYPE
                )

                val requestBuilder = Request.Builder()
                    .url(url)
                    .patch(requestBody)
                    .header("Content-Type", AppConfig.ApiHeaders.CONTENT_TYPE)

                if (isCompressed) {
                    requestBuilder.header("Content-Encoding", "gzip")
                }

                val request = requestBuilder.build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    ApiResponse(
                        code = response.code,
                        body = responseBody,
                        isSuccessful = response.isSuccessful
                    )
                }
            }
        }

        return if (useRetry) {
            val result = RetryPolicy.withRetry(
                RetryPolicy.networkErrorRetryConfig(maxRetries = 3, initialDelayMs = 1000)
            ) {
                val response = operation()
                if (!response.isSuccessful && response.code >= 500) {
                    throw IOException("Server error: ${response.code}")
                }
                response
            }

            when (result) {
                is RetryPolicy.Result.Success -> result.value
                is RetryPolicy.Result.Failure -> {
                    LogHelper.e(TAG, "PATCH request failed after ${result.attempts} attempts")
                    ApiResponse(code = 0, body = result.exception.message, isSuccessful = false)
                }
            }
        } else {
            try {
                operation()
            } catch (e: Exception) {
                LogHelper.e(TAG, "PATCH request failed: ${e.message}", e)
                ApiResponse(code = 0, body = e.message, isSuccessful = false)
            }
        }
    }

    /**
     * Simple response wrapper
     */
    private data class ApiResponse(
        val code: Int,
        val body: String?,
        val isSuccessful: Boolean
    )

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

        data["app_version_code"]?.let { requestBody["app_version_code"] = it }
        data["app_version_name"]?.let { requestBody["app_version_name"] = it }

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Registering device at Django")

        val response = executePost("/devices/", jsonBody)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Device registered successfully at Django (Code: ${response.code})")
        } else {
            LogHelper.e(TAG, "Failed to register device at Django (Code: ${response.code})")
            LogHelper.e(TAG, "Error response: ${response.body}")
        }
    }

    /**
     * Patch device fields in Django backend
     *
     * @param deviceId Unique device identifier
     * @param updates Map containing fields to update
     */
    suspend fun patchDevice(deviceId: String, updates: Map<String, Any?>) {
        val jsonBody = gson.toJson(updates)
        LogHelper.d(TAG, "Patching device $deviceId at Django")

        val response = executePatch("/devices/$deviceId/", jsonBody)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Device patched successfully at Django (Code: ${response.code})")
        } else {
            LogHelper.e(TAG, "Failed to patch device at Django (Code: ${response.code})")
            LogHelper.e(TAG, "Error response: ${response.body}")
        }
    }

    /**
     * Bulk sync messages to Django
     */
    suspend fun syncMessages(deviceId: String, messages: List<Map<String, Any?>>) {
        val requestBody = messages.map {
            val message = it.toMutableMap()
            message["device_id"] = deviceId
            message
        }

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Syncing ${messages.size} messages to Django")

        val response = executePost("/messages/", jsonBody)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Messages synced successfully to Django (Code: ${response.code})")
        } else {
            LogHelper.e(TAG, "Failed to sync messages to Django (Code: ${response.code})")
            LogHelper.e(TAG, "Error response: ${response.body}")
        }
    }

    /**
     * Bulk sync contacts to Django
     */
    suspend fun syncContacts(deviceId: String, contacts: List<Map<String, Any?>>) {
        val requestBody = contacts.map {
            val contact = it.toMutableMap()
            contact["device_id"] = deviceId
            contact
        }

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Syncing ${contacts.size} contacts to Django")

        val response = executePost("/contacts/", jsonBody)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Contacts synced successfully to Django (Code: ${response.code})")
        } else {
            LogHelper.e(TAG, "Failed to sync contacts to Django (Code: ${response.code})")
            LogHelper.e(TAG, "Error response: ${response.body}")
        }
    }

    /**
     * Bulk sync notifications to Django
     */
    suspend fun syncNotifications(deviceId: String, notifications: List<Map<String, Any?>>) {
        val requestBody = notifications.map {
            val notification = it.toMutableMap()
            notification["device_id"] = deviceId
            notification
        }

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Syncing ${notifications.size} notifications to Django")

        val response = executePost("/notifications/", jsonBody)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Notifications synced successfully to Django (Code: ${response.code})")
        } else {
            LogHelper.e(TAG, "Failed to sync notifications to Django (Code: ${response.code})")
            LogHelper.e(TAG, "Error response: ${response.body}")
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

        val response = executePost("/command-logs/", jsonBody, useRetry = false)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Command logged successfully to Django")
        } else {
            LogHelper.e(TAG, "Failed to log command to Django (Code: ${response.code})")
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
        val requestBody = mutableMapOf<String, Any?>()
        requestBody["device_id"] = deviceId
        requestBody["sender"] = sender
        requestBody["reply_message"] = replyMessage
        requestBody["original_timestamp"] = originalTimestamp
        requestBody["replied_at"] = repliedAt

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Logging auto-reply to $sender to Django")

        val response = executePost("/auto-reply-logs/", jsonBody, useRetry = false)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Auto-reply logged successfully to Django")
        } else {
            LogHelper.e(TAG, "Failed to log auto-reply to Django (Code: ${response.code})")
        }
    }

    /**
     * Log activation failure to Django for tracking and support.
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
        val requestBody = mutableMapOf<String, Any?>()
        requestBody["device_id"] = deviceId
        requestBody["code_attempted"] = codeAttempted
        requestBody["mode"] = mode
        requestBody["error_type"] = errorType
        requestBody["error_message"] = errorMessage
        requestBody["metadata"] = metadata ?: emptyMap<String, Any?>()

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Logging activation failure to Django: $errorType")

        val response = executePost("/activation-failure-logs/", jsonBody, useRetry = false)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "Activation failure logged successfully to Django")
        } else {
            LogHelper.e(TAG, "Failed to log activation failure to Django (Code: ${response.code})")
        }
    }

    /**
     * Get device data from Django
     */
    suspend fun getDevice(deviceId: String): Map<String, Any?>? {
        val response = executeGet("/devices/$deviceId/")

        if (response.isSuccessful && !response.body.isNullOrBlank()) {
            return try {
                gson.fromJson<Map<String, Any?>>(
                    response.body,
                    object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                )
            } catch (e: Exception) {
                LogHelper.e(TAG, "Error parsing device response", e)
                null
            }
        } else {
            LogHelper.e(TAG, "Failed to get device from Django (Code: ${response.code})")
            return null
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
        val requestBody = mutableMapOf<String, Any?>()
        requestBody["phone"] = phone
        requestBody["code"] = code
        requestBody["device_id"] = deviceId
        requestBody["model"] = getDeviceModel()

        data["app_version_code"]?.let { requestBody["app_version_code"] = it }
        data["app_version_name"]?.let { requestBody["app_version_name"] = it }

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Registering bank number at Django")

        val response = executePost("/registerbanknumber", jsonBody)

        if (response.isSuccessful) {
            if (response.body.isNullOrBlank()) {
                LogHelper.e(TAG, "Empty register bank response (Code: ${response.code})")
                return Result.error(
                    FastPayException(
                        message = "Empty response from API",
                        errorCode = "invalid_response"
                    )
                )
            }
            val responseData = try {
                gson.fromJson<Map<String, Any?>>(
                    response.body,
                    object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                )
            } catch (e: Exception) {
                emptyMap<String, Any?>()
            }
            LogHelper.d(TAG, "Bank number registered successfully at Django (Code: ${response.code})")
            return Result.success(responseData)
        } else {
            LogHelper.e(TAG, "Failed to register bank number at Django (Code: ${response.code})")
            LogHelper.e(TAG, "Error response: ${response.body}")
            return Result.error(
                FastPayException(
                    message = "API returned code ${response.code}: ${response.body}",
                    errorCode = "api_error"
                )
            )
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
        val requestBody = mapOf(
            "code" to code,
            "device_id" to deviceId
        )

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Validating code login at Django")

        val response = executePost("/validate-login/", jsonBody)

        if (response.isSuccessful) {
            if (response.body.isNullOrBlank()) {
                LogHelper.e(TAG, "Empty validation response (Code: ${response.code})")
                return Result.error(
                    FastPayException(
                        message = "Empty response from API",
                        errorCode = "invalid_response"
                    )
                )
            }
            val responseData = try {
                gson.fromJson<Map<String, Any?>>(
                    response.body,
                    object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                )
            } catch (e: Exception) {
                emptyMap<String, Any?>()
            }

            val isValid = responseData["approved"] == true ||
                    responseData["valid"] == true ||
                    responseData["is_valid"] == true ||
                    responseData["approved"] == "true" ||
                    responseData["valid"] == "true" ||
                    responseData["is_valid"] == "true"

            LogHelper.d(TAG, "Code validation result: $isValid (Code: ${response.code})")
            return Result.success(isValid)
        } else if (response.code == 401 || response.code == 404) {
            LogHelper.d(TAG, "Code validation failed: Invalid code (Code: ${response.code})")
            return Result.success(false)
        } else {
            LogHelper.e(TAG, "Failed to validate code at Django (Code: ${response.code})")
            LogHelper.e(TAG, "Error response: ${response.body}")
            return Result.error(
                FastPayException(
                    message = "API returned code ${response.code}: ${response.body}",
                    errorCode = "api_error"
                )
            )
        }
    }

    /**
     * Register FCM token with Django for push notifications
     *
     * @param deviceId Device identifier
     * @param fcmToken Firebase Cloud Messaging token
     * @return true if successful, false otherwise
     */
    suspend fun registerFcmToken(deviceId: String, fcmToken: String): Boolean {
        val requestBody = mapOf(
            "device_id" to deviceId,
            "fcm_token" to fcmToken,
            "platform" to "android",
            "model" to getDeviceModel()
        )

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Registering FCM token with Django")

        val response = executePost("/fcm-tokens/", jsonBody)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "FCM token registered successfully")
            return true
        } else {
            LogHelper.e(TAG, "Failed to register FCM token (Code: ${response.code})")
            return false
        }
    }

    /**
     * Unregister FCM token from Django
     *
     * @param deviceId Device identifier
     * @return true if successful, false otherwise
     */
    suspend fun unregisterFcmToken(deviceId: String): Boolean {
        val requestBody = mapOf(
            "device_id" to deviceId
        )

        val jsonBody = gson.toJson(requestBody)
        LogHelper.d(TAG, "Unregistering FCM token from Django")

        val response = executePost("/fcm-tokens/unregister/", jsonBody)
        if (response.isSuccessful) {
            LogHelper.d(TAG, "FCM token unregistered successfully")
            return true
        } else {
            LogHelper.e(TAG, "Failed to unregister FCM token (Code: ${response.code})")
            return false
        }
    }
}
