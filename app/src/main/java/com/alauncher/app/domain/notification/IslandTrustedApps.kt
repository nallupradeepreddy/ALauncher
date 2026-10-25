package com.alauncher.app.domain.notification

/**
 * Curated default allowlists of packages permitted to surface a given category on the
 * Contextual Island. These are conservative starting points — the user can add or remove
 * apps in Settings, and the persisted allowlist takes precedence over these defaults.
 *
 * Design notes:
 *  - OTP is intentionally NOT gated by an app allowlist: any app posting a well-formed OTP
 *    (verified by content parsing + trust signals) can bubble it up. This is because OTPs
 *    can arrive from arbitrary email/messaging clients we cannot exhaustively enumerate.
 *  - Transaction / Delivery / Meeting categories ARE gated by allowlist because the noise
 *    floor from arbitrary apps posting keyword-adjacent notifications (promo, news, etc.)
 *    is too high to auto-parse safely for now.
 *  - Even for allowlisted apps, content parsing is still required; being on the allowlist
 *    only gives the notification permission to attempt classification.
 */
object IslandTrustedApps {

    /**
     * Banking, wallet, UPI, credit-card, and payment apps whose notifications are trusted
     * to be parsed as transaction/bill/EMI/subscription/failed-payment events.
     */
    val DEFAULT_TRANSACTION_APPS: Set<String> = linkedSetOf(
        // UPI / Wallets - India
        "com.google.android.apps.nbu.paisa.user", // Google Pay India
        "com.phonepe.app",
        "net.one97.paytm",
        "com.amazon.mShop.upi",
        "in.org.npci.upiapp", // BHIM
        "com.mobikwik_new",
        "com.freecharge.android",

        // Cards / Credit / BNPL - India
        "com.cred.android",
        "com.slice.android",
        "com.uni.upipayments",
        "com.jupiterpay.android",

        // Banks - India
        "com.snapwork.hdfc", // HDFC Mobile Banking
        "com.hdfcbank.payzapp",
        "com.csam.icici.bank.imobile",
        "com.icicibank.pockets",
        "com.axis.mobile",
        "com.sbi.SBIFreedomPlus",
        "com.sbi.lotusintouch",
        "com.msf.kbank.mobile", // Kotak
        "com.infrasofttech.indianBank",
        "com.pnb.mobilebanking",
        "com.fss.pnbpsb",
        "com.yesbank",
        "com.rblbank.mobank",
        "com.idfcfirstbank.optimus",
        "com.fss.federalbankmb",
        "com.epifi.pep", // Fi Money
        "com.myairtelapp",

        // Global banks / payments
        "com.chase.sig.android",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.stripe.android",
        "com.wise.android",
        "com.revolut.revolut",
        "co.uk.getmondo", // Monzo
        "com.starlingbank.android",
        "com.discover.mobile.card",
        "com.americanexpress.android.acctsvcs.us",
        "com.wf.wellsfargomobile",
        "com.citi.mobile.wallet",
        "com.bankofamerica.digitalwallet",
        "com.onedebit.chime",
        "de.number26.android",
        "com.klarna.mobile",
        "com.afterpay.android",
        "com.affirm.android",

        // Corporate meal/benefits (India)
        "com.sodexo.zetauser",
        "com.pluxee.card",

        // SMS apps - many banks in India only push transaction alerts via SMS, so we allow
        // the default SMS handlers to have their notifications parsed for transactions too.
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    /**
     * Ride, food-delivery, grocery, and courier apps trusted for delivery/ride tracking.
     */
    val DEFAULT_DELIVERY_APPS: Set<String> = linkedSetOf(
        // Ride hailing
        "com.ubercab",
        "com.olacabs.customer",
        "com.grab.driver.app", "com.grabtaxi.passenger", "com.grabpay.passenger",
        "com.lyft.android",
        "com.rapidoOnly",
        "com.rapido.passenger",
        "sinet.startup.inDriver", // InDrive
        "com.careem.taxi",

        // Food delivery
        "com.application.zomato",
        "in.swiggy.android",
        "com.swiggy.android",
        "com.ubercab.eats",
        "com.dd.doordash",
        "com.grubhub.android",
        "com.hungerstation.android",
        "com.talabat",

        // Grocery / quick commerce
        "com.grofers.customerapp", // Blinkit
        "com.dunzo.user",
        "com.bigbasket.mobileapp",
        "com.zepto.consumer",
        "com.tataneu.consumer",
        "com.tatadigital.tataneu",
        "com.jiomart",
        "com.countrydelight.app",

        // E-commerce / courier
        "in.amazon.mShop.android.shopping",
        "com.amazon.mShop.android.shopping",
        "com.flipkart.android",
        "com.myntra.android",
        "com.nykaa.NykaaApp",
        "com.meesho.supply",
        "com.fedex.ida.android",
        "com.ups.mobile.android",
        "com.dhl.smartsend",
        "com.bluedart.mobile",
        "com.delhivery.driver", "com.delhivery.consumer",
        "com.aramex.customer"
    )

    /**
     * Calendar and video-conferencing apps trusted for meeting/call notifications.
     */
    val DEFAULT_MEETING_APPS: Set<String> = linkedSetOf(
        // Calendar
        "com.google.android.calendar",
        "com.samsung.android.calendar",
        "com.microsoft.office.outlook",
        "com.readdle.spark",
        "com.fantastical",

        // Email clients (used for Meet/Teams invites)
        "com.google.android.gm",

        // Video conferencing
        "com.microsoft.teams",
        "us.zoom.videomeetings",
        "com.cisco.webex.meetings",
        "com.google.android.apps.meetings", // Google Meet
        "com.skype.raider",
        "com.hangouts",
        "com.bluejeansnet.Base",
        "us.zoom.videomeetings.workplace"
    )
}
