package com.alauncher.app.service

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherAccessibilityServiceTest {

    private lateinit var context: Context
    private lateinit var shadowApp: ShadowApplication

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        shadowApp = shadowOf(context as Application)
        shadowApp.clearNextStartedActivities()
    }

    @Test
    fun testLockScreenReturnsFalseWhenServiceNotRunning() {
        assertFalse("Service should not be running initially", LauncherAccessibilityService.isRunning())
        assertFalse("lockScreen should return false when service is not running", LauncherAccessibilityService.lockScreen())
    }

    @Test
    fun testOpenAccessibilitySettingsStartsCorrectIntent() {
        LauncherAccessibilityService.openAccessibilitySettings(context)
        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("Should start accessibility settings activity", startedIntent)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, startedIntent.action)
        assertTrue("Intent should have FLAG_ACTIVITY_NEW_TASK", (startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun testServiceLifecycleManagesInstanceRunningState() {
        val controller = Robolectric.buildService(LauncherAccessibilityService::class.java)
        val service = controller.create().get()

        assertFalse(LauncherAccessibilityService.isRunning())

        // onServiceConnected is protected on AccessibilityService, invoke via reflection
        val onServiceConnectedMethod = AccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        onServiceConnectedMethod.isAccessible = true
        onServiceConnectedMethod.invoke(service)

        assertTrue("Service should report running after onServiceConnected", LauncherAccessibilityService.isRunning())

        controller.destroy()
        assertFalse("Service should not report running after onDestroy", LauncherAccessibilityService.isRunning())
    }

    @Test
    fun testOnUnbindClearsRunningStateImmediately() {
        val controller = Robolectric.buildService(LauncherAccessibilityService::class.java)
        val service = controller.create().get()

        val onServiceConnectedMethod = AccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        onServiceConnectedMethod.isAccessible = true
        onServiceConnectedMethod.invoke(service)

        assertTrue(LauncherAccessibilityService.isRunning())

        service.onUnbind(Intent())
        assertFalse("Service should immediately report not running upon onUnbind", LauncherAccessibilityService.isRunning())
    }

    @Test
    fun testAccessibilityStringResourcesExist() {
        assertEquals("ALauncher Screen Lock", context.getString(R.string.accessibility_service_label))
        assertEquals("Double-Tap to Lock", context.getString(R.string.double_tap_to_lock_dialog_title))
        assertTrue(context.getString(R.string.double_tap_to_lock_dialog_message).contains("Accessibility Service"))
    }
}
