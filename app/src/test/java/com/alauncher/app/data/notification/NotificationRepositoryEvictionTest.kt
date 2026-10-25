package com.alauncher.app.data.notification

import com.alauncher.app.domain.notification.NotificationFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationRepositoryEvictionTest {

    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        repository = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    private suspend fun postNotificationWithTime(
        key: String,
        postTime: Long,
        isOngoing: Boolean = false,
        title: String = "Title $key"
    ) {
        repository.postNotification(
            key = key,
            packageName = "com.test.app",
            appName = "Test App",
            title = title,
            text = "Body $key",
            postTime = postTime,
            channelId = "default",
            isOngoing = isOngoing,
            isClearable = !isOngoing,
            importance = 3,
            pendingIntent = null
        )
    }

    @Test
    fun testCapacityEvictionPrunesOldestWhenExceeding100Notifications() = runBlocking {
        val baseTime = 1_700_000_000_000L // Valid realistic epoch millis

        // Post 105 non-ongoing notifications
        for (i in 1..105) {
            postNotificationWithTime(
                key = "key_$i",
                postTime = baseTime + (i * 1000L) // Each 1s newer
            )
        }

        val active = repository.resolvedNotificationsFlow.first()
        // Repository should be capped at MAX_ACTIVE_NOTIFICATIONS (100)
        assertEquals(100, active.size)

        // The oldest 5 (key_1 .. key_5) must have been evicted
        val activeKeys = active.map { it.key }.toSet()
        for (i in 1..5) {
            assertTrue("Expected key_$i to be evicted", "key_$i" !in activeKeys)
        }
        // Newer keys (key_6 .. key_105) must still be present
        for (i in 6..105) {
            assertTrue("Expected key_$i to be retained", "key_$i" in activeKeys)
        }
    }

    @Test
    fun testCapacityEvictionNeverPrunesOngoingNotifications() = runBlocking {
        val baseTime = 1_700_000_000_000L

        // Post an ongoing notification early (oldest)
        postNotificationWithTime(
            key = "ongoing_important",
            postTime = baseTime,
            isOngoing = true
        )

        // Post 104 more regular notifications to trigger capacity trimming
        for (i in 1..104) {
            postNotificationWithTime(
                key = "regular_$i",
                postTime = baseTime + (i * 1000L),
                isOngoing = false
            )
        }

        val active = repository.resolvedNotificationsFlow.first()
        val activeKeys = active.map { it.key }.toSet()

        // The ongoing notification MUST be preserved even though it is the oldest
        assertTrue("Ongoing notification must not be evicted", "ongoing_important" in activeKeys)
        assertEquals(100, active.size)
    }

    @Test
    fun testStaleNotificationEvictionRemovesEntriesOlderThan48Hours() = runBlocking {
        val now = System.currentTimeMillis()
        val fortyNineHoursAgo = now - (49 * 60 * 60 * 1000L)

        // Reset the trim throttle by posting a notification with realistic timestamp
        postNotificationWithTime("fresh_initial", now - 1000L)

        // Now post a stale notification older than 48 hours
        postNotificationWithTime("stale_item", fortyNineHoursAgo)

        // Force TTL check to run by artificially setting lastTrimTimeMs in the past via reflection
        val lastTrimField = NotificationRepository::class.java.getDeclaredField("lastTrimTimeMs")
        lastTrimField.isAccessible = true
        lastTrimField.set(repository, now - (10 * 60 * 1000L)) // 10 minutes ago to exceed 5m throttle

        // Posting a new notification triggers the throttled TTL eviction
        postNotificationWithTime("fresh_trigger", now)

        val active = repository.resolvedNotificationsFlow.first()
        val activeKeys = active.map { it.key }.toSet()

        assertTrue("Stale item older than 48 hours must be evicted", "stale_item" !in activeKeys)
        assertTrue("Fresh trigger item must be present", "fresh_trigger" in activeKeys)
        assertTrue("Fresh initial item must be present", "fresh_initial" in activeKeys)
    }

    @Test
    fun allOngoingNotifications_neverEvictedBelowCapacity() = runBlocking {
        val baseTime = 1_700_000_000_000L

        // Post 105 ongoing notifications — all above the 100 capacity limit
        for (i in 1..105) {
            postNotificationWithTime(
                key = "ongoing_$i",
                postTime = baseTime + (i * 1000L),
                isOngoing = true
            )
        }

        val active = repository.resolvedNotificationsFlow.first()
        assertEquals(
            "All 105 ongoing notifications must survive — eviction only targets non-ongoing items",
            105,
            active.size
        )

        val activeKeys = active.map { it.key }.toSet()
        for (i in 1..105) {
            assertTrue("ongoing_$i must be present", "ongoing_$i" in activeKeys)
        }
    }
}

