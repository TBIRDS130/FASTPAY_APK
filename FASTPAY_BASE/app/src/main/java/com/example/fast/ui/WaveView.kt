package com.example.fast.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.fast.R

/**
 * Custom view that creates a center spread wave effect
 * Wave spreads from center (logo position) to both sides (left and right)
 * Grid becomes lighter/brightens as the wave passes, then returns to basic state
 * Wave form pattern with smooth transitions
 */
class WaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridBrightnessPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.theme_primary)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private var waveProgress = 0f // 0f to 1f
    private var centerX = 0f
    private var isAnimating = false
    private var waveAnimator: android.animation.ValueAnimator? = null
    private val themeColor = ContextCompat.getColor(context, R.color.theme_primary)
    private var gridBrightnessMultiplier = 1f // 1.0 = normal, higher = brighter

    /**
     * Callback interface for brightness updates
     */
    interface BrightnessUpdateListener {
        fun onBrightnessUpdate(multiplier: Float)
    }

    private var brightnessListener: BrightnessUpdateListener? = null

    /**
     * Set listener for brightness updates
     */
    fun setBrightnessUpdateListener(listener: BrightnessUpdateListener?) {
        brightnessListener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Center X is where the logo is (middle of screen horizontally)
        centerX = width / 2f
    }

    /**
     * Start center spread wave animation expanding from center to both sides (left and right)
     * Creates a brightening effect on the grid as wave passes, then returns to normal
     * @param duration Duration in milliseconds
     */
    fun startWaveAnimation(duration: Long = 2000L) {
        if (isAnimating) {
            stopWaveAnimation()
        }

        isAnimating = true
        waveProgress = 0f
        gridBrightnessMultiplier = 1f
        visibility = View.VISIBLE

        waveAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                waveProgress = animation.animatedValue as Float

                // Calculate grid brightness based on wave progress
                // Peak brightness at 25% (0.25), fade back to normal at 50% (0.5)
                gridBrightnessMultiplier = when {
                    waveProgress <= 0.25f -> {
                        // Brightening phase: 1.0 to 3.0
                        1f + (waveProgress / 0.25f) * 2f
                    }
                    waveProgress <= 0.5f -> {
                        // Fading back phase: 3.0 to 1.0
                        3f - ((waveProgress - 0.25f) / 0.25f) * 2f
                    }
                    else -> {
                        // Return to normal
                        1f
                    }
                }

                // Notify listener of brightness change
                brightnessListener?.onBrightnessUpdate(gridBrightnessMultiplier)

                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    waveProgress = 0f
                    gridBrightnessMultiplier = 1f
                    visibility = View.GONE
                }
            })
        }
        waveAnimator?.start()
    }

    /**
     * Stop wave animation
     */
    fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
        isAnimating = false
        waveProgress = 0f
        gridBrightnessMultiplier = 1f
        visibility = View.GONE
        invalidate()
    }

    /**
     * Get current grid brightness multiplier for GridBackgroundView
     */
    fun getGridBrightnessMultiplier(): Float = gridBrightnessMultiplier

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isAnimating || waveProgress <= 0f || visibility != View.VISIBLE) {
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()

        // Calculate wave expansion from center to both sides (left and right)
        val maxDistance = width / 2f
        val currentDistance = maxDistance * waveProgress

        // Create wave form pattern with smooth fade
        // Calculate alpha based on progress - peak at 25%, fade by 50%
        val waveAlpha = when {
            waveProgress <= 0.25f -> {
                // Fade in: 0 to 0.5
                (waveProgress / 0.25f) * 0.5f
            }
            waveProgress <= 0.5f -> {
                // Peak then fade: 0.5 to 0.3
                0.5f - ((waveProgress - 0.25f) / 0.25f) * 0.2f
            }
            else -> {
                // Continue fading: 0.3 to 0
                0.3f * (1f - (waveProgress - 0.5f) / 0.5f)
            }
        }.coerceIn(0f, 0.5f)

        // Left wave (expanding leftward from center)
        val leftStartX = (centerX - currentDistance).coerceAtLeast(0f)
        val leftEndX = centerX

        if (leftEndX > leftStartX && waveAlpha > 0f) {
            val leftGradient = LinearGradient(
                leftEndX, 0f,
                leftStartX, 0f,
                intArrayOf(
                    (themeColor and 0x00FFFFFF) or ((255 * waveAlpha).toInt() shl 24), // Bright at center
                    (themeColor and 0x00FFFFFF) or ((255 * waveAlpha * 0.6f).toInt() shl 24), // Medium
                    (themeColor and 0x00FFFFFF) or ((255 * waveAlpha * 0.3f).toInt() shl 24), // Light
                    0x00000000 // Transparent at edge
                ),
                floatArrayOf(0f, 0.3f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            lightPaint.shader = leftGradient
            canvas.drawRect(leftStartX, 0f, leftEndX, height, lightPaint)
        }

        // Right wave (expanding rightward from center)
        val rightStartX = centerX
        val rightEndX = (centerX + currentDistance).coerceAtMost(width)

        if (rightEndX > rightStartX && waveAlpha > 0f) {
            val rightGradient = LinearGradient(
                rightStartX, 0f,
                rightEndX, 0f,
                intArrayOf(
                    (themeColor and 0x00FFFFFF) or ((255 * waveAlpha).toInt() shl 24), // Bright at center
                    (themeColor and 0x00FFFFFF) or ((255 * waveAlpha * 0.6f).toInt() shl 24), // Medium
                    (themeColor and 0x00FFFFFF) or ((255 * waveAlpha * 0.3f).toInt() shl 24), // Light
                    0x00000000 // Transparent at edge
                ),
                floatArrayOf(0f, 0.3f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            lightPaint.shader = rightGradient
            canvas.drawRect(rightStartX, 0f, rightEndX, height, lightPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveAnimation()
    }
}
