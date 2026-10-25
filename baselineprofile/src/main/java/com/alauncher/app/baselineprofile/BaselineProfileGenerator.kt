package com.alauncher.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This class generates a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles)
 * for the `:app` target.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.alauncher.app",
        // Check out docs for more iteration types
        includeInStartupProfile = true
    ) {
        // This block defines the user journey to be captured in the profile.
        // We start the app and wait for the home screen to load.
        pressHome()
        startActivityAndWait()

        // Wait for the time text to appear as a signal that the home screen is ready
        device.waitForIdle()
        
        // Performance efficiency: We only capture the most frequent user journeys.
        // For a launcher, that's opening the app and swiping.
        
        // Simulate a swipe to ensure that part of the code is also optimized.
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        device.swipe(
            displayWidth / 2, 
            displayHeight / 2, 
            displayWidth / 4, 
            displayHeight / 2, 
            20
        )
        device.waitForIdle()
    }
}
