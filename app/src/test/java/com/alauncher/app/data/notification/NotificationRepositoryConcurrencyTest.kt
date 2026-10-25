package com.alauncher.app.data.notification

import com.alauncher.app.domain.notification.NotificationFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationRepositoryConcurrencyTest {

    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        repository = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun concurrentPostAndRemove_neverThrowsConcurrentModificationException() = runBlocking {
        val jobs = (1..50).map { i ->
            launch(Dispatchers.Default) {
                val key = "concurrent_key_$i"
                repository.postNotification(
                    key = key,
                    packageName = "com.test.concurrent$i",
                    appName = "App $i",
                    title = "Title $i",
                    text = "Text $i",
                    postTime = System.currentTimeMillis(),
                    channelId = "ch_test",
                    isOngoing = false,
                    isClearable = true,
                    importance = 3,
                    pendingIntent = null
                )

                // Simultaneously read
                repository.snapshotActiveKeys().contains(key)

                // Alternate removing half
                if (i % 2 == 0) {
                    repository.removeNotification(key)
                }
            }
        }
        jobs.joinAll()

        val active = repository.resolvedNotificationsFlow.first()
        // Exactly the 25 odd notifications should remain
        assertEquals(25, active.size)
    }

    @Test
    fun deduplicatesIdenticalNotificationsFromSamePackage() = runBlocking {
        val pkg = "com.chat.app"
        val title = "Alice"
        val text = "Are you available?"

        // Post original
        repository.postNotification(
            key = "key_stage_1",
            packageName = pkg,
            appName = "Chat",
            title = title,
            text = text,
            postTime = System.currentTimeMillis(),
            channelId = "ch_chat",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        assertEquals(1, repository.resolvedNotificationsFlow.first().size)

        // Post identical notification under a new key (e.g. status bar group summary replacement)
        repository.postNotification(
            key = "key_stage_2",
            packageName = pkg,
            appName = "Chat",
            title = title,
            text = text,
            postTime = System.currentTimeMillis() + 100,
            channelId = "ch_chat",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        // Old key must be replaced by new key — only 1 notification remains in active list
        val active = repository.resolvedNotificationsFlow.first()
        assertEquals("Duplicate identical notification must be deduplicated", 1, active.size)
        assertEquals("New key must be preserved", "key_stage_2", active.first().key)
    }

    @Test
    fun launchNotification_underLock_safelyHandlesMissingAndValidKeys() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. Non-existent key must return false under synchronized lock without throwing
        val missingResult = repository.launchNotification("non_existent_key", context)
        assertFalse("Missing notification key must return false", missingResult)

        // 2. Post a notification without PendingIntent
        val key = "notif_launch_test"
        repository.postNotification(
            key = key,
            packageName = "com.nonexistent.app",
            appName = "Fake App",
            title = "Test",
            text = "Testing launch",
            postTime = System.currentTimeMillis(),
            channelId = "ch_test",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        // Launch with fallback: since package does not exist in PackageManager, launch returns false gracefully
        val launchResult = repository.launchNotification(key, context)
        assertFalse("App without valid launch intent returns false", launchResult)
        // Notification is still retained because launch failed
        assertTrue(repository.snapshotActiveKeys().contains(key))
    }
}

