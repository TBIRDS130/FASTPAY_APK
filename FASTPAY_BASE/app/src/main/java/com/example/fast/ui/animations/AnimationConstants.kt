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

    // ========== Activation Flow (from activate-animation-demo.html) ==========

    /** Ripple effect duration on ACTIVATE click */
    const val ACTIVATION_RIPPLE_DURATION_MS = 1200L

    /** Keyboard-to-status card transition duration */
    const val ACTIVATION_KEYBOARD_TO_STATUS_MS = 800L

    /** Status-to-keyboard card transition duration (retry flow) */
    const val ACTIVATION_STATUS_TO_KEYBOARD_MS = 380L

    /** Status typing total duration for all 5 lines (single phase, 4 seconds) */
    const val ACTIVATION_STATUS_TYPING_TOTAL_MS = 4000L

    /** Delay after update card is dismissed before resuming status typing (so card hide is visible before next character). */
    const val ACTIVATION_STATUS_TYPING_RESUME_AFTER_CARD_MS = 280L

    /** AUTHORIZED result line typing duration */
    const val ACTIVATION_AUTHORIZED_TYPING_MS = 500L

    /** UNAUTHORIZED result line typing duration */
    const val ACTIVATION_UNAUTHORIZED_TYPING_MS = 800L

    /** Wait before status-to-keyboard on DENIED (retry) flow */
    const val ACTIVATION_DENIED_WAIT_BEFORE_RETRY_MS = 1500L

    /** Wipe-down entry animation duration */
    const val ACTIVATION_WIPE_DOWN_ENTRY_MS = 500L

    /** Stagger between wipe-down elements */
    const val ACTIVATION_WIPE_DOWN_STAGGER_MS = 100L

    /** Wipe line animation duration */
    const val ACTIVATION_WIPE_LINE_MS = 400L

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

    // ========== Card Flip Animations (Cross-Activity) ==========

    /** Standard card flip duration (0 to 90 or -90 to 0 degrees) */
    const val CARD_FLIP_DURATION_MS = 350L

    /** Content fade during flip (fade out old / fade in new) */
    const val CARD_CONTENT_FADE_MS = 150L

    /** Background dim overlay fade duration */
    const val DIM_OVERLAY_FADE_MS = 200L

    /** Glow pulse effect during flip edge-on moment */
    const val GLOW_PULSE_DURATION_MS = 150L

    /** Wipe up animation before flip */
    const val WIPE_UP_DURATION_MS = 350L

    /** Cards pulse animation (crypto hash + utility card) on ACTIVATE */
    const val CARDS_PULSE_DURATION_MS = 350L

    // ========== Keypad Animations ==========

    /** Keypad slide up + fade in duration */
    const val KEYPAD_SHOW_DURATION_MS = 220L

    /** Keypad key press scale down duration */
    const val KEYPAD_KEY_PRESS_DOWN_MS = 80L

    /** Keypad key press scale up duration */
    const val KEYPAD_KEY_PRESS_UP_MS = 120L

    // ========== Empty State Animations ==========

    /** Empty state icon fade in duration */
    const val EMPTY_STATE_ICON_FADE_IN_DURATION = 800L

    /** Empty state icon pulse animation duration */
    const val EMPTY_STATE_ICON_PULSE_DURATION = 1500L

    // ========== Update/Permission Card Animations ==========
    // Used by both ActivationActivity (prompt card) and ActivatedActivity (master card)

    /** Card shrink/grow animation duration */
    const val UPDATE_CARD_ANIM_DURATION_MS = 350L

    /** Background cards opacity fade duration */
    const val UPDATE_CARD_OPACITY_FADE_MS = 600L

    /** Card flip animation duration */
    const val UPDATE_CARD_FLIP_DURATION_MS = 600L

    /** Per-character typing delay for permission list */
    const val UPDATE_CARD_PER_CHAR_DELAY_MS = 45L

    /** Delay between phases (after showing "up to date" etc.) */
    const val UPDATE_CARD_PHASE_DELAY_MS = 1500L

    /** Version check timeout before proceeding without update */
    const val UPDATE_CARD_VERSION_CHECK_TIMEOUT_MS = 15000L

    // ========== Permission Gate Card Flip Animation ==========
    // 3D flip animation for permission card birth/death from status card

    /** Duration for receding background views (scale down, fade) */
    const val PERM_CARD_RECEDE_DURATION_MS = 350L

    /** Duration for recede fade (alpha/scale) so background stays faded during card flip-in */
    const val PERM_CARD_RECEDE_FADE_MS = 600L

    /** WebView content fade-in duration after card flip-in (instruction/request card) */
    const val CARD_WEBVIEW_CONTENT_FADE_MS = 150L

    /** Scale factor for receded views */
    const val PERM_CARD_RECEDE_SCALE = 0.85f

    /** Alpha for receded views */
    const val PERM_CARD_RECEDE_ALPHA = 0.4f

    /** Alpha for receded views when RenderEffect blur is not available (API < 31). Lower so background is clearly faded. */
    const val PERM_CARD_RECEDE_ALPHA_NO_BLUR = 0.2f

    /** Blur radius in pixels for receded views (API 31+). */
    const val PERM_CARD_BLUR_RADIUS = 25f

    /** Duration for card flip-in animation (rotationX 90 to 0) */
    const val PERM_CARD_FLIP_IN_DURATION_MS = 500L

    /** Duration for card flip-out animation (rotationX 0 to 90) */
    const val PERM_CARD_FLIP_OUT_DURATION_MS = 300L

    /** Duration for restoring receded views after card dismisses */
    const val PERM_CARD_RESTORE_DURATION_MS = 350L

    /** Camera distance multiplier for 3D perspective */
    const val PERM_CARD_CAMERA_DISTANCE_MULTIPLIER = 8000f

    /** Delay before starting flip animation (after overlay fades in) */
    const val PERM_CARD_FLIP_START_DELAY_MS = 150L
}
