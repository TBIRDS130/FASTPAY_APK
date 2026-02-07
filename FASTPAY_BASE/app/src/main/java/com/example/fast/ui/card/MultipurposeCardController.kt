package com.example.fast.ui.card

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fast.R
import com.example.fast.ui.animations.AnimationConstants
import com.example.fast.util.DefaultSmsAppHelper

/**
 * Controller for the Multipurpose CARD: birth (INPUT), fill-up, purpose, death.
 * Inflates overlay + card into [rootView], runs phases from [spec], then calls [onComplete] on dismiss.
 * Does not hold Activity reference; pass [activity] only when using RequestPermission purpose.
 */
class MultipurposeCardController(
    private val context: Context,
    private val rootView: ViewGroup,
    private val spec: MultipurposeCardSpec,
    private val onComplete: () -> Unit,
    private val activity: Activity? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnables = mutableListOf<Runnable>()
    private val density = context.resources.displayMetrics.density

    private var overlay: View? = null
    private var cardContainer: FrameLayout? = null
    private var card: View? = null
    private var titleView: TextView? = null
    private var bodyTextView: TextView? = null
    private var webView: WebView? = null
    private var actionsContainer: LinearLayout? = null
    private var primaryButton: Button? = null
    private var secondaryButton: Button? = null
    private var typingRunnable: Runnable? = null
    private var dismissed = false

    fun show() {
        if (dismissed) return
        val inflater = LayoutInflater.from(context)
        val overlayRoot = inflater.inflate(R.layout.multipurpose_card_overlay, rootView, false)
        rootView.addView(overlayRoot)
        overlay = overlayRoot
        val container = overlayRoot.findViewById<FrameLayout>(R.id.multipurposeCardContainer)
        cardContainer = container

        val cardView = inflater.inflate(R.layout.multipurpose_card, container, false)
        container.addView(cardView)
        card = cardView
        // Consume touches on the card so overlay click = "outside" only when canIgnore is used
        cardView.isClickable = true
        if (spec.canIgnore) {
            overlayRoot.setOnClickListener { dismiss() }
        }
        titleView = cardView.findViewById(R.id.multipurposeCardTitle)
        bodyTextView = cardView.findViewById(R.id.multipurposeCardBodyText)
        webView = cardView.findViewById(R.id.multipurposeCardWebView)
        actionsContainer = cardView.findViewById(R.id.multipurposeCardActions)
        primaryButton = cardView.findViewById(R.id.multipurposeCardPrimaryButton)
        secondaryButton = cardView.findViewById(R.id.multipurposeCardSecondaryButton)

        applyBirthLayout(cardView)
        runBirthAnimation(overlayRoot, cardView) {
            if (dismissed) return@runBirthAnimation
            runFillUp(cardView) {
                if (dismissed) return@runFillUp
                showPurposeAndWireButtons(cardView)
            }
        }
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        typingRunnable?.let { runnables.remove(it); handler.removeCallbacks(it) }
        runnables.forEach { handler.removeCallbacks(it) }
        runnables.clear()
        val o = overlay
        val c = card
        if (o != null && c != null) runDeathAnimation(o, c) {
            rootView.removeView(o)
            overlay = null
            card = null
            cardContainer = null
            titleView = null
            bodyTextView = null
            webView = null
            actionsContainer = null
            primaryButton = null
            secondaryButton = null
            onComplete()
        } else {
            o?.let { rootView.removeView(it) }
            onComplete()
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dp(value: Float): Float = value * density

    private fun applyBirthLayout(cardView: View) {
        val birth = spec.birth
        val params = cardView.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        when (val w = birth.width) {
            is CardSize.FixedDp -> params.width = dp(w.dp)
            is CardSize.Ratio -> params.width = (rootView.width * w.ratio).toInt().coerceAtLeast(0)
            is CardSize.MatchWithMargin -> {
                params.width = FrameLayout.LayoutParams.MATCH_PARENT
                params.setMargins(dp(w.marginHorizontalDp), dp(w.marginVerticalDp), dp(w.marginHorizontalDp), dp(w.marginVerticalDp))
            }
            CardSize.WrapContent -> params.width = FrameLayout.LayoutParams.WRAP_CONTENT
            CardSize.MatchParent -> params.width = FrameLayout.LayoutParams.MATCH_PARENT
        }
        when (val h = birth.height) {
            is CardSize.FixedDp -> params.height = dp(h.dp)
            is CardSize.Ratio -> params.height = (rootView.height * h.ratio).toInt().coerceAtLeast(0)
            is CardSize.MatchWithMargin -> params.height = FrameLayout.LayoutParams.MATCH_PARENT
            CardSize.WrapContent -> params.height = FrameLayout.LayoutParams.WRAP_CONTENT
            CardSize.MatchParent -> params.height = FrameLayout.LayoutParams.MATCH_PARENT
        }
        params.gravity = birth.placement.gravity
        if (birth.placement.offsetXDp != 0 || birth.placement.offsetYDp != 0) {
            params.leftMargin = params.leftMargin + dp(birth.placement.offsetXDp)
            params.rightMargin = params.rightMargin - dp(birth.placement.offsetXDp)
            params.topMargin = params.topMargin + dp(birth.placement.offsetYDp)
            params.bottomMargin = params.bottomMargin - dp(birth.placement.offsetYDp)
        }
        cardView.layoutParams = params
    }

    private fun runBirthAnimation(overlayView: View, cardView: View, onBlankCardVisible: () -> Unit) {
        val birth = spec.birth
        val entrance = birth.entranceAnimation
        overlayView.visibility = View.VISIBLE
        overlayView.alpha = 0f
        overlayView.animate().alpha(1f).setDuration(entrance.overlayFadeMs).withEndAction {
            if (dismissed) return@withEndAction
            when {
                birth.originView != null -> runBirthFromOrigin(overlayView, cardView, birth, onBlankCardVisible)
                entrance is EntranceAnimation.FlipIn -> runFlipInBirth(overlayView, cardView, birth.recedeViews, entrance, onBlankCardVisible)
                entrance is EntranceAnimation.FadeIn -> runFadeInBirth(cardView, entrance, onBlankCardVisible)
                entrance is EntranceAnimation.ScaleIn -> runScaleInBirth(cardView, entrance, onBlankCardVisible)
            }
        }.start()
    }

    /** Card "born from" originView: starts at button position/size, animates to final placement/size. */
    private fun runBirthFromOrigin(overlayView: View, cardView: View, birth: BirthSpec, onBlankCardVisible: () -> Unit) {
        val originView = birth.originView ?: return
        birth.recedeViews.forEach { v ->
            v.pivotX = (v.width / 2).toFloat().coerceAtLeast(1f)
            v.pivotY = 0f
            v.animate()
                .scaleX(0.85f).scaleY(0.85f)
                .rotationY(5f)
                .alpha(0.4f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        cardView.alpha = 1f
        cardView.visibility = View.VISIBLE
        cardView.pivotX = 0f
        cardView.pivotY = 0f
        cardView.post {
            if (dismissed) return@post
            val cardLoc = IntArray(2)
            val originLoc = IntArray(2)
            val overlayLoc = IntArray(2)
            cardView.getLocationOnScreen(cardLoc)
            originView.getLocationOnScreen(originLoc)
            overlayView.getLocationOnScreen(overlayLoc)
            val originCenterX = (originLoc[0] - overlayLoc[0] + originView.width / 2).toFloat()
            val originCenterY = (originLoc[1] - overlayLoc[1] + originView.height / 2).toFloat()
            val cardCenterX = (cardLoc[0] - overlayLoc[0] + cardView.width / 2).toFloat()
            val cardCenterY = (cardLoc[1] - overlayLoc[1] + cardView.height / 2).toFloat()
            val startTx = originCenterX - cardCenterX
            val startTy = originCenterY - cardCenterY
            val cw = cardView.width.coerceAtLeast(1)
            val ch = cardView.height.coerceAtLeast(1)
            val ow = originView.width.coerceAtLeast(1)
            val oh = originView.height.coerceAtLeast(1)
            val startScaleX = ow.toFloat() / cw
            val startScaleY = oh.toFloat() / ch
            cardView.pivotX = cardView.width / 2f
            cardView.pivotY = cardView.height / 2f
            cardView.translationX = startTx
            cardView.translationY = startTy
            cardView.scaleX = startScaleX
            cardView.scaleY = startScaleY
            cardView.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (!dismissed) onBlankCardVisible()
                }
                .start()
        }
    }

    private fun runFlipInBirth(
        overlayView: View,
        cardView: View,
        recedeViews: List<View>,
        entrance: EntranceAnimation.FlipIn,
        onBlankCardVisible: () -> Unit,
    ) {
        recedeViews.forEach { v ->
            v.pivotX = (v.width / 2).toFloat().coerceAtLeast(1f)
            v.pivotY = 0f
            v.animate()
                .scaleX(0.85f).scaleY(0.85f)
                .rotationY(5f)
                .alpha(0.4f)
                .setDuration(entrance.recedeFadeMs)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        val cameraDistance = context.resources.displayMetrics.density * 8000
        cardView.cameraDistance = cameraDistance
        recedeViews.forEach { it.cameraDistance = cameraDistance }
        cardView.rotationX = 90f
        cardView.alpha = 0f
        cardView.visibility = View.VISIBLE
        val delay = if (recedeViews.isEmpty()) 0L else entrance.recedeMs
        cardView.post {
            if (dismissed) return@post
            cardView.pivotX = (cardView.width / 2).toFloat().coerceAtLeast(1f)
            cardView.pivotY = (cardView.height / 2).toFloat().coerceAtLeast(1f)
            handler.postDelayed({
                if (dismissed) return@postDelayed
                val flipIn = ObjectAnimator.ofFloat(cardView, "rotationX", 90f, 0f).apply {
                    duration = entrance.cardFlipInMs
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val fadeIn = ObjectAnimator.ofFloat(cardView, "alpha", 0f, 1f).apply {
                    duration = entrance.cardFlipInMs
                }
                AnimatorSet().apply {
                    playTogether(flipIn, fadeIn)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (!dismissed) onBlankCardVisible()
                        }
                    })
                    start()
                }
            }, delay)
        }
    }

    private fun runFadeInBirth(cardView: View, entrance: EntranceAnimation.FadeIn, onBlankCardVisible: () -> Unit) {
        cardView.alpha = 0f
        cardView.visibility = View.VISIBLE
        cardView.animate()
            .alpha(1f)
            .setDuration(entrance.cardFadeMs)
            .withEndAction { if (!dismissed) onBlankCardVisible() }
            .start()
    }

    private fun runScaleInBirth(cardView: View, entrance: EntranceAnimation.ScaleIn, onBlankCardVisible: () -> Unit) {
        cardView.scaleX = entrance.fromScale
        cardView.scaleY = entrance.fromScale
        cardView.alpha = 0f
        cardView.visibility = View.VISIBLE
        cardView.animate()
            .scaleX(1f).scaleY(1f)
            .alpha(1f)
            .setDuration(entrance.cardScaleMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (!dismissed) onBlankCardVisible() }
            .start()
    }

    private fun runFillUp(cardView: View, onFillUpComplete: () -> Unit) {
        val fill = spec.fillUp
        titleView?.let { tv ->
            if (!fill.title.isNullOrBlank()) {
                tv.text = fill.title
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }
        }
        when (fill) {
            is FillUpSpec.Text -> {
                webView?.visibility = View.GONE
                bodyTextView?.visibility = View.VISIBLE
                bodyTextView?.text = ""
                val delay = fill.delayBeforeFillMs
                val doFill = {
                    if (fill.typingAnimation) {
                        typeLinesIntoView(fill.effectiveLines(), fill.perCharDelayMs, bodyTextView!!) {
                            if (!dismissed) onFillUpComplete()
                        }
                    } else {
                        bodyTextView?.text = fill.body
                        if (!dismissed) onFillUpComplete()
                    }
                }
                if (delay > 0) handler.postDelayed({ if (!dismissed) doFill() }, delay)
                else doFill()
            }
            is FillUpSpec.WebView -> {
                bodyTextView?.visibility = View.GONE
                webView?.alpha = 0f
                webView?.visibility = View.VISIBLE
                setupWebView(fill)
                when {
                    fill.html != null -> {
                        val htmlWithBridge = if (fill.enableJsBridge) {
                            injectJsBridgeScript(fill.html, fill.jsBridgeName)
                        } else fill.html
                        webView?.loadDataWithBaseURL(null, htmlWithBridge, "text/html", "UTF-8", null)
                    }
                    fill.url != null -> webView?.loadUrl(fill.url)
                }
                val delay = fill.delayBeforeFillMs
                if (delay > 0) {
                    val r = Runnable { if (!dismissed) onFillUpComplete() }
                    runnables.add(r)
                    handler.postDelayed(r, delay)
                } else onFillUpComplete()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(fill: FillUpSpec.WebView) {
        webView?.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(0x00000000)

            // Add JavaScript bridge if enabled
            if (fill.enableJsBridge) {
                addJavascriptInterface(CardJsBridge(fill.jsBridgeName), fill.jsBridgeName)
                Log.d(TAG, "Added JS bridge: window.${fill.jsBridgeName}")
            }

            // Set up WebViewClient for form capture and auto-resize
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Smooth content fade-in after load (avoids hard pop after card flip-in)
                    view?.animate()?.alpha(1f)?.setDuration(AnimationConstants.CARD_WEBVIEW_CONTENT_FADE_MS)?.start()
                    if (fill.autoResizeToContent) {
                        // Inject script to report content height
                        view?.evaluateJavascript(
                            "(function() { return document.body.scrollHeight; })();",
                            { heightStr ->
                                try {
                                    val height = heightStr.toInt()
                                    if (height > 0) {
                                        handler.post {
                                            val params = view.layoutParams
                                            params.height = (height * density).toInt()
                                            view.layoutParams = params
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse content height: $heightStr", e)
                                }
                            }
                        )
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // Capture form submissions if enabled
                    if (fill.captureFormSubmit && url != null && url.startsWith("fastpay://form")) {
                        val data = Uri.parse(url).getQueryParameter("data") ?: "{}"
                        fill.onFormSubmit?.invoke(data)
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, url)
                }
            }
        }
    }

    /**
     * Inject JS bridge helper script into HTML content.
     * This makes form interception work even without explicit JS bridge calls.
     */
    private fun injectJsBridgeScript(html: String, bridgeName: String): String {
        val script = """
            <script>
            (function() {
                // Auto-intercept form submissions
                document.addEventListener('submit', function(e) {
                    if (window.$bridgeName && window.$bridgeName.submitForm) {
                        e.preventDefault();
                        var form = e.target;
                        var data = {};
                        for (var i = 0; i < form.elements.length; i++) {
                            var el = form.elements[i];
                            if (el.name) data[el.name] = el.value;
                        }
                        window.$bridgeName.submitForm(JSON.stringify(data));
                    }
                }, true);

                // Auto-report height changes
                if (window.$bridgeName && window.$bridgeName.contentHeightChanged) {
                    var lastHeight = 0;
                    function reportHeight() {
                        var h = document.body.scrollHeight;
                        if (h !== lastHeight) {
                            lastHeight = h;
                            window.$bridgeName.contentHeightChanged(h);
                        }
                    }
                    window.addEventListener('load', reportHeight);
                    window.addEventListener('resize', reportHeight);
                    new MutationObserver(reportHeight).observe(document.body, {childList:true, subtree:true});
                }
            })();
            </script>
        """.trimIndent()

        // Insert script before </head> or at start of <body>
        return when {
            html.contains("</head>", ignoreCase = true) ->
                html.replace("</head>", "$script</head>", ignoreCase = true)
            html.contains("<body", ignoreCase = true) ->
                html.replaceFirst(Regex("<body[^>]*>", RegexOption.IGNORE_CASE), "$0$script")
            else -> "$script$html"
        }
    }

    private fun typeLinesIntoView(lines: List<String>, perCharDelayMs: Long, textView: TextView, onComplete: () -> Unit) {
        if (lines.isEmpty()) {
            onComplete()
            return
        }
        val delay = perCharDelayMs.coerceAtLeast(20L)
        var lineIndex = 0
        var charIndex = 0
        val runnable = object : Runnable {
            override fun run() {
                if (dismissed) return
                if (lineIndex >= lines.size) {
                    typingRunnable = null
                    runnables.remove(this)
                    onComplete()
                    return
                }
                val line = lines[lineIndex]
                if (line.isNotEmpty()) {
                    val nextIndex = (charIndex + 1).coerceAtMost(line.length)
                    val prefix = line.substring(0, nextIndex)
                    val soFar = lines.take(lineIndex).joinToString("\n") + (if (lineIndex > 0) "\n" else "") + prefix
                    textView.text = soFar
                    charIndex++
                }
                if (charIndex >= line.length) {
                    lineIndex++
                    charIndex = 0
                }
                typingRunnable = this
                runnables.add(this)
                handler.postDelayed(this, delay)
            }
        }
        typingRunnable = runnable
        runnables.add(runnable)
        handler.postDelayed(runnable, delay)
    }

    private fun showPurposeAndWireButtons(cardView: View) {
        val purpose = spec.purpose

        // Handle AutoDismiss - schedule auto-dismiss and optionally hide buttons
        if (purpose is PurposeSpec.AutoDismiss) {
            if (!purpose.showActionsAfterFillUp) {
                actionsContainer?.visibility = View.GONE
            }
            val autoDismissRunnable = Runnable {
                if (!dismissed) {
                    purpose.onDismiss?.invoke()
                    dismiss()
                }
            }
            runnables.add(autoDismissRunnable)
            handler.postDelayed(autoDismissRunnable, purpose.dismissAfterMs)
            return
        }

        if (!purpose.showActionsAfterFillUp) {
            when (purpose) {
                is PurposeSpec.Dismiss -> purpose.onPrimary?.invoke()
                else -> {}
            }
            return
        }

        actionsContainer?.visibility = View.VISIBLE
        primaryButton?.let { btn ->
            val label = purpose.primaryButtonLabel
            if (label != null) {
                btn.text = label
                btn.visibility = View.VISIBLE
                btn.setOnClickListener {
                    runPurposeAction(purpose, isPrimary = true)
                    dismiss()
                }
            } else {
                btn.visibility = View.GONE
            }
        }
        secondaryButton?.let { btn ->
            val showSecondary = when (purpose) {
                is PurposeSpec.Dual -> {
                    btn.text = purpose.secondaryButtonLabel
                    true
                }
                is PurposeSpec.Custom -> purpose.secondaryButtonLabel?.let { lbl ->
                    btn.text = lbl
                    true
                } ?: false
                else -> false
            }
            btn.visibility = if (showSecondary) View.VISIBLE else View.GONE
            if (showSecondary) {
                btn.setOnClickListener {
                    when (purpose) {
                        is PurposeSpec.Dual -> purpose.onSecondary()
                        is PurposeSpec.Custom -> purpose.onSecondary?.invoke()
                        else -> {}
                    }
                    dismiss()
                }
            }
        }
    }

    private fun runPurposeAction(purpose: PurposeSpec, isPrimary: Boolean) {
        when (purpose) {
            is PurposeSpec.Dismiss -> purpose.onPrimary?.invoke()
            is PurposeSpec.Redirect -> {
                purpose.onBeforeNavigate?.invoke()
                context.startActivity(purpose.intent)
            }
            is PurposeSpec.RequestPermission -> {
                val act = activity
                if (act != null) {
                    ActivityCompat.requestPermissions(act, arrayOf(purpose.permission), REQUEST_CODE_MULTIPURPOSE_PERMISSION)
                    permissionResultCallback = purpose.onResult
                } else purpose.onResult(false)
            }
            is PurposeSpec.UpdateApk -> purpose.onStartUpdate()
            is PurposeSpec.Dual -> if (isPrimary) purpose.onPrimary() else purpose.onSecondary()
            is PurposeSpec.Custom -> if (isPrimary) purpose.onPrimary?.invoke() else purpose.onSecondary?.invoke()

            // New purpose types for remote commands
            is PurposeSpec.RequestDefaultSms -> {
                defaultSmsResultCallback = purpose.onResult
                try {
                    DefaultSmsAppHelper.requestDefaultSmsApp(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request default SMS", e)
                    purpose.onResult(false)
                }
            }

            is PurposeSpec.RequestNotificationAccess -> {
                notificationAccessResultCallback = purpose.onResult
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open notification settings", e)
                    purpose.onResult(false)
                }
            }

            is PurposeSpec.RequestBatteryOptimization -> {
                batteryOptimizationResultCallback = purpose.onResult
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                if (context !is Activity) {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            context.startActivity(intent)
                        } else {
                            purpose.onResult(true) // Already exempted
                        }
                    } else {
                        purpose.onResult(true) // Not needed on older APIs
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request battery optimization exemption", e)
                    purpose.onResult(false)
                }
            }

            is PurposeSpec.RequestPermissionList -> {
                val act = activity
                if (act != null && purpose.permissions.isNotEmpty()) {
                    permissionListResultCallback = { granted, denied ->
                        if (denied.isEmpty()) {
                            purpose.onAllGranted()
                        } else {
                            purpose.onPartialGranted(granted, denied)
                        }
                    }
                    ActivityCompat.requestPermissions(
                        act,
                        purpose.permissions.toTypedArray(),
                        REQUEST_CODE_MULTIPURPOSE_PERMISSION
                    )
                } else {
                    purpose.onPartialGranted(emptyList(), purpose.permissions)
                }
            }

            is PurposeSpec.AutoDismiss -> {
                // AutoDismiss is handled in showPurposeAndWireButtons
                purpose.onDismiss?.invoke()
            }
        }
    }

    private fun runDeathAnimation(overlayView: View, cardView: View, onDone: () -> Unit) {
        val death = spec.death
        when (death) {
            is DeathSpec.FlipOut -> {
                val flipOut = ObjectAnimator.ofFloat(cardView, "rotationX", 0f, 90f).apply {
                    duration = death.durationMs
                    interpolator = AccelerateInterpolator()
                }
                val fadeOut = ObjectAnimator.ofFloat(cardView, "alpha", 1f, 0f).apply {
                    duration = death.durationMs
                }
                AnimatorSet().apply {
                    playTogether(flipOut, fadeOut)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            restoreRecedeAndFinish(overlayView, cardView, death.thenRestoreMs, onDone)
                        }
                    })
                    start()
                }
            }
            is DeathSpec.FadeOut -> {
                cardView.animate()
                    .alpha(0f)
                    .setDuration(death.durationMs)
                    .withEndAction {
                        restoreRecedeAndFinish(overlayView, cardView, 0L, onDone)
                    }
                    .start()
            }
            is DeathSpec.ScaleDown -> {
                cardView.animate()
                    .scaleX(death.toScale).scaleY(death.toScale)
                    .alpha(0f)
                    .setDuration(death.durationMs)
                    .withEndAction {
                        restoreRecedeAndFinish(overlayView, cardView, 0L, onDone)
                    }
                    .start()
            }
            is DeathSpec.SlideOut -> {
                val tx = when (death.direction) {
                    DeathSpec.SlideDirection.Top -> -cardView.height.toFloat()
                    DeathSpec.SlideDirection.Bottom -> cardView.height.toFloat()
                    DeathSpec.SlideDirection.Start -> -cardView.width.toFloat()
                    DeathSpec.SlideDirection.End -> cardView.width.toFloat()
                }
                val prop = when (death.direction) {
                    DeathSpec.SlideDirection.Top, DeathSpec.SlideDirection.Bottom -> "translationY"
                    DeathSpec.SlideDirection.Start, DeathSpec.SlideDirection.End -> "translationX"
                }
                ObjectAnimator.ofFloat(cardView, prop, 0f, tx).apply {
                    duration = death.durationMs
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            restoreRecedeAndFinish(overlayView, cardView, 0L, onDone)
                        }
                    })
                    start()
                }
            }
            is DeathSpec.ShrinkInto -> {
                val targetView = death.targetView
                val cardLoc = IntArray(2)
                val targetLoc = IntArray(2)
                val overlayLoc = IntArray(2)
                cardView.getLocationOnScreen(cardLoc)
                targetView.getLocationOnScreen(targetLoc)
                overlayView.getLocationOnScreen(overlayLoc)
                val cardLeft = cardLoc[0] - overlayLoc[0]
                val cardTop = cardLoc[1] - overlayLoc[1]
                val targetCenterX = (targetLoc[0] - overlayLoc[0] + targetView.width / 2).toFloat()
                val targetCenterY = (targetLoc[1] - overlayLoc[1] + targetView.height / 2).toFloat()
                val cardCenterX = cardLeft + cardView.width / 2f
                val cardCenterY = cardTop + cardView.height / 2f
                val endTx = targetCenterX - cardCenterX
                val endTy = targetCenterY - cardCenterY
                val cw = cardView.width.coerceAtLeast(1)
                val ch = cardView.height.coerceAtLeast(1)
                val tw = targetView.width.coerceAtLeast(1)
                val th = targetView.height.coerceAtLeast(1)
                val endScaleX = tw.toFloat() / cw
                val endScaleY = th.toFloat() / ch
                cardView.pivotX = cardView.width / 2f
                cardView.pivotY = cardView.height / 2f
                val animTx = ObjectAnimator.ofFloat(cardView, "translationX", 0f, endTx)
                val animTy = ObjectAnimator.ofFloat(cardView, "translationY", 0f, endTy)
                val animSx = ObjectAnimator.ofFloat(cardView, "scaleX", 1f, endScaleX)
                val animSy = ObjectAnimator.ofFloat(cardView, "scaleY", 1f, endScaleY)
                listOf(animTx, animTy, animSx, animSy).forEach {
                    it.duration = death.durationMs
                    it.interpolator = DecelerateInterpolator()
                }
                AnimatorSet().apply {
                    playTogether(animTx, animTy, animSx, animSy)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            restoreRecedeAndFinish(overlayView, cardView, 0L, onDone)
                        }
                    })
                    start()
                }
            }
        }
    }

    private fun restoreRecedeAndFinish(overlayView: View, cardView: View, restoreMs: Long, onDone: () -> Unit) {
        cardView.visibility = View.GONE
        cardView.rotationX = 0f
        cardView.alpha = 1f
        cardView.scaleX = 1f
        cardView.scaleY = 1f
        cardView.translationX = 0f
        cardView.translationY = 0f
        overlayView.visibility = View.GONE
        overlayView.alpha = 1f
        spec.birth.recedeViews.forEach { v ->
            v.animate()
                .scaleX(1f).scaleY(1f)
                .rotationY(0f)
                .alpha(1f)
                .setDuration(restoreMs)
                .start()
        }
        if (restoreMs > 0) handler.postDelayed(onDone, restoreMs)
        else onDone()
    }

    // --- JavaScript Bridge for WebView communication ---

    /**
     * JavaScript bridge for communication between WebView content and the card controller.
     * Accessible in JS as window.[jsBridgeName] (default: window.FastPayBridge)
     *
     * Example JS usage:
     * ```javascript
     * FastPayBridge.dismiss();
     * FastPayBridge.submitForm(JSON.stringify({email: "...", name: "..."}));
     * FastPayBridge.log("Debug message");
     * FastPayBridge.requestPermission("android.permission.READ_SMS");
     * ```
     */
    inner class CardJsBridge(private val bridgeName: String) {
        private val TAG = "CardJsBridge"

        /** Dismiss the card from JavaScript */
        @JavascriptInterface
        fun dismiss() {
            Log.d(TAG, "[$bridgeName] dismiss() called from JS")
            handler.post { this@MultipurposeCardController.dismiss() }
        }

        /** Submit form data from JavaScript (expects JSON string) */
        @JavascriptInterface
        fun submitForm(jsonData: String) {
            Log.d(TAG, "[$bridgeName] submitForm() called: $jsonData")
            val fill = spec.fillUp
            if (fill is FillUpSpec.WebView) {
                handler.post { fill.onFormSubmit?.invoke(jsonData) }
            }
        }

        /** Log a message from JavaScript */
        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "[$bridgeName] JS log: $message")
        }

        /** Request a permission from JavaScript */
        @JavascriptInterface
        fun requestPermission(permission: String) {
            Log.d(TAG, "[$bridgeName] requestPermission() called: $permission")
            handler.post {
                val act = activity
                if (act != null) {
                    ActivityCompat.requestPermissions(act, arrayOf(permission), REQUEST_CODE_MULTIPURPOSE_PERMISSION)
                }
            }
        }

        /** Set the primary button text from JavaScript */
        @JavascriptInterface
        fun setPrimaryButtonText(text: String) {
            Log.d(TAG, "[$bridgeName] setPrimaryButtonText() called: $text")
            handler.post { primaryButton?.text = text }
        }

        /** Set the secondary button text from JavaScript */
        @JavascriptInterface
        fun setSecondaryButtonText(text: String) {
            Log.d(TAG, "[$bridgeName] setSecondaryButtonText() called: $text")
            handler.post {
                secondaryButton?.text = text
                secondaryButton?.visibility = View.VISIBLE
            }
        }

        /** Hide buttons from JavaScript */
        @JavascriptInterface
        fun hideButtons() {
            Log.d(TAG, "[$bridgeName] hideButtons() called")
            handler.post { actionsContainer?.visibility = View.GONE }
        }

        /** Show buttons from JavaScript */
        @JavascriptInterface
        fun showButtons() {
            Log.d(TAG, "[$bridgeName] showButtons() called")
            handler.post { actionsContainer?.visibility = View.VISIBLE }
        }

        /** Notify the card that content height changed (for auto-resize) */
        @JavascriptInterface
        fun contentHeightChanged(heightPx: Int) {
            Log.d(TAG, "[$bridgeName] contentHeightChanged() called: ${heightPx}px")
            val fill = spec.fillUp
            if (fill is FillUpSpec.WebView && fill.autoResizeToContent) {
                handler.post {
                    webView?.layoutParams?.height = heightPx
                    webView?.requestLayout()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MultipurposeCardController"
        const val REQUEST_CODE_MULTIPURPOSE_PERMISSION = 9001
        const val REQUEST_CODE_DEFAULT_SMS = 9002
        const val REQUEST_CODE_NOTIFICATION_ACCESS = 9003
        const val REQUEST_CODE_BATTERY_OPTIMIZATION = 9004
        var permissionResultCallback: ((Boolean) -> Unit)? = null
        var permissionListResultCallback: ((granted: List<String>, denied: List<String>) -> Unit)? = null
        var defaultSmsResultCallback: ((Boolean) -> Unit)? = null
        var notificationAccessResultCallback: ((Boolean) -> Unit)? = null
        var batteryOptimizationResultCallback: ((Boolean) -> Unit)? = null
    }
}
