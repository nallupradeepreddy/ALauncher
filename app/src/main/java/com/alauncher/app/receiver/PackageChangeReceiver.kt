package com.alauncher.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.data.notification.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val notificationRepository: NotificationRepository? = null,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED &&
            action != Intent.ACTION_PACKAGE_REMOVED &&
            action != Intent.ACTION_PACKAGE_FULLY_REMOVED &&
            action != Intent.ACTION_PACKAGE_CHANGED &&
            action != Intent.ACTION_PACKAGE_REPLACED) {
            return
        }
        val scheme = intent.data?.schemeSpecificPart

        // Avoid scanning unnecessarily if it's our own package being updated
        if (context != null && scheme == context.packageName) {
            return
        }

        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (com.alauncher.app.BuildConfig.DEBUG) {
            Log.d("PackageChangeReceiver", "Broadcast received: action=$action app=$scheme isReplacing=$isReplacing")
        }
        
        val pendingResult = try { goAsync() } catch (e: Exception) { null }
        coroutineScope.launch(ioDispatcher) {
            try {
                if (scheme != null) {
                    try {
                        appRepository.clearIconCache(scheme)
                    } catch (e: Exception) {
                        Log.e("PackageChangeReceiver", "Error clearing icon cache for $scheme", e)
                    }
                    if ((action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_FULLY_REMOVED) && !isReplacing) {
                        try {
                            appRepository.deleteAppMetadata(scheme)
                        } catch (e: Exception) {
                            Log.e("PackageChangeReceiver", "Error deleting app metadata for $scheme", e)
                        }
                        try {
                            settingsRepository?.removePackageFromAllSettings(scheme)
                        } catch (e: Exception) {
                            Log.e("PackageChangeReceiver", "Error removing package from settings for $scheme", e)
                        }
                        try {
                            notificationRepository?.removeNotificationsForPackage(scheme)
                        } catch (e: Exception) {
                            Log.e("PackageChangeReceiver", "Error removing notifications for $scheme", e)
                        }
                    }
                }
                try {
                    appRepository.refreshAppsList()
                } catch (e: Exception) {
                    Log.e("PackageChangeReceiver", "Error refreshing apps list", e)
                }
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    Log.e("PackageChangeReceiver", "Error finishing pending result", e)
                }
            }
        }
    }
}
