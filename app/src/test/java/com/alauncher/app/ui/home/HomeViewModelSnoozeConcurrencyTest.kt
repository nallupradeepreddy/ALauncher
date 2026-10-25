package com.alauncher.app.ui.home

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.calendar.CalendarRepository
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.AppMetadataEntity
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.domain.notification.NotificationFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSnoozeConcurrencyTest {

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

    private lateinit var app: Application
    private lateinit var viewModel: HomeViewModel
    private lateinit var snoozedKeys: ConcurrentHashMap<String, Long>
    private lateinit var snoozeVersionFlow: MutableStateFlow<Int>
    private lateinit var islandRefreshTriggerFlow: MutableStateFlow<Int>

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        val settingsRepo = SettingsRepository(app)
        val notifRepo = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = settingsRepo,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
        val appRepo = AppRepository(
            context = app,
            appMetadataDao = FakeAppMetadataDao(),
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
        val calendarRepo = CalendarRepository(app)

        viewModel = HomeViewModel(
            application = app,
            appRepository = appRepo,
            settingsRepository = settingsRepo,
            notificationRepository = notifRepo,
            calendarRepository = calendarRepo
        )

        val snoozedKeysField = HomeViewModel::class.java.getDeclaredField("snoozedKeys").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        snoozedKeys = snoozedKeysField.get(viewModel) as ConcurrentHashMap<String, Long>

        val snoozeVersionField = HomeViewModel::class.java.getDeclaredField("_snoozeVersion").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        snoozeVersionFlow = snoozeVersionField.get(viewModel) as MutableStateFlow<Int>

        val islandRefreshField = HomeViewModel::class.java.getDeclaredField("_islandRefreshTrigger").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        islandRefreshTriggerFlow = islandRefreshField.get(viewModel) as MutableStateFlow<Int>
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun getSnoozeDurationMs_matchesProductionContract() {
        assertEquals(5 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.RIDE_DELIVERY))
        assertEquals(5 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.OTP))
        assertEquals(10 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.HIGH_PRIORITY_TASK))
        assertEquals(10 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.CRITICAL_ALERT))
        assertEquals(15 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.BILL_TRANSACTIONAL))
        assertEquals(15 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.AMBIENT_STATE))
        assertEquals(15 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.AMBIENT_INFO))
        assertEquals(15 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.GENERAL_NOTIFICATION))
        assertEquals(15 * 60_000L, HomeViewModel.getSnoozeDurationMs(ContextualIslandType.UPCOMING_MEETING))

        for (type in ContextualIslandType.entries) {
            assertTrue("Duration for $type must be > 0", HomeViewModel.getSnoozeDurationMs(type) > 0)
        }
    }

    @Test
    fun dismissCurrentIsland_whenNoIslandActive_returnsSafelyWithoutMutatingState() {
        val initialSnoozeVersion = snoozeVersionFlow.value
        viewModel.dismissCurrentIsland()
        assertEquals(
            "Snooze version must not change when no island is active",
            initialSnoozeVersion,
            snoozeVersionFlow.value
        )
        assertTrue("Snoozed keys must remain empty", snoozedKeys.isEmpty())
    }

    @Test
    fun atomicSnoozeVersionAndTrigger_incrementsConcurrentlyWithoutLostUpdates() = runBlocking {
        snoozeVersionFlow.value = 0
        islandRefreshTriggerFlow.value = 0

        // Launch 50 concurrent updates to both StateFlows
        val jobs = (1..50).map {
            launch(Dispatchers.Default) {
                snoozeVersionFlow.update { it + 1 }
                islandRefreshTriggerFlow.update { it + 1 }
            }
        }
        jobs.joinAll()

        assertEquals(
            "Atomic .update { it + 1 } must preserve all 50 concurrent increments on _snoozeVersion",
            50,
            snoozeVersionFlow.value
        )
        assertEquals(
            "Atomic .update { it + 1 } must preserve all 50 concurrent increments on _islandRefreshTrigger",
            50,
            islandRefreshTriggerFlow.value
        )
    }

    @Test
    fun conditionalSnoozeRemoval_preservesExtendedSnoozeWhenOldTimerFires() {
        val key = "test_island_key"
        val initialSnoozeUntil = 1000L
        snoozedKeys[key] = initialSnoozeUntil

        // User extends the snooze to 2000L before the first timer finishes
        val extendedSnoozeUntil = 2000L
        snoozedKeys[key] = extendedSnoozeUntil

        // The first timer callback executes with its captured `initialSnoozeUntil`
        val removedByOldTimer = snoozedKeys.remove(key, initialSnoozeUntil)

        // The old timer must NOT remove the newly extended snooze
        assertFalse("Old timer should fail conditional remove because value has changed", removedByOldTimer)
        assertTrue("Snoozed key must still exist in map", snoozedKeys.containsKey(key))
        assertEquals("Snoozed key must still be mapped to extended timestamp", extendedSnoozeUntil, snoozedKeys[key])

        // The new timer callback executes with `extendedSnoozeUntil`
        val removedByNewTimer = snoozedKeys.remove(key, extendedSnoozeUntil)
        assertTrue("New timer should succeed in conditional remove", removedByNewTimer)
        assertFalse("Snoozed key must now be fully cleared", snoozedKeys.containsKey(key))
    }
}
