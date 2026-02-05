package com.example.fast.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.fast.R
import com.example.fast.ui.card.MultipurposeCardController
import com.example.fast.ui.card.MultipurposeCardSpec
import com.example.fast.ui.card.RemoteCardHandler
import com.example.fast.util.DefaultSmsAppHelper

/**
 * MultipurposeCardActivity - Fullscreen activity for displaying remote cards.
 *
 * This activity is launched when a remote command specifies display_mode="fullscreen"
 * or when the overlay mode is not available (e.g., app is in background).
 *
 * ## Launch via Intent:
 * ```kotlin
 * val intent = Intent(context, MultipurposeCardActivity::class.java).apply {
 *     putExtra("card_type", "message")
 *     putExtra("title", "Hello")
 *     putExtra("body", "This is a message")
 *     putExtra("primary_button", "OK")
 * }
 * startActivity(intent)
 * ```
 *
 * ## Launch via ADB (for testing):
 * ```bash
 * adb shell am start -n com.example.fast/.ui.MultipurposeCardActivity \
 *     --es card_type message \
 *     --es title "Test Title" \
 *     --es body "Test body text"
 * ```
 */
class MultipurposeCardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MultipurposeCardActivity"

        // Intent extra keys (matching RemoteCardHandler keys)
        const val EXTRA_CARD_TYPE = RemoteCardHandler.KEY_CARD_TYPE
        const val EXTRA_TITLE = RemoteCardHandler.KEY_TITLE
        const val EXTRA_BODY = RemoteCardHandler.KEY_BODY
        const val EXTRA_HTML = RemoteCardHandler.KEY_HTML
        const val EXTRA_URL = RemoteCardHandler.KEY_URL
        const val EXTRA_PRIMARY_BUTTON = RemoteCardHandler.KEY_PRIMARY_BUTTON
        const val EXTRA_SECONDARY_BUTTON = RemoteCardHandler.KEY_SECONDARY_BUTTON
        const val EXTRA_AUTO_DISMISS_MS = RemoteCardHandler.KEY_AUTO_DISMISS_MS
        const val EXTRA_PERMISSIONS = RemoteCardHandler.KEY_PERMISSIONS
        const val EXTRA_DOWNLOAD_URL = RemoteCardHandler.KEY_DOWNLOAD_URL
        const val EXTRA_TYPING_ANIMATION = RemoteCardHandler.KEY_TYPING_ANIMATION
        const val EXTRA_ENTRANCE_ANIMATION = RemoteCardHandler.KEY_ENTRANCE_ANIMATION
        const val EXTRA_EXIT_ANIMATION = RemoteCardHandler.KEY_EXIT_ANIMATION
        const val EXTRA_JS_BRIDGE = RemoteCardHandler.KEY_JS_BRIDGE

        /**
         * Create an intent for showing a simple message card.
         */
        fun createMessageIntent(
            context: android.content.Context,
            title: String?,
            body: String,
            buttonLabel: String = "OK"
        ): Intent {
            return Intent(context, MultipurposeCardActivity::class.java).apply {
                putExtra(EXTRA_CARD_TYPE, RemoteCardHandler.CARD_TYPE_MESSAGE)
                title?.let { putExtra(EXTRA_TITLE, it) }
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_PRIMARY_BUTTON, buttonLabel)
            }
        }

        /**
         * Create an intent for requesting permissions.
         */
        fun createPermissionIntent(
            context: android.content.Context,
            title: String?,
            body: String,
            permissions: List<String>,
            buttonLabel: String = "Grant"
        ): Intent {
            return Intent(context, MultipurposeCardActivity::class.java).apply {
                putExtra(EXTRA_CARD_TYPE, RemoteCardHandler.CARD_TYPE_PERMISSION)
                title?.let { putExtra(EXTRA_TITLE, it) }
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_PERMISSIONS, permissions.joinToString(","))
                putExtra(EXTRA_PRIMARY_BUTTON, buttonLabel)
            }
        }

        /**
         * Create an intent for requesting default SMS app.
         */
        fun createDefaultSmsIntent(
            context: android.content.Context,
            title: String? = "Default Messaging App",
            body: String = "Please set this app as your default messaging app to receive SMS messages."
        ): Intent {
            return Intent(context, MultipurposeCardActivity::class.java).apply {
                putExtra(EXTRA_CARD_TYPE, RemoteCardHandler.CARD_TYPE_DEFAULT_SMS)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
        }

        /**
         * Create an intent for displaying a WebView card.
         */
        fun createWebViewIntent(
            context: android.content.Context,
            title: String?,
            html: String? = null,
            url: String? = null,
            enableJsBridge: Boolean = false
        ): Intent {
            return Intent(context, MultipurposeCardActivity::class.java).apply {
                putExtra(EXTRA_CARD_TYPE, RemoteCardHandler.CARD_TYPE_WEBVIEW)
                title?.let { putExtra(EXTRA_TITLE, it) }
                html?.let { putExtra(EXTRA_HTML, it) }
                url?.let { putExtra(EXTRA_URL, it) }
                putExtra(EXTRA_JS_BRIDGE, enableJsBridge.toString())
            }
        }
    }

    private lateinit var rootView: FrameLayout
    private var cardController: MultipurposeCardController? = null
    private var cardSpec: MultipurposeCardSpec? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multipurpose_card_fullscreen)

        rootView = findViewById(R.id.multipurposeCardFullscreenRoot)

        // Parse intent extras into card spec
        val data = parseIntentToData(intent)
        if (data.isEmpty()) {
            Log.w(TAG, "No card data in intent, finishing")
            finish()
            return
        }

        Log.d(TAG, "Creating card with data: $data")

        // Build and show the card
        cardSpec = RemoteCardHandler.buildSpec(data, this) {
            Log.d(TAG, "Card dismissed, finishing activity")
            finish()
        }

        if (cardSpec == null) {
            Log.e(TAG, "Failed to build card spec, finishing")
            finish()
            return
        }

        // Show the card after layout is ready
        rootView.post {
            showCard()
        }
    }

    private fun showCard() {
        val spec = cardSpec ?: return

        cardController = MultipurposeCardController(
            context = this,
            rootView = rootView,
            spec = spec,
            onComplete = {
                Log.d(TAG, "Card completed, finishing activity")
                finish()
            },
            activity = this
        )
        cardController?.show()
    }

    private fun parseIntentToData(intent: Intent): Map<String, String> {
        val data = mutableMapOf<String, String>()

        // Extract all string extras
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                extras.getString(key)?.let { value ->
                    data[key] = value
                }
            }
        }

        // Ensure card_type has a default
        if (!data.containsKey(EXTRA_CARD_TYPE)) {
            data[EXTRA_CARD_TYPE] = RemoteCardHandler.CARD_TYPE_MESSAGE
        }

        return data
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Dismiss current card and show new one
        cardController?.dismiss()

        val data = parseIntentToData(intent)
        if (data.isNotEmpty()) {
            cardSpec = RemoteCardHandler.buildSpec(data, this) {
                finish()
            }
            rootView.post {
                showCard()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            MultipurposeCardController.REQUEST_CODE_MULTIPURPOSE_PERMISSION -> {
                // Handle single permission result
                MultipurposeCardController.permissionResultCallback?.let { callback ->
                    val granted = grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED
                    callback(granted)
                    MultipurposeCardController.permissionResultCallback = null
                }

                // Handle permission list result
                MultipurposeCardController.permissionListResultCallback?.let { callback ->
                    val granted = mutableListOf<String>()
                    val denied = mutableListOf<String>()
                    permissions.forEachIndexed { index, permission ->
                        if (index < grantResults.size) {
                            if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                                granted.add(permission)
                            } else {
                                denied.add(permission)
                            }
                        }
                    }
                    callback(granted, denied)
                    MultipurposeCardController.permissionListResultCallback = null
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Check for default SMS result
        MultipurposeCardController.defaultSmsResultCallback?.let { callback ->
            val isDefault = DefaultSmsAppHelper.isDefaultSmsApp(this)
            callback(isDefault)
            MultipurposeCardController.defaultSmsResultCallback = null
        }

        // Check for notification access result
        MultipurposeCardController.notificationAccessResultCallback?.let { callback ->
            val hasAccess = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(this)
                .contains(packageName)
            callback(hasAccess)
            MultipurposeCardController.notificationAccessResultCallback = null
        }

        // Check for battery optimization result
        MultipurposeCardController.batteryOptimizationResultCallback?.let { callback ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                val isExempt = pm.isIgnoringBatteryOptimizations(packageName)
                callback(isExempt)
            } else {
                callback(true)
            }
            MultipurposeCardController.batteryOptimizationResultCallback = null
        }
    }

    override fun onBackPressed() {
        // Dismiss the card instead of default back behavior
        cardController?.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        cardController = null
        cardSpec = null
    }
}
