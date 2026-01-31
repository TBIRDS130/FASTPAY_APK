package com.example.fast.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.fast.domain.usecase.SendSmsUseCase
import com.example.fast.repository.SmsRepository
import com.example.fast.util.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for ChatActivityViewModel with Hilt dependencies
 */
class ChatActivityViewModelHiltTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var application: Application
    private lateinit var smsRepository: SmsRepository
    private lateinit var sendSmsUseCase: SendSmsUseCase
    private lateinit var viewModel: ChatActivityViewModel
    
    @Before
    fun setUp() {
        application = mockk<Application>(relaxed = true)
        smsRepository = mockk(relaxed = true)
        sendSmsUseCase = mockk(relaxed = true)
        viewModel = ChatActivityViewModel(application, smsRepository, sendSmsUseCase)
    }
    
    @Test
    fun `test sendMessage with success`() = runTest {
        val phoneNumber = "1234567890"
        val message = "Test message"
        
        viewModel.initialize(phoneNumber, "Test Contact")
        
        coEvery { 
            sendSmsUseCase(SendSmsUseCase.Params(phoneNumber, message))
        } returns Result.success(Unit)
        
        viewModel.sendMessage(message)
        
        // Give it a moment for LiveData to update
        kotlinx.coroutines.delay(100)
        
        val result = viewModel.sendMessageResult.value
        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(ChatActivityViewModel.SendMessageResult.Success::class.java)
    }
    
    @Test
    fun `test sendMessage with error`() = runTest {
        val phoneNumber = "1234567890"
        val message = "Test message"
        
        viewModel.initialize(phoneNumber, "Test Contact")
        
        coEvery { 
            sendSmsUseCase(SendSmsUseCase.Params(phoneNumber, message))
        } returns Result.error(
            com.example.fast.model.exceptions.SmsException("Error")
        )
        
        viewModel.sendMessage(message)
        
        // Give it a moment for LiveData to update
        kotlinx.coroutines.delay(100)
        
        val result = viewModel.sendMessageResult.value
        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(ChatActivityViewModel.SendMessageResult.Error::class.java)
    }
}
