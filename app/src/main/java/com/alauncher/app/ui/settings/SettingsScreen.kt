package com.alauncher.app.ui.settings

import com.alauncher.app.BuildConfig

import com.alauncher.app.ui.home.TransactionSettingsKeys
import com.alauncher.app.ui.home.TransactionAlertType
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alauncher.app.ui.settings.components.*
import com.alauncher.app.R
import com.alauncher.app.model.SortMode
import com.alauncher.app.model.ThemeMode
import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.HomeAlignment
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.ui.utils.InAppReviewHelper
import com.alauncher.app.ui.utils.LauncherUtils
import com.alauncher.app.ui.utils.isNotificationAccessGranted
import com.alauncher.app.ui.theme.getThemePreviewColors
import kotlinx.coroutines.launch

private val ThemeDarkBadgeBg = Color(0xFF1E293B)
private val ThemeLightBadgeBg = Color(0xFFE2E8F0)
private val ThemeDarkBadgeText = Color(0xFF94A3B8)
private val ThemeLightBadgeText = Color(0xFF475569)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    allApps: List<LauncherApp>,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetSortMode: (SortMode) -> Unit,
    onSetShowIcons: (Boolean) -> Unit,
    onSetShowFocusModeButton: (Boolean) -> Unit,
    onSetAllowMeetingsInFocusMode: (Boolean) -> Unit,
    onSetShowHomeIcons: (Boolean) -> Unit,
    onSetShowHomeLabels: (Boolean) -> Unit,
    onSetCalendarEnabled: (Boolean) -> Unit,
    onSetQuietHoursEnabled: (Boolean) -> Unit,
    onSetQuietHoursRange: (String, String) -> Unit,
    onSetHomeApps: (List<String>) -> Unit,
    onSetHomeAlignment: (HomeAlignment) -> Unit,
    onToggleUnfilteredApp: (String) -> Unit,
    onExportConfig: suspend () -> String,
    onImportConfig: (String, () -> Unit, () -> Unit) -> Unit,
    onSetThumbDockEnabled: (Boolean) -> Unit,
    onSetThumbDockEdgeRight: (Boolean) -> Unit,
    onSetAppLimitsEnabled: (Boolean) -> Unit,
    onSetAppLimitsBreakDuration: (Int) -> Unit,
    onSetAppLimit: (String, Int) -> Unit,
    hasUsageStatsPermission: (Context) -> Boolean,
    onSetIslandCategoryEnabled: (String, Boolean) -> Unit,
    onSetLiveTrackingEnabled: (Boolean) -> Unit,
    onSetTransactionSubtypeEnabled: (String, Boolean) -> Unit,
    onToggleTransactionAllowedApp: (String) -> Unit,
    onToggleDeliveryAllowedApp: (String) -> Unit,
    onToggleMeetingAllowedApp: (String) -> Unit,
    onResetTransactionAllowedApps: () -> Unit,
    onResetDeliveryAllowedApps: () -> Unit,
    onResetMeetingAllowedApps: () -> Unit,
    onRequestDefaultLauncher: () -> Unit = {},
    onSetNotificationFilteringEnabled: (Boolean) -> Unit = {},
    onReplayWalkthrough: () -> Unit = {},
    overlayApps: Set<String> = emptySet(),
    onRefreshOverlayApps: () -> Unit = {},
    onOpenOverlaySettings: (String?) -> Unit = {},
    isDefaultLauncher: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionHandler = rememberPermissionSettingHandler(snackbarHostState)
    var isSettingsLocked by remember { mutableStateOf(true) }
    val isEnabled = !isSettingsLocked

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    var isAccessibilityServiceRunning by remember { mutableStateOf(com.alauncher.app.service.LauncherAccessibilityService.isRunning()) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionRefreshKey++
                isAccessibilityServiceRunning = com.alauncher.app.service.LauncherAccessibilityService.isRunning()
                onRefreshOverlayApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasOverlayPermission = remember(permissionRefreshKey) {
        android.provider.Settings.canDrawOverlays(context)
    }
    val hasUsagePermission = remember(permissionRefreshKey) {
        hasUsageStatsPermission(context)
    }

    val toastLongPressUnlock = stringResource(R.string.toast_long_press_unlock)
    val toastSettingsLocked = stringResource(R.string.toast_settings_locked)
    val toastSettingsUnlocked = stringResource(R.string.toast_settings_unlocked)
    val toastMaxHomeApps = stringResource(R.string.toast_max_home_apps)
    val toastBackupCopied = stringResource(R.string.toast_backup_copied)
    val toastSettingsRestored = stringResource(R.string.toast_settings_restored)
    val toastRestoreFailed = stringResource(R.string.toast_restore_failed)

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAccessibilityDisclosureDialog by remember { mutableStateOf(false) }
    var backupJsonText by remember { mutableStateOf("") }
    var restoreJsonText by remember { mutableStateOf("") }

    var showUnfilteredAppsDialog by remember { mutableStateOf(false) }
    var showOverlayShieldDialog by remember { mutableStateOf(false) }
    var showAppLimitsDialog by remember { mutableStateOf(false) }
    var showAddAppsToHomeDialog by remember { mutableStateOf(false) }
    var showConfigureHomeAppsDialog by remember { mutableStateOf(false) }
    var showSortModeDialog by remember { mutableStateOf(false) }


    // Per-category Contextual Island allowlist dialogs.
    var showTransactionAllowlistDialog by remember { mutableStateOf(false) }
    var showDeliveryAllowlistDialog by remember { mutableStateOf(false) }
    var showMeetingAllowlistDialog by remember { mutableStateOf(false) }

    var quietHoursStartInput by remember(state.quietHoursStart) { mutableStateOf(state.quietHoursStart) }
    var quietHoursEndInput by remember(state.quietHoursEnd) { mutableStateOf(state.quietHoursEnd) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("settings_scaffold"),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Card 1: ⚙️ SYSTEM (Always Unlocked)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_system),
                    enabled = true
                ) {
                    // Set Default Launcher Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = true) {
                                onRequestDefaultLauncher()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.set_default_launcher), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (isDefaultLauncher) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isDefaultLauncher) stringResource(R.string.already_default) else stringResource(R.string.set_default_launcher_desc),
                                fontSize = 12.sp,
                                color = if (isDefaultLauncher) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    // Interactive Feature Tour Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReplayWalkthrough() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.walkthrough_replay_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.walkthrough_replay_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    // Auto Screen Off Setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = true) { openAutoScreenOffSettings(context) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.screen_timeout_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.screen_timeout_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    // Android System Settings Shortcut Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = true) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, R.string.toast_system_settings_error, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.android_system_settings), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.android_system_settings_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Card 2: 👆 Default Gestures (Always Unlocked)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_default_gestures),
                    enabled = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.gesture_swipe_up_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.gesture_swipe_up_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.gesture_swipe_down_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.gesture_swipe_down_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.gesture_swipe_right_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.gesture_swipe_right_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    SettingsDivider()

                    // Double-Tap to Lock Screen Setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = true) {
                                if (isAccessibilityServiceRunning) {
                                    com.alauncher.app.service.LauncherAccessibilityService.openAccessibilitySettings(context)
                                } else {
                                    showAccessibilityDisclosureDialog = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.double_tap_to_lock_dialog_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (isAccessibilityServiceRunning) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isAccessibilityServiceRunning) {
                                    stringResource(R.string.double_tap_to_lock_active_desc)
                                } else {
                                    stringResource(R.string.double_tap_to_lock_inactive_desc)
                                },
                                fontSize = 12.sp,
                                color = if (isAccessibilityServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Item 3: Lock Status Card Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSettingsLocked)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSettingsLocked) stringResource(R.string.settings_locked_label) else stringResource(R.string.settings_unlocked_label),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSettingsLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isSettingsLocked)
                                    stringResource(R.string.settings_locked_notice)
                                else
                                    stringResource(R.string.settings_unlocked_notice),
                                fontSize = 12.sp,
                                color = if (isSettingsLocked) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        if (isSettingsLocked) {
                                            Toast.makeText(context, toastLongPressUnlock, Toast.LENGTH_SHORT).show()
                                        } else {
                                            isSettingsLocked = true
                                            Toast.makeText(context, toastSettingsLocked, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLongClick = {
                                        if (isSettingsLocked) {
                                            isSettingsLocked = false
                                            Toast.makeText(context, toastSettingsUnlocked, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                .testTag("lock_settings_button")
                        ) {
                            Icon(
                                imageVector = if (isSettingsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.lock_state_desc),
                                tint = if (isSettingsLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Card 4: 🏠 HOME SCREEN (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_home_screen),
                    enabled = isEnabled
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) { showConfigureHomeAppsDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.home_shortcuts_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.home_shortcuts_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(stringResource(R.string.home_app_alignment), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.home_app_alignment_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HomeAlignment.entries.forEach { alignment ->
                                FilterChip(
                                    selected = state.homeAlignment == alignment,
                                    onClick = { onSetHomeAlignment(alignment) },
                                    label = {
                                        Text(
                                            when (alignment) {
                                                HomeAlignment.Left -> stringResource(R.string.alignment_left)
                                                HomeAlignment.Right -> stringResource(R.string.alignment_right)
                                            }
                                        )
                                    },
                                    enabled = isEnabled,
                                    modifier = Modifier.testTag("alignment_chip_${alignment.name}")
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.show_home_icons), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.show_home_icons_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.showHomeIcons,
                            onCheckedChange = { onSetShowHomeIcons(it) },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("home_icons_switch")
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.show_home_labels), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.show_home_labels_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.showHomeLabels,
                            onCheckedChange = { onSetShowHomeLabels(it) },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("home_labels_switch")
                        )
                    }
                }
            }

            // Card 5: 🔍 SEARCH & GESTURES (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_search_gestures),
                    enabled = isEnabled
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) { showSortModeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sort_mode_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = when (state.sortMode) {
                                    SortMode.AlphabeticalAsc -> stringResource(R.string.sort_mode_alpha_asc)
                                    SortMode.AlphabeticalDesc -> stringResource(R.string.sort_mode_alpha_desc)
                                    SortMode.FavoritesFirst -> stringResource(R.string.sort_mode_favorites)
                                    SortMode.MostUsed -> stringResource(R.string.sort_mode_most_used)
                                    SortMode.RecentlyOpened -> stringResource(R.string.sort_mode_recently_opened)
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.show_icons_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.show_icons_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.showIcons,
                            onCheckedChange = { onSetShowIcons(it) },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("icons_switch")
                        )
                    }
                }
            }

            // Card 6: 🔔 NOTIFICATION FILTER (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_notification_filter),
                    enabled = isEnabled
                ) {
                    val hasNotifAccess = remember(permissionRefreshKey) { isNotificationAccessGranted(context) }
                    if (!hasNotifAccess) {
                        PermissionBanner(
                            buttonText = "Grant Notification Access",
                            onGrant = {
                                try {
                                    context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                }
                            },
                            enabled = isEnabled
                        )
                        SettingsDivider()
                    }

                    // 1. Master Filter Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.notification_filter_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    if (state.notificationFilteringEnabled) {
                                        R.string.notification_filter_desc_on
                                    } else {
                                        R.string.notification_filter_desc_off
                                    }
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        ThemedSwitch(
                            checked = state.notificationFilteringEnabled,
                            onCheckedChange = { onSetNotificationFilteringEnabled(it) },
                            enabled = isEnabled && hasNotifAccess,
                            modifier = Modifier.testTag("notification_filtering_switch")
                        )
                    }

                    SettingsDivider()

                    // 2. Combined Immediate Apps Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled && hasNotifAccess) { showUnfilteredAppsDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.immediate_apps_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.immediate_apps_desc, state.unfilteredApps.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Card 7: 🔕 FOCUS & QUIET HOURS (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_focus_notifications),
                    enabled = isEnabled
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.show_focus_mode_button_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.show_focus_mode_button_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.showFocusModeButton,
                            onCheckedChange = { onSetShowFocusModeButton(it) },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("focus_mode_button_switch")
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.allow_meetings_in_focus_mode_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.allow_meetings_in_focus_mode_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.allowMeetingsInFocusMode,
                            onCheckedChange = { onSetAllowMeetingsInFocusMode(it) },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("allow_meetings_in_focus_mode_switch")
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) {
                                onRefreshOverlayApps()
                                showOverlayShieldDialog = true
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.overlay_shield_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.overlay_shield_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.quiet_hours_silence), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.quiet_hours_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.quietHoursEnabled,
                            onCheckedChange = { onSetQuietHoursEnabled(it) },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("quiet_hours_switch")
                        )
                    }

                    if (state.quietHoursEnabled) {
                        SettingsDivider()
                        val timeFormatRegex = remember { Regex("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$") }
                        val isStartError = quietHoursStartInput.isNotEmpty() && !timeFormatRegex.matches(quietHoursStartInput)
                        val isEndError = quietHoursEndInput.isNotEmpty() && !timeFormatRegex.matches(quietHoursEndInput)
                        val isSameError = run {
                            val startMin = com.alauncher.app.data.settings.parseTimeToMinutes(quietHoursStartInput)
                            val endMin = com.alauncher.app.data.settings.parseTimeToMinutes(quietHoursEndInput)
                            startMin != null && endMin != null && startMin == endMin
                        }

                        fun showTimePicker(initialTime: String, onTimeSelected: (String) -> Unit) {
                            val parsed = com.alauncher.app.data.settings.parseTimeToMinutes(initialTime) ?: (22 * 60)
                            val h = parsed / 60
                            val m = parsed % 60
                            android.app.TimePickerDialog(context, { _, hour, minute ->
                                val formatted = String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)
                                onTimeSelected(formatted)
                            }, h, m, true).show()
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                OutlinedTextField(
                                    value = quietHoursStartInput,
                                    onValueChange = {
                                        quietHoursStartInput = it
                                        val sMin = com.alauncher.app.data.settings.parseTimeToMinutes(it)
                                        val eMin = com.alauncher.app.data.settings.parseTimeToMinutes(quietHoursEndInput)
                                        if (timeFormatRegex.matches(it) && sMin != null && eMin != null && sMin != eMin) {
                                            onSetQuietHoursRange(it, quietHoursEndInput)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.quiet_hours_start_label)) },
                                    isError = isStartError || isSameError,
                                    enabled = isEnabled,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                showTimePicker(quietHoursStartInput) { picked ->
                                                    quietHoursStartInput = picked
                                                    val sMin = com.alauncher.app.data.settings.parseTimeToMinutes(picked)
                                                    val eMin = com.alauncher.app.data.settings.parseTimeToMinutes(quietHoursEndInput)
                                                    if (timeFormatRegex.matches(picked) && sMin != null && eMin != null && sMin != eMin) {
                                                        onSetQuietHoursRange(picked, quietHoursEndInput)
                                                    }
                                                }
                                            },
                                            enabled = isEnabled
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = "Pick start time",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("quiet_start_time"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = quietHoursEndInput,
                                    onValueChange = {
                                        quietHoursEndInput = it
                                        val sMin = com.alauncher.app.data.settings.parseTimeToMinutes(quietHoursStartInput)
                                        val eMin = com.alauncher.app.data.settings.parseTimeToMinutes(it)
                                        if (timeFormatRegex.matches(it) && sMin != null && eMin != null && sMin != eMin) {
                                            onSetQuietHoursRange(quietHoursStartInput, it)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.quiet_hours_end_label)) },
                                    isError = isEndError || isSameError,
                                    enabled = isEnabled,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                showTimePicker(quietHoursEndInput) { picked ->
                                                    quietHoursEndInput = picked
                                                    val sMin = com.alauncher.app.data.settings.parseTimeToMinutes(quietHoursStartInput)
                                                    val eMin = com.alauncher.app.data.settings.parseTimeToMinutes(picked)
                                                    if (timeFormatRegex.matches(picked) && sMin != null && eMin != null && sMin != eMin) {
                                                        onSetQuietHoursRange(quietHoursStartInput, picked)
                                                    }
                                                }
                                            },
                                            enabled = isEnabled
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = "Pick end time",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("quiet_end_time"),
                                    singleLine = true
                                )
                            }
                            if (isSameError) {
                                Text(
                                    text = "Start and end times cannot be the same",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) { showAppLimitsDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_time_limits), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val statusText = if (state.appLimitsEnabled) {
                                val activeCount = state.appLimitsMap.size
                                stringResource(R.string.app_time_limits_status_active, activeCount, state.appLimitsBreakDuration)
                            } else {
                                stringResource(R.string.disabled)
                            }
                            Text(statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Card 8: 🛠️ UNIVERSAL THUMB DOCK (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_universal_thumb_dock),
                    enabled = isEnabled
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.enable), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.thumb_dock_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.thumbDockEnabled,
                            onCheckedChange = { checked ->
                                onSetThumbDockEnabled(checked)
                            },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("thumb_dock_switch")
                        )
                    }

                    if (state.thumbDockEnabled) {
                        if (!hasOverlayPermission) {
                            SettingsDivider()
                            PermissionBanner(
                                buttonText = stringResource(R.string.grant_overlay_permission),
                                onGrant = {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                enabled = isEnabled
                            )
                        }

                        SettingsDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.thumb_dock_position_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.thumb_dock_position_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ThemedSwitch(
                                checked = state.thumbDockEdgeRight,
                                onCheckedChange = { onSetThumbDockEdgeRight(it) },
                                enabled = isEnabled,
                                modifier = Modifier.testTag("thumb_dock_edge_switch")
                            )
                        }
                    }
                }
            }

            // Card 8: 🏝️ CONTEXTUAL ISLAND (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_contextual_island),
                    enabled = isEnabled
                ) {
                    Text(
                        text = stringResource(R.string.island_section_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SettingsDivider()

                    // OTP
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_otp_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_otp_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("OTP"),
                            onCheckedChange = { onSetIslandCategoryEnabled("OTP", it) },
                            enabled = isEnabled
                        )
                    }

                    SettingsDivider()

                    // Critical Alerts
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_critical_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_critical_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("CRITICAL_ALERT"),
                            onCheckedChange = { onSetIslandCategoryEnabled("CRITICAL_ALERT", it) },
                            enabled = isEnabled
                        )
                    }

                    SettingsDivider()

                    // Calendar Integration
                    val calendarPermission = android.Manifest.permission.READ_CALENDAR
                    val hasCalendarPermission = remember(permissionRefreshKey) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            calendarPermission
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }

                    val calendarLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        permissionHandler.onRuntimePermissionResult(
                            isGranted = isGranted,
                            featureName = "Calendar Integration",
                            onSetEnabled = onSetCalendarEnabled
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_calendar_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_calendar_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.calendarEnabled,
                            onCheckedChange = { checked ->
                                permissionHandler.handleRuntimeToggle(
                                    permission = calendarPermission,
                                    isGranted = hasCalendarPermission,
                                    onSetEnabled = onSetCalendarEnabled,
                                    requestPermission = { calendarLauncher.launch(it) },
                                    newChecked = checked
                                )
                            },
                            enabled = isEnabled,
                            modifier = Modifier.testTag("calendar_switch")
                        )
                    }

                    if (state.calendarEnabled && !hasCalendarPermission) {
                        SettingsDivider()
                        PermissionBanner(
                            buttonText = stringResource(R.string.grant_calendar_permission),
                            onGrant = { calendarLauncher.launch(calendarPermission) },
                            enabled = isEnabled
                        )
                    }

                    SettingsDivider()

                    // Upcoming Meetings
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_meetings_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_meetings_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("UPCOMING_MEETING"),
                            onCheckedChange = { onSetIslandCategoryEnabled("UPCOMING_MEETING", it) },
                            enabled = isEnabled
                        )
                    }

                    if (state.enabledIslandCategories.contains("UPCOMING_MEETING")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isEnabled) { showMeetingAllowlistDialog = true }
                                .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_meetings_apps_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                val meetingInstalledCount = remember(allApps, state.meetingAllowedApps) { allApps.count { state.meetingAllowedApps.contains(it.packageName) } }
                                Text(stringResource(R.string.installed_apps_allowed_count, meetingInstalledCount), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }

                    SettingsDivider()

                    // Ongoing Tasks
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_tasks_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_tasks_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("HIGH_PRIORITY_TASK"),
                            onCheckedChange = { onSetIslandCategoryEnabled("HIGH_PRIORITY_TASK", it) },
                            enabled = isEnabled
                        )
                    }

                    SettingsDivider()

                    // Rides & Deliveries
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_rides_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_rides_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("RIDE_DELIVERY"),
                            onCheckedChange = { onSetIslandCategoryEnabled("RIDE_DELIVERY", it) },
                            enabled = isEnabled
                        )
                    }

                    if (state.enabledIslandCategories.contains("RIDE_DELIVERY")) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_live_tracking_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(stringResource(R.string.island_live_tracking_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ThemedSwitch(
                                checked = state.liveTrackingEnabled,
                                onCheckedChange = { onSetLiveTrackingEnabled(it) },
                                enabled = isEnabled
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isEnabled) { showDeliveryAllowlistDialog = true }
                                .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_delivery_apps_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                val deliveryInstalledCount = remember(allApps, state.deliveryAllowedApps) { allApps.count { state.deliveryAllowedApps.contains(it.packageName) } }
                                Text(stringResource(R.string.installed_apps_allowed_count, deliveryInstalledCount), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }

                    SettingsDivider()

                    // System & Travel Status
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_system_status_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_system_status_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("GENERAL_NOTIFICATION"),
                            onCheckedChange = { onSetIslandCategoryEnabled("GENERAL_NOTIFICATION", it) },
                            enabled = isEnabled
                        )
                    }

                    SettingsDivider()

                    // Ambient System Info
                    val btPermission = "android.permission.BLUETOOTH_CONNECT"
                    val hasBluetoothPermission = remember(permissionRefreshKey) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                btPermission
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                    }
                    val btLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        permissionHandler.onRuntimePermissionResult(
                            isGranted = isGranted,
                            featureName = "Bluetooth",
                            onSetEnabled = { granted ->
                                onSetIslandCategoryEnabled("AMBIENT_INFO", granted)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ambient_system_info_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.ambient_system_info_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("AMBIENT_INFO"),
                            onCheckedChange = { checked ->
                                if (checked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    permissionHandler.handleRuntimeToggle(
                                        permission = btPermission,
                                        isGranted = hasBluetoothPermission,
                                        onSetEnabled = { granted ->
                                            onSetIslandCategoryEnabled("AMBIENT_INFO", granted)
                                        },
                                        requestPermission = { btLauncher.launch(it) },
                                        newChecked = checked
                                    )
                                } else {
                                    onSetIslandCategoryEnabled("AMBIENT_INFO", checked)
                                }
                            },
                            enabled = isEnabled
                        )
                    }

                    if (state.enabledIslandCategories.contains("AMBIENT_INFO") && !hasBluetoothPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        SettingsDivider()
                        PermissionBanner(
                            buttonText = stringResource(R.string.grant_bluetooth_permission),
                            onGrant = { btLauncher.launch(btPermission) },
                            enabled = isEnabled
                        )
                    }

                    SettingsDivider()

                    // Ambient Device States
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ambient_device_states_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.ambient_device_states_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains("AMBIENT_STATE"),
                            onCheckedChange = { onSetIslandCategoryEnabled("AMBIENT_STATE", it) },
                            enabled = isEnabled
                        )
                    }

                    SettingsDivider()

                    // Bills & Transactions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.island_bills_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.island_bills_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ThemedSwitch(
                            checked = state.enabledIslandCategories.contains(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL),
                            onCheckedChange = { onSetIslandCategoryEnabled(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL, it) },
                            enabled = isEnabled
                        )
                    }

                    if (state.enabledIslandCategories.contains(TransactionSettingsKeys.CATEGORY_BILL_TRANSACTIONAL)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_bills_debit_credit_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(stringResource(R.string.island_bills_debit_credit_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val debitEnabled = state.enabledTransactionSubtypes.contains(TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT)
                            ThemedSwitch(
                                checked = debitEnabled,
                                onCheckedChange = { isChecked ->
                                    onSetTransactionSubtypeEnabled(TransactionSettingsKeys.SUBTYPE_GROUP_DEBIT, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.CREDIT.name, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.REFUND.name, isChecked)
                                },
                                enabled = isEnabled
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_bills_reminders_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(stringResource(R.string.island_bills_reminders_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val billEnabled = state.enabledTransactionSubtypes.contains(TransactionSettingsKeys.SUBTYPE_GROUP_BILL)
                            ThemedSwitch(
                                checked = billEnabled,
                                onCheckedChange = { isChecked ->
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.BILL_GENERATED.name, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionSettingsKeys.SUBTYPE_GROUP_BILL, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.EMI_DUE.name, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.PAYMENT_REQUEST.name, isChecked)
                                },
                                enabled = isEnabled
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_bills_renewals_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(stringResource(R.string.island_bills_renewals_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val renewalEnabled = state.enabledTransactionSubtypes.contains(TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL)
                            ThemedSwitch(
                                checked = renewalEnabled,
                                onCheckedChange = { isChecked ->
                                    onSetTransactionSubtypeEnabled(TransactionSettingsKeys.SUBTYPE_GROUP_RENEWAL, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.TRIAL_ENDING.name, isChecked)
                                },
                                enabled = isEnabled
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_bills_security_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(stringResource(R.string.island_bills_security_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val failedEnabled = state.enabledTransactionSubtypes.contains(TransactionSettingsKeys.SUBTYPE_GROUP_FAILED)
                            ThemedSwitch(
                                checked = failedEnabled,
                                onCheckedChange = { isChecked ->
                                    onSetTransactionSubtypeEnabled(TransactionSettingsKeys.SUBTYPE_GROUP_FAILED, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.TRANSACTION_DECLINED.name, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.LOW_BALANCE.name, isChecked)
                                    onSetTransactionSubtypeEnabled(TransactionAlertType.SECURITY_ALERT.name, isChecked)
                                },
                                enabled = isEnabled
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isEnabled) { showTransactionAllowlistDialog = true }
                                .padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.island_bills_apps_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                val transactionInstalledCount = remember(allApps, state.transactionAllowedApps) { allApps.count { state.transactionAllowedApps.contains(it.packageName) } }
                                Text(stringResource(R.string.installed_apps_allowed_count, transactionInstalledCount), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // Card 9: 🎨 VISUAL STYLE & THEME (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_visual_theme),
                    enabled = isEnabled
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(stringResource(R.string.visual_theme), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.visual_theme_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val displayName = when (mode) {
                                    ThemeMode.NordicPine -> stringResource(R.string.theme_teal_dark)
                                    ThemeMode.BotanicalMint -> stringResource(R.string.theme_mint_light)
                                    ThemeMode.SunsetClay -> stringResource(R.string.theme_clay_light)
                                    ThemeMode.MidnightAurora -> stringResource(R.string.theme_sky_dark)
                                }
                                val (moodDesc, isDarkTheme) = when (mode) {
                                    ThemeMode.NordicPine -> Pair("Alpine Spruce & Frost Jade", true)
                                    ThemeMode.BotanicalMint -> Pair("Porcelain Fog & Cobalt Ink", false)
                                    ThemeMode.SunsetClay -> Pair("Warm Linen & Burnt Terracotta", false)
                                    ThemeMode.MidnightAurora -> Pair("Obsidian Velvet & Starlight Indigo", true)
                                }
                                val previewColors = getThemePreviewColors(mode)
                                val isSelected = state.themeMode == mode
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                           else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = isEnabled) { onSetThemeMode(mode) }
                                        .testTag("theme_chip_${mode.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isDarkTheme) ThemeDarkBadgeBg else ThemeLightBadgeBg
                                                ) {
                                                    Text(
                                                        text = if (isDarkTheme) "DARK" else "LIGHT",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.8.sp,
                                                        color = if (isDarkTheme) ThemeDarkBadgeText else ThemeLightBadgeText,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = moodDesc,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Color Swatches preview
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        ) {
                                            previewColors.forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(11.dp)
                                                        .background(color, shape = CircleShape)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape)
                                                )
                                            }
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            enabled = isEnabled
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 10: 💾 BACKUP & PORTABILITY (Requires Unlock)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_settings_lock_backup),
                    enabled = isEnabled
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) {
                                coroutineScope.launch {
                                    backupJsonText = onExportConfig()
                                    showBackupDialog = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.export_backup), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.export_backup_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) { showRestoreDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.import_backup), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.import_backup_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Card 13: 💬 Feedback & Suggestions (Always Unlocked)
            item {
                SettingsGroup(
                    title = stringResource(R.string.section_feedback),
                    enabled = true
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFeedbackDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.feedback_row_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.feedback_row_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                InAppReviewHelper.openStoreListing(context)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("rate_app_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.rate_app_row_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.rate_app_row_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Card 14: 🛡️ About & Privacy Policy
            item {
                SettingsGroup(
                    title = "About & Privacy",
                    enabled = true
                ) {
                    val uriHandler = LocalUriHandler.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    uriHandler.openUri(SettingsRepository.PRIVACY_POLICY_URL)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Read our 100% on-device data & privacy policy", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SettingsDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Version", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // 1. Unfiltered Apps Preference Dialog Checklist
    if (showUnfilteredAppsDialog) {
        var unfilteredQuery by remember { mutableStateOf("") }
        val sortedApps = remember(allApps, unfilteredQuery) {
            val filtered = if (unfilteredQuery.isBlank()) allApps
            else allApps.filter { it.label.contains(unfilteredQuery, ignoreCase = true) || it.packageName.contains(unfilteredQuery, ignoreCase = true) }
            filtered.sortedBy { it.label }
        }
        AlertDialog(
            onDismissRequest = { showUnfilteredAppsDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.immediate_notifications),
                    subtitle = stringResource(R.string.immediate_notifications_dialog_subtitle)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = unfilteredQuery,
                        onValueChange = { unfilteredQuery = it },
                        placeholder = { Text(stringResource(R.string.search_apps_placeholder), fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (unfilteredQuery.isNotEmpty()) {
                                IconButton(onClick = { unfilteredQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    Box(modifier = Modifier.height(320.dp).fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = sortedApps, key = { "unf:${it.packageName}" }) { app ->
                                val isUnfiltered = state.unfilteredApps.contains(app.packageName)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleUnfilteredApp(app.packageName) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(
                                            packageName = app.packageName,
                                            showIcon = state.showIcons,
                                            modifier = Modifier
                                                .padding(end = 10.dp)
                                                .size(28.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = app.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text(text = app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        ThemedSwitch(
                                            checked = isUnfiltered,
                                            onCheckedChange = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showUnfilteredAppsDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    if (showOverlayShieldDialog) {
        OverlayShieldDialog(
            allApps = allApps,
            overlayApps = overlayApps,
            unfilteredApps = state.unfilteredApps,
            showAppIcons = state.showIcons,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onDismiss = { showOverlayShieldDialog = false }
        )
    }

    // Per-category Contextual Island allowlist dialogs. All three share the same shape,
    // so the AllowlistAppsDialog helper below renders them uniformly.
    if (showTransactionAllowlistDialog) {
        AllowlistAppsDialog(
            title = stringResource(R.string.island_bills_apps_title),
            explanation = stringResource(R.string.island_bills_apps_explanation),
            allApps = allApps,
            allowedPackages = state.transactionAllowedApps,
            showAppIcons = state.showIcons,
            onToggleApp = onToggleTransactionAllowedApp,
            onResetToDefaults = onResetTransactionAllowedApps,
            onDismiss = { showTransactionAllowlistDialog = false }
        )
    }

    if (showDeliveryAllowlistDialog) {
        AllowlistAppsDialog(
            title = stringResource(R.string.island_delivery_apps_title),
            explanation = stringResource(R.string.island_delivery_apps_explanation),
            allApps = allApps,
            allowedPackages = state.deliveryAllowedApps,
            showAppIcons = state.showIcons,
            onToggleApp = onToggleDeliveryAllowedApp,
            onResetToDefaults = onResetDeliveryAllowedApps,
            onDismiss = { showDeliveryAllowlistDialog = false }
        )
    }

    if (showMeetingAllowlistDialog) {
        AllowlistAppsDialog(
            title = stringResource(R.string.island_meetings_apps_title),
            explanation = stringResource(R.string.island_meetings_apps_explanation),
            allApps = allApps,
            allowedPackages = state.meetingAllowedApps,
            showAppIcons = state.showIcons,
            onToggleApp = onToggleMeetingAllowedApp,
            onResetToDefaults = onResetMeetingAllowedApps,
            onDismiss = { showMeetingAllowlistDialog = false }
        )
    }

    if (showAppLimitsDialog) {
        var appQuery by remember { mutableStateOf("") }
        val filteredApps = remember(allApps, appQuery) {
            allApps.filter { it.label.contains(appQuery, ignoreCase = true) || it.packageName.contains(appQuery, ignoreCase = true) }
                .sortedWith { app1, app2 ->
                    val limit1 = state.appLimitsMap[app1.packageName] ?: 0
                    val limit2 = state.appLimitsMap[app2.packageName] ?: 0
                    if ((limit1 > 0) != (limit2 > 0)) {
                        if (limit1 > 0) -1 else 1
                    } else {
                        app1.label.compareTo(app2.label, ignoreCase = true)
                    }
                }
        }
        AlertDialog(
            onDismissRequest = { showAppLimitsDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.Default.HourglassEmpty,
                    title = stringResource(R.string.app_time_limits),
                    subtitle = stringResource(R.string.app_time_limits_dialog_subtitle)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Master Toggle
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(stringResource(R.string.enable_app_time_limits), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            ThemedSwitch(
                                checked = state.appLimitsEnabled,
                                onCheckedChange = { checked ->
                                    onSetAppLimitsEnabled(checked)
                                },
                                enabled = isEnabled
                            )
                        }
                    }

                    if (state.appLimitsEnabled) {
                        if (!hasUsagePermission) {
                            PermissionBanner(
                                buttonText = stringResource(R.string.grant_usage_access_permission),
                                onGrant = {
                                    try {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (e: Exception) {
                                        Toast.makeText(context, R.string.toast_usage_settings_error, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = isEnabled
                            )
                        }

                        if (!hasOverlayPermission) {
                            PermissionBanner(
                                buttonText = stringResource(R.string.grant_overlay_permission),
                                onGrant = {
                                    try {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (e: Exception) {
                                        try {
                                            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, R.string.toast_overlay_settings_error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = isEnabled
                            )
                        }

                        // Break Duration Configuration Row
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.break_duration_label), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { onSetAppLimitsBreakDuration(state.appLimitsBreakDuration - 1) },
                                        enabled = isEnabled && state.appLimitsBreakDuration > 1,
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        "${state.appLimitsBreakDuration}s",
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    OutlinedButton(
                                        onClick = { onSetAppLimitsBreakDuration(state.appLimitsBreakDuration + 1) },
                                        enabled = isEnabled && state.appLimitsBreakDuration < 30,
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Search
                        OutlinedTextField(
                            value = appQuery,
                            onValueChange = { appQuery = it },
                            placeholder = { Text(stringResource(R.string.search_apps_placeholder), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (appQuery.isNotEmpty()) {
                                    IconButton(onClick = { appQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        // Apps List
                        Box(modifier = Modifier.height(220.dp).fillMaxWidth()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = filteredApps, key = { "limit:${it.packageName}" }) { app ->
                                    val currentLimit = state.appLimitsMap[app.packageName] ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppIcon(
                                                packageName = app.packageName,
                                                showIcon = state.showIcons,
                                                modifier = Modifier.padding(end = 10.dp).size(26.dp)
                                            )
                                            Text(
                                                text = app.label,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { if (currentLimit > 0) onSetAppLimit(app.packageName, currentLimit - 1) },
                                                    enabled = currentLimit > 0,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (currentLimit > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (currentLimit > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (currentLimit == 0) stringResource(R.string.limit_none) else stringResource(R.string.limit_minutes, currentLimit),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (currentLimit > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onSetAppLimit(app.packageName, currentLimit + 1) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAppLimitsDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    // 2. Add App to Home Shortcuts Dialog Checklist
    if (showAddAppsToHomeDialog) {
        var localSelectedApps by remember(state.homeApps) { mutableStateOf(state.homeApps) }
        var homeSearchQuery by remember { mutableStateOf("") }
        val sortedApps = remember(allApps, homeSearchQuery) {
            val filtered = if (homeSearchQuery.isBlank()) allApps
            else allApps.filter { it.label.contains(homeSearchQuery, ignoreCase = true) || it.packageName.contains(homeSearchQuery, ignoreCase = true) }
            filtered.sortedBy { it.label }
        }
        AlertDialog(
            onDismissRequest = {
                showAddAppsToHomeDialog = false
                showConfigureHomeAppsDialog = true
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.Default.AddBox,
                    title = stringResource(R.string.home_shortcuts_title),
                    subtitle = "${localSelectedApps.size} / 10 selected • Tap an app to add or remove"
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = homeSearchQuery,
                        onValueChange = { homeSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_apps_placeholder), fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (homeSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { homeSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    Box(modifier = Modifier.height(350.dp).fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = sortedApps, key = { "addhm:${it.packageName}" }) { app ->
                                val isChecked = localSelectedApps.contains(app.packageName)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                           else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) {
                                                localSelectedApps = localSelectedApps.filter { it != app.packageName }
                                            } else {
                                                if (localSelectedApps.size >= 10) {
                                                    Toast.makeText(context, toastMaxHomeApps, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    localSelectedApps = localSelectedApps + app.packageName
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(
                                            packageName = app.packageName,
                                            showIcon = state.showIcons,
                                            modifier = Modifier
                                                .padding(end = 10.dp)
                                                .size(28.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.label,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = app.packageName,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (localSelectedApps.size >= 10) {
                                                        Toast.makeText(context, toastMaxHomeApps, Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        localSelectedApps = localSelectedApps + app.packageName
                                                    }
                                                } else {
                                                    localSelectedApps = localSelectedApps.filter { it != app.packageName }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetHomeApps(localSelectedApps)
                        showAddAppsToHomeDialog = false
                        showConfigureHomeAppsDialog = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddAppsToHomeDialog = false
                        showConfigureHomeAppsDialog = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Backup Serialized Overlay Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.Default.CloudUpload,
                    title = stringResource(R.string.backup_porting),
                    subtitle = stringResource(R.string.backup_dialog_subtitle)
                )
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = backupJsonText,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().height(150.dp).padding(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("alauncher_settings_backup", backupJsonText))
                        Toast.makeText(context, toastBackupCopied, Toast.LENGTH_SHORT).show()
                        showBackupDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_code))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackupDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    // Restoration Input String Overlay Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.Default.CloudDownload,
                    title = stringResource(R.string.import_backup),
                    subtitle = stringResource(R.string.restore_dialog_subtitle)
                )
            },
            text = {
                OutlinedTextField(
                    value = restoreJsonText,
                    onValueChange = { restoreJsonText = it },
                    placeholder = { Text(stringResource(R.string.import_backup_placeholder), fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportConfig(
                            restoreJsonText,
                            {
                                Toast.makeText(context, toastSettingsRestored, Toast.LENGTH_SHORT).show()
                                showRestoreDialog = false
                            },
                            {
                                Toast.makeText(context, toastRestoreFailed, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.import_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 3. Configure Home Apps dialog
    if (showConfigureHomeAppsDialog) {
        AlertDialog(
            onDismissRequest = { showConfigureHomeAppsDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.Default.DashboardCustomize,
                    title = stringResource(R.string.home_shortcuts_title),
                    subtitle = stringResource(R.string.home_shortcuts_desc)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                        if (state.homeApps.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.no_home_apps),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(items = state.homeApps, key = { _, pkg -> pkg }) { index, pkg ->
                                    val appModel = allApps.find { it.packageName == pkg }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppIcon(
                                                packageName = pkg,
                                                showIcon = state.showIcons,
                                                modifier = Modifier
                                                    .padding(end = 10.dp)
                                                    .size(26.dp)
                                            )
                                            Text(
                                                text = appModel?.label ?: pkg,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )

                                            // Move Up button
                                            IconButton(
                                                enabled = index > 0,
                                                onClick = {
                                                    val updated = state.homeApps.toMutableList()
                                                    val item = updated.removeAt(index)
                                                    updated.add(index - 1, item)
                                                    onSetHomeApps(updated)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = stringResource(R.string.move_up),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Move Down button
                                            IconButton(
                                                enabled = index < state.homeApps.size - 1,
                                                onClick = {
                                                    val updated = state.homeApps.toMutableList()
                                                    val item = updated.removeAt(index)
                                                    updated.add(index + 1, item)
                                                    onSetHomeApps(updated)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = stringResource(R.string.move_down),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Remove button
                                            IconButton(
                                                onClick = {
                                                    val updated = state.homeApps.filter { it != pkg }
                                                    onSetHomeApps(updated)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.remove),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    FilledTonalButton(
                        onClick = {
                            showConfigureHomeAppsDialog = false
                            showAddAppsToHomeDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_home_app_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_home_apps), fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showConfigureHomeAppsDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    // 4. App Search List Sorting Dialog
    if (showSortModeDialog) {
        AlertDialog(
            onDismissRequest = { showSortModeDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.sort_mode_title),
                    subtitle = stringResource(R.string.sort_mode_desc)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortMode.entries.forEach { mode ->
                        val isSelected = state.sortMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSortMode(mode)
                                    showSortModeDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSetSortMode(mode)
                                        showSortModeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (mode) {
                                        SortMode.AlphabeticalAsc -> stringResource(R.string.sort_mode_alpha_asc)
                                        SortMode.AlphabeticalDesc -> stringResource(R.string.sort_mode_alpha_desc)
                                        SortMode.FavoritesFirst -> stringResource(R.string.sort_mode_favorites)
                                        SortMode.MostUsed -> stringResource(R.string.sort_mode_most_used)
                                        SortMode.RecentlyOpened -> stringResource(R.string.sort_mode_recently_opened)
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSortModeDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            isDefaultLauncher = isDefaultLauncher,
            onDismiss = { showFeedbackDialog = false }
        )
    }

    if (showAccessibilityDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosureDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                SettingsDialogHeader(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(R.string.double_tap_to_lock_dialog_title)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.double_tap_to_lock_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDisclosureDialog = false
                        com.alauncher.app.service.LauncherAccessibilityService.openAccessibilitySettings(context)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAccessibilityDisclosureDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.feedback_cancel))
                }
            }
        )
    }
}

// Removed internal components - now using components.*

/**
 * Reusable dialog for editing a per-category Contextual Island allowlist. The dialog
 * shows every installed launcher app (searchable) with a switch to add/remove it from
 * the given allowlist, plus a "Reset to defaults" action that restores the curated
 * factory list from `IslandTrustedApps`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllowlistAppsDialog(
    title: String,
    explanation: String,
    allApps: List<LauncherApp>,
    allowedPackages: Set<String>,
    showAppIcons: Boolean,
    onToggleApp: (String) -> Unit,
    onResetToDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var resetTrigger by remember { mutableIntStateOf(0) }
    // We snapshot the allowedPackages to freeze the sort order while toggling,
    // so items don't jump around under the user's finger. We only re-sort on search or reset.
    val initialAllowedPackages = remember(query, resetTrigger) { allowedPackages }
    val sorted = remember(allApps, initialAllowedPackages, query) {
        allApps
            .filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<LauncherApp> { initialAllowedPackages.contains(it.packageName) }
                .thenBy { it.label.lowercase() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            SettingsDialogHeader(
                icon = Icons.Default.Tune,
                title = title,
                subtitle = explanation
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_apps_placeholder), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                val installedAllowedCount = remember(allApps, allowedPackages) { allApps.count { allowedPackages.contains(it.packageName) } }
                Text(
                    stringResource(R.string.installed_apps_allowed_count, installedAllowedCount),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.height(320.dp).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = sorted, key = { "allow:${it.packageName}" }) { app ->
                            val isAllowed = allowedPackages.contains(app.packageName)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleApp(app.packageName) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        packageName = app.packageName,
                                        showIcon = showAppIcons,
                                        modifier = Modifier
                                            .padding(end = 10.dp)
                                            .size(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = app.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(text = app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    ThemedSwitch(
                                        checked = isAllowed,
                                        onCheckedChange = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onResetToDefaults()
                    resetTrigger++
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.reset_to_defaults))
            }
        }
    )
}

private fun openAutoScreenOffSettings(context: Context) {
    val intentsToTry = listOf(
        Intent("android.settings.SCREEN_TIMEOUT_SETTINGS"),
        Intent("com.samsung.android.settings.SCREEN_TIMEOUT_SETTINGS"),
        Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS),
        Intent(android.provider.Settings.ACTION_SETTINGS)
    )

    for (intent in intentsToTry) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
            // Try next fallback intent
        }
    }
}

@Composable
private fun OverlayShieldDialog(
    allApps: List<LauncherApp>,
    overlayApps: Set<String>,
    unfilteredApps: Set<String>,
    showAppIcons: Boolean,
    onOpenOverlaySettings: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val overlayAppList = remember(allApps, overlayApps) {
        allApps.filter { overlayApps.contains(it.packageName) }
            .sortedBy { it.label }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            SettingsDialogHeader(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.overlay_shield_dialog_title),
                subtitle = stringResource(R.string.overlay_shield_dialog_explanation)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onOpenOverlaySettings(null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(stringResource(R.string.overlay_shield_all_button), fontSize = 12.sp)
                }

                if (overlayAppList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.overlay_shield_no_apps),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.height(280.dp).fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = overlayAppList, key = { "overlay:${it.packageName}" }) { app ->
                                val isImmediate = unfilteredApps.contains(app.packageName)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(
                                            packageName = app.packageName,
                                            showIcon = showAppIcons,
                                            modifier = Modifier
                                                .padding(end = 10.dp)
                                                .size(28.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.label,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = stringResource(if (isImmediate) R.string.overlay_shield_immediate_app else R.string.overlay_shield_filtered_app),
                                                fontSize = 11.sp,
                                                color = if (isImmediate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { onOpenOverlaySettings(app.packageName) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(stringResource(R.string.overlay_shield_manage), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.done))
            }
        }
    )
}
