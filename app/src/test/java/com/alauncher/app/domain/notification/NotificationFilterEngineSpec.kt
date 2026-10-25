package com.alauncher.app.domain.notification

import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.LauncherNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationFilterEngineSpec {

    private lateinit var engine: NotificationFilterEngine

    @Before
    fun setUp() {
        engine = NotificationFilterEngine()
    }

    private fun createNotification(
        key: String = "test_key",
        packageName: String = "com.test.app",
        appName: String = "TestApp",
        title: String? = null,
        text: String? = null,
        postTime: Long = System.currentTimeMillis()
    ): LauncherNotification {
        return LauncherNotification(
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            postTime = postTime,
            channelId = "ch_default",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
    }

    @Test
    fun otpNotification_bypassesFocusModeAndQuietHours() {
        val now = System.currentTimeMillis()
        val otpNotif = createNotification(
            packageName = "com.bank.app",
            appName = "Bank",
            title = "OTP Verification",
            text = "Your one-time password is 948271. Valid for 5 minutes.",
            postTime = now
        )

        // With focus mode active
        val focusSettings = SettingsState(
            isFocusModeActive = true,
            focusEndTime = now + 3600_000L
        )
        val cat = engine.classify(otpNotif, focusSettings, now)
        assertEquals("OTP must be promoted to PRIORITY even during Focus Mode", FilterCategory.PRIORITY, cat)
    }

    @Test
    fun promotionalNotification_isMutedDuringFocusMode() {
        val now = System.currentTimeMillis()
        val promoNotif = createNotification(
            packageName = "com.shopping.app",
            appName = "Shop",
            title = "Mega Sale!",
            text = "Flat 50% discount on shoes. Use coupon SALE50.",
            postTime = now
        )

        // Normal mode -> NORMAL
        val normalSettings = SettingsState()
        val normalCat = engine.classify(promoNotif, normalSettings, now)
        assertEquals(FilterCategory.NORMAL, normalCat)

        // Focus mode -> MUTED
        val focusSettings = SettingsState(
            isFocusModeActive = true,
            focusEndTime = now + 3600_000L
        )
        val focusCat = engine.classify(promoNotif, focusSettings, now)
        assertEquals("Promotional notification must be MUTED during Focus Mode", FilterCategory.MUTED, focusCat)
    }

    @Test
    fun unfilteredApp_alwaysBypassesFocusMode() {
        val now = System.currentTimeMillis()
        val notif = createNotification(
            packageName = "com.important.contact",
            appName = "WorkChat",
            title = "Boss",
            text = "Emergency update on release"
        )

        val settings = SettingsState(
            isFocusModeActive = true,
            focusEndTime = now + 3600_000L,
            unfilteredApps = setOf("com.important.contact")
        )

        val cat = engine.classify(notif, settings, now)
        assertEquals("Unfiltered app must bypass Focus Mode with PRIORITY", FilterCategory.PRIORITY, cat)
    }

    @Test
    fun enrichWithMemoizedSignals_cachesStaticContentSignals() {
        val notif = createNotification(
            title = "Special Offer",
            text = "Get flat 40% discount on your purchase"
        )

        val enriched = engine.enrichWithMemoizedSignals(notif)
        assertTrue("Memoized promo signal must be true", enriched.memoizedIsPromo == true)
        assertEquals("Memoized OTP signal must be false", false, enriched.memoizedIsOtp)
    }
}

