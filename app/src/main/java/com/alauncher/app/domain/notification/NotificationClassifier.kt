package com.alauncher.app.domain.notification

import android.app.Notification
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.model.LauncherNotification
import java.util.Locale

/**
 * The classifier's coarse output — which contextual-island lane, if any, a notification
 * belongs to. Refined details (OTP digits, transaction fields, meeting schedule) are still
 * produced by NotificationParser / HomeViewModel; this classifier only decides the lane
 * and computes a confidence score used to break ties.
 */
enum class NotificationIntent {
    NONE,
    OTP,
    TRANSACTION,
    DELIVERY,
    MEETING,
    SECURITY_ALERT
}

/**
 * Result of running a notification through the classifier.
 *
 * `intent` — the winning lane (or NONE if nothing scored above the threshold or the app
 *            is not on the relevant allowlist).
 * `confidence` — the raw score of the winning lane, primarily for debugging/telemetry.
 * `blockedByAllowlist` — set to true when the classifier believed the notification
 *                        matched an intent, but the app is not on that category's
 *                        allowlist. Useful for surfacing "add this app to allowlist"
 *                        hints in the future.
 */
data class ClassifiedIntent(
    val intent: NotificationIntent,
    val confidence: Int,
    val blockedByAllowlist: Boolean = false
)

/**
 * Reusable set of signals extracted from a single notification. All flags/counters here
 * are cheap boolean/int aggregates over the notification's text + rich fields; the
 * classifier's scoring step consumes only this struct.
 */
internal data class NotificationSignals(
    val pkgLower: String,
    val bodyLower: String,
    val hasOtpKeyword: Boolean,
    val hasStrictOtpKeyword: Boolean,
    val hasActionCode: Boolean,
    val hasTransactionKeyword: Boolean,
    val hasFinancialTrustSignal: Boolean,
    val hasAccountReference: Boolean,
    val hasAmount: Boolean,
    val hasSecurityAlertKeyword: Boolean,
    val hasCalendarKeyword: Boolean,
    val hasMeetingLink: Boolean,
    val hasScheduleKeyword: Boolean,
    val hasDeliveryKeyword: Boolean,
    val hasCourierTracking: Boolean,
    val hasDeliveryProgress: Boolean,
    val osCategory: String?,
    val isOngoing: Boolean
)

/**
 * Scoring-based classifier that unifies routing decisions previously scattered across
 * `parseLiveTracker`, `isCalendarReminderNotification`, and `parseTransactionAlert`.
 *
 * Design:
 *  - We first extract a bag of signals (cheap, order-independent).
 *  - Each intent has a scoring function that consumes those signals and returns an int.
 *  - We pick the argmax above the minimum-confidence threshold.
 *  - Per-category allowlists gate the result (OTP is intentionally NEVER gated).
 *  - Content parsing (extracting OTP digits, transaction details, etc.) still runs
 *    downstream in the existing parser — this classifier only decides the lane.
 *
 * This replaces the previous first-match order-dependent branching, which had several
 * subtle bugs (refund matched debit branch first, security alerts never emitted, etc.).
 */
class NotificationClassifier(private val parser: NotificationParser = NotificationParser()) {

    companion object {
        // A hit needs at least this many points to be considered above the noise floor.
        private const val MIN_CONFIDENCE = 3

        // --- Keyword sets (word-boundary regex, not raw contains, to avoid partial matches). ---

        private val otpStrictKeywords = NotificationConstants.OTP_STRICT_KEYWORDS_REGEX
        private val otpLooseKeywords = NotificationConstants.OTP_LOOSE_KEYWORDS_REGEX

        private val transactionVerbs = NotificationConstants.TRANSACTION_VERBS_REGEX
        private val amountRegex = NotificationConstants.AMOUNT_REGEX
        private val financialTrustRegex = NotificationConstants.FINANCIAL_TRUST_REGEX
        private val accountReferenceRegex = NotificationConstants.ACCOUNT_REFERENCE_REGEX

        private val meetingKeywords = NotificationConstants.MEETING_KEYWORDS_REGEX
        private val meetingLinkRegex = NotificationConstants.MEETING_LINK_REGEX
        private val scheduleKeywords = NotificationConstants.SCHEDULE_KEYWORDS_REGEX

        private val deliveryKeywords = NotificationConstants.DELIVERY_KEYWORDS_REGEX
        // Package families known to be couriers/rides/food. NOTE: bare "amazon" is NOT
        // included because it matches Amazon Music, Kindle, Alexa, etc. The Shopping
        // package is enumerated explicitly.
        private val courierPkgRegex = NotificationConstants.COURIER_PKG_REGEX
        private val deliveryProgressRegex = NotificationConstants.DELIVERY_PROGRESS_REGEX

        private val calendarPkgRegex = NotificationConstants.CALENDAR_PKG_REGEX
    }

    /**
     * Scores the notification purely based on its content/signals without applying allowlist policy gates.
     * Accepts optional pre-computed signals to avoid redundant extraction when called from [classify].
     */
    internal fun classifyIntrinsic(notification: LauncherNotification, precomputedSignals: NotificationSignals? = null): Pair<NotificationIntent, Int> {
        val signals = precomputedSignals ?: extractSignals(notification)

        val otpScore = scoreOtp(notification, signals)
        val txnScore = scoreTransaction(signals)
        val securityScore = scoreSecurity(signals)
        val deliveryScore = scoreDelivery(signals)
        val meetingScore = scoreMeeting(signals)

        val candidates = listOf(
            NotificationIntent.OTP to otpScore,
            NotificationIntent.SECURITY_ALERT to securityScore,
            NotificationIntent.TRANSACTION to txnScore,
            NotificationIntent.MEETING to meetingScore,
            NotificationIntent.DELIVERY to deliveryScore
        )

        val winner = candidates.maxByOrNull { it.second } ?: (NotificationIntent.NONE to 0)
        return if (winner.second >= MIN_CONFIDENCE) winner else (NotificationIntent.NONE to winner.second)
    }

    /**
     * Classify a notification, respecting the user's per-category allowlists.
     */
    fun classify(notification: LauncherNotification, settings: SettingsState): ClassifiedIntent {
        val signals = extractSignals(notification)
        var (winnerIntent, winnerScore) = classifyIntrinsic(notification, signals)

        if (winnerIntent == NotificationIntent.TRANSACTION && parser.parseTransactionDetails(notification, settings) == null) {
            winnerIntent = NotificationIntent.NONE
        }

        if (winnerScore < MIN_CONFIDENCE || winnerIntent == NotificationIntent.NONE) {
            return ClassifiedIntent(NotificationIntent.NONE, winnerScore)
        }

        val pkg = notification.packageName
        val isAllowed = when (winnerIntent) {
            NotificationIntent.OTP -> true
            NotificationIntent.SECURITY_ALERT -> settings.transactionAllowedApps.contains(pkg) || isVerifiedSecuritySms(notification, signals) || isVerifiedFinancialSms(notification, signals)
            NotificationIntent.TRANSACTION -> settings.transactionAllowedApps.contains(pkg) || isVerifiedFinancialSms(notification, signals)
            NotificationIntent.DELIVERY -> settings.deliveryAllowedApps.contains(pkg)
            NotificationIntent.MEETING -> settings.meetingAllowedApps.contains(pkg)
            NotificationIntent.NONE -> true
        }

        return if (isAllowed) {
            ClassifiedIntent(winnerIntent, winnerScore, blockedByAllowlist = false)
        } else {
            ClassifiedIntent(NotificationIntent.NONE, winnerScore, blockedByAllowlist = true)
        }
    }

    private fun isVerifiedFinancialSms(notification: LauncherNotification, s: NotificationSignals): Boolean {
        val title = notification.title ?: ""
        val text = notification.text ?: ""
        val richBody = listOfNotNull(
            notification.bigText?.takeIf { it.isNotBlank() },
            notification.subText?.takeIf { it.isNotBlank() },
            notification.infoText?.takeIf { it.isNotBlank() },
            text.takeIf { it.isNotBlank() }
        ).joinToString("\n")
        val parsingSource = if (title.isNotEmpty() && richBody.isNotEmpty()) "$title\n$richBody" else title.ifEmpty { richBody }

        return NotificationParser.isVerifiedFinancialSms(
            parsingSource = parsingSource,
            combinedLower = s.bodyLower,
            pkg = notification.packageName,
            appName = notification.appName,
            title = notification.title
        )
    }

    private fun isVerifiedSecuritySms(notification: LauncherNotification, s: NotificationSignals): Boolean {
        return NotificationParser.isVerifiedSecuritySms(
            combinedLower = s.bodyLower,
            pkg = notification.packageName,
            appName = notification.appName,
            title = notification.title
        )
    }

    // ---------- Signal extraction ----------

    private fun extractSignals(notification: LauncherNotification): NotificationSignals {
        val pkg = notification.packageName.lowercase(Locale.ROOT)
        val body = listOfNotNull(
            notification.title, notification.text,
            notification.bigText, notification.subText, notification.infoText
        ).joinToString(" ")
        val bodyLower = body.lowercase(Locale.ROOT)

        return NotificationSignals(
            pkgLower = pkg,
            bodyLower = bodyLower,
            hasOtpKeyword = otpLooseKeywords.containsMatchIn(bodyLower) || otpStrictKeywords.containsMatchIn(bodyLower),
            hasStrictOtpKeyword = otpStrictKeywords.containsMatchIn(bodyLower),
            hasActionCode = notification.actionCodes.isNotEmpty(),
            hasTransactionKeyword = transactionVerbs.containsMatchIn(bodyLower),
            hasFinancialTrustSignal = financialTrustRegex.containsMatchIn(pkg) ||
                financialTrustRegex.containsMatchIn(notification.appName ?: "") ||
                financialTrustRegex.containsMatchIn(notification.title ?: ""),
            hasAccountReference = accountReferenceRegex.containsMatchIn(bodyLower),
            hasAmount = amountRegex.containsMatchIn(body),
            hasSecurityAlertKeyword = NotificationParser.securityAlertKeywordsRegex.containsMatchIn(bodyLower),
            hasCalendarKeyword = meetingKeywords.containsMatchIn(bodyLower) ||
                calendarPkgRegex.containsMatchIn(pkg),
            hasMeetingLink = meetingLinkRegex.containsMatchIn(body),
            hasScheduleKeyword = scheduleKeywords.containsMatchIn(bodyLower),
            hasDeliveryKeyword = deliveryKeywords.containsMatchIn(bodyLower),
            hasCourierTracking = courierPkgRegex.containsMatchIn(pkg),
            hasDeliveryProgress = deliveryProgressRegex.containsMatchIn(bodyLower),
            osCategory = notification.notificationCategory,
            isOngoing = notification.isOngoing
        )
    }

    // ---------- Scoring ----------

    private fun scoreOtp(notification: LauncherNotification, s: NotificationSignals): Int {
        // Promo / recommendation notifications should never surface as OTPs.
        if (s.osCategory == "promo" || s.osCategory == "recommendation") return 0
        if (NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(s.bodyLower) ||
            NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(s.bodyLower)) return 0

        var score = 0
        val extractedOtp = parser.extractOtp(notification)
        val hasExtractedOtp = extractedOtp != null

        // Action-side code declared by the app is the highest-confidence signal.
        // Require an OTP context keyword in the body to avoid reply-suggestion digits.
        if (s.hasActionCode && s.hasOtpKeyword) score += 6
        // Action code present but no OTP keyword in body — still worth something if we
        // can actually extract a well-formed OTP from the text.
        else if (s.hasActionCode && hasExtractedOtp) score += 4
        if (s.hasStrictOtpKeyword) score += 4
        // If we can actually extract a well-formed OTP, that is worth a lot.
        if (hasExtractedOtp) score += 4
        // Msg category boost (Android tags SMS/messaging apps with CATEGORY_MSG).
        if (s.osCategory == Notification.CATEGORY_MESSAGE) score += 1
        // Very common transactional apps posting an amount are NOT OTPs even if they
        // mention "code" (e.g. "promo code SAVE15").
        if (s.hasAmount && !s.hasStrictOtpKeyword && !s.hasActionCode) score -= 3
        return score.coerceAtLeast(0)
    }

    private fun scoreTransaction(s: NotificationSignals): Int {
        if (s.osCategory == "promo" || s.osCategory == "recommendation") return 0
        if (NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(s.bodyLower) ||
            NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(s.bodyLower)) return 0
        // Security alerts that ALSO contain transaction signals (e.g. "Suspicious debit of ₹5000")
        // should still surface as TRANSACTION so the amount/merchant details are preserved.
        // The security lane only wins when there are NO transaction signals alongside it.
        if (s.hasSecurityAlertKeyword && !s.hasTransactionKeyword && !s.hasAmount) return 0

        var score = 0
        if (s.hasTransactionKeyword) score += 2
        if (s.hasAmount) score += 2
        if (s.hasFinancialTrustSignal) score += 2
        if (s.hasAccountReference) score += 2
        // Strong pattern: bank package + amount + verb.
        if (s.hasFinancialTrustSignal && s.hasAmount && s.hasTransactionKeyword) score += 2
        // OTP-shaped notifications should not surface as transactions.
        if (s.hasStrictOtpKeyword) score -= 4
        return score.coerceAtLeast(0)
    }

    private fun scoreSecurity(s: NotificationSignals): Int {
        if (s.osCategory == "promo" || s.osCategory == "recommendation") return 0
        if (NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(s.bodyLower) ||
            NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(s.bodyLower)) return 0

        var score = 0
        if (s.hasSecurityAlertKeyword) score += 5
        if (s.hasFinancialTrustSignal) score += 2
        if (s.osCategory == "err") score += 1
        return score.coerceAtLeast(0)
    }

    private fun scoreMeeting(s: NotificationSignals): Int {
        if (s.osCategory == "promo" || s.osCategory == "recommendation") return 0
        if (NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(s.bodyLower) ||
            NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(s.bodyLower)) return 0

        var score = 0
        if (s.osCategory == Notification.CATEGORY_EVENT &&
            (calendarPkgRegex.containsMatchIn(s.pkgLower) || s.hasCalendarKeyword || s.hasMeetingLink || s.hasScheduleKeyword)) score += 4
        if (s.osCategory == Notification.CATEGORY_REMINDER) score += 3
        if (s.hasMeetingLink) score += 3
        if (s.hasCalendarKeyword) score += 2
        if (s.hasScheduleKeyword) score += 1
        // Calendar keyword + schedule keyword is a strong combined signal.
        if (s.hasCalendarKeyword && s.hasScheduleKeyword) score += 2
        // Transactional notifications should not surface as meetings.
        if (s.hasAmount && s.hasTransactionKeyword) score -= 3
        return score.coerceAtLeast(0)
    }

    private fun scoreDelivery(s: NotificationSignals): Int {
        if (s.osCategory == "promo" || s.osCategory == "recommendation") return 0
        if (NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(s.bodyLower) ||
            NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(s.bodyLower)) return 0

        var score = 0
        if (s.osCategory == Notification.CATEGORY_TRANSPORT) score += 3
        if (s.hasDeliveryKeyword) score += 2
        if (s.hasDeliveryProgress) score += 2
        if (s.hasCourierTracking) {
            if (s.hasDeliveryKeyword || s.hasDeliveryProgress || s.isOngoing || s.osCategory == Notification.CATEGORY_TRANSPORT) {
                score += 3
            } else {
                score += 1
            }
        }
        // Ongoing notification from a known courier is a very strong live-tracking signal.
        if (s.isOngoing && s.hasCourierTracking) score += 2
        // Penalise when there is NO delivery-specific keyword or progress signal:
        if (s.hasAmount && s.hasTransactionKeyword && !s.hasDeliveryKeyword && !s.hasDeliveryProgress) score -= 3
        return score.coerceAtLeast(0)
    }
}
