package com.alauncher.app.service

import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUsageServiceOverlayLifecycleTest {

    private lateinit var service: AppUsageService

    @Before
    fun setUp() {
        val controller = Robolectric.buildService(AppUsageService::class.java)
        service = controller.get()
    }

    @Test
    fun sharedStateFields_areMarkedVolatileToPreventThreadVisibilityHazards() {
        val volatileFieldNames = listOf(
            "currentTrackingPackage",
            "sessionStartTime",
            "isOverlayShowing",
            "settingsState",
            "lastKnownForegroundPackage",
            "lastPollTime"
        )

        for (fieldName in volatileFieldNames) {
            val field = AppUsageService::class.java.getDeclaredField(fieldName)
            assertTrue(
                "Field $fieldName must be marked @Volatile to guarantee cross-thread visibility",
                Modifier.isVolatile(field.modifiers)
            )
        }
    }

    @Test
    fun dismissOverlay_isIdempotentAndResetsOverlayState() {
        val overlayShowingField = AppUsageService::class.java.getDeclaredField("isOverlayShowing")
        overlayShowingField.isAccessible = true

        val dismissMethod = AppUsageService::class.java.getDeclaredMethod("dismissOverlay")
        dismissMethod.isAccessible = true

        // Simulate overlay being marked active
        overlayShowingField.set(service, true)
        assertTrue(overlayShowingField.get(service) as Boolean)

        // Dismiss overlay
        dismissMethod.invoke(service)
        assertFalse(
            "isOverlayShowing must be reset to false after dismissOverlay()",
            overlayShowingField.get(service) as Boolean
        )

        // Second dismiss call must be safe and idempotent
        dismissMethod.invoke(service)
        assertFalse(overlayShowingField.get(service) as Boolean)
    }

    @Test
    fun dismissOverlay_withComposeView_nullsFieldAndResetsState() {
        // Inject windowManager via reflection (onCreate guard prevents normal init)
        val wmField = AppUsageService::class.java.getDeclaredField("windowManager")
        wmField.isAccessible = true
        val wm = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        wmField.set(service, wm)

        // Create a real ComposeView and inject it
        val composeViewField = AppUsageService::class.java.getDeclaredField("composeView")
        composeViewField.isAccessible = true
        val cv = ComposeView(ApplicationProvider.getApplicationContext())
        composeViewField.set(service, cv)

        // Set overlay state
        val overlayShowingField = AppUsageService::class.java.getDeclaredField("isOverlayShowing")
        overlayShowingField.isAccessible = true
        overlayShowingField.set(service, true)

        // Dismiss — this exercises the let{} block with disposeComposition + removeView.
        // The ComposeView was never added to the WindowManager, so removeView will throw,
        // which is caught by the try-catch in production code. The important thing is the
        // field state is updated: composeView nulled and isOverlayShowing reset.
        val dismissMethod = AppUsageService::class.java.getDeclaredMethod("dismissOverlay")
        dismissMethod.isAccessible = true
        dismissMethod.invoke(service)

        assertFalse(
            "isOverlayShowing must be false after dismiss",
            overlayShowingField.get(service) as Boolean
        )
        assertNull(
            "composeView field must be nulled after dismiss (proves the let{} block ran)",
            composeViewField.get(service)
        )
    }

    @Test
    fun showOverlay_whenCannotDrawOverlays_resetsIsOverlayShowingToFalse() {
        val overlayShowingField = AppUsageService::class.java.getDeclaredField("isOverlayShowing")
        overlayShowingField.isAccessible = true
        overlayShowingField.set(service, true)

        val showOverlayMethod = AppUsageService::class.java.getDeclaredMethod("showOverlay", String::class.java)
        showOverlayMethod.isAccessible = true
        showOverlayMethod.invoke(service, "Test App")

        assertFalse(
            "isOverlayShowing must be reset to false when Settings.canDrawOverlays is false",
            overlayShowingField.get(service) as Boolean
        )
    }

    @Test
    fun showOverlay_whenWindowManagerAddViewThrows_disposesViewAndResetsFields() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)

        val wmField = AppUsageService::class.java.getDeclaredField("windowManager").apply { isAccessible = true }
        val composeViewField = AppUsageService::class.java.getDeclaredField("composeView").apply { isAccessible = true }
        val overlayShowingField = AppUsageService::class.java.getDeclaredField("isOverlayShowing").apply { isAccessible = true }

        // Inject a throwing WindowManager proxy
        val throwingWm = java.lang.reflect.Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)
        ) { _, method, _ ->
            if (method.name == "addView") {
                throw WindowManager.BadTokenException("Simulated token revoked")
            }
            null
        } as WindowManager
        wmField.set(service, throwingWm)

        val showOverlayMethod = AppUsageService::class.java.getDeclaredMethod("showOverlay", String::class.java).apply { isAccessible = true }
        showOverlayMethod.invoke(service, "Test App")

        assertNull(
            "composeView must be nulled when addView throws",
            composeViewField.get(service)
        )
        assertFalse(
            "isOverlayShowing must be reset to false when addView throws",
            overlayShowingField.get(service) as Boolean
        )
    }

    @Test
    fun showOverlay_whenComposeViewAlreadyExists_isIdempotentAndDoesNotRecreateView() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)

        val composeViewField = AppUsageService::class.java.getDeclaredField("composeView").apply { isAccessible = true }
        val existingView = ComposeView(ApplicationProvider.getApplicationContext())
        composeViewField.set(service, existingView)

        val showOverlayMethod = AppUsageService::class.java.getDeclaredMethod("showOverlay", String::class.java).apply { isAccessible = true }
        showOverlayMethod.invoke(service, "Test App")

        org.junit.Assert.assertSame(
            "composeView instance must not be replaced when overlay already exists",
            existingView,
            composeViewField.get(service)
        )
    }
}
