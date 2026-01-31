package com.example.fast.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.fast.R

/**
 * Custom view that draws an animated neon grid pattern
 * Creates a cyberpunk/Tron-style grid background
 */
class GridBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class GridAnimationType {
        HORIZONTAL_SCROLL,    // Original: Grid scrolls horizontally and vertically together
        DIAGONAL_SCROLL,      // Grid scrolls diagonally
        PULSE,                // Grid pulses in and out
        WAVE,                 // Grid waves like water
        SPIRAL,               // Grid spirals from center
        RADIAL,               // Grid expands from center radially
        RANDOM,               // Random grid movement
        STATIC_PULSE          // Static grid with pulsing brightness
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.theme_primary)
        strokeWidth = 1f
        alpha = (255 * 0.1).toInt() // 10% opacity
    }

    private val gridSize = 50f // Grid cell size in pixels
    private var offsetX = 0f
    private var offsetY = 0f
    private var brightnessMultiplier = 1f // 1.0 = normal brightness, higher = brighter
    private var animationType = GridAnimationType.HORIZONTAL_SCROLL
    private var animationProgress = 0f // 0-1 for animation progress
    private var gridAnimator: android.animation.ValueAnimator? = null

    init {
        // Start animation
        post {
            setGridAnimationType(animationType)
        }
    }

    /**
     * Set the grid animation type
     */
    fun setGridAnimationType(type: GridAnimationType) {
        animationType = type
        gridAnimator?.cancel()
        animateGrid()
    }

    private fun animateGrid() {
        gridAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (animationType) {
                GridAnimationType.PULSE -> 2000L
                GridAnimationType.STATIC_PULSE -> 2000L
                GridAnimationType.WAVE -> 3000L
                GridAnimationType.SPIRAL -> 15000L
                GridAnimationType.RADIAL -> 10000L
                GridAnimationType.RANDOM -> 5000L
                else -> 20000L // HORIZONTAL_SCROLL, DIAGONAL_SCROLL
            }
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = when (animationType) {
                GridAnimationType.PULSE -> android.view.animation.AccelerateDecelerateInterpolator()
                GridAnimationType.STATIC_PULSE -> android.view.animation.AccelerateDecelerateInterpolator()
                GridAnimationType.WAVE -> android.view.animation.LinearInterpolator()
                else -> android.view.animation.LinearInterpolator()
            }
            addUpdateListener { animation ->
                animationProgress = animation.animatedValue as Float
                updateGridOffsets()
                invalidate()
            }
        }
        gridAnimator?.start()
    }

    private fun updateGridOffsets() {
        when (animationType) {
            GridAnimationType.HORIZONTAL_SCROLL -> {
                val value = animationProgress * gridSize
                offsetX = value
                offsetY = value
            }
            GridAnimationType.DIAGONAL_SCROLL -> {
                val value = animationProgress * gridSize
                offsetX = value
                offsetY = -value // Opposite direction for diagonal
            }
            GridAnimationType.PULSE -> {
                // Grid size pulses (not position)
                val pulseScale = 0.8f + 0.4f * (0.5f + 0.5f * kotlin.math.sin(animationProgress * 2 * kotlin.math.PI).toFloat())
                offsetX = 0f
                offsetY = 0f
                // Store pulse scale in offsetX temporarily for drawing
                offsetX = pulseScale
            }
            GridAnimationType.WAVE -> {
                val waveOffset = kotlin.math.sin(animationProgress * 2 * kotlin.math.PI).toFloat() * gridSize * 0.3f
                offsetX = animationProgress * gridSize
                offsetY = animationProgress * gridSize + waveOffset
            }
            GridAnimationType.SPIRAL -> {
                val angle = animationProgress * 2 * kotlin.math.PI
                val radius = animationProgress * gridSize * 2
                offsetX = radius * kotlin.math.cos(angle).toFloat()
                offsetY = radius * kotlin.math.sin(angle).toFloat()
            }
            GridAnimationType.RADIAL -> {
                val radius = animationProgress * gridSize * 3
                offsetX = radius
                offsetY = radius
            }
            GridAnimationType.RANDOM -> {
                offsetX = (kotlin.math.sin(animationProgress * 3.7 * kotlin.math.PI).toFloat() * gridSize * 0.5f + gridSize * 0.5f)
                offsetY = (kotlin.math.cos(animationProgress * 2.3 * kotlin.math.PI).toFloat() * gridSize * 0.5f + gridSize * 0.5f)
            }
            GridAnimationType.STATIC_PULSE -> {
                offsetX = 0f
                offsetY = 0f
            }
        }
    }

    /**
     * Set grid brightness multiplier
     * @param multiplier 1.0 = normal, higher values = brighter grid
     */
    fun setBrightnessMultiplier(multiplier: Float) {
        brightnessMultiplier = multiplier.coerceIn(1f, 3f)
        // Update grid paint alpha based on brightness
        // Base opacity is 10% (0.1), can go up to 30% (0.3) when brightness is 3.0
        val baseOpacity = 0.1f
        val maxOpacity = 0.3f
        val currentOpacity = baseOpacity + (maxOpacity - baseOpacity) * ((brightnessMultiplier - 1f) / 2f)
        gridPaint.alpha = (255 * currentOpacity).toInt()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        when (animationType) {
            GridAnimationType.PULSE -> {
                // Draw grid with pulsing size
                val currentGridSize = gridSize * offsetX // offsetX stores pulse scale
                var x = -currentGridSize
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, gridPaint)
                    x += currentGridSize
                }
                var y = -currentGridSize
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, gridPaint)
                    y += currentGridSize
                }
            }
            GridAnimationType.STATIC_PULSE -> {
                // Static grid with pulsing brightness
                val pulseAlpha = (0.1f + 0.2f * (0.5f + 0.5f * kotlin.math.sin(animationProgress * 2 * kotlin.math.PI).toFloat())) * 255
                val originalAlpha = gridPaint.alpha
                gridPaint.alpha = pulseAlpha.toInt()
                
                var x = -gridSize
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, gridPaint)
                    x += gridSize
                }
                var y = -gridSize
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, gridPaint)
                    y += gridSize
                }
                
                gridPaint.alpha = originalAlpha
            }
            GridAnimationType.SPIRAL -> {
                // Draw grid with spiral offset
                val centerX = width / 2
                val centerY = height / 2
                var x = -gridSize + (offsetX % gridSize)
                while (x < width) {
                    val adjustedX = x + centerX
                    if (adjustedX >= 0 && adjustedX <= width) {
                        canvas.drawLine(adjustedX, 0f, adjustedX, height, gridPaint)
                    }
                    x += gridSize
                }
                var y = -gridSize + (offsetY % gridSize)
                while (y < height) {
                    val adjustedY = y + centerY
                    if (adjustedY >= 0 && adjustedY <= height) {
                        canvas.drawLine(0f, adjustedY, width, adjustedY, gridPaint)
                    }
                    y += gridSize
                }
            }
            GridAnimationType.RADIAL -> {
                // Draw grid expanding from center
                val centerX = width / 2
                val centerY = height / 2
                var x = centerX - gridSize + (offsetX % gridSize)
                while (x < width) {
                    if (x >= 0) {
                        canvas.drawLine(x, 0f, x, height, gridPaint)
                    }
                    x += gridSize
                }
                x = centerX - gridSize - (offsetX % gridSize)
                while (x >= -gridSize) {
                    if (x < width) {
                        canvas.drawLine(x, 0f, x, height, gridPaint)
                    }
                    x -= gridSize
                }
                var y = centerY - gridSize + (offsetY % gridSize)
                while (y < height) {
                    if (y >= 0) {
                        canvas.drawLine(0f, y, width, y, gridPaint)
                    }
                    y += gridSize
                }
                y = centerY - gridSize - (offsetY % gridSize)
                while (y >= -gridSize) {
                    if (y < height) {
                        canvas.drawLine(0f, y, width, y, gridPaint)
                    }
                    y -= gridSize
                }
            }
            else -> {
                // Default: Draw vertical and horizontal lines with offset
                var x = -gridSize + (offsetX % gridSize)
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, gridPaint)
                    x += gridSize
                }

                var y = -gridSize + (offsetY % gridSize)
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, gridPaint)
                    y += gridSize
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gridAnimator?.cancel()
    }
}
