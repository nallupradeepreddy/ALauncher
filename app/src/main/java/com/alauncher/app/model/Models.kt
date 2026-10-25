package com.alauncher.app.model

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

enum class FilterCategory {
    PRIORITY,
    NORMAL,
    MUTED,
    HIDDEN
}

enum class SortMode {
    AlphabeticalAsc,
    AlphabeticalDesc,
    FavoritesFirst,
    MostUsed,
    RecentlyOpened
}

enum class ThemeMode {
    NordicPine,
    BotanicalMint,
    SunsetClay,
    MidnightAurora
}

enum class HomeAlignment {
    Left,
    Right
}

/**
 * Domain model representing an installed app.
 * Using a lightweight memory-only class that can hold transient icons.
 */
@Stable
data class LauncherApp(
    val packageName: String,
    val label: String,
    val normalizedLabel: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val launchCount: Int = 0,
    val lastOpenedTime: Long = 0,
    val notificationCount: Int = 0,
    val sortWeight: Int = 0,
    val icon: Drawable? = null // Transient, loaded asynchronously
)

/**
 * Domain model representing a transient capture of a notification from the listener service.
 *
 * `bigText`, `subText`, `infoText`, `notificationCategory`, and `actionCodes` are the "rich"
 * fields we now capture in addition to title/text so the classifier can make higher-accuracy
 * decisions (e.g. many bank apps put the full transaction detail only in bigText, and Android
 * gives us an OS-level category tag that we should trust for PROMO/EMAIL/TRANSPORT/EVENT hints).
 *
 * `actionCodes` collects any 4-8 digit codes advertised via `RemoteInput` / action titles by the
 * source app (many apps expose OTPs this way — much more reliable than regex parsing).
 */
@Immutable
data class LauncherNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val channelId: String?,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val importance: Int,
    val filterCategory: FilterCategory = FilterCategory.NORMAL,
    val hasPendingIntent: Boolean = false,
    val progress: Int = -1,
    val progressMax: Int = -1,
    val progressIndeterminate: Boolean = false,
    val showChronometer: Boolean = false,
    val chronometerCountDown: Boolean = false,
    val whenValue: Long = 0L,
    val bigText: String? = null,
    val subText: String? = null,
    val infoText: String? = null,
    val notificationCategory: String? = null,
    val actionCodes: List<String> = emptyList(),
    val memoizedIsPromo: Boolean? = null,
    val memoizedIsOtp: Boolean? = null,
    val memoizedOtpMaxAgeMs: Long? = null,
    val memoizedIsMeetingCandidate: Boolean? = null
)

val LauncherNotification.displayTitle: String?
    get() {
        if (!title.isNullOrBlank()) return title
        if (!text.isNullOrBlank()) return text
        if (!bigText.isNullOrBlank()) return bigText
        if (!subText.isNullOrBlank()) return subText
        if (!infoText.isNullOrBlank()) return infoText
        return null
    }

val LauncherNotification.displayBody: String?
    get() {
        val dt = displayTitle?.trim() ?: return null
        val candidates = listOfNotNull(
            text,
            bigText,
            subText,
            infoText
        ).filter { it.isNotBlank() && !it.trim().equals(dt, ignoreCase = true) }

        return candidates.firstOrNull()
    }

internal val WHITESPACE_REGEX = Regex("\\s+")

val LauncherNotification.displayTitlePreview: String?
    get() {
        val dt = displayTitle ?: return null
        val cleaned = dt.replace(WHITESPACE_REGEX, " ").trim()
        return cleaned.ifBlank { null }
    }

val LauncherNotification.displayBodyPreview: String?
    get() {
        val db = displayBody ?: return null
        val cleaned = db.replace(WHITESPACE_REGEX, " ").trim()
        return cleaned.ifBlank { null }
    }

val LauncherNotification.hasContent: Boolean
    get() {
        val dt = displayTitle ?: return false
        val db = displayBody
        if (dt.equals(appName, ignoreCase = true) && db.isNullOrBlank()) {
            return false
        }
        return true
    }


