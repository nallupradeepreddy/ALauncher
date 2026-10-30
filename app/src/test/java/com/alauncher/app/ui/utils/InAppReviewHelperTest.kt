package com.alauncher.app.ui.utils

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
    }

    @Test
    fun openStoreListing_launchesMarketViewIntent() {
        val launched = InAppReviewHelper.openStoreListing(context)
        assertTrue(launched)

        val nextIntent: Intent? = shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull("Expected an ACTION_VIEW intent to be started", nextIntent)
        assertEquals(Intent.ACTION_VIEW, nextIntent?.action)
        assertTrue(
            "Expected market:// or https://play.google.com URI",
            nextIntent?.dataString?.contains(context.packageName) == true,
        )
    }
}
