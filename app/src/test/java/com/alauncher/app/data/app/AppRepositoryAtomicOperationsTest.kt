package com.alauncher.app.data.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.LauncherDatabase
import com.alauncher.app.model.LauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRepositoryAtomicOperationsTest {

    private lateinit var database: LauncherDatabase
    private lateinit var dao: AppMetadataDao
    private lateinit var repository: AppRepository
    private lateinit var rawAppsStateFlow: MutableStateFlow<List<LauncherApp>>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.appMetadataDao()

        repository = AppRepository(
            context = context,
            appMetadataDao = dao,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val rawAppsField = AppRepository::class.java.getDeclaredField("_rawAppsFlow")
        rawAppsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        rawAppsStateFlow = rawAppsField.get(repository) as MutableStateFlow<List<LauncherApp>>
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun toggleFavorite_and_toggleHidden_interleaveWithoutClobbering() = runBlocking {
        val pkg = "com.example.interleave"
        val testApp = LauncherApp(packageName = pkg, label = "Interleave App", normalizedLabel = "interleave app")
        rawAppsStateFlow.value = listOf(testApp)

        // Toggle Favorite -> true
        repository.toggleFavorite(pkg, true)
        val meta1 = dao.getMetadata(pkg)
        assertNotNull(meta1)
        assertTrue(meta1!!.isFavorite)
        assertFalse(meta1.isHidden)

        // Toggle Hidden -> true
        repository.toggleHidden(pkg, true)
        val meta2 = dao.getMetadata(pkg)
        assertNotNull(meta2)
        // BOTH must be true now! The second upsert must not overwrite isFavorite to false!
        assertTrue("isFavorite must remain true after toggling hidden", meta2!!.isFavorite)
        assertTrue("isHidden must be true", meta2.isHidden)

        // Toggle Favorite -> false
        repository.toggleFavorite(pkg, false)
        val meta3 = dao.getMetadata(pkg)
        assertNotNull(meta3)
        assertFalse("isFavorite must now be false", meta3!!.isFavorite)
        assertTrue("isHidden must remain true", meta3.isHidden)
    }

    @Test
    fun recordAppLaunch_atomicallyIncrementsAndPreservesMetadata() = runBlocking {
        val pkg = "com.example.launchable"
        val testApp = LauncherApp(packageName = pkg, label = "Launchable", normalizedLabel = "launchable")
        rawAppsStateFlow.value = listOf(testApp)

        // Set as favorite first
        repository.toggleFavorite(pkg, true)

        // Record launch 1
        repository.recordAppLaunch(pkg)
        val meta1 = dao.getMetadata(pkg)
        assertNotNull(meta1)
        assertEquals(1, meta1!!.launchCount)
        assertTrue(meta1.isFavorite)

        // Record launch 2
        repository.recordAppLaunch(pkg)
        val meta2 = dao.getMetadata(pkg)
        assertNotNull(meta2)
        assertEquals(2, meta2!!.launchCount)
        assertTrue("Favorite status must be preserved across app launches", meta2.isFavorite)
    }

    @Test
    fun concurrent_toggleFavoriteAndHidden_preservesBothFlags() = runBlocking {
        val pkg = "com.example.concurrent"
        val testApp = LauncherApp(packageName = pkg, label = "Concurrent App", normalizedLabel = "concurrent app")
        rawAppsStateFlow.value = listOf(testApp)

        // Launch 5 toggleFavorite(true) and 5 toggleHidden(true) simultaneously.
        // Without @Transaction, the second insert could clobber the first's flag.
        val jobs = (1..5).map {
            launch(Dispatchers.Default) { repository.toggleFavorite(pkg, true) }
        } + (1..5).map {
            launch(Dispatchers.Default) { repository.toggleHidden(pkg, true) }
        }
        jobs.joinAll()

        val meta = dao.getMetadata(pkg)
        assertNotNull("Metadata must exist after concurrent toggles", meta)
        assertTrue(
            "isFavorite must be true after concurrent toggleFavorite(true) calls",
            meta!!.isFavorite
        )
        assertTrue(
            "isHidden must be true after concurrent toggleHidden(true) calls",
            meta.isHidden
        )
    }
}

