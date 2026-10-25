package com.alauncher.app.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppMetadataDaoAtomicTest {

    private lateinit var database: LauncherDatabase
    private lateinit var dao: AppMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.appMetadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertFavorite_createsEntityWhenMissing() = runBlocking {
        val pkg = "com.test.app1"
        dao.upsertFavorite(pkg, isFavorite = true)

        val entity = dao.getMetadata(pkg)
        assertNotNull(entity)
        assertEquals(pkg, entity?.packageName)
        assertEquals(true, entity?.isFavorite)
        assertEquals(false, entity?.isHidden)
        assertEquals(0, entity?.launchCount)
    }

    @Test
    fun upsertHidden_afterUpsertFavorite_preservesFavoriteStatus() = runBlocking {
        val pkg = "com.test.app2"
        // First mark as favorite
        dao.upsertFavorite(pkg, isFavorite = true)

        // Then mark as hidden
        dao.upsertHidden(pkg, isHidden = true)

        // The entity must retain isFavorite = true while isHidden becomes true
        val entity = dao.getMetadata(pkg)
        assertNotNull(entity)
        assertEquals(true, entity?.isFavorite)
        assertEquals(true, entity?.isHidden)
    }

    @Test
    fun upsertFavorite_afterUpsertHidden_preservesHiddenStatus() = runBlocking {
        val pkg = "com.test.app3"
        // First mark as hidden
        dao.upsertHidden(pkg, isHidden = true)

        // Then mark as favorite
        dao.upsertFavorite(pkg, isFavorite = true)

        // The entity must retain isHidden = true
        val entity = dao.getMetadata(pkg)
        assertNotNull(entity)
        assertEquals(true, entity?.isFavorite)
        assertEquals(true, entity?.isHidden)
    }

    @Test
    fun recordLaunchAtomic_insertsOnMissingAndIncrementsOnExisting() = runBlocking {
        val pkg = "com.test.app4"
        val time1 = 1000L
        val time2 = 2000L

        // Non-existing: should insert with launchCount = 1
        dao.recordLaunchAtomic(pkg, time1)
        val entity1 = dao.getMetadata(pkg)
        assertNotNull(entity1)
        assertEquals(1, entity1?.launchCount)
        assertEquals(time1, entity1?.lastOpenedTime)

        // Mark as favorite
        dao.upsertFavorite(pkg, isFavorite = true)

        // Second launch: should increment to 2 and update timestamp without wiping favorite
        dao.recordLaunchAtomic(pkg, time2)
        val entity2 = dao.getMetadata(pkg)
        assertNotNull(entity2)
        assertEquals(2, entity2?.launchCount)
        assertEquals(time2, entity2?.lastOpenedTime)
        assertEquals(true, entity2?.isFavorite)
    }
}

