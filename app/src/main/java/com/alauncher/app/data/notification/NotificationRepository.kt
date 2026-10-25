package com.alauncher.app.data.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import com.alauncher.app.domain.notification.NotificationFilterEngine
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.model.displayBodyPreview
import com.alauncher.app.model.displayTitlePreview
import com.alauncher.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class NotificationRepository(
    private val filterEngine: NotificationFilterEngine,
    private val settingsRepository: SettingsRepository?,
    private val externalScope: CoroutineScope
) {
    private val mapLock = Any()

    // Thread-safe map of active, intercepted notifications from status bar
    private val activeNotificationsMap = ConcurrentHashMap<String, LauncherNotification>()
    private val _notificationsFlow = MutableStateFlow<List<LauncherNotification>>(emptyList())

    // Thread-safe map of associated PendingIntents for in-memory launching
    private val pendingIntentMap = ConcurrentHashMap<String, PendingIntent>()

    init {
        // Expose a combined state flow that automatically re-evaluates classifications
        // whenever active notifications, custom rules, focus modes, or quiet hours change!
        _notificationsFlow.value = activeNotificationsMap.values.toList()
    }

    /**
     * Publishes a list update from the concurrent map
     */
    private fun publishNotifications() {
        _notificationsFlow.value = activeNotificationsMap.values.toList()
    }

    private val repoTickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(60000)
        }
    }

    // Re-evaluates classification whenever settings or the 60s ticker change.
    // CONTRACT: notifications in activeNotificationsMap are pre-enriched with memoized signals
    // by postNotification() → enrichWithMemoizedSignals(). classify() has a cold-path fallback
    // (via ?: run { ... }) for any un-enriched notifications, so this is safe even if a
    // notification enters the map without enrichment.
    val resolvedNotificationsFlow: Flow<List<LauncherNotification>> = combine(
        _notificationsFlow,
        settingsRepository?.settingsFlow ?: flowOf(com.alauncher.app.data.settings.SettingsState()),
        repoTickerFlow
    ) { activeNodes, settings, tickerMs ->
        activeNodes.map { notif ->
            val computedCategory = filterEngine.classify(notif, settings, currentTimeMs = tickerMs)
            notif.copy(filterCategory = computedCategory)
        }
    }

    val priorityNotificationsFlow: Flow<List<LauncherNotification>> = resolvedNotificationsFlow.map { list ->
        list.filter { it.filterCategory == FilterCategory.PRIORITY }.sortedByDescending { it.postTime }
    }

    val normalNotificationsFlow: Flow<List<LauncherNotification>> = resolvedNotificationsFlow.map { list ->
        list.filter { it.filterCategory == FilterCategory.NORMAL }.sortedByDescending { it.postTime }
    }

    val mutedNotificationsFlow: Flow<List<LauncherNotification>> = resolvedNotificationsFlow.map { list ->
        list.filter { it.filterCategory == FilterCategory.MUTED }.sortedByDescending { it.postTime }
    }

    /**
     * Handles incoming notification postings from the intercepting listener service.
     */
    suspend fun postNotification(
        key: String,
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        postTime: Long,
        channelId: String?,
        isOngoing: Boolean,
        isClearable: Boolean,
        importance: Int,
        pendingIntent: PendingIntent?,
        progress: Int = -1,
        progressMax: Int = -1,
        progressIndeterminate: Boolean = false,
        showChronometer: Boolean = false,
        chronometerCountDown: Boolean = false,
        whenValue: Long = 0L,
        bigText: String? = null,
        subText: String? = null,
        infoText: String? = null,
        notificationCategory: String? = null,
        actionCodes: List<String> = emptyList()
    ): FilterCategory {
        val originalNotification = LauncherNotification(
            key = key,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            postTime = postTime,
            channelId = channelId,
            isOngoing = isOngoing,
            isClearable = isClearable,
            importance = importance,
            hasPendingIntent = pendingIntent != null,
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate = progressIndeterminate,
            showChronometer = showChronometer,
            chronometerCountDown = chronometerCountDown,
            whenValue = whenValue,
            bigText = bigText,
            subText = subText,
            infoText = infoText,
            notificationCategory = notificationCategory,
            actionCodes = actionCodes
        )

        // Run classification to determine filter categories
        val settings = settingsRepository?.settingsFlow?.firstOrNull() ?: com.alauncher.app.data.settings.SettingsState()
        val memoizedNotification = filterEngine.enrichWithMemoizedSignals(originalNotification)
        val cat = filterEngine.classify(
            memoizedNotification,
            settings
        )

        synchronized(mapLock) {
            var resolvedPendingIntent = pendingIntent ?: pendingIntentMap[key]
            val newTitle = memoizedNotification.displayTitlePreview
            val newBody = memoizedNotification.displayBodyPreview

            // Deduplicate identical notifications from the same package posted under different keys
            // (e.g. group summary duplicates or two-phase rich push updates where the initial
            // notification was already cancelled from SystemUI via REASON_LISTENER_CANCEL).
            if (newTitle != null && progress < 0 && !progressIndeterminate) {
                val duplicateKeys = activeNotificationsMap.values
                    .filter { existing ->
                        existing.key != key &&
                        existing.packageName == packageName &&
                        existing.displayTitlePreview == newTitle &&
                        existing.displayBodyPreview == newBody
                    }
                    .map { it.key }
                for (dupKey in duplicateKeys) {
                    activeNotificationsMap.remove(dupKey)
                    val oldPi = pendingIntentMap.remove(dupKey)
                    if (resolvedPendingIntent == null && oldPi != null) {
                        resolvedPendingIntent = oldPi
                    }
                }
            }

            if (resolvedPendingIntent != null) {
                pendingIntentMap[key] = resolvedPendingIntent
            }

            // Cache classified notification with memoized static signals
            activeNotificationsMap[key] = memoizedNotification.copy(
                filterCategory = cat,
                hasPendingIntent = resolvedPendingIntent != null
            )
            trimStaleNotificationsLocked()
            publishNotifications()
        }

        return cat
    }

    private var lastTrimTimeMs = 0L

    private fun trimStaleNotificationsLocked() {
        val now = System.currentTimeMillis()
        val shouldCheckTtl = (now - lastTrimTimeMs) > 5 * 60 * 1000L
        val overflow = activeNotificationsMap.size - MAX_ACTIVE_NOTIFICATIONS

        // Fast-path: return immediately if within capacity and TTL check is throttled
        if (!shouldCheckTtl && overflow <= 0) return

        if (shouldCheckTtl) {
            lastTrimTimeMs = now
            val expiredKeys = activeNotificationsMap.values
                .filter { !it.isOngoing && it.postTime >= 1_000_000_000_000L && (now - it.postTime > MAX_NOTIFICATION_AGE_MS) }
                .map { it.key }
            for (k in expiredKeys) {
                activeNotificationsMap.remove(k)
                pendingIntentMap.remove(k)
            }
        }

        val currentOverflow = activeNotificationsMap.size - MAX_ACTIVE_NOTIFICATIONS
        if (currentOverflow > 0) {
            val oldestKeys = activeNotificationsMap.values
                .filter { !it.isOngoing }
                .sortedBy { it.postTime }
                .take(currentOverflow)
                .map { it.key }
            for (k in oldestKeys) {
                activeNotificationsMap.remove(k)
                pendingIntentMap.remove(k)
            }
        }
    }


    /**
     * Removes a notification when dismissed by system or user.
     */
    fun removeNotification(key: String) {
        synchronized(mapLock) {
            activeNotificationsMap.remove(key)
            pendingIntentMap.remove(key)
            publishNotifications()
        }
    }

    /**
     * Batch removes multiple notifications when dismissed together (e.g. Clear All)
     * and publishes a single combined update to avoid performance stutter.
     */
    fun removeNotifications(keys: Collection<String>) {
        if (keys.isEmpty()) return
        synchronized(mapLock) {
            for (key in keys) {
                activeNotificationsMap.remove(key)
                pendingIntentMap.remove(key)
            }
            publishNotifications()
        }
    }

    /**
     * Clear all cached active notifications.
     */
    fun clearAllActive() {
        synchronized(mapLock) {
            activeNotificationsMap.clear()
            pendingIntentMap.clear()
            publishNotifications()
        }
    }

    /**
     * Clears all active notifications and pending intents belonging to an uninstalled package.
     */
    fun removeNotificationsForPackage(packageName: String) {
        synchronized(mapLock) {
            val keysToRemove = activeNotificationsMap.filter { it.value.packageName == packageName }.keys
            if (keysToRemove.isNotEmpty()) {
                for (key in keysToRemove) {
                    activeNotificationsMap.remove(key)
                    pendingIntentMap.remove(key)
                }
                publishNotifications()
            }
        }
    }

    /**
     * Returns a thread-safe snapshot of keys currently in [activeNotificationsMap].
     */
    fun snapshotActiveKeys(): Set<String> = synchronized(mapLock) {
        activeNotificationsMap.keys.toSet()
    }

    /**
     * Reconciles repository state with the system's current active notifications snapshot on listener reconnect.
     *
     * IMPORTANT: When ALauncher is the default launcher, clearable non-ongoing NORMAL/MUTED notifications are
     * intentionally cancelled from the system status bar (REASON_LISTENER_CANCEL) so they live exclusively in
     * ALauncher's Notification Center. Those intercepted notifications are expected to be absent from
     * [activeSystemKeys] and MUST be preserved. Only notifications that remained resident in the system status
     * bar (PRIORITY, ongoing, non-clearable, or all notifications when not default launcher) are pruned if they
     * are no longer present in [activeSystemKeys].
     *
     * @param candidateKeys Optional pre-resync key snapshot so notifications arriving concurrently during the
     *                      resync window are never pruned.
     */
    fun reconcileStatusBarNotifications(
        activeSystemKeys: Set<String>,
        isDefaultLauncher: Boolean,
        candidateKeys: Set<String>? = null
    ) {
        synchronized(mapLock) {
            val staleKeys = activeNotificationsMap.values
                .filter { existing ->
                    val isEligibleCandidate = candidateKeys == null || existing.key in candidateKeys
                    val isStatusBarResident = !isDefaultLauncher ||
                        existing.filterCategory == FilterCategory.PRIORITY ||
                        existing.isOngoing ||
                        !existing.isClearable
                    isEligibleCandidate && isStatusBarResident && existing.key !in activeSystemKeys
                }
                .map { it.key }

            if (staleKeys.isNotEmpty()) {
                for (key in staleKeys) {
                    activeNotificationsMap.remove(key)
                    pendingIntentMap.remove(key)
                }
                publishNotifications()
            }
        }
    }

    @Suppress("DEPRECATION")
    fun launchNotification(key: String, context: Context): Boolean {
        val (pi, notif) = synchronized(mapLock) {
            pendingIntentMap[key] to activeNotificationsMap[key]
        }
        var launched = false

        if (pi != null) {
            try {
                // Configure ActivityOptions to allow background activity starts on Android 14+ (API 34+)
                val options = android.app.ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    options.setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pi.send(context, 0, null, null, null, null, options.toBundle())
                launched = true
            } catch (e: Exception) {
                Log.e("NotificationRepository", "Canceled or stale PendingIntent captured, using fallback launch", e)
            }
        }
        
        // Fallback: Launch app's primary launcher intent by packageName
        if (!launched && notif != null) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(notif.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    launched = true
                }
            } catch (e: Exception) {
                Log.e("NotificationRepository", "Failed fallback launch for package: ${notif.packageName}", e)
            }
        }

        if (launched) {
            // Dismiss the clicked notification from the launcher list
            removeNotification(key)
            return true
        }
        return false
    }

    companion object {
        const val MAX_ACTIVE_NOTIFICATIONS = 100
        const val MAX_NOTIFICATION_AGE_MS = 48 * 60 * 60 * 1000L // 48 hours
    }
}
