package com.alauncher.app.ui.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InAppReviewHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(InAppReviewHelper.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun shouldRequestReview_enforcesAllMilestoneGates() {
        val baseTime = 1_700_000_000_000L
        val fourDaysLater = baseTime + InAppReviewHelper.MIN_INSTALL_AGE_MS + 1_000L

        // 1. Walkthrough incomplete -> false
        assertFalse(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = false,
                isFocusModeActive = false,
                firstSeenMs = baseTime,
                appLaunchCount = 20,
                lastReviewRequestMs = 0L,
                nowMs = fourDaysLater
            )
        )

        // 2. Focus Mode active -> false
        assertFalse(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = true,
                isFocusModeActive = true,
                firstSeenMs = baseTime,
                appLaunchCount = 20,
                lastReviewRequestMs = 0L,
                nowMs = fourDaysLater
            )
        )

        // 3. Install age < 3 days -> false
        assertFalse(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = true,
                isFocusModeActive = false,
                firstSeenMs = baseTime,
                appLaunchCount = 20,
                lastReviewRequestMs = 0L,
                nowMs = baseTime + InAppReviewHelper.MIN_INSTALL_AGE_MS - 1L
            )
        )

        // 4. Launch count < 15 -> false
        assertFalse(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = true,
                isFocusModeActive = false,
                firstSeenMs = baseTime,
                appLaunchCount = InAppReviewHelper.MIN_APP_LAUNCH_COUNT - 1,
                lastReviewRequestMs = 0L,
                nowMs = fourDaysLater
            )
        )

        // 5. All criteria met (first prompt) -> true
        assertTrue(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = true,
                isFocusModeActive = false,
                firstSeenMs = baseTime,
                appLaunchCount = InAppReviewHelper.MIN_APP_LAUNCH_COUNT,
                lastReviewRequestMs = 0L,
                nowMs = fourDaysLater
            )
        )

        // 6. Within 60-day cooldown -> false
        val thirtyDaysAfterPrompt = fourDaysLater + (InAppReviewHelper.REVIEW_COOLDOWN_MS / 2)
        assertFalse(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = true,
                isFocusModeActive = false,
                firstSeenMs = baseTime,
                appLaunchCount = 30,
                lastReviewRequestMs = fourDaysLater,
                nowMs = thirtyDaysAfterPrompt
            )
        )

        // 7. After 60-day cooldown -> true
        val sixtyOneDaysAfterPrompt = fourDaysLater + InAppReviewHelper.REVIEW_COOLDOWN_MS + 1_000L
        assertTrue(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = true,
                isFocusModeActive = false,
                firstSeenMs = baseTime,
                appLaunchCount = 30,
                lastReviewRequestMs = fourDaysLater,
                nowMs = sixtyOneDaysAfterPrompt
            )
        )

        // 8. Non-default launcher -> false
        assertFalse(
            InAppReviewHelper.shouldRequestReview(
                hasCompletedWalkthrough = true,
                isFocusModeActive = false,
                firstSeenMs = baseTime,
                appLaunchCount = 30,
                lastReviewRequestMs = 0L,
                nowMs = fourDaysLater,
                isDefaultLauncher = false
            )
        )
    }

    @Test
    fun maybeLaunchInAppReview_withFakeReviewManager_launchesOnceAndEnforcesCooldown() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val fakeReviewManager = FakeReviewManager(context)
        val startMs = 1_700_000_000_000L

        // Seed 15 app launches starting at startMs
        repeat(InAppReviewHelper.MIN_APP_LAUNCH_COUNT) {
            InAppReviewHelper.recordAppLaunch(context, nowMs = startMs)
        }

        // Override first_seen_timestamp_ms to startMs (in case PackageManager had a different install time)
        context.getSharedPreferences(InAppReviewHelper.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(InAppReviewHelper.KEY_FIRST_SEEN_MS, startMs)
            .commit()

        val eligibleTimeMs = startMs + InAppReviewHelper.MIN_INSTALL_AGE_MS + 5_000L

        // First call at eligible milestone should launch and return true
        val launchedFirst = InAppReviewHelper.maybeLaunchInAppReview(
            activity = activity,
            hasCompletedWalkthrough = true,
            isFocusModeActive = false,
            reviewManager = fakeReviewManager,
            nowMs = eligibleTimeMs
        )
        assertTrue("Expected review flow to launch when milestone is reached", launchedFirst)

        // Immediate second call must be blocked by the 60-day cooldown
        val launchedSecond = InAppReviewHelper.maybeLaunchInAppReview(
            activity = activity,
            hasCompletedWalkthrough = true,
            isFocusModeActive = false,
            reviewManager = fakeReviewManager,
            nowMs = eligibleTimeMs + 10_000L
        )
        assertFalse("Expected second review request within 60-day cooldown to be skipped", launchedSecond)
    }

    @Test
    fun openPlayStoreListing_launchesMarketViewIntent() {
        val launched = InAppReviewHelper.openPlayStoreListing(context)
        assertTrue(launched)

        val nextIntent: Intent? = shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull("Expected an ACTION_VIEW intent to be started", nextIntent)
        assertEquals(Intent.ACTION_VIEW, nextIntent?.action)
        assertTrue(
            "Expected market:// or https://play.google.com URI",
            nextIntent?.dataString?.contains(context.packageName) == true
        )
    }
}
