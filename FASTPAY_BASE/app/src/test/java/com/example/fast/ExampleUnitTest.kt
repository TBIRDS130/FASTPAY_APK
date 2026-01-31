package com.example.fast

import org.junit.Test
import org.junit.Assert.*
import io.mockk.mockk
import com.google.common.truth.Truth.assertThat

/**
 * Example unit test to verify testing framework is set up correctly
 * 
 * This test serves as a template and verification that all testing dependencies
 * are properly configured.
 */
class ExampleUnitTest {
    
    @Test
    fun `test basic assertion works`() {
        // Arrange
        val expected = 4
        
        // Act
        val actual = 2 + 2
        
        // Assert
        assertEquals(expected, actual)
    }
    
    @Test
    fun `test Truth assertions work`() {
        // Arrange
        val list = listOf(1, 2, 3)
        
        // Act & Assert
        assertThat(list).hasSize(3)
        assertThat(list).contains(2)
    }
    
    @Test
    fun `test MockK works`() {
        // Arrange
        val mockObject = mockk<Any>()
        
        // Act & Assert
        assertNotNull(mockObject)
    }
}
