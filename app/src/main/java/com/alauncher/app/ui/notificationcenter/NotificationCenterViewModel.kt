package com.alauncher.app.ui.notificationcenter

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.data.settings.isFocusModeActiveNow
import com.alauncher.app.data.settings.isQuietHoursActiveNow
import androidx.compose.runtime.Immutable
import com.alauncher.app.model.LauncherNotification
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
data class NotificationCenterState(
    val priorityList: List<LauncherNotification> = emptyList(),
    val normalList: List<LauncherNotification> = emptyList(),
    val mutedList: List<LauncherNotification> = emptyList(),
    val isFocusMode: Boolean = false,
    val isQuietHours: Boolean = false,
    val searchQuery: String = ""
)

class NotificationCenterViewModel(
    private val notificationRepository: NotificationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private val centerTickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(60000)
        }
    }

    val state: StateFlow<NotificationCenterState> = combine(
        notificationRepository.priorityNotificationsFlow,
        notificationRepository.normalNotificationsFlow,
        notificationRepository.mutedNotificationsFlow,
        settingsRepository.settingsFlow,
        _searchQuery,
        centerTickerFlow
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val priority = flows[0] as List<LauncherNotification>
        @Suppress("UNCHECKED_CAST")
        val normal = flows[1] as List<LauncherNotification>
        @Suppress("UNCHECKED_CAST")
        val muted = flows[2] as List<LauncherNotification>
        val settings = flows[3] as com.alauncher.app.data.settings.SettingsState
        val query = (flows[4] as String).trim()
        val nowMs = flows[5] as Long

        val isQuietNow = settings.isQuietHoursActiveNow(nowMs)
        val isFocusNow = settings.isFocusModeActiveNow(nowMs)

        val matches: (LauncherNotification) -> Boolean = { notif ->
            if (query.isEmpty()) true
            else {
                notif.title?.contains(query, ignoreCase = true) == true ||
                notif.text?.contains(query, ignoreCase = true) == true ||
                notif.appName.contains(query, ignoreCase = true) ||
                notif.subText?.contains(query, ignoreCase = true) == true ||
                notif.bigText?.contains(query, ignoreCase = true) == true
            }
        }

        NotificationCenterState(
            priorityList = priority.filter(matches),
            normalList = normal.filter(matches),
            mutedList = muted.filter(matches),
            isFocusMode = isFocusNow,
            isQuietHours = isQuietNow,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationCenterState()
    )

    fun launchNotification(key: String, context: Context): Boolean {
        val success = notificationRepository.launchNotification(key, context)
        dismissNotification(key)
        return success
    }

    fun dismissNotification(key: String) {
        // Remove locally from UI state
        notificationRepository.removeNotification(key)
        // Also call cancel notification to clear from system tray
        com.alauncher.app.service.NotificationListener.cancelNotification(key)
    }

    fun clearAllNotifications() {
        val currentState = state.value
        // Priority notifications (2FA OTP codes, urgent calls, calendar alarms) are intentionally
        // preserved and NEVER cleared when tapping "Clear All" to protect critical user alerts.
        val keysToRemove = (currentState.normalList + currentState.mutedList).map { it.key }
        if (keysToRemove.isNotEmpty()) {
            notificationRepository.removeNotifications(keysToRemove)
            keysToRemove.forEach { key ->
                com.alauncher.app.service.NotificationListener.cancelNotification(key)
            }
        }
    }

    class Factory(
        private val notifRepo: NotificationRepository,
        private val settingsRepo: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationCenterViewModel(notifRepo, settingsRepo) as T
        }
    }
}
