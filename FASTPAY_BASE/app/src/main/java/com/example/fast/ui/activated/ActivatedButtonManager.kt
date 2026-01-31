package com.example.fast.ui.activated

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.fast.R
import com.example.fast.databinding.ActivityActivatedBinding
import com.example.fast.ui.interactions.MicroInteractionHelper
import com.example.fast.util.LogHelper

/**
 * Manages button setup and interactions for ActivatedActivity
 */
class ActivatedButtonManager(
    private val binding: ActivityActivatedBinding,
    private val context: Context,
    private val onResetClick: () -> Unit,
    private val onTestClick: () -> Unit
) {
    
    /**
     * Setup all buttons
     */
    fun setupButtons() {
        setupResetButton()
        setupTestButton()
    }
    
    /**
     * Setup reset button
     */
    private fun setupResetButton() {
        MicroInteractionHelper.addCardPressAndLift(binding.resetButtonCard, 0.97f, 4f)
        
        binding.resetButtonCard.setOnClickListener {
            // Flash animation
            binding.resetButtonCard.animate()
                .alpha(0.7f)
                .setDuration(100)
                .withEndAction {
                    binding.resetButtonCard.animate()
                        .alpha(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
            
            Toast.makeText(context, "Resetting activation...", Toast.LENGTH_SHORT).show()
            onResetClick()
        }
    }
    
    /**
     * Setup test button
     */
    private fun setupTestButton() {
        try {
            val testButton = binding.testButtonCard
            if (testButton == null) {
                LogHelper.e("ActivatedButtonManager", "testButtonCard is null!")
                return
            }
            
            LogHelper.d("ActivatedButtonManager", "Setting up test button")
            MicroInteractionHelper.addCardPressAndLift(testButton, 0.97f, 4f)
            
            testButton.setOnClickListener { view ->
                try {
                    LogHelper.d("ActivatedButtonManager", "Test button clicked!")
                    
                    // Flash animation
                    testButton.animate()
                        .alpha(0.7f)
                        .setDuration(100)
                        .withEndAction {
                            testButton.animate()
                                .alpha(1.0f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                    
                    Toast.makeText(context, "Sending test SMS...", Toast.LENGTH_SHORT).show()
                    onTestClick()
                } catch (e: Exception) {
                    LogHelper.e("ActivatedButtonManager", "Error in test button click", e)
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            LogHelper.d("ActivatedButtonManager", "Test button click listener set successfully")
        } catch (e: Exception) {
            LogHelper.e("ActivatedButtonManager", "Error setting up test button", e)
        }
    }
}
