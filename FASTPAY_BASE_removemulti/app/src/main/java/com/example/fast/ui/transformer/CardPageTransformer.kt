package com.example.fast.ui.transformer

import android.os.Build
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Page transformer for swipeable cards
 * Creates a card effect with scale and translation animations
 */
class CardPageTransformer : ViewPager2.PageTransformer {

    companion object {
        private const val MIN_SCALE = 0.85f
        private const val MIN_ALPHA = 0.5f
        private const val MAX_TRANSLATION = 0.3f
    }

    override fun transformPage(page: View, position: Float) {
        val pageWidth = page.width
        val pageHeight = page.height

        when {
            position < -1 -> {
                // Page is off-screen to the left
                page.alpha = 0f
            }
            position <= 1 -> {
                // Page is visible or partially visible
                val scaleFactor = MIN_SCALE.coerceAtLeast(1 - abs(position) * (1 - MIN_SCALE))
                val alphaFactor = MIN_ALPHA.coerceAtLeast(1 - abs(position) * (1 - MIN_ALPHA))
                val translationX = -position * pageWidth * MAX_TRANSLATION

                page.scaleX = scaleFactor
                page.scaleY = scaleFactor
                page.alpha = alphaFactor
                page.translationX = translationX

                // Add elevation for depth effect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    page.elevation = if (abs(position) < 1) {
                        (1 - abs(position)) * 8f
                    } else {
                        0f
                    }
                }
            }
            else -> {
                // Page is off-screen to the right
                page.alpha = 0f
            }
        }
    }
}
