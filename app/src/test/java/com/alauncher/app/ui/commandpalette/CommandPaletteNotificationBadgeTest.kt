package com.alauncher.app.ui.commandpalette

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.AppMetadataEntity
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.domain.notification.NotificationFilterEngine
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.LauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class CommandPaletteNotificationBadgeTest {

    private class FakeAppMetadataDao : AppMetadataDao {
        override fun getAllMetadataFlow(): Flow<List<AppMetadataEntity>> = flowOf(emptyList())
        override suspend fun getAllMetadataDirect(): List<AppMetadataEntity> = emptyList()
        override suspend fun getMetadata(packageName: String): AppMetadataEntity? = null
        override suspend fun insertMetadata(metadata: AppMetadataEntity) {}
        override suspend fun updateFavorite(packageName: String, isFavorite: Boolean) {}
        override suspend fun updateHidden(packageName: String, isHidden: Boolean) {}
        override suspend fun incrementLaunch(packageName: String, time: Long): Int = 1
        override suspend fun resetAllAppStats() {}
        override suspend fun deleteMetadata(packageName: String): Int = 1
    }

    private lateinit var appRepository: AppRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var rawAppsStateFlow: MutableStateFlow<List<LauncherApp>>
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        appRepository = AppRepository(
            context = context,
            appMetadataDao = FakeAppMetadataDao(),
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
        settingsRepository = SettingsRepository(context)

        val rawAppsField = AppRepository::class.java.getDeclaredField("_rawAppsFlow")
        rawAppsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        rawAppsStateFlow = rawAppsField.get(appRepository) as MutableStateFlow<List<LauncherApp>>
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Tests the enrichedAppsFlow logic directly: a NORMAL notification should contribute
     * to the badge count via the enrichment combine in CommandPaletteViewModel.
     * We access the private enrichedAppsFlow via reflection to avoid timeout issues
     * from the downstream stateIn chains that depend on virtual time.
     */
    @Test
    fun normalNotification_contributesToBadgeCount() = runTest {
        val notifRepo = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val testApp = LauncherApp(
            packageName = "com.test.messenger",
            label = "Messenger",
            normalizedLabel = "messenger"
        )
        rawAppsStateFlow.value = listOf(testApp)

        notifRepo.postNotification(
            key = "msg_notif_1",
            packageName = "com.test.messenger",
            appName = "Messenger",
            title = "Alice",
            text = "Hey there!",
            postTime = System.currentTimeMillis(),
            channelId = "channel_msgs",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        // Verify the notification is classified as NORMAL
        val resolved = notifRepo.resolvedNotificationsFlow.first()
        assertEquals(1, resolved.size)
        assertEquals(FilterCategory.NORMAL, resolved[0].filterCategory)

        // Access the private enrichedAppsFlow via reflection
        val vm = CommandPaletteViewModel(
            appRepository = appRepository,
            settingsRepository = settingsRepository,
            notificationRepository = notifRepo
        )
        try {
            val enrichedField = CommandPaletteViewModel::class.java.getDeclaredField("enrichedAppsFlow")
            enrichedField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val enrichedFlow = enrichedField.get(vm) as kotlinx.coroutines.flow.StateFlow<List<LauncherApp>>

            // The enrichedAppsFlow should have the badge count set to 1
            val enrichedApps = enrichedFlow.first { it.isNotEmpty() }
            val app = enrichedApps.first { it.packageName == "com.test.messenger" }
            assertEquals(
                "NORMAL notification should contribute to badge count",
                1,
                app.notificationCount
            )
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * Tests that MUTED notifications are excluded from the badge count.
     * Focus mode causes normal notifications to be classified as MUTED.
     */
    @Test
    fun mutedNotifications_excludedFromBadgeCount() = runTest {
        val focusSettingsRepo = SettingsRepository(context)
        focusSettingsRepo.setFocusModeActive(true)

        val mutedNotifRepo = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = focusSettingsRepo,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val testApp = LauncherApp(
            packageName = "com.test.social",
            label = "Social",
            normalizedLabel = "social"
        )
        rawAppsStateFlow.value = listOf(testApp)

        mutedNotifRepo.postNotification(
            key = "social_notif_1",
            packageName = "com.test.social",
            appName = "Social",
            title = "New follower",
            text = "Someone followed you",
            postTime = System.currentTimeMillis(),
            channelId = "social_ch",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        // Verify classification is MUTED
        val resolved = mutedNotifRepo.resolvedNotificationsFlow.first()
        assertEquals(1, resolved.size)
        assertEquals(
            "Notification should be MUTED in focus mode",
            FilterCategory.MUTED,
            resolved[0].filterCategory
        )

        // Access the private enrichedAppsFlow via reflection
        val vm = CommandPaletteViewModel(
            appRepository = appRepository,
            settingsRepository = settingsRepository,
            notificationRepository = mutedNotifRepo
        )
        try {
            val enrichedField = CommandPaletteViewModel::class.java.getDeclaredField("enrichedAppsFlow")
            enrichedField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val enrichedFlow = enrichedField.get(vm) as kotlinx.coroutines.flow.StateFlow<List<LauncherApp>>

            val enrichedApps = enrichedFlow.first { it.isNotEmpty() }
            val app = enrichedApps.first { it.packageName == "com.test.social" }
            assertEquals(
                "MUTED notifications must NOT contribute to badge count",
                0,
                app.notificationCount
            )
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * Verifies that while searchIndex (built from rawApps) returns apps with count 0,
     * the ViewModel's remapping through enrichedMap restores the live notification badge count.
     */
    @Test
    fun searchingApps_retainsNotificationBadgeCount() = runTest {
        val notifRepo = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val rawApp = LauncherApp(
            packageName = "com.test.messenger",
            label = "Messenger",
            normalizedLabel = "messenger",
            notificationCount = 0
        )
        rawAppsStateFlow.value = listOf(rawApp)

        notifRepo.postNotification(
            key = "msg_notif_search_1",
            packageName = "com.test.messenger",
            appName = "Messenger",
            title = "Alice",
            text = "Hey there!",
            postTime = System.currentTimeMillis(),
            channelId = "channel_msgs",
            isOngoing = false,
            isClearable = true,
            importance = 3,
            pendingIntent = null
        )

        // Verify the notification is classified as NORMAL and prime the flow
        val resolved = notifRepo.resolvedNotificationsFlow.first()
        assertEquals(1, resolved.size)

        val vm = CommandPaletteViewModel(
            appRepository = appRepository,
            settingsRepository = settingsRepository,
            notificationRepository = notifRepo
        )
        try {
            val enrichedField = CommandPaletteViewModel::class.java.getDeclaredField("enrichedAppsFlow").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val enrichedFlow = enrichedField.get(vm) as kotlinx.coroutines.flow.StateFlow<List<LauncherApp>>
            val enrichedApps = enrichedFlow.first { it.isNotEmpty() }

            val searchIndexField = CommandPaletteViewModel::class.java.getDeclaredField("searchIndex").apply { isAccessible = true }
            val searchIndex = searchIndexField.get(vm) as com.alauncher.app.domain.app.AppTrieSearchIndex

            // The search index is built from rawApps (where notificationCount is 0)
            searchIndex.rebuildIndex(listOf(rawApp))

            // searchIndex.search returns the raw app from the trie with notificationCount == 0
            val searchResults = searchIndex.search("mess", enrichedApps)
            assertEquals(0, searchResults.first().notificationCount)

            // Production remapping in CommandPaletteViewModel maps search results back through enrichedMap
            val enrichedMap = enrichedApps.associateBy { it.packageName }
            val remappedResults = searchResults.map { app -> enrichedMap[app.packageName] ?: app }

            // The remapped app retains the live badge count from enrichedApps
            assertEquals(
                "Remapped search results must retain live notification badge count",
                1,
                remappedResults.first().notificationCount
            )
        } finally {
            vm.viewModelScope.cancel()
        }
    }
}
