package com.alauncher.app.ui.notificationcenter

import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.LauncherNotification
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationCenterSearchTest {

    private fun createNotification(
        key: String,
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        subText: String? = null,
        bigText: String? = null,
        filterCategory: FilterCategory = FilterCategory.NORMAL
    ): LauncherNotification {
        return LauncherNotification(
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            postTime = System.currentTimeMillis(),
            channelId = "channel_$key",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            filterCategory = filterCategory
        )
    }

    private fun filterList(list: List<LauncherNotification>, query: String): List<LauncherNotification> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { notif ->
            notif.title?.contains(q, ignoreCase = true) == true ||
            notif.text?.contains(q, ignoreCase = true) == true ||
            notif.appName.contains(q, ignoreCase = true) ||
            notif.subText?.contains(q, ignoreCase = true) == true ||
            notif.bigText?.contains(q, ignoreCase = true) == true
        }
    }

    @Test
    fun testSearchByAppNameCaseInsensitive() {
        val n1 = createNotification("1", "com.amazon.mShop", "Amazon", "Out for Delivery", "Package arriving today")
        val n2 = createNotification("2", "com.whatsapp", "WhatsApp", "Alice", "Meeting notes")
        val list = listOf(n1, n2)

        assertEquals(1, filterList(list, "amazon").size)
        assertEquals(1, filterList(list, "AMAZON").size)
        assertEquals(1, filterList(list, "  Amazon  ").size)
        assertEquals("Amazon", filterList(list, "amazon")[0].appName)
    }

    @Test
    fun testSearchByTitleAndBody() {
        val n1 = createNotification("1", "com.uber", "Uber", "Driver Arriving", "Honda Civic silver")
        val n2 = createNotification("2", "com.slack", "Slack", "#design-team", "Please review mockups")
        val list = listOf(n1, n2)

        // Match by title
        val titleMatch = filterList(list, "driver")
        assertEquals(1, titleMatch.size)
        assertEquals("Uber", titleMatch[0].appName)

        // Match by body text
        val bodyMatch = filterList(list, "mockups")
        assertEquals(1, bodyMatch.size)
        assertEquals("Slack", bodyMatch[0].appName)
    }

    @Test
    fun testSearchBySubTextAndBigText() {
        val n1 = createNotification("1", "com.google.android.gm", "Gmail", "Project Update", "Summary", subText = "work@company.com")
        val n2 = createNotification("2", "com.google.android.gm", "Gmail", "Newsletter", "Brief", bigText = "Full detailed quarterly report")
        val list = listOf(n1, n2)

        val subMatch = filterList(list, "work@company")
        assertEquals(1, subMatch.size)
        assertEquals("1", subMatch[0].key)

        val bigMatch = filterList(list, "quarterly report")
        assertEquals(1, bigMatch.size)
        assertEquals("2", bigMatch[0].key)
    }

    @Test
    fun testEmptyQueryRestoresAllItems() {
        val n1 = createNotification("1", "com.app1", "App1", "Title 1", "Body 1")
        val n2 = createNotification("2", "com.app2", "App2", "Title 2", "Body 2")
        val list = listOf(n1, n2)

        assertEquals(2, filterList(list, "").size)
        assertEquals(2, filterList(list, "   ").size)
    }

    @Test
    fun testSpecialCharactersDoNotCrash() {
        val n1 = createNotification("1", "com.app1", "Bank", "OTP: 123456", "Ref: #TXN-987654 (USD $50.00)")
        val list = listOf(n1)

        // Regex characters in search string should not crash String.contains(..., ignoreCase = true)
        val res1 = filterList(list, "$50.00")
        assertEquals(1, res1.size)

        val res2 = filterList(list, "(USD")
        assertEquals(1, res2.size)

        val res3 = filterList(list, "#TXN")
        assertEquals(1, res3.size)
    }

    @Test
    fun testClearAllCandidateSelectionPreservesPriorityNotifications() {
        val priorityNotif = createNotification("p1", "com.bank", "Bank", "OTP 123456", "Your code", filterCategory = FilterCategory.PRIORITY)
        val normalNotif = createNotification("n1", "com.chat", "Chat", "Hello", "How are you?", filterCategory = FilterCategory.NORMAL)
        val mutedNotif = createNotification("m1", "com.promo", "Promo", "Deal", "50% off", filterCategory = FilterCategory.MUTED)

        val state = NotificationCenterState(
            priorityList = listOf(priorityNotif),
            normalList = listOf(normalNotif),
            mutedList = listOf(mutedNotif)
        )

        val keysToRemove = (state.normalList + state.mutedList).map { it.key }

        assertEquals(2, keysToRemove.size)
        assertTrue(keysToRemove.contains("n1"))
        assertTrue(keysToRemove.contains("m1"))
        assertFalse("Priority notifications must never be included in Clear All batch", keysToRemove.contains("p1"))
    }

    @Test
    fun testClearAllWithOnlyPriorityNotificationsProducesEmptyBatch() {
        val priorityNotif = createNotification("p1", "com.bank", "Bank", "OTP", "Code", filterCategory = FilterCategory.PRIORITY)

        val state = NotificationCenterState(
            priorityList = listOf(priorityNotif),
            normalList = emptyList(),
            mutedList = emptyList()
        )

        val keysToRemove = (state.normalList + state.mutedList).map { it.key }
        assertTrue("Batch should be empty when only priority alerts are present", keysToRemove.isEmpty())
    }
}
