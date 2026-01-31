package com.example.fast.ui.animations

/**
 * Animation duration constants for consistent timing across the app
 * All durations are in milliseconds
 */
object AnimationConstants {
    
    // ========== Splash Screen Animations ==========
    
    /** Glow background animation duration */
    const val SPLASH_GLOW_DURATION = 600L
    
    /** Delay before starting letter animations */
    const val SPLASH_LETTER_ANIMATION_DELAY = 200L
    
    /** Stagger delay between each letter animation */
    const val SPLASH_LETTER_STAGGER_DELAY = 100L
    
    /** Duration for each letter to animate in */
    const val SPLASH_LETTER_ANIMATION_DURATION = 500L
    
    /** Total splash screen display duration before navigation */
    const val SPLASH_TOTAL_DURATION = 6000L
    
    /** Fade out duration before navigation */
    const val SPLASH_FADE_OUT_DURATION = 300L
    
    // ========== Activation Screen Animations ==========
    
    /** Button exit animation duration (moves down and fades) */
    const val ACTIVATION_BUTTON_EXIT_DURATION = 350L
    
    /** Input field fade out duration */
    const val ACTIVATION_FADE_OUT_DURATION = 300L
    
    /** Background overlay fade in duration */
    const val ACTIVATION_BACKGROUND_FADE_DURATION = 400L
    
    /** Delay before showing activated state elements */
    const val ACTIVATION_STATE_DELAY = 300L
    
    /** Phone display card animation duration */
    const val ACTIVATION_PHONE_DISPLAY_DURATION = 400L
    
    /** Delay before showing bank tag card */
    const val ACTIVATION_BANK_TAG_DELAY = 200L
    
    /** Bank tag card animation duration */
    const val ACTIVATION_BANK_TAG_DURATION = 400L
    
    /** Delay before showing instruction card */
    const val ACTIVATION_INSTRUCTION_DELAY = 150L
    
    /** Instruction card animation duration */
    const val ACTIVATION_INSTRUCTION_DURATION = 500L
    
    // ========== Text Scroll Animations ==========
    
    /** Duration for text to scroll from start to end */
    const val TEXT_SCROLL_DURATION = 2500L
    
    /** Pause duration at scroll ends */
    const val TEXT_SCROLL_PAUSE_DURATION = 1000L
    
    /** Padding added to text scroll calculation */
    const val TEXT_SCROLL_PADDING = 40f
    
    // ========== Button Press Animations ==========
    
    /** Button scale down duration on press */
    const val BUTTON_PRESS_SCALE_DOWN_DURATION = 100L
    
    /** Button scale up duration after press */
    const val BUTTON_PRESS_SCALE_UP_DURATION = 150L
    
    // ========== Empty State Animations ==========
    
    /** Empty state icon fade in duration */
    const val EMPTY_STATE_ICON_FADE_IN_DURATION = 800L
    
    /** Empty state icon pulse animation duration */
    const val EMPTY_STATE_ICON_PULSE_DURATION = 1500L
}

