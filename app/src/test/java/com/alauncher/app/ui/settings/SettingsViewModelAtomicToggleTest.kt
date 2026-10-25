package com.alauncher.app.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.AppMetadataEntity
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.domain.notification.NotificationFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelAtomicToggleTest {

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

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepository(context)
        val notifRepo = NotificationRepository(
            filterEngine = NotificationFilterEngine(),
            settingsRepository = null,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
        val appRepo = AppRepository(
            context = context,
            appMetadataDao = FakeAppMetadataDao(),
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel = SettingsViewModel(
            settingsRepository = settingsRepository,
            notificationRepository = notifRepo,
            appRepository = appRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleUnfilteredApp_delegatesToRepositoryAtomically() = runBlocking {
        val pkg = "com.test.unfiltered"
        settingsRepository.setUnfilteredApps(emptySet())
        assertFalse(settingsRepository.settingsFlow.first().unfilteredApps.contains(pkg))

        viewModel.toggleUnfilteredApp(pkg)
        val addedState = settingsRepository.settingsFlow.first { it.unfilteredApps.contains(pkg) }
        assertTrue(addedState.unfilteredApps.contains(pkg))

        viewModel.toggleUnfilteredApp(pkg)
        val removedState = settingsRepository.settingsFlow.first { !it.unfilteredApps.contains(pkg) }
        assertFalse(removedState.unfilteredApps.contains(pkg))
    }

    @Test
    fun toggleTransactionAllowedApp_delegatesToRepositoryAtomically() = runBlocking {
        val pkg = "com.test.bank"
        viewModel.toggleTransactionAllowedApp(pkg)
        val addedState = settingsRepository.settingsFlow.first { it.transactionAllowedApps.contains(pkg) }
        assertTrue(addedState.transactionAllowedApps.contains(pkg))

        viewModel.toggleTransactionAllowedApp(pkg)
        val removedState = settingsRepository.settingsFlow.first { !it.transactionAllowedApps.contains(pkg) }
        assertFalse(removedState.transactionAllowedApps.contains(pkg))
    }

    @Test
    fun toggleDeliveryAllowedApp_delegatesToRepositoryAtomically() = runBlocking {
        val pkg = "com.test.food"
        viewModel.toggleDeliveryAllowedApp(pkg)
        val addedState = settingsRepository.settingsFlow.first { it.deliveryAllowedApps.contains(pkg) }
        assertTrue(addedState.deliveryAllowedApps.contains(pkg))

        viewModel.toggleDeliveryAllowedApp(pkg)
        val removedState = settingsRepository.settingsFlow.first { !it.deliveryAllowedApps.contains(pkg) }
        assertFalse(removedState.deliveryAllowedApps.contains(pkg))
    }

    @Test
    fun toggleMeetingAllowedApp_delegatesToRepositoryAtomically() = runBlocking {
        val pkg = "com.test.meet"
        viewModel.toggleMeetingAllowedApp(pkg)
        val addedState = settingsRepository.settingsFlow.first { it.meetingAllowedApps.contains(pkg) }
        assertTrue(addedState.meetingAllowedApps.contains(pkg))

        viewModel.toggleMeetingAllowedApp(pkg)
        val removedState = settingsRepository.settingsFlow.first { !it.meetingAllowedApps.contains(pkg) }
        assertFalse(removedState.meetingAllowedApps.contains(pkg))
    }

    @Test
    fun setNotificationFilteringEnabled_updatesViewModelStateFlow() = runBlocking {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.settingsState.collect {}
        }
        try {
            viewModel.setNotificationFilteringEnabled(true)
            val trueState = settingsRepository.settingsFlow.first { state: com.alauncher.app.data.settings.SettingsState -> state.notificationFilteringEnabled }
            assertTrue("SettingsFlow must emit notificationFilteringEnabled = true", trueState.notificationFilteringEnabled)
            val vmTrue = viewModel.settingsState.first { it.notificationFilteringEnabled }
            assertTrue("ViewModel StateFlow must reflect notificationFilteringEnabled = true", vmTrue.notificationFilteringEnabled)

            viewModel.setNotificationFilteringEnabled(false)
            val falseState = settingsRepository.settingsFlow.first { state: com.alauncher.app.data.settings.SettingsState -> !state.notificationFilteringEnabled }
            assertFalse("SettingsFlow must emit notificationFilteringEnabled = false", falseState.notificationFilteringEnabled)
            val vmFalse = viewModel.settingsState.first { !it.notificationFilteringEnabled }
            assertFalse("ViewModel StateFlow must reflect notificationFilteringEnabled = false", vmFalse.notificationFilteringEnabled)
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun notificationFilteringAndImmediateApps_toggleIndependentlyWithoutInterference() = runBlocking {
        val vipApp1 = "com.vip.chat"
        val vipApp2 = "com.vip.bank"

        // Step 1: Start with filtering off
        viewModel.setNotificationFilteringEnabled(false)
        settingsRepository.setUnfilteredApps(emptySet())

        assertFalse(viewModel.settingsState.value.notificationFilteringEnabled)
        assertTrue(viewModel.settingsState.value.unfilteredApps.isEmpty())

        // Step 2: Add immediate apps while filtering is off
        viewModel.toggleUnfilteredApp(vipApp1)
        settingsRepository.settingsFlow.first { it.unfilteredApps.contains(vipApp1) }
        viewModel.toggleUnfilteredApp(vipApp2)

        val withApps = settingsRepository.settingsFlow.first { state: com.alauncher.app.data.settings.SettingsState ->
            state.unfilteredApps.contains(vipApp1) && state.unfilteredApps.contains(vipApp2)
        }
        assertEquals(setOf(vipApp1, vipApp2), withApps.unfilteredApps)
        assertFalse("Enabling immediate apps must not alter notificationFilteringEnabled", withApps.notificationFilteringEnabled)

        // Step 3: Turn filtering on
        viewModel.setNotificationFilteringEnabled(true)
        val filterOn = settingsRepository.settingsFlow.first { state: com.alauncher.app.data.settings.SettingsState -> state.notificationFilteringEnabled }
        assertTrue(filterOn.notificationFilteringEnabled)
        assertEquals("Immediate apps list must remain completely intact when filter is turned on", setOf(vipApp1, vipApp2), filterOn.unfilteredApps)

        // Step 4: Remove one immediate app while filtering is on
        viewModel.toggleUnfilteredApp(vipApp1)
        val appRemoved = settingsRepository.settingsFlow.first { state: com.alauncher.app.data.settings.SettingsState -> !state.unfilteredApps.contains(vipApp1) }
        assertTrue(appRemoved.notificationFilteringEnabled)
        assertEquals(setOf(vipApp2), appRemoved.unfilteredApps)

        // Step 5: Turn filtering off again
        viewModel.setNotificationFilteringEnabled(false)
        val filterOff = settingsRepository.settingsFlow.first { state: com.alauncher.app.data.settings.SettingsState -> !state.notificationFilteringEnabled }
        assertFalse(filterOff.notificationFilteringEnabled)
        assertEquals("Immediate apps list must persist even when filter is turned off", setOf(vipApp2), filterOff.unfilteredApps)
    }
}
