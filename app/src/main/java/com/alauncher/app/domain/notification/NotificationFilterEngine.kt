package com.alauncher.app.domain.notification

import com.alauncher.app.data.settings.isFocusModeActiveNow
import com.alauncher.app.data.settings.isQuietHoursActiveNow
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.LauncherNotification

class NotificationFilterEngine(
    private val parser: NotificationParser = NotificationParser(),
    private val classifier: NotificationClassifier = NotificationClassifier(parser)
) {

    /**
     * Pre-computes and caches expensive static regex classification signals (Promo, OTP, Meeting)
     * on the notification once at creation time using pure content-based heuristics.
     *
     * IMPORTANT: This must remain settings-independent. Package allowlist gates (e.g.
     * meetingAllowedApps, calendarTrustPkgsRegex) are applied at classify() time, not here.
     */
    fun enrichWithMemoizedSignals(
        notification: LauncherNotification
    ): LauncherNotification {
        val textToMatch = listOfNotNull(
            notification.title,
            notification.text,
            notification.subText,
            notification.bigText
        ).joinToString(" ").lowercase()

        val isPromo = NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(textToMatch) ||
                      NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(textToMatch)

        val rawIntent = classifier.classifyIntrinsic(notification).first
        val isOtp = (rawIntent == NotificationIntent.OTP || parser.extractOtp(notification) != null)
        val otpMaxAge = if (isOtp) (parser.extractOtpValidityMs(notification) ?: NotificationConstants.DEFAULT_OTP_MAX_AGE_MS) else null
        // Pure content signal only — package/allowlist gating is done in classify()
        val isMeetingCandidate = rawIntent == NotificationIntent.MEETING

        return notification.copy(
            memoizedIsPromo = isPromo,
            memoizedIsOtp = isOtp,
            memoizedOtpMaxAgeMs = otpMaxAge,
            memoizedIsMeetingCandidate = isMeetingCandidate
        )
    }

    /**
     * Determines which FilterCategory a notification belongs to based on unfiltered apps list,
     * quiet hours, and focus modes.
     *
     * Runs in sub-millisecond time when static signals are memoized on the notification.
     */
    private fun isFreshOtp(notif: LauncherNotification, currentTimeMs: Long): Boolean {
        if (notif.memoizedIsOtp != true) return false
        val otpMaxAge = notif.memoizedOtpMaxAgeMs ?: NotificationConstants.DEFAULT_OTP_MAX_AGE_MS
        val otpAgeMs = currentTimeMs - notif.postTime
        return otpAgeMs in -30_000L..otpMaxAge
    }

    fun classify(
        notification: LauncherNotification,
        settings: com.alauncher.app.data.settings.SettingsState,
        currentTimeMs: Long = System.currentTimeMillis()
    ): FilterCategory {
        // Defensive self-healing: if caller passed an un-enriched notification, compute static signals once.
        val notif = if (notification.memoizedIsPromo == null ||
            notification.memoizedIsOtp == null ||
            notification.memoizedIsMeetingCandidate == null
        ) {
            enrichWithMemoizedSignals(notification)
        } else {
            notification
        }

        val isFocusMode = settings.isFocusModeActiveNow(currentTimeMs)
        val isQuietHours = settings.isQuietHoursActiveNow(currentTimeMs)
        val unfilteredApps = settings.unfilteredApps

        // 1. Check for OTP (should bypass Focus Mode / Quiet Hours muting and promo guards).
        //    Age-gate to validity window (e.g. 5-10 minutes). Authentic OTPs containing phrases like
        //    "use code" must NEVER be swallowed by promotional regex filters.
        if (isFreshOtp(notif, currentTimeMs)) {
            return FilterCategory.PRIORITY
        }

        // 2. Fail-safe promo guard: Promotional notifications must NEVER be promoted to PRIORITY
        //     (unless explicitly whitelisted by the user in unfilteredApps).
        val isPromo = notif.memoizedIsPromo == true
        if (isPromo && !unfilteredApps.contains(notif.packageName)) {
            return if (isFocusMode || isQuietHours) FilterCategory.MUTED else FilterCategory.NORMAL
        }

        // 3. Check unfiltered apps (Whitelisted by user to ALWAYS bypass Focus/Quiet hours)
        val isUnfiltered = unfilteredApps.contains(notif.packageName)
        if (isUnfiltered) {
            return FilterCategory.PRIORITY
        }

        // 4. Check for Calendar/Meeting Call reminders (should show on dynamic island/lockscreen and status bar)
        //    Architecture:
        //    a) isCalendarReminderNotification: Handles built-in trusted calendar packages (e.g. Google Calendar,
        //       Outlook, Samsung Calendar) via CALENDAR_PKG_REGEX and calendar strict keywords, OR custom apps in meetingAllowedApps.
        //    b) isContentMeetingCandidate: Pure semantic classification (NotificationIntent.MEETING) for ad-hoc
        //       video/call links (e.g. Zoom, Meet, Teams, or work chat) requiring explicit meetingAllowedApps consent.
        //    Either qualifying path promotes the notification to a meeting.
        val isContentMeetingCandidate = notif.memoizedIsMeetingCandidate == true
        val isCalendarReminder = parser.isCalendarReminderNotification(notif, settings.meetingAllowedApps)
        val isMeetingAllowed = isContentMeetingCandidate && settings.meetingAllowedApps.contains(notif.packageName)
        val isMeeting = isMeetingAllowed || isCalendarReminder
        if (isMeeting) {
            if ((isFocusMode || isQuietHours) && !settings.allowMeetingsInFocusMode) {
                return FilterCategory.MUTED
            }
            return FilterCategory.PRIORITY
        }

        // 5. Silenced in Focus Mode or Quiet Hours (MUTED)
        if (isFocusMode || isQuietHours) {
            return FilterCategory.MUTED
        }

        return FilterCategory.NORMAL
    }
}
