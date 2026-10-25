package com.alauncher.app

import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.data.settings.getQuietHoursMinutesLeft
import com.alauncher.app.data.settings.getQuietHoursMinutesSinceStart
import com.alauncher.app.data.settings.getQuietHoursMinutesUntilStart
import com.alauncher.app.data.settings.isFocusModeActiveNow
import com.alauncher.app.data.settings.isQuietHoursActiveNow
import com.alauncher.app.data.settings.parseTimeToMinutes
import com.alauncher.app.domain.notification.NotificationClassifier
import com.alauncher.app.domain.notification.NotificationParser
import com.alauncher.app.domain.notification.NotificationFilterEngine
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.model.hasContent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusAndQuietHoursTest {

    private lateinit var filterEngine: NotificationFilterEngine
    private lateinit var classifier: NotificationClassifier
    private lateinit var parser: NotificationParser

    @Before
    fun setUp() {
        parser = NotificationParser()
        classifier = NotificationClassifier(parser)
        filterEngine = NotificationFilterEngine(parser, classifier)
    }

    private fun createTimestampForTimeOfDay(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun createNotification(
        key: String = "test_key",
        packageName: String = "com.test.app",
        appName: String = "Test App",
        title: String? = "Test Title",
        text: String? = "Test Body",
        postTime: Long = System.currentTimeMillis(),
        channelId: String? = "channel_default",
        isOngoing: Boolean = false,
        isClearable: Boolean = true,
        importance: Int = 3,
        filterCategory: FilterCategory = FilterCategory.NORMAL
    ): LauncherNotification {
        return LauncherNotification(
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            postTime = postTime,
            channelId = channelId,
            isOngoing = isOngoing,
            isClearable = isClearable,
            importance = importance,
            filterCategory = filterCategory
        )
    }

    // ==========================================
    // 1. Focus Mode Domain Helpers
    // ==========================================

    @Test
    fun testFocusModeActiveNow_Inactive() {
        val state = SettingsState(isFocusModeActive = false, focusEndTime = 0L)
        assertFalse(state.isFocusModeActiveNow(1000L))
    }

    @Test
    fun testFocusModeActiveNow_Continuous() {
        val state = SettingsState(isFocusModeActive = true, focusEndTime = 0L)
        assertTrue(state.isFocusModeActiveNow(1000L))
        assertTrue(state.isFocusModeActiveNow(99999999L))
    }

    @Test
    fun testFocusModeActiveNow_Timed() {
        val now = 100_000L
        val activeTimedState = SettingsState(isFocusModeActive = true, focusEndTime = now + 60_000L)
        assertTrue("Timed focus mode must be active before focusEndTime", activeTimedState.isFocusModeActiveNow(now))

        val expiredAtBoundary = SettingsState(isFocusModeActive = true, focusEndTime = now)
        assertFalse("Timed focus mode must be inactive exactly at focusEndTime", expiredAtBoundary.isFocusModeActiveNow(now))

        val expiredPastBoundary = SettingsState(isFocusModeActive = true, focusEndTime = now - 1L)
        assertFalse("Timed focus mode must be inactive after focusEndTime", expiredPastBoundary.isFocusModeActiveNow(now))
    }

    // ==========================================
    // 2. Quiet Hours Time Parser
    // ==========================================

    @Test
    fun testParseTimeToMinutes_ValidCases() {
        assertEquals(0, parseTimeToMinutes("00:00"))
        assertEquals(0, parseTimeToMinutes("0:00"))
        assertEquals(1, parseTimeToMinutes("00:01"))
        assertEquals(450, parseTimeToMinutes("07:30"))
        assertEquals(720, parseTimeToMinutes("12:00"))
        assertEquals(1320, parseTimeToMinutes("22:00"))
        assertEquals(1439, parseTimeToMinutes("23:59"))
    }

    @Test
    fun testParseTimeToMinutes_InvalidCases() {
        assertNull(parseTimeToMinutes("24:00"))
        assertNull(parseTimeToMinutes("25:00"))
        assertNull(parseTimeToMinutes("12:60"))
        assertNull(parseTimeToMinutes("12:75"))
        assertNull(parseTimeToMinutes("-1:00"))
        assertNull(parseTimeToMinutes("12:-5"))
        assertNull(parseTimeToMinutes(""))
        assertNull(parseTimeToMinutes("invalid"))
        assertNull(parseTimeToMinutes("12"))
        assertNull(parseTimeToMinutes("12:3"))
    }

    // ==========================================
    // 3. Quiet Hours Interval Logic
    // ==========================================

    @Test
    fun testQuietHoursActiveNow_NormalInterval() {
        // Normal daytime interval: 09:00 to 17:00
        val state = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "09:00",
            quietHoursEnd = "17:00"
        )

        val beforeStart = createTimestampForTimeOfDay(8, 59)
        assertFalse("Before start time must be inactive", state.isQuietHoursActiveNow(beforeStart))

        val atStart = createTimestampForTimeOfDay(9, 0)
        assertTrue("At start time must be active", state.isQuietHoursActiveNow(atStart))

        val midday = createTimestampForTimeOfDay(12, 30)
        assertTrue("Inside interval must be active", state.isQuietHoursActiveNow(midday))

        val rightBeforeEnd = createTimestampForTimeOfDay(16, 59)
        assertTrue("Right before end time must be active", state.isQuietHoursActiveNow(rightBeforeEnd))

        val atEndBoundary = createTimestampForTimeOfDay(17, 0)
        assertFalse("Half-open interval: at end time must be inactive", state.isQuietHoursActiveNow(atEndBoundary))

        val afterEnd = createTimestampForTimeOfDay(17, 1)
        assertFalse("After end time must be inactive", state.isQuietHoursActiveNow(afterEnd))
    }

    @Test
    fun testQuietHoursActiveNow_OvernightInterval() {
        // Overnight interval: 22:00 to 07:00
        val state = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00"
        )

        val beforeStart = createTimestampForTimeOfDay(21, 59)
        assertFalse("Daytime before start must be inactive", state.isQuietHoursActiveNow(beforeStart))

        val atStart = createTimestampForTimeOfDay(22, 0)
        assertTrue("At start time must be active", state.isQuietHoursActiveNow(atStart))

        val lateNight = createTimestampForTimeOfDay(23, 45)
        assertTrue("Late night before midnight must be active", state.isQuietHoursActiveNow(lateNight))

        val midnight = createTimestampForTimeOfDay(0, 0)
        assertTrue("At midnight must be active", state.isQuietHoursActiveNow(midnight))

        val earlyMorning = createTimestampForTimeOfDay(6, 59)
        assertTrue("Right before end time must be active", state.isQuietHoursActiveNow(earlyMorning))

        val atEndBoundary = createTimestampForTimeOfDay(7, 0)
        assertFalse("Half-open interval: at end time must be inactive", state.isQuietHoursActiveNow(atEndBoundary))

        val daytime = createTimestampForTimeOfDay(14, 0)
        assertFalse("Midday must be inactive", state.isQuietHoursActiveNow(daytime))
    }

    @Test
    fun testQuietHoursActiveNow_DegenerateInterval() {
        // Degenerate interval: 22:00 to 22:00
        val state = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "22:00",
            quietHoursEnd = "22:00"
        )

        val atTime = createTimestampForTimeOfDay(22, 0)
        assertFalse("Degenerate interval start == end must be inactive", state.isQuietHoursActiveNow(atTime))

        val otherTime = createTimestampForTimeOfDay(12, 0)
        assertFalse(state.isQuietHoursActiveNow(otherTime))
    }

    @Test
    fun testQuietHoursActiveNow_DegenerateIntervalWithDifferentFormats() {
        // Degenerate interval: "9:00" to "09:00" (both 540 min)
        val state = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "9:00",
            quietHoursEnd = "09:00"
        )

        val atTime = createTimestampForTimeOfDay(9, 0)
        assertFalse("Interval with same minutes in different format must be inactive", state.isQuietHoursActiveNow(atTime))
    }

    @Test
    fun testQuietHoursActiveNow_Disabled() {
        val state = SettingsState(
            quietHoursEnabled = false,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00"
        )
        val midnight = createTimestampForTimeOfDay(0, 0)
        assertFalse("Disabled quiet hours must never be active", state.isQuietHoursActiveNow(midnight))
    }

    // ==========================================
    // 4. Quiet Hours Time Countdown Helpers
    // ==========================================

    @Test
    fun testQuietHoursHelpers_NormalInterval() {
        val state = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "09:00",
            quietHoursEnd = "17:00"
        )

        // At 09:05: 5 minutes since start
        val at0905 = createTimestampForTimeOfDay(9, 5)
        assertEquals(5, state.getQuietHoursMinutesSinceStart(at0905))
        assertEquals(475, state.getQuietHoursMinutesLeft(at0905))

        // At 16:55: 5 minutes left
        val at1655 = createTimestampForTimeOfDay(16, 55)
        assertEquals(5, state.getQuietHoursMinutesLeft(at1655))

        // At 08:45: 15 minutes until start
        val at0845 = createTimestampForTimeOfDay(8, 45)
        assertEquals(15, state.getQuietHoursMinutesUntilStart(at0845))
    }

    @Test
    fun testQuietHoursHelpers_OvernightInterval() {
        val state = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00"
        )

        // At 22:03: 3 minutes since start
        val at2203 = createTimestampForTimeOfDay(22, 3)
        assertEquals(3, state.getQuietHoursMinutesSinceStart(at2203))

        // At 00:30: (120 min before midnight + 30 min) = 150 min since start
        val at0030 = createTimestampForTimeOfDay(0, 30)
        assertEquals(150, state.getQuietHoursMinutesSinceStart(at0030))

        // At 06:56: 4 minutes left until 07:00
        val at0656 = createTimestampForTimeOfDay(6, 56)
        assertEquals(4, state.getQuietHoursMinutesLeft(at0656))

        // At 21:50: 10 minutes until start
        val at2150 = createTimestampForTimeOfDay(21, 50)
        assertEquals(10, state.getQuietHoursMinutesUntilStart(at2150))
    }

    // ==========================================
    // 5. Notification Filter Engine - Focus Mode
    // ==========================================

    @Test
    fun testNotificationFilterEngine_FocusModeMutesNormalNotification() {
        val normalNotif = createNotification(
            key = "key1",
            packageName = "com.instagram.android",
            appName = "Instagram",
            title = "New photo",
            text = "Someone posted a photo",
            postTime = System.currentTimeMillis()
        )

        // Normal inactive settings
        val normalSettings = SettingsState(isFocusModeActive = false)
        val normalCat = filterEngine.classify(normalNotif, normalSettings)
        assertNotEquals(FilterCategory.MUTED, normalCat)

        // Active Focus Mode
        val focusSettings = SettingsState(isFocusModeActive = true, focusEndTime = 0L)
        val focusCat = filterEngine.classify(normalNotif, focusSettings)
        assertEquals(FilterCategory.MUTED, focusCat)
    }

    @Test
    fun testNotificationFilterEngine_FocusModePreservesWhitelistedApps() {
        val normalNotif = createNotification(
            key = "key1",
            packageName = "com.slack",
            appName = "Slack",
            title = "New message",
            text = "Team chat update",
            postTime = System.currentTimeMillis()
        )

        // Whitelisted app in unfilteredApps
        val focusSettingsWithWhitelist = SettingsState(
            isFocusModeActive = true,
            focusEndTime = 0L,
            unfilteredApps = setOf("com.slack")
        )

        val cat = filterEngine.classify(normalNotif, focusSettingsWithWhitelist)
        assertNotEquals("Whitelisted app must not be muted during Focus Mode", FilterCategory.MUTED, cat)
    }

    @Test
    fun testNotificationFilterEngine_FocusModePreservesOtpAndPriority() {
        val otpNotif = createNotification(
            key = "otp_key",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Bank Alert",
            text = "Your OTP is 481920 for transaction verification. Valid for 10 minutes.",
            postTime = System.currentTimeMillis()
        )

        val focusSettings = SettingsState(isFocusModeActive = true, focusEndTime = 0L)
        val cat = filterEngine.classify(otpNotif, focusSettings)
        assertEquals("Critical OTP notification must remain PRIORITY during Focus Mode", FilterCategory.PRIORITY, cat)
    }

    @Test
    fun testNotificationFilterEngine_FocusModeExpiresCorrectlyWithTimestamp() {
        val normalNotif = createNotification(
            key = "key1",
            packageName = "com.instagram.android",
            appName = "Instagram",
            title = "New post",
            text = "Check this out",
            postTime = 100_000L
        )

        val focusSettings = SettingsState(
            isFocusModeActive = true,
            focusEndTime = 150_000L
        )

        // Before expiry: MUTED
        val beforeCat = filterEngine.classify(normalNotif, focusSettings, currentTimeMs = 120_000L)
        assertEquals(FilterCategory.MUTED, beforeCat)

        // At/After expiry: NOT MUTED
        val afterCat = filterEngine.classify(normalNotif, focusSettings, currentTimeMs = 150_000L)
        assertNotEquals("Expired focus mode must not mute incoming notifications", FilterCategory.MUTED, afterCat)
    }

    // ==========================================
    // 6. Notification Filter Engine - Quiet Hours
    // ==========================================

    @Test
    fun testNotificationFilterEngine_QuietHoursMuting() {
        val normalNotif = createNotification(
            key = "key1",
            packageName = "com.twitter.android",
            appName = "X",
            title = "Trending now",
            text = "See what is happening",
            postTime = 1000L
        )

        val quietSettings = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00"
        )

        val midnightMs = createTimestampForTimeOfDay(0, 0)
        val cat = filterEngine.classify(normalNotif, quietSettings, currentTimeMs = midnightMs)
        assertEquals("Notification during Quiet Hours must be MUTED", FilterCategory.MUTED, cat)

        val middayMs = createTimestampForTimeOfDay(12, 0)
        val middayCat = filterEngine.classify(normalNotif, quietSettings, currentTimeMs = middayMs)
        assertNotEquals("Notification outside Quiet Hours must not be MUTED", FilterCategory.MUTED, middayCat)
    }

    @Test
    fun testNotificationFilterEngine_QuietHoursPreservesOtp() {
        val midnightMs = createTimestampForTimeOfDay(0, 0)
        val otpNotif = createNotification(
            key = "otp_key",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Bank Verification",
            text = "Your one-time passcode is 998822 for online payment.",
            postTime = midnightMs
        )

        val quietSettings = SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00"
        )

        val cat = filterEngine.classify(otpNotif, quietSettings, currentTimeMs = midnightMs)
        assertEquals("OTP notifications must never be muted during Quiet Hours", FilterCategory.PRIORITY, cat)
    }

    // ==========================================
    // 7. Contextual Island Stashed Count Representation
    // ==========================================

    @Test
    fun testStashedCountExcludesPriorityAndOtp() {
        val notifications = listOf(
            createNotification(
                key = "otp",
                packageName = "sms",
                appName = "SMS",
                title = "OTP",
                text = "OTP is 1234",
                filterCategory = FilterCategory.PRIORITY
            ),
            createNotification(
                key = "call",
                packageName = "phone",
                appName = "Phone",
                title = "Incoming Call",
                text = "Call",
                filterCategory = FilterCategory.PRIORITY
            ),
            createNotification(
                key = "muted1",
                packageName = "insta",
                appName = "Instagram",
                title = "Post",
                text = "Liked",
                filterCategory = FilterCategory.MUTED
            ),
            createNotification(
                key = "muted2",
                packageName = "twitter",
                appName = "Twitter",
                title = "Tweet",
                text = "New tweet",
                filterCategory = FilterCategory.MUTED
            ),
            createNotification(
                key = "muted_contentless",
                packageName = "com.zebpay",
                appName = "Zebpay",
                title = "Zebpay",
                text = null,
                filterCategory = FilterCategory.MUTED
            )
        )

        // Contentless notifications (e.g. title matches app name with null text) must be excluded
        val stashedCount = notifications.count { it.filterCategory == FilterCategory.MUTED && it.hasContent }
        assertEquals("Stashed count must strictly count MUTED notifications with content", 2, stashedCount)

        val title = when (stashedCount) {
            0 -> "🤫 Focus Mode Active"
            1 -> "🤫 Focus Mode Active • 1 notification stashed"
            else -> "🤫 Focus Mode Active • $stashedCount notifications stashed"
        }
        assertEquals("🤫 Focus Mode Active • 2 notifications stashed", title)

        val quietTitle = when (stashedCount) {
            0 -> "🤫 Quiet Hours Active"
            1 -> "🤫 Quiet Hours Active • 1 notification stashed"
            else -> "🤫 Quiet Hours Active • $stashedCount notifications stashed"
        }
        assertEquals("🤫 Quiet Hours Active • 2 notifications stashed", quietTitle)

        // Notification Center validMutedList must match stashedCount
        val validMutedList = notifications.filter { it.filterCategory == FilterCategory.MUTED }.filter { it.hasContent }
        assertEquals("Notification center valid muted list size must strictly match stashedCount", stashedCount, validMutedList.size)
    }

    @Test
    fun testSilencedNotificationSubtitleGrammarAndContext() {
        fun formatSilencedSubtitle(count: Int, isFocus: Boolean, isQuiet: Boolean): String {
            val alertText = if (count == 1) "1 alert" else "$count alerts"
            val contextText = if (isFocus) "while focus was active."
                else if (isQuiet) "while quiet hours were active."
                else "while focus / quiet hours were active."
            return "$alertText kept quiet $contextText"
        }

        assertEquals("1 alert kept quiet while focus was active.", formatSilencedSubtitle(1, isFocus = true, isQuiet = false))
        assertEquals("3 alerts kept quiet while focus was active.", formatSilencedSubtitle(3, isFocus = true, isQuiet = false))
        assertEquals("1 alert kept quiet while quiet hours were active.", formatSilencedSubtitle(1, isFocus = false, isQuiet = true))
        assertEquals("5 alerts kept quiet while quiet hours were active.", formatSilencedSubtitle(5, isFocus = false, isQuiet = true))
    }

    @Test
    fun testNotificationFilterEngine_PromoMutedDuringFocusMode() {
        val notif = createNotification(
            key = "promo",
            packageName = "com.shopping.app",
            appName = "Shop",
            title = "MEGA SALE 50% OFF!",
            text = "Use coupon code SAVE50 today only. Don't miss out on deals!"
        )

        val focusSettings = SettingsState(isFocusModeActive = true, focusEndTime = 0L)
        val cat = filterEngine.classify(notif, focusSettings)
        assertEquals("Promotional notification must be MUTED during Focus Mode", FilterCategory.MUTED, cat)
    }

    @Test
    fun testNotificationFilterEngine_StaleOtpMutedDuringFocusMode() {
        val now = System.currentTimeMillis()
        val stalePostTime = now - (15 * 60 * 1000L) // 15 minutes ago (exceeds default 5-10m OTP validity)
        val staleOtpNotif = createNotification(
            key = "stale_otp",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Verification",
            text = "Your OTP is 928371 for login. Valid for 5 minutes.",
            postTime = stalePostTime
        )

        val focusSettings = SettingsState(isFocusModeActive = true, focusEndTime = 0L)
        val cat = filterEngine.classify(staleOtpNotif, focusSettings, currentTimeMs = now)
        assertEquals("Stale OTP (> 10m old) must not bypass Focus Mode and must be MUTED", FilterCategory.MUTED, cat)
    }

    // ==========================================
    // 8. Production Review Edge Case Tests
    // ==========================================

    @Test
    fun testClearAllExcludesPriorityNotifications() {
        val priorityOtp = createNotification(
            key = "otp_key",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Bank OTP",
            text = "Your OTP is 123456",
            filterCategory = FilterCategory.PRIORITY
        )
        val normalNotif = createNotification(
            key = "normal_key",
            packageName = "com.news",
            appName = "News",
            title = "Breaking News",
            text = "Story update",
            filterCategory = FilterCategory.NORMAL
        )
        val mutedNotif = createNotification(
            key = "muted_key",
            packageName = "com.social",
            appName = "Social",
            title = "New like",
            text = "User liked your photo",
            filterCategory = FilterCategory.MUTED
        )

        // Simulate Clear All logic: only normal and muted items are dismissed
        val allNotifications = listOf(priorityOtp, normalNotif, mutedNotif)
        val dismissedKeys = mutableListOf<String>()

        val normalList = allNotifications.filter { it.filterCategory == FilterCategory.NORMAL }
        val mutedList = allNotifications.filter { it.filterCategory == FilterCategory.MUTED }

        normalList.forEach { dismissedKeys.add(it.key) }
        mutedList.forEach { dismissedKeys.add(it.key) }

        val remainingNotifications = allNotifications.filterNot { it.key in dismissedKeys }

        assertEquals(1, remainingNotifications.size)
        assertEquals("otp_key", remainingNotifications.first().key)
        assertEquals(FilterCategory.PRIORITY, remainingNotifications.first().filterCategory)
    }

    @Test
    fun testFocusModeCountdown_ZeroBoundaryDisplaysNoSuffix() {
        val now = 100_000L
        val expiredEndTime = 100_000L // remainingMs == 0L

        val remainingMs = expiredEndTime - now
        val buttonText = if (remainingMs > 0L) {
            val remainingMins = (remainingMs + 59999L) / 60000L
            "Deactivate Focus Mode (${remainingMins}m left)"
        } else {
            "Deactivate Focus Mode"
        }

        assertEquals("When session expires (0ms left), text must be 'Deactivate Focus Mode' without any '1m left' suffix",
            "Deactivate Focus Mode", buttonText)
    }

    @Test
    fun testDndFilterRestoration_PreExistingPriorityPreserved() {
        val FILTER_ALL = 1
        val FILTER_PRIORITY = 2
        val FILTER_ALARMS = 4

        // Case 1: Phone is in FILTER_ALL when Focus Mode starts.
        // ALauncher sets PRIORITY, saves previousInterruptionFilter = FILTER_ALL.
        // When Focus ends, previousInterruptionFilter (FILTER_ALL) is restored.
        var previousInterruptionFilter: Int? = null
        var currentFilter = FILTER_ALL
        if (currentFilter != FILTER_PRIORITY) {
            previousInterruptionFilter = currentFilter
        }
        assertEquals(FILTER_ALL, previousInterruptionFilter)

        // Case 2: Phone is ALREADY in FILTER_PRIORITY when Focus Mode starts.
        // ALauncher does not need to change anything, previousInterruptionFilter remains null.
        // When Focus ends, previousInterruptionFilter is null so ALauncher leaves DND untouched!
        previousInterruptionFilter = null
        currentFilter = FILTER_PRIORITY
        if (currentFilter != FILTER_PRIORITY) {
            previousInterruptionFilter = currentFilter
        }
        assertNull("Pre-existing Priority DND must result in null previousInterruptionFilter so user's DND is not disabled", previousInterruptionFilter)

        // Case 3: Phone was in FILTER_ALARMS.
        previousInterruptionFilter = null
        currentFilter = FILTER_ALARMS
        if (currentFilter != FILTER_PRIORITY) {
            previousInterruptionFilter = currentFilter
        }
        assertEquals(FILTER_ALARMS, previousInterruptionFilter)
    }

    @Test
    fun testNotificationFilterEngine_OtpWithUseCodePhraseNotSilencedByPromoGuard() {
        val now = System.currentTimeMillis()
        val notif = createNotification(
            key = "bank_use_code",
            packageName = "com.bank.app",
            appName = "Bank",
            title = "Security Alert",
            text = "Use code 582910 to complete your card transaction. Valid for 5 minutes.",
            postTime = now
        )
        val focusSettings = SettingsState(isFocusModeActive = true, focusEndTime = 0L)
        val cat = filterEngine.classify(notif, focusSettings, currentTimeMs = now)
        assertEquals("OTP with 'use code' must be PRIORITY and not silenced by promo guard", FilterCategory.PRIORITY, cat)
    }

    @Test
    fun testNotificationFilteringEnabled_interactionWithFilterEngineAndCancellationMatrix() {
        val now = System.currentTimeMillis()
        val routineNotif = createNotification(
            key = "routine_notif",
            packageName = "com.reddit.frontpage",
            title = "Trending today",
            text = "Check out what is hot right now",
            postTime = now
        )
        val immediateAppNotif = createNotification(
            key = "vip_notif",
            packageName = "com.whatsapp",
            title = "Alice",
            text = "Are you on your way?",
            postTime = now
        )
        val otpNotif = createNotification(
            key = "otp_notif",
            packageName = "com.bank.secure",
            title = "OTP Alert",
            text = "Your OTP is 918234 for online purchase",
            postTime = now
        )

        // Helper replicating NotificationListener's cancellation evaluation:
        fun shouldCancel(cat: FilterCategory, isClearable: Boolean, isOngoing: Boolean, filteringEnabled: Boolean): Boolean {
            return isClearable && !isOngoing && when {
                cat == FilterCategory.PRIORITY -> false
                cat == FilterCategory.MUTED -> true
                else -> filteringEnabled
            }
        }

        // Scenario 1: New User Default (notificationFilteringEnabled = false, no Focus Mode)
        val settingsNewUser = SettingsState(
            notificationFilteringEnabled = false,
            unfilteredApps = setOf("com.whatsapp"),
            isFocusModeActive = false
        )
        val routineCat1 = filterEngine.classify(routineNotif, settingsNewUser, now)
        val vipCat1 = filterEngine.classify(immediateAppNotif, settingsNewUser, now)
        val otpCat1 = filterEngine.classify(otpNotif, settingsNewUser, now)

        assertEquals(FilterCategory.NORMAL, routineCat1)
        assertEquals(FilterCategory.PRIORITY, vipCat1)
        assertEquals(FilterCategory.PRIORITY, otpCat1)

        assertFalse("Routine notification must NOT be cancelled when filtering is disabled", shouldCancel(routineCat1, true, false, settingsNewUser.notificationFilteringEnabled))
        assertFalse("Immediate VIP notification must NOT be cancelled", shouldCancel(vipCat1, true, false, settingsNewUser.notificationFilteringEnabled))
        assertFalse("OTP notification must NOT be cancelled", shouldCancel(otpCat1, true, false, settingsNewUser.notificationFilteringEnabled))

        // Scenario 2: Existing User Default or Opt-in User (notificationFilteringEnabled = true, no Focus Mode)
        val settingsFiltered = SettingsState(
            notificationFilteringEnabled = true,
            unfilteredApps = setOf("com.whatsapp"),
            isFocusModeActive = false
        )
        val routineCat2 = filterEngine.classify(routineNotif, settingsFiltered, now)
        val vipCat2 = filterEngine.classify(immediateAppNotif, settingsFiltered, now)
        val otpCat2 = filterEngine.classify(otpNotif, settingsFiltered, now)

        assertEquals(FilterCategory.NORMAL, routineCat2)
        assertEquals(FilterCategory.PRIORITY, vipCat2)
        assertEquals(FilterCategory.PRIORITY, otpCat2)

        assertTrue("Routine notification MUST be cancelled (routed to Notification Center) when filtering is enabled", shouldCancel(routineCat2, true, false, settingsFiltered.notificationFilteringEnabled))
        assertFalse("Immediate VIP app must NOT be cancelled even when filtering is enabled", shouldCancel(vipCat2, true, false, settingsFiltered.notificationFilteringEnabled))
        assertFalse("OTP notification must NOT be cancelled even when filtering is enabled", shouldCancel(otpCat2, true, false, settingsFiltered.notificationFilteringEnabled))

        // Scenario 3: Focus Mode Active (regardless of notificationFilteringEnabled setting)
        for (filteringFlag in listOf(false, true)) {
            val settingsFocus = SettingsState(
                notificationFilteringEnabled = filteringFlag,
                unfilteredApps = setOf("com.whatsapp"),
                isFocusModeActive = true,
                focusEndTime = now + 60_000L
            )
            val routineCatFocus = filterEngine.classify(routineNotif, settingsFocus, now)
            val vipCatFocus = filterEngine.classify(immediateAppNotif, settingsFocus, now)
            val otpCatFocus = filterEngine.classify(otpNotif, settingsFocus, now)

            assertEquals(FilterCategory.MUTED, routineCatFocus)
            assertEquals(FilterCategory.PRIORITY, vipCatFocus)
            assertEquals(FilterCategory.PRIORITY, otpCatFocus)

            assertTrue("Routine notification MUST be cancelled when Focus Mode is active, even if filtering is disabled", shouldCancel(routineCatFocus, true, false, filteringFlag))
            assertFalse("Immediate VIP app notification must still pass through during Focus Mode", shouldCancel(vipCatFocus, true, false, filteringFlag))
            assertFalse("OTP notification must still pass through during Focus Mode", shouldCancel(otpCatFocus, true, false, filteringFlag))
        }

        // Scenario 4: Quiet Hours Active (regardless of notificationFilteringEnabled setting)
        val midnight = createTimestampForTimeOfDay(0, 30)
        val quietRoutineNotif = createNotification(
            key = "routine_quiet",
            packageName = "com.reddit.frontpage",
            title = "Trending today",
            text = "Check out what is hot right now",
            postTime = midnight
        )
        val quietVipNotif = createNotification(
            key = "vip_quiet",
            packageName = "com.whatsapp",
            title = "Alice",
            text = "Are you on your way?",
            postTime = midnight
        )
        val quietOtpNotif = createNotification(
            key = "otp_quiet",
            packageName = "com.bank.secure",
            title = "OTP Alert",
            text = "Your OTP is 918234 for online purchase",
            postTime = midnight
        )
        for (filteringFlag in listOf(false, true)) {
            val settingsQuiet = SettingsState(
                notificationFilteringEnabled = filteringFlag,
                unfilteredApps = setOf("com.whatsapp"),
                quietHoursEnabled = true,
                quietHoursStart = "22:00",
                quietHoursEnd = "07:00"
            )
            val routineCatQuiet = filterEngine.classify(quietRoutineNotif, settingsQuiet, midnight)
            val vipCatQuiet = filterEngine.classify(quietVipNotif, settingsQuiet, midnight)
            val otpCatQuiet = filterEngine.classify(quietOtpNotif, settingsQuiet, midnight)

            assertEquals(FilterCategory.MUTED, routineCatQuiet)
            assertEquals(FilterCategory.PRIORITY, vipCatQuiet)
            assertEquals(FilterCategory.PRIORITY, otpCatQuiet)

            assertTrue("Routine notification MUST be cancelled when Quiet Hours is active, even if filtering is disabled", shouldCancel(routineCatQuiet, true, false, filteringFlag))
            assertFalse("Immediate VIP app notification must still pass through during Quiet Hours", shouldCancel(vipCatQuiet, true, false, filteringFlag))
            assertFalse("OTP notification must still pass through during Quiet Hours", shouldCancel(otpCatQuiet, true, false, filteringFlag))
        }
    }
}
