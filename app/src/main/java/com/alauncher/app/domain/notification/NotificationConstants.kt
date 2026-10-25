package com.alauncher.app.domain.notification

object NotificationConstants {
    
    // --- OTP Patterns ---
    
    val OTP_STRICT_KEYWORDS_REGEX = Regex(
        """\b(?:otp|one[-\s]?time\s+(?:password|passcode)|verification\s+code|passcode|authentication\s+code|security\s+code|confirmation\s+code|access\s+code|sign[-\s]?in\s+code|login\s+code|authorization\s+code|two[-\s]?factor\s+(?:code|auth))\b""",
        RegexOption.IGNORE_CASE
    )

    // "code" and "pin" alone are intentionally excluded — they match promo codes, PIN numbers
    // on debit cards, etc. Only strict OTP-specific phrases or action-anchored PINs qualify as loose signals.
    val OTP_LOOSE_KEYWORDS_REGEX = Regex(
        """\b(?:otp|passcode|verification\s+code|one[-\s]?time\s+(?:password|passcode)|security\s+code|confirmation\s+code|login\s+code|sign[-\s]?in\s+code|(?:use|enter|share)\s+pin)\b""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 1: OTP keyword (including bare "code") immediately followed by separator + digits.
    // Bare "code" uses chained fixed-length negative lookbehinds to block promotional prefixes.
    // Variable-length lookbehinds are NOT supported by Android's java.util.regex engine and
    // would throw PatternSyntaxException at class-load time.
    val OTP_PATTERN_1 = Regex(
        """(?i)(?:otp|passcode|verification\s+code|one[-\s]?time\s+(?:password|passcode)|security\s+code|confirmation\s+code|access\s+code|login\s+code|authorization\s+code|(?<!promo\s)(?<!referral\s)(?<!coupon\s)(?<!discount\s)(?<!invite\s)(?<!offer\s)(?<!gift\s)code)(?:\s+[a-z0-9\-\.\/]+){0,8}?\s*(?:is|:|-|—|–|=)\s*(?:G-)?(?<!\d)(\d{4,8})(?!\d)"""
    )
    // Pattern 2: digits followed by "is your <otp-word>", including bare "code".
    // "123456 is your promo code" is excluded because "promo" precedes "code" — but
    // the negative lookahead here targets the word AFTER "your", so we instead rely on
    // the fact that promo messages say "is your promo code" (extra word before "code"),
    // which the {0,3} intervening-word group would still match. We accept this edge case
    // because the classifier's promo OS-category check and promoExclusionRegex handle it.
    val OTP_PATTERN_2 = Regex(
        """(?i)(?<!\d)(\d{4,8})(?!\d)\s+is\s+(?:your|the)\s+(?:[a-z]+\s+){0,3}(?:otp|passcode|verification\s+code|one[-\s]?time\s+(?:password|passcode)|security\s+code|confirmation\s+code|access\s+code|login\s+code|authorization\s+code|code)"""
    )
    // Pattern 3a: "use/enter <digits> to verify/complete/…".
    // Pattern 3b: "use/enter/share <otp-keyword> <digits>" — e.g. "Use OTP 3797 at Max Fashion", "Use PIN 0362".
    val OTP_PATTERN_3 = Regex(
        """(?i)(?:(?:use|enter)\s+(?:G-)?(?<!\d)(\d{4,8})(?!\d)\s+(?:to|for)\s+(?:verify|complete|log\s+in|sign\s+in|confirm|access)|(?:use|enter|share)\s+(?:otp|pin|passcode|code|verification\s+code|one[-\s]?time\s+(?:password|passcode)|security\s+code|confirmation\s+code|access\s+code|login\s+code|authorization\s+code)\s+(?:is|:|-)?\s*(?:G-)?(?<!\d)(\d{4,8})(?!\d))"""
    )

    // Alphanumeric OTP: full strict keyword set so "security code is AB12CD" and
    // "login code is ABCD1234" are covered, not just the original 4 keywords.
    val OTP_ALPHANUM_REGEX = Regex(
        """(?i)(?:otp|passcode|verification\s+code|one[-\s]?time\s+(?:password|passcode)|security\s+code|confirmation\s+code|access\s+code|login\s+code|authorization\s+code)\s*(?:is|:|-|—|–|=)+\s*([A-Z0-9]{6,8})\b"""
    )
    // Dashed OTP: full strict keyword set so "security code is 123-456" is covered.
    val OTP_DASHED_REGEX = Regex(
        """(?i)(?:otp|passcode|verification\s+code|one[-\s]?time\s+(?:password|passcode)|security\s+code|confirmation\s+code|access\s+code|login\s+code|authorization\s+code)\s*(?:is|:|-|—|–|=)*\s*(\d{3,4}[- ]\d{3,4})"""
    )
    val OTP_PATTERN_FALLBACK = Regex("""(?<!\d)(\d{4,6})(?!\d)""")

    // Fallback keyword scan: only strict OTP-specific phrases trigger digit search.
    // "code" and "pin" are intentionally excluded to avoid promo/coupon false positives.
    val OTP_KEYWORDS = listOf(
        "otp", "one-time password", "one time password", "one-time passcode", "one time passcode",
        "passcode", "verification code", "security code", "confirmation code",
        "login code", "sign-in code", "authorization code", "authentication code",
        // International OTP terms (Hindi, Spanish, French, German, Portuguese)
        "ओटीपी", "सत्यापन कोड", "प्रमाणन कोड",
        "código de verificación", "código de seguridad", "clave de verificación",
        "code de vérification", "code de sécurité",
        "bestätigungscode", "sicherheitscode",
        "código de verificação", "código de segurança"
    )
    // Context keywords used to validate action-side codes (RemoteInput / action titles).
    // This is a loose gate — false positives are acceptable here because the action code
    // itself is the high-precision signal. Include common confirmation terms so that
    // notifications like "Verify your account" or "Your code is ready" don't silently
    // discard a valid action code.
    val OTP_CONTEXT_KEYWORDS = listOf(
        "otp", "passcode", "verification", "one-time", "one time",
        "authentication", "authenticate", "sign-in code", "login code",
        "security code", "confirmation code",
        // Looser terms — safe here because action codes are app-declared, not regex-extracted.
        "code", "pin", "verify", "confirm", "login", "sign in", "activation"
    )

    // --- Transaction Patterns ---
    
    val TRANSACTION_VERBS_REGEX = Regex(
        """\b(?:debited|credited|spent|paid|charged|received|refunded|reversal|reversed|transferred|withdrawn|deposited|deducted|sent|statement\s+(?:ready|generated)|(?:amount|total|bill|payment|emi|minimum)\s+due|payable|outstanding\s+amount|renewal|renews|emi\s+due|mandate|auto-debit|auto-debited)\b""",
        RegexOption.IGNORE_CASE
    )
    
    val AMOUNT_REGEX = Regex(
        """(?:[\$€₹£¥]|\bRs\.?|\bINR\b|\bUSD\b|\bAED\b|\bEUR\b|\bGBP\b)\s*[0-9][0-9,\.]*""",
        RegexOption.IGNORE_CASE
    )
    
    // Matched against package name and app name only — NOT notification body text —
    // to avoid false positives from messages that mention bank names in passing.
    val FINANCIAL_TRUST_REGEX = Regex(
        """\b(?:bank|hdfc|icici|axis|sbi|chase|citi|barclays|hsbc|wells|capitalone|amex|discover|revolut|monzo|starling|paypal|venmo|zelle|cashapp|phonepe|gpay|paytm|cred|slice|jupiter|freecharge|mobikwik|razorpay|stripe|square|pluxee|zeta|sodexo)\b""",
        RegexOption.IGNORE_CASE
    )
    
    val ACCOUNT_REFERENCE_REGEX = Regex(
        """\b(?:a/c|acct|acc|account|card|wallet|credit\s+card|debit\s+card|laa|sba|loan\s+account|xx+\d*|ending\s+in|ending\s+with|ending\s+xxxx|xxxx\s*\d+|a/c\s+xxxx)\b""",
        RegexOption.IGNORE_CASE
    )
    
    val SECURITY_ALERT_KEYWORDS_REGEX = Regex(
        """\b(?:suspicious\s+(?:activity|login|transaction)|unauthori[sz]ed\s+(?:transaction|access|login)|unusual\s+(?:login|activity|transaction)|fraud\s+alert|security\s+alert|verify\s+this\s+transaction|was\s+this\s+you|new\s+device\s+logged\s+in|new\s+sign[- ]?in|password\s+changed|account\s+compromised|otp\s+shared|blocked\s+your\s+card|card\s+blocked)\b""",
        RegexOption.IGNORE_CASE
    )

    // --- Meeting Patterns ---
    
    // "session" removed — too generic ("Netflix session", "browser session").
    // "meet" alone removed — too generic ("meet our team", "meet the seller").
    // "call" alone removed — too generic ("call us", "missed call" from non-calendar apps).
    // These are still caught via SCHEDULE_KEYWORDS_REGEX + video platform combos.
    val MEETING_KEYWORDS_REGEX = Regex(
        """\b(?:meeting|standup|stand[-\u2011]?up|scrum|interview|1:1s?|1on1|1-on-1|1-1|office\s+hours|daily|weekly\s+sync|sync\s+(?:call|meeting|up)|review\s+meeting|kickoff|retrospective|planning\s+(?:meeting|session)|huddle|town\s+hall|all[-\s]?hands|reunión|reunion|会议|会議|회의|réunion)\b""",
        RegexOption.IGNORE_CASE
    )
    
    val MEETING_LINK_REGEX = Regex(
        """(?:https?://[^\s\"'>]*(?:teams\.microsoft\.com|teams\.live\.com|meet\.google\.com|zoom\.us|webex\.com|gotomeeting\.com|goto\.com/meeting|bluejeans\.com|chime\.aws|whereby\.com|jitsi\.org|8x8\.vc|meet\.jit\.si)[^\s\"'>]*)""",
        RegexOption.IGNORE_CASE
    )
    
    // "at" alone removed — it is one of the most common English prepositions and causes
    // false positives in delivery/transaction notifications ("paid at merchant", "delivered at door").
    val SCHEDULE_KEYWORDS_REGEX = Regex(
        """\b(?:starting\s+(?:now|in|at)|starts\s+in|starts\s+at|join\s+now|upcoming\s+(?:meeting|call|event)|scheduled\s+(?:for|at)|in\s+\d+\s*(?:min|mins|minutes|hr|hrs)|\d{1,2}:\d{2}\s*(?:am|pm))\b""",
        RegexOption.IGNORE_CASE
    )
    
    val CALENDAR_PKG_REGEX = Regex(
        """(?:calendar|outlook|exchange|google\.android\.calendar|samsung\.android\.calendar|teams|zoom|webex|meet\.google|bluejeans|jitsi|whereby|chime|gotomeeting)""",
        RegexOption.IGNORE_CASE
    )

    // --- OTP Expiry ---

    const val DEFAULT_OTP_MAX_AGE_MS = 5L * 60_000L

    // --- Delivery Patterns ---
    
    // "package" and "parcel" alone removed — too generic in non-delivery contexts.
    // "nearby" and "near you" alone removed — used in local-search/maps notifications.
    // "tracking" alone removed — used in fitness/analytics apps.
    // "arrived" alone removed — matches "flight arrived", "train arrived" (travel section).
    // Instead, "arrived" is allowed only when preceded by a delivery-context noun so that
    // "Your order has arrived" and "Package arrived" still match, but "flight arrived" does not.
    val DELIVERY_KEYWORDS_REGEX = Regex(
        """\b(?:out\s+for\s+delivery|arriving\s+(?:today|tomorrow|soon|in)|arrived\s+at|(?:order|package|parcel|shipment|your\s+\w+)\s+(?:has\s+)?arrived|delivered(?:\s+to)?|delivery\s+attempt(?:ed)?|attempt\s+successful|delivery\s+successful|shipped(?:\s+from)?|dispatched|on\s+the\s+way|on\s+its\s+way|driver\s+(?:is|has)|rider\s+(?:is|has)|order\s+(?:placed|confirmed|prepared|picked\s+up)|preparing\s+your\s+order|delivery\s+partner(?:\s+assigned)?|expected\s+(?:by|today|tomorrow)|package\s+(?:picked\s+up|shipped|in\s+transit|delivered)|parcel\s+(?:out|delivered|arrived))\b""",
        RegexOption.IGNORE_CASE
    )
    
    val RIDE_PKG_REGEX = Regex("""uber|ola|grab|lyft|rapido""", RegexOption.IGNORE_CASE)
    val FOOD_PKG_REGEX = Regex("""zomato|swiggy|doordash|ubereats|zepto|blinkit|grofers|dunzo|bigbasket|tataneu|tata\.neu|countrydelight""", RegexOption.IGNORE_CASE)

    /**
     * Superset regex containing all delivery, food, ride, and courier packages.
     * Note on Intentional Overlap: Food & quick-commerce packages (e.g. bigbasket, grofers/blinkit, zepto, swiggy)
     * are intentionally present in both COURIER_PKG_REGEX (for broad classifier scoring & allowlist checks)
     * and FOOD_PKG_REGEX (for food/grocery icon selection 🍔). Downstream consumers (like HomeViewModel)
     * evaluate isFoodPkg BEFORE isCourierPkg to preserve food/grocery icon priority.
     */
    val COURIER_PKG_REGEX = Regex(
        """(?:uber|ola|zomato|swiggy|blinkit|grofers|dunzo|bigbasket|zepto|tataneu|tata\.neu|countrydelight|doordash|grubhub|amzn\.mshop|mshop\.android|shop\.android\.shopping|flipkart|myntra|meesho|fedex|dhl|ups|bluedart|delhivery|rapido|lyft|grab|instashop|instacart|indiapost|dotzot|dtdc|spoton|gati|safexpress)""",
        RegexOption.IGNORE_CASE
    )
    
    // "arriving" alone removed — matches flight/train notifications handled by travel section.
    // "arrived" alone removed — same reason.
    val DELIVERY_PROGRESS_REGEX = Regex(
        """\b(?:eta\s*:\s*\d|reaching\s+(?:you|your)|delivering\s+(?:now|soon)|in\s+\d+\s*min(?:ute)?s?|\d+\s*min(?:ute)?s?\s+away|driver\s+\d+\s*min)\b""",
        RegexOption.IGNORE_CASE
    )

    // --- Travel Patterns ---
    val TRAVEL_PKG_REGEX = Regex("""irctc|flight|railone|whereismytrain|confirmtkt|ixigo|makemytrip|goibibo|cleartrip|yatra|indigo|airindia|vistara|akasa|spicejet|rail|train""", RegexOption.IGNORE_CASE)
    val TRAVEL_PROMO_REGEX = Regex(
        """\b(?:sale\s+(?:on|ends|starts|now)|flat\s+\d+%\s+off|up\s+to\s+\d+%\s+off|credit\s+card\s+offer|refer\s+(?:and|a)\s+(?:earn|friend)|reward\s+points?\s+offer|coupon\s+code|promo\s+code|discount|special\s+holiday\s+offer)\b""",
        RegexOption.IGNORE_CASE
    )

    // --- Promotional Patterns ---
    val PROMO_KEYWORD_REGEX = Regex(
        """\b(?:discount|coupon|cashback\s+offer|sale\s+(?:on|ends|starts)|deals?|win\s+(?:up\s+to|a|prizes)|voucher|promo(?:tion)?|membership\s+offer|flash\s+(?:hour|sale|deals?)|crack\s+code|secret\s+code|deal\s+code|coupon\s+code|promo\s+code|voucher\s+code|special\s+code|discount\s+code|instant\s+withdrawals?|ipo\s+alert|new\s+ipo|gift\s+nifty|(?:mega|huge|massive)\s+price\s+drop(?:ped)?|restock\s+(?:now|your|daily)|back\s+in\s+stock)\b""",
        RegexOption.IGNORE_CASE
    )

    val CONSOLIDATED_PROMOTIONAL_REGEX = Regex(
        """\bbuy\s+\d+\s+get\s+\d+(?:\s+free)?\b|""" +
        """\bbuy\s+one\s+get\s+one\b|""" +
        """\b(?:start|enjoy|happy)\s+shopping\b|""" +
        """\bshop\s+(?:now|online|today)\b|""" +
        """\border\s+(?:now|online|today)\b|""" +
        """\bcheck\s+it\s+out(?:\s+now)?\b|""" +
        """\bpocket[-\s]?friendly\b|""" +
        """\bstarts\s+(?:@|at)\s*(?:Rs\.?|INR|₹)?\s*\d+\b|""" +
        """\bends\s+at\s+\d+\b|""" +
        """\bjust\s+(?:Rs\.?|INR|₹)\s*\d+\b|""" +
        """\bat\s+just\s*(?:Rs\.?|INR|₹)?\s*\d+\b|""" +
        """\b(?:Rs\.?|INR|₹)\s*\d+\s*(?:deals?|loot|offer|store)\b|""" +
        """\b(?:shagun|glow[-\s]?up)\b|""" +
        """\b(?:beauty|fashion|mega|grand|clearance|festive|diwali|holi|new\s+year|monsoon|summer|winter)\s+sale\b|""" +
        """\bgrab\s+(?:your\s+)?(?:faves?|favorites?|deals?)\b|""" +
        """\bsteal\s+the\s+deals?\b|""" +
        """\bgrab\s+(?:[a-z0-9]+\s+){0,3}at\s+just\s+(?:Rs\.?|INR|₹)?\s*\d+\b|""" +
        """\bflash\s+(?:hour|sale|deals?)\b|""" +
        """\bcrack\s+code\b|""" +
        """\bhurry[,!]?\s+(?:shop|grab|buy|order|get|limited)\b|""" +
        """\b(?:double|triple)\s+the\s+(?:fun|deal|shopping|cashback)\b|""" +
        """\beverything\s+you\s+(?:need|want)\b|""" +
        """\buse\s+code\s+(?![0-9]{4,8}\b)[a-zA-Z0-9_-]+\b|""" +
        """\b(?:get|flat|save|upto)\s+(?:Rs\.?|INR|₹)?\s*\d+\s*(?:%|off|cashback)\b|""" +
        """\bfree\s+(?:gift|delivery|shipping|coupon)\b|""" +
        """\blimited\s+time\b|""" +
        """\bprime\s+offer\b|""" +
        """\bsuper\s+saver\b|""" +
        """\bfestival\s+sale\b|""" +
        """\b(?:is|are|now)\s+live\b|""" +
        """\b(?:mega|huge|massive)\s+price\s+drop(?:ped)?\b|""" +
        """\brestock\s+(?:now|your|daily)\b|""" +
        """\bback\s+in\s+stock\b|""" +
        """\bup\s?to\s+(?:Rs\.?|INR|₹|[\$€₹£¥])?\s*\d+(?:\.\d+)?\s*(?:lakhs?|lac|l|crores?|cr|k|m)?\b|""" +
        """\bsecured\s+(?:a\s+)?(?:Rs\.?|INR|₹|[\$€₹£¥])?\s*\d+(?:\.\d+)?\s*(?:lakhs?|lac|l|crores?|cr|k|m)?\s*(?:order|contract)\b|""" +
        """\btap\s+to\s+(?:try|check|view|open|explore)\b|""" +
        """\btry\s+now\b|""" +
        """\bprice\s+range\s*:\s*(?:Rs\.?|INR|₹|[\$€₹£¥])?\s*\d+\b|""" +
        """\blast\s+date\s+to\s+apply\b""",
        RegexOption.IGNORE_CASE
    )
}

