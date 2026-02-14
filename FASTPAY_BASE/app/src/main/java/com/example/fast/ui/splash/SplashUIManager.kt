package com.example.fast.ui.splash

import android.view.View
import android.widget.TextView

/**
 * Manages UI state for Splash screen transition.
 * Elements: logo and tagline (passed in; activity resolves from layout).
 * Single source of truth for preparing these views before navigation.
 */
class SplashUIManager(
    private val logoView: TextView,
    private val taglineView: TextView
) {

    /**
     * Set transition names, visibility, alpha, scale, and translation so logo and tagline
     * are in a single defined state for shared element transition.
     * Call before starting the transition in both navigation paths.
     * Activity must cancel any running logo/tagline animators before calling.
     */
    fun prepareForTransition() {
        logoView.transitionName = "logo_transition"
        taglineView.transitionName = "tagline_transition"
        logoView.visibility = View.VISIBLE
        logoView.alpha = 1f
        logoView.scaleX = 1f
        logoView.scaleY = 1f
        logoView.translationY = 0f
        taglineView.visibility = View.VISIBLE
        taglineView.alpha = 1f
        taglineView.scaleX = 1f
        taglineView.scaleY = 1f
        taglineView.translationY = 0f
    }
}
