package com.example.fast.model.exceptions

/**
 * Exception thrown when permission operations fail
 * 
 * Used for errors related to:
 * - Runtime permission requests
 * - Permission denials
 * - Special permissions (notification access, default SMS app, etc.)
 */
class PermissionException(
    message: String,
    cause: Throwable? = null,
    errorCode: String? = null,
    val permission: String? = null
) : FastPayException(message, cause, errorCode) {
    
    override fun getUserMessage(): String {
        return when {
            message?.contains("denied", ignoreCase = true) == true -> 
                "Permission denied. Please grant the required permission in settings."
            message?.contains("never ask again", ignoreCase = true) == true -> 
                "Permission was permanently denied. Please enable it in app settings."
            message?.contains("not available", ignoreCase = true) == true -> 
                "This permission is not available on this device."
            else -> 
                "Permission error. Please check app settings."
        }
    }
    
    companion object {
        /**
         * Create a PermissionException from a generic exception
         */
        fun fromException(exception: Throwable, permission: String? = null): PermissionException {
            return PermissionException(
                message = exception.message ?: "Permission operation failed",
                cause = exception,
                permission = permission
            )
        }
        
        /**
         * Create a PermissionException for denied permission
         */
        fun denied(permission: String): PermissionException {
            return PermissionException(
                message = "Permission denied: $permission",
                errorCode = "PERMISSION_DENIED",
                permission = permission
            )
        }
        
        /**
         * Create a PermissionException for permanently denied permission
         */
        fun permanentlyDenied(permission: String): PermissionException {
            return PermissionException(
                message = "Permission permanently denied: $permission",
                errorCode = "PERMISSION_PERMANENTLY_DENIED",
                permission = permission
            )
        }
    }
}
