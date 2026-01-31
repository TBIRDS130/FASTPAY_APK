package com.example.fast.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.fast.R

/**
 * Cyberpunk Grid Border - Animated grid pattern overlay with border glow
 * Features:
 * - Solid border with glow effect
 * - Animated grid pattern that moves continuously
 * - Cyberpunk aesthetic
 */
class CyberpunkGridBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var borderColor = ContextCompat.getColor(context, R.color.theme_primary)
    private var borderWidth = 1f * resources.displayMetrics.density // 1dp
    private var cornerRadius = 16f * resources.displayMetrics.density // 16dp default
    private var gridSize = 20f * resources.displayMetrics.density // 20dp grid cells
    private var gridOpacity = 0.2f // 20% opacity for grid
    private var animationProgress = 0f // 0-1 for grid movement

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        strokeWidth = borderWidth
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        strokeWidth = borderWidth
        style = Paint.Style.STROKE
        maskFilter = android.graphics.BlurMaskFilter(5f * resources.displayMetrics.density, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        strokeWidth = 1f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        alpha = (gridOpacity * 255).toInt()
    }

    private val path = Path()
    private val rectF = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 10000L // 10 seconds for full cycle (matching HTML)
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            animationProgress = animation.animatedValue as Float
            invalidate()
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for glow effect
    }

    fun setBorderColor(color: Int) {
        borderColor = color
        borderPaint.color = color
        glowPaint.color = color
        gridPaint.color = color
        invalidate()
    }

    fun setCornerRadius(radiusDp: Float) {
        cornerRadius = radiusDp * resources.displayMetrics.density
        invalidate()
    }

    fun setGridSize(sizeDp: Float) {
        gridSize = sizeDp * resources.displayMetrics.density
        invalidate()
    }

    fun setGridOpacity(opacity: Float) {
        gridOpacity = opacity.coerceIn(0f, 1f)
        gridPaint.alpha = (gridOpacity * 255).toInt()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        rectF.set(0f, 0f, width.toFloat(), height.toFloat())

        // Draw border with rounded corners
        path.reset()
        path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)

        // Draw glow effect (outer)
        canvas.drawPath(path, glowPaint)

        // Draw main border
        canvas.drawPath(path, borderPaint)

        // Draw inner glow (inset)
        val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            strokeWidth = borderWidth * 0.5f
            style = Paint.Style.STROKE
            alpha = (0.1f * 255).toInt()
            maskFilter = android.graphics.BlurMaskFilter(5f * resources.displayMetrics.density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, innerGlowPaint)

        // Draw animated grid pattern
        drawAnimatedGrid(canvas, rectF)
    }

    private fun drawAnimatedGrid(canvas: Canvas, rect: RectF) {
        // Calculate grid offset based on animation progress
        // Grid moves from 0 to gridSize (matching HTML: 0% to 100% = 0px to 20px)
        val offsetX = animationProgress * gridSize
        val offsetY = animationProgress * gridSize

        // Draw vertical grid lines
        var x = rect.left - gridSize + (offsetX % gridSize)
        while (x <= rect.right + gridSize) {
            canvas.drawLine(x, rect.top, x, rect.bottom, gridPaint)
            x += gridSize
        }

        // Draw horizontal grid lines
        var y = rect.top - gridSize + (offsetY % gridSize)
        while (y <= rect.bottom + gridSize) {
            canvas.drawLine(rect.left, y, rect.right, y, gridPaint)
            y += gridSize
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
