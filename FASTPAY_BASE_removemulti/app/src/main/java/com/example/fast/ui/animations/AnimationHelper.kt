package com.example.fast.ui.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * AnimationHelper
 *
 * Centralized animation utilities for consistent, smooth animations across the app.
 * Follows Material Design 3 motion principles.
 *
 * Features:
 * - Standard durations and interpolators
 * - Reusable animation functions
 * - Performance optimized (hardware acceleration)
 * - Proper cleanup handling
 */
object AnimationHelper {

    // ============================================================================
    // STANDARD DURATIONS (Material Design 3)
    // ============================================================================

    /** Short duration for quick feedback (200ms) */
    const val DURATION_SHORT = 200L

    /** Medium duration for standard transitions (300ms) */
    const val DURATION_MEDIUM = 300L

    /** Long duration for complex animations (400ms) */
    const val DURATION_LONG = 400L

    /** Extra long duration for prominent animations (600ms) */
    const val DURATION_EXTRA_LONG = 600L

    // ============================================================================
    // STANDARD INTERPOLATORS
    // ============================================================================

    /** Ease out - decelerates smoothly */
    val EASE_OUT = DecelerateInterpolator()

    /** Ease in-out with slight overshoot */
    val EASE_IN_OUT = OvershootInterpolator(1.1f)

    /** Ease out cubic - smooth deceleration */
    val EASE_OUT_CUBIC = AccelerateDecelerateInterpolator()

    // ============================================================================
    // FADE ANIMATIONS
    // ============================================================================

    /**
     * Fade in animation
     * @param view View to animate
     * @param duration Animation duration (default: DURATION_MEDIUM)
     * @param onEnd Optional callback when animation completes
     */
    fun fadeIn(
        view: View,
        duration: Long = DURATION_MEDIUM,
        onEnd: (() -> Unit)? = null
    ) {
        view.alpha = 0f
        view.visibility = View.VISIBLE

        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(EASE_OUT)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    /**
     * Fade out animation
     * @param view View to animate
     * @param duration Animation duration (default: DURATION_SHORT)
     * @param onEnd Optional callback when animation completes
     */
    fun fadeOut(
        view: View,
        duration: Long = DURATION_SHORT,
        onEnd: (() -> Unit)? = null
    ) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(EASE_OUT)
            .withEndAction {
                view.visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    // ============================================================================
    // SLIDE ANIMATIONS
    // ============================================================================

    /**
     * Slide up animation
     * @param view View to animate
     * @param distance Distance to slide (default: 50dp converted to pixels)
     * @param duration Animation duration (default: DURATION_MEDIUM)
     * @param onEnd Optional callback when animation completes
     */
    fun slideUp(
        view: View,
        distance: Float = 50f,
        duration: Long = DURATION_MEDIUM,
        onEnd: (() -> Unit)? = null
    ) {
        view.translationY = distance
        view.alpha = 0f
        view.visibility = View.VISIBLE

        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(EASE_OUT)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    /**
     * Slide down animation
     * @param view View to animate
     * @param distance Distance to slide (default: 50dp converted to pixels)
     * @param duration Animation duration (default: DURATION_MEDIUM)
     * @param onEnd Optional callback when animation completes
     */
    fun slideDown(
        view: View,
        distance: Float = 50f,
        duration: Long = DURATION_MEDIUM,
        onEnd: (() -> Unit)? = null
    ) {
        view.translationY = -distance
        view.alpha = 0f
        view.visibility = View.VISIBLE

        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(EASE_OUT)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    // ============================================================================
    // SCALE ANIMATIONS
    // ============================================================================

    /**
     * Scale in animation (from small to normal)
     * @param view View to animate
     * @param scale Initial scale (default: 0.9f)
     * @param duration Animation duration (default: DURATION_MEDIUM)
     * @param onEnd Optional callback when animation completes
     */
    fun scaleIn(
        view: View,
        scale: Float = 0.9f,
        duration: Long = DURATION_MEDIUM,
        onEnd: (() -> Unit)? = null
    ) {
        view.scaleX = scale
        view.scaleY = scale
        view.alpha = 0f
        view.visibility = View.VISIBLE

        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(EASE_IN_OUT)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    /**
     * Scale out animation (from normal to small)
     * @param view View to animate
     * @param scale Final scale (default: 0.9f)
     * @param duration Animation duration (default: DURATION_SHORT)
     * @param onEnd Optional callback when animation completes
     */
    fun scaleOut(
        view: View,
        scale: Float = 0.9f,
        duration: Long = DURATION_SHORT,
        onEnd: (() -> Unit)? = null
    ) {
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(EASE_OUT)
            .withEndAction {
                view.visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    // ============================================================================
    // CARD ENTER ANIMATION
    // ============================================================================

    /**
     * Animate card entry with fade, slide, and scale
     * @param card View to animate
     * @param delay Delay before starting animation (default: 0)
     * @param onEnd Optional callback when animation completes
     */
    fun animateCardEnter(
        card: View,
        delay: Long = 0,
        onEnd: (() -> Unit)? = null
    ) {
        card.alpha = 0f
        card.translationY = 60f
        card.scaleX = 0.95f
        card.scaleY = 0.95f
        card.visibility = View.VISIBLE

        card.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(DURATION_MEDIUM)
            .setInterpolator(EASE_OUT_CUBIC)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    // ============================================================================
    // STAGGERED LIST ANIMATIONS
    // ============================================================================

    /**
     * Animate list items with staggered delay
     * @param container ViewGroup containing items to animate
     * @param delayBetween Delay between each item (default: 50ms)
     * @param animationType Type of animation (default: CARD_ENTER)
     */
    fun animateListItems(
        container: android.view.ViewGroup,
        delayBetween: Long = 50,
        animationType: AnimationType = AnimationType.CARD_ENTER
    ) {
        // Iterate through child views
        for (index in 0 until container.childCount) {
            val view = container.getChildAt(index)
            val delay = index * delayBetween
            when (animationType) {
                AnimationType.CARD_ENTER -> animateCardEnter(view, delay)
                AnimationType.FADE_IN -> fadeIn(view, DURATION_MEDIUM)
                AnimationType.SLIDE_UP -> slideUp(view, 50f, DURATION_MEDIUM)
                AnimationType.SCALE_IN -> scaleIn(view, 0.9f, DURATION_MEDIUM)
            }
        }
    }

    /**
     * Animation types for list items
     */
    enum class AnimationType {
        CARD_ENTER,
        FADE_IN,
        SLIDE_UP,
        SCALE_IN
    }

    // ============================================================================
    // PERFORMANCE OPTIMIZATION
    // ============================================================================

    /**
     * Enable hardware acceleration for view during animation
     * Call this before starting animation, and disable after
     */
    fun enableHardwareAcceleration(view: View) {
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Disable hardware acceleration after animation completes
     */
    fun disableHardwareAcceleration(view: View) {
        view.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /**
     * Animate with hardware acceleration (automatically enabled/disabled)
     */
    fun animateWithHardwareAccel(
        view: View,
        animation: Animator,
        onEnd: (() -> Unit)? = null
    ) {
        enableHardwareAcceleration(view)
        animation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                disableHardwareAcceleration(view)
                onEnd?.invoke()
            }
        })
        animation.start()
    }
}
