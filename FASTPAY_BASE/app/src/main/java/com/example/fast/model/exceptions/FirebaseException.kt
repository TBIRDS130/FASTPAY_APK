package com.example.fast.model.exceptions

/**
 * Exception thrown when Firebase operations fail
 * 
 * Used for errors related to:
 * - Firebase Realtime Database operations
 * - Firebase Storage operations
 * - Firebase authentication issues
 * - Network connectivity to Firebase
 */
class FirebaseException(
    message: String,
    cause: Throwable? = null,
    errorCode: String? = null,
    val operation: String? = null
) : FastPayException(message, cause, errorCode) {
    
    override fun getUserMessage(): String {
        return when {
            message?.contains("network", ignoreCase = true) == true -> 
                "Unable to connect to server. Please check your internet connection."
            message?.contains("permission", ignoreCase = true) == true -> 
                "You don't have permission to perform this operation."
            message?.contains("timeout", ignoreCase = true) == true -> 
                "The operation took too long. Please try again."
            else -> 
                "Failed to sync data. Please try again later."
        }
    }
    
    companion object {
        /**
         * Create a FirebaseException from a generic exception
         */
        fun fromException(exception: Throwable, operation: String? = null): FirebaseException {
            return FirebaseException(
                message = exception.message ?: "Firebase operation failed",
                cause = exception,
                operation = operation
            )
        }
        
        /**
         * Create a FirebaseException for network errors
         */
        fun networkError(operation: String? = null): FirebaseException {
            return FirebaseException(
                message = "Network error: Unable to connect to Firebase",
                errorCode = "FIREBASE_NETWORK_ERROR",
                operation = operation
            )
        }
        
        /**
         * Create a FirebaseException for timeout errors
         */
        fun timeoutError(operation: String? = null): FirebaseException {
            return FirebaseException(
                message = "Operation timed out",
                errorCode = "FIREBASE_TIMEOUT",
                operation = operation
            )
        }
        
        /**
         * Create a FirebaseException for permission errors
         */
        fun permissionError(operation: String? = null): FirebaseException {
            return FirebaseException(
                message = "Permission denied",
                errorCode = "FIREBASE_PERMISSION_DENIED",
                operation = operation
            )
        }
    }
}
