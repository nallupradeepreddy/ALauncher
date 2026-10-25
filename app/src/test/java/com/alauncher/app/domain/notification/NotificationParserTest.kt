package com.alauncher.app.domain.notification

import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.ui.home.TransactionAlertType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationParserTest {

    private lateinit var parser: NotificationParser
    private lateinit var classifier: NotificationClassifier
    private lateinit var settings: SettingsState

    @Before
    fun setUp() {
        parser = NotificationParser()
        classifier = NotificationClassifier(parser)
        settings = SettingsState(
            enabledIslandCategories = setOf(
                "OTP", "CRITICAL_ALERT", "UPCOMING_MEETING",
                "HIGH_PRIORITY_TASK", "RIDE_DELIVERY", "AMBIENT_STATE",
                "GENERAL_NOTIFICATION", "AMBIENT_INFO", "BILL_TRANSACTIONAL"
            ),
            transactionAllowedApps = IslandTrustedApps.DEFAULT_TRANSACTION_APPS,
            deliveryAllowedApps = IslandTrustedApps.DEFAULT_DELIVERY_APPS,
            meetingAllowedApps = IslandTrustedApps.DEFAULT_MEETING_APPS
        )
    }

    private fun createNotif(
        key: String,
        packageName: String,
        title: String?,
        text: String?,
        subText: String? = null,
        bigText: String? = null,
        postTime: Long = System.currentTimeMillis()
    ): LauncherNotification {
        return LauncherNotification(
            key = key,
            packageName = packageName,
            appName = packageName.substringAfterLast("."),
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            postTime = postTime,
            channelId = "default",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
    }

    // --- 1. OTP Tests ---

    @Test
    fun `test OTP extracted from Zoom sign in notification`() {
        val notif = createNotif(
            key = "zoom_otp_1",
            packageName = "us.zoom.videomeetings",
            title = "Zoom Sign-In",
            text = "Your Zoom sign-in code is 483920. Do not share."
        )
        val otp = parser.extractOtp(notif)
        assertEquals("483920", otp)
    }

    @Test
    fun `test OTP extracted from Teams authentication notification`() {
        val notif = createNotif(
            key = "teams_otp_1",
            packageName = "com.microsoft.teams",
            title = "Microsoft Security",
            text = "Use 739201 as your verification code to log in."
        )
        val otp = parser.extractOtp(notif)
        assertEquals("739201", otp)
    }

    @Test
    fun `test OTP not extracted from meeting invitation passcode`() {
        val notif = createNotif(
            key = "meet_passcode_1",
            packageName = "us.zoom.videomeetings",
            title = "Weekly Sync Meeting",
            text = "Join Zoom Meeting: https://zoom.us/j/123456 Passcode: 884920"
        )
        val otp = parser.extractOtp(notif)
        assertNull("Meeting passcodes inside invitation links should not be parsed as login OTPs", otp)
    }

    @Test
    fun `test non English OTP keywords extraction`() {
        val notifHindi = createNotif(
            key = "hi_otp",
            packageName = "com.google.android.apps.messaging",
            title = "बैंक",
            text = "आपका सत्यापन कोड 884920 है।"
        )
        assertEquals("884920", parser.extractOtp(notifHindi))

        val notifSpanish = createNotif(
            key = "es_otp",
            packageName = "com.google.android.apps.messaging",
            title = "Banco",
            text = "Su código de verificación es 192847"
        )
        assertEquals("192847", parser.extractOtp(notifSpanish))
    }

    // --- 2. Meeting Detection Tests ---

    @Test
    fun `test meeting detected with unicode hyphen stand-up and global terms`() {
        val notif = createNotif(
            key = "meeting_1",
            packageName = "com.google.android.calendar",
            title = "Daily Stand‑up", // unicode hyphen \u2011
            text = "Starting in 10 mins"
        )
        assertTrue(parser.isCalendarReminderNotification(notif, settings.meetingAllowedApps))
    }

    @Test
    fun `test meeting detected with international terms`() {
        val notifReunion = createNotif(
            key = "meeting_es",
            packageName = "com.google.android.calendar",
            title = "Reunión de equipo",
            text = "Starts at 10:00 AM"
        )
        assertTrue(parser.isCalendarReminderNotification(notifReunion, settings.meetingAllowedApps))

        val notifZh = createNotif(
            key = "meeting_zh",
            packageName = "com.google.android.calendar",
            title = "周会 阶段总结会议",
            text = "Starting now"
        )
        assertTrue(parser.isCalendarReminderNotification(notifZh, settings.meetingAllowedApps))
    }

    @Test
    fun `test RE and FW email threads in bigText rejected as calendar reminders`() {
        val notifFw = createNotif(
            key = "email_fw",
            packageName = "com.microsoft.office.outlook",
            title = "Project Review",
            subText = "Outlook Mail",
            bigText = "FW: Project Review Discussion Notes",
            text = "See attached notes from last week"
        )
        assertFalse("Forwarded email threads in bigText should not surface as calendar reminders", parser.isCalendarReminderNotification(notifFw, settings.meetingAllowedApps))
    }

    // --- 3. Severe Weather Detection ---

    @Test
    fun `test severe weather alert regex matching`() {
        assertTrue(NotificationParser.severeWeatherAlertRegex.containsMatchIn("Flash Flood Warning issued for your area"))
        assertTrue(NotificationParser.severeWeatherAlertRegex.containsMatchIn("Tornado alert: Seek shelter immediately"))
    }

    // --- 4. Transaction & Low Balance Tests ---

    @Test
    fun `test account balance is salary SMS NOT misclassified as low balance`() {
        val notif = createNotif(
            key = "bank_salary_1",
            packageName = "com.snapwork.hdfc",
            title = "Salary Credited",
            text = "Your account balance is Rs 4,52,000 after credit of Rs 1,50,000."
        )
        val details = parser.parseTransactionDetails(notif, settings)
        assertNotNull(details)
        assertNotEquals("Salary credit with account balance is must NOT be classified as LOW_BALANCE", TransactionAlertType.LOW_BALANCE, details?.type)
        assertEquals(TransactionAlertType.CREDIT, details?.type)
    }

    @Test
    fun `test legitimate low balance alert IS correctly classified`() {
        val notif = createNotif(
            key = "bank_low_1",
            packageName = "com.snapwork.hdfc",
            title = "Low Balance Alert",
            text = "Your account balance is low. Available balance below Rs 500."
        )
        val details = parser.parseTransactionDetails(notif, settings)
        assertNotNull(details)
        assertEquals(TransactionAlertType.LOW_BALANCE, details?.type)
    }

    // --- 5. Delivery & Courier Tests ---

    @Test
    fun `test payment is successful is NOT matched as courier status`() {
        val notif = createNotif(
            key = "amazon_pay_1",
            packageName = "in.amazon.mShop.android.shopping",
            title = "Amazon Pay",
            text = "Payment is successful for order #123-456"
        )
        val intent = classifier.classify(notif, settings)
        assertNotEquals("Payment is successful should NOT match DELIVERY intent", NotificationIntent.DELIVERY, intent.intent)
    }

    @Test
    fun `test delivery attempt successful IS matched as courier status`() {
        val notif = createNotif(
            key = "amazon_del_1",
            packageName = "in.amazon.mShop.android.shopping",
            title = "Amazon Delivery",
            text = "Delivery attempt successful for package #99812"
        )
        val intent = classifier.classify(notif, settings)
        assertEquals(NotificationIntent.DELIVERY, intent.intent)
    }

    @Test
    fun `test promotional biryani near you is NOT matched as strict delivery state`() {
        val notif = createNotif(
            key = "zomato_promo_1",
            packageName = "com.application.zomato",
            title = "Hungry?",
            text = "Delicious biryani near you! Order now and get 50% off."
        )
        val intent = classifier.classify(notif, settings)
        assertNotEquals(NotificationIntent.DELIVERY, intent.intent)
    }

    // --- 6. Trusted App Allowlist Verification ---

    @Test
    fun `test newly added banking and delivery packages are present in trusted allowlists`() {
        assertTrue(IslandTrustedApps.DEFAULT_TRANSACTION_APPS.contains("com.yesbank"))
        assertTrue(IslandTrustedApps.DEFAULT_TRANSACTION_APPS.contains("com.epifi.pep"))
        assertTrue(IslandTrustedApps.DEFAULT_TRANSACTION_APPS.contains("com.onedebit.chime"))
        assertTrue(IslandTrustedApps.DEFAULT_TRANSACTION_APPS.contains("de.number26.android"))

        assertTrue(IslandTrustedApps.DEFAULT_DELIVERY_APPS.contains("sinet.startup.inDriver"))
        assertTrue(IslandTrustedApps.DEFAULT_DELIVERY_APPS.contains("com.jiomart"))
        assertTrue(IslandTrustedApps.DEFAULT_DELIVERY_APPS.contains("com.aramex.customer"))
        assertTrue(IslandTrustedApps.DEFAULT_DELIVERY_APPS.contains("com.hungerstation.android"))
    }

    // --- 7. E-Commerce Promo Leak Prevention Tests ---

    @Test
    fun `test Pepperfry flash hour promo with crack code and timer is NOT parsed as OTP`() {
        val notif = createNotif(
            key = "pepperfry_promo_1",
            packageName = "com.pepperfry.shopping",
            title = "⚡ Flash Hour Is LIVE! ⏰",
            text = "Crack Code: E*D**26 | Grab Office Chair at Just Rs.80 | Ends at 5 PM 1:50:45"
        )
        val otp = parser.extractOtp(notif)
        assertNull("Pepperfry promo notifications with promo codes and timers should NOT extract OTP", otp)
        val classified = classifier.classify(notif, settings)
        assertNotEquals("Pepperfry promo should NOT be classified as OTP", NotificationIntent.OTP, classified.intent)
    }

    @Test
    fun `test Myntra flash sale promo code is NOT parsed as OTP`() {
        val notif = createNotif(
            key = "myntra_promo_1",
            packageName = "com.myntra.android",
            title = "Flash Sale Live!",
            text = "Use code MYNTRA50 to get flat 50% OFF | Shop Now"
        )
        val otp = parser.extractOtp(notif)
        assertNull("Myntra promo code notifications should NOT extract OTP", otp)
        val classified = classifier.classify(notif, settings)
        assertNotEquals("Myntra promo should NOT be classified as OTP", NotificationIntent.OTP, classified.intent)
    }

    @Test
    fun `test SAHI feature promo Instant Withdrawals live is NOT parsed as transaction`() {
        val notif = createNotif(
            key = "sahi_promo_1",
            packageName = "com.sahi.app",
            title = "⚡ New: Instant Withdrawals are live",
            text = "Up to ₹2L. Credited in under 30 minutes. Tap to try now 👉"
        )
        val details = parser.parseTransactionDetails(notif, settings)
        assertNull("SAHI feature promo notification should NOT be parsed as transaction", details)
        val classified = classifier.classify(notif, settings)
        assertNotEquals("SAHI feature promo should NOT be classified as TRANSACTION", NotificationIntent.TRANSACTION, classified.intent)
    }

    @Test
    fun `test Telegram corporate news order win is NOT parsed as transaction`() {
        val notif = createNotif(
            key = "telegram_news_1",
            packageName = "org.telegram.messenger",
            title = "Stock Market Updates",
            text = "- Zen Technologies: Secured a ₹295 crore order from from the ministry of Deference of supply of sumulators"
        )
        val details = parser.parseTransactionDetails(notif, settings)
        assertNull("Telegram chat message regarding corporate order win should NOT be parsed as transaction", details)
        val classified = classifier.classify(notif, settings)
        assertNotEquals("Telegram chat news should NOT be classified as TRANSACTION", NotificationIntent.TRANSACTION, classified.intent)
    }

    @Test
    fun `test IPO alert and market updates are NOT parsed as transaction`() {
        val ipoNotif = createNotif(
            key = "ipo_alert_1",
            packageName = "com.sahi.app",
            title = "📢 Milky Mist Dairy IPO",
            text = "Price range: ₹133 - ₹140. Last date to apply: 13 August"
        )
        assertNull("IPO alert should NOT be parsed as transaction", parser.parseTransactionDetails(ipoNotif, settings))

        val niftyNotif = createNotif(
            key = "nifty_alert_1",
            packageName = "com.sahi.app",
            title = "🐻 Flat start likely, hints Gift Nifty!",
            text = "Key levels: Nifty: 24,450 & 24,700"
        )
        assertNull("Nifty market update should NOT be parsed as transaction", parser.parseTransactionDetails(niftyNotif, settings))
    }

    @Test
    fun `test amount multiplier parsing for Lakhs and Crores`() {
        val bankLakhNotif = createNotif(
            key = "bank_lakh",
            packageName = "com.snapwork.hdfc",
            title = "Account Credited",
            text = "Rs 2L credited to your A/C 4882 by NEFT."
        )
        val details = parser.parseTransactionDetails(bankLakhNotif, settings)
        assertNotNull(details)
        assertEquals(200000.0, details?.amount?.value)
    }

    @Test
    fun `test non-financial metric units like followers and downloads are NOT parsed as transactions`() {
        val socialNotif = createNotif(
            key = "social_metrics",
            packageName = "com.instagram.android",
            title = "Milestone Reached!",
            text = "Your account hit 2k followers and Rs 1M downloads this month!"
        )
        val details = parser.parseTransactionDetails(socialNotif, settings)
        assertNull("Non-financial metrics (followers, downloads) should NOT parse as monetary transactions", details)
    }
}


