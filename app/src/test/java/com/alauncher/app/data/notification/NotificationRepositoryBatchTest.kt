package com.alauncher.app.data.notification

import com.alauncher.app.domain.notification.NotificationFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationRepositoryBatchTest {

    private lateinit var repository: NotificationRepository

    private suspend fun postTestNotification(key: String, title: String) {
        repository.postNotification(
            key = key,
            packageName = "com.test.app",
            appName = "Test App",
            title = title,
            text = "Notification body $key",
            postTime = System.currentTimeMillis(),
            channelId = "channel_default",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )
    }

    @Before
    fun setUp() {
        repository = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun testRemoveNotificationsBatchRemovesAllMatchingKeys() = runBlocking {
        postTestNotification("key_1", "Title 1")
        postTestNotification("key_2", "Title 2")
        postTestNotification("key_3", "Title 3")

        assertEquals(3, repository.resolvedNotificationsFlow.first().size)

        // Batch remove 2 keys
        repository.removeNotifications(listOf("key_1", "key_3"))

        val remaining = repository.resolvedNotificationsFlow.first()
        assertEquals(1, remaining.size)
        assertEquals("key_2", remaining[0].key)
    }

    @Test
    fun testRemoveNotificationsWithEmptyCollectionDoesNothing() = runBlocking {
        postTestNotification("key_1", "Title 1")

        repository.removeNotifications(emptyList())

        assertEquals(1, repository.resolvedNotificationsFlow.first().size)
    }

    @Test
    fun testRemoveNotificationsWithNonExistentKeysDoesNotAffectExisting() = runBlocking {
        postTestNotification("key_1", "Title 1")

        repository.removeNotifications(listOf("key_999", "key_foo"))

        assertEquals(1, repository.resolvedNotificationsFlow.first().size)
    }

    @Test
    fun testRemoveNotificationsRemovesAllKeys() = runBlocking {
        val keys = (1..50).map { "key_$it" }
        keys.forEach { key ->
            postTestNotification(key, "Title $key")
        }

        assertEquals(50, repository.resolvedNotificationsFlow.first().size)

        repository.removeNotifications(keys)

        assertTrue(repository.resolvedNotificationsFlow.first().isEmpty())
    }

    @Test
    fun testRemoveNotificationsForPackage() = runBlocking {
        repository.postNotification(
            key = "key_app1_a",
            packageName = "com.uninstalled.app",
            appName = "Uninstalled App",
            title = "Alert 1",
            text = "Body 1",
            postTime = System.currentTimeMillis(),
            channelId = "c1",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )
        repository.postNotification(
            key = "key_app1_b",
            packageName = "com.uninstalled.app",
            appName = "Uninstalled App",
            title = "Alert 2",
            text = "Body 2",
            postTime = System.currentTimeMillis(),
            channelId = "c1",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )
        repository.postNotification(
            key = "key_other",
            packageName = "com.other.app",
            appName = "Other App",
            title = "Other Alert",
            text = "Body Other",
            postTime = System.currentTimeMillis(),
            channelId = "c2",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        assertEquals(3, repository.resolvedNotificationsFlow.first().size)

        repository.removeNotificationsForPackage("com.uninstalled.app")

        val remaining = repository.resolvedNotificationsFlow.first()
        assertEquals(1, remaining.size)
        assertEquals("key_other", remaining[0].key)
    }

    @Test
    fun testPostDuplicateContentUnderDifferentKeysDeduplicates() = runBlocking {
        repository.postNotification(
            key = "0|in.amazon.mShop.android.shopping|101|null|10234",
            packageName = "in.amazon.mShop.android.shopping",
            appName = "Amazon",
            title = "Get set for Colombo!",
            text = "Save 23%* on flights with Amazon Pay. Book now",
            postTime = 1000L,
            channelId = "promos",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )
        repository.postNotification(
            key = "0|in.amazon.mShop.android.shopping|102|null|10234",
            packageName = "in.amazon.mShop.android.shopping",
            appName = "Amazon",
            title = "Get set for Colombo!",
            text = "Save 23%* on flights with Amazon Pay. Book now",
            postTime = 1050L,
            channelId = "promos",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        val notifications = repository.resolvedNotificationsFlow.first()
        assertEquals(1, notifications.size)
        assertEquals("0|in.amazon.mShop.android.shopping|102|null|10234", notifications[0].key)
    }

    @Test
    fun testPostDistinctContentFromSamePackageKeepsBoth() = runBlocking {
        repository.postNotification(
            key = "0|in.amazon.mShop.android.shopping|101|null|10234",
            packageName = "in.amazon.mShop.android.shopping",
            appName = "Amazon",
            title = "Get set for Colombo!",
            text = "Save 23%* on flights with Amazon Pay. Book now",
            postTime = 1000L,
            channelId = "promos",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )
        repository.postNotification(
            key = "0|in.amazon.mShop.android.shopping|102|null|10234",
            packageName = "in.amazon.mShop.android.shopping",
            appName = "Amazon",
            title = "Your package has shipped",
            text = "Arriving tomorrow by 9 PM",
            postTime = 2000L,
            channelId = "orders",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        val notifications = repository.resolvedNotificationsFlow.first()
        assertEquals(2, notifications.size)
    }

    @Test
    fun testReconcileStatusBarNotificationsPreservesInterceptedNormalAndPrunesStaleStatusBarItems() = runBlocking {
        // 1. Intercepted NORMAL notification (already cancelled from SystemUI by ALauncher)
        repository.postNotification(
            key = "key_normal_intercepted",
            packageName = "in.amazon.mShop.android.shopping",
            appName = "Amazon",
            title = "Get set for Colombo!",
            text = "Save 23%* on flights",
            postTime = 1000L,
            channelId = "promos",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )
        // 2. Status-bar-resident ongoing notification that finished while listener was disconnected
        repository.postNotification(
            key = "key_ongoing_stale",
            packageName = "com.spotify.music",
            appName = "Spotify",
            title = "Song 1",
            text = "Artist 1",
            postTime = 2000L,
            channelId = "playback",
            isOngoing = true,
            isClearable = false,
            importance = 3,
            pendingIntent = null
        )
        // 3. Status-bar-resident ongoing notification still active in SystemUI
        repository.postNotification(
            key = "key_ongoing_active",
            packageName = "com.google.android.apps.maps",
            appName = "Maps",
            title = "Turn right",
            text = "200m",
            postTime = 3000L,
            channelId = "nav",
            isOngoing = true,
            isClearable = false,
            importance = 4,
            pendingIntent = null
        )

        val preResyncKeys = repository.snapshotActiveKeys()

        // 4. Status-bar-resident notification arriving concurrently during the resync window
        repository.postNotification(
            key = "key_arrived_during_resync",
            packageName = "com.google.android.dialer",
            appName = "Phone",
            title = "Ongoing Call",
            text = "00:12",
            postTime = 4000L,
            channelId = "call",
            isOngoing = true,
            isClearable = false,
            importance = 4,
            pendingIntent = null
        )

        repository.reconcileStatusBarNotifications(
            activeSystemKeys = setOf("key_ongoing_active"),
            isDefaultLauncher = true,
            candidateKeys = preResyncKeys
        )

        val remainingKeys = repository.resolvedNotificationsFlow.first().map { it.key }.toSet()
        assertEquals(
            setOf("key_normal_intercepted", "key_ongoing_active", "key_arrived_during_resync"),
            remainingKeys
        )
    }
}

