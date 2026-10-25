package com.alauncher.app.data.app

import android.content.ComponentCallbacks2.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.os.UserHandle
import android.util.LruCache
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.alauncher.app.data.database.AppMetadataDao
import com.alauncher.app.data.database.AppMetadataEntity
import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.SortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class AppRepository(
    context: Context,
    private val appMetadataDao: AppMetadataDao,
    private val externalScope: CoroutineScope
) {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager

    // Fallback for drawables with no intrinsic size — approximates adaptive icon RSS.
    private val defaultLauncherIconSizePx: Int by lazy {
        (this.context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.launcherLargeIconSize ?: 192
    }

    // In-memory LRU cache of loaded icons to prevent OOM
    private val iconCache = object : LruCache<String, Drawable>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt() // 1/8th of available memory
    ) {
        override fun sizeOf(key: String, value: Drawable): Int {
            val w = value.intrinsicWidth.takeIf { it > 0 } ?: defaultLauncherIconSizePx
            val h = value.intrinsicHeight.takeIf { it > 0 } ?: defaultLauncherIconSizePx
            return (w * h * 4) / 1024
        }
    }

    // Memory-only backer of currently scanned apps
    private val _rawAppsFlow = MutableStateFlow<List<LauncherApp>>(emptyList())
    val rawAppsFlow: StateFlow<List<LauncherApp>> = _rawAppsFlow.asStateFlow()

    private val _databaseError = MutableStateFlow<String?>(null)
    val databaseError: StateFlow<String?> = _databaseError.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        // First-run / startup asynchronous load of our app database and PackageManager scanning
        externalScope.launch(Dispatchers.IO) {
            refreshAppsList()
            // Observe metadata updates and merge with current list
            appMetadataDao.getAllMetadataFlow()
                .onStart { _databaseError.value = null }
                .catch { exception ->
                    Log.e("AppRepository", "Error observing app metadata updates", exception)
                    _databaseError.value = exception.message ?: "Unknown database error"
                }
                .collect { metadataList ->
                    mergeMetadataWithScanned(metadataList)
                }
        }
    }

    /**
     * Scans the system for installed launcher apps.
     */
    suspend fun refreshAppsList() = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
                val userProfiles = userManager.userProfiles
                
                val freshAppList = mutableListOf<LauncherApp>()
                val dbMetadata = appMetadataDao.getAllMetadataDirect().associateBy { it.packageName }
                
                for (profile in userProfiles) {
                    val activityList = launcherApps.getActivityList(null, profile)
                    for (activityInfo in activityList) {
                        val pkgName = activityInfo.applicationInfo.packageName
                        if (pkgName == context.packageName) continue // Exclude ourselves
                        
                        val label = activityInfo.label.toString()
                        val metadata = dbMetadata[pkgName]
                        
                        freshAppList.add(
                            LauncherApp(
                                packageName = pkgName,
                                label = label,
                                normalizedLabel = label.lowercase(Locale.getDefault()),
                                isFavorite = metadata?.isFavorite ?: false,
                                isHidden = metadata?.isHidden ?: false,
                                launchCount = metadata?.launchCount ?: 0,
                                lastOpenedTime = metadata?.lastOpenedTime ?: 0L,
                                sortWeight = metadata?.sortWeight ?: 0,
                                icon = null // Icons loaded on-demand by UI
                            )
                        )
                    }
                }
                
                val dedupedApps = freshAppList.distinctBy { it.packageName }
                _rawAppsFlow.value = dedupedApps
            } catch (e: Exception) {
                Log.e("AppRepository", "Error scanning launcher apps", e)
            }
        }
    }

    private suspend fun mergeMetadataWithScanned(metadataList: List<AppMetadataEntity>) = refreshMutex.withLock {
        val metadataMap = metadataList.associateBy { it.packageName }
        _rawAppsFlow.update { currentList ->
            currentList.map { app ->
                val meta = metadataMap[app.packageName]
                app.copy(
                    isFavorite = meta?.isFavorite ?: false,
                    isHidden = meta?.isHidden ?: false,
                    launchCount = meta?.launchCount ?: 0,
                    lastOpenedTime = meta?.lastOpenedTime ?: 0L,
                    sortWeight = meta?.sortWeight ?: 0
                )
            }
        }
    }

    /**
     * Synchronously checks memory cache for a loaded icon without background disk/package I/O.
     * Returns immediately for 0-latency Composable rendering.
     */
    fun getCachedIcon(packageName: String): Drawable? {
        return iconCache.get(packageName)?.let {
            it.constantState?.newDrawable()?.mutate() ?: it
        }
    }

    /**
     * Retrieves an icon, using cache if available.
     * Note: Should be called on a background thread for non-cached items.
     */
    fun getIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let {
            val copied = it.constantState?.newDrawable()?.mutate()
            return copied ?: it
        }

        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val profiles = userManager.userProfiles
            
            var drawable: Drawable? = null
            for (profile in profiles) {
                val activities = launcherApps.getActivityList(packageName, profile)
                if (activities.isNotEmpty()) {
                    val info = activities[0]
                    val density = context.resources.displayMetrics.densityDpi
                    drawable = info.getBadgedIcon(density) ?: info.getIcon(density)
                    break
                }
            }
            
            if (drawable != null) {
                iconCache.put(packageName, drawable)
                drawable.constantState?.newDrawable()?.mutate() ?: drawable
            } else {
                Log.w("AppRepository", "No launcher activity icon found for $packageName. Falling back to default.")
                packageManager.defaultActivityIcon.constantState?.newDrawable()?.mutate() ?: packageManager.defaultActivityIcon
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Critical failure loading icon for $packageName. Possible OOM or system error.", e)
            packageManager.defaultActivityIcon
        }
    }

    /**
     * Clears cached icon for a package.
     */
    fun clearIconCache(packageName: String) {
        iconCache.remove(packageName)
    }

    /**
     * Clears the entire icon cache.
     */
    fun clearIconCache() {
        iconCache.evictAll()
    }

    /**
     * Tiered memory trim handling to balance performance, background RSS, and memory safety
     * per Android Vitals bad-behavior threshold guidelines.
     */
    @Suppress("DEPRECATION") // Several TRIM_MEMORY_* constants are @Deprecated in API 34+ but still delivered.
    fun onTrimMemory(level: Int) {
        when (level) {
            // Hard purge — OS is about to kill us or system is critically low
            TRIM_MEMORY_COMPLETE,
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                iconCache.evictAll()
            }

            // Moderate pressure — halve the cache (covers foreground RUNNING_LOW and background MODERATE/BACKGROUND).
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_RUNNING_LOW -> {
                iconCache.trimToSize(iconCache.maxSize() / 2)
            }

            // Launcher just went to background — shed non-essential bitmaps to keep
            // background RSS near-zero per Play Vitals guidance.
            TRIM_MEMORY_UI_HIDDEN -> {
                iconCache.trimToSize(iconCache.maxSize() / 4)
            }

            // TRIM_MEMORY_RUNNING_MODERATE (5) and unknown levels: intentionally no-op.
        }
    }

    @VisibleForTesting
    internal fun iconCacheSize(): Int = iconCache.size()

    @VisibleForTesting
    internal fun iconCacheMaxSize(): Int = iconCache.maxSize()

    @VisibleForTesting
    internal fun putIconForTest(packageName: String, drawable: Drawable) {
        iconCache.put(packageName, drawable)
    }

    /**
     * Increases the launch count of an app and saves to Room.
     */
    suspend fun recordAppLaunch(packageName: String) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        appMetadataDao.recordLaunchAtomic(packageName, timestamp)
    }

    /**
     * Set App favorite status.
     */
    suspend fun toggleFavorite(packageName: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        appMetadataDao.upsertFavorite(packageName, isFavorite)
    }

    /**
     * Set App hidden status.
     */
    suspend fun toggleHidden(packageName: String, isHidden: Boolean) = withContext(Dispatchers.IO) {
        appMetadataDao.upsertHidden(packageName, isHidden)
    }

    /**
     * Resets all app stats atomically.
     */
    suspend fun resetAllAppStats() = withContext(Dispatchers.IO) {
        appMetadataDao.resetAllAppStats()
    }

    /**
     * Completely removes an uninstalled package's metadata from Room DB.
     */
    suspend fun deleteAppMetadata(packageName: String) = withContext(Dispatchers.IO) {
        appMetadataDao.deleteMetadata(packageName)
    }
}
