package com.alauncher.app.service

import android.app.Notification
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
class NotificationListenerFastCancelTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationListener.setCachedUnfilteredAppsForTesting(emptySet())
        NotificationListener.setCachedNotificationFilteringEnabledForTesting(true)
    }

    @Test
    fun `test fast cancellation is disabled when notificationFilteringEnabled is false`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.shopping.app",
            title = "Super Sale!",
            text = "Flat 50% off",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true,
            notificationFilteringEnabled = false
        )
        assertFalse("When notification filtering is disabled, fast-path cancel must be false", result)
    }

    @Test
    fun `test routine notification from non-immediate app is fast-path cancel candidate`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.shopping.app",
            title = "Super Sale!",
            text = "Flat 50% off on all items today only",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertTrue("Routine notification should be candidate for fast-path cancellation", result)
    }

    @Test
    fun `test notification from unfiltered immediate app bypasses fast cancellation`() {
        val unfilteredApps = setOf("com.whatsapp", "com.banking.app")
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.whatsapp",
            title = "Alice",
            text = "Hey, are you free?",
            category = Notification.CATEGORY_MESSAGE,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = unfilteredApps,
            isDefaultLauncher = true
        )
        assertFalse("Unfiltered immediate app must bypass fast-path cancellation", result)
    }

    @Test
    fun `test ongoing or non-clearable notification bypasses fast cancellation`() {
        val resultOngoing = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.music.player",
            title = "Now Playing",
            text = "Song title",
            category = Notification.CATEGORY_TRANSPORT,
            isClearable = true,
            isOngoing = true,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Ongoing notification must not be fast-cancelled", resultOngoing)

        val resultNotClearable = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.download.manager",
            title = "Downloading file",
            text = "45% completed",
            category = Notification.CATEGORY_PROGRESS,
            isClearable = false,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Non-clearable notification must not be fast-cancelled", resultNotClearable)
    }

    @Test
    fun `test not default launcher bypasses fast cancellation`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.shopping.app",
            title = "Flash Sale",
            text = "Huge discount",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = false
        )
        assertFalse("Should never cancel notifications if not the default launcher", result)
    }

    @Test
    fun `test critical system categories bypass fast cancellation`() {
        val callResult = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.google.android.dialer",
            title = "Incoming Call",
            text = "Mom",
            category = Notification.CATEGORY_CALL,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Incoming call category must bypass fast cancellation", callResult)

        val alarmResult = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.google.android.deskclock",
            title = "Alarm",
            text = "Time to wake up",
            category = Notification.CATEGORY_ALARM,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Alarm category must bypass fast cancellation", alarmResult)

        val eventResult = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.google.android.calendar",
            title = "Meeting in 10 mins",
            text = "Sprint Planning",
            category = Notification.CATEGORY_EVENT,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Calendar event category must bypass fast cancellation", eventResult)
    }

    @Test
    fun `test authentic OTP notifications bypass fast cancellation`() {
        val otp1 = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.unknown.sms",
            title = "Bank Alert",
            text = "Your verification code is 492810. Do not share with anyone.",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Authentic OTP must bypass fast cancellation", otp1)

        val otp2 = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.service.login",
            title = "Login Request",
            text = "Use OTP 829104 to complete sign in",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Action-anchored OTP must bypass fast cancellation", otp2)
    }

    @Test
    fun `test severe weather alerts bypass fast cancellation`() {
        val weatherAlert = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.weather.service",
            title = "Emergency Alert",
            text = "Tornado warning in your area. Seek shelter immediately.",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Severe weather alert must bypass fast cancellation", weatherAlert)
    }

    @Test
    fun `test meeting notifications from meetingAllowedApps bypass fast cancellation`() {
        val meetingAllowed = setOf("us.zoom.videomeetings")
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "us.zoom.videomeetings",
            title = "Daily Standup",
            text = "Starting now",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            meetingAllowedApps = meetingAllowed,
            isDefaultLauncher = true
        )
        assertFalse("App in meetingAllowedApps must bypass fast cancellation to retain heads-up alert", result)
    }

    @Test
    fun `test calendar package notifications bypass fast cancellation`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.google.android.calendar",
            title = "Design Review",
            text = "10:00 - 11:00 AM",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            meetingAllowedApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Calendar package must bypass fast cancellation", result)
    }

    @Test
    fun `test video meeting link notifications bypass fast cancellation`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.some.chat",
            title = "Join call",
            text = "Please join https://meet.google.com/abc-defg-hij",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            meetingAllowedApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Notification containing meeting link must bypass fast cancellation", result)
    }

    @Test
    fun `test meeting keywords combined with schedule keywords bypass fast cancellation`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.company.portal",
            title = "Sprint Planning meeting",
            text = "starts in 10 mins",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            meetingAllowedApps = emptySet(),
            isDefaultLauncher = true
        )
        assertFalse("Meeting keywords + schedule keywords must bypass fast cancellation", result)
    }

    @Test
    fun `test category message from non-unfiltered app is fast cancelled`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.social.network",
            title = "Bob",
            text = "Did you see this photo?",
            category = Notification.CATEGORY_MESSAGE,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            meetingAllowedApps = emptySet(),
            isDefaultLauncher = true
        )
        assertTrue("Non-unfiltered routine message should be fast-cancelled to avoid distraction", result)
    }

    @Test
    fun `test null title and null text handled safely without exception`() {
        val result = NotificationListener.isFastPathCancelCandidate(
            packageName = "com.mystery.app",
            title = null,
            text = null,
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            meetingAllowedApps = emptySet(),
            isDefaultLauncher = true
        )
        assertTrue("Null title and text should be safely processed as cancel candidate", result)
    }

    @Test
    fun `test complete matrix of notificationFilteringEnabled flag behavior`() {
        val routineApp = "com.news.daily"
        val immediateApp = "com.chat.vip"
        val unfilteredSet = setOf(immediateApp)

        // Case 1: notificationFilteringEnabled = false (Fresh installs or users who turned it off)
        // NEITHER routine nor immediate apps must ever be fast cancelled!
        val routineWhenFilterDisabled = NotificationListener.isFastPathCancelCandidate(
            packageName = routineApp,
            title = "Breaking News",
            text = "Markets hit new highs",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = unfilteredSet,
            isDefaultLauncher = true,
            notificationFilteringEnabled = false
        )
        assertFalse("When filter is disabled, routine notification must NOT be fast cancelled (remains in status bar)", routineWhenFilterDisabled)

        val immediateWhenFilterDisabled = NotificationListener.isFastPathCancelCandidate(
            packageName = immediateApp,
            title = "Boss",
            text = "Need this file urgently",
            category = Notification.CATEGORY_MESSAGE,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = unfilteredSet,
            isDefaultLauncher = true,
            notificationFilteringEnabled = false
        )
        assertFalse("When filter is disabled, immediate app notification must NOT be fast cancelled (remains in status bar)", immediateWhenFilterDisabled)

        // Case 2: notificationFilteringEnabled = true (Existing users or users who opted into filtering)
        // Routine apps ARE cancelled (moved to Notification Center), but Immediate apps are spared!
        val routineWhenFilterEnabled = NotificationListener.isFastPathCancelCandidate(
            packageName = routineApp,
            title = "Breaking News",
            text = "Markets hit new highs",
            category = null,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = unfilteredSet,
            isDefaultLauncher = true,
            notificationFilteringEnabled = true
        )
        assertTrue("When filter is enabled, routine notification from non-immediate app must be fast cancelled", routineWhenFilterEnabled)

        val immediateWhenFilterEnabled = NotificationListener.isFastPathCancelCandidate(
            packageName = immediateApp,
            title = "Boss",
            text = "Need this file urgently",
            category = Notification.CATEGORY_MESSAGE,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = unfilteredSet,
            isDefaultLauncher = true,
            notificationFilteringEnabled = true
        )
        assertFalse("When filter is enabled, app listed in immediate apps must BYPASS fast cancellation", immediateWhenFilterEnabled)
    }

    @Test
    fun `test all clearable and ongoing flag permutations with filtering enabled`() {
        val pkg = "com.test.app"

        // clearable = false, ongoing = false -> must bypass
        assertFalse(
            "Non-clearable must never be cancelled",
            NotificationListener.isFastPathCancelCandidate(
                packageName = pkg, title = "Title", text = "Text", category = null,
                isClearable = false, isOngoing = false, unfilteredApps = emptySet(),
                isDefaultLauncher = true, notificationFilteringEnabled = true
            )
        )

        // clearable = true, ongoing = true -> must bypass
        assertFalse(
            "Ongoing must never be cancelled",
            NotificationListener.isFastPathCancelCandidate(
                packageName = pkg, title = "Title", text = "Text", category = null,
                isClearable = true, isOngoing = true, unfilteredApps = emptySet(),
                isDefaultLauncher = true, notificationFilteringEnabled = true
            )
        )

        // clearable = false, ongoing = true -> must bypass
        assertFalse(
            "Non-clearable ongoing must never be cancelled",
            NotificationListener.isFastPathCancelCandidate(
                packageName = pkg, title = "Title", text = "Text", category = null,
                isClearable = false, isOngoing = true, unfilteredApps = emptySet(),
                isDefaultLauncher = true, notificationFilteringEnabled = true
            )
        )

        // clearable = true, ongoing = false -> cancel candidate
        assertTrue(
            "Clearable non-ongoing notification is cancel candidate when filtering enabled",
            NotificationListener.isFastPathCancelCandidate(
                packageName = pkg, title = "Title", text = "Text", category = null,
                isClearable = true, isOngoing = false, unfilteredApps = emptySet(),
                isDefaultLauncher = true, notificationFilteringEnabled = true
            )
        )
    }

    @Test
    fun `test shouldCancelNotification respects notificationFilteringEnabled for sync and live paths`() {
        // Case 1: When notificationFilteringEnabled is FALSE (Bug #1 regression guard)
        // Normal clearable notifications must NOT be cancelled on sync or live reconnect!
        assertFalse(
            "Normal clearable notification must NOT be cancelled when filtering is disabled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.NORMAL,
                isClearable = true,
                isOngoing = false,
                isDefaultLauncher = true,
                notificationFilteringEnabled = false
            )
        )
        assertFalse(
            "Priority notification must NOT be cancelled when filtering is disabled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.PRIORITY,
                isClearable = true,
                isOngoing = false,
                isDefaultLauncher = true,
                notificationFilteringEnabled = false
            )
        )
        assertTrue(
            "Explicitly MUTED notification must be cancelled even when filtering is disabled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.MUTED,
                isClearable = true,
                isOngoing = false,
                isDefaultLauncher = true,
                notificationFilteringEnabled = false
            )
        )

        // Case 2: When notificationFilteringEnabled is TRUE
        assertTrue(
            "Normal clearable notification MUST be cancelled (filtered to center) when filtering is enabled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.NORMAL,
                isClearable = true,
                isOngoing = false,
                isDefaultLauncher = true,
                notificationFilteringEnabled = true
            )
        )
        assertFalse(
            "Priority notification must NOT be cancelled when filtering is enabled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.PRIORITY,
                isClearable = true,
                isOngoing = false,
                isDefaultLauncher = true,
                notificationFilteringEnabled = true
            )
        )
        assertTrue(
            "Muted notification MUST be cancelled when filtering is enabled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.MUTED,
                isClearable = true,
                isOngoing = false,
                isDefaultLauncher = true,
                notificationFilteringEnabled = true
            )
        )

        // Case 3: Invariant guards: Ongoing, Non-clearable, or Not Default Launcher
        assertFalse(
            "Ongoing notification must never be cancelled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.NORMAL,
                isClearable = true,
                isOngoing = true,
                isDefaultLauncher = true,
                notificationFilteringEnabled = true
            )
        )
        assertFalse(
            "Non-clearable notification must never be cancelled",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.NORMAL,
                isClearable = false,
                isOngoing = false,
                isDefaultLauncher = true,
                notificationFilteringEnabled = true
            )
        )
        assertFalse(
            "Notification must never be cancelled if not default launcher",
            NotificationListener.shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.NORMAL,
                isClearable = true,
                isOngoing = false,
                isDefaultLauncher = false,
                notificationFilteringEnabled = true
            )
        )
    }

    @Test
    fun `test MUTED notification with filtering disabled bypasses fast-path and is cancelled on slow-path`() {
        val mutedPkg = "com.noisy.social"

        // 1. Fast-path check: With notificationFilteringEnabled = false, fast-path must ALWAYS return false
        val fastResult = NotificationListener.isFastPathCancelCandidate(
            packageName = mutedPkg,
            title = "Random chatter",
            text = "Someone posted a photo",
            category = Notification.CATEGORY_MESSAGE,
            isClearable = true,
            isOngoing = false,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true,
            notificationFilteringEnabled = false
        )
        assertFalse("When filtering is disabled, fast-path must return false (no early cancellation)", fastResult)

        // 2. Slow-path check: After classification determines the notification is MUTED,
        // shouldCancelNotification must return true to ensure muted notifications are silenced
        val slowResult = NotificationListener.shouldCancelNotification(
            cat = com.alauncher.app.model.FilterCategory.MUTED,
            isClearable = true,
            isOngoing = false,
            isDefaultLauncher = true,
            notificationFilteringEnabled = false
        )
        assertTrue("When filtering is disabled, explicitly MUTED notifications must still be cancelled on slow path", slowResult)
    }
}
