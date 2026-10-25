package com.alauncher.app.service

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationListenerRebindTest {

    private lateinit var context: Context
    private var accessGranted = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationListener.resetRebindCooldownForTesting()
        NotificationListener.setActiveInstanceForTesting(null)
        accessGranted = false
    }

    private fun grantNotificationAccess() {
        accessGranted = true
    }

    @Test
    fun `test rebind not requested when permission is not granted`() {
        var rebindCalled = false
        val result = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 1000L,
            isAccessGranted = { accessGranted },
            rebindAction = { rebindCalled = true }
        )

        assertFalse("Should return false when permission is missing", result)
        assertFalse("Rebind action should not be invoked", rebindCalled)
    }

    @Test
    fun `test rebind not requested when listener is already active and connected`() {
        grantNotificationAccess()
        // Simulate active listener
        val dummyInstance = NotificationListener()
        NotificationListener.setActiveInstanceForTesting(dummyInstance)

        var rebindCalled = false
        val result = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 1000L,
            isAccessGranted = { accessGranted },
            rebindAction = { rebindCalled = true }
        )

        assertFalse("Should return false when activeInstance is non-null", result)
        assertFalse("Rebind action should not be invoked", rebindCalled)
    }

    @Test
    fun `test rebind requested when disconnected and permission granted`() {
        grantNotificationAccess()
        var passedComponentName: ComponentName? = null

        val result = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 1000L,
            isAccessGranted = { accessGranted },
            rebindAction = { passedComponentName = it }
        )

        assertTrue("Should return true when successfully requested", result)
        assertNotNull("Rebind action should have been called", passedComponentName)
        assertEquals(NotificationListener::class.java.name, passedComponentName?.className)
    }

    @Test
    fun `test debounce throttles repeated calls within cooldown window`() {
        grantNotificationAccess()
        var callCount = 0
        val rebindAction: (ComponentName) -> Unit = { callCount++ }

        val firstResult = NotificationListener.requestRebindIfDisconnected(
            context = context,
            minIntervalMs = 30_000L,
            currentTimeMs = 10_000L,
            isAccessGranted = { accessGranted },
            rebindAction = rebindAction
        )
        assertTrue("First call should succeed", firstResult)
        assertEquals(1, callCount)

        // Call 5 seconds later (within 30s cooldown)
        val secondResult = NotificationListener.requestRebindIfDisconnected(
            context = context,
            minIntervalMs = 30_000L,
            currentTimeMs = 15_000L,
            isAccessGranted = { accessGranted },
            rebindAction = rebindAction
        )
        assertFalse("Second call within cooldown should be throttled", secondResult)
        assertEquals("Call count should remain 1", 1, callCount)

        // Call 25 seconds later (total 25s elapsed, still within 30s)
        val thirdResult = NotificationListener.requestRebindIfDisconnected(
            context = context,
            minIntervalMs = 30_000L,
            currentTimeMs = 35_000L,
            isAccessGranted = { accessGranted },
            rebindAction = rebindAction
        )
        assertFalse("Third call within cooldown should be throttled", thirdResult)
        assertEquals("Call count should remain 1", 1, callCount)

        // Call 31 seconds after first call (cooldown expired)
        val fourthResult = NotificationListener.requestRebindIfDisconnected(
            context = context,
            minIntervalMs = 30_000L,
            currentTimeMs = 41_000L,
            isAccessGranted = { accessGranted },
            rebindAction = rebindAction
        )
        assertTrue("Fourth call after cooldown should succeed", fourthResult)
        assertEquals("Call count should increment to 2", 2, callCount)
    }

    @Test
    fun `test exception during rebind is safely caught and returns false`() {
        grantNotificationAccess()

        val result = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 1000L,
            isAccessGranted = { accessGranted },
            rebindAction = { throw SecurityException("Not allowed by OEM framework") }
        )

        assertFalse("Should return false when exception thrown", result)
    }

    @Test
    fun `test failed rebind does not consume cooldown so retry can proceed`() {
        grantNotificationAccess()
        var attemptCount = 0

        // First attempt throws SecurityException
        val firstResult = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 1000L,
            isAccessGranted = { accessGranted },
            rebindAction = {
                attemptCount++
                throw SecurityException("OEM blocked rebind")
            }
        )
        assertFalse("First attempt should fail", firstResult)
        assertEquals(1, attemptCount)

        // Second attempt 2 seconds later should NOT be throttled by cooldown because the first failed
        val secondResult = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 3000L,
            isAccessGranted = { accessGranted },
            rebindAction = {
                attemptCount++
            }
        )
        assertTrue("Second attempt should be allowed to retry immediately", secondResult)
        assertEquals(2, attemptCount)
    }

    @Test
    fun `test resetRebindCooldownForTesting allows immediate rebind during active window`() {
        grantNotificationAccess()
        var callCount = 0

        val first = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 10_000L,
            isAccessGranted = { accessGranted },
            rebindAction = { callCount++ }
        )
        assertTrue(first)
        assertEquals(1, callCount)

        // Reset cooldown explicitly
        NotificationListener.resetRebindCooldownForTesting()

        // Call 1 second later (would normally be throttled)
        val second = NotificationListener.requestRebindIfDisconnected(
            context = context,
            currentTimeMs = 11_000L,
            isAccessGranted = { accessGranted },
            rebindAction = { callCount++ }
        )
        assertTrue("Rebind should proceed after manual cooldown reset", second)
        assertEquals(2, callCount)
    }

    @Test
    fun `test PackageReplacedReceiver triggers rebind on ACTION_MY_PACKAGE_REPLACED`() {
        val receiver = com.alauncher.app.receiver.PackageReplacedReceiver()
        val intent = android.content.Intent(android.content.Intent.ACTION_MY_PACKAGE_REPLACED)

        var rebindCalled = false
        com.alauncher.app.receiver.PackageReplacedReceiver.rebindTrigger = {
            rebindCalled = true
            true
        }

        receiver.onReceive(context, intent)
        assertTrue("Rebind must be triggered on ACTION_MY_PACKAGE_REPLACED", rebindCalled)

        // Other actions should be ignored and not trigger rebind
        rebindCalled = false
        val dummyIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_LOW)
        receiver.onReceive(context, dummyIntent)
        assertFalse("Dummy action must not trigger rebind", rebindCalled)

        // Null context or null intent should be handled safely
        receiver.onReceive(null, intent)
        receiver.onReceive(context, null)

        // Reset rebindTrigger
        com.alauncher.app.receiver.PackageReplacedReceiver.rebindTrigger = { NotificationListener.requestRebindIfDisconnected(it) }
    }
}
