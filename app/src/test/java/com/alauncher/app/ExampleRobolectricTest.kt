package com.alauncher.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.alauncher.app.data.settings.dataStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Before
  fun setUp() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      context.dataStore.edit { it.clear() }
      val bootstrapPrefs = context.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
      bootstrapPrefs.edit().clear().commit()
    }
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Alauncher", appName)
  }

  @Test
  fun testAppLimitsState() {
    val state = com.alauncher.app.data.settings.SettingsState(
      appLimitsEnabled = true,
      appLimitsMap = mapOf("com.facebook.katana" to 15),
      appLimitsBreakDuration = 20
    )
    org.junit.Assert.assertTrue(state.appLimitsEnabled)
    assertEquals(15, state.appLimitsMap["com.facebook.katana"])
    assertEquals(20, state.appLimitsBreakDuration)
  }

  @Test
  fun testAppLimitsJsonParsing() {
    val jsonStr = "{\"com.instagram.android\":10,\"com.google.android.youtube\":30}"
    val jsonObj = org.json.JSONObject(jsonStr)
    val map = mutableMapOf<String, Int>()
    jsonObj.keys().forEach { key ->
      map[key] = jsonObj.getInt(key)
    }
    assertEquals(2, map.size)
    assertEquals(10, map["com.instagram.android"])
    assertEquals(30, map["com.google.android.youtube"])
  }

  @Test
  fun testHasCompletedWalkthroughFastFreshInstallAndMidTourRelaunch() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    // 1. Fresh install: dataStoreFile doesn't exist, key not in prefs
    val freshResult = com.alauncher.app.data.settings.SettingsRepository.hasCompletedWalkthroughFast(context)
    assertEquals(false, freshResult)
    // Verify it seeded KEY_COMPLETED_WALKTHROUGH = false
    assertEquals(true, prefs.contains("has_completed_walkthrough"))
    assertEquals(false, prefs.getBoolean("has_completed_walkthrough", true))

    val freshDisclosure = com.alauncher.app.data.settings.SettingsRepository.hasSeenPermissionDisclosureFast(context)
    assertEquals(false, freshDisclosure)
    // Verify it seeded KEY_SEEN_DISCLOSURE = false
    assertEquals(true, prefs.contains("has_seen_permission_disclosure"))
    assertEquals(false, prefs.getBoolean("has_seen_permission_disclosure", true))

    // 2. Mid-tour relaunch: simulate user seeing disclosure (creates dataStore file)
    val dataStoreDir = java.io.File(context.filesDir, "datastore")
    dataStoreDir.mkdirs()
    val dataStoreFile = java.io.File(dataStoreDir, "launcher_settings.preferences_pb")
    dataStoreFile.createNewFile()

    // On relaunch mid-tour, hasCompletedWalkthroughFast MUST STILL return false!
    val midTourRelaunchResult = com.alauncher.app.data.settings.SettingsRepository.hasCompletedWalkthroughFast(context)
    assertEquals(false, midTourRelaunchResult)

    // Clean up
    dataStoreFile.delete()
    prefs.edit().clear().commit()
  }

  @Test
  fun testHasCompletedWalkthroughFastLegacyUpgrader() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    // Simulate legacy user with existing DataStore file but no KEY_COMPLETED_WALKTHROUGH in BOOTSTRAP_PREFS
    val dataStoreDir = java.io.File(context.filesDir, "datastore")
    dataStoreDir.mkdirs()
    val dataStoreFile = java.io.File(dataStoreDir, "launcher_settings.preferences_pb")
    dataStoreFile.createNewFile()

    val upgraderResult = com.alauncher.app.data.settings.SettingsRepository.hasCompletedWalkthroughFast(context)
    assertEquals(true, upgraderResult)
    // Verify it seeded true
    assertEquals(true, prefs.getBoolean("has_completed_walkthrough", false))

    // Clean up
    dataStoreFile.delete()
    prefs.edit().clear().commit()
  }

  @Test
  fun testDisclosureFastConsistentWithWalkthroughCompletion() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("alauncher_bootstrap", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    // If walkthrough is marked completed in prefs, hasSeenPermissionDisclosureFast MUST return true
    prefs.edit().putBoolean("has_completed_walkthrough", true).commit()
    assertEquals(true, com.alauncher.app.data.settings.SettingsRepository.hasSeenPermissionDisclosureFast(context))

    val repo = com.alauncher.app.data.settings.SettingsRepository(context)
    val initialState = repo.getInitialSettingsState()
    assertEquals(true, initialState.hasCompletedWalkthrough)
    assertEquals(true, initialState.hasSeenPermissionDisclosure)

    prefs.edit().clear().commit()
  }

  @Test
  fun testRemovePackageFromAllSettings() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.alauncher.app.data.settings.SettingsRepository(context)

    repo.setHomeApps(listOf("com.keep.app", "com.uninstall.app"))
    repo.setUnfilteredApps(setOf("com.keep.app", "com.uninstall.app"))
    repo.setAppLimit("com.uninstall.app", 30)

    val before = repo.settingsFlow.first()
    assertEquals(listOf("com.keep.app", "com.uninstall.app"), before.homeApps)
    assertEquals(setOf("com.keep.app", "com.uninstall.app"), before.unfilteredApps)
    assertEquals(30, before.appLimitsMap["com.uninstall.app"])
    assertEquals(true, before.homeAppsConfigured)

    repo.removePackageFromAllSettings("com.uninstall.app")

    val after = repo.settingsFlow.first()
    assertEquals(listOf("com.keep.app"), after.homeApps)
    assertEquals(setOf("com.keep.app"), after.unfilteredApps)
    assertEquals(false, after.appLimitsMap.containsKey("com.uninstall.app"))
  }

  @Test
  fun testHomeAppsConfiguredEmptyList() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.alauncher.app.data.settings.SettingsRepository(context)

    repo.setHomeApps(emptyList())

    val state = repo.settingsFlow.first()
    assertEquals(emptyList<String>(), state.homeApps)
    assertEquals(true, state.homeAppsConfigured)
  }

  private class TestFakeAppMetadataDao : com.alauncher.app.data.database.AppMetadataDao {
    override fun getAllMetadataFlow(): kotlinx.coroutines.flow.Flow<List<com.alauncher.app.data.database.AppMetadataEntity>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getAllMetadataDirect(): List<com.alauncher.app.data.database.AppMetadataEntity> = emptyList()
    override suspend fun getMetadata(packageName: String): com.alauncher.app.data.database.AppMetadataEntity? = null
    override suspend fun insertMetadata(metadata: com.alauncher.app.data.database.AppMetadataEntity) {}
    override suspend fun updateFavorite(packageName: String, isFavorite: Boolean) {}
    override suspend fun updateHidden(packageName: String, isHidden: Boolean) {}
    override suspend fun incrementLaunch(packageName: String, time: Long): Int = 1
    override suspend fun resetAllAppStats() {}
    override suspend fun deleteMetadata(packageName: String): Int = 1
  }

  @Test
  fun testPackageChangeReceiverFullyRemoved() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settingsRepo = com.alauncher.app.data.settings.SettingsRepository(context)
    val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
    val notifRepo = com.alauncher.app.data.notification.NotificationRepository(
      com.alauncher.app.domain.notification.NotificationFilterEngine(
        com.alauncher.app.domain.notification.NotificationParser(),
        com.alauncher.app.domain.notification.NotificationClassifier(
          com.alauncher.app.domain.notification.NotificationParser()
        )
      ),
      settingsRepo,
      testScope
    )

    val appRepo = com.alauncher.app.data.app.AppRepository(context, TestFakeAppMetadataDao(), testScope)

    settingsRepo.setHomeApps(listOf("com.test.app"))
    notifRepo.postNotification(
      key = "key1",
      packageName = "com.test.app",
      appName = "Test App",
      title = "Hello",
      text = "World",
      postTime = System.currentTimeMillis(),
      channelId = "c1",
      isOngoing = false,
      isClearable = true,
      importance = 3,
      pendingIntent = null
    )

    val receiver = com.alauncher.app.receiver.PackageChangeReceiver(
      appRepository = appRepo,
      settingsRepository = settingsRepo,
      notificationRepository = notifRepo,
      coroutineScope = testScope,
      ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
    )

    val intent = android.content.Intent(android.content.Intent.ACTION_PACKAGE_FULLY_REMOVED).apply {
      data = android.net.Uri.parse("package:com.test.app")
    }

    receiver.onReceive(context, intent)
    
    var settingsAfter = settingsRepo.settingsFlow.first()
    var count = 0
    while (settingsAfter.homeApps.contains("com.test.app") && count < 20) {
      kotlinx.coroutines.delay(100)
      settingsAfter = settingsRepo.settingsFlow.first()
      count++
    }

    assertEquals(emptyList<String>(), settingsAfter.homeApps)
    assertEquals(false, settingsAfter.homeAppsConfigured)
    testScope.cancel()
  }

  @Test
  fun testHomeAppsOrderDistinct() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.alauncher.app.data.settings.SettingsRepository(context)
    repo.setHomeApps(listOf("com.app.a", "com.app.b", "com.app.a"))
    val state = repo.settingsFlow.first()
    assertEquals(listOf("com.app.a", "com.app.b"), state.homeApps)
  }

  @Test
  fun testRemovePackagePartialKeepsConfigured() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.alauncher.app.data.settings.SettingsRepository(context)
    repo.setHomeApps(listOf("com.app.a", "com.app.b"))
    repo.removePackageFromAllSettings("com.app.a")
    val state = repo.settingsFlow.first()
    assertEquals(listOf("com.app.b"), state.homeApps)
    assertEquals(true, state.homeAppsConfigured)

    repo.removePackageFromAllSettings("com.app.b")
    val stateEmpty = repo.settingsFlow.first()
    assertEquals(emptyList<String>(), stateEmpty.homeApps)
    assertEquals(false, stateEmpty.homeAppsConfigured)
  }

  @Test
  fun testImportHomeAppsOrderAsJsonArray() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.alauncher.app.data.settings.SettingsRepository(context)

    val rawJson = """
      {
        "home_apps_order": ["com.app.x", "com.app.y", "com.app.z"]
      }
    """.trimIndent()

    val success = repo.importSettingsFromJson(rawJson)
    assertTrue(success)

    val state = repo.settingsFlow.first()
    assertEquals(listOf("com.app.x", "com.app.y", "com.app.z"), state.homeApps)
    assertEquals(true, state.homeAppsConfigured)
  }

  @Test
  fun testImportSettingsMalformedJson() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.alauncher.app.data.settings.SettingsRepository(context)

    val malformed = "{ this is not valid json }"
    val success = repo.importSettingsFromJson(malformed)
    assertEquals(false, success)
  }

  @Test
  fun testImportSettingsUnknownKeyFallback() = kotlinx.coroutines.runBlocking<Unit> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.alauncher.app.data.settings.SettingsRepository(context)

    val jsonWithUnknownKeys = """
      {
        "unknown_bool": true,
        "unknown_int": 42,
        "unknown_string": "test_value"
      }
    """.trimIndent()

    val success = repo.importSettingsFromJson(jsonWithUnknownKeys)
    assertTrue(success)

    val prefs = context.dataStore.data.first()
    assertEquals(true, prefs[booleanPreferencesKey("unknown_bool")])
    assertEquals(42, prefs[androidx.datastore.preferences.core.intPreferencesKey("unknown_int")])
    assertEquals("test_value", prefs[stringPreferencesKey("unknown_string")])
  }
}

