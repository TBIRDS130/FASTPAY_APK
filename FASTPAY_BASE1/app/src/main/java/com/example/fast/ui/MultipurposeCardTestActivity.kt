package com.example.fast.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.fast.R
import com.example.fast.databinding.ActivityMultipurposeCardTestBinding
import com.example.fast.ui.card.BirthSpec
import com.example.fast.ui.card.CardSize
import com.example.fast.ui.card.DeathSpec
import com.example.fast.ui.card.EntranceAnimation
import com.example.fast.ui.card.FillUpSpec
import com.example.fast.ui.card.MultipurposeCardController
import com.example.fast.ui.card.MultipurposeCardSpec
import com.example.fast.ui.card.PlacementSpec
import com.example.fast.ui.card.PurposeSpec

/**
 * Test activity for Multipurpose CARD. Launch via:
 * adb shell am start -n com.example.fast/.ui.MultipurposeCardTestActivity
 * Or from another activity: startActivity(Intent(this, MultipurposeCardTestActivity::class.java))
 */
class MultipurposeCardTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMultipurposeCardTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultipurposeCardTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.testDismiss.setOnClickListener { showDismissCard() }
        binding.testFadeBirthDeath.setOnClickListener { showFadeBirthDeathCard() }
        binding.testTyping.setOnClickListener { showTypingCard() }
        binding.testDual.setOnClickListener { showDualCard() }
        binding.testRedirect.setOnClickListener { showRedirectCard() }
        binding.testWebView.setOnClickListener { showWebViewCard() }
        binding.testSizePlace.setOnClickListener { showSizePlaceCard() }
        binding.testScaleDeath.setOnClickListener { showScaleDeathCard() }
        binding.testBornShrinkInto.setOnClickListener { showBornShrinkIntoCard() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MultipurposeCardController.REQUEST_CODE_MULTIPURPOSE_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            MultipurposeCardController.permissionResultCallback?.invoke(granted)
            MultipurposeCardController.permissionResultCallback = null
        }
    }

    private fun showCard(spec: MultipurposeCardSpec) {
        MultipurposeCardController(
            context = this,
            rootView = binding.multipurposeCardTestRoot,
            spec = spec,
            onComplete = { Toast.makeText(this, "Card dismissed", Toast.LENGTH_SHORT).show() },
            activity = this,
        ).show()
    }

    /** 1. Dismiss card: FlipIn birth, text fill, Continue button, FlipOut death. */
    private fun showDismissCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.WrapContent,
                    placement = PlacementSpec.Center,
                    recedeViews = emptyList(),
                    entranceAnimation = EntranceAnimation.FlipIn(),
                ),
                fillUp = FillUpSpec.Text(
                    title = "TEST CARD",
                    body = "This is a multipurpose card with Dismiss purpose. Tap Continue to close.",
                    delayBeforeFillMs = 0,
                    typingAnimation = false,
                ),
                purpose = PurposeSpec.Dismiss(
                    primaryButtonLabel = "Continue",
                    onPrimary = { },
                ),
                death = DeathSpec.FlipOut(durationMs = 200, thenRestoreMs = 350),
            ),
        )
    }

    /** 2. FadeIn birth + FadeOut death. */
    private fun showFadeBirthDeathCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.WrapContent,
                    placement = PlacementSpec.Center,
                    entranceAnimation = EntranceAnimation.FadeIn(overlayFadeMs = 150, cardFadeMs = 300),
                ),
                fillUp = FillUpSpec.Text(
                    title = "FADE CARD",
                    body = "Birth: FadeIn. Death: FadeOut.",
                    typingAnimation = false,
                ),
                purpose = PurposeSpec.Dismiss(primaryButtonLabel = "OK"),
                death = DeathSpec.FadeOut(durationMs = 200),
            ),
        )
    }

    /** 3. Text with typing animation. */
    private fun showTypingCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.WrapContent,
                    placement = PlacementSpec.Center,
                    entranceAnimation = EntranceAnimation.FlipIn(),
                ),
                fillUp = FillUpSpec.Text(
                    title = "TYPING",
                    body = "Line one.\nLine two.\nLine three.",
                    bodyLines = listOf("Line one.", "Line two.", "Line three."),
                    delayBeforeFillMs = 0,
                    typingAnimation = true,
                    perCharDelayMs = 40,
                ),
                purpose = PurposeSpec.Dismiss(primaryButtonLabel = "Continue"),
                death = DeathSpec.FlipOut(),
            ),
        )
    }

    /** 4. Dual buttons (Grant / Not now). */
    private fun showDualCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.WrapContent,
                    placement = PlacementSpec.Center,
                    entranceAnimation = EntranceAnimation.FadeIn(),
                ),
                fillUp = FillUpSpec.Text(
                    title = "DUAL ACTION",
                    body = "Choose Grant or Not now.",
                    typingAnimation = false,
                ),
                purpose = PurposeSpec.Dual(
                    primaryButtonLabel = "Grant",
                    secondaryButtonLabel = "Not now",
                    onPrimary = { Toast.makeText(this, "Grant tapped", Toast.LENGTH_SHORT).show() },
                    onSecondary = { Toast.makeText(this, "Not now tapped", Toast.LENGTH_SHORT).show() },
                ),
                death = DeathSpec.FadeOut(durationMs = 200),
            ),
        )
    }

    /** 5. Redirect: opens same activity again (so we stay on test screen). */
    private fun showRedirectCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.WrapContent,
                    placement = PlacementSpec.Center,
                    entranceAnimation = EntranceAnimation.FlipIn(),
                ),
                fillUp = FillUpSpec.Text(
                    title = "REDIRECT",
                    body = "Tap Go to refresh this screen.",
                    typingAnimation = false,
                ),
                purpose = PurposeSpec.Redirect(
                    primaryButtonLabel = "Go",
                    intent = Intent(this, MultipurposeCardTestActivity::class.java),
                    onBeforeNavigate = { },
                ),
                death = DeathSpec.FlipOut(),
            ),
        )
    }

    /** 6. WebView fill. */
    private fun showWebViewCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.FixedDp(220),
                    placement = PlacementSpec.Center,
                    entranceAnimation = EntranceAnimation.FadeIn(),
                ),
                fillUp = FillUpSpec.WebView(
                    title = "WEBVIEW",
                    html = """
                        <html><body style="color:#00ff88; font-family:monospace; font-size:12px;">
                        <p>HTML content inside the card.</p>
                        <p>Supports <b>bold</b> and <i>italic</i>.</p>
                        </body></html>
                    """.trimIndent(),
                    delayBeforeFillMs = 0,
                ),
                purpose = PurposeSpec.Dismiss(primaryButtonLabel = "Close"),
                death = DeathSpec.FadeOut(durationMs = 200),
            ),
        )
    }

    /** 7. Custom size + bottom placement. */
    private fun showSizePlaceCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.Ratio(0.9f),
                    height = CardSize.FixedDp(180),
                    placement = PlacementSpec.Bottom.copy(offsetYDp = -48),
                    entranceAnimation = EntranceAnimation.ScaleIn(fromScale = 0.7f),
                ),
                fillUp = FillUpSpec.Text(
                    title = "BOTTOM CARD",
                    body = "90% width, 180dp height, bottom placement.",
                    typingAnimation = false,
                ),
                purpose = PurposeSpec.Dismiss(primaryButtonLabel = "OK"),
                death = DeathSpec.SlideOut(durationMs = 250, direction = DeathSpec.SlideDirection.Bottom),
            ),
        )
    }

    /** 8. ScaleDown death. */
    private fun showScaleDeathCard() {
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.WrapContent,
                    placement = PlacementSpec.Center,
                    entranceAnimation = EntranceAnimation.FadeIn(),
                ),
                fillUp = FillUpSpec.Text(
                    title = "SCALE DOWN",
                    body = "Card will scale down when dismissed.",
                    typingAnimation = false,
                ),
                purpose = PurposeSpec.Dismiss(primaryButtonLabel = "Dismiss"),
                death = DeathSpec.ScaleDown(durationMs = 250, toScale = 0.5f),
            ),
        )
    }

    /** 9. Born from button + ShrinkInto (same flow as ActivatedActivity test button). */
    private fun showBornShrinkIntoCard() {
        val originAndTarget = binding.testBornShrinkInto
        showCard(
            MultipurposeCardSpec(
                birth = BirthSpec(
                    originView = originAndTarget,
                    width = CardSize.MatchWithMargin(24),
                    height = CardSize.Ratio(0.34f),
                    placement = PlacementSpec.Center,
                    recedeViews = emptyList(),
                    entranceAnimation = EntranceAnimation.FlipIn(),
                ),
                fillUp = FillUpSpec.Text(
                    title = null,
                    body = "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                    bodyLines = listOf(
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                    ),
                    typingAnimation = true,
                    perCharDelayMs = 45,
                ),
                purpose = PurposeSpec.Dismiss(primaryButtonLabel = "Continue"),
                death = DeathSpec.ShrinkInto(targetView = originAndTarget, durationMs = 300),
            ),
        )
    }
}
