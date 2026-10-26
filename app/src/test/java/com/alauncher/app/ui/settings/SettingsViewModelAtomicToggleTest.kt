package com.alauncher.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.AppMetadataEntity
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.data.settings.dataStore
import com.alauncher.app.domain.notification.NotificationFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
        runBlocking {
            context.dataStore.edit { it.clear() }
            val bootstrapPrefs = context.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
            bootstrapPrefs.edit().clear().commit()
        }
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
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        runBlocking {
            context.dataStore.edit { it.clear() }
            val bootstrapPrefs = context.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
            bootstrapPrefs.edit().clear().commit()
        }
        Dispatchers.resetMain()
    }

    @Test(timeout = 5_000)
    fun toggleUnfilteredApp_delegatesToRepositoryAtomically() = runBlocking {
        withTimeout(3_000) {
            val pkg = "com.test.unfiltered"
            val collectJob = launch(Dispatchers.Unconfined) {
                viewModel.settingsState.collect {}
            }
            try {
                viewModel.toggleUnfilteredApp(pkg)
                val addedState = viewModel.settingsState.first { it.unfilteredApps.contains(pkg) }
                assertTrue(addedState.unfilteredApps.contains(pkg))

                viewModel.toggleUnfilteredApp(pkg)
                val removedState = viewModel.settingsState.first { !it.unfilteredApps.contains(pkg) }
                assertFalse(removedState.unfilteredApps.contains(pkg))
            } finally {
                collectJob.cancel()
            }
        }
    }

    @Test(timeout = 5_000)
    fun toggleTransactionAllowedApp_delegatesToRepositoryAtomically() = runBlocking {
        withTimeout(3_000) {
            val pkg = "com.test.bank"
            val collectJob = launch(Dispatchers.Unconfined) {
                viewModel.settingsState.collect {}
            }
            try {
                viewModel.toggleTransactionAllowedApp(pkg)
                val addedState = viewModel.settingsState.first { it.transactionAllowedApps.contains(pkg) }
                assertTrue(addedState.transactionAllowedApps.contains(pkg))

                viewModel.toggleTransactionAllowedApp(pkg)
                val removedState = viewModel.settingsState.first { !it.transactionAllowedApps.contains(pkg) }
                assertFalse(removedState.transactionAllowedApps.contains(pkg))
            } finally {
                collectJob.cancel()
            }
        }
    }

    @Test(timeout = 5_000)
    fun toggleDeliveryAllowedApp_delegatesToRepositoryAtomically() = runBlocking {
        withTimeout(3_000) {
            val pkg = "com.test.food"
            val collectJob = launch(Dispatchers.Unconfined) {
                viewModel.settingsState.collect {}
            }
            try {
                viewModel.toggleDeliveryAllowedApp(pkg)
                val addedState = viewModel.settingsState.first { it.deliveryAllowedApps.contains(pkg) }
                assertTrue(addedState.deliveryAllowedApps.contains(pkg))

                viewModel.toggleDeliveryAllowedApp(pkg)
                val removedState = viewModel.settingsState.first { !it.deliveryAllowedApps.contains(pkg) }
                assertFalse(removedState.deliveryAllowedApps.contains(pkg))
            } finally {
                collectJob.cancel()
            }
        }
    }

    @Test(timeout = 5_000)
    fun toggleMeetingAllowedApp_delegatesToRepositoryAtomically() = runBlocking {
        withTimeout(3_000) {
            val pkg = "com.test.meet"
            val collectJob = launch(Dispatchers.Unconfined) {
                viewModel.settingsState.collect {}
            }
            try {
                viewModel.toggleMeetingAllowedApp(pkg)
                val addedState = viewModel.settingsState.first { it.meetingAllowedApps.contains(pkg) }
                assertTrue(addedState.meetingAllowedApps.contains(pkg))

                viewModel.toggleMeetingAllowedApp(pkg)
                val removedState = viewModel.settingsState.first { !it.meetingAllowedApps.contains(pkg) }
                assertFalse(removedState.meetingAllowedApps.contains(pkg))
            } finally {
                collectJob.cancel()
            }
        }
    }

    @Test(timeout = 5_000)
    fun setNotificationFilteringEnabled_updatesViewModelStateFlow() = runBlocking {
        withTimeout(3_000) {
            val collectJob = launch(Dispatchers.Unconfined) {
                viewModel.settingsState.collect {}
            }
            try {
                viewModel.setNotificationFilteringEnabled(true)
                val vmTrue = viewModel.settingsState.first { it.notificationFilteringEnabled }
                assertTrue("ViewModel StateFlow must reflect notificationFilteringEnabled = true", vmTrue.notificationFilteringEnabled)

                viewModel.setNotificationFilteringEnabled(false)
                val vmFalse = viewModel.settingsState.first { !it.notificationFilteringEnabled }
                assertFalse("ViewModel StateFlow must reflect notificationFilteringEnabled = false", vmFalse.notificationFilteringEnabled)
            } finally {
                collectJob.cancel()
            }
        }
    }

    @Test(timeout = 5_000)
    fun notificationFilteringAndImmediateApps_toggleIndependentlyWithoutInterference() = runBlocking {
        withTimeout(3_000) {
            val vipApp1 = "com.vip.chat"
            val vipApp2 = "com.vip.bank"

            val collectJob = launch(Dispatchers.Unconfined) {
                viewModel.settingsState.collect {}
            }
            try {
                // Step 1: Start with filtering off
                viewModel.setNotificationFilteringEnabled(false)
                viewModel.settingsState.first { !it.notificationFilteringEnabled }

                // Step 2: Add immediate apps while filtering is off
                viewModel.toggleUnfilteredApp(vipApp1)
                viewModel.settingsState.first { it.unfilteredApps.contains(vipApp1) }
                viewModel.toggleUnfilteredApp(vipApp2)

                val withApps = viewModel.settingsState.first { state ->
                    state.unfilteredApps.contains(vipApp1) && state.unfilteredApps.contains(vipApp2)
                }
                assertEquals(setOf(vipApp1, vipApp2), withApps.unfilteredApps)
                assertFalse("Enabling immediate apps must not alter notificationFilteringEnabled", withApps.notificationFilteringEnabled)

                // Step 3: Turn filtering on
                viewModel.setNotificationFilteringEnabled(true)
                val filterOn = viewModel.settingsState.first { it.notificationFilteringEnabled }
                assertTrue(filterOn.notificationFilteringEnabled)
                assertEquals("Immediate apps list must remain completely intact when filter is turned on", setOf(vipApp1, vipApp2), filterOn.unfilteredApps)

                // Step 4: Remove one immediate app while filtering is on
                viewModel.toggleUnfilteredApp(vipApp1)
                val appRemoved = viewModel.settingsState.first { !it.unfilteredApps.contains(vipApp1) }
                assertTrue(appRemoved.notificationFilteringEnabled)
                assertEquals(setOf(vipApp2), appRemoved.unfilteredApps)

                // Step 5: Turn filtering off again
                viewModel.setNotificationFilteringEnabled(false)
                val filterOff = viewModel.settingsState.first { !it.notificationFilteringEnabled }
                assertFalse(filterOff.notificationFilteringEnabled)
                assertEquals("Immediate apps list must persist even when filter is turned off", setOf(vipApp2), filterOff.unfilteredApps)
            } finally {
                collectJob.cancel()
            }
        }
    }
}
