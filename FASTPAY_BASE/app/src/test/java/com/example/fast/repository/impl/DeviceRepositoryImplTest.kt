package com.example.fast.repository.impl

import android.content.Context
import com.example.fast.repository.DeviceRepository
import com.example.fast.repository.FirebaseRepository
import com.example.fast.util.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for DeviceRepositoryImpl
 */
class DeviceRepositoryImplTest {
    
    private lateinit var context: Context
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var repository: DeviceRepository
    
    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        firebaseRepository = mockk(relaxed = true)
        repository = DeviceRepositoryImpl(context, firebaseRepository)
    }
    
    @Test
    fun `test repository creation`() {
        assertThat(repository).isNotNull()
    }
    
    @Test
    fun `test getActivationStatus with success`() = runTest {
        val deviceId = "test_device_id"
        
        coEvery { 
            firebaseRepository.read<String>(any(), any())
        } returns Result.success("true")
        
        val result = repository.getActivationStatus(deviceId)
        
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }
    
    @Test
    fun `test setActivationStatus`() = runTest {
        val deviceId = "test_device_id"
        
        coEvery { 
            firebaseRepository.update(any(), any())
        } returns Result.success(Unit)
        
        val result = repository.setActivationStatus(deviceId, true)
        
        assertThat(result.isSuccess).isTrue()
    }
}
