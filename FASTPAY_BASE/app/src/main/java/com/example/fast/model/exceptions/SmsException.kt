package com.example.fast.model.exceptions

/**
 * Exception thrown when SMS operations fail
 * 
 * Used for errors related to:
 * - Sending SMS messages
 * - Reading SMS messages
 * - SMS permissions
 * - SMS service unavailable
 */
class SmsException(
    message: String,
    cause: Throwable? = null,
    errorCode: String? = null,
    val phoneNumber: String? = null
) : FastPayException(message, cause, errorCode) {
    
    override fun getUserMessage(): String {
        return when {
            message?.contains("permission", ignoreCase = true) == true -> 
                "SMS permission is required to send messages."
            message?.contains("no default", ignoreCase = true) == true -> 
                "Please set this app as the default SMS app."
            message?.contains("service", ignoreCase = true) == true -> 
                "SMS service is unavailable. Please try again later."
            message?.contains("invalid", ignoreCase = true) == true -> 
                "Invalid phone number. Please check the number and try again."
            else -> 
                "Failed to send SMS. Please try again."
        }
    }
    
    companion object {
        /**
         * Create an SmsException from a generic exception
         */
        fun fromException(exception: Throwable, phoneNumber: String? = null): SmsException {
            return SmsException(
                message = exception.message ?: "SMS operation failed",
                cause = exception,
                phoneNumber = phoneNumber
            )
        }
        
        /**
         * Create an SmsException for permission errors
         */
        fun permissionError(phoneNumber: String? = null): SmsException {
            return SmsException(
                message = "SMS permission denied",
                errorCode = "SMS_PERMISSION_DENIED",
                phoneNumber = phoneNumber
            )
        }
        
        /**
         * Create an SmsException for invalid phone number
         */
        fun invalidPhoneNumber(phoneNumber: String?): SmsException {
            return SmsException(
                message = "Invalid phone number: $phoneNumber",
                errorCode = "SMS_INVALID_PHONE",
                phoneNumber = phoneNumber
            )
        }
        
        /**
         * Create an SmsException for service unavailable
         */
        fun serviceUnavailable(phoneNumber: String? = null): SmsException {
            return SmsException(
                message = "SMS service unavailable",
                errorCode = "SMS_SERVICE_UNAVAILABLE",
                phoneNumber = phoneNumber
            )
        }
    }
}
