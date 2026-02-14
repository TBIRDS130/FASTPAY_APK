package com.example.fast.integration

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.fast.R
import com.example.fast.ui.ActivatedActivity
import com.example.fast.ui.SplashActivity
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.CoreMatchers.anyOf

/**
 * UI automation tests for Activation → Activated flow.
 *
 * - Verifies app launches and reaches either Activation or Activated screen (no crash).
 * - Verifies ActivatedActivity can open and show main UI (catches regressions like card flip crash).
 *
 * Run: ./gradlew connectedDebugAndroidTest --info
 * (--info shows test stdout in terminal)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ActivationToActivatedFlowTest {

    private fun log(msg: String) {
        System.out.println("[ActivationFlowTest] $msg")
    }

    /**
     * Launch app from Splash (launcher). Splash navigates to ActivationActivity or ActivatedActivity.
     * Assert we reach a main screen: either activation (Activate button) or activated (status card).
     */
    @Test
    fun appLaunchesAndReachesActivationOrActivatedScreen() {
        log("appLaunchesAndReachesActivationOrActivatedScreen: Starting")
        val intent = Intent(ApplicationProvider.getApplicationContext(), SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<SplashActivity>(intent).use { scenario ->
            log("Splash launched, waiting 6s for navigation")
            scenario.onActivity { }
            Thread.sleep(6_000)
            log("Asserting Activation or Activated screen visible")
            onView(
                anyOf(
                    withId(R.id.activationActivateButton),
                    withId(R.id.statusCard)
                )
            ).check(ViewAssertions.matches(isDisplayed()))
            log("appLaunchesAndReachesActivationOrActivatedScreen: PASSED")
        }
    }

    /**
     * Launch ActivatedActivity directly. Asserts main UI is shown without crash
     * (e.g. regression from cardControl/animation flip or other Activated-only bugs).
     */
    @Test
    fun activatedActivityLaunchesAndShowsMainUI() {
        log("activatedActivityLaunchesAndShowsMainUI: Starting")
        val intent = Intent(ApplicationProvider.getApplicationContext(), ActivatedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<ActivatedActivity>(intent).use { scenario ->
            log("ActivatedActivity launched")
            scenario.onActivity { }
            log("Asserting statusCard or smsCard visible")
            onView(
                anyOf(
                    withId(R.id.statusCard),
                    withId(R.id.smsCard)
                )
            ).check(ViewAssertions.matches(isDisplayed()))
            log("activatedActivityLaunchesAndShowsMainUI: PASSED")
        }
    }
}
