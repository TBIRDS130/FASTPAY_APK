package com.example.fast.ui.activated

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.fast.R
import com.example.fast.databinding.ActivityActivatedBinding
import com.example.fast.ui.interactions.MicroInteractionHelper

/**
 * Manages button setup and interactions for ActivatedActivity
 */
class ActivatedButtonManager(
    private val binding: ActivityActivatedBinding,
    private val context: Context,
    private val onResetClick: () -> Unit
) {

    /**
     * Setup all buttons
     */
    fun setupButtons() {
        setupResetButton()
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

}
