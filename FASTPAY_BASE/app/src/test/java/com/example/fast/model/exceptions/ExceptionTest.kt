package com.example.fast.model.exceptions

import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for custom exception classes
 */
class ExceptionTest {
    
    @Test
    fun `test FastPayException creation`() {
        val exception = FastPayException("Test message")
        
        assertThat(exception.message).isEqualTo("Test message")
        assertThat(exception.errorCode).isNull()
        assertThat(exception.getUserMessage()).isEqualTo("Test message")
    }
    
    @Test
    fun `test FastPayException with error code`() {
        val exception = FastPayException("Test message", null, "ERROR_001")
        
        assertThat(exception.errorCode).isEqualTo("ERROR_001")
        assertThat(exception.getErrorDetails()).contains("ERROR_001")
    }
    
    @Test
    fun `test FirebaseException network error`() {
        val exception = FirebaseException.networkError("readData")
        
        assertThat(exception.errorCode).isEqualTo("FIREBASE_NETWORK_ERROR")
        assertThat(exception.operation).isEqualTo("readData")
        assertThat(exception.getUserMessage()).contains("internet connection")
    }
    
    @Test
    fun `test FirebaseException timeout error`() {
        val exception = FirebaseException.timeoutError("writeData")
        
        assertThat(exception.errorCode).isEqualTo("FIREBASE_TIMEOUT")
        assertThat(exception.operation).isEqualTo("writeData")
        assertThat(exception.getUserMessage()).contains("too long")
    }
    
    @Test
    fun `test SmsException permission error`() {
        val exception = SmsException.permissionError("1234567890")
        
        assertThat(exception.errorCode).isEqualTo("SMS_PERMISSION_DENIED")
        assertThat(exception.phoneNumber).isEqualTo("1234567890")
        assertThat(exception.getUserMessage()).contains("permission")
    }
    
    @Test
    fun `test SmsException invalid phone number`() {
        val exception = SmsException.invalidPhoneNumber("invalid")
        
        assertThat(exception.errorCode).isEqualTo("SMS_INVALID_PHONE")
        assertThat(exception.phoneNumber).isEqualTo("invalid")
        assertThat(exception.getUserMessage()).contains("Invalid phone number")
    }
    
    @Test
    fun `test PermissionException denied`() {
        val exception = PermissionException.denied("android.permission.SEND_SMS")
        
        assertThat(exception.errorCode).isEqualTo("PERMISSION_DENIED")
        assertThat(exception.permission).isEqualTo("android.permission.SEND_SMS")
        assertThat(exception.getUserMessage()).contains("Permission denied")
    }
    
    @Test
    fun `test NetworkException no internet`() {
        val exception = NetworkException.noInternet()
        
        assertThat(exception.errorCode).isEqualTo("NETWORK_NO_INTERNET")
        assertThat(exception.getUserMessage()).contains("internet connection")
    }
    
    @Test
    fun `test exception getUserMessage`() {
        val firebaseException = FirebaseException("Network timeout", null, null, "readData")
        val message = firebaseException.getUserMessage()
        
        assertThat(message).isNotEmpty()
        assertThat(message).contains("try again")
    }
}
