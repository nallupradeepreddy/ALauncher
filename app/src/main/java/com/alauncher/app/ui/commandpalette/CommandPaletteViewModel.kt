package com.alauncher.app.ui.commandpalette

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.data.settings.isFocusModeActiveNow
import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.SortMode
import com.alauncher.app.model.hasContent
import com.alauncher.app.domain.app.AppTrieSearchIndex
import com.alauncher.app.data.notification.NotificationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import com.alauncher.app.domain.math.MathExpressionEvaluator
import com.alauncher.app.domain.math.MathResult
import kotlinx.coroutines.flow.map

class CommandPaletteViewModel(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val searchIndex = AppTrieSearchIndex()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val mathResult: StateFlow<MathResult?> = _query.map { q ->
        MathExpressionEvaluator.evaluate(q)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val settingsState: StateFlow<SettingsState> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )

    private val enrichedAppsFlow: StateFlow<List<LauncherApp>> = combine(
        appRepository.rawAppsFlow,
        notificationRepository.resolvedNotificationsFlow
    ) { rawApps, activeNotifications ->
        val notifCounts = activeNotifications
            .filter { it.filterCategory != com.alauncher.app.model.FilterCategory.MUTED && 
                      it.filterCategory != com.alauncher.app.model.FilterCategory.HIDDEN &&
                      !it.packageName.isNullOrBlank() &&
                      it.hasContent }
            .groupBy { it.packageName }
            .mapValues { it.value.size }

        rawApps.map { app ->
            val count = notifCounts[app.packageName] ?: 0
            if (app.notificationCount != count) {
                app.copy(notificationCount = count)
            } else {
                app
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            appRepository.rawAppsFlow.collect { apps ->
                searchIndex.rebuildIndex(apps)
            }
        }
    }

    val apps: StateFlow<List<LauncherApp>> = combine(
        enrichedAppsFlow,
        settingsRepository.settingsFlow,
        _query
    ) { enrichedApps, settings, q ->
        val isSearching = q.trim().isNotEmpty()
        // Filter by search query utilizing search index with relevance scoring & enrichedApps fallback
        var filteredApps = if (isSearching) {
            val searchResults = searchIndex.search(q, enrichedApps)
            val enrichedMap = enrichedApps.associateBy { it.packageName }
            searchResults.map { app -> enrichedMap[app.packageName] ?: app }
        } else {
            enrichedApps
        }

        // Apply normal filters
        filteredApps = filteredApps.filter { app ->
            // Filter out hidden apps
            !app.isHidden
        }

        // Apply Focus Mode logic: hide non-favorite apps when browsing, but allow finding any app when actively searching
        if (!isSearching && settings.isFocusModeActiveNow()) {
            filteredApps = filteredApps.filter { app ->
                if (settings.homeAppsConfigured) {
                    settings.homeApps.contains(app.packageName)
                } else {
                    app.isFavorite
                }
            }
        }

        // Only apply user default sort mode when NOT searching, so search relevance score is preserved at index 0
        if (isSearching) {
            filteredApps
        } else {
            sortAppsList(filteredApps, settings.sortMode)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    private fun sortAppsList(list: List<LauncherApp>, mode: SortMode): List<LauncherApp> {
        return when (mode) {
            SortMode.AlphabeticalAsc -> list.sortedBy { it.normalizedLabel }
            SortMode.AlphabeticalDesc -> list.sortedByDescending { it.normalizedLabel }
            SortMode.FavoritesFirst -> list.sortedWith(compareByDescending<LauncherApp> { it.isFavorite }.thenBy { it.normalizedLabel })
            SortMode.MostUsed -> list.sortedByDescending { it.launchCount }
            SortMode.RecentlyOpened -> list.sortedByDescending { it.lastOpenedTime }
        }
    }

    fun toggleFocusMode(active: Boolean, durationMinutes: Int = 0) {
        viewModelScope.launch {
            settingsRepository.setFocusModeActive(active, if (active) durationMinutes else 0)
        }
    }

    fun recordAppLaunch(packageName: String) {
        viewModelScope.launch {
            appRepository.recordAppLaunch(packageName)
        }
    }

    fun addAppToHomeScreen(packageName: String, context: Context) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val currentHomeApps = settings.homeApps
            val enrichedApps = appRepository.rawAppsFlow.first()
            val installedPkgs = enrichedApps.map { it.packageName }.toSet()
            
            val resolvedHomeApps = if (settings.homeAppsConfigured) {
                currentHomeApps.filter { it in installedPkgs }
            } else {
                enrichedApps.filter { it.isFavorite }.map { it.packageName }
            }
            
            if (resolvedHomeApps.contains(packageName)) {
                val updatedList = listOf(packageName) + (resolvedHomeApps - packageName)
                settingsRepository.setHomeApps(updatedList)
                appRepository.toggleFavorite(packageName, true)
                Toast.makeText(context.applicationContext, "App moved to top of Home screen", Toast.LENGTH_SHORT).show()
            } else if (resolvedHomeApps.size < 10) {
                val updatedList = listOf(packageName) + resolvedHomeApps
                settingsRepository.setHomeApps(updatedList)
                appRepository.toggleFavorite(packageName, true)
                Toast.makeText(context.applicationContext, "Added to Home screen", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context.applicationContext, "Cannot add: Home screen is full (limit 10)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class Factory(
        private val appRepo: AppRepository,
        private val settingsRepo: SettingsRepository,
        private val notifRepo: NotificationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CommandPaletteViewModel(appRepo, settingsRepo, notifRepo) as T
        }
    }
}
