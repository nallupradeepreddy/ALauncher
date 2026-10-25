package com.alauncher.app.data.app

import android.content.ComponentCallbacks2.*
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.AppMetadataEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("DEPRECATION") // Several TRIM_MEMORY_* constants are @Deprecated in API 34+ but still delivered.
class AppRepositoryTrimMemoryTest {

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

    private lateinit var repo: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = AppRepository(
            context = context,
            appMetadataDao = FakeAppMetadataDao(),
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    // Fixed intrinsic dimensions give a predictable AppRepository.sizeOf() result:
    // (16 * 16 * 4) / 1024 = 1 KB per entry.
    private class OneKbDrawable : ColorDrawable(0) {
        override fun getIntrinsicWidth(): Int = 16
        override fun getIntrinsicHeight(): Int = 16
    }

    private fun fillCache(entries: Int) {
        repeat(entries) { i -> repo.putIconForTest("pkg.$i", OneKbDrawable()) }
    }

    @Test
    fun uiHidden_trimsCacheToAtMostQuarterOfMax() {
        val max = repo.iconCacheMaxSize()
        fillCache(max)
        assertTrue("precondition: cache should be non-empty", repo.iconCacheSize() > 0)

        repo.onTrimMemory(TRIM_MEMORY_UI_HIDDEN)

        assertTrue(
            "UI_HIDDEN must trim to <= max/4, was ${repo.iconCacheSize()} of $max",
            repo.iconCacheSize() <= max / 4
        )
    }

    @Test
    fun moderateAndRunningLowAndBackground_trimCacheToAtMostHalfOfMax() {
        val max = repo.iconCacheMaxSize()

        for (level in listOf(TRIM_MEMORY_MODERATE, TRIM_MEMORY_BACKGROUND, TRIM_MEMORY_RUNNING_LOW)) {
            fillCache(max)
            repo.onTrimMemory(level)
            assertTrue(
                "level=$level must trim to <= max/2, was ${repo.iconCacheSize()} of $max",
                repo.iconCacheSize() <= max / 2
            )
        }
    }

    @Test
    fun completeAndRunningCritical_evictEntireCache() {
        val max = repo.iconCacheMaxSize()

        for (level in listOf(TRIM_MEMORY_COMPLETE, TRIM_MEMORY_RUNNING_CRITICAL)) {
            fillCache(max)
            repo.onTrimMemory(level)
            assertEquals("level=$level must evict entire cache", 0, repo.iconCacheSize())
        }
    }

    @Test
    fun runningModerateAndUnknownLevels_areNoOp() {
        val max = repo.iconCacheMaxSize()
        fillCache(max)
        val before = repo.iconCacheSize()

        repo.onTrimMemory(TRIM_MEMORY_RUNNING_MODERATE)
        assertEquals("RUNNING_MODERATE must not shrink the cache", before, repo.iconCacheSize())

        repo.onTrimMemory(999)
        assertEquals("Unknown levels must not shrink the cache", before, repo.iconCacheSize())
    }

    @Test
    fun clearIconCache_evictsAll() {
        fillCache(repo.iconCacheMaxSize())
        assertTrue(repo.iconCacheSize() > 0)

        repo.clearIconCache()

        assertEquals(0, repo.iconCacheSize())
    }
}
