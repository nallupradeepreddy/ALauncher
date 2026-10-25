package com.alauncher.app

import android.app.Application
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.database.LauncherDatabase
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.domain.notification.NotificationFilterEngine
import com.alauncher.app.service.GlobalExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

class MinimalSmartLauncherApp : Application() {

    // Global lifecycle scope for our database asynchronous operations
    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { LauncherDatabase.getDatabase(this) }

    val settingsRepository by lazy { SettingsRepository(this) }

    val calendarRepository by lazy {
        com.alauncher.app.data.calendar.CalendarRepository(this)
    }

    val appRepository by lazy {
        AppRepository(this, database.appMetadataDao(), applicationScope)
    }

    val filterEngine by lazy { 
        val parser = com.alauncher.app.domain.notification.NotificationParser()
        val classifier = com.alauncher.app.domain.notification.NotificationClassifier(parser)
        NotificationFilterEngine(parser, classifier)
    }

    val notificationRepository by lazy {
        NotificationRepository(
            filterEngine = filterEngine,
            settingsRepository = settingsRepository,
            externalScope = applicationScope
        )
    }

    override fun onCreate() {
        super.onCreate()
        GlobalExceptionHandler.initialize(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            appRepository.onTrimMemory(level)
        } catch (e: Exception) {
            Timber.w(e, "onTrimMemory failed for level: $level")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            appRepository.clearIconCache()
        } catch (e: Exception) {
            Timber.w(e, "onLowMemory failed")
        }
    }
}
