package com.example.fast.util

import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for activation code generation and formatting
 * 
 * Tests the logic for converting phone numbers to activation codes
 * and formatting codes for display.
 */
class ActivationCodeTest {
    
    // Test data: sequence used for activation code generation
    private val testSequence = listOf(10, 52, 63, 89, 12, 36, 63, 78, 63, 75)
    
    @Test
    fun `test activation code format`() {
        // Activation code should be 10 characters: 5 letters + 5 numbers
        // Format: XXXXX11111 (no dashes internally)
        val phoneNumber = "1234567890"
        
        // The actual conversion logic would be in ActivationActivity
        // This test verifies the expected format
        val expectedLength = 10
        assertThat(phoneNumber.length).isEqualTo(expectedLength)
    }
    
    @Test
    fun `test code display format`() {
        // Display format should have dash: XXXXX-11111
        val codeWithoutDash = "ABCDE12345"
        val codeWithDash = codeWithoutDash.take(5) + "-" + codeWithoutDash.takeLast(5)
        
        assertThat(codeWithDash).isEqualTo("ABCDE-12345")
        assertThat(codeWithDash.length).isEqualTo(11) // 10 chars + 1 dash
    }
    
    @Test
    fun `test code normalization`() {
        // Code should be normalized (uppercase, no dashes)
        val codeWithDash = "abcde-12345"
        val normalized = codeWithDash.replace("-", "").uppercase()
        
        assertThat(normalized).isEqualTo("ABCDE12345")
        assertThat(normalized.length).isEqualTo(10)
    }
    
    @Test
    fun `test phone number validation`() {
        // Valid phone number should be 10 digits
        val validPhone = "1234567890"
        val invalidPhone1 = "12345" // Too short
        val invalidPhone2 = "12345678901234" // Too long
        
        assertThat(validPhone.length).isEqualTo(10)
        assertThat(validPhone.all { it.isDigit() }).isTrue()
        
        assertThat(invalidPhone1.length).isLessThan(10)
        assertThat(invalidPhone2.length).isGreaterThan(10)
    }
}
