package com.example.fast.model.exceptions

/**
 * Exception thrown when network operations fail
 * 
 * Used for errors related to:
 * - No internet connection
 * - Network timeouts
 * - Network unreachable
 * - DNS resolution failures
 */
class NetworkException(
    message: String,
    cause: Throwable? = null,
    errorCode: String? = null
) : FastPayException(message, cause, errorCode) {
    
    override fun getUserMessage(): String {
        return when {
            message?.contains("no internet", ignoreCase = true) == true -> 
                "No internet connection. Please check your network settings."
            message?.contains("timeout", ignoreCase = true) == true -> 
                "Network request timed out. Please try again."
            message?.contains("unreachable", ignoreCase = true) == true -> 
                "Network is unreachable. Please check your connection."
            else -> 
                "Network error. Please check your internet connection and try again."
        }
    }
    
    companion object {
        /**
         * Create a NetworkException from a generic exception
         */
        fun fromException(exception: Throwable): NetworkException {
            return NetworkException(
                message = exception.message ?: "Network operation failed",
                cause = exception
            )
        }
        
        /**
         * Create a NetworkException for no internet connection
         */
        fun noInternet(): NetworkException {
            return NetworkException(
                message = "No internet connection available",
                errorCode = "NETWORK_NO_INTERNET"
            )
        }
        
        /**
         * Create a NetworkException for timeout
         */
        fun timeout(): NetworkException {
            return NetworkException(
                message = "Network request timed out",
                errorCode = "NETWORK_TIMEOUT"
            )
        }
        
        /**
         * Create a NetworkException for unreachable network
         */
        fun unreachable(): NetworkException {
            return NetworkException(
                message = "Network is unreachable",
                errorCode = "NETWORK_UNREACHABLE"
            )
        }
    }
}
