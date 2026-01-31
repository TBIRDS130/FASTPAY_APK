package com.example.fast.util

/**
 * Application-wide constants
 * 
 * This file contains all hardcoded string values, magic numbers, and configuration
 * constants used throughout the application. This centralizes constants for easier
 * maintenance and reduces the risk of typos.
 */
object Constants {
    
    // ============================================================================
    // SharedPreferences Keys
    // ============================================================================
    
    object SharedPreferences {
        const val PREFS_NAME_ACTIVATION = "activation_prefs"
        const val KEY_LOCALLY_ACTIVATED = "locally_activated"
        const val KEY_ACTIVATION_CODE = "activation_code"
    }
    
    // ============================================================================
    // Transition Names (for Shared Element Transitions)
    // ============================================================================
    
    object Transitions {
        const val LOGO = "logo_transition"
        const val TAGLINE = "tagline_transition"
        const val CARD_WRAPPER = "card_wrapper_transition"
        const val CARD = "card_transition"
        const val PHONE_CARD = "phone_card_transition"
    }
    
    // ============================================================================
    // Animation Constants
    // ============================================================================
    
    object Animation {
        const val MATRIX_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"
        const val HEX_CHARS = "0123456789ABCDEF"
        const val HASH_CHARS = "0123456789ABCDEFabcdef"
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }
    
    // ============================================================================
    // UI Text Constants
    // ============================================================================
    
    object UI {
        const val PHONE_INPUT_HINT = "Enter Your Phone Number"
        const val CODE_INPUT_HINT = "Input Code"
        const val NUMBER_INPUT_HINT = "Input Number"
        const val CRYPTO_HASH_PREFIX = "#"
        const val CRYPTO_HASH_SUFFIX = " ALWAYS SECURE"
        const val TERMINAL_PREFIX = "> "
        const val TERMINAL_CURSOR = "_"
    }
    
    // ============================================================================
    // Default Values
    // ============================================================================
    
    object Defaults {
        const val BANK_STATUS_PENDING = "PENDING"
        const val BANK_STATUS_COLOR = "#FFA500"
        const val BANK_NAME = "WELCOME"
        const val COMPANY_NAME = "STAY CONNECTED!"
        const val OTHER_INFO = "🫵ALWAYS PRIORITY!🫶"
        const val STATUS_TEXT_DEFAULT = "PENDING"
    }
    
    // ============================================================================
    // Firebase Path Segments
    // ============================================================================
    
    object FirebasePaths {
        const val TESTING = "testing"
        const val RUNNING = "running"
        const val DEVICE = "device"
        const val MESSAGE = "message"
        const val NOTIFICATION = "notification"
        const val HEARTBEAT = "hertbit"
        const val DEVICE_BACKUPS = "device-backups"
        const val APP = "app"
        const val VERSION = "version"
        const val TESTCODE = "TESTCODE"
        const val BANKCODE = "BANKCODE"
    }
    
    // ============================================================================
    // Intent Extras
    // ============================================================================
    
    object IntentExtras {
        const val CONTACT_NUMBER = "contact_number"
        const val CONTACT_NAME = "contact_name"
        const val DOWNLOAD_URL = "downloadUrl"
        const val ACTIVATION_CODE = "activation_code"
        const val PHONE_NUMBER = "phone_number"
        const val DEVICE_ID = "device_id"
    }
    
    // ============================================================================
    // Time Constants (in milliseconds)
    // ============================================================================
    
    object Time {
        const val SECOND = 1000L
        const val MINUTE = 60 * SECOND
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        
        // Common delays
        const val SHORT_DELAY = 300L
        const val MEDIUM_DELAY = 500L
        const val LONG_DELAY = 1000L
    }
    
    // ============================================================================
    // Display Constants
    // ============================================================================
    
    object Display {
        const val CODE_DISPLAY_LENGTH = 4  // Last 4 digits for display
        const val CODE_SEPARATOR = "-"
    }
    
    // ============================================================================
    // Format Strings
    // ============================================================================
    
    object Formats {
        const val CODE_DISPLAY = "Code %s"
        const val PHONE_DISPLAY = "Phone: %s"
    }
}
