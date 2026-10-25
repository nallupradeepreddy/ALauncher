package com.alauncher.app.domain.notification

import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.ui.home.MoneyAmount
import com.alauncher.app.ui.home.TransactionDetails
import com.alauncher.app.ui.home.TransactionAlertType
import com.alauncher.app.ui.home.TransactionSettingsKeys
import com.alauncher.app.data.settings.SettingsState
import java.util.Locale

class NotificationParser {

    companion object {
        private val progressPercentRegex = Regex("(\\d{1,3})%")
        internal val otpValidityRegex = Regex("""(?i)valid(?:\s+for|\s+in|ity)?\s+(\d{1,2})\s*(?:min(?:ute)?s?|mins)""")
        // OTP patterns — ordering matters: more specific first.
        // Pattern 1 accepts bare "code" with a negative lookbehind blocking promo prefixes
        // ("promo code: 123456"). Pattern 2 accepts "123456 is your code" constructions.
        // Fallback keyword scan uses only strict OTP-specific phrases — "code" and "pin"
        // alone are excluded to prevent promo/coupon false positives in the digit scan.
        private val otpPattern1 = NotificationConstants.OTP_PATTERN_1
        private val otpPattern2 = NotificationConstants.OTP_PATTERN_2
        private val otpPattern3 = NotificationConstants.OTP_PATTERN_3
        private val otpAlphanumRegex = NotificationConstants.OTP_ALPHANUM_REGEX
        private val otpDashedRegex = NotificationConstants.OTP_DASHED_REGEX
        private val otpPatternFallback = NotificationConstants.OTP_PATTERN_FALLBACK
        private val otpKeywords = NotificationConstants.OTP_KEYWORDS
        private val otpContextKeywords = NotificationConstants.OTP_CONTEXT_KEYWORDS

        private val protocolHeaderPrefixRegex = Regex("""(?i)^\s*<#>\s*""")
        private val domainHashSuffixRegex = Regex("""(?i)\s*@[a-z0-9\.\-]+\s*$""")
        private val appSignatureHashSuffixRegex = Regex("""(?i)\s*\b(?=[a-zA-Z0-9]*\d)[a-zA-Z0-9]{11}\b\s*$""")
        private val maskedIdPrefixRegex = Regex("""(?i)(?:[xX\*]{2,}\s*|[xX\*]{2,}\s+[xX\*]{2,}\s*|\b(?:uan|otp-id|id|txn\s*id|order\s*id|user\s*id|customer\s*id|policy|pnr)\b\s*[:\-]?\s*|\bref\b\.?\s*[:\-]?\s*)$""")
        private val idLabelRegex = Regex("""(?i)\b(?:uan|id|ref|otp-id)\b""")
        private val merchantTrailingDigitsRegex = Regex("""\s+\d{4,}+$""")
        private val currencySuffixCheckRegex = Regex("""(?:INR|USD|EUR|GBP|AED|CAD|AUD|SGD|Rs\.?|₹|\$|€|£|¥)\s*$""", RegexOption.IGNORE_CASE)
        val severeWeatherAlertRegex = Regex("""\b(?:severe\s+weather|tornado|tsunami|flash\s+flood|hurricane|earthquake|extreme\s+heat)\s+(?:warning|alert|watch)\b""", RegexOption.IGNORE_CASE)

        private val indianLakhRegex = Regex("""^\d{1,3}(?:,\d{2}){1,},\d{3}(?:\.\d+)?$""")

        private val otpPatternsList = listOf(otpPattern1, otpPattern2, otpPattern3)

        private val allNumbersRegex = Regex("""\b([0-9,.]*[0-9])\b""")

        private val batteryRegex = Regex("""(?:battery (?:is )?(?:now )?charged|charged to \d+%\b)""", RegexOption.IGNORE_CASE)

        private val merchantCreditRefundRegex = Regex(
            """\b(?:from|by|to)\s+(?:(?:your|the|our|their)\s+)?((?:(?!\b(?:on|at|to|from|for|ref|by|with|via|through|in|txn|using|towards|a/c|card|account|balance|vpa)\b)[A-Za-z0-9\*'&\-\./#@_])+(?:\s+(?:(?!\b(?:on|at|to|from|for|ref|by|with|via|through|in|txn|using|towards|a/c|card|account|balance|vpa)\b)[A-Za-z0-9\*'&\-\./#@_])+)*)""",
            RegexOption.IGNORE_CASE
        )

        private val merchantDebitRegex = Regex(
            """\b(?:at|to|for|towards|with|via|through)\s+(?:(?:your|the|our|their)\s+)?((?:(?!\b(?:on|at|to|from|for|ref|by|with|via|through|in|txn|using|towards|a/c|card|account|balance|vpa)\b)[A-Za-z0-9\*'&\-\./#@_])+(?:\s+(?:(?!\b(?:on|at|to|from|for|ref|by|with|via|through|in|txn|using|towards|a/c|card|account|balance|vpa)\b)[A-Za-z0-9\*'&\-\./#@_])+)*)""",
            RegexOption.IGNORE_CASE
        )

        private val excludedProgressPkgs = listOf(
            "calendar", "outlook", "gmail", "android.gm", "mail", "teams", "zoom",
            "webex", "meet", "slack", "whatsapp", "telegram", "messages"
        )

        private val completedProgressKeywords = listOf(
            "download complete", "upload complete", "installed",
            "updated successfully", "sync complete", "backup complete"
        )

        private val progressKeywords = listOf("download", "upload", "install", "update", "transfer", "sync", "backup", "sending", "receiving", "progress")

        private val excludedTransactionPkgs = listOf(
            "teams", "zoom", "slack", "webex", "meet",
            "whatsapp", "telegram", "linkedin", "skype", "facebook", "messenger",
            "instagram", "twitter", "discord", "reddit", "tiktok", "snapchat",
            "gmail", "outlook", "mail", "chrome", "firefox", "opera", "safari", "browser",
            "youtube", "spotify", "netflix", "disney", "hulu", "primevideo",
            "medium", "substack", "quora", "news", "dailyhunt", "inshorts", "flipboard", "feedly"
        )

        private val otpTransactionExclusionRegex = Regex(
            "\\b(?:otp|one time password|one-time password|verification code|authorization code|do not share)\\b",
            RegexOption.IGNORE_CASE
        )
        
        // "pending" removed: "EMI payment pending" and "payment pending confirmation" are real alerts.
        // "scheduled" removed: "auto-debit scheduled" is a real EMI/mandate alert.
        // "processing" removed: "refund processing" is a real refund update.
        // "initiated" removed: "transfer initiated" is a real transaction.
        private val promoTransactionExclusionRegex = Regex(
            "\\b(?:cashback\\s+offer|eligible\\s+for|save\\s+(?:up\\s+to|flat)|discount\\s+(?:code|coupon)|coupon\\s+code|apply\\s+now|estimate\\s+only|could\\s+be|may\\s+be)\\b",
            RegexOption.IGNORE_CASE
        )

        private val promoExclusionRegex = Regex(
            """\b(?:win|chance|luck|lucky|scratch\s+card|prize|jackpot|gift|voucher|promo|promotion|earn|assured|win\s+up\s+to|get\s+up\s+to|stand\s+a\s+chance)\b|""" +
            """\b(?:get|flat|save|up\s?to)\s+(?:Rs\.?|INR|₹|[\$€₹£¥])?\s*\d+\s*(?:%|off|cashback)\b""",
            RegexOption.IGNORE_CASE
        )

        private val nonTransactionAlertExclusionRegex = Regex(
            """\b(?:should|must|kindly|please|request\s+to|how\s+to|instructed\s+to|fail\s+to|non-maintenance|maintenance\s+of|required\s+balance|minimum\s+balance|required\s+amb|minimum\s+amb|trading\s+at|is\s+trading\s+at|target\s*:\s*(?:₹|Rs\.?|INR|[\$€₹£¥])?\s*\d+|buy\b.{1,100}?\bat\s*(?:₹|Rs\.?|INR|[\$€₹£¥])?\s*\d+|pre-approved|pre\s+approved|apply\s+now|apply\s+for|loan\s+offer|get\s+pre-approved|eligible\s+for\s+loan|market\s+update|crypto\s+market|stock\s+update|stock\s+pick|short-term\s+pick|short\s+term\s+pick|secured\s+(?:a\s+)?(?:Rs\.?|INR|₹|[\$€₹£¥])?\s*\d+(?:\.\d+)?\s*(?:lakhs?|lac|l|crores?|cr|k|m)?\s*(?:order|contract|deal)?|secured\s+(?:a\s+)?(?:[a-z0-9]+\s+){0,4}(?:order|contract|deal)|bags?\s+(?:a\s+)?(?:[a-z0-9]+\s+){0,3}(?:order|contract)|wins?\s+(?:a\s+)?(?:[a-z0-9]+\s+){0,3}(?:order|contract)|order\s+from|order\s+worth|ipo\s+alert|new\s+ipo|price\s+range|last\s+date\s+to\s+apply|gift\s+nifty|key\s+levels|(?:is|are|now)\s+live|instant\s+withdrawals?|instant\s+payouts?|credited\s+in\s+under\s+\d+|tap\s+to\s+try|try\s+now|up\s?to\s+(?:Rs\.?|INR|₹|[\$€₹£¥])?\s*\d+)\b""",
            RegexOption.IGNORE_CASE
        )

        private val amountIndicatorKeywordsRegex = Regex(
            "\\b(?:charged|spent|paid|debited|credited|amount|amt|for|of|value|price)\\b",
            RegexOption.IGNORE_CASE
        )

        private val calendarTrustPkgsRegex = NotificationConstants.CALENDAR_PKG_REGEX
        
        private val calendarExclusionRegex = Regex(
            "\\b(?:sent you a message|new message|reacted to|liked your message|posted in|missed activity|unread messages|new email|sent an email|activity digest|unread digest|post|liked|reacted)\\b",
            RegexOption.IGNORE_CASE
        )
        
        private val calendarStrictKeywordsRegex = Regex(
            """\b(?:meeting|standup|scrum|interview|1:1|1on1|1-on-1|1-1|sync\s+(?:call|meeting|up)|review\s+meeting|kickoff|retrospective|planning\s+(?:meeting|session)|huddle|town\s+hall|all[-\s]?hands)\b""",
            RegexOption.IGNORE_CASE
        )
        
        private val scheduledContextKeywordsRegex = Regex(
            "\\b(?:starting\\s+(?:now|in|at)|starts\\s+(?:in|at)|join\\s+now|upcoming\\s+(?:meeting|call)|scheduled\\s+(?:for|at)|today\\s+at|tomorrow\\s+at|in\\s+\\d+\\s*(?:mins|minutes))\\b",
            RegexOption.IGNORE_CASE
        )

        private val timeRegex = Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|AM|PM)?\\b")
        private val durationRegex = Regex("\\(\\d+\\s*(?:m|min|mins|minutes|h|hr|hrs|hours)\\)")
        private val meetingLinkRegex = NotificationConstants.MEETING_LINK_REGEX
        private val meetingInviteContextRegex = Regex(
            """\b(?:meeting\s+id|join\s+(?:the\s+)?meeting|meeting\s+passcode|meeting\s+password|dial[-\s]in|conference\s+id|host\s+key|webinar\s+id|join\s+by\s+phone|join\s+by\s+computer)\b""",
            RegexOption.IGNORE_CASE
        )
        private val otpPrefixRegex = Regex("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}:\\s*")
        private val otpContextKeywordsRegex = Regex("""(?i)\b(?:is|code|otp|passcode|password)\b""")
        private val calendarFwReRegex = Regex("^(?i)(?:fw|re|fwd|reply|forward)\\s*:\\s*")
        private val httpLinkRegex = Regex("https?://\\S+")
        
        private val dayKeywords = listOf("today", "tomorrow", "yesterday", "mon", "tue", "wed", "thu", "fri", "sat", "sun")
        private val monthKeywords = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        private val scheduleSplitRegex = Regex("[•\\n\\r]")

        private val txAmountRegex = Regex(
            """(?:\b(?:Rs\.?|INR|USD|EUR|GBP|AED|CAD|AUD|SGD|NZD|CHF|HKD|JPY|CNY|SAR|QAR|KWD|BHD|OMR|ZAR|BRL|RUB|MYR|THB|IDR|PHP|VND|KRW)|[\$€₹£¥])\s*([0-9,.]*[0-9])|([0-9,.]*[0-9])\s*(?:Rs\.?|INR|USD|EUR|GBP|AED|CAD|AUD|SGD|NZD|CHF|HKD|JPY|CNY|SAR|QAR|KWD|BHD|OMR|ZAR|BRL|RUB|MYR|THB|IDR|PHP|VND|KRW|₹|\$|€|£|¥)\b""",
            RegexOption.IGNORE_CASE
        )

        private val amountCheckRegex = Regex(
            """^(?:(?:Rs\.?|INR|₹|\$|€|£|USD|AED|¥)\s*[0-9,.]+|[0-9]+[.,][0-9]{2,3}|[0-9]{2,})\b""",
            RegexOption.IGNORE_CASE
        )
        private val txLakhsRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(?:lakhs?|lac|L)""", RegexOption.IGNORE_CASE)
        private val txPrepositionRegex = Regex(
            """\b(?:at|to|from|for|towards|by|with|via|through)\s+(?:(?:your|the|our|their)\s+)?((?:(?!\b(?:on|at|to|from|for|ref|by|with|via|through|in|txn|using|towards|a/c|card|account|balance|vpa)\b)[A-Za-z0-9\*'&\-\./#@_])+(?:\s+(?:(?!\b(?:on|at|to|from|for|ref|by|with|via|through|in|txn|using|towards|a/c|card|account|balance|vpa)\b)[A-Za-z0-9\*'&\-\./#@_])+)*)""",
            RegexOption.IGNORE_CASE
        )
        private val upiMerchantRegex = Regex(
            """@([A-Za-z][A-Za-z0-9_]{2,}(?:\s+[A-Za-z][A-Za-z0-9_]{1,}){0,4})"""
        )

        private val txCardSuffixRegex = Regex("""\b(?:card|a/c|acc|account)\s*(?:ending in|ending|no\.?)?\s*[xX\*#]*([0-9]{3,4})\b""", RegexOption.IGNORE_CASE)
        private val txDateRegex = Regex("""\b(?:on|by|before|due)\s+(\d+\s+days?\s+ago|\d+\s+days?|[0-9]{1,2}(?:[/\-][0-9]{1,2}(?:[/\-][0-9]{2,4})?)?|tomorrow|today)\b""", RegexOption.IGNORE_CASE)

        private val financialAppKeywordsRegex = Regex(
            """\b(?:chase|hdfc|citi|pay|wallet|card|bank|finance|money|pluxee|zeta|amex|hsbc|barclays|wells|capitalone|discover|revolut|monzo|cred|slice|uni|phonepe|gpay|paytm|paypal)\b""",
            RegexOption.IGNORE_CASE
        )

        private val accountReferenceRegex = Regex(
            """\b(?:card|a/c|acct|account|bank|wallet|upi|gpay|paytm|phonepe|imps|neft|rtgs|nach|mandate|credit\s+card|debit\s+card)\b|\b(?:ending\s+in|ending\s+with|ending\s+xxxx|xxxx\s*\d+|a/c\s+xxxx)\b""",
            RegexOption.IGNORE_CASE
        )

        private val strictTransactionPhraseRegex = Regex(
            """\b(?:debited|credited|refunded|reversal|reversed|auto-debit|auto-debited|deducted|payment\s+successful|transaction\s+completed|statement\s+ready|outstanding\s+amount|minimum\s+amount\s+due|total\s+amount\s+due|statement\s+generated|emi\s+due|bill\s+due|payment\s+failed|transaction\s+declined)\b|\b(?:spent|paid|charged|sent|transferred|withdrawn)\b.{1,50}?\b(?:at|to|from|on|for|by|through|with|towards|via)\b""",
            RegexOption.IGNORE_CASE
        )

        private val debitKeywordsRegex = Regex(
            """\b(?:debited|spent|paid|payment\s+to|sent\s+to|transferred\s+to|withdrawn|charged|used\s+at|transaction\s+done|transaction\s+successful|paid\s+using|upi\s+payment|card\s+transaction|atm\s+withdrawal|cash\s+withdrawal|auto-debited|deducted|debit\s+of|transfer\s+of|sent|auto-debit|remitted|withdrew|transferred)\b""",
            RegexOption.IGNORE_CASE
        )

        private val refundKeywordsRegex = Regex(
            """\b(?:refunded|reversal|reversed|refund\s+processed|refund\s+completed|money\s+back|credit\s+back|returned\s+to\s+account)\b""",
            RegexOption.IGNORE_CASE
        )

        private val creditKeywordsRegex = Regex(
            """\b(?:credited|received|deposited|added\s+to\s+your\s+account|added\s+to|added|credit\s+of|received\s+from|cashback\s+received|settlement\s+received|salary\s+credited|neft\s+credit|imps\s+credit|rtgs\s+credit|upi\s+credit|payment\s+received|is\s+received)\b""",
            RegexOption.IGNORE_CASE
        )

        private val failedKeywordsRegex = Regex(
            """\b(?:unsuccessful|could\s+not\s+process|retry\s+payment|mandate\s+failed|auto\s+debit\s+failed|card\s+declined|payment\s+overdue|payment\s+pending|bounced|returned\s+unpaid|payment\s+failed|transaction\s+declined|insufficient\s+funds|returned\s+item)\b""",
            RegexOption.IGNORE_CASE
        )

        private val billDueKeywordsRegex = Regex(
            """\b(?:minimum\s+amount\s+due|total\s+amount\s+due|statement\s+ready|invoice\s+generated|pay\s+by|last\s+date|due\s+date|outstanding\s+amount|amount\s+payable|bill\s+due|statement\s+generated|payment\s+due|due\s+on|outstanding\s+bill|overdue|bill\s+overdue|past\s+due)\b""",
            RegexOption.IGNORE_CASE
        )

        private val renewalKeywordsRegex = Regex(
            """\b(?:auto-renew|subscription\s+renewal|will\s+renew\s+on|recurring\s+charge|trial\s+ends|billing\s+starts|membership\s+renews|plan\s+renews|next\s+billing\s+date|subscription\s+expires|price\s+will\s+change)\b""",
            RegexOption.IGNORE_CASE
        )

        private val lowBalanceKeywordsRegex = Regex(
            """\b(?:low\s+balance|balance\s+below|insufficient\s+balance|maintain\s+minimum\s+balance)\b""",
            RegexOption.IGNORE_CASE
        )

        private val emiDueKeywordsRegex = Regex(
            """\b(?:emi\s+due|mandate|auto\s+debit\s+scheduled|nach|loan\s+payment\s+due|sip\s+debit|scheduled\s+payment)\b""",
            RegexOption.IGNORE_CASE
        )

        private val paymentRequestKeywordsRegex = Regex(
            """\b(?:payment\s+request|collect\s+request|approve\s+payment|requested\s+money|upi\s+mandate\s+request)\b""",
            RegexOption.IGNORE_CASE
        )

        private val billGeneratedKeywordsRegex = Regex(
            """\b(?:statement\s+generated|statement\s+is\s+ready|new\s+statement|invoice\s+generated|bill\s+generated|bill\s+is\s+ready|monthly\s+statement)\b""",
            RegexOption.IGNORE_CASE
        )

        internal val securityAlertKeywordsRegex = NotificationConstants.SECURITY_ALERT_KEYWORDS_REGEX

        private val nonFinancialUnitRegex = Regex(
            """(?i)^\s*(?:users?|downloads?|followers?|subscribers?|views?|likes?|reviews?|ratings?|plays?|members?|points?|stars?|coins?|tokens?|items?|units?|steps?|km|miles?)\b"""
        )

        private val prefixExcludedMerchantKeywords = setOf(
            "avoid", "apply", "login", "register", "download", "install",
            "open", "click", "visit", "get", "pay", "trade", "view", "link",
            "your", "our", "their", "the", "try", "tap", "explore", "learn",
            "check", "know", "start", "claim", "subscribe", "now", "ministry",
            "government", "govt", "department", "dept", "state", "union", "central",
            "authority", "bureau", "council", "commission", "board"
        )
        private val fullyExcludedMerchantKeywords = setOf(
            "loan", "card", "account", "neft", "imps", "rtgs", "upi", "nach",
            "bank", "wallet", "mandate", "credit", "debit", "savings"
        )

        fun isVerifiedFinancialSms(
            parsingSource: String,
            combinedLower: String,
            pkg: String,
            appName: String?,
            title: String?
        ): Boolean {
            val hasAmount = txAmountRegex.containsMatchIn(parsingSource)
            val hasVerb = NotificationConstants.TRANSACTION_VERBS_REGEX.containsMatchIn(combinedLower)
            val hasTrustSignal = NotificationConstants.FINANCIAL_TRUST_REGEX.containsMatchIn(pkg) ||
                                 NotificationConstants.FINANCIAL_TRUST_REGEX.containsMatchIn(appName ?: "") ||
                                 NotificationConstants.FINANCIAL_TRUST_REGEX.containsMatchIn(title ?: "")
            val hasRefOrAccount = NotificationConstants.ACCOUNT_REFERENCE_REGEX.containsMatchIn(combinedLower) ||
                                  hasTrustSignal ||
                                  combinedLower.contains("ref") || combinedLower.contains("utr") || combinedLower.contains("vpa")
            return hasAmount && hasVerb && hasRefOrAccount
        }

        fun isVerifiedSecuritySms(
            combinedLower: String,
            pkg: String,
            appName: String?,
            title: String?
        ): Boolean {
            val hasSecurityAlert = securityAlertKeywordsRegex.containsMatchIn(combinedLower)
            if (!hasSecurityAlert) return false
            val hasTrustSignal = NotificationConstants.FINANCIAL_TRUST_REGEX.containsMatchIn(pkg) ||
                                 NotificationConstants.FINANCIAL_TRUST_REGEX.containsMatchIn(appName ?: "") ||
                                 NotificationConstants.FINANCIAL_TRUST_REGEX.containsMatchIn(title ?: "")
            val hasAccountRef = NotificationConstants.ACCOUNT_REFERENCE_REGEX.containsMatchIn(combinedLower)
            val hasContextKeywords = combinedLower.contains("bank") || combinedLower.contains("card") ||
                                    combinedLower.contains("login") || combinedLower.contains("device") || combinedLower.contains("account")
            return hasTrustSignal || hasAccountRef || hasContextKeywords
        }
    }

    /**
     * Extracts the OTP validity window in milliseconds from the notification body,
     * e.g. "valid for 10 minutes" → 600_000L. Returns null if no validity is stated.
     */
    fun extractOtpValidityMs(notification: LauncherNotification): Long? {
        val fields = listOfNotNull(notification.text, notification.bigText, notification.subText)
        for (field in fields) {
            val mins = NotificationParser.otpValidityRegex.find(field)?.groupValues?.get(1)?.toLongOrNull()
            if (mins != null && mins in 1..60) return mins * 60_000L
        }
        return null
    }

    /**
     * Extracts OTP code if present, checking title, text and the notification's rich fields.
     *
     * If the source app advertised an action-side code (via a RemoteInput or action title),
     * that is preferred — this is the highest-precision signal because the app itself
     * declared the code, so no regex heuristics are needed.
     */
    fun extractOtp(notification: LauncherNotification): String? {
        // 1. Strongest signal: app-declared action codes (RemoteInput / action title).
        //    Only treat as OTP if the notification's other text mentions an OTP-context keyword,
        //    to avoid picking up reply-suggestion digits, etc.
        val bodyForContext = listOfNotNull(
            notification.title, notification.text, notification.bigText, notification.subText
        ).joinToString("\n").lowercase(Locale.ROOT)
        if (notification.actionCodes.isNotEmpty() && otpContextKeywords.any { bodyForContext.contains(it) }) {
            // Prefer a 6-digit code if available (most common OTP length), else first.
            return notification.actionCodes.firstOrNull { it.length == 6 } ?: notification.actionCodes.first()
        }

        // 2. Regex extraction over each rich-text field in priority order.
        val fields = listOfNotNull(
            notification.text,
            notification.title,
            notification.bigText,
            notification.subText
        )
        for (field in fields) {
            val hit = extractOtpFromSingleString(field)
            if (hit != null) return hit
        }
        return null
    }

    /**
     * Back-compat shim for legacy call sites that still pass (title, text).
     */
    fun extractOtp(title: String?, text: String?): String? {
        val t = title ?: ""
        val tx = text ?: ""
        return extractOtpFromSingleString(tx) ?: extractOtpFromSingleString(t)
    }

    private fun isMeetingInviteContext(text: String): Boolean {
        return meetingLinkRegex.containsMatchIn(text) || meetingInviteContextRegex.containsMatchIn(text)
    }

    private fun extractOtpFromSingleString(text: String): String? {
        // Return null immediately for meeting invitations or promotional text so that
        // meeting passcodes or promo codes/timers are never extracted as authentication OTPs.
        if (isMeetingInviteContext(text) ||
            NotificationConstants.PROMO_KEYWORD_REGEX.containsMatchIn(text) ||
            NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX.containsMatchIn(text)) {
            return null
        }

        // Sanitize protocol headers (<#>, @domain hash tags, app signature hashes, time/clock formats)
        val cleanedText = text
            .replace(protocolHeaderPrefixRegex, "")
            .replace(domainHashSuffixRegex, "")
            .replace(appSignatureHashSuffixRegex, "")
            .replace(Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b"""), "") // Strip HH:MM:SS or HH:MM timer formats
            .trim()

        // 1. Alphanumeric OTPs (some Indian issuer banks).
        otpAlphanumRegex.find(cleanedText)?.let { return it.groupValues[1] }

        // 2. Dashed OTPs (e.g. Apple, some Google flows).
        otpDashedRegex.find(cleanedText)?.let { return it.groupValues[1].replace(Regex("[- ]"), "") }

        // 3. Prioritised numeric OTP patterns.
        for (pattern in otpPatternsList) {
            val match = pattern.find(cleanedText)
            if (match != null) {
                // Some patterns have multiple capturing groups (e.g. OTP_PATTERN_3 has two
                // alternatives each with their own group). Return the first non-empty group.
                val code = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
                if (code != null) return code
            }
        }

        // 4. Fallback: If a STRICT otp keyword (not "code" or "pin" alone) is present,
        //    look for any 4-8 digit number. This prevents notifications like
        //    "Use promo code SAVE15 for 20% off" from parsing "SAVE15" style noise.
        if (otpKeywords.any { cleanedText.contains(it, ignoreCase = true) }) {
            val matches = otpPatternFallback.findAll(cleanedText).toList()
            val safeMatches = matches.filter { match ->
                val startIdx = match.range.first
                val endIdx = match.range.last + 1

                // 1. Filter out decimals (e.g. 1882 in 1882.15 or 1882,15)
                val isDecimalSuffix = endIdx < cleanedText.length && (cleanedText[endIdx] == '.' || cleanedText[endIdx] == ',') && endIdx + 1 < cleanedText.length && cleanedText[endIdx + 1].isDigit()
                val isDecimalPrefix = startIdx > 0 && (cleanedText[startIdx - 1] == '.' || cleanedText[startIdx - 1] == ',') && startIdx - 2 >= 0 && cleanedText[startIdx - 2].isDigit()
                if (isDecimalSuffix || isDecimalPrefix) {
                    return@filter false
                }

                // 2. Filter out currencies and typical transaction terms
                val prefix = cleanedText.substring(0, startIdx).trimEnd()
                val isCurrency = currencySuffixCheckRegex.containsMatchIn(prefix)
                val isCardOrAccount = prefix.endsWith("ending in", ignoreCase = true) ||
                                      prefix.endsWith("ending", ignoreCase = true) ||
                                      prefix.endsWith("card", ignoreCase = true) ||
                                      prefix.endsWith("A/C", ignoreCase = true) ||
                                      prefix.endsWith("A/c", ignoreCase = true) ||
                                      prefix.endsWith("a/c", ignoreCase = true) ||
                                      prefix.endsWith("Acc", ignoreCase = true) ||
                                      prefix.endsWith("Account", ignoreCase = true) ||
                                      prefix.endsWith("Acct", ignoreCase = true) ||
                                      prefix.endsWith("no.", ignoreCase = true) ||
                                      prefix.endsWith("number", ignoreCase = true) ||
                                      prefix.endsWith("no", ignoreCase = true)
                val isMaskedOrIdPrefix = maskedIdPrefixRegex.containsMatchIn(prefix)
                !isCurrency && !isCardOrAccount && !isMaskedOrIdPrefix
            }
            if (safeMatches.size == 1) {
                return safeMatches[0].value
            } else if (safeMatches.size > 1) {
                // If multiple numbers, pick the one closest to an OTP keyword context (e.g., preceded by "is", "code", "otp") and not an ID label
                val bestMatch = safeMatches.find { match ->
                    val startIdx = match.range.first
                    val prefixSegment = text.substring((startIdx - 25).coerceAtLeast(0), startIdx).lowercase(Locale.ROOT)
                    val hasOtpContext = otpContextKeywordsRegex.containsMatchIn(prefixSegment)
                    val hasIdLabel = idLabelRegex.containsMatchIn(prefixSegment)
                    hasOtpContext && !hasIdLabel
                }
                return bestMatch?.value ?: safeMatches.find { it.value.length == 6 }?.value ?: safeMatches[0].value
            }
        }

        return null
    }

    /**
     * Extracts download or upload progress message if applicable.
     */
    fun extractDownloadProgress(notification: LauncherNotification): String? {
        val pkg = notification.packageName.lowercase(Locale.ROOT)
        // Exclude calendar, email, chat, and collaboration apps
        if (excludedProgressPkgs.any { pkg.contains(it) }) {
            return null
        }

        val title = notification.title?.lowercase(Locale.ROOT) ?: ""
        val text = notification.text?.lowercase(Locale.ROOT) ?: ""
        val combined = "$pkg $title $text"

        // Exclude passive or completed notifications
        if (completedProgressKeywords.any { combined.contains(it) }) {
            return null
        }

        if (notification.progressMax > 0 && notification.progress >= 0) {
            val pct = ((notification.progress.toLong() * 100) / notification.progressMax).toInt()
            val icon = if (combined.contains("upload")) "📤" else "📥"
            return "Progress: $pct% $icon"
        }
        if (notification.progressIndeterminate) {
            val hasKeyword = progressKeywords.any { combined.contains(it) }
            if (hasKeyword) {
                val icon = if (combined.contains("upload")) "📤" else "📥"
                return "Progress: In Progress... $icon"
            }
        }

        if (combined.contains("downloading") || combined.contains("uploading") || combined.contains("installing") || combined.contains("updating")) {
            val match = progressPercentRegex.find(combined)
            if (match != null) {
                val icon = if (combined.contains("upload")) "📤" else "📥"
                return "Progress: ${match.groupValues[1]}% $icon"
            }
        }
        return null
    }

    fun parseTransactionDetails(
        notification: LauncherNotification,
        settings: SettingsState
    ): TransactionDetails? {
        val title = notification.title ?: ""
        val text = notification.text ?: ""
        // Bank / financial apps very commonly put the full transaction breakdown ONLY in bigText.
        // Fall back to sub/info text as well since some apps use those for the amount summary.
        val richBody = listOfNotNull(
            notification.bigText?.takeIf { it.isNotBlank() },
            notification.subText?.takeIf { it.isNotBlank() },
            notification.infoText?.takeIf { it.isNotBlank() },
            text.takeIf { it.isNotBlank() }
        ).joinToString("\n")
        val combined = "$title $richBody".lowercase(Locale.ROOT)
        val parsingSource = if (title.isNotEmpty() && richBody.isNotEmpty()) "$title\n$richBody" else title.ifEmpty { richBody }

        // Exclude system/settings packages from transaction alerts
        val pkg = notification.packageName.lowercase(Locale.ROOT)
        val isSystemOrSettings = pkg == "android" || pkg == "com.android.systemui" || 
                                 pkg == "com.android.settings" || pkg == "com.google.android.settings.intelligence" ||
                                 pkg.contains("safetyhub")
        if (isSystemOrSettings) {
            return null
        }

        // Exclude social, chat, messaging, email, browser, media, and reading/news apps from parsing transaction details
        // to prevent false positives from message contents containing financial words or values, UNLESS
        // the message exhibits a complete relational financial transaction structure (Verb + Amount + Account/Ref/Recipient).
        val isVerifiedSms = isVerifiedFinancialSms(
            parsingSource = parsingSource,
            combinedLower = combined,
            pkg = pkg,
            appName = notification.appName,
            title = title
        )

        if (excludedTransactionPkgs.any { pkg.contains(it) } && !isVerifiedSms) {
            return null
        }

        // Trust Android's own category flag: PROMO / RECOMMENDATION are never real transactions.
        val notifCat = notification.notificationCategory
        if (notifCat == "promo" || notifCat == "recommendation") {
            return null
        }

        // Exclude battery charging notifications using strict regex patterns to avoid blocking bank transactions
        if (batteryRegex.containsMatchIn(combined) || combined.contains("power saving mode")) {
            return null
        }

        // 1. Avoid OTP conflicts (run first check)
        if (otpTransactionExclusionRegex.containsMatchIn(combined)) {
            return null
        }

        // 1b. Security / fraud alerts should short-circuit ahead of promo/exclusion checks so
        //     that a suspicious-activity alert containing "please verify" is not filtered out
        //     by the "please" clause in nonTransactionAlertExclusionRegex.
        val looksLikeSecurityAlert = securityAlertKeywordsRegex.containsMatchIn(combined)

        // 2. Exclude Promotional, Informational and non-transaction alerts (strict filter)
        if (!looksLikeSecurityAlert && (
            promoTransactionExclusionRegex.containsMatchIn(combined) ||
            promoExclusionRegex.containsMatchIn(combined) ||
            nonTransactionAlertExclusionRegex.containsMatchIn(combined))
        ) {
            return null
        }

        // 3. Parse Amount & Currency early (Mandatory requirement)
        var parsedAmount: MoneyAmount? = null
        
        // Match with currency symbol/code (e.g. INR 500, AED 100, $10, € 10,50)
        var match = txAmountRegex.find(parsingSource)
        
        if (match == null) {
            // Fallback: match without currency, but filter out card suffix and date numbers
            val suffixMatch = txCardSuffixRegex.find(parsingSource)
            val cardSuffix = suffixMatch?.groupValues?.getOrNull(1)
            
            val allNumbers = allNumbersRegex.findAll(parsingSource)
            for (numMatch in allNumbers) {
                val valueStr = numMatch.groupValues[1]
                // Skip if it matches the card suffix
                if (cardSuffix != null && valueStr.endsWith(cardSuffix)) {
                    continue
                }
                
                // Ensure it is preceded by an amount indicator/transaction keyword
                val index = parsingSource.indexOf(valueStr)
                if (index != -1) {
                    val contextBefore = parsingSource.substring(0, index).lowercase(Locale.ROOT)
                    if (amountIndicatorKeywordsRegex.containsMatchIn(contextBefore)) {
                        match = numMatch
                        break
                    }
                }
            }
        }

        if (match != null) {
            val rawText = match.value
            val valueStr = match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]

            // Normalise number: handle US ($1,234.56), EU (€1.234,56), and Indian (₹1,00,000.50) formats.
            //   - Indian format is identified by the presence of at least one ,dd (two-digit)
            //     group followed by a final ,ddd (three-digit) group. This is required to
            //     avoid mis-treating "10,50" as an Indian-lakh number.
            //   - US format: comma is thousands, dot is decimal → strip commas.
            //   - EU format: dot is thousands, comma is decimal → strip dots, swap comma→dot.
            val hasBothSeparators = valueStr.contains(".") && valueStr.contains(",")
            val isIndianStyle = indianLakhRegex.matches(valueStr)
            val normalizedValueStr = when {
                isIndianStyle -> valueStr.replace(",", "")
                hasBothSeparators -> {
                    if (valueStr.indexOf(".") < valueStr.indexOf(",")) {
                        // EU: dot as thousands, comma as decimal.
                        valueStr.replace(".", "").replace(",", ".")
                    } else {
                        // US: comma as thousands, dot as decimal.
                        valueStr.replace(",", "")
                    }
                }
                valueStr.contains(",") -> {
                    val parts = valueStr.split(",")
                    when {
                        // "1,234" - thousands separator
                        parts.size == 2 && parts[1].length == 3 -> valueStr.replace(",", "")
                        // "10,50" - EU decimal (2 digits after single comma)
                        parts.size == 2 && parts[1].length in 1..2 -> valueStr.replace(",", ".")
                        // "1,234,567" - all thousands
                        parts.drop(1).all { it.length == 3 } -> valueStr.replace(",", "")
                        // Otherwise strip commas (Indian variants without trailing 3-digit)
                        else -> valueStr.replace(",", "")
                    }
                }
                else -> valueStr
            }

            var value = normalizedValueStr.toDoubleOrNull()
            if (value != null) {
                val rawValueIndex = rawText.indexOf(valueStr)
                val symbol = if (rawValueIndex > 0) {
                    rawText.substring(0, rawValueIndex).trim()
                } else if (rawValueIndex == 0 && rawText.length > valueStr.length) {
                    rawText.substring(valueStr.length).trim()
                } else {
                    ""
                }
                var fullRawText = rawText
                val matchEndIndex = match.range.last + 1
                if (matchEndIndex < parsingSource.length) {
                    val remainingText = parsingSource.substring(matchEndIndex)
                    val croreMatch = Regex("""(?i)^\s*(?:crores?|cr)\b""").find(remainingText)
                    val lakhMatch = Regex("""(?i)^\s*(?:lakhs?|lac|l)\b""").find(remainingText)
                    val thousandMatch = Regex("""(?i)^\s*k\b""").find(remainingText)
                    val millionMatch = Regex("""(?i)^\s*(?:millions?|m)\b""").find(remainingText)
                    when {
                        croreMatch != null -> {
                            value *= 10_000_000.0
                            fullRawText += croreMatch.value
                        }
                        lakhMatch != null -> {
                            value *= 100_000.0
                            fullRawText += lakhMatch.value
                        }
                        thousandMatch != null -> {
                            value *= 1_000.0
                            fullRawText += thousandMatch.value
                        }
                        millionMatch != null -> {
                            value *= 1_000_000.0
                            fullRawText += millionMatch.value
                        }
                    }
                }
                parsedAmount = MoneyAmount(value, symbol, fullRawText)
            }
        } else {
            val lakhsMatch = txLakhsRegex.find(parsingSource)
            if (lakhsMatch != null) {
                val valStr = lakhsMatch.groupValues[1]
                val rawText = lakhsMatch.value
                val numericVal = valStr.toDoubleOrNull()
                if (numericVal != null) {
                    val index = parsingSource.indexOf(rawText)
                    val symbol = if (index > 0) {
                        val textBefore = parsingSource.substring(0, index).trim()
                        val currMatch = Regex("""(?:[A-Z]{2,3}|Rs\.?|INR|₹|\$|€|£|USD|¥)\s*$""", RegexOption.IGNORE_CASE).find(textBefore)
                        currMatch?.value?.trim() ?: ""
                    } else {
                        ""
                    }
                    parsedAmount = MoneyAmount(numericVal * 100000.0, symbol, rawText)
                }
            }
        }

        if (parsedAmount != null) {
            val endIdx = parsingSource.indexOf(parsedAmount.rawText) + parsedAmount.rawText.length
            if (endIdx < parsingSource.length) {
                val remaining = parsingSource.substring(endIdx)
                if (nonFinancialUnitRegex.containsMatchIn(remaining)) {
                    parsedAmount = null
                }
            }
        }

        // Transactions MUST contain a valid amount greater than 0 - EXCEPT for security alerts,
        // which frequently have no amount ("New device signed in", "Verify this activity").
        if ((parsedAmount == null || parsedAmount.value <= 0.0) && !looksLikeSecurityAlert) {
            return null
        }

        // 4. Deterministic Combinatorial Trust Signal Verification
        val signalA = financialAppKeywordsRegex.containsMatchIn(pkg) || financialAppKeywordsRegex.containsMatchIn(notification.appName ?: "")
        val signalB = accountReferenceRegex.containsMatchIn(combined)
        val signalC = strictTransactionPhraseRegex.containsMatchIn(combined)

        // Security alerts only require the app to be trusted (Signal A). All other alert
        // types must have at least one of the three signals.
        if (looksLikeSecurityAlert) {
            if (!signalA) return null
        } else if (!signalA && !signalB && !signalC) {
            return null
        }

        // 5. Classify transaction alert category based on trusted keywords (word-boundary-aware).
        //    Precedence rationale:
        //      - Security alerts trump everything (safety-critical).
        //      - Failed / declined trumps everything else (blocks flow, needs user action).
        //      - Bill-generated is checked BEFORE bill-due so a "statement generated ... due <date>"
        //        message still surfaces as a due notice via the fallback.
        //      - Refund is checked BEFORE credit/debit since a refund message contains both.
        val alertType = when {
            securityAlertKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.SECURITY_ALERT
            }
            // Overdue & Failed (must be checked early as they can contain other transaction verbs)
            failedKeywordsRegex.containsMatchIn(combined) -> {
                if (combined.contains("declined")) TransactionAlertType.TRANSACTION_DECLINED else TransactionAlertType.PAYMENT_FAILED
            }
            // Bill Generated (statement ready) - checked before "bill due" so we can classify
            // credit-card statement notifications correctly.
            billGeneratedKeywordsRegex.containsMatchIn(combined) && !billDueKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.BILL_GENERATED
            }
            // Bill Due
            billDueKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.BILL_DUE
            }
            // EMI / Mandates
            emiDueKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.EMI_DUE
            }
            // Collect requests
            paymentRequestKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.PAYMENT_REQUEST
            }
            // Renewals
            renewalKeywordsRegex.containsMatchIn(combined) -> {
                if (combined.contains("trial")) TransactionAlertType.TRIAL_ENDING else TransactionAlertType.SUBSCRIPTION_RENEWAL
            }
            // Refund / Reversal - checked before debit/credit because "refund credited" and
            // "auto-debit reversal" would otherwise be misclassified.
            refundKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.REFUND
            }
            // Low Balance
            lowBalanceKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.LOW_BALANCE
            }
            // Outbound / Spent
            debitKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.DEBIT
            }
            // Inbound / Received
            creditKeywordsRegex.containsMatchIn(combined) -> {
                TransactionAlertType.CREDIT
            }
            else -> return null
        }

        // 6. Check if this specific subtype category is enabled in settings
        val settingKey = when (alertType) {
            TransactionAlertType.DEBIT, TransactionAlertType.CREDIT, TransactionAlertType.REFUND -> TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT
            TransactionAlertType.BILL_GENERATED, TransactionAlertType.BILL_DUE, TransactionAlertType.EMI_DUE, TransactionAlertType.PAYMENT_REQUEST -> TransactionSettingsKeys.SUBTYPE_GROUP_BILL
            TransactionAlertType.SUBSCRIPTION_RENEWAL, TransactionAlertType.TRIAL_ENDING -> TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL
            TransactionAlertType.PAYMENT_FAILED, TransactionAlertType.TRANSACTION_DECLINED, TransactionAlertType.LOW_BALANCE, TransactionAlertType.SECURITY_ALERT -> TransactionSettingsKeys.SUBTYPE_GROUP_FAILED
        }
        if (!settings.enabledTransactionSubtypes.contains(settingKey)) {
            return null
        }

        // 7. Parse Merchant / Biller
        var merchant: String? = null
        val prepRegex = if (alertType == TransactionAlertType.CREDIT || alertType == TransactionAlertType.REFUND) {
            merchantCreditRefundRegex
        } else {
            merchantDebitRegex
        }

        // Helper to validate merchant name
        fun isValidMerchant(name: String): Boolean {
            if (name.isBlank()) return false
            if (amountCheckRegex.containsMatchIn(name)) return false
            val lowerName = name.lowercase(Locale.ROOT)
            // Reject if the entire name is a generic infrastructure term (e.g. bare "bank", "upi").
            if (fullyExcludedMerchantKeywords.contains(lowerName)) return false
            val words = lowerName.split(Regex("\\s+"))
            // Reject if the first word is an action verb (e.g. "pay now", "login here").
            val firstWord = words.firstOrNull() ?: ""
            if (prefixExcludedMerchantKeywords.contains(firstWord)) return false
            return true
        }

        // Find the first valid merchant match using prepRegex
        val prepMatches = prepRegex.findAll(parsingSource)
        var validMatch = prepMatches.firstOrNull { matchResult ->
            val candidate = matchResult.groupValues[1].trim()
                .removeSuffix(".").removeSuffix(",").removeSuffix("-").trim()
            isValidMerchant(candidate)
        }

        // Fallback to UPI merchant handle (@UPI_MERCHANT) BEFORE weaker fallback prepositions
        if (validMatch == null) {
            val upiMatch = upiMerchantRegex.find(parsingSource)
            if (upiMatch != null) {
                val candidate = upiMatch.groupValues[1].trim()
                if (isValidMerchant(candidate)) {
                    merchant = candidate
                }
            }
        }

        // Fallback to txPrepositionRegex if no valid match found (for non-credit and non-refund transactions only)
        if (validMatch == null && merchant == null && alertType != TransactionAlertType.CREDIT && alertType != TransactionAlertType.REFUND) {
            val fallbackMatches = txPrepositionRegex.findAll(parsingSource)
            validMatch = fallbackMatches.firstOrNull { matchResult ->
                val candidate = matchResult.groupValues[1].trim()
                    .removeSuffix(".").removeSuffix(",").removeSuffix("-").trim()
                isValidMerchant(candidate)
            }
        }

        if (validMatch != null) {
            val rawMerchant = validMatch.groupValues[1].trim()
                .removeSuffix(".").removeSuffix(",").removeSuffix("-").trim()
            // Clean up trailing reference/account/card numbers ending the merchant name (4 or more digits)
            val cleanedRaw = rawMerchant.replace(merchantTrailingDigitsRegex, "").trim()
            merchant = cleanedRaw
            val cleanMerchant = when {
                merchant.contains("amzn", true) || merchant.contains("amazon", true) -> "Amazon"
                merchant.contains("netflix", true) -> "Netflix"
                merchant.contains("spotify", true) -> "Spotify"
                merchant.contains("youtube", true) -> "YouTube"
                merchant.contains("swiggy", true) -> "Swiggy"
                merchant.contains("zomato", true) -> "Zomato"
                merchant.contains("uber", true) -> "Uber"
                else -> merchant
            }
            merchant = cleanMerchant
        }

        // 8. Card/Account Suffix
        val suffixMatch = txCardSuffixRegex.find(parsingSource)
        val accountOrCardSuffix = suffixMatch?.groupValues?.getOrNull(1)

        // 9. Due Date
        val dateMatch = txDateRegex.find(parsingSource)
        val dueDate = dateMatch?.groupValues?.getOrNull(1)?.trim()

        return TransactionDetails(
            type = alertType,
            amount = parsedAmount,
            merchantOrBiller = merchant,
            dueDate = dueDate,
            accountSuffix = accountOrCardSuffix
        )
    }

    /**
     * Determines whether a notification is an upcoming meeting or call reminder.
     */
    fun isCalendarReminderNotification(notification: LauncherNotification, meetingAllowedApps: Set<String> = emptySet()): Boolean {
        val pkg = notification.packageName.lowercase(Locale.ROOT)
        val title = notification.title?.lowercase(Locale.ROOT) ?: ""
        val text = notification.text?.lowercase(Locale.ROOT) ?: ""
        val subText = notification.subText?.lowercase(Locale.ROOT) ?: ""
        val bigText = notification.bigText?.lowercase(Locale.ROOT) ?: ""
        val combined = "$title $text $subText $bigText"

        // 1. Must be from trusted calendar/reminder packages or collaboration apps or user-allowlisted meeting apps
        if (!calendarTrustPkgsRegex.containsMatchIn(pkg) && !meetingAllowedApps.contains(notification.packageName)) {
            return false
        }

        // 2. Exclude email threads starting with RE/FW prefixes across title, subText, or bigText
        val hasEmailPrefix = title.trim().startsWith("re:") || 
                             title.trim().startsWith("fw:") || 
                             title.trim().startsWith("fwd:") ||
                             subText.trim().startsWith("re:") ||
                             subText.trim().startsWith("fw:") ||
                             subText.trim().startsWith("fwd:") ||
                             bigText.trim().startsWith("re:") ||
                             bigText.trim().startsWith("fw:") ||
                             bigText.trim().startsWith("fwd:")
        if (hasEmailPrefix) {
            return false
        }

        // 3. Aggressive exclusion of generic messaging/chat/activity notifications
        if (calendarExclusionRegex.containsMatchIn(combined)) {
            return false
        }

        // 4. Strict inclusion keywords check
        val hasStrictKeyword = calendarStrictKeywordsRegex.containsMatchIn(combined)

        // 5. Scheduled context check
        val hasScheduledContext = scheduledContextKeywordsRegex.containsMatchIn(combined)

        val hasLink = extractMeetingLink(notification.text, notification.title) != null

        // For hybrid email/calendar packages (like Outlook or Exchange), we require a time/schedule context or join link
        // to filter out standard emails containing words like "review", "call", or "discussion".
        val isHybridPkg = pkg.contains("outlook") || pkg.contains("exchange")
        if (isHybridPkg) {
            val hasSchedule = extractScheduleInfo(notification.text) != null || extractScheduleInfo(notification.title) != null
            if (!hasLink && !hasSchedule && !hasScheduledContext) {
                return false
            }
        }

        return hasStrictKeyword || hasScheduledContext || hasLink
    }

    fun cleanCalendarTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        var cleaned = title.trim()
        
        // Remove email prefixes: "name@domain.com: "
        cleaned = cleaned.replace(otpPrefixRegex, "")
        
        // Remove forward/reply prefixes: "FW:", "RE:", "Fwd:", etc. (case-insensitive) repeatedly
        var lastCleaned: String
        do {
            lastCleaned = cleaned
            cleaned = cleaned.replace(calendarFwReRegex, "").trim()
        } while (cleaned != lastCleaned)
        
        return cleaned.trim()
    }

    fun extractScheduleInfo(text: String?): String? {
        if (text == null) return null
        val parts = text.split(scheduleSplitRegex).map { it.trim() }.filter { it.isNotEmpty() }
        
        for (part in parts) {
            val lowerPart = part.lowercase(Locale.ROOT)
            val hasTime = timeRegex.containsMatchIn(part)
            val hasDuration = durationRegex.containsMatchIn(lowerPart)
            val hasDay = dayKeywords.any { lowerPart.contains(it) }
            val hasMonth = monthKeywords.any { lowerPart.contains(it) }
            
            if (hasDuration || (hasTime && (hasDay || hasMonth))) {
                return cleanSchedulePart(part)
            }
        }
        
        if (parts.isNotEmpty()) {
            val firstPart = parts[0]
            if (firstPart.length < 50 && (timeRegex.containsMatchIn(firstPart) || dayKeywords.any { firstPart.lowercase(Locale.ROOT).contains(it) })) {
                return cleanSchedulePart(firstPart)
            }
        }
        return null
    }

    fun cleanSchedulePart(part: String): String {
        val noLink = part.replace(httpLinkRegex, "").trim()
        return noLink.removeSuffix(".").removeSuffix(",").trim()
    }

    fun extractMeetingLink(location: String?, description: String?): String? {
        val combined = "${location ?: ""} ${description ?: ""}"
        val match = meetingLinkRegex.find(combined)
        return match?.value
    }
}
