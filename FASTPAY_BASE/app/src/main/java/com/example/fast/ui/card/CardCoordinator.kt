package com.example.fast.ui.card

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.example.fast.util.LogHelper

/**
 * Single entry point to show a MultipurposeCard either as an overlay or fullscreen.
 * All card types (permission, update, default_sms, message, webview, confirm, input, etc.)
 * go through RemoteCardHandler.buildSpec; this coordinator only decides overlay vs fullscreen.
 *
 * Usage:
 * - Fullscreen (e.g. from FCM/Service): CardCoordinator.show(context, data, asOverlay = false)
 * - Overlay (e.g. from ActivatedActivity): CardCoordinator.show(activity, data, asOverlay = true, rootView)
 */
object CardCoordinator {
    private const val TAG = "CardCoordinator"

    /**
     * Show a card from a map of key-value data (card_type, title, body, permissions, etc.).
     *
     * @param context Context (used for fullscreen launch; can be Application context).
     * @param data Map matching RemoteCardHandler keys (KEY_CARD_TYPE, KEY_TITLE, KEY_BODY, etc.).
     * @param asOverlay If true, show as overlay on [rootView] of [activity]; requires non-null activity and rootView.
     * @param rootView Required when [asOverlay] is true; the view group to attach the overlay to.
     * @param activity Required when [asOverlay] is true; the hosting activity.
     * @param recedeViews Optional views to recede (scale/fade) while the card is shown; e.g. logo/header so they are not left at full size on screen.
     * @param onComplete Called when the card is dismissed (overlay only; fullscreen activity finishes independently).
     * @return When [asOverlay] is true, the overlay controller so the caller can dismiss it; null otherwise or if spec failed.
     */
    @JvmStatic
    fun show(
        context: Context,
        data: Map<String, String>,
        asOverlay: Boolean,
        rootView: ViewGroup? = null,
        activity: Activity? = null,
        recedeViews: List<View>? = null,
        onComplete: () -> Unit = {}
    ): MultipurposeCardController? {
        return when {
            asOverlay && rootView != null && activity != null -> {
                LogHelper.d(TAG, "Showing card as overlay: ${data[RemoteCardHandler.KEY_CARD_TYPE]}")
                RemoteCardHandler.showOverlay(
                    context = context,
                    rootView = rootView,
                    data = data,
                    activity = activity,
                    recedeViews = recedeViews,
                    onComplete = onComplete
                )
            }
            else -> {
                LogHelper.d(TAG, "Showing card fullscreen: ${data[RemoteCardHandler.KEY_CARD_TYPE]}")
                RemoteCardHandler.launchFullscreenActivity(context, data)
                onComplete()
                null
            }
        }
    }
}
