package com.example.fast.model.exceptions

/**
 * Base exception class for all FastPay application exceptions
 * 
 * All custom exceptions in the FastPay app should extend this class.
 * This provides a consistent way to handle errors throughout the application.
 * 
 * @param message User-friendly error message
 * @param cause The underlying exception that caused this error
 * @param errorCode Optional error code for programmatic error handling
 */
open class FastPayException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String? = null
) : Exception(message, cause) {
    
    /**
     * Get a user-friendly error message
     * Can be overridden by subclasses to provide more specific messages
     */
    open fun getUserMessage(): String = message ?: "An unexpected error occurred"
    
    /**
     * Get error details for debugging
     * Includes error code if available
     */
    fun getErrorDetails(): String {
        return buildString {
            append(getUserMessage())
            errorCode?.let {
                append(" (Error Code: $it)")
            }
            cause?.let {
                append(" - Caused by: ${it.javaClass.simpleName}")
            }
        }
    }
}
