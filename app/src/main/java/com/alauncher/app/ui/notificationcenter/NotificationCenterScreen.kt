package com.alauncher.app.ui.notificationcenter

import com.alauncher.app.ui.utils.isNotificationAccessGranted
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import com.alauncher.app.R
import com.alauncher.app.model.LauncherNotification
import com.alauncher.app.model.displayTitlePreview
import com.alauncher.app.model.displayBodyPreview
import com.alauncher.app.model.hasContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    state: NotificationCenterState,
    onBack: () -> Unit,
    onLaunchNotification: (String) -> Unit,
    onDismissNotification: (String) -> Unit,
    onClearAll: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isPermissionGranted = remember(permissionRefreshKey) {
        isNotificationAccessGranted(context)
    }

    var expandedAppGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val validNormalList = remember(state.normalList) {
        state.normalList.filter { it.hasContent }
    }

    // Immediate / Priority notifications (OTPs, calls, calendar alerts) remain in the system
    // status bar and Home Ambient Island by design; only filtered notifications appear in this center.
    val validMutedList = remember(state.mutedList) {
        state.mutedList.filter { it.hasContent }
    }
    var isSilencedExpanded by remember { mutableStateOf(false) }

    val groupedNotifications = remember(validNormalList) {
        validNormalList.groupBy { it.packageName }
            .toList()
            .sortedByDescending { (_, notifications) ->
                notifications.maxOfOrNull { it.postTime } ?: 0L
            }
    }

    val totalNotificationCount = validNormalList.size + validMutedList.size
    val showBottomDock = isPermissionGranted && (totalNotificationCount > 0 || state.searchQuery.isNotEmpty())

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("notification_center_scaffold"),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Notification Center", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = totalNotificationCount > 0,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable(role = Role.Button) { onClearAll() }
                                .testTag("clear_all_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_all_notifications),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = stringResource(R.string.clear_all_count, totalNotificationCount),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 24.dp)
            ) {
                // Permission Block showing active onboarding guidelines if listener is disabled
                if (!isPermissionGranted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .testTag("permission_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Notification Access Required",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Allow ALauncher to read incoming alerts to filter out distractions and organize your notification center locally.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("grant_permission_button")
                            ) {
                                Text("Grant Access")
                            }
                        }
                    }
                }

                if (validNormalList.isEmpty() && validMutedList.isEmpty()) {
                    // Calm, empty state representation
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (state.searchQuery.isNotEmpty()) {
                                Text(
                                    "No matching notifications",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Try a different search term",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                )
                            } else {
                                Text(
                                    "No distractions.",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Your notifications are completely quiet.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 16.dp
                        )
                    ) {
                    // Category II: Ordinary Notifications (Grouped by App)
                    if (groupedNotifications.isNotEmpty()) {
                        item {
                            SectionHeaderLabel(text = "FILTERED NOTIFICATIONS")
                        }
                        
                        groupedNotifications.forEach { (packageName, notifications) ->
                            val isExpanded = expandedAppGroups.contains(packageName)
                            
                            if (notifications.size == 1) {
                                val notif = notifications[0]
                                item(key = "norm:${notif.key}") {
                                    val dismissState = rememberSwipeToDismissBoxState()
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        onDismiss = { dismissValue ->
                                            if (dismissValue == SwipeToDismissBoxValue.StartToEnd || 
                                                dismissValue == SwipeToDismissBoxValue.EndToStart
                                            ) {
                                                onDismissNotification(notif.key)
                                            }
                                        },
                                        backgroundContent = { DismissBackground(dismissState) },
                                        content = {
                                            NotificationCardItem(
                                                notification = notif,
                                                isPriority = false,
                                                onSelection = { onLaunchNotification(notif.key) },
                                                onDismiss = { onDismissNotification(notif.key) },
                                                onLongClick = { }
                                            )
                                        }
                                    )
                                }
                            } else {
                                if (isExpanded) {
                                    item(key = "expanded_group:$packageName") {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("expanded_group_$packageName")
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                val headerDismissState = rememberSwipeToDismissBoxState()
                                                SwipeToDismissBox(
                                                    state = headerDismissState,
                                                    onDismiss = { dismissValue ->
                                                        if (dismissValue == SwipeToDismissBoxValue.StartToEnd || 
                                                            dismissValue == SwipeToDismissBoxValue.EndToStart
                                                        ) {
                                                            notifications.forEach { onDismissNotification(it.key) }
                                                        }
                                                    },
                                                    backgroundContent = { DismissBackground(headerDismissState) },
                                                    content = {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(MaterialTheme.colorScheme.surface)
                                                                .clickable { expandedAppGroups = expandedAppGroups - packageName }
                                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = notifications[0].appName.uppercase(Locale.ROOT),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 1.25.sp,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Surface(
                                                                onClick = { expandedAppGroups = expandedAppGroups - packageName },
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                                                modifier = Modifier.testTag("collapse_stack_button")
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "${notifications.size} updates",
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Icon(
                                                                        imageVector = Icons.Default.ExpandLess,
                                                                        contentDescription = stringResource(R.string.collapse_desc),
                                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                                
                                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                                notifications.forEachIndexed { index, notif ->
                                                    if (index > 0) {
                                                        HorizontalDivider(
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                                            modifier = Modifier.padding(horizontal = 16.dp)
                                                        )
                                                    }
                                                    key(notif.key) {
                                                        val dismissState = rememberSwipeToDismissBoxState()
                                                        SwipeToDismissBox(
                                                            state = dismissState,
                                                            onDismiss = { dismissValue ->
                                                                if (dismissValue == SwipeToDismissBoxValue.StartToEnd || 
                                                                    dismissValue == SwipeToDismissBoxValue.EndToStart
                                                                ) {
                                                                    onDismissNotification(notif.key)
                                                                }
                                                            },
                                                            backgroundContent = { DismissBackground(dismissState) },
                                                            content = {
                                                                NotificationCardItem(
                                                                    notification = notif,
                                                                    isPriority = false,
                                                                    onSelection = { onLaunchNotification(notif.key) },
                                                                    onDismiss = { onDismissNotification(notif.key) },
                                                                    onLongClick = { },
                                                                    containerColor = Color.Transparent
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    item(key = "stack:$packageName") {
                                        val latestNotif = notifications.maxBy { it.postTime }
                                        val dismissState = rememberSwipeToDismissBoxState()
                                        SwipeToDismissBox(
                                            state = dismissState,
                                            onDismiss = { dismissValue ->
                                                if (dismissValue == SwipeToDismissBoxValue.StartToEnd || 
                                                    dismissValue == SwipeToDismissBoxValue.EndToStart
                                                ) {
                                                    notifications.forEach { onDismissNotification(it.key) }
                                                }
                                            },
                                            backgroundContent = { DismissBackground(dismissState) },
                                            content = {
                                                NotificationStackItem(
                                                    latestNotification = latestNotif,
                                                    totalCount = notifications.size,
                                                    onExpand = { expandedAppGroups = expandedAppGroups + packageName },
                                                    onDismiss = { notifications.forEach { onDismissNotification(it.key) } }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Category III: Silenced Notifications (Focus Mode / Quiet Hours)
                    if (validMutedList.isNotEmpty()) {
                        item(key = "silenced_header") {
                            SectionHeaderLabel(text = "SILENCED DURING FOCUS / QUIET HOURS (${validMutedList.size})")
                        }
                        item(key = "silenced_card") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSilencedExpanded = !isSilencedExpanded }
                                    .testTag("silenced_notifications_card")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Silenced Notifications",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val alertText = if (validMutedList.size == 1) "1 alert" else "${validMutedList.size} alerts"
                                            val contextText = if (state.isFocusMode) "while focus was active."
                                                else if (state.isQuietHours) "while quiet hours were active."
                                                else "while focus / quiet hours were active."
                                            Text(
                                                text = "$alertText kept quiet $contextText",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isSilencedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }

                                    AnimatedVisibility(visible = isSilencedExpanded) {
                                        Column(
                                            modifier = Modifier.padding(top = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            validMutedList.forEach { item ->
                                                key(item.key) {
                                                    val dismissState = rememberSwipeToDismissBoxState()
                                                    SwipeToDismissBox(
                                                        state = dismissState,
                                                        onDismiss = { dismissValue ->
                                                            if (dismissValue == SwipeToDismissBoxValue.StartToEnd ||
                                                                dismissValue == SwipeToDismissBoxValue.EndToStart
                                                            ) {
                                                                onDismissNotification(item.key)
                                                            }
                                                        },
                                                        backgroundContent = { DismissBackground(dismissState) },
                                                        content = {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(MaterialTheme.colorScheme.surface)
                                                                    .clickable { onLaunchNotification(item.key) }
                                                                    .padding(vertical = 4.dp)
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    val titleText = item.displayTitlePreview
                                                                    if (!titleText.isNullOrBlank()) {
                                                                        Text(
                                                                            text = titleText,
                                                                            fontSize = 13.sp,
                                                                            fontWeight = FontWeight.Medium,
                                                                            color = MaterialTheme.colorScheme.onSurface,
                                                                            maxLines = 2,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                    val body = item.displayBodyPreview
                                                                    if (!body.isNullOrBlank()) {
                                                                        Text(
                                                                            text = body,
                                                                            fontSize = 12.sp,
                                                                            maxLines = 3,
                                                                            overflow = TextOverflow.Ellipsis,
                                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                                        )
                                                                    }
                                                                }
                                                                IconButton(
                                                                    onClick = { onDismissNotification(item.key) },
                                                                    modifier = Modifier.size(36.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Clear,
                                                                        contentDescription = stringResource(R.string.dismiss),
                                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
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
                }

                // Bottom Reachable Dock: Search Bar (Docked below list so notifications never bleed underneath)
                if (showBottomDock) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(top = 8.dp, bottom = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "Search notifications...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search_desc),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clear_search),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notification_search_input")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeaderLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.25.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationCardItem(
    notification: LauncherNotification,
    isPriority: Boolean,
    onSelection: () -> Unit,
    onDismiss: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null
) {
    val dateString = remember(notification.postTime) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(notification.postTime))
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPriority) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: if (isPriority) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelection,
                onLongClick = onLongClick
            )
            .testTag("notification_card_${notification.key}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.appName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•  $dateString",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                val titleText = notification.displayTitlePreview
                if (!titleText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = titleText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val body = notification.displayBodyPreview
                if (!body.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            // Clean 48dp+ accessibility dismiss target
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("dismiss_button_${notification.key}")
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.dismiss_alert_desc),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val direction = dismissState.dismissDirection
    if (direction == SwipeToDismissBoxValue.Settled) return

    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = alignment
    ) {
        Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = stringResource(R.string.dismiss),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun NotificationStackItem(
    latestNotification: LauncherNotification,
    totalCount: Int,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateString = remember(latestNotification.postTime) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(latestNotification.postTime))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Layer 2 (Bottom-most card background)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.33f)),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(72.dp)
                .offset(y = 8.dp)
        ) {}
        
        // Layer 1 (Middle card background)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.66f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(72.dp)
                .offset(y = 4.dp)
        ) {}

        // Top Card (Main notification details)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = latestNotification.appName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•  $dateString",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    val titleText = latestNotification.displayTitlePreview
                    if (!titleText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = titleText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val body = latestNotification.displayBodyPreview
                    if (!body.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = body,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp).testTag("expand_stack_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$totalCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.expand_stack_desc),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.dismiss_alert_desc),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


