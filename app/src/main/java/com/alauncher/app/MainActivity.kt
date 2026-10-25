package com.alauncher.app

import com.alauncher.app.service.NotificationListener
import com.alauncher.app.ui.utils.isNotificationAccessGranted
import com.alauncher.app.ui.utils.LauncherUtils
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.alauncher.app.ui.home.ContextualActionType
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.ui.res.stringResource
import com.alauncher.app.R
import com.alauncher.app.ui.components.AppDialogHeader
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alauncher.app.model.ThemeMode
import com.alauncher.app.model.HomeAlignment
import com.alauncher.app.receiver.PackageChangeReceiver
import com.alauncher.app.ui.commandpalette.CommandPaletteScreen
import com.alauncher.app.ui.commandpalette.CommandPaletteViewModel
import com.alauncher.app.ui.home.LauncherHomeScreen
import com.alauncher.app.ui.home.HomeViewModel
import com.alauncher.app.ui.notificationcenter.NotificationCenterScreen
import com.alauncher.app.ui.notificationcenter.NotificationCenterViewModel
import com.alauncher.app.ui.settings.SettingsScreen
import com.alauncher.app.ui.main.LauncherAppScaffold
import com.alauncher.app.ui.settings.SettingsViewModel
import com.alauncher.app.ui.theme.MinimalSmartLauncherTheme
import com.alauncher.app.ui.theme.isLight
import com.alauncher.app.data.settings.isFocusModeActiveNow
import com.alauncher.app.ui.utils.InAppReviewHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay

enum class ActiveScreen {
    Home,
    NotificationCenter,
    CommandPalette,
    Settings,
    Permissions
}

class MainActivity : ComponentActivity() {

    private val app: MinimalSmartLauncherApp
        get() = application as MinimalSmartLauncherApp

    // Asynchronously bound lazy ViewModels using custom manual factories
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(
            app = app,
            appRepo = app.appRepository,
            settingsRepo = app.settingsRepository,
            notifRepo = app.notificationRepository,
            calendarRepo = app.calendarRepository
        )
    }

    private val notificationViewModel: NotificationCenterViewModel by viewModels {
        NotificationCenterViewModel.Factory(
            notifRepo = app.notificationRepository,
            settingsRepo = app.settingsRepository
        )
    }

    private val commandViewModel: CommandPaletteViewModel by viewModels {
        CommandPaletteViewModel.Factory(
            appRepo = app.appRepository,
            settingsRepo = app.settingsRepository,
            notifRepo = app.notificationRepository
        )
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(
            settingsRepo = app.settingsRepository,
            notifRepo = app.notificationRepository,
            appRepo = app.appRepository
        )
    }



    private lateinit var packageReceiver: PackageChangeReceiver
    private val isLaunchingState = mutableStateOf(false)
    private var activeScreen by mutableStateOf(ActiveScreen.Home)
    private var isRequestingDefaultLauncher = false
    private var reviewJob: Job? = null

    override fun onResume() {
        super.onResume()
        isLaunchingState.value = false
        NotificationListener.requestRebindIfDisconnected(this)
        val isDefault = com.alauncher.app.ui.utils.LauncherUtils.isDefaultLauncher(this)
        val hasOverlay = android.provider.Settings.canDrawOverlays(this)
        lifecycleScope.launch {
            val settings = app.settingsRepository.settingsFlow.first()
            if (settings.isFocusModeActive && settings.focusEndTime > 0 && System.currentTimeMillis() >= settings.focusEndTime) {
                app.settingsRepository.setFocusModeActive(false)
            }
            if (isDefault) {
                if (settings.thumbDockEnabled && hasOverlay) {
                    try {
                        startForegroundService(Intent(this@MainActivity, com.alauncher.app.service.ThumbDockService::class.java))
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                val hasUsage = settingsViewModel.hasUsageStatsPermission(this@MainActivity)
                if (settings.appLimitsEnabled && hasUsage && hasOverlay) {
                    try {
                        startForegroundService(Intent(this@MainActivity, com.alauncher.app.service.AppUsageService::class.java))
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } else {
                try {
                    stopService(Intent(this@MainActivity, com.alauncher.app.service.ThumbDockService::class.java))
                } catch (e: Exception) {}
                try {
                    stopService(Intent(this@MainActivity, com.alauncher.app.service.AppUsageService::class.java))
                } catch (e: Exception) {}
            }
        }
        reviewJob?.cancel()
        if (activeScreen == ActiveScreen.Home && !isRequestingDefaultLauncher && isDefault) {
            reviewJob = lifecycleScope.launch {
                delay(1500)
                if (activeScreen == ActiveScreen.Home && !isLaunchingState.value && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    val latestSettings = app.settingsRepository.settingsFlow.first()
                    InAppReviewHelper.maybeLaunchInAppReview(
                        activity = this@MainActivity,
                        hasCompletedWalkthrough = latestSettings.hasCompletedWalkthrough,
                        isFocusModeActive = latestSettings.isFocusModeActiveNow()
                    )
                }
            }
        }
    }

    override fun onPause() {
        reviewJob?.cancel()
        reviewJob = null
        super.onPause()
        if (activeScreen == ActiveScreen.CommandPalette) {
            activeScreen = ActiveScreen.Home
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) {
            if (isRequestingDefaultLauncher) {
                isRequestingDefaultLauncher = false
                // Returning from default launcher system prompt: retain current screen without forcing navigation
            } else if (activeScreen != ActiveScreen.Permissions) {
                activeScreen = ActiveScreen.Home
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("active_screen", activeScreen.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Register package installs/updates receiver with Android 14/15 broadcast safety flags.
        // Must use RECEIVER_EXPORTED because ACTION_PACKAGE_* broadcasts originate from the OS system server (UID 1000).
        packageReceiver = PackageChangeReceiver(
            appRepository = app.appRepository,
            settingsRepository = app.settingsRepository,
            notificationRepository = app.notificationRepository,
            coroutineScope = app.applicationScope
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }

        val savedScreen = savedInstanceState?.getString("active_screen")
        activeScreen = if (savedScreen != null) {
            try {
                ActiveScreen.valueOf(savedScreen)
            } catch (e: Exception) {
                ActiveScreen.Home
            }
        // Skip permission disclosure once the user has seen it — even without granting access.
        // Safety nets: PermissionBanner in Settings and NotificationCenterScreen banner
        // will continue reminding the user to grant notification access.
        } else if (isNotificationAccessGranted(this) || com.alauncher.app.data.settings.SettingsRepository.hasSeenPermissionDisclosureFast(this)) {
            ActiveScreen.Home
        } else {
            ActiveScreen.Permissions
        }

        // Unified reactive service lifecycle manager
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previousSettings: com.alauncher.app.data.settings.SettingsState? = null
                app.settingsRepository.settingsFlow.collect { settings ->
                    val appContext = applicationContext
                    
                    val prevDockEnabled = previousSettings?.thumbDockEnabled
                    val prevLimitsEnabled = previousSettings?.appLimitsEnabled

                    val dockChanged = prevDockEnabled != settings.thumbDockEnabled || previousSettings == null
                    val limitsChanged = prevLimitsEnabled != settings.appLimitsEnabled || previousSettings == null

                    if (dockChanged || limitsChanged) {
                        val isDefault by lazy { com.alauncher.app.ui.utils.LauncherUtils.isDefaultLauncher(appContext) }
                        val hasOverlay by lazy { android.provider.Settings.canDrawOverlays(appContext) }

                        // 1. Manage ThumbDockService
                        if (dockChanged) {
                            try {
                                val dockIntent = Intent(appContext, com.alauncher.app.service.ThumbDockService::class.java)
                                if (settings.thumbDockEnabled && hasOverlay && isDefault) {
                                    appContext.startForegroundService(dockIntent)
                                } else {
                                    appContext.stopService(dockIntent)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update ThumbDockService state", e)
                            }
                        }

                        // 2. Manage AppUsageService
                        if (limitsChanged) {
                            try {
                                val usageIntent = Intent(appContext, com.alauncher.app.service.AppUsageService::class.java)
                                val hasUsage = settingsViewModel.hasUsageStatsPermission(appContext)
                                if (settings.appLimitsEnabled && hasUsage && hasOverlay && isDefault) {
                                    appContext.startForegroundService(usageIntent)
                                } else {
                                    appContext.stopService(usageIntent)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update AppUsageService state", e)
                            }
                        }
                    }
                    
                    previousSettings = settings
                }
            }
        }

        setContent {
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            
            LaunchedEffect(settingsState.hasSeenPermissionDisclosure) {
                if (settingsState.hasSeenPermissionDisclosure && activeScreen == ActiveScreen.Permissions) {
                    activeScreen = ActiveScreen.Home
                }
            }

            val isDark = !settingsState.themeMode.isLight
            LaunchedEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
            


            var previousInterruptionFilter by rememberSaveable { mutableStateOf<Int?>(null) }
            var wasFocusActive by rememberSaveable { mutableStateOf<Boolean?>(null) }
            var showDndPermissionDialog by rememberSaveable { mutableStateOf(false) }
            val isFocusActiveNow = settingsState.isFocusModeActiveNow()

            LaunchedEffect(isFocusActiveNow) {
                val isDefault = com.alauncher.app.ui.utils.LauncherUtils.isDefaultLauncher(this@MainActivity)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                if (notificationManager != null) {
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        try {
                            if (isFocusActiveNow && isDefault) {
                                if (wasFocusActive != true) {
                                    val currentFilter = notificationManager.currentInterruptionFilter
                                    // If phone is not in Priority DND, record user's current filter and engage Priority DND.
                                    // If phone is ALREADY in Priority DND, do not record anything so we don't disable their DND on exit.
                                    if (currentFilter != android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                                        previousInterruptionFilter = currentFilter
                                        notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                                    } else {
                                        previousInterruptionFilter = null
                                    }
                                    wasFocusActive = true
                                }
                            } else if (wasFocusActive == true) {
                                // Only restore DND if ALauncher actually changed it.
                                // If user was already in Priority DND before Focus Mode, leave their DND untouched.
                                if (previousInterruptionFilter != null &&
                                    previousInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                ) {
                                    notificationManager.setInterruptionFilter(previousInterruptionFilter!!)
                                    previousInterruptionFilter = null
                                }
                                wasFocusActive = false
                            } else {
                                wasFocusActive = false
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to update notification policy filter", e)
                        }
                    } else {
                        // User activated Focus Mode without DND notification policy access
                        if (isFocusActiveNow && isDefault && wasFocusActive != true) {
                            showDndPermissionDialog = true
                            wasFocusActive = true
                        } else if (!isFocusActiveNow || !isDefault) {
                            wasFocusActive = false
                        }
                    }
                }
            }

            MinimalSmartLauncherTheme(themeMode = settingsState.themeMode) {
                if (showDndPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showDndPermissionDialog = false },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        title = {
                            AppDialogHeader(
                                icon = Icons.Default.DoNotDisturbOn,
                                title = "Do Not Disturb Access",
                                subtitle = stringResource(R.string.dnd_permission_dialog_subtitle)
                            )
                        },
                        text = {
                            Text(
                                text = "To automatically silence system alerts and calls during Focus Mode, ALauncher requires Do Not Disturb permission. Would you like to enable it in system settings?",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDndPermissionDialog = false
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Failed to launch DND settings", e)
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text("Open Settings", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDndPermissionDialog = false },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text("Not Now", fontSize = 13.sp)
                            }
                        }
                    )
                }

                val isLaunching = isLaunchingState.value
                val triggerTransition = { targetScreen: ActiveScreen ->
                    if (!isLaunchingState.value) {
                        isLaunchingState.value = true
                        activeScreen = targetScreen
                        lifecycleScope.launch {
                            delay(400)
                            isLaunchingState.value = false
                        }
                    }
                }

                val btPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Handled by state
                }

                val defaultHomeRoleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) {
                    isRequestingDefaultLauncher = false
                    if (LauncherUtils.isDefaultLauncher(this)) {
                        Toast.makeText(this, getString(R.string.toast_default_launcher_success), Toast.LENGTH_SHORT).show()
                    }
                }

                LauncherAppScaffold(
                    activeScreen = activeScreen,
                    onScreenChange = { activeScreen = it },
                    homeViewModel = homeViewModel,
                    notificationViewModel = notificationViewModel,
                    commandViewModel = commandViewModel,
                    settingsViewModel = settingsViewModel,
                    isLaunching = isLaunching,
                    onLaunchApp = { packageName ->
                        if (!isLaunchingState.value) {
                            isLaunchingState.value = true
                            lifecycleScope.launch {
                                delay(1000)
                                isLaunchingState.value = false
                            }
                            if (LauncherUtils.launchAppPackage(this@MainActivity, packageName)) {
                                InAppReviewHelper.recordAppLaunch(this@MainActivity)
                            }
                        }
                    },
                    triggerTransition = triggerTransition,
                    onGrantNotificationAccess = {
                        try {
                            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: Exception) {
                            try {
                                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                            } catch (e2: Exception) {
                                android.util.Log.e("MainActivity", "Failed to open settings", e2)
                            }
                        }
                    },
                    onRequestDefaultLauncher = {
                        isRequestingDefaultLauncher = true
                        LauncherUtils.requestDefaultLauncher(this@MainActivity, defaultHomeRoleLauncher)
                    },
                    onGrantBluetoothAccess = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            btPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                )
            }
        }
    }



    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // Safe fallback if already unregistered
        }
        super.onDestroy()
    }
}
