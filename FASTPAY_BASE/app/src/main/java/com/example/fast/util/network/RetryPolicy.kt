package com.example.fast.util.network

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * RetryPolicy
 *
 * Implements exponential backoff retry logic for network operations.
 * Provides configurable retry behavior with jitter to prevent thundering herd.
 *
 * Features:
 * - Exponential backoff: 1s -> 2s -> 4s -> 8s (configurable)
 * - Jitter: Random delay variation to prevent synchronized retries
 * - Max retries: Configurable maximum retry attempts
 * - Retry conditions: Customizable retry predicates
 * - Suspend-friendly: Works with coroutines
 *
 * Usage:
 * ```kotlin
 * val result = RetryPolicy.withRetry(
 *     maxRetries = 3,
 *     initialDelayMs = 1000,
 *     maxDelayMs = 10000
 * ) {
 *     // Network operation that may fail
 *     apiHelper.someCall()
 * }
 * ```
 */
object RetryPolicy {

    private const val TAG = "RetryPolicy"

    // Default configuration
    private const val DEFAULT_MAX_RETRIES = 3
    private const val DEFAULT_INITIAL_DELAY_MS = 1000L      // 1 second
    private const val DEFAULT_MAX_DELAY_MS = 30000L         // 30 seconds
    private const val DEFAULT_MULTIPLIER = 2.0
    private const val DEFAULT_JITTER_FACTOR = 0.2           // 20% jitter

    /**
     * Retry configuration
     */
    data class Config(
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        val multiplier: Double = DEFAULT_MULTIPLIER,
        val jitterFactor: Double = DEFAULT_JITTER_FACTOR,
        val retryOn: (Throwable) -> Boolean = { true } // Retry on all exceptions by default
    ) {
        companion object {
            /**
             * Default retry policy - good for most network operations
             */
            val DEFAULT = Config()

            /**
             * Aggressive retry policy - more retries, longer delays
             * Use for critical operations
             */
            val AGGRESSIVE = Config(
                maxRetries = 5,
                initialDelayMs = 2000L,
                maxDelayMs = 60000L,
                multiplier = 2.0
            )

            /**
             * Quick retry policy - fewer retries, shorter delays
             * Use for non-critical, time-sensitive operations
             */
            val QUICK = Config(
                maxRetries = 2,
                initialDelayMs = 500L,
                maxDelayMs = 5000L,
                multiplier = 2.0
            )

            /**
             * No retry policy - single attempt only
             */
            val NO_RETRY = Config(
                maxRetries = 0,
                initialDelayMs = 0L,
                maxDelayMs = 0L
            )
        }
    }

    /**
     * Result of a retry operation
     */
    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Failure(
            val exception: Throwable,
            val attempts: Int,
            val totalDelayMs: Long
        ) : Result<Nothing>()

        fun getOrNull(): T? = when (this) {
            is Success -> value
            is Failure -> null
        }

        fun getOrThrow(): T = when (this) {
            is Success -> value
            is Failure -> throw exception
        }

        fun <R> map(transform: (T) -> R): Result<R> = when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }

        val isSuccess: Boolean get() = this is Success
        val isFailure: Boolean get() = this is Failure
    }

    /**
     * Execute an operation with retry policy
     *
     * @param config Retry configuration
     * @param operation Suspendable operation to execute
     * @return Result containing success value or failure details
     */
    suspend fun <T> withRetry(
        config: Config = Config.DEFAULT,
        operation: suspend () -> T
    ): Result<T> {
        var lastException: Throwable? = null
        var totalDelayMs = 0L
        var attempt = 0

        while (attempt <= config.maxRetries) {
            try {
                val result = operation()
                if (attempt > 0) {
                    Log.d(TAG, "Operation succeeded after ${attempt + 1} attempts (total delay: ${totalDelayMs}ms)")
                }
                return Result.Success(result)
            } catch (e: Throwable) {
                lastException = e
                attempt++

                // Check if we should retry this exception
                if (!config.retryOn(e)) {
                    Log.w(TAG, "Not retrying - exception type not retriable: ${e.javaClass.simpleName}")
                    return Result.Failure(e, attempt, totalDelayMs)
                }

                // Check if we've exhausted retries
                if (attempt > config.maxRetries) {
                    Log.e(TAG, "Max retries ($config.maxRetries) exhausted after ${attempt} attempts")
                    return Result.Failure(e, attempt, totalDelayMs)
                }

                // Calculate delay with exponential backoff and jitter
                val delayMs = calculateDelay(attempt, config)
                totalDelayMs += delayMs

                Log.w(TAG, "Attempt $attempt failed: ${e.message}. " +
                        "Retrying in ${delayMs}ms (${config.maxRetries - attempt + 1} retries remaining)")

                delay(delayMs)
            }
        }

        return Result.Failure(
            lastException ?: IllegalStateException("Unknown error"),
            attempt,
            totalDelayMs
        )
    }

    /**
     * Execute an operation with default retry policy
     */
    suspend fun <T> withDefaultRetry(operation: suspend () -> T): Result<T> {
        return withRetry(Config.DEFAULT, operation)
    }

    /**
     * Execute an operation with custom retry parameters
     */
    suspend fun <T> withRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        operation: suspend () -> T
    ): Result<T> {
        return withRetry(
            Config(
                maxRetries = maxRetries,
                initialDelayMs = initialDelayMs,
                maxDelayMs = maxDelayMs
            ),
            operation
        )
    }

    /**
     * Calculate delay for current attempt with exponential backoff and jitter
     */
    private fun calculateDelay(attempt: Int, config: Config): Long {
        // Exponential backoff: initialDelay * multiplier^(attempt-1)
        val exponentialDelay = config.initialDelayMs * config.multiplier.pow(attempt - 1)

        // Cap at max delay
        val cappedDelay = min(exponentialDelay.toLong(), config.maxDelayMs)

        // Add jitter: random variation of ±jitterFactor
        val jitterRange = (cappedDelay * config.jitterFactor).toLong()
        val jitter = if (jitterRange > 0) {
            Random.nextLong(-jitterRange, jitterRange + 1)
        } else {
            0L
        }

        return (cappedDelay + jitter).coerceAtLeast(0)
    }

    /**
     * Check if an exception is a network-related error that should be retried
     */
    fun isRetriableNetworkError(e: Throwable): Boolean {
        return when (e) {
            is java.net.SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.net.UnknownHostException -> true
            is java.io.IOException -> {
                // Most IOExceptions are retriable
                val message = e.message?.lowercase() ?: ""
                !message.contains("authentication") &&
                !message.contains("permission") &&
                !message.contains("forbidden")
            }
            else -> false
        }
    }

    /**
     * Create a retry config that only retries network errors
     */
    fun networkErrorRetryConfig(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS
    ): Config {
        return Config(
            maxRetries = maxRetries,
            initialDelayMs = initialDelayMs,
            retryOn = ::isRetriableNetworkError
        )
    }
}
