package com.alauncher.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alauncher.app.model.SortMode
import com.alauncher.app.model.ThemeMode
import com.alauncher.app.model.HomeAlignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.IOException
import com.alauncher.app.ui.home.ContextualIslandType
import com.alauncher.app.ui.home.TransactionAlertType
import com.alauncher.app.domain.notification.IslandTrustedApps
import androidx.compose.runtime.Immutable

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

@Immutable
data class SettingsState(
    val sortMode: SortMode = SortMode.AlphabeticalAsc,
    val themeMode: ThemeMode = ThemeMode.NordicPine,
    val showIcons: Boolean = true,
    val fontSizeScale: Float = 1.0f,
    val isFocusModeActive: Boolean = false,
    val focusEndTime: Long = 0L,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val unfilteredApps: Set<String> = emptySet(),
    val notificationFilteringEnabled: Boolean = false,
    val homeApps: List<String> = emptyList(),
    val homeAppsConfigured: Boolean = false,
    val homeAlignment: HomeAlignment = HomeAlignment.Right,
    val thumbDockEnabled: Boolean = true,
    val thumbDockEdgeRight: Boolean = false,
    val calendarEnabled: Boolean = false,
    val showFocusModeButton: Boolean = true,
    val allowMeetingsInFocusMode: Boolean = true,
    val showHomeIcons: Boolean = true,
    val showHomeLabels: Boolean = true,
    val appLimitsEnabled: Boolean = false,
    val appLimitsMap: Map<String, Int> = emptyMap(),
    val appLimitsBreakDuration: Int = 15,
    val enabledIslandCategories: Set<String> = ContextualIslandType.entries.map { it.name }.toSet(),
    val liveTrackingEnabled: Boolean = false,
    val enabledTransactionSubtypes: Set<String> = TransactionAlertType.entries.map { it.name }.toSet(),
    /**
     * Per-category allowlists of package names permitted to surface on the Contextual Island.
     *
     * OTP does NOT have an allowlist — any app posting a well-formed OTP can bubble up
     * (this is validated by the classifier's trust checks).
     *
     * These are seeded from `IslandTrustedApps` curated defaults on first launch; users can
     * add or remove apps via the Settings screen and the persisted set takes over.
     */
    val transactionAllowedApps: Set<String> = IslandTrustedApps.DEFAULT_TRANSACTION_APPS,
    val deliveryAllowedApps: Set<String> = IslandTrustedApps.DEFAULT_DELIVERY_APPS,
    val meetingAllowedApps: Set<String> = IslandTrustedApps.DEFAULT_MEETING_APPS,
    val hasCompletedWalkthrough: Boolean = false,
    val hasSeenPermissionDisclosure: Boolean = false,
    val walkthroughStep: Int = 0
)

class SettingsRepository(context: Context) {

    private val context = context.applicationContext
    private val dataStore = this.context.dataStore

    companion object {
        const val PRIVACY_POLICY_URL = "https://sites.google.com/view/alauncher-privacy-policy/home"
        private const val BOOTSTRAP_PREFS = "alauncher_bootstrap"
        private const val KEY_SEEN_DISCLOSURE = "has_seen_permission_disclosure"
        private const val KEY_COMPLETED_WALKTHROUGH = "has_completed_walkthrough"

        fun hasSeenPermissionDisclosureFast(context: Context): Boolean {
            val prefs = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_COMPLETED_WALKTHROUGH, false)) {
                return true
            }
            if (prefs.contains(KEY_SEEN_DISCLOSURE)) {
                return prefs.getBoolean(KEY_SEEN_DISCLOSURE, false)
            }
            // Migration for existing users: if legacy DataStore file exists, seed and return true
            val dataStoreFile = java.io.File(context.filesDir, "datastore/launcher_settings.preferences_pb")
            if (dataStoreFile.exists()) {
                prefs.edit().putBoolean(KEY_SEEN_DISCLOSURE, true).apply()
                return true
            }
            // Fresh install: seed explicitly as false so subsequent launches don't trigger migration logic
            prefs.edit().putBoolean(KEY_SEEN_DISCLOSURE, false).apply()
            return false
        }

        fun hasCompletedWalkthroughFast(context: Context): Boolean {
            val prefs = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_COMPLETED_WALKTHROUGH)) {
                return prefs.getBoolean(KEY_COMPLETED_WALKTHROUGH, false)
            }
            val dataStoreFile = java.io.File(context.filesDir, "datastore/launcher_settings.preferences_pb")
            if (dataStoreFile.exists()) {
                // Legacy user upgrading from an older version where KEY_COMPLETED_WALKTHROUGH wasn't in BOOTSTRAP_PREFS
                prefs.edit().putBoolean(KEY_COMPLETED_WALKTHROUGH, true).apply()
                return true
            }
            // Fresh install: seed explicitly as false so subsequent launches don't trigger migration logic
            prefs.edit().putBoolean(KEY_COMPLETED_WALKTHROUGH, false).apply()
            return false
        }

        private const val KEY_IS_EXISTING_USER_FOR_NOTIF_FILTER = "is_existing_user_notif_filter"
        private const val KEY_NOTIFICATION_FILTERING_ENABLED = "notification_filtering_enabled"

        fun isExistingUserForNotificationFiltering(context: Context): Boolean {
            val prefs = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_IS_EXISTING_USER_FOR_NOTIF_FILTER)) {
                return prefs.getBoolean(KEY_IS_EXISTING_USER_FOR_NOTIF_FILTER, false)
            }
            val dataStoreFile = java.io.File(context.filesDir, "datastore/launcher_settings.preferences_pb")
            val isExisting = prefs.getBoolean(KEY_COMPLETED_WALKTHROUGH, false) ||
                             prefs.getBoolean(KEY_SEEN_DISCLOSURE, false) ||
                             dataStoreFile.exists()

            prefs.edit().putBoolean(KEY_IS_EXISTING_USER_FOR_NOTIF_FILTER, isExisting).apply()
            return isExisting
        }

        fun getNotificationFilteringEnabledFast(context: Context): Boolean {
            val prefs = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_NOTIFICATION_FILTERING_ENABLED)) {
                return prefs.getBoolean(KEY_NOTIFICATION_FILTERING_ENABLED, false)
            }
            // Value is null (not yet explicitly configured or migrated).
            // Only evaluate migration default when null: if prior walkthrough completion or
            // existing preferences file exists, turn it to true for existing users (false for new users).
            val isExisting = isExistingUserForNotificationFiltering(context)
            prefs.edit().putBoolean(KEY_NOTIFICATION_FILTERING_ENABLED, isExisting).apply()
            return isExisting
        }

        private val SORT_MODE = stringPreferencesKey("sort_mode")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val SHOW_ICONS = booleanPreferencesKey("show_icons")
        private val NOTIFICATION_FILTERING_ENABLED = booleanPreferencesKey("notification_filtering_enabled")
        private val FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
        private val FOCUS_MODE_ACTIVE = booleanPreferencesKey("focus_mode_active")
        private val FOCUS_END_TIME = longPreferencesKey("focus_end_time")
        private val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
        private val QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
        private val UNFILTERED_APPS = stringSetPreferencesKey("unfiltered_apps_set")
        private val HOME_APPS_ORDER = stringPreferencesKey("home_apps_order")
        private val HOME_ALIGNMENT = stringPreferencesKey("home_alignment")
        private val THUMB_DOCK_ENABLED = booleanPreferencesKey("thumb_dock_enabled")
        private val THUMB_DOCK_EDGE_RIGHT = booleanPreferencesKey("thumb_dock_edge_right")
        private val CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        private val SHOW_FOCUS_MODE_BUTTON = booleanPreferencesKey("show_focus_mode_button")
        private val ALLOW_MEETINGS_IN_FOCUS_MODE = booleanPreferencesKey("allow_meetings_in_focus_mode")
        private val SHOW_HOME_ICONS = booleanPreferencesKey("show_home_icons")
        private val SHOW_HOME_LABELS = booleanPreferencesKey("show_home_labels")
        private val APP_LIMITS_ENABLED = booleanPreferencesKey("app_limits_enabled")
        private val APP_LIMITS_JSON = stringPreferencesKey("app_limits_json")
        private val APP_LIMITS_BREAK_DURATION = intPreferencesKey("app_limits_break_duration")
        private val ENABLED_ISLAND_CATEGORIES = stringSetPreferencesKey("enabled_island_categories")
        private val LIVE_TRACKING_ENABLED = booleanPreferencesKey("live_tracking_enabled")
        private val ENABLED_TRANSACTION_SUBTYPES = stringSetPreferencesKey("enabled_transaction_subtypes")
        private val TRANSACTION_ALLOWED_APPS = stringSetPreferencesKey("transaction_allowed_apps")
        private val DELIVERY_ALLOWED_APPS = stringSetPreferencesKey("delivery_allowed_apps")
        private val MEETING_ALLOWED_APPS = stringSetPreferencesKey("meeting_allowed_apps")
        // Sentinel keys to distinguish "user cleared the list" from "never customised".
        // Without these, the allowlist getter cannot tell an empty set apart from a first-launch
        // state, so it would keep reinstating the curated defaults.
        private val TRANSACTION_ALLOWED_APPS_INITIALIZED = booleanPreferencesKey("transaction_allowed_apps_initialized")
        private val DELIVERY_ALLOWED_APPS_INITIALIZED = booleanPreferencesKey("delivery_allowed_apps_initialized")
        private val MEETING_ALLOWED_APPS_INITIALIZED = booleanPreferencesKey("meeting_allowed_apps_initialized")
        private val HAS_COMPLETED_WALKTHROUGH = booleanPreferencesKey("has_completed_walkthrough")
        private val HAS_SEEN_PERMISSION_DISCLOSURE = booleanPreferencesKey("has_seen_permission_disclosure")
        private val WALKTHROUGH_STEP = intPreferencesKey("walkthrough_step")

        private val KNOWN_STRING_KEYS = setOf(
            SORT_MODE.name,
            THEME_MODE.name,
            QUIET_HOURS_START.name,
            QUIET_HOURS_END.name,
            HOME_ALIGNMENT.name
            // Note: HOME_APPS_ORDER and APP_LIMITS_JSON
            // have dedicated explicit handlers in importSettingsFromJson
        )

        private val KNOWN_BOOLEAN_KEYS = setOf(
            SHOW_ICONS.name,
            NOTIFICATION_FILTERING_ENABLED.name,
            FOCUS_MODE_ACTIVE.name,
            QUIET_HOURS_ENABLED.name,
            THUMB_DOCK_ENABLED.name,
            THUMB_DOCK_EDGE_RIGHT.name,
            CALENDAR_ENABLED.name,
            SHOW_FOCUS_MODE_BUTTON.name,
            ALLOW_MEETINGS_IN_FOCUS_MODE.name,
            SHOW_HOME_ICONS.name,
            SHOW_HOME_LABELS.name,
            APP_LIMITS_ENABLED.name,
            LIVE_TRACKING_ENABLED.name,
            TRANSACTION_ALLOWED_APPS_INITIALIZED.name,
            DELIVERY_ALLOWED_APPS_INITIALIZED.name,
            MEETING_ALLOWED_APPS_INITIALIZED.name,
            HAS_COMPLETED_WALKTHROUGH.name,
            HAS_SEEN_PERMISSION_DISCLOSURE.name
        )

        private val KNOWN_STRING_SET_KEYS = setOf(
            UNFILTERED_APPS.name,
            ENABLED_ISLAND_CATEGORIES.name,
            ENABLED_TRANSACTION_SUBTYPES.name,
            TRANSACTION_ALLOWED_APPS.name,
            DELIVERY_ALLOWED_APPS.name,
            MEETING_ALLOWED_APPS.name
        )

        private val KNOWN_INT_KEYS = setOf(
            APP_LIMITS_BREAK_DURATION.name,
            WALKTHROUGH_STEP.name
        )

        private val KNOWN_LONG_KEYS = setOf(
            FOCUS_END_TIME.name
        )

        private val KNOWN_FLOAT_KEYS = setOf(
            FONT_SIZE_SCALE.name
        )
    }

    fun getInitialSettingsState(): SettingsState {
        return SettingsState(
            hasCompletedWalkthrough = hasCompletedWalkthroughFast(context),
            hasSeenPermissionDisclosure = hasSeenPermissionDisclosureFast(context),
            notificationFilteringEnabled = getNotificationFilteringEnabledFast(context)
        )
    }

    val settingsFlow: Flow<SettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val unfApps = preferences[UNFILTERED_APPS] ?: emptySet()
            val notifFilterEnabled = if (preferences.contains(NOTIFICATION_FILTERING_ENABLED)) {
                // Key is present in DataStore (not null).
                // ALWAYS respect the persisted value (whether true or false).
                // Never override with migration logic in future releases.
                val explicitVal = preferences[NOTIFICATION_FILTERING_ENABLED] == true
                val prefs = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
                if (!prefs.contains(KEY_NOTIFICATION_FILTERING_ENABLED) || prefs.getBoolean(KEY_NOTIFICATION_FILTERING_ENABLED, !explicitVal) != explicitVal) {
                    prefs.edit().putBoolean(KEY_NOTIFICATION_FILTERING_ENABLED, explicitVal).apply()
                }
                explicitVal
            } else {
                // Key is null in DataStore (unconfigured / first run before explicit persistence).
                // Consider default value: if prior walkthrough completion or existing preferences file exists,
                // turn it to true for existing users (false for new users).
                getNotificationFilteringEnabledFast(context)
            }
            val rawHomeOrder = preferences[HOME_APPS_ORDER]
            val isHomeOrderConfigured = rawHomeOrder != null
            val hmAppsOrdered = rawHomeOrder?.split(",")?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()
            val enabledIslandCats = preferences[ENABLED_ISLAND_CATEGORIES] ?: ContextualIslandType.entries.map { it.name }.toSet()
            val liveTracking = preferences[LIVE_TRACKING_ENABLED] == true
            val enabledTxSubtypes = preferences[ENABLED_TRANSACTION_SUBTYPES] ?: TransactionAlertType.entries.map { it.name }.toSet()

            // Per-category allowlists: use the persisted set if the user has ever customised
            // it, otherwise fall back to the curated defaults.
            val txnAllowedApps = if (preferences[TRANSACTION_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[TRANSACTION_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_TRANSACTION_APPS
            }
            val deliveryAllowedApps = if (preferences[DELIVERY_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[DELIVERY_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_DELIVERY_APPS
            }
            val meetingAllowedApps = if (preferences[MEETING_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[MEETING_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_MEETING_APPS
            }

            SettingsState(
                sortMode = try {
                    SortMode.valueOf(preferences[SORT_MODE] ?: SortMode.AlphabeticalAsc.name)
                } catch (e: Exception) {
                    SortMode.AlphabeticalAsc
                },
                themeMode = try {
                    ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.NordicPine.name)
                } catch (e: Exception) {
                    ThemeMode.NordicPine
                },
                showIcons = preferences[SHOW_ICONS] != false, // Default to true
                fontSizeScale = preferences[FONT_SIZE_SCALE] ?: 1.0f,
                isFocusModeActive = preferences[FOCUS_MODE_ACTIVE] ?: false,
                focusEndTime = preferences[FOCUS_END_TIME] ?: 0L,
                quietHoursEnabled = preferences[QUIET_HOURS_ENABLED] ?: false,
                quietHoursStart = preferences[QUIET_HOURS_START] ?: "22:00",
                quietHoursEnd = preferences[QUIET_HOURS_END] ?: "07:00",
                unfilteredApps = unfApps,
                notificationFilteringEnabled = notifFilterEnabled,
                homeApps = hmAppsOrdered,
                homeAppsConfigured = isHomeOrderConfigured,
                homeAlignment = try {
                    HomeAlignment.valueOf(preferences[HOME_ALIGNMENT] ?: HomeAlignment.Right.name)
                } catch (e: Exception) {
                    HomeAlignment.Right
                },
                thumbDockEnabled = preferences[THUMB_DOCK_ENABLED] != false,
                thumbDockEdgeRight = preferences[THUMB_DOCK_EDGE_RIGHT] == true,
                calendarEnabled = preferences[CALENDAR_ENABLED] == true,
                showFocusModeButton = preferences[SHOW_FOCUS_MODE_BUTTON] != false,
                allowMeetingsInFocusMode = preferences[ALLOW_MEETINGS_IN_FOCUS_MODE] != false,
                showHomeIcons = preferences[SHOW_HOME_ICONS] != false,
                showHomeLabels = preferences[SHOW_HOME_LABELS] != false,
                appLimitsEnabled = preferences[APP_LIMITS_ENABLED] == true,
                appLimitsMap = try {
                    val jsonStr = preferences[APP_LIMITS_JSON] ?: "{}"
                    val jsonObj = JSONObject(jsonStr)
                    val map = mutableMapOf<String, Int>()
                    jsonObj.keys().forEach { key ->
                        map[key] = jsonObj.getInt(key)
                    }
                    map
                } catch (e: Exception) {
                    emptyMap()
                },
                appLimitsBreakDuration = preferences[APP_LIMITS_BREAK_DURATION] ?: 15,
                enabledIslandCategories = enabledIslandCats,
                liveTrackingEnabled = liveTracking,
                enabledTransactionSubtypes = enabledTxSubtypes,
                transactionAllowedApps = txnAllowedApps,
                deliveryAllowedApps = deliveryAllowedApps,
                meetingAllowedApps = meetingAllowedApps,
                hasCompletedWalkthrough = preferences[HAS_COMPLETED_WALKTHROUGH] ?: false,
                hasSeenPermissionDisclosure = preferences[HAS_SEEN_PERMISSION_DISCLOSURE] == true || preferences[HAS_COMPLETED_WALKTHROUGH] == true,
                walkthroughStep = preferences[WALKTHROUGH_STEP] ?: 0
            )
        }

    suspend fun setHasSeenPermissionDisclosure(seen: Boolean) {
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SEEN_DISCLOSURE, seen)
            .apply()
        dataStore.edit { it[HAS_SEEN_PERMISSION_DISCLOSURE] = seen }
    }

    suspend fun setWalkthroughStep(step: Int) {
        dataStore.edit { it[WALKTHROUGH_STEP] = step }
    }

    suspend fun setHasCompletedWalkthrough(completed: Boolean) {
        val editor = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).edit()
        editor.putBoolean(KEY_COMPLETED_WALKTHROUGH, completed)
        val defaultNotifFilter = if (completed) {
            editor.putBoolean(KEY_SEEN_DISCLOSURE, true)
            // Ensures KEY_NOTIFICATION_FILTERING_ENABLED is seeded once into BOOTSTRAP_PREFS if missing
            getNotificationFilteringEnabledFast(context)
        } else false
        editor.apply()

        dataStore.edit {
            it[HAS_COMPLETED_WALKTHROUGH] = completed
            if (completed) {
                it[HAS_SEEN_PERMISSION_DISCLOSURE] = true
                it[WALKTHROUGH_STEP] = 0
                if (!it.contains(NOTIFICATION_FILTERING_ENABLED)) {
                    it[NOTIFICATION_FILTERING_ENABLED] = defaultNotifFilter
                }
            }
        }
    }

    suspend fun setCalendarEnabled(enabled: Boolean) {
        dataStore.edit { it[CALENDAR_ENABLED] = enabled }
    }

    suspend fun setSortMode(sortMode: SortMode) {
        dataStore.edit { it[SORT_MODE] = sortMode.name }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = themeMode.name }
    }

    suspend fun setShowIcons(show: Boolean) {
        dataStore.edit { it[SHOW_ICONS] = show }
    }

    suspend fun setNotificationFilteringEnabled(enabled: Boolean) {
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_FILTERING_ENABLED, enabled)
            .apply()
        dataStore.edit { it[NOTIFICATION_FILTERING_ENABLED] = enabled }
    }

    suspend fun setShowFocusModeButton(show: Boolean) {
        dataStore.edit { it[SHOW_FOCUS_MODE_BUTTON] = show }
    }

    suspend fun setAllowMeetingsInFocusMode(allow: Boolean) {
        dataStore.edit { it[ALLOW_MEETINGS_IN_FOCUS_MODE] = allow }
    }

    suspend fun setShowHomeIcons(show: Boolean) {
        dataStore.edit { it[SHOW_HOME_ICONS] = show }
    }

    suspend fun setShowHomeLabels(show: Boolean) {
        dataStore.edit { it[SHOW_HOME_LABELS] = show }
    }

    suspend fun setFontSizeScale(scale: Float) {
        dataStore.edit { it[FONT_SIZE_SCALE] = scale }
    }

    suspend fun setFocusModeActive(active: Boolean, durationMinutes: Int = 0) {
        val endTime = if (active && durationMinutes > 0) {
            System.currentTimeMillis() + (durationMinutes.toLong() * 60L * 1000L)
        } else if (active) {
            -1L // Continuous focus mode
        } else {
            0L // Inactive
        }
        dataStore.edit {
            it[FOCUS_MODE_ACTIVE] = active
            it[FOCUS_END_TIME] = endTime
        }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        dataStore.edit { it[QUIET_HOURS_ENABLED] = enabled }
    }

    suspend fun setQuietHoursRange(start: String, end: String) {
        dataStore.edit {
            it[QUIET_HOURS_START] = start
            it[QUIET_HOURS_END] = end
        }
    }

    suspend fun setAppLimitsEnabled(enabled: Boolean) {
        dataStore.edit { it[APP_LIMITS_ENABLED] = enabled }
    }

    suspend fun setAppLimitsBreakDuration(durationSeconds: Int) {
        val validated = durationSeconds.coerceIn(1, 30)
        dataStore.edit { it[APP_LIMITS_BREAK_DURATION] = validated }
    }

    suspend fun setAppLimit(packageName: String, limitMinutes: Int) {
        dataStore.edit { preferences ->
            val jsonStr = preferences[APP_LIMITS_JSON] ?: "{}"
            val jsonObj = try {
                JSONObject(jsonStr)
            } catch (e: Exception) {
                JSONObject()
            }
            if (limitMinutes <= 0) {
                jsonObj.remove(packageName)
            } else {
                jsonObj.put(packageName, limitMinutes)
            }
            preferences[APP_LIMITS_JSON] = jsonObj.toString()
        }
    }



    suspend fun setUnfilteredApps(apps: Set<String>) {
        dataStore.edit { it[UNFILTERED_APPS] = apps }
    }

    suspend fun toggleUnfilteredApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[UNFILTERED_APPS] ?: emptySet()
            val updated = if (current.contains(packageName)) current - packageName else current + packageName
            prefs[UNFILTERED_APPS] = updated
        }
    }

    suspend fun setHomeApps(apps: List<String>) {
        dataStore.edit { 
            it[HOME_APPS_ORDER] = apps.joinToString(",")
        }
    }

    /**
     * Completely removes an uninstalled package from homeApps, unfilteredApps, app limits, and island allowlists.
     */
    suspend fun removePackageFromAllSettings(packageName: String) {
        dataStore.edit { prefs ->
            val currentHome = prefs[HOME_APPS_ORDER]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            if (currentHome.contains(packageName)) {
                val newList = currentHome - packageName
                if (newList.isEmpty()) {
                    prefs.remove(HOME_APPS_ORDER)
                } else {
                    prefs[HOME_APPS_ORDER] = newList.joinToString(",")
                }
            }

            val currentUnfiltered = prefs[UNFILTERED_APPS] ?: emptySet()
            if (currentUnfiltered.contains(packageName)) {
                prefs[UNFILTERED_APPS] = currentUnfiltered - packageName
            }

            val jsonStr = prefs[APP_LIMITS_JSON] ?: "{}"
            try {
                val jsonObj = JSONObject(jsonStr)
                if (jsonObj.has(packageName)) {
                    jsonObj.remove(packageName)
                    prefs[APP_LIMITS_JSON] = jsonObj.toString()
                }
            } catch (e: Exception) {
                // Ignore corrupted JSON
            }

            val currentTx = prefs[TRANSACTION_ALLOWED_APPS] ?: emptySet()
            if (currentTx.contains(packageName)) {
                prefs[TRANSACTION_ALLOWED_APPS] = currentTx - packageName
            }
            val currentDel = prefs[DELIVERY_ALLOWED_APPS] ?: emptySet()
            if (currentDel.contains(packageName)) {
                prefs[DELIVERY_ALLOWED_APPS] = currentDel - packageName
            }
            val currentMeet = prefs[MEETING_ALLOWED_APPS] ?: emptySet()
            if (currentMeet.contains(packageName)) {
                prefs[MEETING_ALLOWED_APPS] = currentMeet - packageName
            }
        }
    }

    suspend fun setHomeAlignment(alignment: HomeAlignment) {
        dataStore.edit { it[HOME_ALIGNMENT] = alignment.name }
    }

    suspend fun setThumbDockEnabled(enabled: Boolean) {
        dataStore.edit { it[THUMB_DOCK_ENABLED] = enabled }
    }

    suspend fun setThumbDockEdgeRight(right: Boolean) {
        dataStore.edit { it[THUMB_DOCK_EDGE_RIGHT] = right }
    }

    suspend fun setIslandCategoryEnabled(category: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[ENABLED_ISLAND_CATEGORIES] ?: ContextualIslandType.entries.map { it.name }.toSet()
            val updated = current.toMutableSet()
            if (enabled) {
                updated.add(category)
            } else {
                updated.remove(category)
            }
            preferences[ENABLED_ISLAND_CATEGORIES] = updated
        }
    }

    suspend fun setLiveTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[LIVE_TRACKING_ENABLED] = enabled }
    }


    suspend fun setTransactionSubtypeEnabled(subtype: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[ENABLED_TRANSACTION_SUBTYPES] ?: TransactionAlertType.entries.map { it.name }.toSet()
            val updated = current.toMutableSet()
            if (enabled) {
                updated.add(subtype)
            } else {
                updated.remove(subtype)
            }
            preferences[ENABLED_TRANSACTION_SUBTYPES] = updated
        }
    }

    /**
     * Explicit setter: sets whether a package is allowed to surface transaction alerts on the Island.
     * Use this method when the desired state (true/false) is known explicitly (e.g. batch imports or programmatic setup).
     * For interactive UI toggles, prefer [toggleTransactionAllowedApp] which avoids client-side read-modify-write races.
     */
    suspend fun setTransactionAllowedApp(packageName: String, allowed: Boolean) {
        dataStore.edit { preferences ->
            val current = if (preferences[TRANSACTION_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[TRANSACTION_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_TRANSACTION_APPS
            }
            val updated = current.toMutableSet()
            if (allowed) updated.add(packageName) else updated.remove(packageName)
            preferences[TRANSACTION_ALLOWED_APPS] = updated
            preferences[TRANSACTION_ALLOWED_APPS_INITIALIZED] = true
        }
    }

    /**
     * Explicit setter: sets whether a package is allowed to surface delivery alerts on the Island.
     * Use when the target boolean state is known explicitly. For interactive UI switches, prefer [toggleDeliveryAllowedApp].
     */
    suspend fun setDeliveryAllowedApp(packageName: String, allowed: Boolean) {
        dataStore.edit { preferences ->
            val current = if (preferences[DELIVERY_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[DELIVERY_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_DELIVERY_APPS
            }
            val updated = current.toMutableSet()
            if (allowed) updated.add(packageName) else updated.remove(packageName)
            preferences[DELIVERY_ALLOWED_APPS] = updated
            preferences[DELIVERY_ALLOWED_APPS_INITIALIZED] = true
        }
    }

    /**
     * Explicit setter: sets whether a package is allowed to surface meeting alerts on the Island.
     * Use when the target boolean state is known explicitly. For interactive UI switches, prefer [toggleMeetingAllowedApp].
     */
    suspend fun setMeetingAllowedApp(packageName: String, allowed: Boolean) {
        dataStore.edit { preferences ->
            val current = if (preferences[MEETING_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[MEETING_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_MEETING_APPS
            }
            val updated = current.toMutableSet()
            if (allowed) updated.add(packageName) else updated.remove(packageName)
            preferences[MEETING_ALLOWED_APPS] = updated
            preferences[MEETING_ALLOWED_APPS_INITIALIZED] = true
        }
    }

    /**
     * Atomic toggle: flips the inclusion state of a package in the transaction allowlist.
     * Reads and mutates the current allowlist atomically inside a single DataStore transaction.
     */
    suspend fun toggleTransactionAllowedApp(packageName: String) {
        dataStore.edit { preferences ->
            val current = if (preferences[TRANSACTION_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[TRANSACTION_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_TRANSACTION_APPS
            }
            val updated = current.toMutableSet()
            if (updated.contains(packageName)) updated.remove(packageName) else updated.add(packageName)
            preferences[TRANSACTION_ALLOWED_APPS] = updated
            preferences[TRANSACTION_ALLOWED_APPS_INITIALIZED] = true
        }
    }

    suspend fun toggleDeliveryAllowedApp(packageName: String) {
        dataStore.edit { preferences ->
            val current = if (preferences[DELIVERY_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[DELIVERY_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_DELIVERY_APPS
            }
            val updated = current.toMutableSet()
            if (updated.contains(packageName)) updated.remove(packageName) else updated.add(packageName)
            preferences[DELIVERY_ALLOWED_APPS] = updated
            preferences[DELIVERY_ALLOWED_APPS_INITIALIZED] = true
        }
    }

    suspend fun toggleMeetingAllowedApp(packageName: String) {
        dataStore.edit { preferences ->
            val current = if (preferences[MEETING_ALLOWED_APPS_INITIALIZED] == true) {
                preferences[MEETING_ALLOWED_APPS] ?: emptySet()
            } else {
                IslandTrustedApps.DEFAULT_MEETING_APPS
            }
            val updated = current.toMutableSet()
            if (updated.contains(packageName)) updated.remove(packageName) else updated.add(packageName)
            preferences[MEETING_ALLOWED_APPS] = updated
            preferences[MEETING_ALLOWED_APPS_INITIALIZED] = true
        }
    }

    /**
     * Restores a per-category allowlist to the curated defaults.
     */
    suspend fun resetTransactionAllowedApps() {
        dataStore.edit { preferences ->
            preferences.remove(TRANSACTION_ALLOWED_APPS)
            preferences.remove(TRANSACTION_ALLOWED_APPS_INITIALIZED)
        }
    }

    suspend fun resetDeliveryAllowedApps() {
        dataStore.edit { preferences ->
            preferences.remove(DELIVERY_ALLOWED_APPS)
            preferences.remove(DELIVERY_ALLOWED_APPS_INITIALIZED)
        }
    }

    suspend fun resetMeetingAllowedApps() {
        dataStore.edit { preferences ->
            preferences.remove(MEETING_ALLOWED_APPS)
            preferences.remove(MEETING_ALLOWED_APPS_INITIALIZED)
        }
    }

    /**
     * Serializes all current settings to a JSON String.
     */
    suspend fun exportSettingsToJson(): String {
        val json = JSONObject()
        val current = dataStore.data.first()

        current.asMap().forEach { (key, value) ->
            when {
                value is Set<*> -> {
                    val jsonArray = org.json.JSONArray()
                    value.forEach { item -> if (item != null) jsonArray.put(item.toString()) }
                    json.put(key.name, jsonArray)
                }
                else -> json.put(key.name, value)
            }
        }

        return json.toString()
    }

    /**
     * Restores settings from a backup JSON String.
     */
    suspend fun importSettingsFromJson(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            dataStore.edit { prefs ->
                json.keys().forEach { key ->
                    val value = json.get(key)
                    if (value == null || value == JSONObject.NULL) return@forEach

                    when {
                        // 0. Purged legacy keys (Quick Contacts feature removed): ignore to keep DataStore clean
                        key == "quick_contacts_json" || key == "quick_contacts" || key == "contacts_enabled" -> {
                            // No-op: ignore legacy quick contacts
                        }

                        // 1. Explicit Home Apps Order handler
                        key == HOME_APPS_ORDER.name -> {
                            val orderStr = when (value) {
                                is org.json.JSONArray -> {
                                    val list = mutableListOf<String>()
                                    for (i in 0 until value.length()) list.add(value.getString(i))
                                    list.joinToString(",")
                                }
                                is String -> {
                                    if (value.startsWith("[") && value.endsWith("]")) {
                                        value.substring(1, value.length - 1)
                                            .split(",")
                                            .map { it.trim().removeSurrounding("\"") }
                                            .filter { it.isNotEmpty() }
                                            .joinToString(",")
                                    } else {
                                        value
                                    }
                                }
                                else -> value.toString()
                            }
                            prefs[HOME_APPS_ORDER] = orderStr
                        }

                        // 4. Explicit App Limits JSON handler
                        key == APP_LIMITS_JSON.name -> {
                            prefs[APP_LIMITS_JSON] = value.toString()
                        }

                        // 5. Known String Keys
                        key in KNOWN_STRING_KEYS -> {
                            prefs[stringPreferencesKey(key)] = value.toString()
                        }

                        // 6. Known Boolean Keys
                        key in KNOWN_BOOLEAN_KEYS -> {
                            prefs[booleanPreferencesKey(key)] = when (value) {
                                is Boolean -> value
                                else -> value.toString().toBoolean()
                            }
                        }

                        // 7. Known StringSet Keys
                        key in KNOWN_STRING_SET_KEYS -> {
                            val set = mutableSetOf<String>()
                            when (value) {
                                is org.json.JSONArray -> {
                                    for (i in 0 until value.length()) set.add(value.getString(i))
                                }
                                is String -> {
                                    if (value.startsWith("[") && value.endsWith("]")) {
                                        val parsedAsJsonArray = try {
                                            val jsonArr = org.json.JSONArray(value)
                                            for (i in 0 until jsonArr.length()) set.add(jsonArr.getString(i))
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                        if (!parsedAsJsonArray) {
                                            val items = value.substring(1, value.length - 1)
                                                .split(",")
                                                .map { it.trim().removeSurrounding("\"") }
                                                .filter { it.isNotEmpty() }
                                            set.addAll(items)
                                        }
                                    } else {
                                        set.add(value)
                                    }
                                }
                                else -> set.add(value.toString())
                            }
                            prefs[stringSetPreferencesKey(key)] = set
                        }

                        // 8. Known Int Keys
                        key in KNOWN_INT_KEYS -> {
                            val intVal = when (value) {
                                is Number -> value.toInt()
                                else -> value.toString().toIntOrNull() ?: 0
                            }
                            prefs[intPreferencesKey(key)] = intVal
                        }

                        // 9. Known Long Keys
                        key in KNOWN_LONG_KEYS -> {
                            val longVal = when (value) {
                                is Number -> value.toLong()
                                else -> value.toString().toLongOrNull() ?: 0L
                            }
                            prefs[longPreferencesKey(key)] = longVal
                        }

                        // 10. Known Float Keys
                        key in KNOWN_FLOAT_KEYS -> {
                            val floatVal = when (value) {
                                is Number -> value.toFloat()
                                else -> value.toString().toFloatOrNull() ?: 1.0f
                            }
                            prefs[floatPreferencesKey(key)] = floatVal
                        }

                        // 11. Generic fallback for unknown future keys
                        else -> {
                            when (value) {
                                is Boolean -> prefs[booleanPreferencesKey(key)] = value
                                is Int -> prefs[intPreferencesKey(key)] = value
                                is Long -> prefs[longPreferencesKey(key)] = value
                                is Double -> prefs[floatPreferencesKey(key)] = value.toFloat()
                                is Float -> prefs[floatPreferencesKey(key)] = value
                                is org.json.JSONArray -> {
                                    val set = mutableSetOf<String>()
                                    for (i in 0 until value.length()) set.add(value.getString(i))
                                    prefs[stringSetPreferencesKey(key)] = set
                                }
                                is JSONObject -> prefs[stringPreferencesKey(key)] = value.toString()
                                is String -> prefs[stringPreferencesKey(key)] = value
                            }
                        }
                    }
                }

                if (json.has("has_completed_walkthrough") && !json.optBoolean("has_completed_walkthrough", false)) {
                    prefs[WALKTHROUGH_STEP] = 0
                }
            }
            if (json.has("has_seen_permission_disclosure") || json.has("has_completed_walkthrough") || json.has("notification_filtering_enabled")) {
                val editor = context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).edit()
                if (json.has("has_seen_permission_disclosure")) {
                    editor.putBoolean(KEY_SEEN_DISCLOSURE, json.optBoolean("has_seen_permission_disclosure", false))
                }
                if (json.has("has_completed_walkthrough")) {
                    editor.putBoolean(KEY_COMPLETED_WALKTHROUGH, json.optBoolean("has_completed_walkthrough", false))
                }
                if (json.has("notification_filtering_enabled")) {
                    editor.putBoolean(KEY_NOTIFICATION_FILTERING_ENABLED, json.optBoolean("notification_filtering_enabled", false))
                }
                editor.apply()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @androidx.annotation.VisibleForTesting
    @android.annotation.SuppressLint("ApplySharedPref")
    suspend fun clearAllForTesting() {
        dataStore.edit { it.clear() }
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
}

fun parseTimeToMinutes(time: String): Int? {
    val idx = time.indexOf(':')
    if (idx == -1) return null
    val hStr = time.substring(0, idx)
    val mStr = time.substring(idx + 1)
    if (mStr.length != 2) return null
    val h = hStr.toIntOrNull() ?: return null
    val m = mStr.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

fun SettingsState.isFocusModeActiveNow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
    if (!isFocusModeActive) return false
    return focusEndTime <= 0L || currentTimeMs < focusEndTime
}

fun SettingsState.isQuietHoursActiveNow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
    if (!quietHoursEnabled) return false
    return try {
        val startMin = parseTimeToMinutes(quietHoursStart)
        val endMin = parseTimeToMinutes(quietHoursEnd)
        if (startMin != null && endMin != null && startMin != endMin) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMs }
            val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            if (startMin < endMin) {
                nowMin in startMin until endMin
            } else {
                nowMin >= startMin || nowMin < endMin
            }
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}

fun SettingsState.getQuietHoursMinutesLeft(currentTimeMs: Long = System.currentTimeMillis()): Int {
    if (!isQuietHoursActiveNow(currentTimeMs)) return 0
    val startMin = parseTimeToMinutes(quietHoursStart) ?: return 0
    val endMin = parseTimeToMinutes(quietHoursEnd) ?: return 0
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMs }
    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    return if (startMin < endMin) {
        (endMin - nowMin).coerceAtLeast(0)
    } else {
        if (nowMin >= startMin) (1440 - nowMin) + endMin else (endMin - nowMin).coerceAtLeast(0)
    }
}

fun SettingsState.getQuietHoursMinutesSinceStart(currentTimeMs: Long = System.currentTimeMillis()): Int {
    if (!isQuietHoursActiveNow(currentTimeMs)) return 0
    val startMin = parseTimeToMinutes(quietHoursStart) ?: return 0
    val endMin = parseTimeToMinutes(quietHoursEnd) ?: return 0
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMs }
    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    return if (startMin < endMin) {
        (nowMin - startMin).coerceAtLeast(0)
    } else {
        if (nowMin >= startMin) nowMin - startMin else (1440 - startMin) + nowMin
    }
}

fun SettingsState.getQuietHoursMinutesUntilStart(currentTimeMs: Long = System.currentTimeMillis()): Int {
    if (!quietHoursEnabled || isQuietHoursActiveNow(currentTimeMs)) return 0
    val startMin = parseTimeToMinutes(quietHoursStart) ?: return 0
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMs }
    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    return if (nowMin < startMin) {
        startMin - nowMin
    } else {
        (1440 - nowMin) + startMin
    }
}

