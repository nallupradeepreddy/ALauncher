package com.alauncher.app.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import com.alauncher.app.ui.components.AppDialogHeader
import com.alauncher.app.R
import com.alauncher.app.service.LauncherAccessibilityService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alauncher.app.ActiveScreen
import com.alauncher.app.ui.commandpalette.CommandPaletteScreen
import com.alauncher.app.ui.commandpalette.CommandPaletteViewModel
import com.alauncher.app.ui.home.ContextualActionType
import com.alauncher.app.ui.home.HomeViewModel
import com.alauncher.app.ui.home.LauncherHomeScreen
import com.alauncher.app.ui.notificationcenter.NotificationCenterScreen
import com.alauncher.app.ui.notificationcenter.NotificationCenterViewModel
import com.alauncher.app.ui.permissions.PermissionDisclosureScreen
import com.alauncher.app.ui.settings.SettingsScreen
import com.alauncher.app.ui.settings.SettingsViewModel
import com.alauncher.app.ui.walkthrough.DynamicWalkthroughOverlay
import com.alauncher.app.data.settings.isFocusModeActiveNow
import com.alauncher.app.service.NotificationListener
import com.alauncher.app.ui.theme.themeBackgroundTexture
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alauncher.app.ui.utils.LauncherUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val PREDICTIVE_BACK_SCALE_FACTOR = 0.06f
private const val PREDICTIVE_BACK_MAIN_ALPHA_FACTOR = 0.25f
private const val PREDICTIVE_BACK_OVERLAY_ALPHA_FACTOR = 0.35f

@Composable
fun LauncherAppScaffold(
    activeScreen: ActiveScreen,
    onScreenChange: (ActiveScreen) -> Unit,
    homeViewModel: HomeViewModel,
    notificationViewModel: NotificationCenterViewModel,
    commandViewModel: CommandPaletteViewModel,
    settingsViewModel: SettingsViewModel,
    isLaunching: Boolean,
    onLaunchApp: (String) -> Unit,
    triggerTransition: (ActiveScreen) -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onRequestDefaultLauncher: () -> Unit = {},
    onGrantBluetoothAccess: () -> Unit = {}
) {
    val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isManualWalkthroughActive by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isDefaultLauncher = remember(permissionRefreshKey) {
        LauncherUtils.isDefaultLauncher(context)
    }

    val isWalkthroughVisible = (activeScreen == ActiveScreen.Home && !settingsState.hasCompletedWalkthrough) || isManualWalkthroughActive

    val dismissWalkthrough: () -> Unit = {
        if (isManualWalkthroughActive) {
            settingsViewModel.setWalkthroughStep(0)
            isManualWalkthroughActive = false
        } else {
            // setHasCompletedWalkthrough(true) already resets WALKTHROUGH_STEP to 0 internally
            settingsViewModel.setHasCompletedWalkthrough(true)
        }
    }

    // Dismiss walkthrough on back press
    BackHandler(enabled = isWalkthroughVisible) { dismissWalkthrough() }

    // Trap device physical back gestures with Android 14+ predictive back gesture progress
    PredictiveBackHandler(
        enabled = !isLaunching && !isWalkthroughVisible && activeScreen != ActiveScreen.Home && activeScreen != ActiveScreen.Permissions
    ) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress = backEvent.progress
            }
            backProgress = 0f
            triggerTransition(ActiveScreen.Home)
        } catch (e: CancellationException) {
            backProgress = 0f
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .themeBackgroundTexture(settingsState.themeMode)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            val mainScreenState = when (activeScreen) {
                ActiveScreen.CommandPalette -> ActiveScreen.Home
                else -> activeScreen
            }

            val blurRadius = when {
                isWalkthroughVisible -> 16.dp
                activeScreen == ActiveScreen.CommandPalette -> 16.dp * (1f - backProgress)
                else -> 0.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                    .then(
                        if (mainScreenState != ActiveScreen.Home && backProgress > 0f) {
                            Modifier.graphicsLayer {
                                val scale = 1f - (backProgress * PREDICTIVE_BACK_SCALE_FACTOR)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - (backProgress * PREDICTIVE_BACK_MAIN_ALPHA_FACTOR)
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                AnimatedContent(
                    targetState = mainScreenState,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "screen_routing"
                ) { screen ->
                    when (screen) {
                        ActiveScreen.Home -> {
                            val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                            LauncherHomeScreen(
                                state = homeState,
                                alignment = settingsState.homeAlignment,
                                showHomeIcons = settingsState.showHomeIcons,
                                showHomeLabels = settingsState.showHomeLabels,
                                onLaunchApp = { appItem ->
                                    if (!isLaunching) {
                                        onLaunchApp(appItem.packageName)
                                        homeViewModel.launchApp(appItem)
                                    }
                                },
                                onToggleFavorite = { if (!isLaunching) homeViewModel.toggleFavorite(it) },
                                onSwipeRight = { triggerTransition(ActiveScreen.NotificationCenter) },
                                onSwipeLeft = {
                                    if (!isLaunching) {
                                        commandViewModel.onQueryChanged("")
                                        triggerTransition(ActiveScreen.CommandPalette)
                                    }
                                },
                                onSwipeUp = {
                                    if (!isLaunching) {
                                        commandViewModel.onQueryChanged("")
                                        triggerTransition(ActiveScreen.CommandPalette)
                                    }
                                },
                                onSwipeDown = {
                                    if (!isLaunching) {
                                        try {
                                            val statusBarService = context.getSystemService("statusbar")
                                            val statusBarManager = Class.forName("android.app.StatusBarManager")
                                            val expandNotificationsPanel = statusBarManager.getMethod("expandNotificationsPanel")
                                            expandNotificationsPanel.invoke(statusBarService)
                                        } catch (e: Exception) {
                                            android.util.Log.e("LauncherAppScaffold", "Failed to expand status bar", e)
                                        }
                                    }
                                },
                                onLongPressHome = { triggerTransition(ActiveScreen.Settings) },
                                onDoubleTapHome = {
                                    if (!isLaunching) {
                                        if (!LauncherAccessibilityService.lockScreen()) {
                                            showAccessibilityDialog = true
                                        }
                                    }
                                },
                                onContextualIslandAction = { actionType, payload ->
                                    handleContextualAction(context, actionType, payload, homeViewModel, onGrantBluetoothAccess)
                                },
                                isLaunching = isLaunching
                            )
                        }
                        ActiveScreen.NotificationCenter -> {
                            val notifState by notificationViewModel.state.collectAsStateWithLifecycle()
                            NotificationCenterScreen(
                                state = notifState,
                                onBack = { triggerTransition(ActiveScreen.Home) },
                                onLaunchNotification = { key ->
                                    if (!isLaunching) {
                                        notificationViewModel.launchNotification(key, context)
                                        onScreenChange(ActiveScreen.Home)
                                    }
                                },
                                onDismissNotification = { key ->
                                    if (!isLaunching) notificationViewModel.dismissNotification(key)
                                },
                                onClearAll = {
                                    if (!isLaunching) notificationViewModel.clearAllNotifications()
                                },
                                onSearchQueryChange = { notificationViewModel.onSearchQueryChanged(it) }
                            )
                        }
                        ActiveScreen.Settings -> {
                            val allAppsList by settingsViewModel.allApps.collectAsStateWithLifecycle()
                            val overlayAppsSet by settingsViewModel.overlayApps.collectAsStateWithLifecycle()
                            SettingsScreen(
                                state = settingsState,
                                allApps = allAppsList,
                                overlayApps = overlayAppsSet,
                                onRefreshOverlayApps = { settingsViewModel.refreshOverlayApps(context) },
                                onOpenOverlaySettings = { pkg -> settingsViewModel.openAppOverlaySettings(context, pkg) },
                                onBack = { triggerTransition(ActiveScreen.Home) },
                                onSetThemeMode = { settingsViewModel.setThemeMode(it) },
                                onSetSortMode = { settingsViewModel.setSortMode(it) },
                                onSetShowIcons = { settingsViewModel.setShowIcons(it) },
                                onSetShowFocusModeButton = { settingsViewModel.setShowFocusModeButton(it) },
                                onSetAllowMeetingsInFocusMode = { settingsViewModel.setAllowMeetingsInFocusMode(it) },
                                onSetShowHomeIcons = { settingsViewModel.setShowHomeIcons(it) },
                                onSetShowHomeLabels = { settingsViewModel.setShowHomeLabels(it) },
                                onSetCalendarEnabled = { settingsViewModel.setCalendarEnabled(it) },
                                onSetQuietHoursEnabled = { settingsViewModel.setQuietHoursEnabled(it) },
                                onSetQuietHoursRange = { start, end -> settingsViewModel.setQuietHoursRange(start, end) },
                                onSetHomeApps = { settingsViewModel.setHomeApps(it) },
                                onSetHomeAlignment = { settingsViewModel.setHomeAlignment(it) },
                                onToggleUnfilteredApp = { settingsViewModel.toggleUnfilteredApp(it) },
                                onExportConfig = { settingsViewModel.exportConfig() },
                                onImportConfig = { json, onSuccess, onFailure -> settingsViewModel.importConfig(json, onSuccess, onFailure) },
                                onSetThumbDockEnabled = { settingsViewModel.setThumbDockEnabled(it) },
                                onSetThumbDockEdgeRight = { settingsViewModel.setThumbDockEdgeRight(it) },
                                onSetAppLimitsEnabled = { settingsViewModel.setAppLimitsEnabled(it) },
                                onSetAppLimitsBreakDuration = { settingsViewModel.setAppLimitsBreakDuration(it) },
                                onSetAppLimit = { pkg, limit -> settingsViewModel.setAppLimit(pkg, limit) },
                                hasUsageStatsPermission = { settingsViewModel.hasUsageStatsPermission(it) },
                                onSetIslandCategoryEnabled = { category, enabled -> settingsViewModel.setIslandCategoryEnabled(category, enabled) },
                                onSetLiveTrackingEnabled = { settingsViewModel.setLiveTrackingEnabled(it) },
                                onSetTransactionSubtypeEnabled = { subtype, enabled -> settingsViewModel.setTransactionSubtypeEnabled(subtype, enabled) },
                                onToggleTransactionAllowedApp = { settingsViewModel.toggleTransactionAllowedApp(it) },
                                onToggleDeliveryAllowedApp = { settingsViewModel.toggleDeliveryAllowedApp(it) },
                                onToggleMeetingAllowedApp = { settingsViewModel.toggleMeetingAllowedApp(it) },
                                onResetTransactionAllowedApps = { settingsViewModel.resetTransactionAllowedApps() },
                                onResetDeliveryAllowedApps = { settingsViewModel.resetDeliveryAllowedApps() },
                                onResetMeetingAllowedApps = { settingsViewModel.resetMeetingAllowedApps() },
                                onRequestDefaultLauncher = onRequestDefaultLauncher,
                                onSetNotificationFilteringEnabled = { settingsViewModel.setNotificationFilteringEnabled(it) },
                                onReplayWalkthrough = {
                                    settingsViewModel.setWalkthroughStep(0)
                                    isManualWalkthroughActive = true
                                    triggerTransition(ActiveScreen.Home)
                                },
                                isDefaultLauncher = isDefaultLauncher
                            )
                        }
                        ActiveScreen.Permissions -> {
                            PermissionDisclosureScreen(
                                onContinue = {
                                    settingsViewModel.setHasSeenPermissionDisclosure(true)
                                    triggerTransition(ActiveScreen.Home)
                                },
                                onGrantNotificationAccess = onGrantNotificationAccess
                            )
                        }
                        ActiveScreen.CommandPalette -> {
                            // Should not happen as it's handled by mainScreenState mapping
                            // but needed for exhaustiveness if not mapped
                        }
                    }
                }
            }

            // Command Palette Overlay (Placed OUTSIDE the blurred Box to remain sharp)
            if (activeScreen == ActiveScreen.CommandPalette) {
                val paletteApps by commandViewModel.apps.collectAsStateWithLifecycle()
                val query by commandViewModel.query.collectAsStateWithLifecycle()
                val mathResult by commandViewModel.mathResult.collectAsStateWithLifecycle()
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (backProgress > 0f) {
                                Modifier.graphicsLayer {
                                    val scale = 1f - (backProgress * PREDICTIVE_BACK_SCALE_FACTOR)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1f - (backProgress * PREDICTIVE_BACK_OVERLAY_ALPHA_FACTOR)
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    CommandPaletteScreen(
                        apps = paletteApps,
                        query = query,
                        onQueryChange = { commandViewModel.onQueryChanged(it) },
                        isFocusModeActive = settingsState.isFocusModeActiveNow(),
                        onToggleFocusMode = { active, duration -> settingsViewModel.toggleFocusMode(active, duration) },
                        onLaunchApp = { app ->
                            if (!isLaunching) {
                                onLaunchApp(app.packageName)
                                homeViewModel.launchApp(app)
                            }
                        },
                        onAddToHomeScreen = { app ->
                            scope.launch {
                                commandViewModel.addAppToHomeScreen(app.packageName, context)
                                triggerTransition(ActiveScreen.Home)
                            }
                        },
                        onHideFromList = { homeViewModel.toggleHidden(it) },
                        showIcons = settingsState.showIcons,
                        showFocusModeButton = settingsState.showFocusModeButton,
                        focusEndTime = settingsState.focusEndTime,
                        mathResult = mathResult,
                        sortMode = settingsState.sortMode,
                        onBack = { triggerTransition(ActiveScreen.Home) },
                        isDefaultLauncher = isDefaultLauncher
                    )
                }
            }

            // Dynamic Onboarding Walkthrough Overlay
            if (isWalkthroughVisible) {
                DynamicWalkthroughOverlay(
                    currentStepIndex = settingsState.walkthroughStep,
                    onStepChange = { settingsViewModel.setWalkthroughStep(it) },
                    onDismiss = dismissWalkthrough,
                    onRequestDefaultLauncher = onRequestDefaultLauncher,
                    onGrantNotificationAccess = onGrantNotificationAccess,
                    isDefaultLauncher = isDefaultLauncher
                )
            }

            if (showAccessibilityDialog) {
                AlertDialog(
                    onDismissRequest = { showAccessibilityDialog = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    title = {
                        AppDialogHeader(
                            icon = Icons.Default.TouchApp,
                            title = stringResource(R.string.double_tap_to_lock_dialog_title),
                            subtitle = stringResource(R.string.double_tap_to_lock_dialog_subtitle)
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.double_tap_to_lock_dialog_message),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showAccessibilityDialog = false
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Open Settings", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showAccessibilityDialog = false },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.feedback_cancel), fontSize = 13.sp)
                        }
                    }
                )
            }
        }
    }
}

private fun handleContextualAction(
    context: Context,
    actionType: ContextualActionType,
    payload: String?,
    homeViewModel: HomeViewModel,
    onGrantBluetoothAccess: () -> Unit = {}
) {
    when (actionType) {
        ContextualActionType.COPY_OTP -> {
            payload?.let { data ->
                // Payload is "otp:::key"
                val parts = data.split(":::")
                val otp = parts[0]
                val key = if (parts.size > 1) parts[1] else null
                
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP", otp)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "OTP copied to clipboard", Toast.LENGTH_SHORT).show()
                    
                    // Trigger UI feedback
                    key?.let { 
                        homeViewModel.onOtpCopied(it)
                        // Auto-dismiss notification
                        NotificationListener.cancelNotification(it)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to copy OTP", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ContextualActionType.OPEN_LINK -> {
            payload?.let { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ContextualActionType.LAUNCH_NOTIFICATION -> {
            payload?.let { key ->
                homeViewModel.launchNotificationByKey(key)
            }
        }
        ContextualActionType.DISMISS -> {
            homeViewModel.dismissCurrentIsland()
        }
        ContextualActionType.OPEN_BLUETOOTH_SETTINGS -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                onGrantBluetoothAccess()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ContextualActionType.OPEN_HOTSPOT_SETTINGS -> {
            try {
                val tetherIntent = Intent().apply {
                    setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings"))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(tetherIntent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "Could not open Hotspot settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
        else -> {}
    }
}
