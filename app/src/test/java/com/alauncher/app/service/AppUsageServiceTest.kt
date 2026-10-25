package com.alauncher.app.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class AppUsageServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
    }

    @Test
    fun testAppUsageServiceStopsSelfWhenNotDefaultLauncher() {
        val controller = Robolectric.buildService(AppUsageService::class.java)
        val service = controller.create().get()
        val shadowService = shadowOf(service)

        assertTrue(
            "AppUsageService must stop itself on creation when not the default launcher",
            shadowService.isStoppedBySelf
        )
    }

    @Test
    fun testAppUsageServiceOnStartCommandStopsSelfWhenNotDefaultLauncher() {
        val controller = Robolectric.buildService(AppUsageService::class.java)
        controller.create()
        val service = controller.startCommand(0, 0).get()
        val shadowService = shadowOf(service)

        assertTrue(
            "AppUsageService must stop itself on startCommand when not the default launcher",
            shadowService.isStoppedBySelf
        )
    }
}

