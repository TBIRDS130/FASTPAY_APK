package com.example.fast.ui.interactions

import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.example.fast.ui.animations.AnimationHelper

/**
 * MicroInteractionHelper
 * 
 * Provides micro-interactions for better user feedback:
 * - Button press animations
 * - Card lift effects
 * - Ripple-like feedback
 * - Touch scale animations
 */
object MicroInteractionHelper {
    
    /**
     * Add press animation to a button/view
     * Scales down on press, scales up on release
     * 
     * @param view View to add press animation to
     * @param scale Scale factor when pressed (default: 0.95f)
     */
    fun addPressAnimation(view: View, scale: Float = 0.95f) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Scale down on press
                    v.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .setInterpolator(AnimationHelper.EASE_OUT)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Scale back up on release
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .setInterpolator(OvershootInterpolator(1.1f))
                        .start()
                }
            }
            false // Don't consume the event, let click listener handle it
        }
    }
    
    /**
     * Add card lift effect on touch
     * Increases elevation when touched
     * 
     * @param view Card view to add lift effect to
     * @param elevationIncrease Elevation increase in dp (default: 8dp)
     */
    fun addCardLiftEffect(view: View, elevationIncrease: Float = 8f) {
        val originalElevation = view.elevation
        val liftedElevation = originalElevation + elevationIncrease
        
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Lift card on touch
                    v.animate()
                        .translationZ(liftedElevation)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Lower card on release
                    v.animate()
                        .translationZ(originalElevation)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .start()
                }
            }
            false
        }
    }
    
    /**
     * Add ripple-like feedback animation
     * Creates a subtle scale pulse effect
     * 
     * @param view View to add ripple effect to
     */
    fun addRippleFeedback(view: View) {
        view.setOnClickListener {
            // Create ripple effect
            view.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }
    
    /**
     * Add smooth hover effect (for touch devices)
     * Slightly scales up when touched
     * 
     * @param view View to add hover effect to
     * @param scale Scale factor (default: 1.02f)
     */
    fun addHoverEffect(view: View, scale: Float = 1.02f) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .start()
                }
            }
            false
        }
    }
    
    /**
     * Add combined press and lift effect for cards
     * Combines scale and elevation changes
     * 
     * @param view Card view
     * @param scale Scale factor when pressed (default: 0.97f)
     * @param elevationIncrease Elevation increase (default: 4f)
     */
    fun addCardPressAndLift(view: View, scale: Float = 0.97f, elevationIncrease: Float = 4f) {
        val originalElevation = view.elevation
        val liftedElevation = originalElevation + elevationIncrease
        
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Scale down and lift up
                    v.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationZ(liftedElevation)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Scale back and lower
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationZ(originalElevation)
                        .setDuration(AnimationHelper.DURATION_SHORT)
                        .setInterpolator(OvershootInterpolator(1.05f))
                        .start()
                }
            }
            false
        }
    }
}

