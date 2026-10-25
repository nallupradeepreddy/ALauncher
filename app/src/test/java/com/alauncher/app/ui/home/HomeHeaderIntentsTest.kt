package com.alauncher.app.ui.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.ui.utils.openAlarmApp
import com.alauncher.app.ui.utils.openCalendarApp
import com.alauncher.app.ui.utils.openClockApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeHeaderIntentsTest {

    private lateinit var context: Context
    private lateinit var shadowApp: ShadowApplication

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        shadowApp = shadowOf(context as Application)
        shadowApp.clearNextStartedActivities()
    }

    @Test
    fun testOpenClockAppLaunchesActivityWithNewTaskFlag() {
        openClockApp(context)
        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("openClockApp should start an activity", startedIntent)
        assertTrue("Intent should contain FLAG_ACTIVITY_NEW_TASK", (startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, startedIntent.action)
    }

    @Test
    fun testOpenCalendarAppLaunchesActivityWithNewTaskFlag() {
        openCalendarApp(context)
        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("openCalendarApp should start an activity", startedIntent)
        assertTrue("Intent should contain FLAG_ACTIVITY_NEW_TASK", (startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertEquals(Intent.ACTION_MAIN, startedIntent.action)
        assertTrue("Intent should have CATEGORY_APP_CALENDAR", startedIntent.categories?.contains(Intent.CATEGORY_APP_CALENDAR) == true)
    }

    @Test
    fun testOpenAlarmAppLaunchesActivityWithNewTaskFlag() {
        openAlarmApp(context)
        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("openAlarmApp should start an activity", startedIntent)
        assertTrue("Intent should contain FLAG_ACTIVITY_NEW_TASK", (startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, startedIntent.action)
    }
}
