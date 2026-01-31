package com.example.fast.util

import com.example.fast.model.exceptions.FastPayException
import kotlin.UnsafeVariance

/**
 * Sealed class representing the result of an operation that can fail
 * 
 * This is a functional approach to error handling that makes errors explicit
 * and forces callers to handle both success and failure cases.
 * 
 * Usage:
 * ```
 * when (val result = someOperation()) {
 *     is Result.Success -> {
 *         val data = result.data
 *         // Handle success
 *     }
 *     is Result.Error -> {
 *         val error = result.exception
 *         // Handle error
 *     }
 * }
 * ```
 * 
 * Or with extension functions:
 * ```
 * result.onSuccess { data ->
 *     // Handle success
 * }.onError { error ->
 *     // Handle error
 * }
 * ```
 */
sealed class Result<out T> {
    /**
     * Represents a successful operation with data
     */
    data class Success<out T>(val data: T) : Result<T>()
    
    /**
     * Represents a failed operation with an exception
     */
    data class Error(val exception: FastPayException) : Result<Nothing>()
    
    /**
     * Returns true if the result is a success
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Returns true if the result is an error
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Get the data if success, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    /**
     * Get the exception if error, null otherwise
     */
    fun exceptionOrNull(): FastPayException? = when (this) {
        is Success -> null
        is Error -> exception
    }
    
    /**
     * Execute block if result is success
     */
    inline fun onSuccess(action: (value: T) -> Unit): Result<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }
    
    /**
     * Execute block if result is error
     */
    inline fun onError(action: (exception: FastPayException) -> Unit): Result<T> {
        if (this is Error) {
            action(exception)
        }
        return this
    }
    
    /**
     * Transform the data if success, otherwise return error
     */
    inline fun <R> map(transform: (value: T) -> R): Result<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> this
        }
    }
    
    /**
     * Transform the data if success using another Result, otherwise return error
     */
    inline fun <R> flatMap(transform: (value: T) -> Result<R>): Result<R> {
        return when (this) {
            is Success -> transform(data)
            is Error -> this
        }
    }
    
    /**
     * Get the data or throw the exception
     * Use with caution - prefer getOrNull() or when expression
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }
    
    /**
     * Get the data or return default value
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }
    
    companion object {
        /**
         * Create a success result
         */
        fun <T> success(data: T): Result<T> = Success(data)
        
        /**
         * Create an error result
         */
        fun <T> error(exception: FastPayException): Result<T> = Error(exception)
        
        /**
         * Create a result from a nullable value
         * Returns Error if value is null
         */
        fun <T> fromNullable(value: T?, errorMessage: String = "Value is null"): Result<T> {
            return if (value != null) {
                Success(value)
            } else {
                Error(
                    com.example.fast.model.exceptions.FastPayException(errorMessage)
                )
            }
        }
        
        /**
         * Create a result from a try-catch block
         */
        inline fun <T> runCatching(block: () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: FastPayException) {
                Error(e)
            } catch (e: Exception) {
                Error(
                    com.example.fast.model.exceptions.FastPayException(
                        message = e.message ?: "Unknown error",
                        cause = e
                    )
                )
            }
        }
    }
}
