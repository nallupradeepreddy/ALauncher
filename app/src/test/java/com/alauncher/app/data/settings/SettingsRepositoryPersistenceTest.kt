package com.alauncher.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryPersistenceTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = SettingsRepository(context)
    }

    @Test
    fun removePackageFromAllSettings_removesPackageFromAllPreferences() = runBlocking {
        val keepPkg = "com.keep.app"
        val removePkg = "com.remove.app"

        repository.setHomeApps(listOf(keepPkg, removePkg))
        repository.setUnfilteredApps(setOf(keepPkg, removePkg))
        repository.setAppLimit(removePkg, 30)
        // Add the package to all three island allowlists
        repository.toggleTransactionAllowedApp(removePkg)
        repository.toggleDeliveryAllowedApp(removePkg)
        repository.toggleMeetingAllowedApp(removePkg)

        val before = repository.settingsFlow.first()
        assertTrue(before.homeApps.contains(removePkg))
        assertTrue(before.unfilteredApps.contains(removePkg))
        assertEquals(30, before.appLimitsMap[removePkg])

        // When package is uninstalled/removed
        repository.removePackageFromAllSettings(removePkg)

        val after = repository.settingsFlow.first()
        assertFalse("Removed package should not be in homeApps", after.homeApps.contains(removePkg))
        assertTrue("Kept package should remain in homeApps", after.homeApps.contains(keepPkg))
        assertFalse("Removed package should not be in unfilteredApps", after.unfilteredApps.contains(removePkg))
        assertNull("Removed package should not be in appLimitsMap", after.appLimitsMap[removePkg])
        assertFalse("Removed package should not be in transactionAllowedApps", after.transactionAllowedApps.contains(removePkg))
        assertFalse("Removed package should not be in deliveryAllowedApps", after.deliveryAllowedApps.contains(removePkg))
        assertFalse("Removed package should not be in meetingAllowedApps", after.meetingAllowedApps.contains(removePkg))
    }

    @Test
    fun setHomeApps_maintainsConfiguredFlag() = runBlocking {
        val apps = listOf("com.app.a", "com.app.b", "com.app.c")
        repository.setHomeApps(apps)

        val state = repository.settingsFlow.first()
        assertEquals(apps, state.homeApps)
        assertTrue("homeAppsConfigured must be true when home apps are set", state.homeAppsConfigured)
    }

    @Test
    fun notificationFiltering_newUsersDefaultToFalse_existingUsersDefaultToTrue() = runBlocking {
        val freshContext = ApplicationProvider.getApplicationContext<Context>()
        val bootstrapPrefs = freshContext.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
        bootstrapPrefs.edit().clear().commit()
        val dataStoreFile = java.io.File(freshContext.filesDir, "datastore/launcher_settings.preferences_pb")
        if (dataStoreFile.exists()) dataStoreFile.delete()

        val isExistingFresh = SettingsRepository.isExistingUserForNotificationFiltering(freshContext)
        assertFalse("Fresh install should NOT be marked as existing user", isExistingFresh)

        bootstrapPrefs.edit().clear().putBoolean("has_completed_walkthrough", true).commit()
        bootstrapPrefs.edit().remove("is_existing_user_notif_filter").commit()
        val isExistingUpgrade = SettingsRepository.isExistingUserForNotificationFiltering(freshContext)
        assertTrue("User who completed walkthrough should be identified as existing user", isExistingUpgrade)
    }

    @Test
    fun setNotificationFilteringEnabled_persistsInState() = runBlocking {
        repository.setNotificationFilteringEnabled(true)
        assertTrue(repository.settingsFlow.first().notificationFilteringEnabled)

        repository.setNotificationFilteringEnabled(false)
        assertFalse(repository.settingsFlow.first().notificationFilteringEnabled)
    }

    @Test
    fun userChoiceFalseIsNotOverriddenByExistingUserMigrationOnFutureUpdate() = runBlocking {
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val bootstrapPrefs = testContext.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
        bootstrapPrefs.edit().clear().putBoolean("has_completed_walkthrough", true).commit()

        val repo = SettingsRepository(testContext)
        // User explicitly disables notification filtering
        repo.setNotificationFilteringEnabled(false)

        assertEquals("Initial state must immediately reflect user's false choice", false, repo.getInitialSettingsState().notificationFilteringEnabled)
        assertEquals("Flow state must reflect user's false choice", false, repo.settingsFlow.first().notificationFilteringEnabled)

        // Simulate future release cold restart: fresh repository instance, walkthrough completed & datastore file present
        val futureReleaseRepo = SettingsRepository(testContext)

        // In future releases, even though prior walkthrough completed and preferences file exists,
        // because the value was explicitly set to false, it must NEVER be updated to true!
        assertEquals("Future release initial state must NOT override false to true", false, futureReleaseRepo.getInitialSettingsState().notificationFilteringEnabled)
        assertEquals("Future release flow state must NOT override false to true", false, futureReleaseRepo.settingsFlow.first().notificationFilteringEnabled)
    }

    @Test
    fun newUserCompletingWalkthroughRetainsFalseOnFutureReleases() = runBlocking {
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val freshRepo = SettingsRepository(testContext)
        freshRepo.clearAllForTesting()

        assertEquals("New install initial state must default to false", false, freshRepo.getInitialSettingsState().notificationFilteringEnabled)

        // New user completes walkthrough
        freshRepo.setHasCompletedWalkthrough(true)

        // Simulate subsequent app launch / update in a future release
        val subsequentRepo = SettingsRepository(testContext)
        assertEquals("Completed walkthrough for new user must NOT cause filtering to flip to true in future releases", false, subsequentRepo.getInitialSettingsState().notificationFilteringEnabled)
        assertEquals("Subsequent flow state must remain false for new users", false, subsequentRepo.settingsFlow.first().notificationFilteringEnabled)
    }

    @Test
    fun existingUserReplayingWalkthroughRetainsPreferences() = runBlocking {
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val bootstrapPrefs = testContext.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
        bootstrapPrefs.edit().clear().putBoolean("has_completed_walkthrough", true).commit()

        val repo = SettingsRepository(testContext)
        // Existing user with filtering enabled
        repo.setNotificationFilteringEnabled(true)
        assertEquals(true, repo.settingsFlow.first().notificationFilteringEnabled)

        // Existing user replays and completes walkthrough from Settings
        repo.setHasCompletedWalkthrough(true)
        assertEquals("Replaying walkthrough must not reset existing user's true setting", true, repo.settingsFlow.first().notificationFilteringEnabled)

        // Existing user who had explicitly set false
        repo.setNotificationFilteringEnabled(false)
        assertEquals(false, repo.settingsFlow.first().notificationFilteringEnabled)

        // Replays and completes walkthrough again
        repo.setHasCompletedWalkthrough(true)
        assertEquals("Replaying walkthrough must not reset existing user's false setting", false, repo.settingsFlow.first().notificationFilteringEnabled)
    }

    @Test
    fun exportAndImportSettings_roundtripsNotificationFilteringState() = runBlocking {
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(testContext)

        // Case 1: Export with true
        repo.setNotificationFilteringEnabled(true)
        val jsonTrue = repo.exportSettingsToJson()
        assertTrue("Exported JSON must contain notification_filtering_enabled: true", jsonTrue.contains("\"notification_filtering_enabled\":true"))

        // Change state to false, then import true
        repo.setNotificationFilteringEnabled(false)
        assertFalse(repo.settingsFlow.first().notificationFilteringEnabled)

        val importSuccessTrue = repo.importSettingsFromJson(jsonTrue)
        assertTrue("Importing valid settings JSON must succeed", importSuccessTrue)
        assertTrue("Imported true state must be reflected in flow", repo.settingsFlow.first().notificationFilteringEnabled)
        assertEquals("Imported true state must update bootstrap cache", true, repo.getInitialSettingsState().notificationFilteringEnabled)

        // Case 2: Export with false
        repo.setNotificationFilteringEnabled(false)
        val jsonFalse = repo.exportSettingsToJson()
        assertTrue("Exported JSON must contain notification_filtering_enabled: false", jsonFalse.contains("\"notification_filtering_enabled\":false"))

        // Change state to true, then import false
        repo.setNotificationFilteringEnabled(true)
        assertTrue(repo.settingsFlow.first().notificationFilteringEnabled)

        val importSuccessFalse = repo.importSettingsFromJson(jsonFalse)
        assertTrue("Importing valid settings JSON must succeed", importSuccessFalse)
        assertFalse("Imported false state must be reflected in flow", repo.settingsFlow.first().notificationFilteringEnabled)
        assertEquals("Imported false state must update bootstrap cache", false, repo.getInitialSettingsState().notificationFilteringEnabled)
    }

    @Test
    fun importLegacyJsonBackupWithoutNotificationFiltering_doesNotCorruptExistingPreference() = runBlocking {
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(testContext)

        repo.setNotificationFilteringEnabled(true)
        assertTrue(repo.settingsFlow.first().notificationFilteringEnabled)

        // Legacy backup JSON that was generated before notification_filtering_enabled existed
        val legacyJson = """
            {
                "show_icons": true,
                "theme_mode": "NordicPine",
                "sort_mode": "AlphabeticalAsc"
            }
        """.trimIndent()

        val success = repo.importSettingsFromJson(legacyJson)
        assertTrue("Importing legacy JSON must succeed", success)
        // Must preserve the existing notification filtering setting
        assertTrue("Legacy import without the key must NOT reset notificationFilteringEnabled to false", repo.settingsFlow.first().notificationFilteringEnabled)
    }

    @Test
    fun importLegacyJsonBackupStartingFromFalse_preservesFalsePreference() = runBlocking {
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(testContext)

        // User explicitly turned filtering off
        repo.setNotificationFilteringEnabled(false)
        assertFalse(repo.settingsFlow.first().notificationFilteringEnabled)

        // Legacy backup JSON that was generated before notification_filtering_enabled existed
        val legacyJson = """
            {
                "show_icons": true,
                "theme_mode": "NordicPine",
                "sort_mode": "AlphabeticalAsc"
            }
        """.trimIndent()

        val success = repo.importSettingsFromJson(legacyJson)
        assertTrue("Importing legacy JSON must succeed", success)
        // Must preserve the existing notification filtering setting as false
        assertFalse("Legacy import without the key must NOT flip an explicit false back to true", repo.settingsFlow.first().notificationFilteringEnabled)
        assertEquals("Bootstrap cache must also remain false after legacy import", false, SettingsRepository.getNotificationFilteringEnabledFast(testContext))
    }

    @Test
    fun existingUserUpgradingWithPureDataStoreFile_defaultsToTrue() {
        runBlocking {
            val testContext = ApplicationProvider.getApplicationContext<Context>()
            val bootstrapPrefs = testContext.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
            // Clear bootstrap completely to simulate an upgrader whose BOOTSTRAP_PREFS were never created
            bootstrapPrefs.edit().clear().commit()

            val dataStoreDir = java.io.File(testContext.filesDir, "datastore")
            dataStoreDir.mkdirs()
            val dataStoreFile = java.io.File(dataStoreDir, "launcher_settings.preferences_pb")
            dataStoreFile.createNewFile()

            val isExisting = SettingsRepository.isExistingUserForNotificationFiltering(testContext)
            assertTrue("Existing datastore file must identify user as an existing user", isExisting)

            val defaultVal = SettingsRepository.getNotificationFilteringEnabledFast(testContext)
            assertTrue("Existing user identified via datastore file must default notification filtering to true", defaultVal)

            // Cleanup
            dataStoreFile.delete()
            bootstrapPrefs.edit().clear().commit()
        }
        Unit
    }

    @Test
    fun rapidSequentialToggles_remainConsistentAcrossAllLayers() {
        runBlocking {
            val testContext = ApplicationProvider.getApplicationContext<Context>()
            val repo = SettingsRepository(testContext)

            val toggleSequence = listOf(false, true, false, false, true, true, false)
            for (expected in toggleSequence) {
                repo.setNotificationFilteringEnabled(expected)
                assertEquals("Flow state must match expected after toggle", expected, repo.settingsFlow.first().notificationFilteringEnabled)
                assertEquals("Initial state cache must match expected after toggle", expected, repo.getInitialSettingsState().notificationFilteringEnabled)
                assertEquals("Bootstrap fast getter must match expected after toggle", expected, SettingsRepository.getNotificationFilteringEnabledFast(testContext))
            }
        }
    }

    @Test
    fun importLegacyJsonBackupWithQuickContacts_ignoresPurgedKeysWithoutDataStorePollution() = runBlocking {
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(testContext)
        repo.clearAllForTesting()

        val legacyJsonWithContacts = """
            {
                "show_icons": true,
                "quick_contacts_json": "[{\"name\":\"Mom\",\"uriString\":\"tel:12345\"}]",
                "contacts_enabled": true,
                "quick_contacts": "old_string",
                "sort_mode": "AlphabeticalAsc"
            }
        """.trimIndent()

        val success = repo.importSettingsFromJson(legacyJsonWithContacts)
        assertTrue("Importing legacy backup with quick contacts must succeed", success)
        val state = repo.settingsFlow.first()
        assertTrue(state.showIcons)

        // Ensure legacy keys are not kept in DataStore
        val exported = repo.exportSettingsToJson()
        assertFalse("Exported settings must not contain orphaned quick_contacts_json", exported.contains("quick_contacts_json"))
        assertFalse("Exported settings must not contain orphaned contacts_enabled", exported.contains("contacts_enabled"))
        assertFalse("Exported settings must not contain orphaned quick_contacts", exported.contains("quick_contacts"))
    }
}

