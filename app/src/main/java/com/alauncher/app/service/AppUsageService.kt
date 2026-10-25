package com.alauncher.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alauncher.app.MinimalSmartLauncherApp
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.ui.theme.MinimalSmartLauncherTheme
import com.alauncher.app.ui.utils.LauncherUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy

class AppUsageService : Service() {
    private lateinit var windowManager: WindowManager
    private var powerManager: android.os.PowerManager? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var currentTrackingPackage: String? = null
    @Volatile
    private var sessionStartTime = 0L
    @Volatile
    private var isOverlayShowing = false

    @Volatile
    private var settingsState = SettingsState()

    @Volatile
    private var lastKnownForegroundPackage: String? = null
    @Volatile
    private var lastPollTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Start as Foreground Service
        val channelId = "app_usage_channel"
        val channelName = "ALauncher App Usage Monitor Service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows that ALauncher screen time tracking is active"
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ALauncher Wellbeing is Active")
            .setContentText("ALauncher is monitoring your app usage limits.")
            .setSmallIcon(com.alauncher.app.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1002,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1002, notification)
        }

        // Startup guard: Check overlay permission and default launcher status after fulfilling Android 12+ foreground service contract
        if (!Settings.canDrawOverlays(this) || !LauncherUtils.isDefaultLauncher(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        overlayLifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        
        serviceScope.launch {
            lastPollTime = System.currentTimeMillis() - 5 * 60 * 1000L // 5 minutes ago to initialize
            withContext(Dispatchers.IO) {
                updateForegroundPackage()
            }
        }

        // Observe settings changes
        val app = applicationContext as? MinimalSmartLauncherApp
        val settingsRepo = app?.settingsRepository
        if (settingsRepo != null) {
            // Collector 1: Full state sync to keep themeMode and limits fresh
            serviceScope.launch {
                settingsRepo.settingsFlow.collectLatest { state ->
                    settingsState = state
                }
            }

            // Collector 2: Distinct lifecycle monitoring for stopping service when disabled
            serviceScope.launch {
                settingsRepo.settingsFlow
                    .distinctUntilChangedBy { Triple(it.appLimitsEnabled, it.appLimitsMap, it.appLimitsBreakDuration) }
                    .collectLatest { state ->
                        if (!state.appLimitsEnabled) {
                            dismissOverlay()
                            stopSelf()
                            return@collectLatest
                        }
                    }
            }
        }

        // Start polling coroutine on main thread (serviceScope uses Dispatchers.Main by default)
        serviceScope.launch {
            while (isActive) {
                val isInteractive = powerManager?.isInteractive ?: true
                if (!isInteractive) {
                    delay(5000) // Poll less frequently when device is screen-off/non-interactive to conserve battery
                    continue
                }
                delay(2500) // check every 2.5 seconds (balanced for battery and precision)
                if (!LauncherUtils.isDefaultLauncher(this@AppUsageService)) {
                    Log.d("AppUsageService", "ALauncher is no longer default launcher, stopping AppUsageService")
                    dismissOverlay()
                    stopSelf()
                    break
                }
                if (settingsState.appLimitsEnabled) {
                    val foregroundPkg = getForegroundPackageName()
                    checkForegroundApp(foregroundPkg)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || !LauncherUtils.isDefaultLauncher(this)) {
            dismissOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun checkForegroundApp(foregroundPkg: String?) {
        if (foregroundPkg == null) {
            if (currentTrackingPackage != null) {
                resetSession()
            }
            return
        }

        // If launcher is foreground app, reset session
        if (foregroundPkg == packageName) {
            resetSession()
            return
        }

        val limitsMap = settingsState.appLimitsMap
        val limitMinutes = limitsMap[foregroundPkg] ?: 0

        if (limitMinutes > 0) {
            if (currentTrackingPackage == foregroundPkg) {
                val elapsedMs = System.currentTimeMillis() - sessionStartTime
                val limitMs = limitMinutes * 60 * 1000L
                if (elapsedMs >= limitMs && !isOverlayShowing) {
                    isOverlayShowing = true
                    serviceScope.launch {
                        val appLabel = withContext(Dispatchers.IO) {
                            getAppName(this@AppUsageService, foregroundPkg)
                        }
                        if (currentTrackingPackage == foregroundPkg) {
                            showOverlay(appLabel)
                        } else {
                            isOverlayShowing = false
                        }
                    }
                }
            } else {
                currentTrackingPackage = foregroundPkg
                sessionStartTime = System.currentTimeMillis()
            }
        } else {
            resetSession()
        }
    }

    private fun resetSession() {
        currentTrackingPackage = null
        sessionStartTime = 0L
        dismissOverlay()
    }

    private suspend fun getForegroundPackageName(): String? = withContext(Dispatchers.IO) {
        if (powerManager != null && !powerManager!!.isInteractive) {
            return@withContext null
        }
        updateForegroundPackage()
        lastKnownForegroundPackage
    }

    private fun updateForegroundPackage() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()

        // Handle clock drift or time jumps (e.g. NTP updates)
        if (lastPollTime <= 0L || now < lastPollTime || (now - lastPollTime) > 10000) {
            lastPollTime = now - 5000
        }

        try {
            val usageEvents = usageStatsManager.queryEvents(lastPollTime, now)
            lastPollTime = now

            if (usageEvents != null) {
                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastKnownForegroundPackage = event.packageName
                    } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                        if (lastKnownForegroundPackage == event.packageName) {
                            lastKnownForegroundPackage = null
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("AppUsageService", "No usage stats permission", e)
        }

        // Fallback to queryUsageStats if lastKnownForegroundPackage is null
        if (lastKnownForegroundPackage == null) {
            try {
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
                if (!stats.isNullOrEmpty()) {
                    val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                    val topStat = sortedStats.firstOrNull()
                    if (topStat != null && (now - topStat.lastTimeUsed) < 10000) {
                        lastKnownForegroundPackage = topStat.packageName
                    }
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    private fun showOverlay(appLabel: String) {
        if (!Settings.canDrawOverlays(this)) {
            isOverlayShowing = false
            return
        }
        if (composeView != null) return
        isOverlayShowing = true
        val breakDuration = settingsState.appLimitsBreakDuration

        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

            setContent {
                MinimalSmartLauncherTheme(themeMode = settingsState.themeMode) {
                    TakeABreakOverlayContent(
                        appName = appLabel,
                        durationSeconds = breakDuration,
                        onFinished = {
                            dismissOverlay()
                            // Reset session start time to give another limit period
                            sessionStartTime = System.currentTimeMillis()
                        }
                    )
                }
            }
        }
        composeView = view

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e("AppUsageService", "Failed to add overlay view", e)
            try {
                view.disposeComposition()
            } catch (ex: Exception) {
                // Ignore disposal failure
            }
            composeView = null
            isOverlayShowing = false
        }
    }

    private fun dismissOverlay() {
        if (!isOverlayShowing && composeView == null) return
        isOverlayShowing = false
        val viewToRemove = composeView
        composeView = null
        viewToRemove?.let {
            try {
                it.disposeComposition()
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("AppUsageService", "Failed to remove overlay view", e)
            }
        }
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onDestroy() {
        dismissOverlay()
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

@Composable
fun TakeABreakOverlayContent(
    appName: String,
    durationSeconds: Int,
    onFinished: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(durationSeconds) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        onFinished()
    }

    // Swallow all touch events
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Take a break",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Are you sure you still want to spend your time in $appName?",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
            ) {
                val totalDuration = durationSeconds.coerceAtLeast(1).toFloat()
                CircularProgressIndicator(
                    progress = { (secondsLeft.toFloat() / totalDuration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Text(
                    text = "$secondsLeft",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
