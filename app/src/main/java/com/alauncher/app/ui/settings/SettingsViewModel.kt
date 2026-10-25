package com.alauncher.app.ui.settings

import android.content.Context
import android.app.AppOpsManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.SortMode
import com.alauncher.app.model.ThemeMode
import com.alauncher.app.model.HomeAlignment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.alauncher.app.ui.utils.getAppsWithOverlayPermission
import com.alauncher.app.ui.utils.createManageOverlayIntent

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
    private val appRepository: AppRepository
) : ViewModel() {

    val settingsState: StateFlow<SettingsState> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = settingsRepository.getInitialSettingsState()
    )

    val allApps: StateFlow<List<LauncherApp>> = appRepository.rawAppsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _overlayApps = MutableStateFlow<Set<String>>(emptySet())
    val overlayApps: StateFlow<Set<String>> = _overlayApps.asStateFlow()

    fun refreshOverlayApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = getAppsWithOverlayPermission(context)
            _overlayApps.value = apps
        }
    }

    fun openAppOverlaySettings(context: Context, packageName: String? = null) {
        try {
            val intent = createManageOverlayIntent(packageName, context)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Failed to open overlay settings", e)
        }
    }

    fun setWalkthroughStep(step: Int) {
        viewModelScope.launch {
            settingsRepository.setWalkthroughStep(step)
        }
    }

    fun setHasSeenPermissionDisclosure(seen: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHasSeenPermissionDisclosure(seen)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch {
            settingsRepository.setSortMode(mode)
        }
    }

    fun setShowIcons(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowIcons(show)
        }
    }

    fun setNotificationFilteringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationFilteringEnabled(enabled)
        }
    }

    fun setShowFocusModeButton(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowFocusModeButton(show)
        }
    }

    fun setAllowMeetingsInFocusMode(allow: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAllowMeetingsInFocusMode(allow)
        }
    }

    fun setShowHomeIcons(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHomeIcons(show)
        }
    }

    fun setShowHomeLabels(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHomeLabels(show)
        }
    }

    fun setCalendarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCalendarEnabled(enabled)
        }
    }

    fun setHasCompletedWalkthrough(completed: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHasCompletedWalkthrough(completed)
        }
    }

    fun setFontSizeScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setFontSizeScale(scale)
        }
    }

    fun toggleFocusMode(active: Boolean, durationMinutes: Int = 0) {
        viewModelScope.launch {
            settingsRepository.setFocusModeActive(active, durationMinutes)
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setQuietHoursEnabled(enabled)
        }
    }

    fun setQuietHoursRange(start: String, end: String) {
        viewModelScope.launch {
            settingsRepository.setQuietHoursRange(start, end)
        }
    }

    fun setIslandCategoryEnabled(category: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIslandCategoryEnabled(category, enabled)
        }
    }

    fun setLiveTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLiveTrackingEnabled(enabled)
        }
    }



    fun setHomeApps(apps: List<String>) {
        viewModelScope.launch {
            settingsRepository.setHomeApps(apps)
            val allScanned = appRepository.rawAppsFlow.value
            allScanned.forEach { app ->
                val shouldBeFav = apps.contains(app.packageName)
                if (app.isFavorite != shouldBeFav) {
                    appRepository.toggleFavorite(app.packageName, shouldBeFav)
                }
            }
        }
    }

    fun setHomeAlignment(alignment: HomeAlignment) {
        viewModelScope.launch {
            settingsRepository.setHomeAlignment(alignment)
        }
    }

    fun setThumbDockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setThumbDockEnabled(enabled)
        }
    }

    fun setThumbDockEdgeRight(right: Boolean) {
        viewModelScope.launch {
            settingsRepository.setThumbDockEdgeRight(right)
        }
    }

    fun toggleUnfilteredApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.toggleUnfilteredApp(packageName)
        }
    }

    fun setAppLimitsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAppLimitsEnabled(enabled)
        }
    }

    fun setAppLimitsBreakDuration(durationSeconds: Int) {
        viewModelScope.launch {
            settingsRepository.setAppLimitsBreakDuration(durationSeconds)
        }
    }

    fun setAppLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAppLimit(packageName, limitMinutes)
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }


    fun setTransactionSubtypeEnabled(subtype: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTransactionSubtypeEnabled(subtype, enabled)
        }
    }

    /**
     * Adds or removes a package from the Transaction/Bills contextual-island allowlist.
     */
    fun toggleTransactionAllowedApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.toggleTransactionAllowedApp(packageName)
        }
    }

    /**
     * Adds or removes a package from the Delivery / Ride contextual-island allowlist.
     */
    fun toggleDeliveryAllowedApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.toggleDeliveryAllowedApp(packageName)
        }
    }

    /**
     * Adds or removes a package from the Meeting / Calendar contextual-island allowlist.
     */
    fun toggleMeetingAllowedApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.toggleMeetingAllowedApp(packageName)
        }
    }

    fun resetTransactionAllowedApps() {
        viewModelScope.launch { settingsRepository.resetTransactionAllowedApps() }
    }

    fun resetDeliveryAllowedApps() {
        viewModelScope.launch { settingsRepository.resetDeliveryAllowedApps() }
    }

    fun resetMeetingAllowedApps() {
        viewModelScope.launch { settingsRepository.resetMeetingAllowedApps() }
    }


    suspend fun exportConfig(): String {
        return settingsRepository.exportSettingsToJson()
    }

    fun importConfig(json: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            val success = settingsRepository.importSettingsFromJson(json)
            if (success) onSuccess() else onFailure()
        }
    }

    class Factory(
        private val settingsRepo: SettingsRepository,
        private val notifRepo: NotificationRepository,
        private val appRepo: AppRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepo, notifRepo, appRepo) as T
        }
    }
}
