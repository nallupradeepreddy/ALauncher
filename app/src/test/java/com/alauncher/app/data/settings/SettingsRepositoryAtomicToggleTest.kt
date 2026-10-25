package com.alauncher.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
class SettingsRepositoryAtomicToggleTest {

    private lateinit var context: Context
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repo = SettingsRepository(context)
    }

    @Test
    fun toggleUnfilteredApp_addsAndRemoves() = runBlocking {
        val pkg = "com.example.unfiltered"

        // Initially not in unfilteredApps
        repo.setUnfilteredApps(emptySet())
        assertFalse(repo.settingsFlow.first().unfilteredApps.contains(pkg))

        // Toggle on
        repo.toggleUnfilteredApp(pkg)
        assertTrue(repo.settingsFlow.first().unfilteredApps.contains(pkg))

        // Toggle off
        repo.toggleUnfilteredApp(pkg)
        assertFalse(repo.settingsFlow.first().unfilteredApps.contains(pkg))
    }

    @Test
    fun toggleTransactionAllowedApp_addsAndRemoves() = runBlocking {
        val pkg = "com.example.bank"

        // Set to known state
        repo.toggleTransactionAllowedApp(pkg)
        val hasPkg = repo.settingsFlow.first().transactionAllowedApps.contains(pkg)
        assertTrue(hasPkg)

        // Toggle off
        repo.toggleTransactionAllowedApp(pkg)
        assertFalse(repo.settingsFlow.first().transactionAllowedApps.contains(pkg))
    }

    @Test
    fun toggleDeliveryAllowedApp_addsAndRemoves() = runBlocking {
        val pkg = "com.example.delivery"

        repo.toggleDeliveryAllowedApp(pkg)
        assertTrue(repo.settingsFlow.first().deliveryAllowedApps.contains(pkg))

        repo.toggleDeliveryAllowedApp(pkg)
        assertFalse(repo.settingsFlow.first().deliveryAllowedApps.contains(pkg))
    }

    @Test
    fun toggleMeetingAllowedApp_addsAndRemoves() = runBlocking {
        val pkg = "com.example.meeting"

        repo.toggleMeetingAllowedApp(pkg)
        assertTrue(repo.settingsFlow.first().meetingAllowedApps.contains(pkg))

        repo.toggleMeetingAllowedApp(pkg)
        assertFalse(repo.settingsFlow.first().meetingAllowedApps.contains(pkg))
    }

    @Test
    fun concurrentToggles_noLostUpdates() = runBlocking {
        // Clear unfiltered apps first
        repo.setUnfilteredApps(emptySet())

        // Rapidly toggle 5 distinct apps concurrently
        val packages = (1..5).map { "com.example.concurrent.app$it" }
        val jobs = packages.map { pkg ->
            launch(Dispatchers.Default) {
                repo.toggleUnfilteredApp(pkg)
            }
        }
        jobs.joinAll()

        // All 5 must be present in DataStore — none lost to stale snapshot reads
        val finalState = repo.settingsFlow.first()
        for (pkg in packages) {
            assertTrue(
                "Package $pkg should be present after concurrent atomic toggles",
                finalState.unfilteredApps.contains(pkg)
            )
        }
    }

    @Test
    fun concurrentNotificationFilteringToggles_maintainSynchronizedConsistencyBetweenBootstrapAndDataStore() = runBlocking {
        // Concurrently invoke setNotificationFilteringEnabled with alternating booleans across multiple coroutines
        val iterations = 30
        val jobs = (1..iterations).map { index ->
            launch(Dispatchers.Default) {
                val value = (index % 2 == 0)
                repo.setNotificationFilteringEnabled(value)
            }
        }
        jobs.joinAll()

        // Read final values across all layers
        val flowValue = repo.settingsFlow.first().notificationFilteringEnabled
        val bootstrapFastValue = SettingsRepository.getNotificationFilteringEnabledFast(context)
        val initialCacheValue = repo.getInitialSettingsState().notificationFilteringEnabled

        // Both SharedPreferences and DataStore MUST be in complete linearizable agreement!
        assertEquals("DataStore and Bootstrap Fast getter must be strictly consistent", flowValue, bootstrapFastValue)
        assertEquals("DataStore and Initial State Cache must be strictly consistent", flowValue, initialCacheValue)
    }
}

