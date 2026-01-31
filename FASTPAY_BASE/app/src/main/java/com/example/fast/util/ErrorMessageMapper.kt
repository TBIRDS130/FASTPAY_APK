package com.example.fast.util

import com.example.fast.model.exceptions.FastPayException
import com.example.fast.model.exceptions.FirebaseException
import com.example.fast.model.exceptions.NetworkException
import com.example.fast.model.exceptions.PermissionException
import com.example.fast.model.exceptions.SmsException

/**
 * Maps technical exceptions to user-friendly error messages
 * 
 * This utility provides a centralized way to convert exceptions
 * into messages that can be displayed to users.
 */
object ErrorMessageMapper {
    
    /**
     * Get a user-friendly message from an exception
     * 
     * @param exception The exception to map
     * @return User-friendly error message
     */
    fun getUserMessage(exception: Throwable): String {
        return when (exception) {
            is FastPayException -> exception.getUserMessage()
            is FirebaseException -> exception.getUserMessage()
            is SmsException -> exception.getUserMessage()
            is PermissionException -> exception.getUserMessage()
            is NetworkException -> exception.getUserMessage()
            else -> getGenericMessage(exception)
        }
    }
    
    /**
     * Get a user-friendly message with optional title
     * 
     * @param exception The exception to map
     * @param title Optional title for the error
     * @return Pair of (title, message)
     */
    fun getUserMessageWithTitle(exception: Throwable, title: String? = null): Pair<String, String> {
        val message = getUserMessage(exception)
        val errorTitle = title ?: getDefaultTitle(exception)
        return Pair(errorTitle, message)
    }
    
    /**
     * Get default title for an exception type
     */
    private fun getDefaultTitle(exception: Throwable): String {
        return when (exception) {
            is NetworkException -> "Network Error"
            is FirebaseException -> "Sync Error"
            is SmsException -> "SMS Error"
            is PermissionException -> "Permission Required"
            is FastPayException -> "Error"
            else -> "Error"
        }
    }
    
    /**
     * Get generic message for unknown exceptions
     */
    private fun getGenericMessage(exception: Throwable): String {
        val message = exception.message ?: "An unexpected error occurred"
        
        return when {
            message.contains("network", ignoreCase = true) -> 
                "Network error. Please check your internet connection."
            message.contains("timeout", ignoreCase = true) -> 
                "The operation took too long. Please try again."
            message.contains("permission", ignoreCase = true) -> 
                "Permission error. Please check app settings."
            else -> 
                "Something went wrong. Please try again."
        }
    }
    
    /**
     * Check if error is recoverable (user can retry)
     */
    fun isRecoverable(exception: Throwable): Boolean {
        return when (exception) {
            is NetworkException -> true
            is FirebaseException -> {
                exception.message?.contains("permission", ignoreCase = true) != true
            }
            is SmsException -> {
                exception.message?.contains("permission", ignoreCase = true) != true &&
                exception.message?.contains("invalid", ignoreCase = true) != true
            }
            is PermissionException -> false
            else -> true
        }
    }
    
    /**
     * Get action suggestion for the error
     */
    fun getActionSuggestion(exception: Throwable): String? {
        return when (exception) {
            is NetworkException -> "Check your internet connection and try again"
            is PermissionException -> "Go to Settings > Apps > FastPay > Permissions"
            is SmsException -> {
                if (exception.message?.contains("permission", ignoreCase = true) == true) {
                    "Grant SMS permission in app settings"
                } else {
                    "Try sending the message again"
                }
            }
            is FirebaseException -> {
                if (exception.message?.contains("network", ignoreCase = true) == true) {
                    "Check your internet connection"
                } else {
                    "Try again in a moment"
                }
            }
            else -> "Please try again"
        }
    }
}
