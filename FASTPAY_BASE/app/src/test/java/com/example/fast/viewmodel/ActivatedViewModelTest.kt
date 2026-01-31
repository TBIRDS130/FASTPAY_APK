package com.example.fast.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.fast.ui.activated.ActivatedViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for ActivatedViewModel
 * 
 * Tests the state management and properties of ActivatedViewModel.
 */
class ActivatedViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var viewModel: ActivatedViewModel
    
    @Before
    fun setUp() {
        viewModel = ActivatedViewModel()
    }
    
    @Test
    fun `test initial state`() {
        assertThat(viewModel.activationCode).isNull()
        assertThat(viewModel.devicePhoneNumber).isNull()
        assertThat(viewModel.cachedAndroidId).isEqualTo("")
        assertThat(viewModel.bankName).isEmpty()
        assertThat(viewModel.companyName).isEmpty()
        assertThat(viewModel.bankStatus).isEqualTo("PENDING")
        assertThat(viewModel.hasAllPermissions).isFalse()
        assertThat(viewModel.isServiceRunning).isFalse()
        assertThat(viewModel.totalMessageCount).isEqualTo(0)
        assertThat(viewModel.displayedMessageCount).isEqualTo(0)
        assertThat(viewModel.hasInstructionCard).isFalse()
    }
    
    @Test
    fun `test setting activation code`() {
        val code = "ABCDE12345"
        viewModel.activationCode = code
        
        assertThat(viewModel.activationCode).isEqualTo(code)
    }
    
    @Test
    fun `test setting device phone number`() {
        val phone = "1234567890"
        viewModel.devicePhoneNumber = phone
        
        assertThat(viewModel.devicePhoneNumber).isEqualTo(phone)
    }
    
    @Test
    fun `test setting bank information`() {
        viewModel.bankName = "Test Bank"
        viewModel.companyName = "Test Company"
        viewModel.otherInfo = "Test Info"
        
        assertThat(viewModel.bankName).isEqualTo("Test Bank")
        assertThat(viewModel.companyName).isEqualTo("Test Company")
        assertThat(viewModel.otherInfo).isEqualTo("Test Info")
    }
    
    @Test
    fun `test setting bank status`() {
        viewModel.bankStatus = "APPROVED"
        viewModel.bankStatusColor = "#00FF00"
        
        assertThat(viewModel.bankStatus).isEqualTo("APPROVED")
        assertThat(viewModel.bankStatusColor).isEqualTo("#00FF00")
    }
    
    @Test
    fun `test message count tracking`() {
        viewModel.totalMessageCount = 100
        viewModel.displayedMessageCount = 50
        
        assertThat(viewModel.totalMessageCount).isEqualTo(100)
        assertThat(viewModel.displayedMessageCount).isEqualTo(50)
    }
    
    @Test
    fun `test permission state`() {
        viewModel.hasAllPermissions = true
        
        assertThat(viewModel.hasAllPermissions).isTrue()
    }
    
    @Test
    fun `test service state`() {
        viewModel.isServiceRunning = true
        
        assertThat(viewModel.isServiceRunning).isTrue()
    }
    
    @Test
    fun `test instruction card state`() {
        viewModel.hasInstructionCard = true
        
        assertThat(viewModel.hasInstructionCard).isTrue()
    }
    
    @Test
    fun `test UI state flags`() {
        viewModel.isTransitioningFromSplash = true
        viewModel.shouldAnimate = true
        
        assertThat(viewModel.isTransitioningFromSplash).isTrue()
        assertThat(viewModel.shouldAnimate).isTrue()
    }
}
