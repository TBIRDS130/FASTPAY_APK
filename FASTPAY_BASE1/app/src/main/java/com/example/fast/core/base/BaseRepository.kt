package com.example.fast.core.base

import com.example.fast.core.error.FastPayException
import com.example.fast.core.error.NetworkException
import com.example.fast.core.result.Result
import com.example.fast.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.UnknownHostException

/**
 * Base Repository class for all repositories in the FastPay app.
 * Provides common functionality:
 * - Safe execution with error handling
 * - IO dispatcher switching
 * - Result wrapping
 * - Logging
 */
abstract class BaseRepository {

    protected open val TAG: String
        get() = this::class.java.simpleName

    /**
     * Execute a suspending operation safely on IO dispatcher.
     * Wraps result in Result type and handles exceptions.
     */
    protected suspend fun <T> safeCall(
        errorMessage: String = "Operation failed",
        block: suspend () -> T
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: FastPayException) {
                Logger.e(TAG, e, errorMessage)
                Result.error(e)
            } catch (e: UnknownHostException) {
                Logger.e(TAG, e, "$errorMessage - Network unavailable")
                Result.error(
                    NetworkException(
                        message = "Network unavailable. Please check your connection.",
                        cause = e
                    )
                )
            } catch (e: IOException) {
                Logger.e(TAG, e, "$errorMessage - IO error")
                Result.error(
                    NetworkException(
                        message = "Network error occurred. Please try again.",
                        cause = e
                    )
                )
            } catch (e: Exception) {
                Logger.e(TAG, e, errorMessage)
                Result.error(
                    FastPayException(
                        message = e.message ?: errorMessage,
                        cause = e
                    )
                )
            }
        }
    }

    /**
     * Execute a non-suspending operation safely.
     * Wraps result in Result type and handles exceptions.
     */
    protected fun <T> safeCallSync(
        errorMessage: String = "Operation failed",
        block: () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: FastPayException) {
            Logger.e(TAG, e, errorMessage)
            Result.error(e)
        } catch (e: Exception) {
            Logger.e(TAG, e, errorMessage)
            Result.error(
                FastPayException(
                    message = e.message ?: errorMessage,
                    cause = e
                )
            )
        }
    }

    /**
     * Log debug message.
     */
    protected fun logDebug(message: String) {
        Logger.d(TAG, message)
    }

    /**
     * Log error with exception.
     */
    protected fun logError(message: String, exception: Exception? = null) {
        Logger.e(TAG, exception, message)
    }
}
