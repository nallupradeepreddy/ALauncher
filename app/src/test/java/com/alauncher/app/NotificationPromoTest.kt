package com.alauncher.app

import com.alauncher.app.domain.notification.NotificationConstants
import com.alauncher.app.domain.notification.NotificationParser
import com.alauncher.app.domain.notification.NotificationClassifier
import com.alauncher.app.domain.notification.NotificationFilterEngine
import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.domain.notification.NotificationIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPromoTest {

    @Test
    fun testPromoBypass() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val filterEngine = NotificationFilterEngine(parser, classifier)
        val settings = com.alauncher.app.data.settings.SettingsState()

        val promoNotif = LauncherNotification(
            key = "promo_1",
            packageName = "com.pepperfry.app",
            appName = "Pepperfry",
            title = "⚡ Flash Hour Is LIVE!",
            text = "⏰ Crack Code: ED**26 | Grab Office Chair at Just Rs.80 | Ends at 5 PM 1:50:45",
            postTime = System.currentTimeMillis(),
            channelId = "promo_ch", isOngoing = false, isClearable = true, importance = 3
        )

        val text = promoNotif.text ?: ""
        val title = promoNotif.title ?: ""

        assertTrue("Title should match promotional regex", NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(title))
        assertTrue("Text should match consolidated promotional regex", NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text))

        val extractedOtp = parser.extractOtp(promoNotif)
        assertNull("Extracted OTP should be null for promotional notifications", extractedOtp)

        val classification = classifier.classify(promoNotif, settings)
        assertEquals("Classification Intent should be NONE for promo notifications", NotificationIntent.NONE, classification.intent)

        val cat = filterEngine.classify(promoNotif, settings)
        assertEquals("Promotional notification should be NORMAL (cancellable from status bar)", FilterCategory.NORMAL, cat)
        assertNotEquals("Promotional notification should NOT be PRIORITY", FilterCategory.PRIORITY, cat)
    }

    @Test
    fun testPromoBypassInFocusMode() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val filterEngine = NotificationFilterEngine(parser, classifier)
        val settings = com.alauncher.app.data.settings.SettingsState(isFocusModeActive = true)

        val promoNotif = LauncherNotification(
            key = "promo_2",
            packageName = "com.pepperfry.app",
            appName = "Pepperfry",
            title = "⚡ Flash Hour Is LIVE!",
            text = "⏰ Crack Code: ED**26 | Grab Office Chair at Just Rs.80 | Ends at 5 PM 1:50:45",
            postTime = System.currentTimeMillis(),
            channelId = "promo_ch", isOngoing = false, isClearable = true, importance = 3
        )

        val cat = filterEngine.classify(promoNotif, settings)
        assertEquals("Promotional notification in Focus Mode should be MUTED", FilterCategory.MUTED, cat)
        assertNotEquals("Promotional notification should NOT be PRIORITY", FilterCategory.PRIORITY, cat)
    }

    @Test
    fun testPurplleMegaPriceDropPromoWithTimerIsClassifiedAsNormalAndNotOtp() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val filterEngine = NotificationFilterEngine(parser, classifier)
        val settings = com.alauncher.app.data.settings.SettingsState()

        val purplleNotif = LauncherNotification(
            key = "purplle_promo_1",
            packageName = "com.manash.purplle",
            appName = "Purplle",
            title = "Scraping the corners of your ...",
            text = "We see you trying to make that last bit work. Restock your daily foundation, lipstick & essentials on mega price drop! 💄 2:16:47",
            postTime = System.currentTimeMillis(),
            channelId = "deals",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )

        val text = purplleNotif.text ?: ""
        val isPromo = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
                      NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)
        assertTrue("Purplle notification text with 'mega price drop' must match promotional regex", isPromo)

        val extractedOtp = parser.extractOtp(purplleNotif)
        assertNull("Timer '2:16:47' in Purplle promo must NOT be extracted as OTP", extractedOtp)

        val cat = filterEngine.classify(purplleNotif, settings)
        assertEquals("Purplle promotional notification must be classified as NORMAL (cancellable from status bar)", FilterCategory.NORMAL, cat)
        assertNotEquals("Purplle promo must NOT be promoted to PRIORITY", FilterCategory.PRIORITY, cat)
    }

    @Test
    fun testRestockAndBackInStockPromoPatterns() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val filterEngine = NotificationFilterEngine(parser, classifier)
        val settings = com.alauncher.app.data.settings.SettingsState()

        val restockTexts = listOf(
            "Restock now and enjoy flat 25% discount on all items",
            "Restock daily essentials with lightning fast delivery",
            "Restock your pantry today"
        )
        for (text in restockTexts) {
            val matches = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
                          NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)
            assertTrue("'$text' must match restock promotional regex", matches)
        }

        val backInStockTexts = listOf(
            "Your favorite sneakers are back in stock!",
            "Back in stock: grab yours before it sells out"
        )
        for (text in backInStockTexts) {
            val matches = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
                          NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)
            assertTrue("'$text' must match back-in-stock promotional regex", matches)
        }

        val notif = LauncherNotification(
            key = "stock_1",
            packageName = "com.ecommerce.store",
            appName = "Store",
            title = "Item Update",
            text = "Your wishlisted jacket is back in stock! Restock now with promo code SAVE10",
            postTime = System.currentTimeMillis(),
            channelId = "updates",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )
        val cat = filterEngine.classify(notif, settings)
        assertEquals("Back in stock notification must be NORMAL", FilterCategory.NORMAL, cat)
    }

    @Test
    fun testPriceDropTransactionalVsMarketingPatterns() {
        val marketingTexts = listOf(
            "Mega price drop on top smartphones! Grab yours now",
            "Huge price drop across all electronics",
            "Massive price dropped on winter jackets",
            "Don't miss the mega price dropped sale"
        )
        for (text in marketingTexts) {
            val matches = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
                          NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)
            assertTrue("'$text' should be classified as promotional", matches)
        }

        val transactionalTexts = listOf(
            "Your order price dropped by ₹50 — refund initiated",
            "Item in your active return: price dropped by $10, credit applied to wallet",
            "Booking update: fare difference of ₹200 refunded as price dropped"
        )
        for (text in transactionalTexts) {
            val matches = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
                          NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)
            assertFalse("Transactional notification '$text' must NOT match promotional regex", matches)
        }
    }

    @Test
    fun testPurplleShagunGlowUpPromoIsClassifiedAsNormalAndMatchesPromoRegex() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val filterEngine = NotificationFilterEngine(parser, classifier)
        val settings = com.alauncher.app.data.settings.SettingsState(
            notificationFilteringEnabled = true
        )

        val title = "Shagun ka ₹1 just got a glow-up! ✨"
        val text = "Get top sellers from Good Vibes, Derm-Doc & NY Bae at just ₹1/-! Grab your faves now. \uD83E\uDDF4"

        // Both title and text must independently match promotional patterns
        val isPromoTitle = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(title) ||
                           NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(title)
        assertTrue("Purplle title with 'shagun' and 'glow-up' must match promo regex", isPromoTitle)

        val isPromoText = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
                          NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)
        assertTrue("Purplle text with 'at just ₹1' and 'grab your faves' must match promo regex", isPromoText)

        val notif = LauncherNotification(
            key = "purplle_shagun_1",
            packageName = "com.manash.purplle",
            appName = "Purplle",
            title = title,
            text = text,
            postTime = System.currentTimeMillis(),
            channelId = "deals",
            isOngoing = false,
            isClearable = true,
            importance = 3
        )

        val enriched = filterEngine.enrichWithMemoizedSignals(notif)
        assertTrue("Memoized promo flag must be true", enriched.memoizedIsPromo == true)

        val classified = classifier.classify(notif, settings)
        assertEquals("Purplle promo must classify as NONE intent", NotificationIntent.NONE, classified.intent)

        val cat = filterEngine.classify(notif, settings)
        assertEquals("Purplle promo must be NORMAL category", FilterCategory.NORMAL, cat)
        assertNotEquals("Purplle promo must NEVER be PRIORITY", FilterCategory.PRIORITY, cat)

        val isFastPath = com.alauncher.app.service.NotificationListener.isFastPathCancelCandidate(
            packageName = notif.packageName,
            title = notif.title,
            text = notif.text,
            category = null,
            isClearable = notif.isClearable,
            isOngoing = notif.isOngoing,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true,
            notificationFilteringEnabled = true
        )
        assertTrue("Fast-path cancellation candidate must be true", isFastPath)

        val shouldCancel = com.alauncher.app.service.NotificationListener.shouldCancelNotification(
            cat = cat,
            isClearable = notif.isClearable,
            isOngoing = notif.isOngoing,
            isDefaultLauncher = true,
            notificationFilteringEnabled = true
        )
        assertTrue("shouldCancelNotification must be true", shouldCancel)
    }

    @Test
    fun testPurplleBeautySaleLiveBuy1Get1DealsPromoIsClassifiedAsNormal() {
        val parser = NotificationParser()
        val classifier = NotificationClassifier(parser)
        val filterEngine = NotificationFilterEngine(parser, classifier)
        val settings = com.alauncher.app.data.settings.SettingsState(
            notificationFilteringEnabled = true
        )

        val title = "The I Hear Beauty Sale is live!"
        val text = "Grab ₹1 deals and Buy 1 Get 1 Free on your ..."

        val isPromoTitle = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(title) ||
                           NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(title)
        assertTrue("Title with 'Beauty Sale is live' must match promo regex", isPromoTitle)

        val isPromoText = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
                          NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)
        assertTrue("Text with '₹1 deals' and 'Buy 1 Get 1 Free' must match promo regex", isPromoText)

        val notif = LauncherNotification(
            key = "purplle_sale_2",
            packageName = "com.manash.purplle",
            appName = "Purplle",
            title = title,
            text = text,
            postTime = System.currentTimeMillis(),
            channelId = "deals",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            notificationCategory = android.app.Notification.CATEGORY_EVENT
        )

        val enriched = filterEngine.enrichWithMemoizedSignals(notif)
        assertTrue("Memoized promo flag must be true", enriched.memoizedIsPromo == true)

        val classified = classifier.classify(notif, settings)
        assertEquals("Even with CATEGORY_EVENT, promo app must classify as NONE (not MEETING)", NotificationIntent.NONE, classified.intent)

        val cat = filterEngine.classify(notif, settings)
        assertEquals("Purplle sale promo must be NORMAL category", FilterCategory.NORMAL, cat)
        assertNotEquals("Purplle sale promo must NEVER be PRIORITY", FilterCategory.PRIORITY, cat)

        // CATEGORY_EVENT on non-calendar app must NOT bypass fast-path cancellation
        val isFastPath = com.alauncher.app.service.NotificationListener.isFastPathCancelCandidate(
            packageName = notif.packageName,
            title = notif.title,
            text = notif.text,
            category = android.app.Notification.CATEGORY_EVENT,
            isClearable = notif.isClearable,
            isOngoing = notif.isOngoing,
            unfilteredApps = emptySet(),
            isDefaultLauncher = true,
            notificationFilteringEnabled = true
        )
        assertTrue("Non-calendar app with CATEGORY_EVENT must still be fast-path cancelled", isFastPath)

        val shouldCancel = com.alauncher.app.service.NotificationListener.shouldCancelNotification(
            cat = cat,
            isClearable = notif.isClearable,
            isOngoing = notif.isOngoing,
            isDefaultLauncher = true,
            notificationFilteringEnabled = true
        )
        assertTrue("shouldCancelNotification must be true", shouldCancel)
    }
}
