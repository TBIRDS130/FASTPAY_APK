package com.example.fast.util

import com.example.fast.model.exceptions.FastPayException
import com.example.fast.model.exceptions.FirebaseException
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for Result pattern
 * 
 * Tests verify that Result pattern works correctly for
 * success and error cases.
 */
class ResultTest {
    
    @Test
    fun `test success result`() {
        val result = Result.success("test data")
        
        assertThat(result.isSuccess).isTrue()
        assertThat(result.isError).isFalse()
        assertThat(result.getOrNull()).isEqualTo("test data")
        assertThat(result.exceptionOrNull()).isNull()
    }
    
    @Test
    fun `test error result`() {
        val exception = FastPayException("Test error")
        val result = Result.error<String>(exception)
        
        assertThat(result.isSuccess).isFalse()
        assertThat(result.isError).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }
    
    @Test
    fun `test getOrDefault with success`() {
        val result = Result.success("data")
        val value = result.getOrDefault("default")
        
        assertThat(value).isEqualTo("data")
    }
    
    @Test
    fun `test getOrDefault with error`() {
        val exception = FastPayException("Error")
        val result = Result.error<String>(exception)
        val value = result.getOrDefault("default")
        
        assertThat(value).isEqualTo("default")
    }
    
    @Test
    fun `test map on success`() {
        val result = Result.success(5)
        val mapped = result.map { it * 2 }
        
        assertThat(mapped.isSuccess).isTrue()
        assertThat(mapped.getOrNull()).isEqualTo(10)
    }
    
    @Test
    fun `test map on error`() {
        val exception = FastPayException("Error")
        val result = Result.error<Int>(exception)
        val mapped = result.map { it * 2 }
        
        assertThat(mapped.isError).isTrue()
        assertThat(mapped.exceptionOrNull()).isEqualTo(exception)
    }
    
    @Test
    fun `test onSuccess callback`() {
        var callbackCalled = false
        val result = Result.success("data")
        
        result.onSuccess {
            callbackCalled = true
            assertThat(it).isEqualTo("data")
        }
        
        assertThat(callbackCalled).isTrue()
    }
    
    @Test
    fun `test onError callback`() {
        var callbackCalled = false
        val exception = FastPayException("Error")
        val result = Result.error<String>(exception)
        
        result.onError {
            callbackCalled = true
            assertThat(it).isEqualTo(exception)
        }
        
        assertThat(callbackCalled).isTrue()
    }
    
    @Test
    fun `test runCatching with success`() {
        val result = Result.runCatching {
            "success"
        }
        
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("success")
    }
    
    @Test
    fun `test runCatching with exception`() {
        val result = Result.runCatching {
            throw RuntimeException("Error")
        }
        
        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()).isNotNull()
    }
    
    @Test
    fun `test fromNullable with value`() {
        val result = Result.fromNullable("value", "Error message")
        
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("value")
    }
    
    @Test
    fun `test fromNullable with null`() {
        val result = Result.fromNullable<String>(null, "Error message")
        
        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Error message")
    }
}
