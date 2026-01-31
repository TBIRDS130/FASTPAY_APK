package com.example.fast.domain.usecase

import com.example.fast.repository.SmsRepository
import com.example.fast.util.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for SendSmsUseCase
 */
class SendSmsUseCaseTest {
    
    private lateinit var smsRepository: SmsRepository
    private lateinit var useCase: SendSmsUseCase
    
    @Before
    fun setUp() {
        smsRepository = mockk(relaxed = true)
        useCase = SendSmsUseCase(smsRepository)
    }
    
    @Test
    fun `test sendSms use case calls repository`() = runTest {
        val phoneNumber = "1234567890"
        val message = "Test message"
        
        coEvery { smsRepository.sendSms(phoneNumber, message) } returns Result.success(Unit)
        
        val result = useCase(SendSmsUseCase.Params(phoneNumber, message))
        
        assertThat(result.isSuccess).isTrue()
    }
    
    @Test
    fun `test sendSms use case handles error`() = runTest {
        val phoneNumber = "1234567890"
        val message = "Test message"
        
        coEvery { smsRepository.sendSms(phoneNumber, message) } returns Result.error(
            com.example.fast.model.exceptions.SmsException("Error")
        )
        
        val result = useCase(SendSmsUseCase.Params(phoneNumber, message))
        
        assertThat(result.isError).isTrue()
    }
}
