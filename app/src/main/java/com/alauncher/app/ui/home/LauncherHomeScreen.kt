package com.alauncher.app.ui.home

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import com.alauncher.app.ui.components.BottomSheetAppHeader
import com.alauncher.app.ui.components.rememberDrawablePainter
import com.alauncher.app.ui.theme.LocalIsLightTheme
import com.alauncher.app.ui.utils.appIconGestureHandler
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.alauncher.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import android.content.res.Configuration
import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.HomeAlignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.abs
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.alauncher.app.ui.utils.openClockApp
import com.alauncher.app.ui.utils.openCalendarApp
import com.alauncher.app.ui.utils.openAlarmApp

@Composable
fun DateAlarmSection(
    currentDate: String,
    nextAlarmText: String,
    island: ContextualIsland?,
    onContextualIslandAction: (ContextualActionType, String?) -> Unit,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    dateAlarmSpacing: Dp = 6.dp,
    islandSpacing: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val arrangement = if (horizontalAlignment == Alignment.CenterHorizontally) {
        Arrangement.Center
    } else {
        Arrangement.Start
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = currentDate,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier
                .testTag("date_text")
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    openCalendarApp(context)
                }
        )
        Spacer(modifier = Modifier.height(dateAlarmSpacing))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = arrangement,
            modifier = Modifier.clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                openAlarmApp(context)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = nextAlarmText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.testTag("alarm_text")
            )
        }
        Spacer(modifier = Modifier.height(islandSpacing))
        ContextualIslandWidget(
            island = island,
            onAction = onContextualIslandAction
        )
    }
}

@Composable
fun ClockText(
    currentTime: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Text(
        text = currentTime,
        fontSize = fontSize,
        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1).sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .testTag("time_text")
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                openClockApp(context)
            }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LauncherHomeScreen(
    state: HomeState,
    alignment: HomeAlignment,
    showHomeIcons: Boolean = true,
    showHomeLabels: Boolean = true,
    onLaunchApp: (LauncherApp) -> Unit,
    onToggleFavorite: (LauncherApp) -> Unit,
    onSwipeRight: () -> Unit, // Open Notification Filter Screen
    onSwipeLeft: () -> Unit,  // Open Command Palette Search
    onSwipeUp: () -> Unit,    // Open Command Palette Search
    onSwipeDown: () -> Unit,  // Expand notification drawer
    onLongPressHome: () -> Unit, // Open Launcher Settings
    onDoubleTapHome: () -> Unit = {}, // Double tap on home to lock screen
    onContextualIslandAction: (ContextualActionType, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    isLaunching: Boolean = false
) {
    val context = LocalContext.current
    var selectedAppForMenu by remember { mutableStateOf<LauncherApp?>(null) }
    

    
    // Time/Date state
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    
    fun updateTime() {
        val now = Date()
        currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        currentDate = SimpleDateFormat("EEE, MMMM d", Locale.getDefault()).format(now)
    }

    DisposableEffect(Unit) {
        updateTime()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_TIME_TICK ||
                    intent.action == Intent.ACTION_TIME_CHANGED ||
                    intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    updateTime()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Alarm state
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }
    var nextAlarmText by remember { mutableStateOf("No Alarm") }
    
    fun updateAlarm() {
        val nextAlarm = alarmManager?.nextAlarmClock
        nextAlarmText = nextAlarm?.let {
            val df = SimpleDateFormat("EEE, h:mm a", Locale.getDefault())
            df.format(Date(it.triggerTime))
        } ?: "No Alarm"
    }

    DisposableEffect(Unit) {
        updateAlarm()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) {
                    updateAlarm()
                }
            }
        }
        val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentIsLaunching by rememberUpdatedState(isLaunching)

    val dragAccumX = remember { mutableFloatStateOf(0f) }
    val dragAccumY = remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (!currentIsLaunching && !change.isConsumed) {
                            change.consume()
                            dragAccumX.floatValue += dragAmount.x
                            dragAccumY.floatValue += dragAmount.y
                        }
                    },
                    onDragEnd = {
                        if (!currentIsLaunching) {
                            val threshold = 120f
                            if (abs(dragAccumX.floatValue) > abs(dragAccumY.floatValue)) {
                                // Horizontal Swipe
                                if (dragAccumX.floatValue > threshold) {
                                    currentOnSwipeRight() // Swipe Right
                                } else if (dragAccumX.floatValue < -threshold) {
                                    currentOnSwipeLeft()  // Swipe Left
                                }
                            } else {
                                // Vertical Swipe
                                if (dragAccumY.floatValue < -threshold) {
                                    currentOnSwipeUp()    // Swipe Up
                                } else if (dragAccumY.floatValue > threshold) {
                                    currentOnSwipeDown()  // Swipe Down
                                }
                            }
                        }
                        dragAccumX.floatValue = 0f
                        dragAccumY.floatValue = 0f
                    },
                    onDragCancel = {
                        dragAccumX.floatValue = 0f
                        dragAccumY.floatValue = 0f
                    }
                )
            }
            .combinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onLongClick = { if (!isLaunching) onLongPressHome() },
                onDoubleClick = { onDoubleTapHome() },
                onClick = {}
            )
            .padding(horizontal = 24.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val screenHeight = maxHeight
            
            // Dynamic metrics calculation
            val topPadding = if (screenHeight < 600.dp) 16.dp else 48.dp
            val bottomPadding = if (screenHeight < 600.dp) 16.dp else 24.dp
            val availableHeight = screenHeight - topPadding - bottomPadding
            val favorites = state.favorites.take(10)
            val N = favorites.size
            
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val spacing = if (screenHeight < 600.dp) 3.dp else 6.dp
            
            val rowHeight = if (N > 0) {
                val totalSpacing = spacing * (N - 1)
                val calculated = (availableHeight - totalSpacing) / N
                if (isLandscape) {
                    calculated.coerceIn(28.dp, 44.dp)
                } else {
                    calculated.coerceIn(28.dp, 48.dp)
                }
            } else {
                if (isLandscape) 44.dp else 48.dp
            }
            val rawIconSize = if (isLandscape) {
                if (showHomeIcons && !showHomeLabels) 32.dp else 24.dp
            } else {
                if (showHomeIcons && !showHomeLabels) 38.dp else 30.dp
            }
            val iconSize = if (!isLandscape && N > 0) {
                minOf(rawIconSize, rowHeight - 8.dp)
            } else {
                rawIconSize
            }
            
            if (!isLandscape) {
                // PORTRAIT LAYOUT
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding, bottom = bottomPadding)
                ) {
                    // 1. Clock/Time at the top (full width, centered)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ClockText(
                            currentTime = currentTime,
                            fontSize = if (screenHeight < 600.dp) 48.sp else 58.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Below the clock: Split layout or full width (if empty favorites)
                    if (favorites.isEmpty()) {
                        DateAlarmSection(
                            currentDate = currentDate,
                            nextAlarmText = nextAlarmText,
                            island = state.contextualIsland,
                            onContextualIslandAction = onContextualIslandAction,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            dateAlarmSpacing = 6.dp,
                            islandSpacing = 10.dp,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            val headerContent = @Composable {
                                DateAlarmSection(
                                    currentDate = currentDate,
                                    nextAlarmText = nextAlarmText,
                                    island = state.contextualIsland,
                                    onContextualIslandAction = onContextualIslandAction,
                                    horizontalAlignment = Alignment.Start,
                                    dateAlarmSpacing = 6.dp,
                                    islandSpacing = 10.dp,
                                    modifier = Modifier
                                        .weight(0.6f)
                                        .fillMaxHeight()
                                )
                            }

                            val appsContent = @Composable {
                                Column(
                                    modifier = Modifier
                                        .weight(0.4f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(spacing)
                                    ) {
                                        favorites.forEach { app ->
                                            key(app.packageName) {
                                                HomeAppRowItem(
                                                    app = app,
                                                    onLaunch = { onLaunchApp(app) },
                                                    onLongClick = { selectedAppForMenu = app },
                                                    alignment = alignment,
                                                    isLaunching = isLaunching,
                                                    rowHeight = rowHeight,
                                                    iconSize = iconSize,
                                                    showIcon = showHomeIcons,
                                                    showLabel = showHomeLabels
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (alignment == HomeAlignment.Right) {
                                headerContent()
                                appsContent()
                            } else {
                                appsContent()
                                headerContent()
                            }
                        }
                    }
                }
            } else {
                // LANDSCAPE LAYOUT
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding, bottom = bottomPadding)
                ) {
                    // 1. Clock/Time at the top (full width, centered)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ClockText(
                            currentTime = currentTime,
                            fontSize = if (screenHeight < 600.dp) 38.sp else 44.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Below the clock: Split layout or full width (if empty favorites)
                    if (favorites.isEmpty()) {
                        DateAlarmSection(
                            currentDate = currentDate,
                            nextAlarmText = nextAlarmText,
                            island = state.contextualIsland,
                            onContextualIslandAction = onContextualIslandAction,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            dateAlarmSpacing = 4.dp,
                            islandSpacing = 8.dp,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            val headerContent = @Composable {
                                DateAlarmSection(
                                    currentDate = currentDate,
                                    nextAlarmText = nextAlarmText,
                                    island = state.contextualIsland,
                                    onContextualIslandAction = onContextualIslandAction,
                                    horizontalAlignment = Alignment.Start,
                                    dateAlarmSpacing = 4.dp,
                                    islandSpacing = 8.dp,
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .fillMaxHeight()
                                )
                            }

                            val appsContent = @Composable {
                                Box(
                                    modifier = Modifier
                                        .weight(0.7f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val row1 = favorites.take((N + 1) / 2)
                                    val row2 = favorites.drop((N + 1) / 2)
                                    val landscapeIconSize = if (showHomeIcons && !showHomeLabels) 48.dp else 44.dp

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                        ) {
                                            row1.forEach { app ->
                                                key(app.packageName) {
                                                    LandscapeAppGridItem(
                                                        app = app,
                                                        onLaunch = { onLaunchApp(app) },
                                                        onLongClick = { selectedAppForMenu = app },
                                                        isLaunching = isLaunching,
                                                        showIcon = showHomeIcons,
                                                        showLabel = showHomeLabels,
                                                        iconSize = landscapeIconSize
                                                    )
                                                }
                                            }
                                        }
                                        if (row2.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                            ) {
                                                row2.forEach { app ->
                                                    key(app.packageName) {
                                                        LandscapeAppGridItem(
                                                            app = app,
                                                            onLaunch = { onLaunchApp(app) },
                                                            onLongClick = { selectedAppForMenu = app },
                                                            isLaunching = isLaunching,
                                                            showIcon = showHomeIcons,
                                                            showLabel = showHomeLabels,
                                                            iconSize = landscapeIconSize
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (alignment == HomeAlignment.Right) {
                                headerContent()
                                appsContent()
                            } else {
                                appsContent()
                                headerContent()
                            }
                        }
                    }
                }
            }
        }

        // Long Press Context Menu
        selectedAppForMenu?.let { app ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            fun dismissMenu(onDismissed: () -> Unit = {}) {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        selectedAppForMenu = null
                        onDismissed()
                    }
                }
            }

            val shortcuts = remember(app) {
                getCombinedShortcutsForApp(
                    context = context,
                    app = app,
                    onDismiss = { dismissMenu {} }
                )
            }
            ModalBottomSheet(
                onDismissRequest = { selectedAppForMenu = null },
                sheetState = sheetState,
                contentWindowInsets = { WindowInsets.systemBars },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                modifier = Modifier.testTag("app_context_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BottomSheetAppHeader(
                        label = app.label,
                        packageName = app.packageName
                    )

                    if (shortcuts.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.shortcuts_header),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            shortcuts.forEach { shortcut ->
                                OutlinedButton(
                                    onClick = {
                                        dismissMenu { shortcut.action() }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(shortcut.label, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                dismissMenu { onLongPressHome() }
                            },
                            modifier = Modifier.weight(1f).testTag("shortcut_launcher_settings"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launcher Settings", fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open app settings", Toast.LENGTH_SHORT).show()
                                }
                                dismissMenu()
                            },
                            modifier = Modifier.weight(1f).testTag("shortcut_app_info"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("App Info", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Button(
                        onClick = {
                            dismissMenu { onToggleFavorite(app) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("shortcut_remove")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove from Home", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
        state.databaseError?.let { errorMsg ->
            var visible by remember(errorMsg) { mutableStateOf(true) }
            if (visible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .padding(horizontal = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { visible = false }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Database Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Stale offline data shown due to a database load error.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Dismiss",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                        )
                    }
                }
            }
        }
    }
}


private val adaptiveMaskCache = java.util.WeakHashMap<Drawable, androidx.compose.ui.graphics.Path>()

internal fun getOrCreateAdaptiveMask(drawable: Drawable, width: Int, height: Int): androidx.compose.ui.graphics.Path? {
    if (drawable !is AdaptiveIconDrawable) return null
    synchronized(adaptiveMaskCache) {
        val existing = adaptiveMaskCache[drawable]
        if (existing != null) return existing
        drawable.setBounds(0, 0, width, height)
        val mask = drawable.iconMask?.asComposePath()
        if (mask != null) {
            adaptiveMaskCache[drawable] = mask
        }
        return mask
    }
}

/**
 * Applies a subtle dark outline border around home screen app icons when a light theme is active.
 *
 * For AdaptiveIconDrawable (Android 8.0+), it retrieves the device's native system mask
 * (AdaptiveIconDrawable.iconMask) so the stroke precisely traces the OEM icon shape
 * (circle, squircle, or rounded rect). For legacy or bitmap drawables, it falls back to
 * a standard rounded-rect shape (22% corner radius).
 *
 * On dark themes, this modifier is an exact no-op.
 */
fun Modifier.homeIconBorder(
    isLight: Boolean,
    drawable: Drawable?,
    strokeWidth: Dp = 1.dp,
    borderColor: Color = Color(0x38000000)
): Modifier = if (!isLight || drawable == null) {
    this
} else {
    this.drawWithContent {
        drawContent()
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@drawWithContent

        val strokePx = strokeWidth.toPx()
        val halfStroke = strokePx / 2f

        if (drawable is AdaptiveIconDrawable) {
            val mask = getOrCreateAdaptiveMask(drawable, width.toInt(), height.toInt())
            if (mask != null) {
                drawPath(
                    path = mask,
                    color = borderColor,
                    style = Stroke(width = strokePx)
                )
                return@drawWithContent
            }
        }

        drawRoundRect(
            color = borderColor,
            topLeft = Offset(halfStroke, halfStroke),
            size = Size(width - strokePx, height - strokePx),
            cornerRadius = CornerRadius(width * 0.22f, height * 0.22f),
            style = Stroke(width = strokePx)
        )
    }
}

@Composable
fun HomeAppRowItem(
    app: LauncherApp,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit,
    alignment: HomeAlignment,
    modifier: Modifier = Modifier,
    isLaunching: Boolean = false,
    rowHeight: Dp = 56.dp,
    iconSize: Dp = 36.dp,
    showIcon: Boolean = true,
    showLabel: Boolean = true
) {
    val isLightTheme = LocalIsLightTheme.current
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val boxAlignment = when (alignment) {
        HomeAlignment.Left -> Alignment.CenterStart
        HomeAlignment.Right -> Alignment.CenterEnd
    }

    val repo = remember { (context.applicationContext as com.alauncher.app.MinimalSmartLauncherApp).appRepository }
    var iconDrawable by remember(app.packageName) {
        mutableStateOf(app.icon ?: repo.getCachedIcon(app.packageName))
    }
    LaunchedEffect(app.packageName) {
        if (iconDrawable == null) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repo.getIcon(app.packageName)
            }
            iconDrawable = loaded
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .appIconGestureHandler(
                onTap = { if (!isLaunching) onLaunch() },
                onSwipeRight = { if (!isLaunching) onLongClick() },
                isLaunching = isLaunching,
                layoutDirection = layoutDirection
            ),
        color = Color.Transparent
    ) {
        Box(
            contentAlignment = boxAlignment,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (alignment == HomeAlignment.Left) Arrangement.Start else Arrangement.End
            ) {
                val iconContent = @Composable {
                    if (showIcon && iconDrawable != null) {
                        androidx.compose.foundation.Image(
                            painter = rememberDrawablePainter(drawable = iconDrawable!!),
                            contentDescription = app.label,
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                            modifier = Modifier
                                .size(iconSize)
                                .homeIconBorder(
                                    isLight = isLightTheme,
                                    drawable = iconDrawable
                                )
                        )
                    }
                }
                val labelContent = @Composable {
                    if (showLabel) {
                        Text(
                            text = app.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                if (alignment == HomeAlignment.Left) {
                    iconContent()
                    if (showIcon && iconDrawable != null && showLabel) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    labelContent()
                } else {
                    labelContent()
                    if (showIcon && iconDrawable != null && showLabel) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    iconContent()
                }
            }
        }
    }
}

@Composable
fun LandscapeAppGridItem(
    app: LauncherApp,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLaunching: Boolean = false,
    showIcon: Boolean = true,
    showLabel: Boolean = true,
    iconSize: Dp = 44.dp
) {
    val isLightTheme = LocalIsLightTheme.current
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val repo = remember { (context.applicationContext as com.alauncher.app.MinimalSmartLauncherApp).appRepository }
    var iconDrawable by remember(app.packageName) {
        mutableStateOf(app.icon ?: repo.getCachedIcon(app.packageName))
    }
    LaunchedEffect(app.packageName) {
        if (iconDrawable == null) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repo.getIcon(app.packageName)
            }
            iconDrawable = loaded
        }
    }

    Surface(
        modifier = modifier
            .width(80.dp)
            .height(75.dp)
            .appIconGestureHandler(
                onTap = { if (!isLaunching) onLaunch() },
                onSwipeRight = { if (!isLaunching) onLongClick() },
                isLaunching = isLaunching,
                layoutDirection = layoutDirection
            ),
        color = Color.Transparent
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (showIcon && iconDrawable != null) {
                androidx.compose.foundation.Image(
                    painter = rememberDrawablePainter(drawable = iconDrawable!!),
                    contentDescription = app.label,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                    modifier = Modifier
                        .size(iconSize)
                        .homeIconBorder(
                            isLight = isLightTheme,
                            drawable = iconDrawable
                        )
                )
            }
            if (showLabel) {
                if (showIcon && iconDrawable != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = app.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}



data class CustomAppShortcut(
    val label: String,
    val action: () -> Unit
)

fun getShortcutsByFlag(
    context: android.content.Context,
    app: com.alauncher.app.model.LauncherApp,
    flags: Int,
    onDismiss: () -> Unit
): List<CustomAppShortcut> {
    val result = mutableListOf<CustomAppShortcut>()
    val launcherApps = context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
    val profiles = launcherApps?.profiles
    if (profiles != null && profiles.isNotEmpty()) {
        try {
            val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                setQueryFlags(flags)
                setPackage(app.packageName)
            }
            val shortcuts = launcherApps.getShortcuts(query, profiles.first())
            if (shortcuts != null) {
                for (shortcut in shortcuts) {
                    val label = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                        ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
                        ?: continue
                    result.add(
                        CustomAppShortcut(label = label) {
                            try {
                                launcherApps.startShortcut(app.packageName, shortcut.id, null, null, profiles.first())
                            } catch (e: Exception) {
                                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (intent != null) {
                                    context.startActivity(intent)
                                }
                            }
                            onDismiss()
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore security or other errors
        }
    }
    return result
}

fun getCombinedShortcutsForApp(
    context: android.content.Context,
    app: com.alauncher.app.model.LauncherApp,
    onDismiss: () -> Unit
): List<CustomAppShortcut> {
    val combined = mutableListOf<CustomAppShortcut>()

    // 1. Fetch App Internal Shortcuts (core actions from manifest)
    val internalShortcuts = getShortcutsByFlag(
        context,
        app,
        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
        onDismiss
    )
    combined.addAll(internalShortcuts)

    if (combined.size >= 5) {
        return combined.take(5)
    }

    // 2. Fetch User-Centric Dynamic/Pinned Shortcuts published directly by the app
    val dynamicShortcuts = getShortcutsByFlag(
        context,
        app,
        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or 
        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        onDismiss
    )
    for (userShortcut in dynamicShortcuts) {
        if (combined.size >= 5) break
        val isDuplicate = combined.any { it.label.equals(userShortcut.label, ignoreCase = true) }
        if (!isDuplicate) {
            combined.add(userShortcut)
        }
    }

    return combined
}

private fun formatChronometer(targetTime: Long, isCountDown: Boolean): String {
    val now = System.currentTimeMillis()
    val diff = if (isCountDown) {
        targetTime - now
    } else {
        now - targetTime
    }
    val absoluteDiff = if (diff < 0) 0L else diff
    val totalSeconds = absoluteDiff / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun ContextualIslandWidget(
    island: ContextualIsland?,
    onAction: (ContextualActionType, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current

    AnimatedContent(
        targetState = island,
        transitionSpec = {
            (slideInVertically { height -> -height } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                .togetherWith(slideOutVertically { height -> -height } + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
        },
        label = "contextual_island",
        modifier = modifier
    ) { currentIsland ->
        if (currentIsland != null) {
            LaunchedEffect(currentIsland) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
            val isClickable = currentIsland.actionType != ContextualActionType.NONE
            
            var offsetX by remember(currentIsland) { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val swipeThresholdPx = with(density) { 72.dp.toPx() }
            val animatedOffsetX by animateFloatAsState(
                targetValue = offsetX,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "island_swipe_offset"
            )

            var timerText by remember(currentIsland) {
                mutableStateOf(if (currentIsland.showChronometer) formatChronometer(currentIsland.whenValue, currentIsland.chronometerCountDown) else "")
            }
            if (currentIsland.showChronometer) {
                LaunchedEffect(currentIsland.whenValue, currentIsland.chronometerCountDown) {
                    while (true) {
                        delay(1000)
                        timerText = formatChronometer(currentIsland.whenValue, currentIsland.chronometerCountDown)
                    }
                }
            }
            
            var iconDrawable by remember(currentIsland.packageName) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
            LaunchedEffect(currentIsland.packageName) {
                if (currentIsland.packageName != null && !currentIsland.packageName.startsWith("__")) {
                    val repo = (context.applicationContext as com.alauncher.app.MinimalSmartLauncherApp).appRepository
                    iconDrawable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repo.getIcon(currentIsland.packageName)
                    }
                } else {
                    iconDrawable = null
                }
            }

            val displayText = if (currentIsland.showChronometer) {
                "${currentIsland.text} • $timerText"
            } else {
                currentIsland.text
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = animatedOffsetX
                        alpha = (1f - (abs(animatedOffsetX) / (swipeThresholdPx * 2.5f))).coerceIn(0.1f, 1f)
                    }
                    .pointerInput(currentIsland) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offsetX) > swipeThresholdPx) {
                                    onAction(ContextualActionType.DISMISS, null)
                                }
                                offsetX = 0f
                            },
                            onDragCancel = {
                                offsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount
                            }
                        )
                    }
                    .clickable(enabled = isClickable) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onAction(currentIsland.actionType, currentIsland.actionPayload)
                    }
                    .testTag("contextual_island")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (iconDrawable != null) {
                        androidx.compose.foundation.Image(
                            painter = rememberDrawablePainter(drawable = iconDrawable!!),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = displayText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (currentIsland.actionType == ContextualActionType.COPY_OTP) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        onAction(currentIsland.actionType, currentIsland.actionPayload)
                                    }
                                ) {
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        } else if (currentIsland.actionType == ContextualActionType.OPEN_LINK) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        onAction(currentIsland.actionType, currentIsland.actionPayload)
                                    }
                                ) {
                                    Text(
                                        text = "Join",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
