package com.example.fast.ui.activated

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fast.R
import com.example.fast.databinding.ActivityActivatedBinding
import com.example.fast.util.LogHelper

/**
 * Manages UI setup and visibility for ActivatedActivity
 */
class ActivatedUIManager(
    private val binding: ActivityActivatedBinding,
    private val isTransitioningFromSplash: Boolean
) {
    
    /**
     * Setup UI after branding is loaded
     * Always shows SMS by default (instruction is always available on back side)
     */
    fun setupUIAfterBranding(hasInstructionCard: Boolean) {
        LogHelper.d("ActivatedUIManager", "setupUIAfterBranding() called (always showing SMS by default)")
        
        if (isTransitioningFromSplash) {
            // Coming from SplashActivity - show all elements immediately
            showAllElementsImmediately()
        } else {
            // Coming from ActivationActivity - hide for animation
            hideElementsForAnimation()
        }
        
        setupEdgeToEdgeInsets()
    }
    
    /**
     * Show all UI elements immediately (no animation)
     * Always shows SMS by default
     */
    private fun showAllElementsImmediately() {
        LogHelper.d("ActivatedUIManager", "Path: SplashActivity - showing all elements immediately")
        
        binding.headerSection.alpha = 1f
        binding.headerSection.visibility = View.VISIBLE
        binding.textView11.visibility = View.VISIBLE
        binding.textView12.visibility = View.VISIBLE
        
        // Phone card
        binding.phoneCard.alpha = 1f
        binding.phoneCard.visibility = View.VISIBLE
        
        // Status card
        binding.statusCard.alpha = 1f
        binding.statusCard.visibility = View.VISIBLE
        
        // SMS card is always visible (instruction is always available on back side)
        binding.smsCard.alpha = 1f
        binding.smsCard.visibility = View.VISIBLE
        
        // Always show SMS by default
        binding.smsContentFront.visibility = View.VISIBLE
        binding.smsContentFront.alpha = 1f
        binding.instructionContentBack.visibility = View.GONE
        binding.instructionContentBack.alpha = 0f
        
        // Buttons
        binding.testButtonsContainer.alpha = 1f
        binding.testButtonsContainer.visibility = View.VISIBLE
        binding.testButtonCard.alpha = 1f
        binding.testButtonCard.visibility = View.VISIBLE
        binding.resetButtonCard.alpha = 1f
        binding.resetButtonCard.visibility = View.VISIBLE
        
        LogHelper.d("ActivatedUIManager", "All elements shown immediately (SMS by default)")
    }
    
    /**
     * Hide elements for animation (coming from ActivationActivity)
     * Always shows SMS by default
     */
    private fun hideElementsForAnimation() {
        LogHelper.d("ActivatedUIManager", "Path: ActivationActivity - hiding elements for animation")
        
        // Ensure headerSection (logo) is always visible
        binding.headerSection.alpha = 1f
        binding.headerSection.visibility = View.VISIBLE
        binding.textView11.visibility = View.VISIBLE
        binding.textView12.visibility = View.VISIBLE
        
        // Hide cards and buttons for animation
        binding.phoneCard.alpha = 0f
        binding.phoneCard.visibility = View.VISIBLE
        binding.statusCard.alpha = 0f
        binding.statusCard.visibility = View.VISIBLE
        
        // SMS card is always visible (instruction is always available on back side)
        binding.smsCard.alpha = 0f
        binding.smsCard.visibility = View.VISIBLE
        
        // Always show SMS by default
        binding.smsContentFront.alpha = 0f
        binding.smsContentFront.visibility = View.VISIBLE
        binding.instructionContentBack.alpha = 0f
        binding.instructionContentBack.visibility = View.GONE
        
        binding.testButtonsContainer.alpha = 0f
        binding.testButtonsContainer.visibility = View.VISIBLE
        
        // Animate cards and buttons in
        binding.phoneCard.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(200)
            .start()
        
        binding.statusCard.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(300)
            .start()
        
        // Animate SMS card
        binding.smsCard.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(400)
            .withEndAction {
                // After SMS card fades in, fade in SMS side (default)
                binding.smsContentFront.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
            .start()
        
        binding.testButtonsContainer.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(500)
            .start()
    }
    
    /**
     * Setup edge-to-edge insets handling
     */
    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val screenHeight = binding.root.resources.displayMetrics.heightPixels
            val logoPositionGuideY = screenHeight * 0.20f // 20% from top
            
            // Only add padding if logo would be under status bar
            val topPadding = if (logoPositionGuideY < systemBars.top) {
                (systemBars.top - logoPositionGuideY + 10).toInt()
            } else {
                0
            }
            
            binding.headerSection.setPadding(
                binding.headerSection.paddingLeft,
                topPadding,
                binding.headerSection.paddingRight,
                binding.headerSection.paddingBottom
            )
            insets
        }
    }
    
    /**
     * Show elements immediately (no animation path)
     * Always shows SMS by default
     */
    fun showElementsImmediately(hasInstructionCard: Boolean) {
        binding.headerSection.alpha = 1f
        binding.headerSection.visibility = View.VISIBLE
        binding.textView11.visibility = View.VISIBLE
        binding.textView12.visibility = View.VISIBLE
        binding.phoneCard.alpha = 1f
        binding.phoneCard.visibility = View.VISIBLE
        binding.statusCard.alpha = 1f
        binding.statusCard.visibility = View.VISIBLE
        
        // SMS card is always visible (instruction is always available on back side)
        binding.smsCard.alpha = 1f
        binding.smsCard.visibility = View.VISIBLE
        
        // Always show SMS by default
        binding.smsContentFront.visibility = View.VISIBLE
        binding.smsContentFront.alpha = 1f
        binding.instructionContentBack.visibility = View.GONE
        binding.instructionContentBack.alpha = 0f
        
        binding.testButtonsContainer.alpha = 1f
        binding.testButtonsContainer.visibility = View.VISIBLE
    }
    
    /**
     * Ensure all elements are visible (safety check)
     * Always shows SMS by default
     */
    fun ensureElementsVisible(hasInstructionCard: Boolean) {
        binding.headerSection.alpha = 1f
        binding.headerSection.visibility = View.VISIBLE
        binding.textView11.visibility = View.VISIBLE
        binding.textView12.visibility = View.VISIBLE
        binding.phoneCard.alpha = 1f
        binding.phoneCard.visibility = View.VISIBLE
        binding.statusCard.alpha = 1f
        binding.statusCard.visibility = View.VISIBLE
        
        // SMS card is always visible (instruction is always available on back side)
        binding.smsCard.alpha = 1f
        binding.smsCard.visibility = View.VISIBLE
        
        // Always show SMS by default
        binding.smsContentFront.visibility = View.VISIBLE
        binding.smsContentFront.alpha = 1f
        binding.instructionContentBack.visibility = View.GONE
        binding.instructionContentBack.alpha = 0f
        
        binding.testButtonsContainer.alpha = 1f
        binding.testButtonsContainer.visibility = View.VISIBLE
    }
}
