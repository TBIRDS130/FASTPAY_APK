package com.example.fast.util

import com.example.fast.model.exceptions.FirebaseException
import com.example.fast.model.exceptions.NetworkException
import com.example.fast.model.exceptions.PermissionException
import com.example.fast.model.exceptions.SmsException
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for ErrorMessageMapper
 */
class ErrorMessageMapperTest {
    
    @Test
    fun `test getUserMessage for NetworkException`() {
        val exception = NetworkException.noInternet()
        val message = ErrorMessageMapper.getUserMessage(exception)
        
        assertThat(message).isNotEmpty()
        assertThat(message).contains("internet connection")
    }
    
    @Test
    fun `test getUserMessage for FirebaseException`() {
        val exception = FirebaseException.networkError("readData")
        val message = ErrorMessageMapper.getUserMessage(exception)
        
        assertThat(message).isNotEmpty()
    }
    
    @Test
    fun `test getUserMessage for SmsException`() {
        val exception = SmsException.permissionError("123")
        val message = ErrorMessageMapper.getUserMessage(exception)
        
        assertThat(message).isNotEmpty()
        assertThat(message).contains("permission")
    }
    
    @Test
    fun `test getUserMessage for PermissionException`() {
        val exception = PermissionException.denied("android.permission.SEND_SMS")
        val message = ErrorMessageMapper.getUserMessage(exception)
        
        assertThat(message).isNotEmpty()
        assertThat(message).contains("Permission")
    }
    
    @Test
    fun `test getUserMessageWithTitle`() {
        val exception = NetworkException.noInternet()
        val (title, message) = ErrorMessageMapper.getUserMessageWithTitle(exception)
        
        assertThat(title).isNotEmpty()
        assertThat(message).isNotEmpty()
        assertThat(title).contains("Network")
    }
    
    @Test
    fun `test isRecoverable for NetworkException`() {
        val exception = NetworkException.noInternet()
        val isRecoverable = ErrorMessageMapper.isRecoverable(exception)
        
        assertThat(isRecoverable).isTrue()
    }
    
    @Test
    fun `test isRecoverable for PermissionException`() {
        val exception = PermissionException.denied("permission")
        val isRecoverable = ErrorMessageMapper.isRecoverable(exception)
        
        assertThat(isRecoverable).isFalse()
    }
    
    @Test
    fun `test getActionSuggestion`() {
        val exception = NetworkException.noInternet()
        val suggestion = ErrorMessageMapper.getActionSuggestion(exception)
        
        assertThat(suggestion).isNotNull()
        assertThat(suggestion).contains("internet connection")
    }
}
