package com.example.fast.ui.activated

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import com.example.fast.util.LogHelper

/**
 * Manages logo animations in ActivatedActivity
 * Rotates through 3 different animations, each playing for 1 minute
 */
class LogoAnimationManager(
    private val logoView: View,
    private val handler: Handler
) {
    private var currentAnimationIndex = 0
    private var currentAnimator: AnimatorSet? = null
    private var rotationRunnable: Runnable? = null
    private var isRunning = false

    // Animation duration: 1 minute = 60,000 milliseconds
    private val ANIMATION_DURATION_MS = 60_000L

    /**
     * Check if animation is currently running
     */
    fun isRunning(): Boolean {
        return isRunning
    }

    // Animation styles enum
    enum class AnimationStyle {
        TYPEWRITER,     // Style 3: Typewriter Effect
        FADE_GLOW,      // Style 10: Fade & Glow
        SHAKE_POP       // Style 14: Shake & Pop
    }

    private val animationStyles = listOf(
        AnimationStyle.TYPEWRITER,
        AnimationStyle.FADE_GLOW,
        AnimationStyle.SHAKE_POP
    )

    /**
     * Start the animation rotation system
     */
    fun start() {
        if (isRunning) {
            stop()
        }

        isRunning = true
        LogHelper.d("LogoAnimationManager", "Starting logo animation rotation")

        // Start with first animation
        playAnimation(animationStyles[0])

        // Schedule rotation
        scheduleNextAnimation()
    }

    /**
     * Stop all animations
     */
    fun stop() {
        isRunning = false
        currentAnimator?.cancel()
        currentAnimator = null
        rotationRunnable?.let { handler.removeCallbacks(it) }
        rotationRunnable = null

        // Reset logo view
        handler.post {
            logoView.clearAnimation()
            logoView.alpha = 1f
            logoView.scaleX = 1f
            logoView.scaleY = 1f
            logoView.rotationX = 0f
            logoView.rotationY = 0f
            logoView.rotation = 0f
            logoView.translationX = 0f
            logoView.translationY = 0f
        }

        LogHelper.d("LogoAnimationManager", "Stopped logo animation rotation")
    }

    /**
     * Schedule next animation rotation
     */
    private fun scheduleNextAnimation() {
        if (!isRunning) return

        rotationRunnable = Runnable {
            if (!isRunning) return@Runnable

            // Move to next animation
            currentAnimationIndex = (currentAnimationIndex + 1) % animationStyles.size
            val nextStyle = animationStyles[currentAnimationIndex]

            LogHelper.d("LogoAnimationManager", "Rotating to animation: $nextStyle")

            // Play next animation
            playAnimation(nextStyle)

            // Schedule next rotation
            scheduleNextAnimation()
        }

        handler.postDelayed(rotationRunnable!!, ANIMATION_DURATION_MS)
    }

    /**
     * Play specific animation style
     */
    private fun playAnimation(style: AnimationStyle) {
        handler.post {
            // Cancel current animation
            currentAnimator?.cancel()

            // Reset view state
            logoView.clearAnimation()
            logoView.alpha = 1f
            logoView.scaleX = 1f
            logoView.scaleY = 1f
            logoView.rotationX = 0f
            logoView.rotationY = 0f
            logoView.rotation = 0f
            logoView.translationX = 0f
            logoView.translationY = 0f

            when (style) {
                AnimationStyle.TYPEWRITER -> animateTypewriter()
                AnimationStyle.FADE_GLOW -> animateFadeGlow()
                AnimationStyle.SHAKE_POP -> animateShakePop()
            }
        }
    }


    /**
     * Animation 3: Typewriter Effect
     */
    private fun animateTypewriter() {
        // Initial scale animation
        currentAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logoView, "scaleX", 0.8f, 1f).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "scaleY", 0.8f, 1f).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "alpha", 0f, 1f).apply {
                    duration = 300
                }
            )
            start()
        }

        // Continuous pulse effect
        handler.postDelayed({
            if (isRunning && currentAnimationIndex == animationStyles.indexOf(AnimationStyle.TYPEWRITER)) {
                val pulseAnimator = ObjectAnimator.ofFloat(logoView, "alpha", 1f, 0.9f, 1f).apply {
                    duration = 2000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                pulseAnimator.start()
            }
        }, 300)
    }


    /**
     * Animation 10: Fade & Glow
     */
    private fun animateFadeGlow() {
        // Initial fade and glow animation
        val enterAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logoView, "alpha", 0f, 1f).apply {
                    duration = 1000
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "scaleX", 0.8f, 1f).apply {
                    duration = 1000
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "scaleY", 0.8f, 1f).apply {
                    duration = 1000
                    interpolator = DecelerateInterpolator()
                }
            )
            start()
        }

        // Continuous glow pulse effect
        handler.postDelayed({
            if (isRunning && currentAnimationIndex == animationStyles.indexOf(AnimationStyle.FADE_GLOW)) {
                val glowAnimator = ObjectAnimator.ofFloat(logoView, "alpha", 1f, 0.95f, 1f).apply {
                    duration = 2000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                glowAnimator.start()
            }
        }, 1000)

        currentAnimator = enterAnimator
    }

    /**
     * Animation 14: Shake & Pop
     */
    private fun animateShakePop() {
        val density = logoView.context.resources.displayMetrics.density

        // Initial shake and pop animation
        currentAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logoView, "translationX", -100f * density, 10f * density, -5f * density, 3f * density, 0f).apply {
                    duration = 1000
                    interpolator = BounceInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "rotation", -20f, 10f, -5f, 2f, 0f).apply {
                    duration = 1000
                    interpolator = BounceInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "scaleX", 0f, 1.2f, 1.1f, 1.05f, 1f).apply {
                    duration = 1000
                    interpolator = BounceInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "scaleY", 0f, 1.2f, 1.1f, 1.05f, 1f).apply {
                    duration = 1000
                    interpolator = BounceInterpolator()
                },
                ObjectAnimator.ofFloat(logoView, "alpha", 0f, 1f).apply {
                    duration = 1000
                }
            )
            start()
        }

        // Continuous subtle shake effect
        handler.postDelayed({
            if (isRunning && currentAnimationIndex == animationStyles.indexOf(AnimationStyle.SHAKE_POP)) {
                val shakeAnimator = ObjectAnimator.ofFloat(logoView, "translationX", 0f, 2f, -2f, 0f).apply {
                    duration = 3000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                shakeAnimator.start()
            }
        }, 1000)
    }
}
