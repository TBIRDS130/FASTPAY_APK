package com.example.fast.util.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.widget.TextView

/**
 * ShimmerHelper - Provides shimmer loading effect for views
 * 
 * Creates a shimmer (moving gradient) effect to indicate loading state.
 * Can be applied to any view or text.
 */
object ShimmerHelper {

    private val activeAnimators = mutableMapOf<View, ValueAnimator>()

    /**
     * Start shimmer effect on a view by animating its alpha
     * Simple but effective loading indication
     */
    fun startShimmer(view: View, minAlpha: Float = 0.3f, maxAlpha: Float = 1.0f, duration: Long = 1000L) {
        stopShimmer(view)
        
        val animator = ObjectAnimator.ofFloat(view, "alpha", maxAlpha, minAlpha, maxAlpha).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        
        activeAnimators[view] = animator
        animator.start()
    }

    /**
     * Stop shimmer effect on a view
     */
    fun stopShimmer(view: View) {
        activeAnimators[view]?.cancel()
        activeAnimators.remove(view)
        view.alpha = 1f
    }

    /**
     * Apply shimmer gradient to a TextView
     * Creates an animated gradient text effect
     */
    fun startTextShimmer(
        textView: TextView,
        baseColor: Int,
        shimmerColor: Int,
        duration: Long = 1500L
    ) {
        textView.post {
            val width = textView.width.toFloat()
            if (width <= 0) return@post

            val animator = ValueAnimator.ofFloat(-width, width * 2).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART

                addUpdateListener { animation ->
                    val offset = animation.animatedValue as Float
                    val gradient = LinearGradient(
                        offset, 0f,
                        offset + width, 0f,
                        intArrayOf(baseColor, shimmerColor, baseColor),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    textView.paint.shader = gradient
                    textView.invalidate()
                }
            }

            activeAnimators[textView] = animator
            animator.start()
        }
    }

    /**
     * Stop text shimmer effect
     */
    fun stopTextShimmer(textView: TextView) {
        activeAnimators[textView]?.cancel()
        activeAnimators.remove(textView)
        textView.paint.shader = null
        textView.invalidate()
    }

    /**
     * Show loading state for a card/container
     * Fades in a shimmer placeholder
     */
    fun showCardLoading(card: View) {
        startShimmer(card, minAlpha = 0.5f, maxAlpha = 0.9f, duration = 800L)
    }

    /**
     * Hide loading state for a card/container
     */
    fun hideCardLoading(card: View) {
        stopShimmer(card)
    }

    /**
     * Cleanup all shimmer animations
     * Call this when activity/fragment is destroyed
     */
    fun cleanup() {
        activeAnimators.values.forEach { it.cancel() }
        activeAnimators.clear()
    }
}
