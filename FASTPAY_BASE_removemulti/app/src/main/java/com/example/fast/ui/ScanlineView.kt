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
 * Custom view that draws an animated scanline effect
 * Creates a sweeping light effect across the screen (like Tron)
 */
class ScanlineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scanlineY = 0f
    private var scanlineHeight = 3f

    init {
        // Create gradient for scanline
        scanlinePaint.shader = LinearGradient(
            0f, 0f, 0f, scanlineHeight,
            intArrayOf(
                0x0000ff88, // Transparent
                0xFF00ff88.toInt(), // Full neon green
                0x0000ff88  // Transparent
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update gradient when size changes
        scanlinePaint.shader = LinearGradient(
            0f, 0f, 0f, scanlineHeight,
            intArrayOf(
                0x0000ff88,
                0xFF00ff88.toInt(),
                0x0000ff88
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    fun startScanlineAnimation() {
        val animator = android.animation.ValueAnimator.ofFloat(0f, height.toFloat()).apply {
            duration = 2000 // 2 seconds for full sweep
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animation ->
                scanlineY = animation.animatedValue as Float
                invalidate()
            }
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (scanlineY > 0 && scanlineY < height) {
            // Draw scanline with glow effect
            canvas.drawRect(
                0f,
                scanlineY - scanlineHeight / 2,
                width.toFloat(),
                scanlineY + scanlineHeight / 2,
                scanlinePaint
            )

            // Add glow effect (multiple passes for stronger glow)
            scanlinePaint.alpha = 100
            canvas.drawRect(
                0f,
                scanlineY - scanlineHeight,
                width.toFloat(),
                scanlineY + scanlineHeight,
                scanlinePaint
            )
            scanlinePaint.alpha = 255
        }
    }
}
