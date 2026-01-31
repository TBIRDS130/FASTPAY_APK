package com.example.fast.repository.impl

import android.content.Context
import com.example.fast.model.exceptions.SmsException
import com.example.fast.repository.SmsRepository
import com.example.fast.util.Result
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for SmsRepositoryImpl
 */
class SmsRepositoryImplTest {
    
    private lateinit var context: Context
    private lateinit var repository: SmsRepository
    
    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        repository = SmsRepositoryImpl(context)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `test sendSms with invalid phone number returns error`() = runTest {
        val result = repository.sendSms("", "test message")
        
        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SmsException::class.java)
    }
    
    @Test
    fun `test sendSms with blank message returns error`() = runTest {
        val result = repository.sendSms("1234567890", "")
        
        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SmsException::class.java)
    }
    
    // Additional tests would require mocking SmsManager and Firebase
    // These are integration tests that would be in androidTest
}
