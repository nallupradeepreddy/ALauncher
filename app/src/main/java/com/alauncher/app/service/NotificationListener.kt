package com.alauncher.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.alauncher.app.MinimalSmartLauncherApp
import com.alauncher.app.ui.utils.LauncherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationListener : NotificationListenerService() {
 
    // Removed isDefaultLauncher duplication - now using LauncherUtils.isDefaultLauncher

    private val app: MinimalSmartLauncherApp?
        get() = applicationContext as? MinimalSmartLauncherApp

    companion object {
        @Volatile
        var activeInstance: NotificationListener? = null
            private set

        const val DEFAULT_REBIND_DEBOUNCE_MS = 30_000L

        private var lastRebindAttemptTimeMs = 0L

        // Thread-safe in-memory cache for app names to avoid repeated heavy system IPC queries
        private val appNameCache = LruCache<String, String>(100)

        fun cancelNotification(key: String) {
            try {
                activeInstance?.cancelNotification(key)
            } catch (e: Exception) {
                Log.e("NotificationListener", "Failed to cancel notification: $key", e)
            }
        }

        /**
         * Requests the Android system to rebind the NotificationListenerService if access is granted
         * but the service instance is currently dead/unbound (common on aggressive OEM task killers like
         * Xiaomi HyperOS, Vivo FuntouchOS, Oppo ColorOS).
         *
         * Throttled by [minIntervalMs] (default 30s) to prevent system IPC thrashing.
         */
        @Synchronized
        fun requestRebindIfDisconnected(
            context: Context,
            minIntervalMs: Long = DEFAULT_REBIND_DEBOUNCE_MS,
            currentTimeMs: Long = System.currentTimeMillis(),
            isAccessGranted: (Context) -> Boolean = { com.alauncher.app.ui.utils.isNotificationAccessGranted(it) },
            rebindAction: (ComponentName) -> Unit = { requestRebind(it) }
        ): Boolean {
            // Guard 1: If listener is already connected, no rebind needed
            if (activeInstance != null) {
                Log.d("NotificationListener", "Rebind skipped: listener already connected")
                return false
            }

            // Guard 2: If user hasn't granted notification listener access in system settings, abort to avoid SecurityException
            if (!isAccessGranted(context)) {
                Log.d("NotificationListener", "Rebind skipped: notification access not granted in system settings")
                return false
            }

            // Guard 3: Rate limiting debounce window
            if (lastRebindAttemptTimeMs != 0L && (currentTimeMs - lastRebindAttemptTimeMs < minIntervalMs)) {
                Log.d("NotificationListener", "Rebind skipped: within ${minIntervalMs}ms debounce window")
                return false
            }

            return try {
                val componentName = ComponentName(context, NotificationListener::class.java)
                require(context.packageName == componentName.packageName) {
                    "requestRebind must only be called for the app's own listener"
                }
                rebindAction(componentName)
                lastRebindAttemptTimeMs = currentTimeMs
                Log.i("NotificationListener", "Requested NotificationListenerService rebind from system.")
                true
            } catch (e: Exception) {
                Log.e("NotificationListener", "Failed to request NotificationListenerService rebind", e)
                false
            }
        }

        @Volatile
        var cachedUnfilteredApps: Set<String> = emptySet()
            internal set

        @Volatile
        var cachedMeetingAllowedApps: Set<String> = com.alauncher.app.domain.notification.IslandTrustedApps.DEFAULT_MEETING_APPS
            internal set

        @Volatile
        var cachedIsDefaultLauncher: Boolean = false
            internal set

        @Volatile
        var cachedNotificationFilteringEnabled: Boolean = false
            internal set

        private val parser = com.alauncher.app.domain.notification.NotificationParser()

        @VisibleForTesting
        internal fun isFastPathCancelCandidate(
            packageName: String,
            title: String?,
            text: String?,
            category: String?,
            isClearable: Boolean,
            isOngoing: Boolean,
            unfilteredApps: Set<String>,
            meetingAllowedApps: Set<String> = cachedMeetingAllowedApps,
            isDefaultLauncher: Boolean,
            notificationFilteringEnabled: Boolean = cachedNotificationFilteringEnabled
        ): Boolean {
            if (!isDefaultLauncher) return false
            if (!notificationFilteringEnabled) return false
            if (!isClearable || isOngoing) return false
            if (unfilteredApps.contains(packageName)) return false

            // Critical system categories should bypass early cancellation
            if (category == Notification.CATEGORY_CALL ||
                category == Notification.CATEGORY_ALARM) {
                return false
            }
            if (category == Notification.CATEGORY_EVENT &&
                (meetingAllowedApps.contains(packageName) ||
                 com.alauncher.app.domain.notification.NotificationConstants.CALENDAR_PKG_REGEX.containsMatchIn(packageName))) {
                return false
            }

            // Check for authentic OTP using fast static heuristics
            if (parser.extractOtp(title, text) != null) {
                return false
            }

            val titleStr = title ?: ""
            val textStr = text ?: ""
            val combinedText = if (titleStr.isNotBlank() && textStr.isNotBlank()) "$titleStr $textStr" else "$titleStr$textStr"

            // Severe weather alert
            if (com.alauncher.app.domain.notification.NotificationParser.severeWeatherAlertRegex.containsMatchIn(combinedText)) {
                return false
            }

            // Meeting & Calendar protection:
            // Bypass early cancellation if:
            // 1. Package is in user-approved meetingAllowedApps (e.g. Zoom, Teams, Meet)
            // 2. Package matches known calendar/meeting apps (CALENDAR_PKG_REGEX)
            // 3. Notification contains a video meeting link (MEETING_LINK_REGEX)
            // 4. Notification matches meeting title and schedule keywords (e.g. "Standup starting in 5 mins")
            if (meetingAllowedApps.contains(packageName) ||
                com.alauncher.app.domain.notification.NotificationConstants.CALENDAR_PKG_REGEX.containsMatchIn(packageName) ||
                com.alauncher.app.domain.notification.NotificationConstants.MEETING_LINK_REGEX.containsMatchIn(combinedText) ||
                (com.alauncher.app.domain.notification.NotificationConstants.MEETING_KEYWORDS_REGEX.containsMatchIn(combinedText) &&
                 com.alauncher.app.domain.notification.NotificationConstants.SCHEDULE_KEYWORDS_REGEX.containsMatchIn(combinedText))
            ) {
                return false
            }

            return true
        }

        @VisibleForTesting
        internal fun shouldCancelNotification(
            cat: com.alauncher.app.model.FilterCategory,
            isClearable: Boolean,
            isOngoing: Boolean,
            isDefaultLauncher: Boolean,
            notificationFilteringEnabled: Boolean = cachedNotificationFilteringEnabled
        ): Boolean {
            if (!isDefaultLauncher) return false
            return isClearable && !isOngoing && when {
                cat == com.alauncher.app.model.FilterCategory.PRIORITY -> false
                cat == com.alauncher.app.model.FilterCategory.MUTED -> true
                else -> notificationFilteringEnabled
            }
        }

        @VisibleForTesting
        internal fun setCachedUnfilteredAppsForTesting(apps: Set<String>) {
            cachedUnfilteredApps = apps
        }

        @VisibleForTesting
        internal fun setCachedNotificationFilteringEnabledForTesting(enabled: Boolean) {
            cachedNotificationFilteringEnabled = enabled
        }

        @VisibleForTesting
        internal fun resetRebindCooldownForTesting() {
            lastRebindAttemptTimeMs = 0L
        }

        @VisibleForTesting
        internal fun setActiveInstanceForTesting(instance: NotificationListener?) {
            activeInstance = instance
        }
    }

    private var settingsCollectorJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var lastDefaultLauncherCheckMs = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        cachedNotificationFilteringEnabled = com.alauncher.app.data.settings.SettingsRepository.getNotificationFilteringEnabledFast(this)
        Log.d("NotificationListener", "Listener connected. Synchronizing active system notifications.")
        val isDefault = LauncherUtils.isDefaultLauncher(this)
        cachedIsDefaultLauncher = isDefault
        loadExistingNotifications(isDefault)

        // Observe SettingsState to keep cached allowlists in sync for zero-latency cancellation
        settingsCollectorJob?.cancel()
        val appRef = app
        if (appRef != null) {
            settingsCollectorJob = appRef.applicationScope.launch(Dispatchers.Default) {
                appRef.settingsRepository.settingsFlow.collect { settings ->
                    cachedUnfilteredApps = settings.unfilteredApps
                    cachedMeetingAllowedApps = settings.meetingAllowedApps
                    cachedNotificationFilteringEnabled = settings.notificationFilteringEnabled
                }
            }
        }

        // If not yet recognized as default (early boot), retry after 3s to catch the post-boot window
        if (!isDefault) {
            app?.applicationScope?.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(3000)
                val refreshedDefault = LauncherUtils.isDefaultLauncher(this@NotificationListener)
                cachedIsDefaultLauncher = refreshedDefault
                if (refreshedDefault) {
                    loadExistingNotifications(isDefault = true)
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        settingsCollectorJob?.cancel()
        settingsCollectorJob = null
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        settingsCollectorJob?.cancel()
        settingsCollectorJob = null
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        
        val notification = sbn.notification ?: return
        val packageName = sbn.packageName ?: ""
        if (packageName.isBlank()) return
        
        // Extract textual payloads safely on caller/binder thread to avoid multi-thread Bundle parsing issues
        val (title, text) = extractNotificationTitleAndText(notification)
        
        val channelId = notification.channelId
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 || sbn.isOngoing
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val isClearable = sbn.isClearable
        val importance = notification.priority // Backup fallback
        
        val pendingIntent = notification.contentIntent
        val details = parseNotificationDetails(notification)
        val key = sbn.key
        val postTime = sbn.postTime

        // Time-gated check for default launcher state to avoid unnecessary IPC per notification (10s TTL)
        val now = System.currentTimeMillis()
        val isDefault = if (now - lastDefaultLauncherCheckMs > 10_000L) {
            lastDefaultLauncherCheckMs = now
            val check = LauncherUtils.isDefaultLauncher(this)
            cachedIsDefaultLauncher = check
            check
        } else {
            cachedIsDefaultLauncher
        }

        // Fast-path early cancellation: cancel on the binder thread immediately to suppress
        // heads-up floating banners before SystemUI can render them on screen.
        val isFastCancelled = if (isFastPathCancelCandidate(
                packageName = packageName,
                title = title,
                text = text,
                category = notification.category,
                isClearable = isClearable,
                isOngoing = isOngoing,
                unfilteredApps = cachedUnfilteredApps,
                meetingAllowedApps = cachedMeetingAllowedApps,
                isDefaultLauncher = isDefault,
                notificationFilteringEnabled = cachedNotificationFilteringEnabled
            )
        ) {
            try {
                cancelNotification(key)
                true
            } catch (ex: Exception) {
                Log.e("NotificationListener", "Fast-path cancellation failed: $key", ex)
                false
            }
        } else {
            false
        }

        // Do not ingest synthetic group summary containers into NotificationRepository.
        // Android 7.0+ requires apps to post both a child notification and a FLAG_GROUP_SUMMARY
        // container (often with identical title/text), while NotificationCenterScreen already
        // groups notifications by packageName.
        if (isGroupSummary) {
            val isProtectedApp = cachedUnfilteredApps.contains(packageName) ||
                cachedMeetingAllowedApps.contains(packageName) ||
                com.alauncher.app.domain.notification.NotificationConstants.CALENDAR_PKG_REGEX.containsMatchIn(packageName)
            val shouldCancelSummary = !isProtectedApp && shouldCancelNotification(
                cat = com.alauncher.app.model.FilterCategory.NORMAL,
                isClearable = isClearable,
                isOngoing = isOngoing,
                isDefaultLauncher = isDefault,
                notificationFilteringEnabled = cachedNotificationFilteringEnabled
            )
            if (shouldCancelSummary) {
                app?.applicationScope?.launch(Dispatchers.Main) {
                    try {
                        cancelNotification(key)
                    } catch (ex: Exception) {
                        Log.e("NotificationListener", "Failed to cancel group summary: $key", ex)
                    }
                }
            }
            return
        }

        val app = app
        app?.applicationScope?.launch(Dispatchers.IO) {
            try {
                val repo = app.notificationRepository ?: return@launch

                val appName = synchronized(appNameCache) {
                    appNameCache.get(packageName)
                } ?: try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    synchronized(appNameCache) {
                        appNameCache.put(packageName, label)
                    }
                    label
                } catch (e: Exception) {
                    packageName
                }

                try {
                    val cat = repo.postNotification(
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
                        pendingIntent = pendingIntent,
                        progress = details.progress,
                        progressMax = details.progressMax,
                        progressIndeterminate = details.progressIndeterminate,
                        showChronometer = details.showChronometer,
                        chronometerCountDown = details.chronometerCountDown,
                        whenValue = details.whenValue,
                        bigText = details.bigText,
                        subText = details.subText,
                        infoText = details.infoText,
                        notificationCategory = details.notificationCategory,
                        actionCodes = details.actionCodes
                    )
                    
                    val shouldCancel = shouldCancelNotification(
                        cat = cat,
                        isClearable = isClearable,
                        isOngoing = isOngoing,
                        isDefaultLauncher = isDefault,
                        notificationFilteringEnabled = cachedNotificationFilteringEnabled
                    )
                    if (shouldCancel) {
                        withContext(Dispatchers.Main) {
                            try {
                                cancelNotification(key)
                            } catch (ex: Exception) {
                                Log.e("NotificationListener", "Could not split/cancel system notification: $key", ex)
                            }
                            cancelOrphanedGroupSummaries(packageName, justCancelledKey = key)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationListener", "Failed posting notification to repository: $key", e)
                }
            } catch (e: Exception) {
                Log.e("NotificationListener", "Fatal/Unexpected error in onNotificationPosted", e)
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: NotificationListenerService.RankingMap?,
        reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn == null) return

        // If the cancellation reason is REASON_LISTENER_CANCEL, this means the notification was
        // cancelled/hidden from the status bar by the launcher itself (us).
        // In this case, we do NOT want to remove it from our local database/UI since it should still
        // be available in the launcher's Notification Center.
        if (reason == REASON_LISTENER_CANCEL) {
            return
        }

        try {
            val repo = app?.notificationRepository ?: return
            app?.applicationScope?.launch(Dispatchers.IO) {
                try {
                    repo.removeNotification(sbn.key)
                } catch (e: Exception) {
                    Log.e("NotificationListener", "Failed removing notification from repository: ${sbn.key}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Fatal/Unexpected error in onNotificationRemoved", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun loadExistingNotifications(isDefault: Boolean) {
        val repo = app?.notificationRepository ?: return
        val preResyncKeys = repo.snapshotActiveKeys()
        
        val activeNotificationsArray = try {
            activeNotifications
        } catch (e: Exception) {
            Log.e("NotificationListener", "Failed to retrieve activeNotifications from system", e)
            null
        }
        if (activeNotificationsArray == null) return

        val summaryKeysToCancel = mutableListOf<String>()

        // Extract textual payloads safely on caller/binder thread to avoid multi-thread Bundle/Parcel parsing issues
        val safeNotifList = activeNotificationsArray.mapNotNull { sbn ->
            val pkg = sbn.packageName ?: return@mapNotNull null
            val notif = sbn.notification ?: return@mapNotNull null
            val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0 || sbn.isOngoing
            val isGroupSummary = (notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            
            val (title, text) = extractNotificationTitleAndText(notif)
            if (isGroupSummary) {
                if (isFastPathCancelCandidate(
                        packageName = pkg,
                        title = title,
                        text = text,
                        category = notif.category,
                        isClearable = sbn.isClearable,
                        isOngoing = isOngoing,
                        unfilteredApps = cachedUnfilteredApps,
                        meetingAllowedApps = cachedMeetingAllowedApps,
                        isDefaultLauncher = isDefault
                    )
                ) {
                    summaryKeysToCancel.add(sbn.key)
                }
                return@mapNotNull null
            }

            val details = parseNotificationDetails(notif)
            
            SafeNotificationData(
                key = sbn.key,
                packageName = pkg,
                title = title,
                text = text,
                postTime = sbn.postTime,
                channelId = notif.channelId,
                isOngoing = isOngoing,
                isClearable = sbn.isClearable,
                importance = notif.priority,
                pendingIntent = notif.contentIntent,
                details = details
            )
        }

        app?.applicationScope?.launch(Dispatchers.IO) {
            try {
                val keysToCancel = mutableListOf<String>()
                keysToCancel.addAll(summaryKeysToCancel)
                for (item in safeNotifList) {
                    try {
                        val appName = synchronized(appNameCache) {
                            appNameCache.get(item.packageName)
                        } ?: try {
                            val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
                            val label = packageManager.getApplicationLabel(appInfo).toString()
                            synchronized(appNameCache) {
                                appNameCache.put(item.packageName, label)
                            }
                            label
                        } catch (e: Exception) {
                            item.packageName
                        }

                        val cat = repo.postNotification(
                            key = item.key,
                            packageName = item.packageName,
                            appName = appName,
                            title = item.title,
                            text = item.text,
                            postTime = item.postTime,
                            channelId = item.channelId,
                            isOngoing = item.isOngoing,
                            isClearable = item.isClearable,
                            importance = item.importance,
                            pendingIntent = item.pendingIntent,
                            progress = item.details.progress,
                            progressMax = item.details.progressMax,
                            progressIndeterminate = item.details.progressIndeterminate,
                            showChronometer = item.details.showChronometer,
                            chronometerCountDown = item.details.chronometerCountDown,
                            whenValue = item.details.whenValue,
                            bigText = item.details.bigText,
                            subText = item.details.subText,
                            infoText = item.details.infoText,
                            notificationCategory = item.details.notificationCategory,
                            actionCodes = item.details.actionCodes
                        )

                        val shouldCancel = shouldCancelNotification(
                            cat = cat,
                            isClearable = item.isClearable,
                            isOngoing = item.isOngoing,
                            isDefaultLauncher = isDefault,
                            notificationFilteringEnabled = cachedNotificationFilteringEnabled
                        )
                        if (shouldCancel) {
                            keysToCancel.add(item.key)
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationListener", "Error processing pre-existing notification sbn", e)
                    }
                }
                val activeSystemKeys = safeNotifList.mapTo(HashSet(safeNotifList.size)) { it.key }
                repo.reconcileStatusBarNotifications(
                    activeSystemKeys = activeSystemKeys,
                    isDefaultLauncher = isDefault,
                    candidateKeys = preResyncKeys
                )
                if (keysToCancel.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        keysToCancel.forEach { key ->
                            try { cancelNotification(key) } catch (ex: Exception) {
                                Log.e("NotificationListener", "Could not cancel pre-existing notification: $key", ex)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationListener", "Failed to query active notifications stack during sync", e)
            }
        }
    }

    private fun cancelOrphanedGroupSummaries(packageName: String, justCancelledKey: String? = null) {
        try {
            val activeNotifs = activeNotifications ?: return
            val allPackageNotifs = activeNotifs.filter { it.packageName == packageName }
            val hasChildNotifs = allPackageNotifs.any {
                (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && it.key != justCancelledKey
            }
            if (!hasChildNotifs && allPackageNotifs.isNotEmpty()) {
                allPackageNotifs
                    .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 && it.isClearable && !it.isOngoing }
                    .forEach { sbn ->
                        try {
                            cancelNotification(sbn.key)
                        } catch (e: Exception) {
                            Log.e("NotificationListener", "Failed to cancel orphaned summary: ${sbn.key}", e)
                        }
                    }
            }
        } catch (e: Exception) {
            Log.d("NotificationListener", "Could not check orphaned group summaries", e)
        }
    }

    private fun extractNotificationTitleAndText(notification: Notification): Pair<String?, String?> {
        return try {
            val extras = notification.extras ?: return Pair(null, notification.tickerText?.toString()?.takeIf { it.isNotBlank() })

            var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.takeIf { it.isNotBlank() }

            var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }

            // 1. Official MessagingStyle extraction via NotificationCompat API
            try {
                val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                if (messagingStyle != null) {
                    if (title.isNullOrBlank()) {
                        title = messagingStyle.conversationTitle?.toString()?.takeIf { it.isNotBlank() }
                    }
                    if (text.isNullOrBlank()) {
                        val lastMsg = messagingStyle.messages.lastOrNull { !it.text.isNullOrBlank() }
                        if (lastMsg != null) {
                            val msgText = lastMsg.text?.toString()?.trim()
                            val msgSender = lastMsg.person?.name?.toString()?.trim()
                            text = if (msgSender != null && msgText != null) "$msgSender: $msgText" else (msgText ?: msgSender)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("NotificationListener", "Could not parse MessagingStyle via NotificationCompat", e)
            }

            // 2. InboxStyle text lines fallback (Google News, Gmail, Mail digests)
            if (text.isNullOrBlank()) {
                val linesArray = try { extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) } catch (e: Exception) { null }
                if (!linesArray.isNullOrEmpty()) {
                    text = linesArray.filterNotNull().map { it.toString().trim() }.filter { it.isNotEmpty() }.joinToString("\n")
                }
            }

            val subText = try { extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() } } catch (e: Exception) { null }
            val summaryText = try { extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.takeIf { it.isNotBlank() } } catch (e: Exception) { null }
            val infoText = try { extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.takeIf { it.isNotBlank() } } catch (e: Exception) { null }
            val ticker = notification.tickerText?.toString()?.takeIf { it.isNotBlank() }

            if (title.isNullOrBlank()) {
                title = summaryText ?: subText ?: ticker
            } else if (text.isNullOrBlank()) {
                text = summaryText ?: subText ?: infoText ?: ticker
            }

            Pair(title, text)
        } catch (e: Exception) {
            Log.e("NotificationListener", "Unmarshalling error parsing extras for title/text", e)
            Pair(null, notification.tickerText?.toString()?.takeIf { it.isNotBlank() })
        }
    }

    private fun parseNotificationDetails(notification: Notification): NotificationDetails {
        return try {
            val extras = notification.extras
            
            val progress = extras?.getInt(Notification.EXTRA_PROGRESS, -1) ?: -1
            val progressMax = extras?.getInt(Notification.EXTRA_PROGRESS_MAX, -1) ?: -1
            val progressIndeterminate = extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ?: false
            
            val showChronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) ?: false
            val chronometerCountDown = extras?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) ?: false
            val whenValue = notification.`when`

            // Rich text fields
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            val infoText = extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()

            // Android OS-level category tag - CATEGORY_PROMO, CATEGORY_MESSAGE, CATEGORY_TRANSPORT etc.
            val notifCategory = notification.category

            // Collect any 4-8 digit codes advertised through actions (some apps expose OTPs this way).
            val actionCodes = mutableListOf<String>()
            val digitCodeRegex = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")
            try {
                notification.actions?.forEach { action ->
                    val label = action.title?.toString() ?: ""
                    digitCodeRegex.findAll(label).forEach { m -> actionCodes.add(m.value) }
                    action.remoteInputs?.forEach { ri ->
                        val riLabel = ri.label?.toString() ?: ""
                        digitCodeRegex.findAll(riLabel).forEach { m -> actionCodes.add(m.value) }
                    }
                }
            } catch (e: Exception) {
                // Some devices restrict access to actions - safely ignore.
            }

            NotificationDetails(
                progress = progress,
                progressMax = progressMax,
                progressIndeterminate = progressIndeterminate,
                showChronometer = showChronometer,
                chronometerCountDown = chronometerCountDown,
                whenValue = whenValue,
                bigText = bigText,
                subText = subText,
                infoText = infoText,
                notificationCategory = notifCategory,
                actionCodes = actionCodes.distinct()
            )
        } catch (e: Exception) {
            Log.e("NotificationListener", "Unmarshalling error parsing notification details", e)
            NotificationDetails(
                progress = -1,
                progressMax = -1,
                progressIndeterminate = false,
                showChronometer = false,
                chronometerCountDown = false,
                whenValue = notification.`when`,
                bigText = null,
                subText = null,
                infoText = null,
                notificationCategory = notification.category,
                actionCodes = emptyList()
            )
        }
    }

    private data class NotificationDetails(
        val progress: Int,
        val progressMax: Int,
        val progressIndeterminate: Boolean,
        val showChronometer: Boolean,
        val chronometerCountDown: Boolean,
        val whenValue: Long,
        val bigText: String? = null,
        val subText: String? = null,
        val infoText: String? = null,
        val notificationCategory: String? = null,
        val actionCodes: List<String> = emptyList()
    )

    private data class SafeNotificationData(
        val key: String,
        val packageName: String,
        val title: String?,
        val text: String?,
        val postTime: Long,
        val channelId: String?,
        val isOngoing: Boolean,
        val isClearable: Boolean,
        val importance: Int,
        val pendingIntent: android.app.PendingIntent?,
        val details: NotificationDetails
    )
}
