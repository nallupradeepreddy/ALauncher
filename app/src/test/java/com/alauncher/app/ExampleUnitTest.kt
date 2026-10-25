package com.alauncher.app

import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.SortMode
import com.alauncher.app.model.displayTitle
import com.alauncher.app.model.displayBody
import com.alauncher.app.model.displayTitlePreview
import com.alauncher.app.model.displayBodyPreview
import com.alauncher.app.model.hasContent
import com.alauncher.app.domain.notification.NotificationFilterEngine
import com.alauncher.app.domain.notification.NotificationParser
import com.alauncher.app.domain.notification.NotificationClassifier
import com.alauncher.app.domain.notification.NotificationIntent
import com.alauncher.app.domain.app.resolveAutoLaunchCandidate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import com.alauncher.app.data.settings.isQuietHoursActiveNow
import com.alauncher.app.ui.home.TransactionAlertType
import com.alauncher.app.ui.home.TransactionSettingsKeys

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun testAppSorting() {
        val appA = LauncherApp("pkg.a", "WhatsApp", "whatsapp", isFavorite = false, launchCount = 10, lastOpenedTime = 1000L)
        val appB = LauncherApp("pkg.b", "Instagram", "instagram", isFavorite = true, launchCount = 5, lastOpenedTime = 3000L)
        val appC = LauncherApp("pkg.c", "Chrome", "chrome", isFavorite = false, launchCount = 20, lastOpenedTime = 2000L)
        
        val list = listOf(appA, appB, appC)

        // Alphabetical Sorting
        val sortedAsc = list.sortedBy { it.label.lowercase(Locale.getDefault()) }
        assertEquals("Chrome", sortedAsc[0].label)
        assertEquals("Instagram", sortedAsc[1].label)
        assertEquals("WhatsApp", sortedAsc[2].label)

        // Favorites First
        val sortedFavFirst = list.sortedWith(compareByDescending<LauncherApp> { it.isFavorite }.thenBy { it.label })
        assertTrue(sortedFavFirst[0].isFavorite)
        assertEquals("Instagram", sortedFavFirst[0].label)

        // Most Used
        val sortedMostUsed = list.sortedByDescending { it.launchCount }
        assertEquals("Chrome", sortedMostUsed[0].label) // 20
        assertEquals("WhatsApp", sortedMostUsed[1].label) // 10
    }

    @Test
    fun testAppTrieSearchIndex() {
        val index = com.alauncher.app.domain.app.AppTrieSearchIndex()
        val appPlayStore = LauncherApp("com.android.vending", "Google Play Store", "google play store")
        val appPlayGames = LauncherApp("com.google.android.play.games", "Google Play Games", "google play games")
        val appPlayServices = LauncherApp("com.google.android.gms", "Google Play Services", "google play services")
        val appCapCut = LauncherApp("com.lemon.lvoverseas", "CapCut", "capcut")
        val appWhatsApp = LauncherApp("com.whatsapp", "WhatsApp", "whatsapp")
        val appAmazon = LauncherApp("in.amazon.mShop.android.shopping", "Amazon", "amazon")
        val appAmazonMusic = LauncherApp("com.amazon.mp3", "Amazon Music", "amazon music")

        val appsList = listOf(appPlayServices, appPlayGames, appPlayStore, appCapCut, appWhatsApp, appAmazonMusic, appAmazon)
        index.rebuildIndex(appsList)

        // 1. Test "play s" multi-word query ranks Play Store at index 0
        val resultsPlayS = index.search("play s")
        assertTrue("Results for 'play s' should contain Play Store", resultsPlayS.any { it.label == "Google Play Store" })
        assertEquals("Google Play Store should be the top-ranked match for 'play s'", "Google Play Store", resultsPlayS.first().label)

        // 2. Test "capcut" exact/spaceless query
        val resultsCapCut = index.search("capcut")
        assertEquals("CapCut", resultsCapCut.first().label)

        // 3. Test "cap cut" space query on "CapCut"
        val resultsCapSpaceCut = index.search("cap cut")
        assertEquals("CapCut", resultsCapSpaceCut.first().label)

        // 4. Test initials "cc"
        val resultsCC = index.search("cc")
        assertTrue("Initials 'cc' should match CapCut", resultsCC.any { it.label == "CapCut" })

        // 5. Test "amazon" query when "Amazon" and "Amazon Music" both exist
        val resultsAmazon = index.search("amazon")
        assertEquals("Amazon should be ranked first on exact query 'amazon'", "Amazon", resultsAmazon.first().label)
    }

    @Test
    fun testAutoLaunchResolution() {
        val appAmazon = LauncherApp("in.amazon.mShop.android.shopping", "Amazon", "amazon")
        val appAmazonMusic = LauncherApp("com.amazon.mp3", "Amazon Music", "amazon music")
        val appCapCut = LauncherApp("com.lemon.lvoverseas", "CapCut", "capcut")
        val appA = LauncherApp("com.example.a", "A", "a")

        // 1. Single-character query should NEVER auto-launch
        assertNull(resolveAutoLaunchCandidate(listOf(appA), "a"))
        assertNull(resolveAutoLaunchCandidate(listOf(appAmazon), "a"))

        // 2. Exact match with prefix ambiguity (e.g. Amazon when Amazon Music exists) should NOT auto-launch
        val exactCandidateAmbiguous = resolveAutoLaunchCandidate(listOf(appAmazon, appAmazonMusic), "amazon")
        assertNull(exactCandidateAmbiguous)

        // 2b. Unambiguous exact match when no longer prefix candidates exist should auto-launch
        val exactCandidateUnambiguous = resolveAutoLaunchCandidate(listOf(appAmazon), "amazon")
        assertNotNull(exactCandidateUnambiguous)
        assertEquals("Amazon", exactCandidateUnambiguous?.label)

        // 2c. User case: "Phone", "Phone Manager", "PhonePe"
        val appPhone = LauncherApp("com.android.dialer", "Phone", "phone")
        val appPhoneManager = LauncherApp("com.coloros.phonemanager", "Phone Manager", "phone manager")
        val appPhonePe = LauncherApp("com.phonepe.app", "PhonePe", "phonepe")
        val phoneApps = listOf(appPhone, appPhoneManager, appPhonePe)

        // "phone" has prefix ambiguity with Phone Manager & PhonePe -> MUST NOT auto-launch
        assertNull(resolveAutoLaunchCandidate(phoneApps, "phone"))
        // "phone m" uniquely narrows to Phone Manager -> auto-launches
        val phoneManagerCandidate = resolveAutoLaunchCandidate(listOf(appPhoneManager), "phone m")
        assertNotNull(phoneManagerCandidate)
        assertEquals("Phone Manager", phoneManagerCandidate?.label)
        // "phonep" uniquely narrows to PhonePe -> auto-launches
        val phonePeCandidate = resolveAutoLaunchCandidate(listOf(appPhonePe), "phonep")
        assertNotNull(phonePeCandidate)
        assertEquals("PhonePe", phonePeCandidate?.label)

        // 2d. Normalized prefix ambiguity (e.g. query "telephone" with "Téléphone" and "Téléphone Pro")
        val appTelephone = LauncherApp("com.example.tel", "Téléphone", "telephone")
        val appTelephonePro = LauncherApp("com.example.telpro", "Téléphone Pro", "telephone pro")
        // "telephone" matches "Téléphone" via normalization, while "Téléphone Pro" causes ambiguity
        // via normalized prefix check (bypassing raw startsWith due to diacritic 'é')
        assertNull(resolveAutoLaunchCandidate(listOf(appTelephone, appTelephonePro), "telephone"))
        val telephoneCandidate = resolveAutoLaunchCandidate(listOf(appTelephone), "telephone")
        assertNotNull(telephoneCandidate)
        assertEquals("Téléphone", telephoneCandidate?.label)

        // 2e. Spaceless prefix ambiguity (e.g. query "capcut" with "CapCut" and "Cap Cut Pro")
        val appCapCutPro = LauncherApp("com.lemon.lvpro", "Cap Cut Pro", "cap cut pro")
        // "capcut" exact matches "CapCut", but "Cap Cut Pro" causes ambiguity via spaceless prefix check
        // (bypassing raw startsWith due to internal whitespace differences)
        assertNull(resolveAutoLaunchCandidate(listOf(appCapCut, appCapCutPro), "capcut"))

        // 3. Spaceless exact match on CapCut
        val capcutCandidate = resolveAutoLaunchCandidate(listOf(appCapCut), "cap cut")
        assertNotNull(capcutCandidate)
        assertEquals("CapCut", capcutCandidate?.label)

        // 4. Single remaining match with length >= 2
        val singleCandidate = resolveAutoLaunchCandidate(listOf(appAmazonMusic), "amazon m")
        assertNotNull(singleCandidate)
        assertEquals("Amazon Music", singleCandidate?.label)

        // 5. Multiple non-exact matches should NOT auto-launch
        assertNull(resolveAutoLaunchCandidate(listOf(appAmazon, appAmazonMusic), "am"))

        // 6. Edge cases: empty apps list or blank queries
        assertNull(resolveAutoLaunchCandidate(emptyList(), "amazon"))
        assertNull(resolveAutoLaunchCandidate(listOf(appAmazon), ""))
        assertNull(resolveAutoLaunchCandidate(listOf(appAmazon), "   "))
    }

    @Test
    fun testNotificationFilterEnginePrecedence() {
        val engine = NotificationFilterEngine()
        val sampleAlert = LauncherNotification(
            key = "key1",
            packageName = "com.facebook.katana",
            appName = "Facebook",
            title = "Special coupons Alert!",
            text = "Use code SAVE50 to save money",
            postTime = System.currentTimeMillis(),
            channelId = null,
            isOngoing = false,
            isClearable = true,
            importance = 3
        )

        // Assertion 1: Unfiltered app is classified as PRIORITY
        val computedCategory = engine.classify(
            notification = sampleAlert,
            settings = com.alauncher.app.data.settings.SettingsState(unfilteredApps = setOf("com.facebook.katana"))
        )
        assertEquals(FilterCategory.PRIORITY, computedCategory)

        // Assertion 2: Focus Mode mutes normal alerts
        val normalAlert = LauncherNotification(
            key = "key2",
            packageName = "com.social.app",
            appName = "SocialApp",
            title = "New message",
            text = "Hello!",
            postTime = System.currentTimeMillis(),
            channelId = null,
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        val focusCategory = engine.classify(normalAlert, com.alauncher.app.data.settings.SettingsState(isFocusModeActive = true))
        assertEquals(FilterCategory.MUTED, focusCategory)
    }


    @Test
    fun testHomeAlignmentEnum() {
        val alignments = com.alauncher.app.model.HomeAlignment.values()
        assertEquals(2, alignments.size)
        assertTrue(alignments.contains(com.alauncher.app.model.HomeAlignment.Left))
        assertTrue(alignments.contains(com.alauncher.app.model.HomeAlignment.Right))
        assertFalse(alignments.any { it.name == "Center" })
    }


    @Test
    fun testNotificationCenterAppGrouping() {
        val notif1 = LauncherNotification(
            key = "key1", packageName = "com.whatsapp", appName = "WhatsApp",
            title = "A", text = "Msg 1", postTime = 1000L, channelId = null,
            isOngoing = false, isClearable = true, importance = 3
        )
        val notif2 = LauncherNotification(
            key = "key2", packageName = "com.instagram.android", appName = "Instagram",
            title = "B", text = "Msg 2", postTime = 3000L, channelId = null,
            isOngoing = false, isClearable = true, importance = 3
        )
        val notif3 = LauncherNotification(
            key = "key3", packageName = "com.whatsapp", appName = "WhatsApp",
            title = "C", text = "Msg 3", postTime = 2000L, channelId = null,
            isOngoing = false, isClearable = true, importance = 3
        )
        val notif4 = LauncherNotification(
            key = "key4", packageName = "com.google.android.youtube", appName = "YouTube",
            title = "D", text = "Video", postTime = 1500L, channelId = null,
            isOngoing = false, isClearable = true, importance = 3
        )

        val list = listOf(notif1, notif2, notif3, notif4)

        val grouped = list.groupBy { it.packageName }
            .toList()
            .sortedByDescending { (_, notifications) ->
                notifications.maxOfOrNull { it.postTime } ?: 0L
            }

        assertEquals(3, grouped.size)
        assertEquals("com.instagram.android", grouped[0].first)
        assertEquals("com.whatsapp", grouped[1].first)
        assertEquals("com.google.android.youtube", grouped[2].first)

        val whatsappGroup = grouped[1].second
        assertEquals(2, whatsappGroup.size)
        assertTrue(whatsappGroup.any { it.key == "key1" })
        assertTrue(whatsappGroup.any { it.key == "key3" })

        // Verify group dismissal logic (swiping header dismisses all items in group)
        val dismissedKeys = mutableListOf<String>()
        whatsappGroup.forEach { dismissedKeys.add(it.key) }
        assertEquals(2, dismissedKeys.size)
        assertTrue(dismissedKeys.contains("key1"))
        assertTrue(dismissedKeys.contains("key3"))
    }

    @Test
    fun testCalendarAndOtpHelpers() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val dummyRepo = com.alauncher.app.data.notification.NotificationRepository(
            filterEngine = com.alauncher.app.domain.notification.NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val repoField = homeViewModel.javaClass.getDeclaredField("notificationRepository")
        repoField.isAccessible = true
        repoField.set(homeViewModel, dummyRepo)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        // Helper to invoke private methods
        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        // Test cleanCalendarTitle
        val title1 = "hanumesh.m@kanarystaffing.com: FW: Interview of Suryateja Pathuri - Sr. Fullstack Engineer"
        val cleanTitle1 = invokeHelper("cleanCalendarTitle", title1) as String
        assertEquals("Interview of Suryateja Pathuri - Sr. Fullstack Engineer", cleanTitle1)

        val title2 = "FW: RE: Quick Sync"
        val cleanTitle2 = invokeHelper("cleanCalendarTitle", title2) as String
        assertEquals("Quick Sync", cleanTitle2)

        val title3 = "Regular Meeting"
        val cleanTitle3 = invokeHelper("cleanCalendarTitle", title3) as String
        assertEquals("Regular Meeting", cleanTitle3)

        // Test isCallRelated
        assertTrue(invokeHelper("isCallRelated", "Weekly Sync", "Discuss sprint goals", true) as Boolean)
        assertTrue(invokeHelper("isCallRelated", "Interview", "Candidate evaluation", true) as Boolean)
        assertTrue(invokeHelper("isCallRelated", "Important Notification", "Please join the teams call: https://teams.microsoft.com/l/meetup-join/123", true) as Boolean)
        assertFalse(invokeHelper("isCallRelated", "Form 16 Release", "Your Form 16 is now available on ESS Portal", true) as Boolean)
        assertFalse(invokeHelper("isCallRelated", "Outage Splunk Cloud", "Splunk heavy forwarder in China is down", true) as Boolean)

        // Test extractScheduleInfo
        val text1 = "• Thu, 18 Jun, 10:30 am (30m) • https://meet.google.com/yyf-pwot-igb • CAUTION: External email"
        val schedule1 = invokeHelper("extractScheduleInfo", text1) as? String
        assertEquals("Thu, 18 Jun, 10:30 am (30m)", schedule1)

        val text2 = "Tomorrow at 3 pm (30m) • Conf-IN-HYD Ajanta • Setup notes"
        val schedule2 = invokeHelper("extractScheduleInfo", text2) as? String
        assertEquals("Tomorrow at 3 pm (30m)", schedule2)

        val text3 = "Meeting link: https://meet.google.com • No schedule info here"
        val schedule3 = invokeHelper("extractScheduleInfo", text3) as? String
        assertNull(schedule3)

        // Test isCalendarReminderNotification for hybrid apps
        val calendarInvite = LauncherNotification(
            key = "cal1", packageName = "com.microsoft.office.outlook", appName = "Outlook",
            title = "Design Sync Meeting", text = "10:00 AM - 10:30 AM", postTime = 0,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertTrue(invokeHelper("isCalendarReminderNotification", calendarInvite) as Boolean)

        val assignmentEmail = LauncherNotification(
            key = "email1", packageName = "com.microsoft.office.outlook", appName = "Outlook",
            title = "IT Asset Management Review", text = "You have been assigned on the SOP-123 Rev C Tech SOP, IT Asset Management", postTime = 0,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertFalse(invokeHelper("isCalendarReminderNotification", assignmentEmail) as Boolean)

        // Test NotificationParser extractOtp
        val parser = com.alauncher.app.domain.notification.NotificationParser()
        val otpText = "Your verification code is 482910"
        val otp = parser.extractOtp(null, otpText)
        assertEquals("482910", otp)

        val zetaOtpText = "The OTP for your transaction amount of Rs. 484.08 at RATNADEEP is 7474. - Zeta"
        val zetaOtp = parser.extractOtp(null, zetaOtpText)
        assertEquals("7474", zetaOtp)

        // 14 OTP cases verification
        val msg1 = """947707 - Your OTP to access Equal Al. Valid for 10 minutes. Do not share this with anyone. Powered by Equal
AI v0eJj6+Х8wg"""
        assertEquals("947707", parser.extractOtp(null, msg1))

        val msg2 = "Your OTP verification code for Stratzy is 3572, OTP expires in 5 minutes ZGZINWEyZjM"
        assertEquals("3572", parser.extractOtp(null, msg2))

        val msg3 = "OTP for updating transactions settings is 6028. Pluxee"
        assertEquals("6028", parser.extractOtp(null, msg3))

        val msg4 = """The OTP for your transaction amount of Rs. 208.91 at RATNADEEP is
7176. - ZetaR"""
        assertEquals("7176", parser.extractOtp(null, msg4))

        val msg5 = """Ekart Update : SPEEDFOX Kids kick scooter ... with tracking ID FMPP4086354233 from flipkart.com, will be delivered by 11pm today by agent 02248887777, PIN 123)..
Share OTP 649337 only to accept order after checking that there are No Damages, Missing or Wrong items at the doorstep. Returns will not be accepted for these reasons once the order is delivered.
8:26"""
        assertEquals("649337", parser.extractOtp(null, msg5))

        val msg6 = "OTP for updating transactions settings is 1034. Pluxee"
        assertEquals("1034", parser.extractOtp(null, msg6))

        val msg7 = "194126 is your OTP for Dhan. Please do not share it with anyone. Two Factor Authentication is mandated for every login session by exchanges."
        assertEquals("194126", parser.extractOtp(null, msg7))

        val msg8 = "Dear Pradeep Your forgot password otp is 398081 -IGRS, Telangana"
        assertEquals("398081", parser.extractOtp(null, msg8))

        val msg9 = """Your OTP to login is 6031 - Team
HungerBox"""
        assertEquals("6031", parser.extractOtp(null, msg9))

        val msg10 = "<#> Enter OTP 650137 on Sahi to get started. Please do not share this OTP with anyone. Team Aaritya. x9hmeiV1kLO"
        assertEquals("650137", parser.extractOtp(null, msg10))

        val msg11 = """213142 is your OTP for payment of Rs.1164737.80 to
SBIEPYREGSTAMSDEPTTE
via HDFC Bank NetBanking.
NEVER share OTP Not you?Call
18002586161"""
        assertEquals("213142", parser.extractOtp(null, msg11))

        val msg12 = "G-276776 is your Google verification code. Don't share your code with anyone."
        assertEquals("276776", parser.extractOtp(null, msg12))

        val msg13 = "Your Play Console verification code is 292828"
        assertEquals("292828", parser.extractOtp(null, msg13))

        val msg14 = """634470 is your One time password
(OTP) to login to MyJio. Don't share OTP with anyone.
Please enter the OTP to proceed."""
        assertEquals("634470", parser.extractOtp(null, msg14))

        // EPFO & Masked ID OTP message test
        val epfoMsg = "Dear Member (UAN: XXXX XXXX 7656). OTP to login to Member inter-face is 766669 OTP-ID 8081. Do not share with anyone. -EPFO"
        assertEquals("766669", parser.extractOtp(null, epfoMsg))

        // Test Ref. prefix rejection and valid word substring safety
        val refDotMsg = "Ref. 987654. Your valid OTP code is 123456"
        assertEquals("123456", parser.extractOtp(null, refDotMsg))

        // Ride PIN / Action-anchored PIN tests
        val uberPinMsg = "Use PIN 0362\nGive it to your driver at pick-up to start your ride."
        assertEquals("0362", parser.extractOtp(null, uberPinMsg))
        val sharePinMsg = "Share PIN 4821 with your driver to start the trip."
        assertEquals("4821", parser.extractOtp(null, sharePinMsg))

        // Length validation tests
        assertNull(parser.extractOtp(null, "Your OTP is 123456789"))
        assertNull(parser.extractOtp(null, "Your OTP is 123"))
        assertEquals("123456", parser.extractOtp(null, "Your OTP is 123456"))

        // Transaction false positive tests
        assertNull(parser.extractOtp(null, "Do not share your password/OTP. Transaction of Rs. 5000 done on card ending 1234."))
        assertNull(parser.extractOtp(null, "Do not share your password/OTP. Transaction of Rs. 5000 was processed."))
        assertNull(parser.extractOtp(null, "Do not share your password/OTP. Transaction done on card ending 1234."))

        // Test NotificationParser extractDownloadProgress
        val downloadNotif = LauncherNotification(
            key = "dl", packageName = "com.android.providers.downloads", appName = "Downloads",
            title = "Downloading file", text = "45% complete", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val uploadNotif = LauncherNotification(
            key = "ul", packageName = "com.google.android.apps.photos", appName = "Photos",
            title = "Uploading photos", text = "Done (90%)", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val regularNotif = LauncherNotification(
            key = "reg", packageName = "com.android.settings", appName = "Settings",
            title = "Just a regular app", text = "No progress here", postTime = 0,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val nativeProgressNotif = LauncherNotification(
            key = "native", packageName = "com.android.vending", appName = "Play Store",
            title = "Downloading Google Maps", text = "Installing...", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3,
            progress = 35, progressMax = 100
        )
        val nativeProgressUploadNotif = LauncherNotification(
            key = "native_ul", packageName = "com.google.android.apps.docs", appName = "Drive",
            title = "Uploading file", text = "Uploading...", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3,
            progress = 70, progressMax = 100
        )

        assertEquals("Progress: 45% 📥", parser.extractDownloadProgress(downloadNotif))
        assertEquals("Progress: 90% 📤", parser.extractDownloadProgress(uploadNotif))
        assertNull(parser.extractDownloadProgress(regularNotif))
        assertEquals("Progress: 35% 📥", parser.extractDownloadProgress(nativeProgressNotif))
        assertEquals("Progress: 70% 📤", parser.extractDownloadProgress(nativeProgressUploadNotif))



        // Teams meeting invite notification — parseLiveTracker must NOT surface an OTP island.
        // The classifier identifies this as MEETING intent, which short-circuits extractOtp.
        val teamsMeetingInviteNotif = LauncherNotification(
            key = "teams_invite", packageName = "com.microsoft.teams", appName = "Teams",
            title = "You're invited: Sprint Planning",
            text = "Meeting ID: 234 567 890 12\nPasscode: xq2wX9DZ\nJoin: https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc/0",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val teamsMeetingIslandResult = invokeHelper("parseLiveTracker", teamsMeetingInviteNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertTrue(
            "Teams meeting invite should render as UPCOMING_MEETING, not OTP",
            teamsMeetingIslandResult == null || teamsMeetingIslandResult.type != com.alauncher.app.ui.home.ContextualIslandType.OTP
        )

        // Test isSystemStatusNotification
        val vpnNotif = LauncherNotification(
            key = "key3", packageName = "com.android.systemui", appName = "System",
            title = "VPN connected", text = "Connected to VPN", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 2
        )
        assertTrue(invokeHelper("isSystemStatusNotification", vpnNotif) as Boolean)

        // Test parseLiveTracker OTP feedback
        val otpNotif = LauncherNotification(
            key = "otp_key", packageName = "com.sms", appName = "SMS",
            title = "OTP", text = "Code: 1234", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 4
        )
        val islandNormal = invokeHelper("parseLiveTracker", otpNotif, null) as com.alauncher.app.ui.home.ContextualIsland
        assertEquals("🔑 OTP Code: 1234", islandNormal.text)
        assertEquals(com.alauncher.app.ui.home.ContextualActionType.COPY_OTP, islandNormal.actionType)
        assertEquals("1234:::otp_key", islandNormal.actionPayload)

        val islandCopied = invokeHelper("parseLiveTracker", otpNotif, "otp_key") as com.alauncher.app.ui.home.ContextualIsland
        assertEquals("✅ OTP Copied", islandCopied.text)
        assertEquals(com.alauncher.app.ui.home.ContextualActionType.NONE, islandCopied.actionType)

        // Test expired OTP (postTime is older than 5-minute freshness gate)
        val expiredOtpNotif = LauncherNotification(
            key = "otp_key", packageName = "com.sms", appName = "SMS",
            title = "OTP", text = "Code: 1234", postTime = System.currentTimeMillis() - (16L * 60_000L),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 4
        )
        val islandExpired = invokeHelper("parseLiveTracker", expiredOtpNotif, null)
        assertNull(islandExpired)

        // Test chronometer/timer
        val timerNotif = LauncherNotification(
            key = "timer_key", packageName = "com.google.android.deskclock", appName = "Clock",
            title = "Timer", text = "Counting down", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3,
            showChronometer = true, chronometerCountDown = true, whenValue = 10000L
        )
        val islandTimer = invokeHelper("parseLiveTracker", timerNotif, null) as com.alauncher.app.ui.home.ContextualIsland
        assertTrue(islandTimer.showChronometer)
        assertTrue(islandTimer.chronometerCountDown)
        assertEquals(10000L, islandTimer.whenValue)
        assertEquals("⏱️ Timer", islandTimer.text)

        // New test cases for progress indeterminate keyword filter & calendar exclusion
        val indeterminateWithKeyword = LauncherNotification(
            key = "ind_kw", packageName = "com.android.providers.downloads", appName = "Downloads",
            title = "Downloading file", text = "Sync in progress...", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3,
            progressIndeterminate = true
        )
        assertEquals("Progress: In Progress... 📥", parser.extractDownloadProgress(indeterminateWithKeyword))

        val indeterminateWithoutKeyword = LauncherNotification(
            key = "ind_nokw", packageName = "com.other.app", appName = "Other App",
            title = "App Running", text = "Foreground Service Active", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3,
            progressIndeterminate = true
        )
        assertNull(parser.extractDownloadProgress(indeterminateWithoutKeyword))

        val calendarWithProgressKeyword = LauncherNotification(
            key = "cal_progress", packageName = "com.google.android.calendar", appName = "Calendar",
            title = "Download project slides", text = "Updating stakeholders, 50% finished", postTime = 0,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(parser.extractDownloadProgress(calendarWithProgressKeyword))

        // Test Gmail progress exclusion
        val gmailProgressNotif = LauncherNotification(
            key = "gmail_sync", packageName = "com.google.android.gm", appName = "Gmail",
            title = "Syncing mail", text = "Sync in progress...", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 2,
            progressIndeterminate = true
        )
        assertNull(parser.extractDownloadProgress(gmailProgressNotif))

        // Test Swiggy active delivery notification (transient state)
        val swiggyActiveNotif = LauncherNotification(
            key = "swiggy_active", packageName = "com.swiggy.android", appName = "Swiggy",
            title = "Order Packed", text = "Rider is on the way to pick up your order", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        // Under default settings (liveTrackingEnabled = false), this transient state is filtered out
        assertNull(invokeHelper("parseLiveTracker", swiggyActiveNotif, null))

        // In Live Tracking Mode, it is displayed with the appropriate food icon
        val swiggyActiveIslandLive = invokeHelper(
            "parseLiveTracker", swiggyActiveNotif, null, com.alauncher.app.data.settings.SettingsState(liveTrackingEnabled = true)
        ) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(swiggyActiveIslandLive)
        assertEquals("🍔 Order Packed: Rider is on the way to pick up your order", swiggyActiveIslandLive?.text)

        // Test Swiggy promotional notification (should always be filtered out)
        val swiggyPromoNotif = LauncherNotification(
            key = "swiggy_promo", packageName = "com.swiggy.android", appName = "Swiggy",
            title = "Hungry?", text = "Get 50% discount on biryani order now!", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 2
        )
        assertNull(invokeHelper("parseLiveTracker", swiggyPromoNotif, null))
        assertNull(invokeHelper("parseLiveTracker", swiggyPromoNotif, null, com.alauncher.app.data.settings.SettingsState(liveTrackingEnabled = true)))

        // Test Zomato active delivery notification (strict state because of 'arriving')
        val zomatoActiveNotif = LauncherNotification(
            key = "zomato_active", packageName = "com.application.zomato", appName = "Zomato",
            title = "Food is being prepared", text = "Valet is arriving at restaurant", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        // Should show up even in default Strict mode
        val zomatoActiveIslandDefault = invokeHelper("parseLiveTracker", zomatoActiveNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(zomatoActiveIslandDefault)
        assertEquals("🍔 Food is being prepared: Valet is arriving at restaurant", zomatoActiveIslandDefault?.text)

        // Test Zomato promotional notification (should be filtered out)
        val zomatoPromoNotif = LauncherNotification(
            key = "zomato_promo", packageName = "com.application.zomato", appName = "Zomato",
            title = "Craving dessert?", text = "Order now and win a scratch card!", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 2
        )
        assertNull(invokeHelper("parseLiveTracker", zomatoPromoNotif, null))

        // Test BigBasket active tracking (uses Whitelist keyword "started" so it is strict state)
        val bigBasketNotif = LauncherNotification(
            key = "bb_active", packageName = "com.bigbasket.mobileapp", appName = "BigBasket",
            title = "Order packing started", text = "Your items are being packed for delivery", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val bigBasketIsland = invokeHelper("parseLiveTracker", bigBasketNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(bigBasketIsland)
        assertEquals("🍔 Order packing started: Your items are being packed for delivery", bigBasketIsland?.text)

        // Test BigBasket promotional notification (should be filtered out because of marketing phrases, even with "delivered" keyword)
        val bigBasketPromoNotif = LauncherNotification(
            key = "bb_promo", packageName = "com.bigbasket.mobileapp", appName = "BigBasket",
            title = "Buy 1 Get 1 free", text = "Double the shopping fun today! Start shopping. Everything you need, delivered now.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseLiveTracker", bigBasketPromoNotif, null))

        // Test BigBasket breakfast promotional deal notification reported by user (must be filtered out despite "delivered" keyword)
        val bigBasketBreakfastPromo = LauncherNotification(
            key = "bb_breakfast_promo", packageName = "com.bigbasket.mobileapp", appName = "BigBasket",
            title = "Breakfast at ₹19/-?", text = "Yes! Your pocket-friendly breakfast starts @ 19/-. Check it out now. Delivered at your doorstep", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseLiveTracker", bigBasketBreakfastPromo, null))

        // Test genuine BigBasket delivery completion notification using "delivered to your doorstep" (must NOT be filtered out)
        val genuineBigBasketDelivery = LauncherNotification(
            key = "bb_real_delivery", packageName = "com.bigbasket.mobileapp", appName = "BigBasket",
            title = "Order Delivered", text = "Your order #BB84920 has been delivered to your doorstep.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val genuineIsland = invokeHelper("parseLiveTracker", genuineBigBasketDelivery, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull("Genuine delivery notification with doorstep phrasing should not be filtered out", genuineIsland)

        // Test genuine delivery completion notification WITHOUT an order ID (must NOT be filtered out)
        val genuineDeliveryNoId = LauncherNotification(
            key = "bb_real_delivery_noid", packageName = "com.bigbasket.mobileapp", appName = "BigBasket",
            title = "Order Delivered", text = "Your order has been delivered to your doorstep.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val genuineIslandNoId = invokeHelper("parseLiveTracker", genuineDeliveryNoId, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull("Genuine delivery notification without order ID should not be filtered out", genuineIslandNoId)

        // Test Blinkit (com.grofers.customerapp) returns food/grocery icon 🍔
        val blinkitDelivery = LauncherNotification(
            key = "blinkit_del", packageName = "com.grofers.customerapp", appName = "Blinkit",
            title = "Order Delivered", text = "Your groceries have been delivered.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val blinkitIsland = invokeHelper("parseLiveTracker", blinkitDelivery, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(blinkitIsland)
        assertTrue("Blinkit (grofers) should return food/grocery icon 🍔", blinkitIsland?.text?.startsWith("🍔") == true)

        // Test potential false positive with 'flat' in address
        val swiggyAddressNotif = LauncherNotification(
            key = "swiggy_addr", packageName = "com.swiggy.android", appName = "Swiggy",
            title = "Order Delivered", text = "Your order has been delivered at Flat 402, Sunshine Apartments.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        // If this is null, it means it was incorrectly filtered out by the 'flat' keyword
        val swiggyAddressIsland = invokeHelper("parseLiveTracker", swiggyAddressNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull("Notification with 'Flat' in address should not be filtered out", swiggyAddressIsland)

        // Test potential false positive with 'win' in name
        val uberEdwinNotif = LauncherNotification(
            key = "uber_edwin", packageName = "com.ubercab", appName = "Uber",
            title = "Driver arriving", text = "Edwin is arriving in 2 mins in a White Swift.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val uberEdwinIsland = invokeHelper("parseLiveTracker", uberEdwinNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull("Notification with 'Edwin' (containing 'win') should not be filtered out", uberEdwinIsland)

        // Test general marketing phrases with order/buy/discount verbs
        val swiggyOrderNowPromo = LauncherNotification(
            key = "sw_promo", packageName = "com.swiggy.android", appName = "Swiggy",
            title = "Order online today", text = "Get your favourite foods delivered right now!", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseLiveTracker", swiggyOrderNowPromo, null))

        // Test Tata Neu active tracking (transient state because booking confirmed has no strict mode keywords)
        val tataNeuNotif = LauncherNotification(
            key = "tata_active", packageName = "com.tatadigital.tataneu", appName = "Tata Neu",
            title = "Booking Confirmed", text = "ID: 987654", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        // Null under default settings
        assertNull(invokeHelper("parseLiveTracker", tataNeuNotif, null))
        
        // Non-null under Live tracking mode
        val tataNeuIslandLive = invokeHelper(
            "parseLiveTracker", tataNeuNotif, null, com.alauncher.app.data.settings.SettingsState(liveTrackingEnabled = true)
        ) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(tataNeuIslandLive)
        assertEquals("🍔 Booking Confirmed: ID: 987654", tataNeuIslandLive?.text)

        // Test promotional filter with "deal" / "off" keyword
        val swiggyPromoOfferNotif = LauncherNotification(
            key = "swiggy_promo_offer", packageName = "com.swiggy.android", appName = "Swiggy",
            title = "Exclusive Deal!", text = "Get 20% off on your next order", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 2
        )
        assertNull(invokeHelper("parseLiveTracker", swiggyPromoOfferNotif, null))

        // Test completed status age limit of 5 minutes (delivered)
        val swiggyDeliveredNowNotif = LauncherNotification(
            key = "swiggy_delivered_now", packageName = "com.swiggy.android", appName = "Swiggy",
            title = "Order Delivered", text = "Enjoy your food!", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        // Delivered within 5 minutes should show up
        val deliveredIslandNow = invokeHelper("parseLiveTracker", swiggyDeliveredNowNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(deliveredIslandNow)
        assertEquals("🍔 Order Delivered: Enjoy your food!", deliveredIslandNow?.text)

        val swiggyDeliveredOldNotif = LauncherNotification(
            key = "swiggy_delivered_old", packageName = "com.swiggy.android", appName = "Swiggy",
            title = "Order Delivered", text = "Enjoy your food!", postTime = System.currentTimeMillis() - (6 * 60 * 1000),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        // Delivered longer than 5 minutes ago should be filtered out
        assertNull(invokeHelper("parseLiveTracker", swiggyDeliveredOldNotif, null))

        // Test cases for Rapido and RailOne/travel apps/keywords
        val rapidoNotif = LauncherNotification(
            key = "rapido", packageName = "com.rapido.passenger", appName = "Rapido",
            title = "Captain is arriving", text = "Your ride is 2 mins away", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val rapidoIsland = invokeHelper("parseLiveTracker", rapidoNotif, null) as com.alauncher.app.ui.home.ContextualIsland
        assertEquals("🚕 Captain is arriving: Your ride is 2 mins away", rapidoIsland.text)

        val railOneNotif = LauncherNotification(
            key = "railone", packageName = "com.railone.app", appName = "RailOne",
            title = "Train 12626 Status", text = "PNR status updated, platform 4", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val railOneIsland = invokeHelper("parseLiveTracker", railOneNotif, null) as com.alauncher.app.ui.home.ContextualIsland
        assertEquals("🚆 Train 12626 Status: PNR status updated, platform 4", railOneIsland.text)

        val travelKeywordNotif = LauncherNotification(
            key = "travel_kw", packageName = "com.some.app", appName = "Travel App",
            title = "Seat confirmation", text = "Your seat is confirmed, berth 24", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val travelKeywordIsland = invokeHelper("parseLiveTracker", travelKeywordNotif, null) as com.alauncher.app.ui.home.ContextualIsland
        assertEquals("🚆 Seat confirmation: Your seat is confirmed, berth 24", travelKeywordIsland.text)

        val travelPromoNotif = LauncherNotification(
            key = "travel_promo", packageName = "com.railone.app", appName = "RailOne",
            title = "Special Holiday Offer", text = "Get 20% discount on hotel bookings", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseLiveTracker", travelPromoNotif, null))

        // BookMyShow seat false positive test case
        val bookMyShowNotif = LauncherNotification(
            key = "bms_seat", packageName = "com.bt.leslie.play", appName = "BookMyShow",
            title = "Watching a movie feels better when \uD83D\uDC47",
            text = "You've got your BestSeller Seat secured \uD83E\uDD1D",
            postTime = 0,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseLiveTracker", bookMyShowNotif, null))

        // System Utility VPN/Casting Connected vs Disconnected/Available
        val vpnConnectedNotif = LauncherNotification(
            key = "vpn_ok", packageName = "com.vpn", appName = "VPN",
            title = "VPN Connected", text = "Secure connection active", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val vpnDisconnectedNotif = LauncherNotification(
            key = "vpn_fail", packageName = "com.vpn", appName = "VPN",
            title = "VPN Disconnected", text = "Connection failed", postTime = 0,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val castActiveNotif = LauncherNotification(
            key = "cast_ok", packageName = "com.android.systemui", appName = "System",
            title = "Casting screen", text = "Screen casting in progress", postTime = 0,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3
        )
        val castAvailableNotif = LauncherNotification(
            key = "cast_avail", packageName = "com.android.systemui", appName = "System",
            title = "Cast available", text = "Nearby device found", postTime = 0,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        val vpnIsland = invokeHelper("parseLiveTracker", vpnConnectedNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(vpnIsland)
        assertEquals("🔒 VPN Connected: Secure connection active", vpnIsland?.text)
        
        assertNull(invokeHelper("parseLiveTracker", vpnDisconnectedNotif, null))

        val castIsland = invokeHelper("parseLiveTracker", castActiveNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(castIsland)
        assertEquals("📺 Casting screen: Screen casting in progress", castIsland?.text)

        assertNull(invokeHelper("parseLiveTracker", castAvailableNotif, null))

        // New test cases for resolveContextualIsland priority rules
        val batteryHot = com.alauncher.app.ui.home.HomeViewModel.BatteryInfo(
            level = 90, isCharging = false, isFastCharging = false, chargeTimeRemainingMs = 0L, temperatureCelsius = 46.0f
        )
        val lowBattery = com.alauncher.app.ui.home.HomeViewModel.BatteryInfo(
            level = 10, isCharging = false, isFastCharging = false, chargeTimeRemainingMs = 0L, temperatureCelsius = 35.0f
        )
        val normalBattery = com.alauncher.app.ui.home.HomeViewModel.BatteryInfo(
            level = 80, isCharging = false, isFastCharging = false, chargeTimeRemainingMs = 0L, temperatureCelsius = 35.0f
        )
        val defaultSettings = com.alauncher.app.data.settings.SettingsState(calendarEnabled = true)

        // OTP should override critical battery hot
        val otpActiveNotif = LauncherNotification(
            key = "otp_act", packageName = "com.sms", appName = "SMS",
            title = "OTP", text = "Your OTP code is 9988", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 4
        )
        val otpAndHotIsland = invokeHelper(
            "resolveContextualIsland", batteryHot, listOf(otpActiveNotif), false, null, null, false, emptyList<com.alauncher.app.data.calendar.CalendarEvent>(), defaultSettings, null
        ) as com.alauncher.app.ui.home.ContextualIsland
        assertEquals("🔑 OTP Code: 9988", otpAndHotIsland.text)

        // Critical battery hot should override upcoming calendar event
        val upcomingMeeting = com.alauncher.app.data.calendar.CalendarEvent(
            title = "Weekly Sync Meeting", startTime = System.currentTimeMillis() + 10 * 60 * 1000, endTime = System.currentTimeMillis() + 40 * 60 * 1000, isAllDay = false
        )
        val hotBatteryIsland = invokeHelper(
            "resolveContextualIsland", batteryHot, emptyList<LauncherNotification>(), false, null, null, false, listOf(upcomingMeeting), defaultSettings, null
        ) as com.alauncher.app.ui.home.ContextualIsland
        assertTrue(hotBatteryIsland.text.startsWith("🔥 Device running hot"))

        // Upcoming meeting should display normally on normal battery
        val upcomingMeetingIsland = invokeHelper(
            "resolveContextualIsland", normalBattery, emptyList<LauncherNotification>(), false, null, null, false, listOf(upcomingMeeting), defaultSettings, null
        ) as com.alauncher.app.ui.home.ContextualIsland
        assertTrue(upcomingMeetingIsland.text.startsWith("📅 Up next: Weekly Sync Meeting"))

        // Critical battery low should display normally
        val lowBatteryIsland = invokeHelper(
            "resolveContextualIsland", lowBattery, emptyList<LauncherNotification>(), false, null, null, false, emptyList<com.alauncher.app.data.calendar.CalendarEvent>(), defaultSettings, null
        ) as com.alauncher.app.ui.home.ContextualIsland
        assertEquals("⚠️ Low Battery (10%) • Plug in", lowBatteryIsland.text)

        // Test filtering: disable UPCOMING_MEETING in settings
        val settingsNoMeeting = com.alauncher.app.data.settings.SettingsState(
            calendarEnabled = true,
            enabledIslandCategories = defaultSettings.enabledIslandCategories - "UPCOMING_MEETING"
        )
        val upcomingMeetingFiltered = invokeHelper(
            "resolveContextualIsland", normalBattery, emptyList<LauncherNotification>(), false, null, null, false, listOf(upcomingMeeting), settingsNoMeeting, null
        )
        assertNull(upcomingMeetingFiltered)

        // Test filtering: disable CRITICAL_ALERT in settings
        val settingsNoCritical = com.alauncher.app.data.settings.SettingsState(
            calendarEnabled = true,
            enabledIslandCategories = defaultSettings.enabledIslandCategories - "CRITICAL_ALERT"
        )
        val lowBatteryFiltered = invokeHelper(
            "resolveContextualIsland", lowBattery, emptyList<LauncherNotification>(), false, null, null, false, emptyList<com.alauncher.app.data.calendar.CalendarEvent>(), settingsNoCritical, null
        )
        assertNull(lowBatteryFiltered)

        // Test active upcoming meeting notification display in resolveContextualIsland
        val teamsMeetingNotif = LauncherNotification(
            key = "teams_meet", packageName = "com.microsoft.teams", appName = "Teams",
            title = "Final Sync on SAGA-1327 Tickets", text = "Sync starting in 10 mins", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 4
        )
        val teamsMeetingIsland = invokeHelper(
            "resolveContextualIsland", normalBattery, listOf(teamsMeetingNotif), false, null, null, false, emptyList<com.alauncher.app.data.calendar.CalendarEvent>(), defaultSettings, null
        ) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(teamsMeetingIsland)
        assertEquals(com.alauncher.app.ui.home.ContextualIslandType.UPCOMING_MEETING, teamsMeetingIsland?.type)
        assertTrue(teamsMeetingIsland?.text?.contains("Final Sync on SAGA-1327 Tickets") == true)

        // 1. Fresh active Teams meeting notification (age = 2 mins) vs database-backed meeting.
        // The fresh active notification should win.
        val freshTeamsMeetingNotif = LauncherNotification(
            key = "teams_meet_fresh", packageName = "com.microsoft.teams", appName = "Teams",
            title = "Final Sync on SAGA-1327 Tickets", text = "Sync starting in 10 mins", postTime = System.currentTimeMillis() - 2 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 4
        )
        val databaseMeeting = com.alauncher.app.data.calendar.CalendarEvent(
            title = "Database Sync Meeting", startTime = System.currentTimeMillis() + 10 * 60 * 1000, endTime = System.currentTimeMillis() + 40 * 60 * 1000, isAllDay = false
        )
        val freshIsland = invokeHelper(
            "resolveContextualIsland", normalBattery, listOf(freshTeamsMeetingNotif), false, null, null, false, listOf(databaseMeeting), defaultSettings, null
        ) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(freshIsland)
        assertTrue(freshIsland?.text?.contains("Final Sync on SAGA-1327 Tickets") == true)

        // 2. Older active Teams meeting notification (age = 10 mins) vs database-backed meeting.
        // The database-backed meeting should win.
        val olderTeamsMeetingNotif = LauncherNotification(
            key = "teams_meet_older", packageName = "com.microsoft.teams", appName = "Teams",
            title = "Final Sync on SAGA-1327 Tickets", text = "Sync starting in 10 mins", postTime = System.currentTimeMillis() - 10 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 4
        )
        val olderIsland = invokeHelper(
            "resolveContextualIsland", normalBattery, listOf(olderTeamsMeetingNotif), false, null, null, false, listOf(databaseMeeting), defaultSettings, null
        ) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(olderIsland)
        assertTrue(olderIsland?.text?.contains("Database Sync Meeting") == true)
    }

    @Test
    fun testQuietHoursActiveNow() {
        val cal = java.util.Calendar.getInstance()
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMin = cal.get(java.util.Calendar.MINUTE)

        // 1. Quiet hours covering the current time
        val startHour = (currentHour - 1 + 24) % 24
        val endHour = (currentHour + 1) % 24
        val startStr = String.format(java.util.Locale.ROOT, "%02d:%02d", startHour, currentMin)
        val endStr = String.format(java.util.Locale.ROOT, "%02d:%02d", endHour, currentMin)

        val settingsActive = com.alauncher.app.data.settings.SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = startStr,
            quietHoursEnd = endStr
        )
        assertTrue(settingsActive.isQuietHoursActiveNow())

        // 2. Quiet hours not covering the current time
        val startHourInactive = (currentHour + 1) % 24
        val endHourInactive = (currentHour + 2) % 24
        val startStrInactive = String.format(java.util.Locale.ROOT, "%02d:%02d", startHourInactive, currentMin)
        val endStrInactive = String.format(java.util.Locale.ROOT, "%02d:%02d", endHourInactive, currentMin)

        val settingsInactive = com.alauncher.app.data.settings.SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = startStrInactive,
            quietHoursEnd = endStrInactive
        )
        assertFalse(settingsInactive.isQuietHoursActiveNow())

        // 3. Disabled quiet hours
        val settingsDisabled = com.alauncher.app.data.settings.SettingsState(
            quietHoursEnabled = false,
            quietHoursStart = startStr,
            quietHoursEnd = endStr
        )
        assertFalse(settingsDisabled.isQuietHoursActiveNow())
    }

    @Test
    fun testTransactionalAlerts() {
        // Allocate HomeViewModel and setup dependencies
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val shownTxField = homeViewModel.javaClass.getDeclaredField("shownTransactions")
        shownTxField.isAccessible = true
        shownTxField.set(homeViewModel, java.util.concurrent.ConcurrentHashMap<String, com.alauncher.app.ui.home.HomeViewModel.TransactionRecord>())

        val dummyRepo = com.alauncher.app.data.notification.NotificationRepository(
            filterEngine = com.alauncher.app.domain.notification.NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val repoField = homeViewModel.javaClass.getDeclaredField("notificationRepository")
        repoField.isAccessible = true
        repoField.set(homeViewModel, dummyRepo)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(
                TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            ),
            transactionAllowedApps = setOf(
                "com.chase", "com.hdfc", "com.paytm", "net.one97.paytm",
                "com.myntra", "com.truecaller", "com.google.android.apps.messaging"
            )
        )

        // 1. Debit test
        val debitNotif = LauncherNotification(
            key = "tx1", packageName = "com.chase", appName = "Chase",
            title = "Transaction Alert", text = "Charged $42.50 at Trader Joe's.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val result1 = invokeHelper("parseTransactionAlert", debitNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result1)
        assertEquals("💸 Spent: $42.50 at Trader Joe's", result1?.text)
        assertEquals(TransactionAlertType.DEBIT, result1?.transactionDetails?.type)

        // 2. INR Debit test
        val inrNotif = LauncherNotification(
            key = "tx2", packageName = "com.hdfc", appName = "HDFC",
            title = "Debit Alert", text = "Rs. 2,500 debited for Swiggy payment.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val result2 = invokeHelper("parseTransactionAlert", inrNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result2)
        assertEquals("💸 Spent: Rs.2500 at Swiggy", result2?.text)

        // 3. Exclude Promo test
        val promoNotif = LauncherNotification(
            key = "tx_promo", packageName = "com.paytm", appName = "Paytm",
            title = "Offers for you", text = "Get Rs.500 cashback on spend of Rs.5,000", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultPromo = invokeHelper("parseTransactionAlert", promoNotif, settings)
        assertNull(resultPromo)

        // 3.5. Exclude Paytm Win Big Promo test
        val paytmPromoNotif = LauncherNotification(
            key = "tx_paytm_promo", packageName = "net.one97.paytm", appName = "Paytm",
            title = "Pay Bill, Win Big", text = "Pay Electricity Bill on Paytm and stand a chance to win up to ₹2,000", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultPaytmPromo = invokeHelper("parseTransactionAlert", paytmPromoNotif, settings)
        assertNull(resultPaytmPromo)

        // 3.6. Exclude flat cashback / off marketing currency patterns tests
        val flatCashbackNotif = LauncherNotification(
            key = "tx_flat_cashback", packageName = "net.one97.paytm", appName = "Paytm",
            title = "Recharge Offer", text = "Get flat ₹50 cashback on your mobile recharge.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseTransactionAlert", flatCashbackNotif, settings))

        val offPromoNotif = LauncherNotification(
            key = "tx_off_promo", packageName = "net.one97.paytm", appName = "Paytm",
            title = "Food Discount", text = "Save ₹100 off on Swiggy order today.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseTransactionAlert", offPromoNotif, settings))

        // Test up to / upto patterns
        val upToPromoNotif1 = LauncherNotification(
            key = "tx_upto_1", packageName = "net.one97.paytm", appName = "Paytm",
            title = "Recharge Promo", text = "Get up to ₹100 cashback on mobile recharge.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseTransactionAlert", upToPromoNotif1, settings))

        val upToPromoNotif2 = LauncherNotification(
            key = "tx_upto_2", packageName = "net.one97.paytm", appName = "Paytm",
            title = "Recharge Promo", text = "Get upto ₹100 cashback on mobile recharge.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseTransactionAlert", upToPromoNotif2, settings))

        // 4. Exclude OTP test
        val otpNotif = LauncherNotification(
            key = "tx_otp", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "Bank OTP", text = "OTP for transaction of Rs. 2,500 is 123456", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultOtp = invokeHelper("parseTransactionAlert", otpNotif, settings)
        assertNull(resultOtp)


        // 6. Deduplication test
        val duplicateNotif = LauncherNotification(
            key = "tx_dup", packageName = "com.paytm", appName = "Paytm",
            title = "Debit Alert", text = "Rs. 2,500 debited for Swiggy payment.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultDup = invokeHelper("parseTransactionAlert", duplicateNotif, settings)
        assertNull(resultDup) // Already processed in test #2


        // 8. Expiration check: older than 15 minutes should be ignored
        val expiredNotif = LauncherNotification(
            key = "tx_expired", packageName = "com.chase", appName = "Chase",
            title = "Transaction Alert", text = "Charged $42.50 at Trader Joe's.", postTime = System.currentTimeMillis() - 16 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultExpired = invokeHelper("parseTransactionAlert", expiredNotif, settings)
        assertNull(resultExpired)

        // 9. End-to-end resolution in resolveContextualIsland

        val debitNotif2 = LauncherNotification(
            key = "tx9", packageName = "com.chase", appName = "Chase",
            title = "Transaction Alert", text = "Charged $42.50 at Whole Foods.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val activeNotifs = listOf(debitNotif2)
        val resolved = invokeHelper(
            "resolveContextualIsland",
            null, // battery
            activeNotifs,
            false, // isDnd
            null, // storageWarning
            null, // bluetoothText
            false, // isHotspotActive
            emptyList<com.alauncher.app.data.calendar.CalendarEvent>(),
            settings,
            null // copiedOtpKey
        ) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(resolved)
        assertEquals(com.alauncher.app.ui.home.ContextualIslandType.BILL_TRANSACTIONAL, resolved?.type)
        assertEquals("💸 Spent: $42.50 at Whole Foods", resolved?.text)

        // 10. Verification of details in title with masked card (Truecaller bug check)
        val truecallerBugNotif = LauncherNotification(
            key = "tx_truecaller", packageName = "com.truecaller", appName = "Truecaller",
            title = "INR 36.00 spent on YES BANK Card X0749.", text = "", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultTruecaller = invokeHelper("parseTransactionAlert", truecallerBugNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNull(resultTruecaller?.transactionDetails?.merchantOrBiller)
        assertEquals("💸 Spent: INR36", resultTruecaller?.text)

        // 11. Transaction validation: debit alert without parsed amount should be ignored
        val noAmountDebitNotif = LauncherNotification(
            key = "tx_no_amount", packageName = "com.myntra", appName = "Myntra",
            title = "Order update", text = "Payment successful for your order. Your items will be shipped soon.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultNoAmount = invokeHelper("parseTransactionAlert", noAmountDebitNotif, settings)
        assertNull(resultNoAmount)
    }

    @Test
    fun testMediumNotification() {
        val parser = com.alauncher.app.domain.notification.NotificationParser()
        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledTransactionSubtypes = setOf(
                com.alauncher.app.ui.home.TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                com.alauncher.app.ui.home.TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                com.alauncher.app.ui.home.TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                com.alauncher.app.ui.home.TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            )
        )

        // 1. Medium article recommendation containing "sent" and dollar amount
        val notif = LauncherNotification(
            key = "medium_test",
            packageName = "com.medium.reader",
            appName = "Medium",
            title = "Medium",
            text = "Vincent sent a new story: Spend: $60 at a Coding Tool Nobody Owns Anymore",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        assertNull("Medium content notifications should be filtered out by package exclusions", parser.parseTransactionDetails(notif, settings))

        // 2. Generic chat message containing embedded keywords (consent, payload) and amount but no trust signal
        val chatNotif = LauncherNotification(
            key = "chat_test",
            packageName = "com.chat.generic",
            appName = "Messenger",
            title = "Friend",
            text = "Your consent is required for the payload of $100.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        assertNull("Messages without financial trust signals should be filtered out", parser.parseTransactionDetails(chatNotif, settings))

        // 3. Generic SMS containing "sent" and amount but no transaction indicators
        val smsNotif = LauncherNotification(
            key = "sms_test",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "John",
            text = "Vincent sent you a photo. Need $15.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        assertNull("SMS without financial trust signals should be filtered out", parser.parseTransactionDetails(smsNotif, settings))

        // 4. Valid Bank transaction notification containing app trust signal (Chase) and verb-prep phrase (charged at)
        val chaseNotif = LauncherNotification(
            key = "chase_test",
            packageName = "com.chase",
            appName = "Chase",
            title = "Chase Alert",
            text = "Charged $42.50 at Trader Joe's.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        val chaseRes = parser.parseTransactionDetails(chaseNotif, settings)
        assertNotNull("Chase banking notification with charged at should be parsed", chaseRes)
        assertEquals(42.5, chaseRes?.amount?.value)
        assertEquals("Trader Joe's", chaseRes?.merchantOrBiller)

        // 5. Valid SMS Bank notification containing account reference (A/c) and strict phrase (debited)
        val smsBankNotif = LauncherNotification(
            key = "sms_bank",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "HDFC Bank",
            text = "Rs. 200 debited from A/c XX1234.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        val smsBankRes = parser.parseTransactionDetails(smsBankNotif, settings)
        assertNotNull("SMS banking notification with debited from A/c should be parsed", smsBankRes)
        assertEquals(200.0, smsBankRes?.amount?.value)
    }

    @Test
    fun testNotificationParserRobustness() {
        val parser = com.alauncher.app.domain.notification.NotificationParser()
        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledTransactionSubtypes = setOf(com.alauncher.app.ui.home.TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT)
        )

        // 1. Masked card suffixes
        val cardMask1 = LauncherNotification(key="k1", packageName="p", appName="a", title="Spent", text="Charged $10 on card xx0749 at Store", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val res1 = parser.parseTransactionDetails(cardMask1, settings)
        assertEquals("0749", res1?.accountSuffix)

        val cardMask2 = LauncherNotification(key="k2", packageName="p", appName="a", title="Spent", text="Charged $10 on card *0749 at Store", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val res2 = parser.parseTransactionDetails(cardMask2, settings)
        assertEquals("0749", res2?.accountSuffix)

        val cardMask3 = LauncherNotification(key="k3", packageName="p", appName="a", title="Spent", text="Charged $10 on card #0749 at Store", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val res3 = parser.parseTransactionDetails(cardMask3, settings)
        assertEquals("0749", res3?.accountSuffix)

        // 2. Varying decimals
        val decimals1 = LauncherNotification(key="d1", packageName="p", appName="a", title="Spent", text="Charged INR 10.5 at Store", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resD1 = parser.parseTransactionDetails(decimals1, settings)
        assertEquals(10.5, resD1?.amount?.value)

        val decimals2 = LauncherNotification(key="d2", packageName="p", appName="a", title="Spent", text="Charged INR 10.555 at Store", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resD2 = parser.parseTransactionDetails(decimals2, settings)
        assertEquals(10.555, resD2?.amount?.value)

        // 3. Preposition 'by'
        val prepBy = LauncherNotification(key="b1", packageName="p", appName="a", title="Spent", text="Paid $10 by Amazon.in", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resB1 = parser.parseTransactionDetails(prepBy, settings)
        assertEquals("Amazon", resB1?.merchantOrBiller) // Amazon is cleaned

        // 4. Merchant names with dots/slashes/hashes
        val merchantDots = LauncherNotification(key="m1", packageName="p", appName="a", title="Spent", text="Spent $10 at ZOMATO#123", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resM1 = parser.parseTransactionDetails(merchantDots, settings)
        assertEquals("Zomato", resM1?.merchantOrBiller) // Zomato is cleaned

        val merchantSlash = LauncherNotification(key="m2", packageName="p", appName="a", title="Spent", text="Spent $10 at MY/STORE", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resM2 = parser.parseTransactionDetails(merchantSlash, settings)
        assertEquals("MY/STORE", resM2?.merchantOrBiller)

        val merchantDot = LauncherNotification(key="m3", packageName="p", appName="a", title="Spent", text="Spent $10 at Google.com", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resM3 = parser.parseTransactionDetails(merchantDot, settings)
        assertEquals("Google.com", resM3?.merchantOrBiller)

        // 5. EU Decimals (comma)
        val euDecimal = LauncherNotification(key="eu1", packageName="p", appName="a", title="Spent", text="Charged € 10,50 with Store", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resEU = parser.parseTransactionDetails(euDecimal, settings)
        assertEquals(10.50, resEU?.amount?.value)
        assertEquals("Store", resEU?.merchantOrBiller)

        // 6. Conversational Prepositions
        val prepVia = LauncherNotification(key="v1", packageName="p", appName="a", title="Spent", text="Payment of $10 via @user_name", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resVia = parser.parseTransactionDetails(prepVia, settings)
        assertEquals("@user_name", resVia?.merchantOrBiller)

        val prepThrough = LauncherNotification(key="t1", packageName="p", appName="a", title="Spent", text="Charged 500 through My_App", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resThrough = parser.parseTransactionDetails(prepThrough, settings)
        assertEquals("My_App", resThrough?.merchantOrBiller)

        // 7. Generic Currency
        val genericCurr = LauncherNotification(key="gc1", packageName="p", appName="a", title="Spent", text="AED 100.50 spent at Mall", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resGC = parser.parseTransactionDetails(genericCurr, settings)
        assertEquals(100.50, resGC?.amount?.value)
        assertEquals("AED", resGC?.amount?.currencySymbol)

        // 8. Avoid matching card suffix as amount
        val cardSuffixBug = LauncherNotification(key="csb1", packageName="p", appName="a", title="Spent", text="Your account ending in 0749 has been debited for 500.", postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3)
        val resCSB = parser.parseTransactionDetails(cardSuffixBug, settings)
        assertEquals(500.0, resCSB?.amount?.value)
        assertEquals("0749", resCSB?.accountSuffix)

        // 9. Exclude battery/system notifications from transactions
        val batteryNotif = LauncherNotification(
            key="bat1", packageName="com.android.systemui", appName="Battery",
            title="Battery", text="Battery is now charged to 90%. Power saving mode is turned off.",
            postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3
        )
        val resBattery = parser.parseTransactionDetails(batteryNotif, settings)
        assertNull(resBattery)

        // 10. Legitimate bank notification with "charged to" should pass and parse correctly
        val bankChargedToNotif = LauncherNotification(
            key="bank1", packageName="com.google.android.apps.messaging", appName="Messages",
            title="Bank Alert", text="Charged to card ending in 1234: USD 500.",
            postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3
        )
        val resBank = parser.parseTransactionDetails(bankChargedToNotif, settings)
        assertNotNull(resBank)
        assertEquals(500.0, resBank?.amount?.value)
        assertEquals("USD", resBank?.amount?.currencySymbol)
        assertEquals("1234", resBank?.accountSuffix)

        // 11. HDFC "Sent Rs.30.00" alert verification
        val hdfcNotif = LauncherNotification(
            key="hdfc1", packageName="com.google.android.apps.messaging", appName="Messages",
            title="HDFC Bank", text="Sent Rs.30.00 From HDFC Bank A/C *8988 To JEEVAN B On 30/06/26 Ref 654711818709",
            postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3
        )
        val resHdfc = parser.parseTransactionDetails(hdfcNotif, settings)
        if (resHdfc == null || resHdfc.merchantOrBiller != "JEEVAN B") {
            val title = hdfcNotif.title ?: ""
            val text = hdfcNotif.text ?: ""
            val parsingSource = if (title.isNotEmpty() && text.isNotEmpty()) "$title\n$text" else title.ifEmpty { text }
            val prepRegex = Regex("""\b(?:at|to|for|towards|with|via|through)\s+([A-Za-z0-9\s\*'&\-\./#@_]{2,25})\b""", RegexOption.IGNORE_CASE)
            val prepMatch = prepRegex.find(parsingSource)
            println("DEBUG prepMatch value: ${prepMatch?.value}")
            println("DEBUG prepMatch group 1: ${prepMatch?.groupValues?.getOrNull(1)}")
            
            val allMatches = prepRegex.findAll(parsingSource).toList()
            for ((idx, m) in allMatches.withIndex()) {
                println("DEBUG match $idx: ${m.value} -> group 1: ${m.groupValues[1]}")
            }
        }
        assertNotNull(resHdfc)
        assertEquals(30.0, resHdfc?.amount?.value)
        assertEquals("Rs.", resHdfc?.amount?.currencySymbol)
        assertEquals("8988", resHdfc?.accountSuffix)
        assertEquals("JEEVAN B", resHdfc?.merchantOrBiller)
        assertEquals("30/06/26", resHdfc?.dueDate) // parsed date matches correctly!

        // 12. LinkedIn false positive exclusion
        val linkedinNotif = LauncherNotification(
            key="li1", packageName="com.linkedin.android", appName="LinkedIn",
            title="Malta Financial", text="Malta Financial: received from John",
            postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3
        )
        val resLinkedin = parser.parseTransactionDetails(linkedinNotif, settings)
        assertNull(resLinkedin)

        // 13. Microsoft Teams false positive exclusion
        val teamsNotif = LauncherNotification(
            key="teams1", packageName="com.microsoft.teams", appName="Teams",
            title="DSP T&C Signing", text="₹2026 from DSP T&C Signing User - Ji",
            postTime=System.currentTimeMillis(), channelId="c", isOngoing=false, isClearable=true, importance=3
        )
        val resTeams = parser.parseTransactionDetails(teamsNotif, settings)
        assertNull(resTeams)
    }

    @Test
    fun testDownloadAndGeneralNotificationExpiration() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf("HIGH_PRIORITY_TASK", "GENERAL_NOTIFICATION")
        )

        // 1. Completed download (100%) progress notification that has expired (> 5 minutes)
        val expiredCompletedProgress = LauncherNotification(
            key = "p1", packageName = "com.android.providers.downloads", appName = "Downloads",
            title = "Downloading App", text = "Progress: 100% 📥", postTime = System.currentTimeMillis() - 6 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3,
            progress = 100, progressMax = 100
        )
        val result1 = invokeHelper("parseLiveTracker", expiredCompletedProgress, null, settings)
        assertNull(result1)

        // 2. Completed download (100%) progress notification that is fresh (< 5 minutes)
        val freshCompletedProgress = LauncherNotification(
            key = "p2", packageName = "com.android.providers.downloads", appName = "Downloads",
            title = "Downloading App", text = "Progress: 100% 📥", postTime = System.currentTimeMillis() - 2 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3,
            progress = 100, progressMax = 100
        )
        val result2 = invokeHelper("parseLiveTracker", freshCompletedProgress, null, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result2)
        assertEquals("Progress: 100% 📥", result2?.text)

        // 3. Stalled non-ongoing progress notification (> 15 minutes)
        val expiredStalledProgress = LauncherNotification(
            key = "p3", packageName = "com.android.providers.downloads", appName = "Downloads",
            title = "Downloading app", text = "Progress: 45% 📥", postTime = System.currentTimeMillis() - 16 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3,
            progress = 45, progressMax = 100
        )
        val result3 = invokeHelper("parseLiveTracker", expiredStalledProgress, null, settings)
        assertNull(result3)

        // 4. Stalled non-ongoing progress notification (< 15 minutes)
        val freshStalledProgress = LauncherNotification(
            key = "p4", packageName = "com.android.providers.downloads", appName = "Downloads",
            title = "Downloading app", text = "Progress: 45% 📥", postTime = System.currentTimeMillis() - 10 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3,
            progress = 45, progressMax = 100
        )
        val result4 = invokeHelper("parseLiveTracker", freshStalledProgress, null, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result4)
        assertEquals("Progress: 45% 📥", result4?.text)

        // 5. Ongoing progress notification (> 15 minutes) should NOT expire while ongoing
        val ongoingProgress = LauncherNotification(
            key = "p5", packageName = "com.android.providers.downloads", appName = "Downloads",
            title = "Downloading app", text = "Progress: 45% 📥", postTime = System.currentTimeMillis() - 20 * 60 * 1000,
            channelId = "ch1", isOngoing = true, isClearable = false, importance = 3,
            progress = 45, progressMax = 100
        )
        val result5 = invokeHelper("parseLiveTracker", ongoingProgress, null, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result5)
        assertEquals("Progress: 45% 📥", result5?.text)

        // 6. Expired non-ongoing VPN notification
        val expiredVpn = LauncherNotification(
            key = "vpn1", packageName = "com.android.settings", appName = "Settings",
            title = "VPN Connection", text = "VPN Connected", postTime = System.currentTimeMillis() - 16 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultVpn1 = invokeHelper("parseLiveTracker", expiredVpn, null, settings)
        assertNull(resultVpn1)

        // 7. Fresh non-ongoing VPN notification
        val freshVpn = LauncherNotification(
            key = "vpn2", packageName = "com.android.settings", appName = "Settings",
            title = "VPN Connection", text = "VPN Connected", postTime = System.currentTimeMillis() - 5 * 60 * 1000,
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val resultVpn2 = invokeHelper("parseLiveTracker", freshVpn, null, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(resultVpn2)
        assertEquals("🔒 VPN Connection: VPN Connected", resultVpn2?.text)
    }

    @Test
    fun testOtpBypassFocusMode() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val dummyRepo = com.alauncher.app.data.notification.NotificationRepository(
            filterEngine = com.alauncher.app.domain.notification.NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val repoField = homeViewModel.javaClass.getDeclaredField("notificationRepository")
        repoField.isAccessible = true
        repoField.set(homeViewModel, dummyRepo)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        // 1. Verify that classify returns PRIORITY for OTP when Focus Mode is active
        val rawOtpNotif = LauncherNotification(
            key = "otp_muted", packageName = "com.whatsapp", appName = "WhatsApp",
            title = "Verification", text = "Your code is 884723", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3,
            filterCategory = FilterCategory.NORMAL
        )
        
        val filterEngine = com.alauncher.app.domain.notification.NotificationFilterEngine()
        val classifiedCategory = filterEngine.classify(
            notification = rawOtpNotif,
            settings = com.alauncher.app.data.settings.SettingsState(isFocusModeActive = true)
        )
        assertEquals(FilterCategory.PRIORITY, classifiedCategory)

        // 2. Set the classified category on the notification
        val otpNotifWithCategory = rawOtpNotif.copy(filterCategory = classifiedCategory)

        val settings = com.alauncher.app.data.settings.SettingsState(
            calendarEnabled = false,
            enabledIslandCategories = setOf("OTP")
        )

        // Resolve contextual island
        val resolved = invokeHelper(
            "resolveContextualIsland",
            null, // battery
            listOf(otpNotifWithCategory),
            false, // isDnd
            null, // storageWarning
            null, // bluetoothText
            false, // isHotspotActive
            emptyList<com.alauncher.app.data.calendar.CalendarEvent>(),
            settings,
            null // copiedOtpKey
        ) as? com.alauncher.app.ui.home.ContextualIsland

        assertNotNull(resolved)
        assertEquals("🔑 OTP Code: 884723", resolved?.text)
        assertEquals(com.alauncher.app.ui.home.ContextualIslandType.OTP, resolved?.type)
    }

    @Test
    fun testTransactionDeduplicationMultipleTicks() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        val shownTxField = homeViewModel.javaClass.getDeclaredField("shownTransactions")
        shownTxField.isAccessible = true
        shownTxField.set(homeViewModel, java.util.concurrent.ConcurrentHashMap<String, com.alauncher.app.ui.home.HomeViewModel.TransactionRecord>())

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT)
        )

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val txNotification1 = LauncherNotification(
            key = "tx_key_1", packageName = "com.bank", appName = "Bank",
            title = "Debit Alert", text = "Charged $45.00 at Starbucks", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        val txNotification2 = LauncherNotification(
            key = "tx_key_2", packageName = "com.bank", appName = "Bank",
            title = "Debit Alert", text = "Charged $45.00 at Starbucks", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        // First parsing: should be allowed
        val result1 = invokeHelper("parseTransactionAlert", txNotification1, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result1)
        assertEquals("💸 Spent: $45 at Starbucks", result1?.text)

        // Second parsing (same notification key on a new tick): should STILL be allowed
        val result2 = invokeHelper("parseTransactionAlert", txNotification1, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result2)
        assertEquals("💸 Spent: $45 at Starbucks", result2?.text)

        // Third parsing (different notification key, same details): should be suppressed/deduplicated
        val result3 = invokeHelper("parseTransactionAlert", txNotification2, settings)
        assertNull(result3)
    }

    @Test
    fun testRefundKeywordParsing() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        val shownTxField = homeViewModel.javaClass.getDeclaredField("shownTransactions")
        shownTxField.isAccessible = true
        shownTxField.set(homeViewModel, java.util.concurrent.ConcurrentHashMap<String, com.alauncher.app.ui.home.HomeViewModel.TransactionRecord>())

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT)
        )

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val refundNotif = LauncherNotification(
            key = "tx_refund", packageName = "com.bank", appName = "Bank",
            title = "Refund Completed", text = "Refund of $29.99 processed successfully.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        val result = invokeHelper("parseTransactionAlert", refundNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result)
        assertEquals(com.alauncher.app.ui.home.TransactionAlertType.REFUND, result?.transactionDetails?.type)
        assertEquals("↩️ Refund: $29.99", result?.text)
    }

    @Test
    fun testPayZappDeductedAlert() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        val shownTxField = homeViewModel.javaClass.getDeclaredField("shownTransactions")
        shownTxField.isAccessible = true
        shownTxField.set(homeViewModel, java.util.concurrent.ConcurrentHashMap<String, com.alauncher.app.ui.home.HomeViewModel.TransactionRecord>())

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(
                TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            )
        )

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val payzappNotif = LauncherNotification(
            key = "payzapp_test", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "AD-HDFCBN-S", text = "Rs.500 Deducted From PayZapp Wallet On 06-07-2026 18:37:18", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        val result = invokeHelper("parseTransactionAlert", payzappNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result)
        assertEquals(com.alauncher.app.ui.home.TransactionAlertType.DEBIT, result?.transactionDetails?.type)
        assertEquals("💸 Spent: Rs.500 at PayZapp Wallet", result?.text)
    }

    @Test
    fun testPnbDebitedAlert() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        val shownTxField = homeViewModel.javaClass.getDeclaredField("shownTransactions")
        shownTxField.isAccessible = true
        shownTxField.set(homeViewModel, java.util.concurrent.ConcurrentHashMap<String, com.alauncher.app.ui.home.HomeViewModel.TransactionRecord>())

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(
                TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            )
        )

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val pnbNotif = LauncherNotification(
            key = "pnb_test", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "Punjab national bank", text = "Dear Customer,your A/c XX9737 debited with Rs.2.36 towards bank charges on 05-07-2026.Available balance: Rs.118401.13-PNB", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        val result = invokeHelper("parseTransactionAlert", pnbNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result)
        assertEquals(com.alauncher.app.ui.home.TransactionAlertType.DEBIT, result?.transactionDetails?.type)
        assertEquals("💸 Spent: Rs.2.36 at bank charges", result?.text)
    }

    @Test
    fun testFastTagAlert() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        val shownTxField = homeViewModel.javaClass.getDeclaredField("shownTransactions")
        shownTxField.isAccessible = true
        shownTxField.set(homeViewModel, java.util.concurrent.ConcurrentHashMap<String, com.alauncher.app.ui.home.HomeViewModel.TransactionRecord>())

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(
                TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            )
        )

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val fasttagNotif = LauncherNotification(
            key = "fasttag_test", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "VD-HDFCBK-S", text = "FASTTag Alert Rs.500 added to HDFC Bank NETC FASTTag 19000015922518 on 2026-07-06 18:37:21.0. Activate Auto-Recharge: https://1.hdfc.bank.in/HDFCBK/s/a/6xbX7vvL", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        val result = invokeHelper("parseTransactionAlert", fasttagNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result)
        assertEquals(com.alauncher.app.ui.home.TransactionAlertType.CREDIT, result?.transactionDetails?.type)
        assertEquals("💰 Received: Rs.500 from HDFC Bank NETC FASTTag", result?.text)
    }

    @Test
    fun testKiwiBillDueAlert() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val contextField = homeViewModel.javaClass.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(homeViewModel, context)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        val shownTxField = homeViewModel.javaClass.getDeclaredField("shownTransactions")
        shownTxField.isAccessible = true
        shownTxField.set(homeViewModel, java.util.concurrent.ConcurrentHashMap<String, com.alauncher.app.ui.home.HomeViewModel.TransactionRecord>())

        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(
                TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            )
        )

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        val kiwiNotif = LauncherNotification(
            key = "kiwi_test", packageName = "com.kiwi", appName = "Kiwi",
            title = "Kiwi", text = "Final call: Credit card bill due today! Pay your YES Bank Card ending in XXXX-0749 bill now and avoid late fees. Convert ₹10021.0 into easy EMIs if you need flexibility. T&C Apply.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )

        val result = invokeHelper("parseTransactionAlert", kiwiNotif, settings) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull(result)
        assertEquals(com.alauncher.app.ui.home.TransactionAlertType.BILL_DUE, result?.transactionDetails?.type)
        assertTrue(result?.text?.startsWith("📅 Bill due: ₹10021") == true)
    }

    @Test
    fun testOutlookTeamsCalendarReminderAndAxisBankOverdue() {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val homeViewModel = allocateInstanceMethod.invoke(unsafe, com.alauncher.app.ui.home.HomeViewModel::class.java)

        val parserField = homeViewModel.javaClass.getDeclaredField("parser")
        parserField.isAccessible = true
        parserField.set(homeViewModel, com.alauncher.app.domain.notification.NotificationParser())

        fun invokeHelper(name: String, vararg args: Any?): Any? {
            val method = homeViewModel.javaClass.declaredMethods.firstOrNull { 
                it.name == name && it.parameterTypes.size == args.size 
            } ?: homeViewModel.javaClass.declaredMethods.first {
                it.name == name && it.parameterTypes.size == args.size + 1
            }
            val finalArgs = if (method.parameterTypes.size == args.size + 1) {
                (args.toList() + emptyMap<String, Long>()).toTypedArray()
            } else {
                args
            }
            method.isAccessible = true
            return method.invoke(homeViewModel, *finalArgs)
        }

        // 1. Outlook email notification (decommissioning email) should NOT be classified as calendar reminder
        val outlookEmail = LauncherNotification(
            key = "outlook_email", packageName = "com.microsoft.office.outlook", appName = "Outlook",
            title = "Adeel Sharon", text = "WSO2 Identity Server Global Decommissioning Hello All Hope this email finds you well. WSO2 Platform Teams are excited to announce that the migration from WSO2 Identity to Auth0 is complete from all teams and applications (Internal and external) across all Business Units. This has been a long j", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertFalse(invokeHelper("isCalendarReminderNotification", outlookEmail) as Boolean)

        // 2. Teams activity notification should NOT be classified as calendar reminder
        val teamsActivity = LauncherNotification(
            key = "teams_activity", packageName = "com.microsoft.office.outlook", appName = "Outlook",
            title = "Calendar", text = "There's new activity in Teams • You have 1 message, 1 mention", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertFalse(invokeHelper("isCalendarReminderNotification", teamsActivity) as Boolean)

        // 3. Axis Bank overdue message (no amount) should NOT be parsed as transactional alert
        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(
                TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            )
        )
        val axisOverdueMsg = LauncherNotification(
            key = "axis_overdue", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "AD-AXISBK-S", text = "Your Axis Bank Legacy Credit Card A/c no. XX1305 has been reported as over-due to Credit Information Companies as on 30-06-26. Kindly ensure timely payment to avoid such reporting in the future. This intimation is being sent to you as per extant regulatory guidelines.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseTransactionAlert", axisOverdueMsg, settings))

        // 3.5. Paytm notification with ₹0 amount should NOT be parsed as transaction alert
        val zeroPaytmNotif = LauncherNotification(
            key = "paytm_zero", packageName = "net.one97.paytm", appName = "Paytm",
            title = "Axis Bank payment", text = "Paid Rs. 0 using Axis Bank. Convenience fee Rs. 0.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(invokeHelper("parseTransactionAlert", zeroPaytmNotif, settings))
        
        // 4. Outlook/Teams meetings / actual calls should be classified as calendar reminders
        val outlookMeeting = LauncherNotification(
            key = "outlook_meeting", packageName = "com.microsoft.office.outlook", appName = "Outlook",
            title = "Sprint Planning Meeting", text = "Starting in 10 mins (30m) • Join call", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertTrue(invokeHelper("isCalendarReminderNotification", outlookMeeting) as Boolean)
    }

    @Test
    fun testUserReportedNotificationIssues() {
        val parser = com.alauncher.app.domain.notification.NotificationParser()
        val settings = com.alauncher.app.data.settings.SettingsState(
            enabledIslandCategories = setOf(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
            enabledTransactionSubtypes = setOf(
                TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT,
                TransactionSettingsKeys.SUBTYPE_GROUP_BILL,
                TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL,
                TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
            )
        )

        // 1. Zebpay: Crypto Market Update (should NOT parse as transaction)
        val zebpayNotif = LauncherNotification(
            key = "zebpay_market", packageName = "co.zebpay", appName = "Zeb Pay",
            title = "Crypto Market Update", text = "ETH is trading at $1784. Pay via app.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(parser.parseTransactionDetails(zebpayNotif, settings))

        // 2. HDFC SKY: stock picks (should NOT parse as transaction)
        val hdfcSkyNotif = LauncherNotification(
            key = "hdfc_sky_pick", packageName = "com.hdfcsec.sky", appName = "HDFC SKY",
            title = "SHORT-TERM PICK!", text = "Buy IFB INDUSTRIES LTD at ₹1303.5 Target: ₹1400. Pay to trade.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(parser.parseTransactionDetails(hdfcSkyNotif, settings))

        // 2.5. General stock pick without Target/Header keywords (should NOT parse as transaction)
        val generalStockPickNotif = LauncherNotification(
            key = "general_stock_pick", packageName = "com.hdfcsec.sky", appName = "HDFC SKY",
            title = "Recommendation", text = "Buy IFB INDUSTRIES LTD at ₹1303.5 today.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(parser.parseTransactionDetails(generalStockPickNotif, settings))

        // 3. IRIS by Yes Bank: pre-approved loan (should NOT parse as transaction)
        val yesBankNotif = LauncherNotification(
            key = "yes_bank_loan", packageName = "com.yesbank.iris", appName = "IRIS by Yes Bank",
            title = "Fund Your Big Plans with an Express Loan", text = "Get pre-approved Express Loan of ₹600000 on your credit card. Pay in easy EMIs.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(parser.parseTransactionDetails(yesBankNotif, settings))

        // 4. Messages: balance warning (should NOT parse as transaction)
        val messagesAmbNotif = LauncherNotification(
            key = "messages_amb", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "Bank Alert", text = "Low activity and non-maintenance of the required AMB ₹5000 have been observed in A/C 5555****4882. Kindly pay to avoid charges.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(parser.parseTransactionDetails(messagesAmbNotif, settings))

        // 5. Zeta OTP issue: amount vs otp (standard length)
        val zetaOtpNotif = LauncherNotification(
            key = "zeta_otp", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "Bank OTP", text = "The OTP for your transaction amount of Rs. 1882.15 at RATNADEEP is 4956. - Zeta", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("4956", parser.extractOtp(zetaOtpNotif.title, zetaOtpNotif.text))

        // 5.3. Zeta OTP issue: amount vs otp (European decimal format with comma)
        val zetaOtpEuropeanNotif = LauncherNotification(
            key = "zeta_otp_euro", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "Bank OTP", text = "The OTP for your transaction amount of Rs. 1882,15 at RATNADEEP SUPERMARKET is 4956. - Zeta", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("4956", parser.extractOtp(zetaOtpEuropeanNotif.title, zetaOtpEuropeanNotif.text))

        // 5.5. Zeta OTP issue: amount vs otp (long text to force fallback execution)
        val zetaOtpLongNotif = LauncherNotification(
            key = "zeta_otp_long", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "Bank OTP", text = "The OTP for your transaction amount of Rs. 1882.15 at RATNADEEP SUPERMARKET HYDERABAD SECUNDERABAD BRANCH which was initiated on your credit card ends with 5555 is actually 4956. Please do not share it with anyone.", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("4956", parser.extractOtp(zetaOtpLongNotif.title, zetaOtpLongNotif.text))

        // 6. IGRS OTP 1
        val igrsOtp1 = LauncherNotification(
            key = "igrs_otp1", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "IGRS, Telangana", text = "Hi 9999999999 Your Account Verification Otp is 230603 - IGRS, Telangana", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("230603", parser.extractOtp(igrsOtp1.title, igrsOtp1.text))

        // 7. IGRS OTP 2
        val igrsOtp2 = LauncherNotification(
            key = "igrs_otp2", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "IGRS, Telangana", text = "Dear Pradeep Your forgot password otp is 727984 - IGRS, Telangana", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("727984", parser.extractOtp(igrsOtp2.title, igrsOtp2.text))

        // 8. Stratzy OTP
        val stratzyOtp = LauncherNotification(
            key = "stratzy_otp", packageName = "com.google.android.apps.messaging", appName = "Messages",
            title = "Stratzy", text = "Your OTP verification code for Stratzy is 3572, OTP expires in 5 minutes slajdlakd", postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("3572", parser.extractOtp(stratzyOtp.title, stratzyOtp.text))

        // 9. Paytm "Bill Overdue" should be classified as BILL_DUE, NOT PAYMENT_FAILED.
        //    Regression: bare "overdue" was inside failedKeywordsRegex, causing the island
        //    to show "❌ Payment failed: Hyderabad Water" instead of "📅 Bill due: ₹258.3".
        val paytmBillOverdueNotif = LauncherNotification(
            key = "paytm_bill_overdue",
            packageName = "net.one97.paytm",
            appName = "Paytm",
            title = "! URGENT: Bill Overdue!",
            text = "Water bill of \u20b9258.3 for Hyderabad Water (HMWSSB) - 622071031 was due on Jun 30. Pay now to avoid disconnection.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )
        val paytmBillOverdueResult = parser.parseTransactionDetails(paytmBillOverdueNotif, settings)
        assertNotNull("Paytm bill overdue notification should be parsed", paytmBillOverdueResult)
        assertEquals(
            "Bill overdue should be classified as BILL_DUE, not PAYMENT_FAILED",
            TransactionAlertType.BILL_DUE,
            paytmBillOverdueResult?.type
        )
        assertEquals(258.3, paytmBillOverdueResult?.amount?.value ?: 0.0, 0.001)

        // 9.5. Paytm credit card bill due ("Bill due 2 days ago")
        val paytmCreditBillNotif = LauncherNotification(
            key = "paytm_credit_bill_due",
            packageName = "net.one97.paytm",
            appName = "Paytm",
            title = "Bill due 2 days ago",
            text = "₹397 was due 2 days ago for your ICICI Bank credit",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )
        val paytmCreditBillResult = parser.parseTransactionDetails(paytmCreditBillNotif, settings)
        assertNotNull("Paytm credit card bill due notification should be parsed", paytmCreditBillResult)
        assertEquals(TransactionAlertType.BILL_DUE, paytmCreditBillResult?.type)
        assertEquals(397.0, paytmCreditBillResult?.amount?.value ?: 0.0, 0.001)
        assertEquals("₹", paytmCreditBillResult?.amount?.currencySymbol)
        assertEquals("ICICI Bank credit", paytmCreditBillResult?.merchantOrBiller)
        assertEquals("2 days ago", paytmCreditBillResult?.dueDate)

        // 9.6. HDFC SKY SIP promo ("Salary Credit - Top 3 picks this month") - no amount, should NOT parse as transaction
        val hdfcSkySipNotif = LauncherNotification(
            key = "hdfc_sky_sip_promo",
            packageName = "com.hdfcsec.sky",
            appName = "HDFC SKY",
            title = "Salary Credit",
            text = "The smartest firt move: SIP! Top 3 picks this month - Bandhan",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        assertNull("HDFC SKY SIP promo without currency amount should NOT parse as transaction", parser.parseTransactionDetails(hdfcSkySipNotif, settings))

        // 10. ICICI Kiwi credit card payment — should be DEBIT with merchant "Kiwi"
        val iciciBillPaidNotif = LauncherNotification(
            key = "icici_kiwi_paid",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "ICICI Bank",
            text = "Your ICICI Bank Credit Card XXXX-5693 bill of Rs. 397.0 has been successfully paid via Kiwi.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val iciciBillPaidResult = parser.parseTransactionDetails(iciciBillPaidNotif, settings)
        assertNotNull(iciciBillPaidResult)
        assertEquals(TransactionAlertType.DEBIT, iciciBillPaidResult?.type)
        assertEquals(397.0, iciciBillPaidResult?.amount?.value ?: 0.0, 0.001)
        assertEquals("Kiwi", iciciBillPaidResult?.merchantOrBiller)

        // 11. HDFC UPI credit from VPA — merchant should NOT be "VPA <handle>"
        val hdfcUpiCreditNotif = LauncherNotification(
            key = "hdfc_upi_credit",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "HDFC Bank",
            text = "Credit Alert! Rs. 500.00 credited to HDFC Bank A/c XX9347 on 17-07-26 from VPA 9827635472@goaxb (UPI 425668929)",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val hdfcUpiCreditResult = parser.parseTransactionDetails(hdfcUpiCreditNotif, settings)
        assertNotNull(hdfcUpiCreditResult)
        assertEquals(TransactionAlertType.CREDIT, hdfcUpiCreditResult?.type)
        assertEquals(500.0, hdfcUpiCreditResult?.amount?.value ?: 0.0, 0.001)
        // Merchant must not start with "VPA" (raw UPI handle should be excluded)
        assertFalse(
            "Merchant should not be a raw VPA handle",
            hdfcUpiCreditResult?.merchantOrBiller?.startsWith("VPA") == true
        )

        // 12. YES BANK credit card payment received — "your YES BANK" must not leak as merchant
        val yesBankPaymentNotif = LauncherNotification(
            key = "yes_bank_payment_received",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "YES BANK",
            text = "Dear Cardmember, payment of Rs. 11,751.00 is received towards your YES BANK Credit Card ending 7896. It will reflect in your Credit Card within 1-2 working days.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val yesBankPaymentResult = parser.parseTransactionDetails(yesBankPaymentNotif, settings)
        assertNotNull(yesBankPaymentResult)
        assertEquals(TransactionAlertType.CREDIT, yesBankPaymentResult?.type)
        assertEquals(11751.0, yesBankPaymentResult?.amount?.value ?: 0.0, 0.001)
        assertFalse(
            "Merchant must not contain possessive 'your'",
            yesBankPaymentResult?.merchantOrBiller?.lowercase()?.startsWith("your") == true
        )

        // 13. YES BANK UPI debit — merchant should be UPI handle, NOT "YES BANK"
        val yesBankUpiDebitNotif = LauncherNotification(
            key = "yes_bank_upi_debit",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "YES BANK",
            text = "INR 2,753.00 spent on YES BANK Card X0837 @UPI_TATVA MODERN DININ 18-07-2026 03:30:30 pm. Avl Lmt INR 7896.00. SMS BLKCC 0837 to 45678987 if not you",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val yesBankUpiDebitResult = parser.parseTransactionDetails(yesBankUpiDebitNotif, settings)
        assertNotNull(yesBankUpiDebitResult)
        assertEquals(TransactionAlertType.DEBIT, yesBankUpiDebitResult?.type)
        assertEquals(2753.0, yesBankUpiDebitResult?.amount?.value ?: 0.0, 0.001)
        assertEquals("UPI_TATVA MODERN DININ", yesBankUpiDebitResult?.merchantOrBiller)
        assertFalse(
            "Merchant should not be 'YES BANK' — actual merchant is the UPI handle",
            yesBankUpiDebitResult?.merchantOrBiller == "YES BANK"
        )

        // 14. Axis Bank overdue regulatory notice — should be suppressed ("kindly" exclusion)
        val axisOverdueNotif = LauncherNotification(
            key = "axis_overdue_regulatory",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "AD-AXISBK-S",
            text = "Your Axis Bank Legacy Credit Card A/c no. XX1305 has been reported as overdue to Credit information Companies as on 09-07-26. Kindly ensure timely payment to avoid such reporting in the future.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(
            "Axis Bank regulatory overdue notice should be suppressed by 'kindly' exclusion",
            parser.parseTransactionDetails(axisOverdueNotif, settings)
        )

        // 16. Teams meeting invite — "Passcode: xq2wX9DZ" must NOT be extracted as OTP.
        //     Root cause: OTP_ALPHANUM_REGEX matched "Passcode: xq2wX9DZ" before any
        //     meeting-context check ran. Fix: isMeetingInviteContext() guard in
        //     extractOtpFromSingleString + classifier MEETING intent guard in parseLiveTracker.
        val teamsMeetingPasscodeText = """
            You're invited to a Microsoft Teams meeting.
            Meeting ID: 234 567 890 12
            Passcode: xq2wX9DZ
            Join: https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc123/0
        """.trimIndent()
        assertNull(
            "Teams meeting passcode should NOT be extracted as OTP",
            parser.extractOtp(null, teamsMeetingPasscodeText)
        )

        // 16a. Zoom meeting invite — same pattern, different platform.
        val zoomMeetingPasscodeText = """
            Zoom Meeting Invitation
            Meeting ID: 123 456 7890
            Passcode: AB12CD
            Join Zoom Meeting: https://zoom.us/j/1234567890
        """.trimIndent()
        assertNull(
            "Zoom meeting passcode should NOT be extracted as OTP",
            parser.extractOtp(null, zoomMeetingPasscodeText)
        )

        // 16b. Meeting invite with "Meeting ID" but no URL — still blocked by meetingInviteContextRegex.
        val meetingIdOnlyText = "Meeting ID: 987 654 321 • Passcode: ZX9WQ2"
        assertNull(
            "Meeting passcode with Meeting ID context but no URL should NOT be extracted as OTP",
            parser.extractOtp(null, meetingIdOnlyText)
        )

        // 16d. Numeric meeting passcode — blocked by the top-level isMeetingInviteContext guard
        //      which now covers all OTP patterns, not just alphanumeric.
        val numericMeetingPasscode = """
            Zoom Meeting
            Meeting ID: 123 456 789
            Passcode: 123456
        """.trimIndent()
        assertNull(
            "Numeric meeting passcode should NOT be extracted as OTP",
            parser.extractOtp(null, numericMeetingPasscode)
        )

        // 16c. Genuine alphanumeric OTP from a bank (no meeting context) — must still be extracted.
        val genuineAlphanumOtp = "Your security code is: AB12CD"
        assertEquals(
            "Genuine alphanumeric OTP without meeting context should still be extracted",
            "AB12CD",
            parser.extractOtp(null, genuineAlphanumOtp)
        )

        // 17. Max Fashion in-store OTP — "Use OTP 3797 at Max Fashion" form
        //     Root cause: OTP_PATTERN_3 only matched "use <digits> to verify" but not
        //     "use <otp-keyword> <digits> at <merchant>". The fallback catches it, but
        //     Pattern 3 should also handle it directly.
        val maxFashionOtpText = "Use OTP 3797 at Max Fashion. Share it with the store team. The OTP is valid for 5 minutes. T&C"
        assertEquals(
            "Max Fashion in-store OTP should be extracted via Pattern 3b",
            "3797",
            parser.extractOtp(null, maxFashionOtpText)
        )

        // 17b. Max Fashion OTP — full parseLiveTracker path must surface the island
        val maxFashionOtpNotif = LauncherNotification(
            key = "max_fashion_otp",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Max Fashion",
            text = maxFashionOtpText,
            postTime = System.currentTimeMillis(),
            channelId = "ch_sms", isOngoing = false, isClearable = true, importance = 3
        )
        val unsafeClass17 = Class.forName("sun.misc.Unsafe")
        val theUnsafeField17 = unsafeClass17.getDeclaredField("theUnsafe")
        theUnsafeField17.isAccessible = true
        val unsafe17 = theUnsafeField17.get(null)
        val allocate17 = unsafeClass17.getMethod("allocateInstance", Class::class.java)
        val hvm17 = allocate17.invoke(unsafe17, com.alauncher.app.ui.home.HomeViewModel::class.java)
        val parserField17 = hvm17.javaClass.getDeclaredField("parser")
        parserField17.isAccessible = true
        parserField17.set(hvm17, com.alauncher.app.domain.notification.NotificationParser())
        val dummyRepo17 = com.alauncher.app.data.notification.NotificationRepository(
            filterEngine = com.alauncher.app.domain.notification.NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val repoField17 = hvm17.javaClass.getDeclaredField("notificationRepository")
        repoField17.isAccessible = true
        repoField17.set(hvm17, dummyRepo17)
        fun invokeHvm17(name: String, vararg args: Any?): Any? {
            val method = hvm17.javaClass.declaredMethods.first { it.name == name && it.parameterTypes.size == args.size }
            method.isAccessible = true
            return method.invoke(hvm17, *args)
        }
        val maxFashionIsland = invokeHvm17("parseLiveTracker", maxFashionOtpNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull("Max Fashion OTP island must not be null", maxFashionIsland)
        assertEquals("🔑 OTP Code: 3797", maxFashionIsland?.text)
        assertEquals(com.alauncher.app.ui.home.ContextualIslandType.OTP, maxFashionIsland?.type)

        // 17c. Uber PIN notification — parseLiveTracker must surface OTP island
        val uberPinNotif = LauncherNotification(
            key = "uber_pin_notif",
            packageName = "com.ubercab",
            appName = "Uber",
            title = "Use PIN 0362",
            text = "Give it to your driver at pick-up to start your ride.",
            postTime = System.currentTimeMillis(),
            channelId = "uber_trip", isOngoing = false, isClearable = true, importance = 3
        )
        val uberPinIsland = invokeHvm17("parseLiveTracker", uberPinNotif, null) as? com.alauncher.app.ui.home.ContextualIsland
        assertNotNull("Uber PIN island must not be null", uberPinIsland)
        assertEquals("🔑 OTP Code: 0362", uberPinIsland?.text)
        assertEquals(com.alauncher.app.ui.home.ContextualIslandType.OTP, uberPinIsland?.type)
        assertEquals(com.alauncher.app.ui.home.ContextualActionType.COPY_OTP, uberPinIsland?.actionType)

        // 15. IRCTC OTP — "DO NOT disclose" should not block OTP extraction
        val irctcOtpNotif = LauncherNotification(
            key = "irctc_otp",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "IRCTC",
            text = "987654 is OTP for Mobile number verification of User P5678. DO NOT disclose it to anyone - IRCTC",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("987654", parser.extractOtp(irctcOtpNotif.title, irctcOtpNotif.text))
    }

    @Test
    fun testNotificationHasContentAndDisplayProperties() {
        // 1. Contentless notification (Zebpay example) — title matches appName, text is null/blank -> hasContent == false
        val zebpayContentless = LauncherNotification(
            key = "zebpay_empty",
            packageName = "com.zebpay",
            appName = "Zebpay",
            title = "Zebpay",
            text = null,
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertFalse("Contentless notification matching appName with no body should return hasContent = false", zebpayContentless.hasContent)

        // 2. Completely blank notification — all fields null -> displayTitle == null, displayBody == null, hasContent == false
        val completelyBlank = LauncherNotification(
            key = "blank_1",
            packageName = "com.example",
            appName = "Example",
            title = null,
            text = null,
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertNull(completelyBlank.displayTitle)
        assertNull(completelyBlank.displayBody)
        assertFalse("Completely blank notification should return hasContent = false", completelyBlank.hasContent)

        // 3. Notification with title and body — Stratzy / Telegram example -> hasContent == true
        val validTelegram = LauncherNotification(
            key = "telegram_1",
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            title = "Stratzy Family Chat",
            text = "alongside Dhan",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("Stratzy Family Chat", validTelegram.displayTitle)
        assertEquals("alongside Dhan", validTelegram.displayBody)
        assertTrue("Notification with title and body should return hasContent = true", validTelegram.hasContent)

        // 4. Notification with only text (Photos / News digest) -> displayTitle promotes text, displayBody resolves next fallback
        val textOnly = LauncherNotification(
            key = "photos_text_only",
            packageName = "com.google.android.apps.photos",
            appName = "Photos",
            title = null,
            text = "New album shared with you",
            bigText = "Check out the photos from weekend trip",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("New album shared with you", textOnly.displayTitle)
        assertEquals("Check out the photos from weekend trip", textOnly.displayBody)
        assertTrue("Notification with promoted title and fallback body should return hasContent = true", textOnly.hasContent)

        // 5. BigTextStyle Notification (Title + BigText, text is null) -> BigText resolves as displayBody!
        val bigTextStyleNotif = LauncherNotification(
            key = "swiggy_bigtext_1",
            packageName = "in.swiggy.android",
            appName = "Swiggy",
            title = "Order on the way!",
            text = null,
            bigText = "Your delivery partner Rajesh is arriving in 7 minutes with your food.",
            postTime = System.currentTimeMillis(),
            channelId = "ch_orders", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("Order on the way!", bigTextStyleNotif.displayTitle)
        assertEquals("Your delivery partner Rajesh is arriving in 7 minutes with your food.", bigTextStyleNotif.displayBody)
        assertTrue(bigTextStyleNotif.hasContent)

        // 6. Notification where text is identical to title -> displayBody skips duplicate text and uses bigText
        val duplicateTextNotif = LauncherNotification(
            key = "alert_dup_1",
            packageName = "com.example.app",
            appName = "Example",
            title = "System Update",
            text = "System Update",
            bigText = "Security patch version 2.4 is ready to install.",
            postTime = System.currentTimeMillis(),
            channelId = "ch_sys", isOngoing = false, isClearable = true, importance = 3
        )
        assertEquals("System Update", duplicateTextNotif.displayTitle)
        assertEquals("Security patch version 2.4 is ready to install.", duplicateTextNotif.displayBody)
        assertTrue(duplicateTextNotif.hasContent)
    }

    @Test
    fun testUnfilteredAppsBypassFocusMode() {
        val filterEngine = NotificationFilterEngine()

        // Whitelisted unfilteredApps bypassing calendar muting in Focus Mode
        val settingsNormal = com.alauncher.app.data.settings.SettingsState()
        val settingsFocus = settingsNormal.copy(
            isFocusModeActive = true,
            unfilteredApps = setOf("com.google.android.calendar")
        )
        val calendarNotif = LauncherNotification(
            key = "cal_1",
            packageName = "com.google.android.calendar",
            appName = "Calendar",
            title = "Team Sync Meeting",
            text = "In 15 minutes",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val calCat = filterEngine.classify(calendarNotif, settingsFocus)
        assertEquals("Whitelisted app must return PRIORITY even during Focus Mode for calendar events", FilterCategory.PRIORITY, calCat)
    }

    @Test
    fun testSettingsExportImportSetSerialization() {
        val jsonStr = """
            {
                "sort_mode": "AlphabeticalAsc",
                "theme_mode": "NordicPine",
                "unfiltered_apps_set": ["com.whatsapp", "com.google.android.calendar"],
                "enabled_island_categories": ["OTP", "TRANSACTION"]
            }
        """.trimIndent()

        val json = org.json.JSONObject(jsonStr)
        val setVal = json.get("unfiltered_apps_set")
        assertTrue("Exported JSON array for Set must be instance of JSONArray", setVal is org.json.JSONArray)
        val arr = setVal as org.json.JSONArray
        assertEquals(2, arr.length())
        assertEquals("com.whatsapp", arr.getString(0))
        assertEquals("com.google.android.calendar", arr.getString(1))
    }

    @Test
    fun testTrailingCurrencyTransactionParsing() {
        val parser = com.alauncher.app.domain.notification.NotificationParser()

        val notif1 = LauncherNotification(
            key = "tx1",
            packageName = "com.hdfcbank.netbanking",
            appName = "HDFC Bank",
            title = "Account Alert",
            text = "Debited 500.00 INR at Starbucks",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val settings = com.alauncher.app.data.settings.SettingsState()
        val details1 = parser.parseTransactionDetails(notif1, settings)
        assertNotNull("Should parse transaction with trailing currency INR", details1)
        assertEquals(500.0, details1?.amount?.value)
        assertEquals("INR", details1?.amount?.currencySymbol)

        val notif2 = LauncherNotification(
            key = "tx2",
            packageName = "com.icicibank.mobile",
            appName = "iMobile",
            title = "Transaction Alert",
            text = "1500 Rs credited to your account A/C *1234",
            postTime = System.currentTimeMillis(),
            channelId = "ch1", isOngoing = false, isClearable = true, importance = 3
        )
        val details2 = parser.parseTransactionDetails(notif2, settings)
        assertNotNull("Should parse transaction with trailing currency Rs", details2)
        assertEquals(1500.0, details2?.amount?.value)
        assertEquals("Rs", details2?.amount?.currencySymbol)
    }

    @Test
    fun testHdfcUpiTransactionParsingFromSms() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val settings = com.alauncher.app.data.settings.SettingsState()
        
        val hdfcSmsNotif = LauncherNotification(
            key = "hdfc_sms_1",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "AZ-HDFCBK",
            text = "Sent Rs.115.00 From HDFC Bank A/C *8988 To ALAKUNTA VENU GOPAL On 24/07/26 Ref 620554882584 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808",
            postTime = System.currentTimeMillis(),
            channelId = "ch_sms", isOngoing = false, isClearable = true, importance = 3
        )

        val classification = classifier.classify(hdfcSmsNotif, settings)
        assertEquals("HDFC UPI SMS should be classified as TRANSACTION intent", NotificationIntent.TRANSACTION, classification.intent)

        val details = parser.parseTransactionDetails(hdfcSmsNotif, settings)
        assertNotNull("Should parse transaction details for HDFC SMS", details)
        assertEquals(115.0, details?.amount?.value)
        assertEquals("Rs.", details?.amount?.currencySymbol)
    }

    @Test
    fun testHousingOtpExtractionWithDomainMetadata() {
        val parser = NotificationParser()
        val housingOtpNotif = LauncherNotification(
            key = "housing_otp_1",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Housing",
            text = "996933 is your OTP for login. Please use this OTP to complete your phone number verification AZR0WfYTzaq @seller.housing.com -Housing",
            postTime = System.currentTimeMillis(),
            channelId = "ch_sms", isOngoing = false, isClearable = true, importance = 3
        )

        val extractedOtp = parser.extractOtp(housingOtpNotif)
        assertEquals("Should extract OTP '996933' from Housing SMS despite app hash and domain tags", "996933", extractedOtp)
    }

    @Test
    fun testNoBrokerSmsRetrieverOtpExtractionWithHeader() {
        val parser = NotificationParser()
        val noBrokerOtpNotif = LauncherNotification(
            key = "nobroker_otp_1",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "NoBroker",
            text = "<#> 134162 is the One Time Password (OTP) for Phone Verification on NoBroker - World's largest brokerage free platform. A7jPtLVJWz3",
            postTime = System.currentTimeMillis(),
            channelId = "ch_sms", isOngoing = false, isClearable = true, importance = 3
        )

        val extractedOtp = parser.extractOtp(noBrokerOtpNotif)
        assertEquals("Should extract OTP '134162' from NoBroker SMS with <#> header", "134162", extractedOtp)
    }

    @Test
    fun testConversationalSentMessageNotClassifiedAsTransaction() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val settings = com.alauncher.app.data.settings.SettingsState()

        val chatNotif = LauncherNotification(
            key = "chat_1",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "John",
            text = "I sent you a photo of the location",
            postTime = System.currentTimeMillis(),
            channelId = "ch_sms", isOngoing = false, isClearable = true, importance = 3
        )

        val classification = classifier.classify(chatNotif, settings)
        assertNotEquals("Conversational chat should NOT be classified as TRANSACTION", NotificationIntent.TRANSACTION, classification.intent)

        val details = parser.parseTransactionDetails(chatNotif, settings)
        assertNull("Conversational chat should return null transaction details", details)
    }

    @Test
    fun testSecurityAlertFromSmsWithoutAmount() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val settings = com.alauncher.app.data.settings.SettingsState()

        val secNotif = LauncherNotification(
            key = "sec_1",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "AZ-HDFCBK",
            text = "Security Alert: New device logged in to your HDFC Bank account *8988",
            postTime = System.currentTimeMillis(),
            channelId = "ch_sms", isOngoing = false, isClearable = true, importance = 3
        )

        val classification = classifier.classify(secNotif, settings)
        assertEquals("SMS Security Alert without amount should be allowed under SECURITY_ALERT intent", NotificationIntent.SECURITY_ALERT, classification.intent)
    }

    @Test
    fun testTruecallerSmsAppPackageAgnosticProcessing() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val settings = com.alauncher.app.data.settings.SettingsState()

        // 1. Transaction via Truecaller SMS
        val truecallerTxNotif = LauncherNotification(
            key = "tc_tx_1",
            packageName = "com.truecaller",
            appName = "Truecaller",
            title = "AZ-HDFCBK",
            text = "Sent Rs.250.00 From HDFC Bank A/C *8988 To ALAKUNTA VENU GOPAL On 24/07/26 Ref 620554882584",
            postTime = System.currentTimeMillis(),
            channelId = "ch_tc", isOngoing = false, isClearable = true, importance = 3
        )
        val txClassification = classifier.classify(truecallerTxNotif, settings)
        assertEquals("Truecaller SMS transaction should be classified as TRANSACTION intent", NotificationIntent.TRANSACTION, txClassification.intent)

        val txDetails = parser.parseTransactionDetails(truecallerTxNotif, settings)
        assertNotNull("Truecaller SMS transaction details should be parsed", txDetails)
        assertEquals(250.0, txDetails?.amount?.value)

        // 2. OTP via Truecaller SMS
        val truecallerOtpNotif = LauncherNotification(
            key = "tc_otp_1",
            packageName = "com.truecaller",
            appName = "Truecaller",
            title = "Housing",
            text = "996933 is your OTP for login. AZR0WfYTzaq @seller.housing.com -Housing",
            postTime = System.currentTimeMillis(),
            channelId = "ch_tc", isOngoing = false, isClearable = true, importance = 3
        )
        val extractedOtp = parser.extractOtp(truecallerOtpNotif)
        assertEquals("Truecaller SMS OTP should be extracted correctly", "996933", extractedOtp)
    }

    @Test
    fun testElevenLetterWordNotStrippedAsAppHash() {
        val parser = NotificationParser()
        val notif = LauncherNotification(
            key = "word_test_1",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Bank",
            text = "Your verification code is 884920 for your account transaction",
            postTime = System.currentTimeMillis(),
            channelId = "ch_sms", isOngoing = false, isClearable = true, importance = 3
        )
        val otp = parser.extractOtp(notif)
        assertEquals("Should extract OTP 884920 without stripping 11-letter word 'transaction'", "884920", otp)
    }

    @Test
    fun testNotificationDisplayPreviewsWhitespaceSanitization() {
        val longText = """
            Gold spiked. Crude swung both sides. Silver held strong. 16 commodity profits booked this month - inside the same plan that also covers Equity, F&O, and Baskets.
            
            One segment missing from your access = one set of profits you never saw.
            
            👉 Get Full App Access at just ₹8,999. Grab before it's gone. 🚀
        """.trimIndent()

        val notif = LauncherNotification(
            key = "telegram_long_1",
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            title = "Univest Official pinned\n\"🔥 16 Commodity Profits. This Month Alone.",
            text = longText,
            postTime = System.currentTimeMillis(),
            channelId = "ch_tg",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )

        // Verify title newline sanitization
        assertEquals(
            "Univest Official pinned \"🔥 16 Commodity Profits. This Month Alone.",
            notif.displayTitlePreview
        )

        // Verify body whitespace/newline collapsing (leaving visual line clamp to Compose Text maxLines + TextOverflow.Ellipsis)
        val preview = notif.displayBodyPreview
        assertNotNull(preview)
        assertFalse("Body preview should not contain raw consecutive newlines", preview!!.contains("\n"))
        assertTrue("Body preview should preserve clean text without mid-string cuts", preview.startsWith("Gold spiked. Crude swung both sides."))
        assertTrue("Body preview should include ending text ready for UI ellipsis", preview.endsWith("Grab before it's gone. 🚀"))
    }

    @Test
    fun testMeetingNotificationsFocusModeSetting() {
        val filterEngine = NotificationFilterEngine()
        val meetingNotif = LauncherNotification(
            key = "meet_1",
            packageName = "com.google.android.apps.meetings",
            appName = "Google Meet",
            title = "Design Sync Meeting",
            text = "Meeting is starting now: https://meet.google.com/abc-defg-hij",
            postTime = System.currentTimeMillis(),
            channelId = "ch_meet",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )

        val enrichedMeetingNotif = filterEngine.enrichWithMemoizedSignals(meetingNotif)
        assertEquals(true, enrichedMeetingNotif.memoizedIsMeetingCandidate)

        // 1. In normal mode -> PRIORITY (both cold and enriched)
        val normalState = com.alauncher.app.data.settings.SettingsState()
        assertEquals(FilterCategory.PRIORITY, filterEngine.classify(meetingNotif, normalState))
        assertEquals(FilterCategory.PRIORITY, filterEngine.classify(enrichedMeetingNotif, normalState))

        // 2. In Focus mode with allowMeetingsInFocusMode = true (default) -> PRIORITY (bypasses Focus Mode)
        val focusAllowState = com.alauncher.app.data.settings.SettingsState(
            isFocusModeActive = true,
            allowMeetingsInFocusMode = true
        )
        assertEquals(FilterCategory.PRIORITY, filterEngine.classify(meetingNotif, focusAllowState))
        assertEquals(FilterCategory.PRIORITY, filterEngine.classify(enrichedMeetingNotif, focusAllowState))

        // 3. In Focus mode with allowMeetingsInFocusMode = false -> MUTED
        val focusBlockState = com.alauncher.app.data.settings.SettingsState(
            isFocusModeActive = true,
            allowMeetingsInFocusMode = false
        )
        assertEquals(FilterCategory.MUTED, filterEngine.classify(meetingNotif, focusBlockState))
        assertEquals(FilterCategory.MUTED, filterEngine.classify(enrichedMeetingNotif, focusBlockState))

        // 4. In Quiet Hours with allowMeetingsInFocusMode = true -> PRIORITY (bypasses Quiet Hours)
        val quietAllowState = com.alauncher.app.data.settings.SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "00:00",
            quietHoursEnd = "23:59",
            allowMeetingsInFocusMode = true
        )
        assertEquals(FilterCategory.PRIORITY, filterEngine.classify(meetingNotif, quietAllowState))
        assertEquals(FilterCategory.PRIORITY, filterEngine.classify(enrichedMeetingNotif, quietAllowState))

        // 5. In Quiet Hours with allowMeetingsInFocusMode = false -> MUTED
        val quietBlockState = com.alauncher.app.data.settings.SettingsState(
            quietHoursEnabled = true,
            quietHoursStart = "00:00",
            quietHoursEnd = "23:59",
            allowMeetingsInFocusMode = false
        )
        assertEquals(FilterCategory.MUTED, filterEngine.classify(meetingNotif, quietBlockState))
        assertEquals(FilterCategory.MUTED, filterEngine.classify(enrichedMeetingNotif, quietBlockState))
    }

    @Test
    fun testMemoizedNotificationSignalsEnrichmentAndClassify() {
        val filterEngine = NotificationFilterEngine()
        val settings = com.alauncher.app.data.settings.SettingsState()

        val otpNotif = LauncherNotification(
            key = "otp_test_memo",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Bank",
            text = "Your login OTP is 584920. Valid for 10 mins.",
            postTime = System.currentTimeMillis(),
            channelId = "ch1",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )

        // Enrich with memoized static signals
        val enriched = filterEngine.enrichWithMemoizedSignals(otpNotif)
        assertEquals(false, enriched.memoizedIsPromo)
        assertEquals(true, enriched.memoizedIsOtp)
        assertEquals(10 * 60 * 1000L, enriched.memoizedOtpMaxAgeMs)
        assertEquals(false, enriched.memoizedIsMeetingCandidate)

        // Classify using memoized properties
        val cat = filterEngine.classify(enriched, settings)
        assertEquals(FilterCategory.PRIORITY, cat)
    }

    @Test
    fun testMemoizationReactivityWhenSettingsChange() {
        val filterEngine = NotificationFilterEngine()
        val initialSettings = com.alauncher.app.data.settings.SettingsState(
            isFocusModeActive = true,
            allowMeetingsInFocusMode = true,
            meetingAllowedApps = emptySet() // Not yet allowed
        )

        val customMeetingNotif = LauncherNotification(
            key = "internal_meeting_1",
            packageName = "com.company.internalchat",
            appName = "WorkChat",
            title = "Design Standup",
            text = "Your meeting is starting now. Click to join: https://company.internal/j/123456789",
            postTime = System.currentTimeMillis(),
            channelId = "ch_meetings",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )

        // Enrich once on notification post
        val memoizedMeetingNotif = filterEngine.enrichWithMemoizedSignals(customMeetingNotif)
        assertEquals(true, memoizedMeetingNotif.memoizedIsMeetingCandidate)

        // 1. App is NOT in meetingAllowedApps -> Muted in focus mode
        val initialCategory = filterEngine.classify(memoizedMeetingNotif, initialSettings)
        assertEquals(FilterCategory.MUTED, initialCategory)

        // 2. User enables app in settings -> classify immediately recognizes candidate as PRIORITY without re-enrichment!
        val updatedSettings = initialSettings.copy(
            meetingAllowedApps = setOf("com.company.internalchat")
        )
        val updatedCategory = filterEngine.classify(memoizedMeetingNotif, updatedSettings)
        assertEquals(FilterCategory.PRIORITY, updatedCategory)
    }

    @Test
    fun testDisplayBodyTitleDeduplication() {
        // When title equals text, bigText should be selected as displayBody
        val notifWithDuplicateText = LauncherNotification(
            key = "bank_dup_1",
            packageName = "com.hdfc.bank",
            appName = "HDFC Bank",
            title = "HDFC Bank",
            text = "HDFC Bank",
            bigText = "Rs. 5,000 debited from A/C XX1234 on 22-Aug-2026 via UPI.",
            postTime = System.currentTimeMillis(),
            channelId = "ch_tx",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )

        assertEquals("HDFC Bank", notifWithDuplicateText.displayTitle)
        assertEquals("Rs. 5,000 debited from A/C XX1234 on 22-Aug-2026 via UPI.", notifWithDuplicateText.displayBody)

        // When title and text are identical and bigText is absent, displayBody should be null (not duplicate title)
        val notifNoExtraBody = LauncherNotification(
            key = "bank_dup_2",
            packageName = "com.hdfc.bank",
            appName = "HDFC Bank",
            title = "  HDFC Bank  ", // title with leading/trailing whitespace
            text = "  hdfc bank  ",  // text with whitespace and lowercase
            postTime = System.currentTimeMillis(),
            channelId = "ch_tx",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )
        assertEquals("  HDFC Bank  ", notifNoExtraBody.displayTitle)
        assertEquals("HDFC Bank", notifNoExtraBody.displayTitlePreview)
        assertNull(notifNoExtraBody.displayBody)

        // Verify that displayBody preserves raw candidate whitespace whereas displayBodyPreview trims it
        val rawBodyNotif = LauncherNotification(
            key = "raw_body_1",
            packageName = "com.chat.app",
            appName = "ChatApp",
            title = "Alice",
            text = "   Hey, are you free?   ",
            postTime = System.currentTimeMillis(),
            channelId = "ch_chat",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )
        assertEquals("Alice", rawBodyNotif.displayTitle)
        assertEquals("   Hey, are you free?   ", rawBodyNotif.displayBody) // Raw preserved
        assertEquals("Hey, are you free?", rawBodyNotif.displayBodyPreview) // Trimmed in preview
    }

    @Test
    fun testAllowlistedMeetingAppDetectionAndColdPath() {
        val filterEngine = NotificationFilterEngine()

        // Notification from a 3rd-party calendar/meeting app with custom reminder structure
        val thirdPartyMeetingNotif = LauncherNotification(
            key = "custom_calendar_1",
            packageName = "com.custom.calendarapp",
            appName = "CustomCalendar",
            title = "Interview with Candidate",
            text = "Starts in 10 mins: Discussion on system architecture",
            postTime = System.currentTimeMillis(),
            channelId = "ch_reminders",
            isOngoing = false,
            isClearable = true,
            importance = 4
        )

        val settingsWithApp = com.alauncher.app.data.settings.SettingsState(
            isFocusModeActive = true,
            allowMeetingsInFocusMode = true,
            meetingAllowedApps = setOf("com.custom.calendarapp")
        )

        // 1. Cold path classification (un-enriched notification)
        val coldCategory = filterEngine.classify(thirdPartyMeetingNotif, settingsWithApp)
        assertEquals(FilterCategory.PRIORITY, coldCategory)

        // 2. Enriched memoized classification
        val enrichedNotif = filterEngine.enrichWithMemoizedSignals(thirdPartyMeetingNotif)
        val enrichedCategory = filterEngine.classify(enrichedNotif, settingsWithApp)
        assertEquals(FilterCategory.PRIORITY, enrichedCategory)
    }
}




