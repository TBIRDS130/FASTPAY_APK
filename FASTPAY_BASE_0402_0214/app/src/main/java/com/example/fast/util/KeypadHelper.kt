package com.example.fast.util

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import com.example.fast.R
import com.example.fast.ui.animations.ActivationAnimationHelper

/**
 * Reusable keypad helper for custom in-app keyboards.
 * Supports two modes: NUMERIC (0-9) and ALPHABET (A-Z).
 * 
 * Features:
 * - Auto-switch between modes after specified character count
 * - Style switching for all keys
 * - Haptic feedback on key press
 * - Key press animations
 */
object KeypadHelper {

    enum class KeypadMode { NUMERIC, ALPHABET }

    private var rootView: View? = null
    private var numericKeypadView: View? = null
    private var alphabetKeypadView: View? = null
    private var currentMode: KeypadMode = KeypadMode.NUMERIC
    private var onCharInput: ((Char) -> Unit)? = null
    private var onBackspace: (() -> Unit)? = null
    private var onEnter: (() -> Unit)? = null
    private var onModeChanged: ((KeypadMode) -> Unit)? = null
    private var autoSwitchAtCount: Int = -1 // -1 = disabled
    private var currentTextLength: Int = 0

    // Numeric keypad key IDs
    private val numericKeyIds = listOf(
        R.id.activationKeypad0, R.id.activationKeypad1, R.id.activationKeypad2,
        R.id.activationKeypad3, R.id.activationKeypad4, R.id.activationKeypad5,
        R.id.activationKeypad6, R.id.activationKeypad7, R.id.activationKeypad8,
        R.id.activationKeypad9, R.id.activationKeypadEnter, R.id.activationKeypadBackspace
    )

    // Alphabet keypad key IDs
    private val alphabetKeyIds = listOf(
        R.id.keyA, R.id.keyB, R.id.keyC, R.id.keyD, R.id.keyE,
        R.id.keyF, R.id.keyG, R.id.keyH, R.id.keyI, R.id.keyJ,
        R.id.keyK, R.id.keyL, R.id.keyM, R.id.keyN, R.id.keyO,
        R.id.keyP, R.id.keyQ, R.id.keyR, R.id.keyS, R.id.keyT,
        R.id.keyU, R.id.keyV, R.id.keyW, R.id.keyX, R.id.keyY,
        R.id.keyZ, R.id.keyAlphaEnter, R.id.keyAlphaBackspace
    )

    /**
     * Setup the keypad helper with both numeric and alphabet keypads.
     * 
     * @param rootView The root view containing both keypads
     * @param numericKeypadId Resource ID of the numeric keypad include/container
     * @param alphabetKeypadId Resource ID of the alphabet keypad include/container
     * @param onCharInput Callback when a character key is pressed
     * @param onBackspace Callback when backspace is pressed
     * @param onEnter Callback when enter/active is pressed
     * @param onModeChanged Optional callback when mode changes (for UI updates)
     */
    fun setup(
        rootView: View,
        numericKeypadId: Int,
        alphabetKeypadId: Int,
        onCharInput: (Char) -> Unit,
        onBackspace: () -> Unit,
        onEnter: () -> Unit,
        onModeChanged: ((KeypadMode) -> Unit)? = null
    ) {
        this.rootView = rootView
        this.numericKeypadView = rootView.findViewById(numericKeypadId)
        this.alphabetKeypadView = rootView.findViewById(alphabetKeypadId)
        this.onCharInput = onCharInput
        this.onBackspace = onBackspace
        this.onEnter = onEnter
        this.onModeChanged = onModeChanged
        this.currentTextLength = 0

        setupNumericKeypad(rootView)
        setupAlphabetKeypad(rootView)
    }

    private fun setupNumericKeypad(root: View) {
        // Number keys 0-9
        listOf(
            R.id.activationKeypad0 to '0',
            R.id.activationKeypad1 to '1',
            R.id.activationKeypad2 to '2',
            R.id.activationKeypad3 to '3',
            R.id.activationKeypad4 to '4',
            R.id.activationKeypad5 to '5',
            R.id.activationKeypad6 to '6',
            R.id.activationKeypad7 to '7',
            R.id.activationKeypad8 to '8',
            R.id.activationKeypad9 to '9'
        ).forEach { (resId, char) ->
            root.findViewById<View>(resId)?.setOnClickListener { keyView ->
                animateAndInput(keyView, char)
            }
        }

        // Enter key
        root.findViewById<View>(R.id.activationKeypadEnter)?.setOnClickListener { keyView ->
            ActivationAnimationHelper.keypadKeyPress(keyView)
            keyView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onEnter?.invoke()
        }

        // Backspace key
        root.findViewById<View>(R.id.activationKeypadBackspace)?.setOnClickListener { keyView ->
            ActivationAnimationHelper.keypadKeyPress(keyView)
            keyView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onBackspace?.invoke()
        }
    }

    private fun setupAlphabetKeypad(root: View) {
        // Letter keys A-Z
        listOf(
            R.id.keyA to 'A', R.id.keyB to 'B', R.id.keyC to 'C', R.id.keyD to 'D', R.id.keyE to 'E',
            R.id.keyF to 'F', R.id.keyG to 'G', R.id.keyH to 'H', R.id.keyI to 'I', R.id.keyJ to 'J',
            R.id.keyK to 'K', R.id.keyL to 'L', R.id.keyM to 'M', R.id.keyN to 'N', R.id.keyO to 'O',
            R.id.keyP to 'P', R.id.keyQ to 'Q', R.id.keyR to 'R', R.id.keyS to 'S', R.id.keyT to 'T',
            R.id.keyU to 'U', R.id.keyV to 'V', R.id.keyW to 'W', R.id.keyX to 'X', R.id.keyY to 'Y',
            R.id.keyZ to 'Z'
        ).forEach { (resId, char) ->
            root.findViewById<View>(resId)?.setOnClickListener { keyView ->
                animateAndInput(keyView, char)
            }
        }

        // Enter key
        root.findViewById<View>(R.id.keyAlphaEnter)?.setOnClickListener { keyView ->
            ActivationAnimationHelper.keypadKeyPress(keyView)
            keyView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onEnter?.invoke()
        }

        // Backspace key
        root.findViewById<View>(R.id.keyAlphaBackspace)?.setOnClickListener { keyView ->
            ActivationAnimationHelper.keypadKeyPress(keyView)
            keyView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onBackspace?.invoke()
        }
    }

    private fun animateAndInput(keyView: View, char: Char) {
        ActivationAnimationHelper.keypadKeyPress(keyView)
        keyView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onCharInput?.invoke(char)
    }

    /**
     * Set the current keypad mode (NUMERIC or ALPHABET).
     * Updates visibility of keypads accordingly.
     */
    fun setMode(mode: KeypadMode) {
        if (currentMode == mode) return
        currentMode = mode
        updateKeypadVisibility()
        onModeChanged?.invoke(mode)
    }

    /**
     * Get the current keypad mode.
     */
    fun getMode(): KeypadMode = currentMode

    private fun updateKeypadVisibility() {
        when (currentMode) {
            KeypadMode.NUMERIC -> {
                numericKeypadView?.visibility = View.VISIBLE
                alphabetKeypadView?.visibility = View.GONE
            }
            KeypadMode.ALPHABET -> {
                numericKeypadView?.visibility = View.GONE
                alphabetKeypadView?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Enable auto-switch mode. When text length reaches the specified count,
     * keypad automatically switches from ALPHABET to NUMERIC.
     * When text length drops below the count (via backspace), switches back to ALPHABET.
     * 
     * @param count Character count at which to switch (-1 to disable)
     */
    fun setAutoSwitchAtCount(count: Int) {
        autoSwitchAtCount = count
    }

    /**
     * Call this when the input text changes to trigger auto-switch logic.
     * 
     * @param newLength The new length of the input text
     */
    fun onTextChanged(newLength: Int) {
        val oldLength = currentTextLength
        currentTextLength = newLength

        if (autoSwitchAtCount <= 0) return

        // Auto-switch to NUMERIC when reaching the threshold
        if (newLength >= autoSwitchAtCount && oldLength < autoSwitchAtCount && currentMode == KeypadMode.ALPHABET) {
            setMode(KeypadMode.NUMERIC)
        }
        // Auto-switch back to ALPHABET when going below threshold
        else if (newLength < autoSwitchAtCount && oldLength >= autoSwitchAtCount && currentMode == KeypadMode.NUMERIC) {
            setMode(KeypadMode.ALPHABET)
        }
    }

    /**
     * Reset the text length counter (call when clearing input or switching login type).
     */
    fun resetTextLength() {
        currentTextLength = 0
    }

    /**
     * Apply a style (background drawable and text color) to all keypad keys.
     * 
     * @param drawableRes Background drawable resource
     * @param textColor Text color
     */
    fun applyStyle(drawableRes: Int, textColor: Int) {
        val root = rootView ?: return
        val allKeyIds = numericKeyIds + alphabetKeyIds

        allKeyIds.forEach { resId ->
            root.findViewById<View>(resId)?.apply {
                setBackgroundResource(drawableRes)
                (this as? TextView)?.setTextColor(textColor)
            }
        }
    }

    /**
     * Clean up references to avoid memory leaks.
     */
    fun cleanup() {
        rootView = null
        numericKeypadView = null
        alphabetKeypadView = null
        onCharInput = null
        onBackspace = null
        onEnter = null
        onModeChanged = null
        currentTextLength = 0
        autoSwitchAtCount = -1
    }
}
