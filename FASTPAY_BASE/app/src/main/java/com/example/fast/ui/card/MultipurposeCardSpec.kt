package com.example.fast.ui.card

import android.content.Intent
import android.view.View

/**
 * Full spec for a Multipurpose CARD: birth (INPUT), fill-up, purpose, and death (dismiss).
 * All aspects are configurable so the card is truly multipurpose.
 */
data class MultipurposeCardSpec(
    val birth: BirthSpec,
    val fillUp: FillUpSpec,
    val purpose: PurposeSpec,
    val death: DeathSpec,
)

// --- Birth / INPUT ---

/**
 * Where the card is born from, its size, place on screen, and entrance animation.
 */
data class BirthSpec(
    /** Optional source view; card can "born from" this view. If null, use [placement]. */
    val originView: View? = null,
    /** Width: exact dp, or ratio of parent (0f-1f), or MATCH_PARENT sentinel. */
    val width: CardSize = CardSize.MatchWithMargin(24),
    /** Height: exact dp, or ratio, or WRAP_CONTENT / MATCH_PARENT. */
    val height: CardSize = CardSize.WrapContent,
    /** Where to place the card on screen (gravity + optional offset). */
    val placement: PlacementSpec = PlacementSpec.Center,
    /** Views to recede (scale/fade) while card appears; empty = no recede. */
    val recedeViews: List<View> = emptyList(),
    /** Entrance animation type and durations (ms). */
    val entranceAnimation: EntranceAnimation = EntranceAnimation.FlipIn(
        overlayFadeMs = 150,
        recedeMs = 350,
        recedeFadeMs = 600,
        flipOutOriginMs = 200,
        cardFlipInMs = 600,
    ),
)

/** Width/height: fixed dp, ratio (0f-1f), match with margin, or wrap. */
sealed class CardSize {
    data class FixedDp(val dp: Int) : CardSize()
    data class Ratio(val ratio: Float) : CardSize()
    data class MatchWithMargin(val marginHorizontalDp: Int = 0, val marginVerticalDp: Int = 0) : CardSize()
    data object WrapContent : CardSize()
    data object MatchParent : CardSize()
}

/** Where the card is placed: gravity and optional offset in dp. */
data class PlacementSpec(
    val gravity: Int,
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
) {
    companion object {
        val Center = PlacementSpec(android.view.Gravity.CENTER)
        val Top = PlacementSpec(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
        val Bottom = PlacementSpec(android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL)
        val Start = PlacementSpec(android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL)
        val End = PlacementSpec(android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL)
    }
}

/** Entrance animation: flip-in (like activation prompt) or simpler variants. */
sealed class EntranceAnimation {
    abstract val overlayFadeMs: Long
    data class FlipIn(
        override val overlayFadeMs: Long = 150,
        val recedeMs: Long = 350,
        val recedeFadeMs: Long = 600,
        val flipOutOriginMs: Long = 200,
        val cardFlipInMs: Long = 600,
    ) : EntranceAnimation()
    data class FadeIn(
        override val overlayFadeMs: Long = 150,
        val cardFadeMs: Long = 300,
    ) : EntranceAnimation()
    data class ScaleIn(
        override val overlayFadeMs: Long = 150,
        val cardScaleMs: Long = 300,
        val fromScale: Float = 0.8f,
    ) : EntranceAnimation()
}

// --- Fill-up ---

/**
 * What goes inside the card: text (with optional typing animation) or WebView.
 */
sealed class FillUpSpec {
    abstract val title: String?
    abstract val delayBeforeFillMs: Long

    data class Text(
        override val title: String?,
        val body: String,
        val bodyLines: List<String>? = null,
        override val delayBeforeFillMs: Long = 0,
        val typingAnimation: Boolean = false,
        val perCharDelayMs: Long = 45,
    ) : FillUpSpec() {
        fun effectiveLines(): List<String> = bodyLines ?: listOf(body)
    }

    data class WebView(
        override val title: String?,
        val html: String? = null,
        val url: String? = null,
        override val delayBeforeFillMs: Long = 0,
    ) : FillUpSpec()
}

// --- Purpose ---

/**
 * Final action: button(s) and what happens when user acts (dismiss, redirect, permission, update, dual, custom).
 */
sealed class PurposeSpec {
    abstract val primaryButtonLabel: String?
    abstract val showActionsAfterFillUp: Boolean

    data class Dismiss(
        override val primaryButtonLabel: String = "Continue",
        override val showActionsAfterFillUp: Boolean = true,
        val onPrimary: (() -> Unit)? = null,
    ) : PurposeSpec()

    data class Redirect(
        override val primaryButtonLabel: String,
        val intent: Intent,
        override val showActionsAfterFillUp: Boolean = true,
        val onBeforeNavigate: (() -> Unit)? = null,
    ) : PurposeSpec()

    data class RequestPermission(
        override val primaryButtonLabel: String = "Grant",
        val permission: String,
        override val showActionsAfterFillUp: Boolean = true,
        val onResult: (Boolean) -> Unit = {},
    ) : PurposeSpec()

    data class UpdateApk(
        override val primaryButtonLabel: String = "Update",
        override val showActionsAfterFillUp: Boolean = true,
        val onStartUpdate: () -> Unit,
    ) : PurposeSpec()

    data class Dual(
        override val primaryButtonLabel: String,
        val secondaryButtonLabel: String,
        override val showActionsAfterFillUp: Boolean = true,
        val onPrimary: () -> Unit = {},
        val onSecondary: () -> Unit = {},
    ) : PurposeSpec()

    data class Custom(
        override val primaryButtonLabel: String? = null,
        val secondaryButtonLabel: String? = null,
        override val showActionsAfterFillUp: Boolean = true,
        val onPrimary: (() -> Unit)? = null,
        val onSecondary: (() -> Unit)? = null,
    ) : PurposeSpec()
}

// --- Death / dismiss ---

/**
 * How the card dies: dismiss animation type and duration.
 */
sealed class DeathSpec {
    abstract val durationMs: Long

    data class FlipOut(
        override val durationMs: Long = 200,
        val thenRestoreMs: Long = 350,
    ) : DeathSpec()

    data class FadeOut(
        override val durationMs: Long = 200,
    ) : DeathSpec()

    data class ScaleDown(
        override val durationMs: Long = 200,
        val toScale: Float = 0.8f,
    ) : DeathSpec()

    data class SlideOut(
        override val durationMs: Long = 250,
        val direction: SlideDirection = SlideDirection.Bottom,
    ) : DeathSpec()

    /**
     * Card shrinks and moves into the target view's bounds, then hides.
     * Use for "die into button" effect.
     */
    data class ShrinkInto(
        val targetView: View,
        override val durationMs: Long = 300,
    ) : DeathSpec()

    enum class SlideDirection { Top, Bottom, Start, End }
}
