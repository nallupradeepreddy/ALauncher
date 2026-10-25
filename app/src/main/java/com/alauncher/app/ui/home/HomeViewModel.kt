package com.alauncher.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alauncher.app.data.app.AppRepository
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.data.settings.isFocusModeActiveNow
import com.alauncher.app.data.settings.isQuietHoursActiveNow
import com.alauncher.app.data.settings.getQuietHoursMinutesLeft
import com.alauncher.app.data.settings.getQuietHoursMinutesSinceStart
import com.alauncher.app.data.settings.getQuietHoursMinutesUntilStart
import com.alauncher.app.data.notification.NotificationRepository
import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.SortMode
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.os.Environment
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothAssignedNumbers
import android.media.AudioManager
import android.media.AudioDeviceInfo
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.model.FilterCategory
import com.alauncher.app.model.hasContent
import com.alauncher.app.model.WHITESPACE_REGEX
import com.alauncher.app.data.calendar.CalendarEvent
import com.alauncher.app.data.calendar.CalendarRepository
import com.alauncher.app.domain.notification.NotificationParser
import com.alauncher.app.domain.notification.NotificationClassifier
import com.alauncher.app.domain.notification.NotificationIntent
import com.alauncher.app.domain.notification.NotificationConstants

enum class ContextualActionType {
    NONE,
    COPY_OTP,
    OPEN_LINK,
    LAUNCH_NOTIFICATION,
    DISMISS,
    OPEN_BLUETOOTH_SETTINGS,
    OPEN_HOTSPOT_SETTINGS
}

@Immutable
data class ContextualIsland(
    val text: String,
    val actionType: ContextualActionType = ContextualActionType.NONE,
    val actionPayload: String? = null,
    val showChronometer: Boolean = false,
    val chronometerCountDown: Boolean = false,
    val whenValue: Long = 0L,
    val type: ContextualIslandType = ContextualIslandType.GENERAL_NOTIFICATION,
    val packageName: String? = null,
    val transactionDetails: TransactionDetails? = null
)

@Stable
data class HomeState(
    val apps: List<LauncherApp> = emptyList(),
    val favorites: List<LauncherApp> = emptyList(),
    val showIcons: Boolean = true,
    val isFocusMode: Boolean = false,
    val sortMode: SortMode = SortMode.AlphabeticalAsc,
    val contextualIsland: ContextualIsland? = null,
    val databaseError: String? = null
)

@SuppressLint("MissingPermission")
class HomeViewModel(
    application: Application,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
    private val calendarRepository: CalendarRepository
) : AndroidViewModel(application) {

    @Suppress("StaticFieldLeak")
    private val context: Context = application.applicationContext

    companion object {
        internal fun getSnoozeDurationMs(type: ContextualIslandType): Long = when (type) {
            ContextualIslandType.RIDE_DELIVERY -> 5 * 60_000L
            ContextualIslandType.OTP -> 5 * 60_000L
            ContextualIslandType.HIGH_PRIORITY_TASK -> 10 * 60_000L
            ContextualIslandType.CRITICAL_ALERT -> 10 * 60_000L
            ContextualIslandType.BILL_TRANSACTIONAL -> 15 * 60_000L
            ContextualIslandType.AMBIENT_STATE, ContextualIslandType.AMBIENT_INFO -> 15 * 60_000L
            else -> 15 * 60_000L
        }

        // Default max age for OTP display. Most Indian/international OTPs are valid
        // 10–15 minutes; 15 min covers the common in-store / checkout delay scenario.
        // parseLiveTracker further extends this per-notification when the SMS body
        // explicitly states a longer validity (e.g. "valid for 10 minutes").
        internal val OTP_MAX_AGE_MS = NotificationConstants.DEFAULT_OTP_MAX_AGE_MS

        // Tight keyword list for NOTIFICATION text — generic words like "call", "meet",
        // "sync", "session" are excluded because they cause false positives in non-calendar
        // app notifications ("call us", "Netflix session", "sync contacts").
        private val notificationCallKeywords = listOf(
            "meeting", "interview", "sync call", "sync meeting", "1:1", "1on1", "1-on-1", "1-1",
            "zoom meeting", "zoom call", "teams meeting", "teams call",
            "webex meeting", "webex call", "google meet", "hangout", "skype call",
            "standup", "scrum", "town hall", "all-hands", "kickoff"
        )
        // Looser keyword list for CALENDAR EVENTS (database-backed, user-created titles).
        // Calendar events don't have spam noise, so common words like "call", "sync",
        // "review", "session" are safe to include here.
        private val calendarEventCallKeywords = listOf(
            "meeting", "meet", "call", "sync", "standup", "scrum", "interview", "session",
            "review", "discussion", "1:1", "1on1", "1-on-1", "1-1",
            "zoom", "teams", "webex", "google meet", "hangout", "skype",
            "town hall", "all-hands", "kickoff", "catch up", "catchup"
        )

        private val timeFormatter = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("h:mm a", Locale.getDefault())
            }
        }

        // "reward" removed: "Your delivery partner earned a reward" is a real delivery update.
        // "invite" removed: meeting invites from calendar apps contain this word.
        // "refer" removed: referral programs are promotional but this word also appears in
        //   legitimate notifications ("refer to your booking").
        private val promoKeywordRegex = NotificationConstants.PROMO_KEYWORD_REGEX
        private val consolidatedPromotionalRegex = NotificationConstants.CONSOLIDATED_PROMOTIONAL_REGEX

        private val ridePkgRegex = NotificationConstants.RIDE_PKG_REGEX
        private val foodPkgRegex = NotificationConstants.FOOD_PKG_REGEX
        private val courierPkgRegex = NotificationConstants.COURIER_PKG_REGEX

        private val strictStateRegex = Regex(
            """\b(?:delivered|completed|arriving|arrived|(?:driver|rider|delivery\s+partner)\s+(?:is\s+)?(?:nearby|near\s+you)|started|out\s+for\s+delivery)\b""",
            RegexOption.IGNORE_CASE
        )
        private val completedRegex = Regex("""\b(?:delivered|completed)\b""", RegexOption.IGNORE_CASE)

        // Bare "confirmed" removed from all three lists — it is too generic and matches
        // promotional messages like "Your 20% discount is confirmed".
        // Each list now uses phrase-anchored patterns that require delivery-specific context.
        private val activeRideKeywords = listOf(
            "ride confirmed", "booking confirmed", "driver assigned", "captain assigned",
            "driver is arriving", "arriving in", "driver has arrived", "captain has arrived",
            "trip started", "on your way", "ride started",
            "reached destination", "trip completed"
        )
        private val activeFoodKeywords = listOf(
            "order placed", "order confirmed", "booking confirmed", "restaurant accepted", "preparing your order",
            "cooking", "packed", "picked up", "delivery partner assigned",
            "delivery partner is on the way", "on the way", "heading your way",
            "arriving soon", "arriving in", "reached your location", "delivered"
        )
        private val activeCourierKeywords = listOf(
            "out for delivery", "arriving today", "arriving tomorrow", "delivery attempted",
            "delivery attempt", "delivery partner assigned", "package picked up", "package shipped",
            "in transit", "reached delivery station", "arrived at hub", "delivered",
            "delivery successful", "attempt successful"
        )

        private val travelPkgRegex = NotificationConstants.TRAVEL_PKG_REGEX
        private val travelPromoRegex = NotificationConstants.TRAVEL_PROMO_REGEX
        
        private val unambiguousFlightKeywords = listOf(
            "boarding pass", "web check-in", "boarding started", "boarding begins", 
            "flight delayed", "flight cancelled", "flight rescheduled", "departure gate", 
            "baggage belt", "flight status"
        )
        private val genericFlightKeywords = listOf("gate", "terminal", "security check")
        
        private val unambiguousTrainKeywords = listOf(
            "pnr", "train status", "berth", "chart prepared", "running status", "arriving at platform"
        )
        private val genericTrainKeywords = listOf("ticket confirmed", "platform", "coach", "seat", "delayed", "departed")

        private val chronometerKeywords = listOf("timer", "stopwatch", "countdown", "elapsed", "remaining", "break", "focus", "pomodoro")

        private val clockPkgs = setOf(
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.deskclock",
            "com.coloros.alarmclock",
            "com.oppo.alarmclock",
            "com.oneplus.deskclock",
            "com.xiaomi.calendar",
            "com.miui.calculator"
        )

        // Native dialer/telecom packages. Word-boundary regex on "phone" avoids matching
        // wallet apps like PhonePe. Trusted on isOngoing OR showChronometer.
        private val phonePkgRegex = Regex(
            """(?:^|\.)(?:dialer|incallui|telecom|truecaller|googlevoice)(?:$|\.)|\bcom\.android\.phone\b|\bphone\b""",
            RegexOption.IGNORE_CASE
        )
        // Messenger packages whose *call* notifications set showChronometer. Chat-only
        // notifications from these packages do NOT set showChronometer, so requiring it
        // prevents background-sync summaries from being elevated to "Active Call".
        private val messengerCallPkgs = setOf(
            "com.whatsapp",
            "org.thoughtcrime.securesms",
            "org.whispersystems.signalservice"
        )

        // WireGuard and other VPN clients use different phrasing — expanded to cover them.
        private val vpnInclusions = listOf(
            "vpn connected", "connected to vpn", "secure connection active", "private connection active",
            "tunnel active", "tunnel is up", "wireguard active", "openvpn connected",
            "protected by vpn", "vpn is on"
        )
        private val vpnExclusions = listOf(
            "vpn disconnected", "connection failed", "tap to connect", "vpn available",
            "tunnel is down", "wireguard disconnected"
        )

        // Chromecast shows "Casting to <device name>" — expanded to cover that pattern.
        private val castInclusions = listOf(
            "casting screen", "screen casting", "screen sharing", "recording screen",
            "screen recording", "sharing your screen", "cast connected",
            "casting to ", "casting audio"
        )
        private val castExclusions = listOf(
            "cast available", "nearby device found",
            "recording saved", "screen recording saved", "screen recording complete"
        )

        private val IPHONEACCEV_REGEX = Regex("""^\+IPHONEACCEV\s*=\s*\d{1,3}\s*,\s*1\s*,\s*(\d{1,3})""")
        private val PLANTRONICS_XEVENT_REGEX = Regex("""^\+XEVENT\s*=\s*BATTERY\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})""")

        private val getBatteryLevelMethod: java.lang.reflect.Method? by lazy {
            try { BluetoothDevice::class.java.getMethod("getBatteryLevel") } catch (e: Exception) { null }
        }
        private val isConnectedMethod: java.lang.reflect.Method? by lazy {
            try { BluetoothDevice::class.java.getMethod("isConnected") } catch (e: Exception) { null }
        }
        private val getMetadataMethod: java.lang.reflect.Method? by lazy {
            try { BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType) } catch (e: Exception) { null }
        }
        private val getWifiApStateMethod: java.lang.reflect.Method? by lazy {
            try { android.net.wifi.WifiManager::class.java.getMethod("getWifiApState") } catch (e: Exception) { null }
        }
    }

    data class TransactionRecord(val notificationKey: String, val timestamp: Long)

    private val parser = NotificationParser()
    private var _classifier: NotificationClassifier? = null
    private val classifier: NotificationClassifier
        get() {
            var c = _classifier
            if (c == null) {
                c = NotificationClassifier(parser)
                _classifier = c
            }
            return c
        }

    private val _copiedOtpKey = MutableStateFlow<String?>(null)
    private val copiedOtpKey = _copiedOtpKey.asStateFlow()
    private val shownTransactions = java.util.concurrent.ConcurrentHashMap<String, TransactionRecord>()

    // Key-based snooze: maps notification key -> snooze-until timestamp.
    // Replaced text-based dismissedIslandText which permanently suppressed islands
    // whose text matched a previously dismissed island (e.g. same OTP code re-shown).
    private val snoozedKeys = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _snoozeVersion = MutableStateFlow(0)
    // Nullable backing field + self-healing getter so unit tests that build the ViewModel
    // via sun.misc.Unsafe.allocateInstance (which skips field initializers) still see a
    // non-null set on first access. Volatile + synchronized give a safe DCL for concurrent
    // first-access from the notification pipeline and UI callbacks.
    @Volatile
    private var _dismissedTransientNotifKeys: MutableSet<String>? = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val dismissedTransientNotifKeys: MutableSet<String>
        get() {
            _dismissedTransientNotifKeys?.let { return it }
            return synchronized(this) {
                _dismissedTransientNotifKeys ?: java.util.concurrent.ConcurrentHashMap.newKeySet<String>().also {
                    _dismissedTransientNotifKeys = it
                }
            }
        }

    private val _islandRefreshTrigger = MutableStateFlow(0)

    private var otpExpirationJob: kotlinx.coroutines.Job? = null
    private var scheduledOtpKey: String? = null

    private fun scheduleOtpExpiration(key: String, delayMs: Long) {
        // Always cancel and reschedule — the same key may be re-posted with a different validity window.
        otpExpirationJob?.cancel()
        scheduledOtpKey = key
        otpExpirationJob = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            _islandRefreshTrigger.update { it + 1 }
            scheduledOtpKey = null
            otpExpirationJob = null
        }
    }

    fun onOtpCopied(key: String) {
        viewModelScope.launch {
            dismissedTransientNotifKeys.add(key)
            _copiedOtpKey.value = key
            notificationRepository.removeNotification(key)
            kotlinx.coroutines.delay(3000)
            if (_copiedOtpKey.value == key) {
                _copiedOtpKey.value = null
            }
        }
    }

    fun dismissCurrentIsland() {
        val island = islandState.value ?: return
        // Derive the snooze key using the same logic as isSnoozed() so dismiss and
        // snooze-check always operate on the same key.
        val key = island.actionPayload?.substringAfterLast(":::")
            ?: island.packageName
            ?: island.type.name

        // For notification-backed islands the actionPayload suffix IS the notification key.
        // Remove it from the repository so it never resurfaces after an explicit user dismiss.
        val notifKey = island.actionPayload?.substringAfterLast(":::")
        val isNotificationBacked = island.actionType == ContextualActionType.LAUNCH_NOTIFICATION ||
                island.actionType == ContextualActionType.COPY_OTP
        if (isNotificationBacked && notifKey != null && notifKey != island.packageName) {
            dismissedTransientNotifKeys.add(notifKey)
            notificationRepository.removeNotification(notifKey)
        }

        // Apply category-specific snooze durations (e.g. 5m for Rides/OTP, 10m for Tasks/Critical, 15m for Ambient/Bills)
        val snoozeDurationMs = getSnoozeDurationMs(island.type)

        val snoozeUntil = System.currentTimeMillis() + snoozeDurationMs
        snoozedKeys[key] = snoozeUntil
        _snoozeVersion.update { it + 1 }
        viewModelScope.launch {
            kotlinx.coroutines.delay(snoozeDurationMs)
            snoozedKeys.remove(key, snoozeUntil)
            _snoozeVersion.update { it + 1 }
        }
    }

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val isFastCharging: Boolean,
        val chargeTimeRemainingMs: Long,
        val temperatureCelsius: Float
    )

    private fun getBatteryInfo(context: Context, intent: Intent): BatteryInfo {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val isFastCharging = isCharging && (plugged == BatteryManager.BATTERY_PLUGGED_AC)
        
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempC = tempTenths / 10.0f
        
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        var chargeTime = if (isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager?.computeChargeTimeRemaining() ?: -1L
        } else {
            -1L
        }
        
        return BatteryInfo(pct, isCharging, isFastCharging, chargeTime, tempC)
    }

    private val batteryFlow: Flow<BatteryInfo?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    trySend(getBatteryInfo(c, intent))
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        val initialIntent = ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (initialIntent != null) {
            trySend(getBatteryInfo(context, initialIntent))
        }
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    private val dndFlow: Flow<Boolean> = callbackFlow {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                    val filter = notificationManager?.currentInterruptionFilter
                    val active = filter != null && filter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                    trySend(active)
                }
            }
        }
        val filter = IntentFilter(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        val initialFilter = notificationManager?.currentInterruptionFilter
        trySend(initialFilter != null && initialFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    private fun getStorageAlert(): String? {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalSpace = totalBlocks * blockSize
            val availableSpace = availableBlocks * blockSize
            val usedSpace = totalSpace - availableSpace
            val usedPct = if (totalSpace > 0) (usedSpace * 100 / totalSpace) else 0
            if (usedPct >= 90) {
                return "🗄️ Storage $usedPct% full • Clear files"
            }
        } catch (e: Exception) {}
        return null
    }

    private val storageFlow: Flow<String?> = flow {
        while (true) {
            emit(getStorageAlert())
            kotlinx.coroutines.delay(15 * 60 * 1000)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    private fun isBluetoothAudioConnected(audioManager: AudioManager?): Boolean {
        if (audioManager == null) return false
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            ))
        }
    }

    @get:SuppressLint("MissingPermission")
    private val bluetoothFlow: Flow<String?> = callbackFlow {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        var lastDevice: BluetoothDevice? = null
        var cachedBatteryLevel: Int = -1
        var transitionJob: kotlinx.coroutines.Job? = null
        var a2dpProfile: BluetoothProfile? = null
        var headsetProfile: BluetoothProfile? = null

        fun isPhoneModelName(name: String?): Boolean {
            if (name.isNullOrBlank()) return true
            val trimmed = name.trim()
            return trimmed.equals(Build.MODEL, ignoreCase = true) ||
                   trimmed.equals(Build.DEVICE, ignoreCase = true) ||
                   trimmed.equals(Build.PRODUCT, ignoreCase = true)
        }

        @SuppressLint("MissingPermission")
        fun resolveConnectedDevice(currentDevice: BluetoothDevice?): BluetoothDevice? {
            if (currentDevice != null) {
                val devName = try { currentDevice.name } catch (e: Exception) { null }
                if (!isPhoneModelName(devName)) {
                    return currentDevice
                }
            }

            // 1. Check active profile proxies (A2DP first, then Headset)
            try {
                val a2dpDevices = a2dpProfile?.connectedDevices
                val validA2dp = a2dpDevices?.firstOrNull { dev ->
                    val name = try { dev.name } catch (e: Exception) { null }
                    !isPhoneModelName(name)
                }
                if (validA2dp != null) return validA2dp

                val hsDevices = headsetProfile?.connectedDevices
                val validHs = hsDevices?.firstOrNull { dev ->
                    val name = try { dev.name } catch (e: Exception) { null }
                    !isPhoneModelName(name)
                }
                if (validHs != null) return validHs
            } catch (e: Exception) {}

            // 2. Match active AudioManager output product name with bonded devices
            try {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled && audioManager != null) {
                    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val activeAudioNames = devices.filter {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        ))
                    }.mapNotNull { it.productName?.toString() }.filter { !isPhoneModelName(it) }

                    if (activeAudioNames.isNotEmpty()) {
                        val matched = bluetoothAdapter.bondedDevices?.firstOrNull { bDev ->
                            val bName = try { bDev.name } catch (e: Exception) { null }
                            bName != null && activeAudioNames.any { active ->
                                bName.equals(active, ignoreCase = true) ||
                                bName.contains(active, ignoreCase = true) ||
                                active.contains(bName, ignoreCase = true)
                            }
                        }
                        if (matched != null) return matched
                    }
                }
            } catch (e: Exception) {}

            // 3. Query connected AudioDeviceInfo address (Android P+)
            if (audioManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    for (dev in devices) {
                        val isBtAudio = dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                            ))
                        if (isBtAudio) {
                            val addr = dev.address
                            if (!addr.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(addr)) {
                                val btDev = bluetoothAdapter?.getRemoteDevice(addr)
                                val name = try { btDev?.name } catch (e: Exception) { null }
                                if (btDev != null && !isPhoneModelName(name)) return btDev
                            }
                        }
                    }
                    for (dev in devices) {
                        if (dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            val prodName = dev.productName?.toString()
                            if (!isPhoneModelName(prodName)) {
                                val addr = dev.address
                                if (!addr.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(addr)) {
                                    val btDev = bluetoothAdapter?.getRemoteDevice(addr)
                                    if (btDev != null) return btDev
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            // 4. Fallback: Query bonded devices with active connection or valid battery level
            try {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                    val bonded = bluetoothAdapter.bondedDevices
                    if (!bonded.isNullOrEmpty()) {
                        for (dev in bonded) {
                            val name = try { dev.name } catch (e: Exception) { null }
                            if (!isPhoneModelName(name)) {
                                val bat = try {
                                    getBatteryLevelMethod?.invoke(dev) as? Int ?: -1
                                } catch (e: Exception) { -1 }
                                if (bat in 0..100) return dev
                            }
                        }
                        for (dev in bonded) {
                            val name = try { dev.name } catch (e: Exception) { null }
                            if (!isPhoneModelName(name)) {
                                val isConnected = try {
                                    isConnectedMethod?.invoke(dev) as? Boolean ?: false
                                } catch (e: Exception) { false }
                                if (isConnected) return dev
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            return null
        }

        @SuppressLint("MissingPermission")
        fun getConnectedAudioDeviceName(): String? {
            val dev = resolveConnectedDevice(lastDevice)
            val name = try {
                dev?.name
            } catch (e: SecurityException) {
                null
            }
            if (!name.isNullOrBlank() && !isPhoneModelName(name)) return name

            if (audioManager != null) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (d in devices) {
                    val isBtAudio = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                            d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        ))
                    if (isBtAudio) {
                        val prodName = d.productName?.toString()
                        if (!prodName.isNullOrBlank() && !isPhoneModelName(prodName)) return prodName
                    }
                }
                for (d in devices) {
                    if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        val prodName = d.productName?.toString()
                        if (!prodName.isNullOrBlank() && !isPhoneModelName(prodName)) return prodName
                    }
                }
            }
            return null
        }

        @SuppressLint("MissingPermission")
        fun extractBatteryLevel(dev: BluetoothDevice?, intent: Intent?): Int {
            if (intent != null) {
                val intentDev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try { intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) } catch (e: Exception) { null }
                } else {
                    @Suppress("DEPRECATION")
                    try { intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) } catch (e: Exception) { null }
                }
                if (intentDev != null) {
                    val addr = intentDev.address
                    if (!addr.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(addr)) {
                        val devName = try { intentDev.name } catch (e: Exception) { null }
                        if (!isPhoneModelName(devName) && lastDevice == null) {
                            lastDevice = intentDev
                        }
                    }
                }

                val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                if (level in 0..100) return level
                val extraLevel = intent.getIntExtra("EXTRA_BATTERY_LEVEL", -1)
                if (extraLevel in 0..100) return extraLevel

                val indId = intent.getIntExtra("android.bluetooth.headset.extra.HF_INDICATORS_IND_ID", -1)
                if (indId == 2) {
                    val indVal = intent.getIntExtra("android.bluetooth.headset.extra.HF_INDICATORS_IND_VALUE", -1)
                    if (indVal in 0..100) return indVal
                }

                // Apple/Vendor-specific AT command (+IPHONEACCEV)
                val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                if (!cmd.isNullOrBlank() && cmd.length in 1..128 && (cmd == "+IPHONEACCEV" || cmd.startsWith("+IPHONEACCEV"))) {
                    // Check if command string has args embedded: +IPHONEACCEV=1,1,9
                    val atMatch = IPHONEACCEV_REGEX.find(cmd)
                    if (atMatch != null) {
                        val rawVal = atMatch.groupValues[1].toIntOrNull()
                        if (rawVal != null) {
                            val bat = if (rawVal in 0..9) (rawVal + 1) * 10 else rawVal
                            if (bat in 0..100) return bat
                        }
                    }

                    val rawArgs = try {
                        val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getSerializableExtra(
                                BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS,
                                Array<Any?>::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS)
                        }
                        args ?: run {
                            @Suppress("DEPRECATION")
                            intent.extras?.get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS)
                        }
                    } catch (e: Exception) {
                        @Suppress("DEPRECATION")
                        try { intent.extras?.get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) } catch (e2: Exception) { null }
                    }

                    val argsList: List<Any?>? = when (rawArgs) {
                        is Array<*> -> rawArgs.toList()
                        is List<*> -> rawArgs
                        is IntArray -> rawArgs.toList()
                        else -> null
                    }

                    if (argsList != null && argsList.size >= 3) {
                        for (i in 1 until argsList.size step 2) {
                            val key = (argsList[i] as? Number)?.toInt() ?: (argsList[i] as? String)?.toIntOrNull()
                            if (key == 1 && i + 1 < argsList.size) {
                                val rawVal = (argsList[i + 1] as? Number)?.toInt() ?: (argsList[i + 1] as? String)?.toIntOrNull()
                                if (rawVal != null) {
                                    val bat = if (rawVal in 0..9) (rawVal + 1) * 10 else rawVal
                                    if (bat in 0..100) return bat
                                }
                            }
                        }
                    }
                }

                // Plantronics/Poly AT command (+XEVENT)
                if (!cmd.isNullOrBlank() && cmd.length in 1..128 && (cmd == "+XEVENT" || cmd.startsWith("+XEVENT"))) {
                    val atMatch = PLANTRONICS_XEVENT_REGEX.find(cmd)
                    if (atMatch != null) {
                        val cur = atMatch.groupValues[1].toIntOrNull()
                        val max = atMatch.groupValues[2].toIntOrNull()
                        if (cur != null && max != null && max > 0) {
                            val pct = (cur * 100) / max
                            if (pct in 0..100) return pct
                        }
                    }
                }

                for (k in intent.extras?.keySet() ?: emptySet()) {
                    if (k.contains("battery", ignoreCase = true) || k.contains("level", ignoreCase = true)) {
                        val v = intent.getIntExtra(k, -1)
                        if (v in 0..100) return v
                    }
                }
            }
            val targetDev = resolveConnectedDevice(dev)
            if (targetDev != null) {
                try {
                    val level = getBatteryLevelMethod?.invoke(targetDev) as? Int ?: -1
                    if (level in 0..100) return level
                } catch (e: Exception) {}
            }
            // Fast Pair / Android Bluetooth Device Metadata (API 30+)
            if (targetDev != null && Build.VERSION.SDK_INT >= 30) {
                try {
                    // 18 is BluetoothDevice.METADATA_MAIN_BATTERY
                    val metaBytes = getMetadataMethod?.invoke(targetDev, 18) as? ByteArray
                    if (metaBytes != null) {
                        val metaStr = String(metaBytes).trim()
                        val parsed = metaStr.toIntOrNull()
                        if (parsed != null && parsed in 0..100) return parsed
                    }
                } catch (e: Exception) {}
            }
            return -1
        }

        fun emitBatteryStage() {
            if (cachedBatteryLevel < 0) {
                val freshBattery = extractBatteryLevel(lastDevice, null)
                if (freshBattery in 0..100) {
                    cachedBatteryLevel = freshBattery
                }
            }
            val devName = getConnectedAudioDeviceName()
            val text = if (cachedBatteryLevel in 0..100) {
                if (cachedBatteryLevel <= 20) {
                    if (devName != null) "🪫 $devName • $cachedBatteryLevel% Battery Low"
                    else "🪫 Bluetooth • $cachedBatteryLevel% Battery Low"
                } else {
                    if (devName != null) "🔋 $devName • $cachedBatteryLevel%"
                    else "🔋 Bluetooth • $cachedBatteryLevel%"
                }
            } else {
                if (devName != null) "🎧 $devName"
                else "🎧 Bluetooth Connected"
            }
            trySend(text)
        }

        val profileListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) a2dpProfile = proxy
                if (profile == BluetoothProfile.HEADSET) headsetProfile = proxy

                try {
                    val connected = proxy.connectedDevices
                    if (!connected.isNullOrEmpty()) {
                        val d = connected.first()
                        lastDevice = d
                        val bat = extractBatteryLevel(d, null)
                        if (bat >= 0) cachedBatteryLevel = bat
                        if (isBluetoothAudioConnected(audioManager) && transitionJob?.isActive != true) {
                            emitBatteryStage()
                        }
                    }
                } catch (e: Exception) {}
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) a2dpProfile = null
                if (profile == BluetoothProfile.HEADSET) headsetProfile = null
            }
        }

        try {
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        } catch (e: Exception) {}

        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context, intent: Intent) {
                val action = intent.action
                val incomingDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try { intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) } catch (e: Exception) { null }
                } else {
                    @Suppress("DEPRECATION")
                    try { intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) } catch (e: Exception) { null }
                }
                if (incomingDevice != null) {
                    val addr = incomingDevice.address
                    if (!addr.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(addr)) {
                        val incomingName = try { incomingDevice.name } catch (e: Exception) { null }
                        if (!isPhoneModelName(incomingName)) {
                            lastDevice = incomingDevice
                        }
                    }
                }

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        transitionJob?.cancel()
                        val immediateBattery = extractBatteryLevel(lastDevice, intent)
                        if (immediateBattery in 0..100) {
                            cachedBatteryLevel = immediateBattery
                            emitBatteryStage()
                        } else {
                            val devName = getConnectedAudioDeviceName()
                            val connectText = if (devName != null) "🎧 Connected • $devName" else "🎧 Bluetooth Connected"
                            trySend(connectText)

                            transitionJob = launch {
                                kotlinx.coroutines.delay(1200)
                                var freshBat = extractBatteryLevel(lastDevice, null)
                                if (freshBat < 0) {
                                    kotlinx.coroutines.delay(1200)
                                    freshBat = extractBatteryLevel(lastDevice, null)
                                }
                                if (freshBat >= 0) {
                                    cachedBatteryLevel = freshBat
                                }
                                emitBatteryStage()
                            }
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        val isStillConnected = isBluetoothAudioConnected(audioManager)
                        if (!isStillConnected) {
                            transitionJob?.cancel()
                            lastDevice = null
                            cachedBatteryLevel = -1
                            trySend(null)
                        } else {
                            if (transitionJob?.isActive != true) {
                                emitBatteryStage()
                            }
                        }
                    }
                    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED",
                    "android.bluetooth.device.action.METADATA_CHANGED",
                    "android.bluetooth.headset.action.HF_INDICATORS_VALUE_CHANGED",
                    BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
                        val bat = extractBatteryLevel(lastDevice, intent)
                        if (bat in 0..100) {
                            cachedBatteryLevel = bat
                            transitionJob?.cancel()
                        }
                        if (isBluetoothAudioConnected(audioManager)) {
                            emitBatteryStage()
                        }
                    }
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            val dev = resolveConnectedDevice(lastDevice)
                            if (dev != null) lastDevice = dev
                            val bat = extractBatteryLevel(lastDevice, intent)
                            if (bat >= 0) {
                                cachedBatteryLevel = bat
                                transitionJob?.cancel()
                            }
                            if (isBluetoothAudioConnected(audioManager)) {
                                emitBatteryStage()
                            }
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            if (!isBluetoothAudioConnected(audioManager)) {
                                transitionJob?.cancel()
                                lastDevice = null
                                cachedBatteryLevel = -1
                                trySend(null)
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.device.action.METADATA_CHANGED")
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction("android.bluetooth.headset.action.HF_INDICATORS_VALUE_CHANGED")
            @Suppress("DEPRECATION")
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        val vendorFilter = IntentFilter(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT).apply {
            addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + BluetoothAssignedNumbers.APPLE)
            addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + BluetoothAssignedNumbers.PLANTRONICS)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(context, receiver, vendorFilter, ContextCompat.RECEIVER_EXPORTED)

        if (isBluetoothAudioConnected(audioManager)) {
            val battery = extractBatteryLevel(lastDevice, null)
            if (battery >= 0) cachedBatteryLevel = battery
            emitBatteryStage()
        } else {
            trySend(null)
        }

        awaitClose {
            transitionJob?.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
            try {
                if (a2dpProfile != null) bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile)
                if (headsetProfile != null) bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, headsetProfile)
            } catch (e: Exception) {}
        }
    }

    private var hotspotStartTime: Long = 0L

    private val hotspotFlow: Flow<Boolean> = callbackFlow {
        val updateHotspot = { active: Boolean ->
            if (active) {
                if (hotspotStartTime == 0L) hotspotStartTime = System.currentTimeMillis()
            } else {
                hotspotStartTime = 0L
            }
            trySend(active)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == "android.net.wifi.WIFI_AP_STATE_CHANGED") {
                    val state = intent.getIntExtra("wifi_state", -1)
                    updateHotspot(state == 13)
                }
            }
        }
        val filter = IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val initialActive = try {
            val state = getWifiApStateMethod?.invoke(wifiManager) as? Int ?: -1
            state == 13
        } catch (e: Exception) {
            false
        }
        updateHotspot(initialActive)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    private fun formatAmountValue(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", value)
        }
    }

    private fun cleanOldTransactions() {
        val now = System.currentTimeMillis()
        shownTransactions.entries.removeIf { now - it.value.timestamp > 10 * 60 * 1000 }
    }

    private fun parseTransactionAlert(
        notification: LauncherNotification,
        settings: com.alauncher.app.data.settings.SettingsState
    ): ContextualIsland? {
        val details = parser.parseTransactionDetails(notification, settings) ?: return null

        // Enforce amount verification in the UI layer for transactional alerts.
        // This prevents showing broken notifications (like "Spent:  at Merchant") when no amount is parsed.
        val requiresAmount = when (details.type) {
            TransactionAlertType.DEBIT,
            TransactionAlertType.CREDIT,
            TransactionAlertType.REFUND,
            TransactionAlertType.BILL_DUE,
            TransactionAlertType.EMI_DUE,
            TransactionAlertType.PAYMENT_REQUEST -> true
            else -> false
        }
        if (requiresAmount && details.amount == null) {
            return null
        }

        val amountVal = details.amount?.value ?: 0.0
        val merchantVal = details.merchantOrBiller ?: ""
        val suffixVal = details.accountSuffix ?: ""
        val timeBucket = System.currentTimeMillis() / (5 * 60 * 1000)
        val dedupeKey = "${details.type}:$amountVal:$merchantVal:$suffixVal:$timeBucket"

        cleanOldTransactions()
        val existing = shownTransactions[dedupeKey]
        if (existing != null && existing.notificationKey != notification.key) {
            return null
        }
        shownTransactions[dedupeKey] = TransactionRecord(notification.key, System.currentTimeMillis())

        val amountStr = details.amount?.let { formatAmountValue(it.value) } ?: ""

        val displayMsg = when (details.type) {
            TransactionAlertType.DEBIT -> {
                val formattedAmount = "${details.amount?.currencySymbol ?: ""}$amountStr"
                val formattedMerchant = details.merchantOrBiller
                if (!formattedMerchant.isNullOrBlank()) {
                    "💸 Spent: $formattedAmount at $formattedMerchant"
                } else {
                    "💸 Spent: $formattedAmount"
                }
            }
            TransactionAlertType.CREDIT -> {
                val formattedAmount = "${details.amount?.currencySymbol ?: ""}$amountStr"
                val formattedMerchant = details.merchantOrBiller
                val combined = "${notification.title ?: ""} ${notification.text ?: ""}".lowercase(Locale.ROOT)
                if (combined.contains("salary")) {
                    "💰 Salary credited: $formattedAmount"
                } else if (!formattedMerchant.isNullOrBlank()) {
                    "💰 Received: $formattedAmount from $formattedMerchant"
                } else {
                    "💰 Received: $formattedAmount"
                }
            }
            TransactionAlertType.REFUND -> {
                val formattedAmount = "${details.amount?.currencySymbol ?: ""}$amountStr"
                "↩️ Refund: $formattedAmount"
            }
            TransactionAlertType.BILL_DUE -> {
                val formattedAmount = "${details.amount?.currencySymbol ?: ""}$amountStr"
                val dateLabel = when {
                    details.dueDate == null -> ""
                    details.dueDate.contains("ago", ignoreCase = true) || details.dueDate.startsWith("in ", ignoreCase = true) -> " (${details.dueDate})"
                    details.dueDate.startsWith("on ", ignoreCase = true) || details.dueDate.startsWith("by ", ignoreCase = true) -> " ${details.dueDate}"
                    else -> " by ${details.dueDate}"
                }
                "📅 Bill due: $formattedAmount$dateLabel"
            }
            TransactionAlertType.SUBSCRIPTION_RENEWAL -> {
                val formattedMerchant = details.merchantOrBiller ?: "Subscription"
                val formattedAmount = if (details.amount != null) " (${details.amount.currencySymbol}$amountStr)" else ""
                val dateLabel = if (details.dueDate != null) " ${details.dueDate}" else " soon"
                "🔄 $formattedMerchant renews$formattedAmount$dateLabel"
            }
            TransactionAlertType.TRIAL_ENDING -> {
                val formattedMerchant = details.merchantOrBiller ?: "Service"
                val dateLabel = if (details.dueDate != null) " ${details.dueDate}" else " tomorrow"
                "⏳ Trial ends: $formattedMerchant$dateLabel"
            }
            TransactionAlertType.PAYMENT_FAILED -> {
                val formattedMerchant = details.merchantOrBiller ?: "Subscription"
                "❌ Payment failed: $formattedMerchant"
            }
            TransactionAlertType.TRANSACTION_DECLINED -> {
                val formattedAmount = if (details.amount != null) " of ${details.amount.currencySymbol}$amountStr" else ""
                "⚠️ Transaction declined$formattedAmount"
            }
            TransactionAlertType.LOW_BALANCE -> {
                val formattedAmount = if (details.amount != null) " (${details.amount.currencySymbol}$amountStr remaining)" else ""
                "⚠️ Low balance$formattedAmount"
            }
            TransactionAlertType.EMI_DUE -> {
                val formattedAmount = if (details.amount != null) " ${details.amount.currencySymbol}$amountStr" else ""
                val dateLabel = if (details.dueDate != null) " on ${details.dueDate}" else ""
                "🏦 EMI due:$formattedAmount$dateLabel"
            }
            TransactionAlertType.PAYMENT_REQUEST -> {
                val formattedAmount = if (details.amount != null) " ${details.amount.currencySymbol}$amountStr" else ""
                val formattedMerchant = details.merchantOrBiller ?: "Sender"
                "🙋 Payment request:$formattedAmount from $formattedMerchant"
            }
            TransactionAlertType.SECURITY_ALERT -> {
                val hasDetails = details.amount != null || details.merchantOrBiller != null
                if (hasDetails) {
                    val formattedAmount = if (details.amount != null) " of ${details.amount.currencySymbol}${formatAmountValue(details.amount.value)}" else ""
                    val formattedMerchant = if (details.merchantOrBiller != null) " at ${details.merchantOrBiller}" else ""
                    "🚨 Security alert: transaction$formattedAmount$formattedMerchant"
                } else {
                    "🚨 Security alert: ${notification.title ?: "Check your account"}"
                }
            }
            TransactionAlertType.BILL_GENERATED -> {
                val formattedAmount = if (details.amount != null) " ${details.amount.currencySymbol}$amountStr" else ""
                "📄 Statement ready$formattedAmount"
            }
        }

        return ContextualIsland(
            text = displayMsg,
            actionType = ContextualActionType.LAUNCH_NOTIFICATION,
            actionPayload = notification.key,
            type = ContextualIslandType.BILL_TRANSACTIONAL,
            packageName = notification.packageName,
            transactionDetails = details
        )
    }





    private fun isSystemStatusNotification(notification: LauncherNotification): Boolean {
        val pkg = notification.packageName.lowercase(Locale.ROOT)
        val title = notification.title?.lowercase(Locale.ROOT) ?: ""
        val text = notification.text?.lowercase(Locale.ROOT) ?: ""
        val combined = "$title $text"
        
        // VPN (Connected only, exclude disconnected/failed/available)
        if (combined.contains("vpn") || pkg.contains("vpn")) {
            if (vpnExclusions.any { combined.contains(it) }) {
                return false
            }
            return vpnInclusions.any { combined.contains(it) }
        }
        
        // Casting / Screen Recording / Sharing
        if (pkg.contains("com.android.systemui") || combined.contains("cast") || combined.contains("screen record") || combined.contains("screen share")) {
            if (castExclusions.any { combined.contains(it) }) {
                return false
            }
            return castInclusions.any { combined.contains(it) }
        }
        
        return false
    }

    private fun isCallRelated(title: String?, text: String?, isCalendarEvent: Boolean = false): Boolean {
        val combined = "${title ?: ""} ${text ?: ""}".lowercase(Locale.ROOT)
        val keywords = if (isCalendarEvent) calendarEventCallKeywords else notificationCallKeywords
        val hasKeyword = keywords.any { combined.contains(it) }
        val hasLink = extractMeetingLink(text, title) != null
        return hasKeyword || hasLink
    }

    private fun cleanCalendarTitle(title: String?): String {
        return parser.cleanCalendarTitle(title)
    }

    private fun extractScheduleInfo(text: String?): String? {
        return parser.extractScheduleInfo(text)
    }

    private fun cleanSchedulePart(part: String): String {
        return parser.cleanSchedulePart(part)
    }

    private fun extractMeetingLink(location: String?, description: String?): String? {
        return parser.extractMeetingLink(location, description)
    }

    private fun isCalendarReminderNotification(notification: LauncherNotification): Boolean {
        return parser.isCalendarReminderNotification(notification)
    }

    private fun isCalendarReminderNotification(notification: LauncherNotification, meetingAllowedApps: Set<String>): Boolean {
        return parser.isCalendarReminderNotification(notification, meetingAllowedApps)
    }

    private fun cleanNotificationText(title: String, text: String): String {
        val t = title.replace(WHITESPACE_REGEX, " ").trim()
        val m = text.replace(WHITESPACE_REGEX, " ").trim()
        val combined = when {
            t.isBlank() -> m
            m.isBlank() -> t
            m.startsWith(t, ignoreCase = true) -> m
            else -> "$t: $m"
        }
        return if (combined.length > 120) combined.take(120).trimEnd() + "…" else combined
    }

    private fun parseLiveTracker(notification: LauncherNotification, copiedOtpKey: String?): ContextualIsland? {
        return parseLiveTracker(notification, copiedOtpKey, com.alauncher.app.data.settings.SettingsState())
    }

    private fun parseLiveTracker(
        notification: LauncherNotification,
        copiedOtpKey: String?,
        settings: com.alauncher.app.data.settings.SettingsState
    ): ContextualIsland? {
        val pkg = notification.packageName.lowercase(Locale.ROOT)
        val title = notification.title ?: ""
        val text = notification.text ?: ""
        val cleanMsg = cleanNotificationText(title, text)

        // 1. OTP capture — highest priority, age-gated to 5 minutes.
        //    parser.extractOtp() internal logic already checks isMeetingInviteContext() to exclude
        //    meeting passcodes, so we do not drop authentication OTPs from meeting-app packages.
        val earlyClassification = classifier.classify(notification, settings)
        val otp = parser.extractOtp(notification)
        if (otp != null) {
            val now = System.currentTimeMillis()
            val age = now - notification.postTime
            // Use the validity window stated in the SMS body (e.g. "valid for 10 minutes"),
            // falling back to OTP_MAX_AGE_MS (5 min) when none is stated.
            val maxAgeMs = parser.extractOtpValidityMs(notification) ?: OTP_MAX_AGE_MS
            if (age in -30_000L..maxAgeMs) {
                if (copiedOtpKey == notification.key) {
                    return ContextualIsland(
                        text = "✅ OTP Copied",
                        actionType = ContextualActionType.NONE,
                        type = ContextualIslandType.OTP,
                        packageName = notification.packageName
                    )
                }
                val remainingDelay = (maxAgeMs - age).coerceAtLeast(0)
                scheduleOtpExpiration(notification.key, remainingDelay)
                return ContextualIsland(
                    text = "🔑 OTP Code: $otp",
                    actionType = ContextualActionType.COPY_OTP,
                    actionPayload = otp + ":::" + notification.key,
                    type = ContextualIslandType.OTP,
                    packageName = notification.packageName
                )
            }
            return null
        }

        // Run the scoring classifier once to establish the notification's primary intent
        // and apply per-category app allowlists. Reuse earlyClassification computed above.
        val classification = earlyClassification

        // 1.5. Transaction / Bill Alerts — allowlist gated by classifier; 60-minute TTL applied
        //      to point-in-time debit/credit alerts so stale receipts don't linger on the island.
        if ((classification.intent == NotificationIntent.TRANSACTION ||
             classification.intent == NotificationIntent.SECURITY_ALERT) &&
            settings.enabledIslandCategories.contains(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL)
        ) {
            val age = System.currentTimeMillis() - notification.postTime
            if (age <= 60 * 60 * 1000) {
                val transaction = parseTransactionAlert(notification, settings)
                if (transaction != null) {
                    return transaction
                }
            }
        }

        // 1.8. Severe Weather Alerts & Missed Emergency Calls (Escalated to Critical Alert)
        //      Emergency escalation is anchored on the OS-level CATEGORY_ALARM/CATEGORY_CALL tag
        //      plus the explicit "missed emergency call" / "sos" phrase — avoids elevating
        //      routine promo/test notifications from safety apps that merely contain "alert" text.
        val isSevereWeather = NotificationParser.severeWeatherAlertRegex.containsMatchIn(cleanMsg)
        val osCat = notification.notificationCategory
        val isAlarmCategory = osCat == android.app.Notification.CATEGORY_ALARM
        val isCallCat = osCat == android.app.Notification.CATEGORY_CALL
        val hasExplicitEmergencyPhrase = cleanMsg.contains("missed emergency call", ignoreCase = true) ||
                cleanMsg.contains("sos alert", ignoreCase = true) ||
                cleanMsg.contains("emergency sos", ignoreCase = true)
        val isMissedEmergencyCall = hasExplicitEmergencyPhrase ||
                (isAlarmCategory && pkg.contains("emergency")) ||
                (isCallCat && pkg.contains("emergency") && cleanMsg.contains("missed", ignoreCase = true))
        if (isSevereWeather || isMissedEmergencyCall) {
            return ContextualIsland(
                text = "🚨 $cleanMsg",
                actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                actionPayload = notification.key,
                type = ContextualIslandType.CRITICAL_ALERT,
                packageName = notification.packageName
            )
        }

        // 2. Download/Progress
        val progress = parser.extractDownloadProgress(notification)
        if (progress != null) {
            val age = System.currentTimeMillis() - notification.postTime
            val isCompleted = progress.contains("100%")
            if (isCompleted) {
                // Completed progress auto-expires after 5 minutes
                if (age > 5 * 60 * 1000) {
                    return null
                }
            } else if (!notification.isOngoing) {
                // Non-ongoing progress (stalled/paused) auto-expires after 15 minutes
                if (age > 15 * 60 * 1000) {
                    return null
                }
            }
            return ContextualIsland(
                text = progress,
                actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                actionPayload = notification.key,
                type = ContextualIslandType.HIGH_PRIORITY_TASK,
                packageName = notification.packageName
            )
        }

        // 3. Active Call / Chronometer (Timer / Stopwatch / Ongoing Calls)
        //    Native dialer/telecom packages are trusted on isOngoing; messenger packages
        //    (WhatsApp, Signal) additionally require showChronometer so background sync
        //    notifications don't get elevated as active calls.
        val isNativePhonePkg = phonePkgRegex.containsMatchIn(pkg)
        val isMessengerCallPkg = pkg in messengerCallPkgs
        val isCallCategory = notification.notificationCategory == android.app.Notification.CATEGORY_CALL ||
                (isNativePhonePkg && (notification.showChronometer || notification.isOngoing)) ||
                (isMessengerCallPkg && notification.showChronometer)
        if (isCallCategory) {
            val displayTitle = if (title.isNotBlank()) title else "Active Call"
            return ContextualIsland(
                text = "📞 Call: $displayTitle",
                actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                actionPayload = notification.key,
                showChronometer = notification.showChronometer,
                whenValue = notification.whenValue,
                type = ContextualIslandType.HIGH_PRIORITY_TASK,
                packageName = notification.packageName
            )
        }

        val isClockPkg = clockPkgs.contains(notification.packageName)
        if (notification.showChronometer || (isClockPkg && notification.isOngoing)) {
            val combined = "${title.lowercase(Locale.ROOT)} ${text.lowercase(Locale.ROOT)}"
            if (isClockPkg || chronometerKeywords.any { combined.contains(it) }) {
                val displayTitle = if (title.isNotBlank()) title else "Timer"
                return ContextualIsland(
                    text = "⏱️ $displayTitle",
                    actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                    actionPayload = notification.key,
                    showChronometer = true,
                    chronometerCountDown = notification.chronometerCountDown,
                    whenValue = notification.whenValue,
                    type = ContextualIslandType.HIGH_PRIORITY_TASK,
                    packageName = notification.packageName
                )
            }
        }

        // 4. System Status (VPN, Casting -> HIGH_PRIORITY_TASK as documented in README)
        if (isSystemStatusNotification(notification)) {
            if (!notification.isOngoing) {
                val age = System.currentTimeMillis() - notification.postTime
                if (age > 15 * 60 * 1000) {
                    return null
                }
            }
            val icon = if (cleanMsg.lowercase(Locale.ROOT).contains("vpn")) "🔒" else "📺"
            return ContextualIsland(
                text = "$icon $cleanMsg",
                actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                actionPayload = notification.key,
                type = ContextualIslandType.HIGH_PRIORITY_TASK,
                packageName = notification.packageName
            )
        }

        // 5. Rides & Deliveries / Couriers
        val isRidePkg = ridePkgRegex.containsMatchIn(pkg)
        // Removed hardcoded `text.contains("instamart")` fallback: use pkg-based detection only.
        // Swiggy Instamart is served from `in.swiggy.android` which foodPkgRegex already catches.
        val isFoodPkg = foodPkgRegex.containsMatchIn(pkg)
        val isCourierPkg = courierPkgRegex.containsMatchIn(pkg)
        val isAllowedDeliveryApp = settings.deliveryAllowedApps.contains(notification.packageName)

        if (isRidePkg || isFoodPkg || isCourierPkg || isAllowedDeliveryApp) {
            // Per-category allowlist gate: only allow delivery/ride notifications from
            // packages the user has trusted. OTP/Transaction/Security intents from the
            // same package are handled earlier and should not re-enter the delivery lane.
            if (classification.intent == NotificationIntent.OTP ||
                classification.intent == NotificationIntent.TRANSACTION ||
                classification.intent == NotificationIntent.SECURITY_ALERT
            ) {
                return null
            }
            if (!settings.deliveryAllowedApps.contains(notification.packageName)) {
                return null
            }

            val combined = "$title $text".lowercase(Locale.ROOT)
            
            // Promotional exclusion check using word-boundary-aware regex
            if (promoKeywordRegex.containsMatchIn(combined) || consolidatedPromotionalRegex.containsMatchIn(combined)) {
                return null
            }

            val isStrictState = strictStateRegex.containsMatchIn(combined)

            val matchesActive = isStrictState || when {
                isRidePkg -> activeRideKeywords.any { combined.contains(it) }
                isFoodPkg -> activeFoodKeywords.any { combined.contains(it) }
                isCourierPkg -> activeCourierKeywords.any { combined.contains(it) }
                else -> false
            }

            if (!matchesActive) {
                return null
            }

            if (!settings.liveTrackingEnabled && !isStrictState) {
                return null
            }

            val isCompletedState = completedRegex.containsMatchIn(combined)
            if (isCompletedState) {
                val age = System.currentTimeMillis() - notification.postTime
                if (age > 5 * 60 * 1000) {
                    return null
                }
            }

            val icon = when {
                isFoodPkg -> "🍔"
                isRidePkg -> "🚕"
                isCourierPkg -> "📦"
                else -> "📦"
            }
            return ContextualIsland(
                text = "$icon $cleanMsg",
                actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                actionPayload = notification.key,
                type = ContextualIslandType.RIDE_DELIVERY,
                packageName = notification.packageName
            )
        }

        // 6. Travel / Flight / Train ticket reminders
        val combinedMsg = "$title $text".lowercase(Locale.ROOT)
        val isTravelPkg = travelPkgRegex.containsMatchIn(pkg)
        
        val flightInclusions = if (isTravelPkg) {
            unambiguousFlightKeywords + genericFlightKeywords
        } else {
            unambiguousFlightKeywords
        }

        val trainInclusions = if (isTravelPkg) {
            unambiguousTrainKeywords + genericTrainKeywords
        } else {
            unambiguousTrainKeywords
        }

        val hasTravelKeyword = flightInclusions.any { combinedMsg.contains(it) } || trainInclusions.any { combinedMsg.contains(it) }

        if (isTravelPkg || hasTravelKeyword) {
            // Exclude promotions using word-boundary-aware regex
            if (travelPromoRegex.containsMatchIn(combinedMsg)) {
                return null
            }

            val isFlight = flightInclusions.any { combinedMsg.contains(it) } || pkg.contains("flight") || pkg.contains("indigo") || pkg.contains("airindia") || pkg.contains("vistara") || pkg.contains("akasa") || pkg.contains("spicejet")
            val isTrain = trainInclusions.any { combinedMsg.contains(it) } || pkg.contains("rail") || pkg.contains("train") || pkg.contains("irctc") || pkg.contains("whereismytrain") || pkg.contains("confirmtkt")

            if (isFlight || isTrain) {
                if (!notification.isOngoing) {
                    val age = System.currentTimeMillis() - notification.postTime
                    if (age > 15 * 60 * 1000) {
                        return null
                    }
                }
                val icon = if (isFlight) "✈️" else "🚆"
                return ContextualIsland(
                    text = "$icon $cleanMsg",
                    actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                    actionPayload = notification.key,
                    type = ContextualIslandType.GENERAL_NOTIFICATION,
                    packageName = notification.packageName
                )
            }
            return null
        }

        // 7. Calendar App Notifications (Google Calendar, Outlook)
        //    Gated by both the classifier (which applies the meeting allowlist) and the
        //    original isCalendarReminderNotification content check.
        val meetingAllowed = classification.intent == NotificationIntent.MEETING ||
            (classification.intent == NotificationIntent.NONE && !classification.blockedByAllowlist &&
             settings.meetingAllowedApps.contains(notification.packageName))
        if (meetingAllowed && isCalendarReminderNotification(notification, settings.meetingAllowedApps)) {
            // Check age to enforce T-30 to T+5 window for notifications
            val age = System.currentTimeMillis() - notification.postTime
            if (age > 35 * 60 * 1000) {
                return null
            }

            val cleanTitle = cleanCalendarTitle(title)
            val schedule = extractScheduleInfo(text)
            val displayMsg = if (!schedule.isNullOrBlank()) {
                "📅 Calendar: $cleanTitle • $schedule"
            } else {
                "📅 Calendar: $cleanTitle"
            }
            val link = extractMeetingLink(text, title)
            return if (link != null) {
                ContextualIsland(
                    text = displayMsg,
                    actionType = ContextualActionType.OPEN_LINK,
                    actionPayload = link,
                    type = ContextualIslandType.UPCOMING_MEETING,
                    packageName = notification.packageName
                )
            } else {
                ContextualIsland(
                    text = displayMsg,
                    actionType = ContextualActionType.LAUNCH_NOTIFICATION,
                    actionPayload = notification.key,
                    type = ContextualIslandType.UPCOMING_MEETING,
                    packageName = notification.packageName
                )
            }
        }

        return null
    }

    private fun resolveContextualIsland(
        battery: BatteryInfo?,
        activeNotifications: List<LauncherNotification>,
        isDnd: Boolean,
        storageWarning: String?,
        bluetoothText: String?,
        isHotspotActive: Boolean,
        calendarEvents: List<CalendarEvent>,
        settings: com.alauncher.app.data.settings.SettingsState,
        copiedOtpKey: String?,
        snoozedKeysMap: Map<String, Long> = emptyMap()
    ): ContextualIsland? {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<ContextualIsland>()

        // Ambient/system islands have no actionPayload and no packageName.
        // Notification-backed islands key by their notification key (actionPayload suffix).
        // Ambient islands key by their stable type name so that dynamic text (battery %, stash
        // count) doesn't break snooze matching when the display string changes between ticks.
        fun isSnoozed(island: ContextualIsland): Boolean {
            val key = island.actionPayload?.substringAfterLast(":::")
                ?: island.packageName
                ?: island.type.name
            return snoozedKeysMap.containsKey(key)
        }

        // Prune stale dismissed keys that are no longer present in activeNotifications
        dismissedTransientNotifKeys.retainAll(activeNotifications.map { it.key }.toSet())

        // Single-Pass active notification parsing, filtering out muted and snoozed alerts
        val parsedNotifications = activeNotifications
            .filter { notif ->
                notif.filterCategory != FilterCategory.MUTED && !snoozedKeysMap.containsKey(notif.key)
            }
            .sortedByDescending { it.postTime }
            .mapNotNull { notif ->
                parseLiveTracker(notif, copiedOtpKey, settings)
            }

        // 1. OTP Copied Status (Highest Priority)
        if (copiedOtpKey != null) {
            val copiedIsland = ContextualIsland(
                text = "✅ OTP Copied",
                actionType = ContextualActionType.NONE,
                type = ContextualIslandType.OTP
            )
            if (!isSnoozed(copiedIsland)) {
                candidates.add(copiedIsland)
            }
        }

        // 2. Active OTP Notifications (Only consider non-dismissed notification keys)
        val otpNotif = parsedNotifications.firstOrNull { it.type == ContextualIslandType.OTP }
        if (otpNotif != null && !isSnoozed(otpNotif)) {
            val notifKey = otpNotif.actionPayload?.substringAfterLast(":::")
            if (notifKey == null || !dismissedTransientNotifKeys.contains(notifKey)) {
                candidates.add(otpNotif)
            }
        }

        // 3. Critical Alerts
        if (battery != null) {
            val isHighTemp = (battery.temperatureCelsius >= 42.0f && battery.isCharging) || battery.temperatureCelsius >= 45.0f
            if (isHighTemp) {
                val hotIsland = ContextualIsland("🔥 Device running hot (${battery.temperatureCelsius.toInt()}°C)", type = ContextualIslandType.CRITICAL_ALERT, packageName = "__battery_hot__")
                if (!isSnoozed(hotIsland)) candidates.add(hotIsland)
            }
            if (battery.level <= 15 && !battery.isCharging) {
                val batIsland = ContextualIsland("⚠️ Low Battery (${battery.level}%) • Plug in", type = ContextualIslandType.CRITICAL_ALERT, packageName = "__battery_low__")
                if (!isSnoozed(batIsland)) candidates.add(batIsland)
            }
        }
        if (storageWarning != null) {
            val storageIsland = ContextualIsland(storageWarning, type = ContextualIslandType.CRITICAL_ALERT, packageName = "__storage__")
            if (!isSnoozed(storageIsland)) candidates.add(storageIsland)
        }

        // Calendar events bypass Focus Mode / Quiet Hours so meetings are never missed
        val callRelatedEvents = if (settings.calendarEnabled) {
            calendarEvents.filter { event ->
                isCallRelated(event.title, "${event.location ?: ""} ${event.description ?: ""}", isCalendarEvent = true)
            }
        } else {
            emptyList()
        }

        // Recent Active Upcoming Meeting Notifications (posted in the last 5 minutes)
        val activeMeetingNotif = activeNotifications.firstOrNull {
            it.filterCategory != FilterCategory.MUTED && isCalendarReminderNotification(it) && !snoozedKeysMap.containsKey(it.key)
        }
        val recentActiveMeetingIsland = if (activeMeetingNotif != null && (now - activeMeetingNotif.postTime) <= 5 * 60 * 1000) {
            parseLiveTracker(activeMeetingNotif, copiedOtpKey, settings)
        } else {
            null
        }

        if (recentActiveMeetingIsland != null && !isSnoozed(recentActiveMeetingIsland)) {
            candidates.add(recentActiveMeetingIsland)
        }

        // 4. Upcoming & In-Progress Meetings (database-backed, retained until endTime + 5m)
        if (settings.calendarEnabled) {
            val activeOrUpcoming = callRelatedEvents.firstOrNull { event ->
                !event.isAllDay && now >= (event.startTime - 30 * 60 * 1000) && now <= (event.endTime + 5 * 60 * 1000)
            }
            if (activeOrUpcoming != null) {
                val timeStr = timeFormatter.get()?.format(Date(activeOrUpcoming.startTime)) ?: ""
                val text = if (now >= activeOrUpcoming.startTime) {
                    "📅 In progress: ${activeOrUpcoming.title}"
                } else {
                    "📅 Up next: ${activeOrUpcoming.title} at $timeStr"
                }
                val link = extractMeetingLink(activeOrUpcoming.location, activeOrUpcoming.description)
                val actionType = if (link != null) ContextualActionType.OPEN_LINK else ContextualActionType.NONE
                val meetingIsland = ContextualIsland(text, actionType, link, type = ContextualIslandType.UPCOMING_MEETING, packageName = "__calendar_db__")
                if (!isSnoozed(meetingIsland)) candidates.add(meetingIsland)
            }
        }

        // 4.5. Older Active Upcoming Meeting Notifications (posted > 5 minutes ago)
        if (recentActiveMeetingIsland == null) {
            val upcomingMeetingNotif = parsedNotifications.firstOrNull { it.type == ContextualIslandType.UPCOMING_MEETING }
            if (upcomingMeetingNotif != null && !isSnoozed(upcomingMeetingNotif)) {
                candidates.add(upcomingMeetingNotif)
            }
        }

        // 5. Active High-Priority Tasks/Notifications (Progress, Chronometer, Active Call joins)
        val highPriorityNotif = parsedNotifications.firstOrNull { it.type == ContextualIslandType.HIGH_PRIORITY_TASK }
        if (highPriorityNotif != null && !isSnoozed(highPriorityNotif)) {
            candidates.add(highPriorityNotif)
        }

        // 6. Ride & Delivery Updates
        val rideDeliveryNotif = parsedNotifications.firstOrNull { it.type == ContextualIslandType.RIDE_DELIVERY }
        if (rideDeliveryNotif != null && !isSnoozed(rideDeliveryNotif)) {
            candidates.add(rideDeliveryNotif)
        }

        // 6.5. Bill & Transaction Alerts (Only consider non-dismissed notification keys)
        val transactionalNotif = parsedNotifications.firstOrNull { it.type == ContextualIslandType.BILL_TRANSACTIONAL }
        if (transactionalNotif != null && !isSnoozed(transactionalNotif)) {
            val notifKey = transactionalNotif.actionPayload?.substringAfterLast(":::")
            if (notifKey == null || !dismissedTransientNotifKeys.contains(notifKey)) {
                candidates.add(transactionalNotif)
            }
        }

        // 7. Ambient Information & Active Peripherals (Bluetooth Audio & Battery, Hotspot)
        if (bluetoothText != null) {
            val btIsland = ContextualIsland(
                text = bluetoothText,
                actionType = ContextualActionType.OPEN_BLUETOOTH_SETTINGS,
                type = ContextualIslandType.AMBIENT_INFO,
                packageName = "__bluetooth__"
            )
            if (!isSnoozed(btIsland)) candidates.add(btIsland)
        }

        if (isHotspotActive) {
            val elapsedMinutes = if (hotspotStartTime > 0L) {
                (now - hotspotStartTime) / 60000L
            } else 0L
            val hotspotText = if (elapsedMinutes >= 30L) {
                "🌐 Hotspot Active • ${elapsedMinutes}m elapsed"
            } else {
                "🌐 Hotspot Active"
            }
            val hotspotIsland = ContextualIsland(
                text = hotspotText,
                actionType = ContextualActionType.OPEN_HOTSPOT_SETTINGS,
                type = ContextualIslandType.AMBIENT_INFO,
                packageName = "__hotspot__"
            )
            if (!isSnoozed(hotspotIsland)) candidates.add(hotspotIsland)
        }

        // 8. Ambient System States (Charging, Focus Mode, DND)
        if (battery != null && battery.isCharging) {
            val chargingIsland = if (battery.level == 100) {
                ContextualIsland("✅ Battery Full • Unplug", type = ContextualIslandType.AMBIENT_STATE, packageName = "__battery_charging__")
            } else {
                val suffix = if (battery.chargeTimeRemainingMs > 0) {
                    val mins = (battery.chargeTimeRemainingMs / (1000 * 60))
                    if (mins > 0) " • Fully in ${mins}m" else ""
                } else {
                    ""
                }
                val text = if (battery.isFastCharging) "⚡ Fast Charging (${battery.level}%)$suffix"
                           else "🔋 Charging (${battery.level}%)$suffix"
                ContextualIsland(text, type = ContextualIslandType.AMBIENT_STATE, packageName = "__battery_charging__")
            }
            if (!isSnoozed(chargingIsland)) candidates.add(chargingIsland)
        }
        
        val stashedCount = activeNotifications.count { it.filterCategory == FilterCategory.MUTED && it.hasContent }

        if (settings.isFocusModeActiveNow(now)) {
            var focusAdded = false
            if (settings.focusEndTime > 0) {
                val remainingMs = settings.focusEndTime - now
                if (remainingMs > 0) {
                    val remainingMins = (remainingMs + 59999L) / 60000L
                    if (remainingMins <= 5) {
                        val focusIsland = ContextualIsland("🤫 Deep Work ending in ${remainingMins}m • Wrap up", type = ContextualIslandType.AMBIENT_STATE, packageName = "__focus_mode__")
                        if (!isSnoozed(focusIsland)) {
                            candidates.add(focusIsland)
                            focusAdded = true
                        }
                    }
                }
            }
            if (!focusAdded) {
                val title = when (stashedCount) {
                    0 -> "🤫 Focus Mode Active"
                    1 -> "🤫 Focus Mode Active • 1 notification stashed"
                    else -> "🤫 Focus Mode Active • $stashedCount notifications stashed"
                }
                val focusIsland = ContextualIsland(title, type = ContextualIslandType.AMBIENT_STATE, packageName = "__focus_mode__")
                if (!isSnoozed(focusIsland)) candidates.add(focusIsland)
            }
        }
        
        if (settings.quietHoursEnabled) {
            try {
                val isQuietActive = settings.isQuietHoursActiveNow(now)
                val quietIsland = if (isQuietActive) {
                    val minutesLeft = settings.getQuietHoursMinutesLeft(now)
                    val minutesSinceStart = settings.getQuietHoursMinutesSinceStart(now)
                    if (minutesSinceStart <= 5) {
                        ContextualIsland("🤫 Quiet Hours started • Time to wind down", type = ContextualIslandType.AMBIENT_STATE, packageName = "__quiet_hours__")
                    } else if (minutesLeft in 1..5) {
                        ContextualIsland("🤫 Quiet Hours ending in ${minutesLeft}m • Hello day", type = ContextualIslandType.AMBIENT_STATE, packageName = "__quiet_hours__")
                    } else {
                        val quietTitle = when (stashedCount) {
                            0 -> "🤫 Quiet Hours Active"
                            1 -> "🤫 Quiet Hours Active • 1 notification stashed"
                            else -> "🤫 Quiet Hours Active • $stashedCount notifications stashed"
                        }
                        ContextualIsland(quietTitle, type = ContextualIslandType.AMBIENT_STATE, packageName = "__quiet_hours__")
                    }
                } else {
                    val minutesUntilStart = settings.getQuietHoursMinutesUntilStart(now)
                    if (minutesUntilStart in 1..15) {
                        ContextualIsland("🤫 Quiet Hours starting in ${minutesUntilStart}m • Wind down", type = ContextualIslandType.AMBIENT_STATE, packageName = "__quiet_hours__")
                    } else null
                }
                if (quietIsland != null && !isSnoozed(quietIsland)) {
                    candidates.add(quietIsland)
                }
            } catch (e: Exception) {}
        }
        
        if (isDnd) {
            val dndIsland = ContextualIsland("🚫 Do Not Disturb Active", type = ContextualIslandType.AMBIENT_STATE, packageName = "__dnd__")
            if (!isSnoozed(dndIsland)) candidates.add(dndIsland)
        }

        // 9. Other Active Notifications (General Calendar alerts, travel, system status)
        val generalNotif = parsedNotifications.firstOrNull { it.type == ContextualIslandType.GENERAL_NOTIFICATION }
        if (generalNotif != null && !isSnoozed(generalNotif)) {
            candidates.add(generalNotif)
        }
        
        val allowedCandidates = candidates.filter { settings.enabledIslandCategories.contains(it.type.name) }
        return allowedCandidates.firstOrNull()
    }

    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(30000)
        }
    }

    /**
     * Optimized State Management:
     * Split uiState into sub-states to prevent unnecessary re-computations of the app list
     * when only the contextual island changes (e.g. battery updates).
     */
    @Suppress("UNCHECKED_CAST")
    internal fun transformIslandFlow(flows: Array<Any?>): ContextualIsland? {
        val battery = flows[0] as BatteryInfo?
        val activeNotifications = flows[1] as List<LauncherNotification>
        val settings = flows[2] as com.alauncher.app.data.settings.SettingsState
        val dnd = flows[3] as Boolean
        val storage = flows[4] as String?
        val bluetooth = flows[5] as String?
        val hotspot = flows[6] as Boolean
        val calendarEvents = flows[7] as List<CalendarEvent>
        val copiedOtpKey = flows[8] as? String

        // Auto-expire focus mode if needed
        val now = System.currentTimeMillis()
        if (settings.isFocusModeActive && settings.focusEndTime > 0 && now >= settings.focusEndTime) {
            viewModelScope.launch {
                settingsRepository.setFocusModeActive(false)
            }
        }

        // Clean up expired snooze entries lazily
        val now2 = System.currentTimeMillis()
        snoozedKeys.entries.removeIf { now2 >= it.value }

        return resolveContextualIsland(
            battery = battery,
            activeNotifications = activeNotifications,
            isDnd = dnd,
            storageWarning = storage,
            bluetoothText = bluetooth,
            isHotspotActive = hotspot,
            calendarEvents = calendarEvents,
            settings = settings,
            copiedOtpKey = copiedOtpKey,
            snoozedKeysMap = snoozedKeys
        )
    }

    private val islandFlow: Flow<ContextualIsland?> = combine(
        batteryFlow,
        notificationRepository.resolvedNotificationsFlow,
        settingsRepository.settingsFlow,
        dndFlow,
        storageFlow,
        bluetoothFlow,
        hotspotFlow,
        calendarRepository.calendarUpdatesFlow,
        _copiedOtpKey,
        _islandRefreshTrigger,
        _snoozeVersion,
        tickerFlow
    ) { flows: Array<Any?> ->
        transformIslandFlow(flows)
    }.distinctUntilChanged()

    val islandState: StateFlow<ContextualIsland?> = islandFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val enrichedAppsFlow: Flow<List<LauncherApp>> = combine(
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
    }.distinctUntilChanged()

    private val baseAppsState: StateFlow<List<LauncherApp>> = enrichedAppsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val homeState: StateFlow<HomeState> = combine(
        baseAppsState,
        settingsRepository.settingsFlow
    ) { enrichedApps, settings ->
        
        val isFocus = settings.isFocusModeActiveNow()
        val filteredApps = enrichedApps.filter { app ->
            if (app.isHidden) return@filter false
            if (isFocus) {
                if (settings.homeAppsConfigured) {
                    settings.homeApps.contains(app.packageName)
                } else {
                    app.isFavorite
                }
            } else {
                true
            }
        }

        val sorted = sortAppsList(filteredApps, settings.sortMode)
        val favorites = if (settings.homeAppsConfigured) {
            settings.homeApps.mapNotNull { pkg ->
                enrichedApps.find { it.packageName == pkg }
            }.take(10)
        } else {
            enrichedApps.filter { it.isFavorite }.take(10)
        }

        HomeState(
            apps = sorted,
            favorites = favorites,
            showIcons = settings.showIcons,
            isFocusMode = isFocus,
            sortMode = settings.sortMode,
            contextualIsland = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeState()
    )

    val databaseError: StateFlow<String?> = appRepository.databaseError

    val uiState: StateFlow<HomeState> = combine(
        homeState,
        islandState,
        databaseError
    ) { home, island, dbError ->
        home.copy(contextualIsland = island, databaseError = dbError)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeState()
    )

    private fun sortAppsList(list: List<LauncherApp>, mode: SortMode): List<LauncherApp> {
        return when (mode) {
            SortMode.AlphabeticalAsc -> list.sortedBy { it.normalizedLabel }
            SortMode.AlphabeticalDesc -> list.sortedByDescending { it.normalizedLabel }
            SortMode.FavoritesFirst -> list.sortedWith(compareByDescending<LauncherApp> { it.isFavorite }.thenBy { it.normalizedLabel })
            SortMode.MostUsed -> list.sortedByDescending { it.launchCount }
            SortMode.RecentlyOpened -> list.sortedByDescending { it.lastOpenedTime }
        }
    }

    fun launchApp(app: LauncherApp) {
        viewModelScope.launch {
            appRepository.recordAppLaunch(app.packageName)
        }
    }

    private val favoriteMutex = Mutex()

    fun toggleFavorite(app: LauncherApp) {
        viewModelScope.launch {
            favoriteMutex.withLock {
                val settings = settingsRepository.settingsFlow.first()
                val enrichedApps = withTimeoutOrNull(2000L) {
                    appRepository.rawAppsFlow.first { it.isNotEmpty() }
                } ?: appRepository.rawAppsFlow.first()
                val installedPkgs = enrichedApps.map { it.packageName }.toSet()

                val currentList = if (settings.homeAppsConfigured) {
                    settings.homeApps.filter { it in installedPkgs }
                } else {
                    enrichedApps.filter { it.isFavorite }.map { it.packageName }
                }
                val isCurrentlyFavorite = currentList.contains(app.packageName)
                val isFav = !isCurrentlyFavorite
                
                appRepository.toggleFavorite(app.packageName, isFav)
                
                val updated = currentList.toMutableList()
                if (isFav) {
                    if (!updated.contains(app.packageName)) {
                        updated.add(app.packageName)
                    }
                } else {
                    updated.remove(app.packageName)
                }
                settingsRepository.setHomeApps(updated)
            }
        }
    }

    fun toggleHidden(app: LauncherApp) {
        viewModelScope.launch {
            appRepository.toggleHidden(app.packageName, !app.isHidden)
        }
    }

    fun launchNotificationByKey(key: String) {
        viewModelScope.launch {
            val success = notificationRepository.launchNotification(key, context)
            if (success) {
                com.alauncher.app.service.NotificationListener.cancelNotification(key)
            }
        }
    }



    override fun onCleared() {
        super.onCleared()
        shownTransactions.clear()
    }

    class Factory(
        private val app: Application,
        private val appRepo: AppRepository,
        private val settingsRepo: SettingsRepository,
        private val notifRepo: NotificationRepository,
        private val calendarRepo: com.alauncher.app.data.calendar.CalendarRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(app, appRepo, settingsRepo, notifRepo, calendarRepo) as T
        }
    }
}
