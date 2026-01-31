package com.example.fast.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.fast.R

/**
 * ShineWaveView - Creates a wave-like shine effect that sweeps from start to end
 */
class ShineWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val themePrimary = ContextCompat.getColor(context, R.color.theme_primary)
    
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    private var waveProgress = 0f
    private var waveAnimator: ValueAnimator? = null
    private var isAnimating = false

    /**
     * Start wave shine animation from start to end
     * @param duration Duration in milliseconds (default: 1000ms)
     */
    fun startWaveShine(duration: Long = 1000L) {
        if (isAnimating) {
            stopWaveShine()
        }

        isAnimating = true
        visibility = VISIBLE

        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                waveProgress = animation.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    visibility = GONE
                }
            })
        }
        waveAnimator?.start()
    }

    /**
     * Stop wave shine animation
     */
    fun stopWaveShine() {
        waveAnimator?.cancel()
        waveAnimator = null
        isAnimating = false
        visibility = GONE
        waveProgress = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isAnimating || waveProgress <= 0f) return

        val width = width.toFloat()
        val height = height.toFloat()

        // Calculate wave position (from start to end)
        val waveWidth = width * 0.3f // Wave width is 30% of view width
        val waveStartX = (width + waveWidth) * waveProgress - waveWidth
        val waveEndX = waveStartX + waveWidth

        // Create gradient shader for the wave using theme primary color
        val r = Color.red(themePrimary)
        val g = Color.green(themePrimary)
        val b = Color.blue(themePrimary)
        
        val gradient = LinearGradient(
            waveStartX, 0f, waveEndX, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(200, r, g, b), // Bright theme color in center
                Color.argb(120, r, g, b), // Medium theme color
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        shinePaint.shader = gradient

        // Draw the wave as a rounded rectangle
        val waveRect = RectF(
            waveStartX,
            0f,
            waveEndX,
            height
        )

        // Draw wave with rounded corners
        canvas.drawRoundRect(waveRect, 8f, 8f, shinePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveShine()
    }
}
