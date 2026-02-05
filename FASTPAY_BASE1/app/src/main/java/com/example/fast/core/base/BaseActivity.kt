package com.example.fast.core.base

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import com.example.fast.util.Logger

/**
 * Base Activity class for all activities in the FastPay app.
 * Provides common functionality:
 * - Edge-to-edge display setup
 * - Window insets handling
 * - Lifecycle logging
 * - Common UI patterns
 */
abstract class BaseActivity : AppCompatActivity() {

    protected open val TAG: String
        get() = this::class.java.simpleName

    /**
     * Override to provide the root view for window insets handling.
     * Return null to skip automatic insets handling.
     */
    protected open fun getInsetsRootView(): View? = null

    /**
     * Override to enable/disable edge-to-edge display.
     * Default is true for modern Android look.
     */
    protected open val enableEdgeToEdge: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d(TAG, "onCreate")
    }

    /**
     * Call this after setContentView to setup common configurations.
     * Should be called in subclass onCreate after setContentView.
     */
    protected fun setupBase() {
        setupWindowInsets()
    }

    /**
     * Setup window insets handling for edge-to-edge display.
     */
    private fun setupWindowInsets() {
        getInsetsRootView()?.let { rootView ->
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Logger.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Logger.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Logger.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Logger.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "onDestroy")
    }

    /**
     * Show a simple toast message.
     */
    protected fun showToast(message: String, duration: Int = android.widget.Toast.LENGTH_SHORT) {
        android.widget.Toast.makeText(this, message, duration).show()
    }

    /**
     * Check if the activity is still valid (not finishing/destroyed).
     */
    protected val isActivityValid: Boolean
        get() = !isFinishing && !isDestroyed
}
