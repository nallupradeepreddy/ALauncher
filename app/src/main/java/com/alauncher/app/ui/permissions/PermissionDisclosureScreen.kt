package com.alauncher.app.ui.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.filled.Security
import com.alauncher.app.ui.components.AppDialogHeader
import com.alauncher.app.R
import com.alauncher.app.data.settings.SettingsRepository
import com.alauncher.app.ui.utils.isNotificationAccessGranted

@Composable
fun rememberPermissionRefreshKey(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var key by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                key++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return key
}

@Composable
fun PermissionDisclosureScreen(
    onGrantNotificationAccess: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val permissionRefreshKey = rememberPermissionRefreshKey()
    var showRestrictedDialog by remember { mutableStateOf(false) }

    val isNotifGranted = remember(permissionRefreshKey) {
        isNotificationAccessGranted(context)
    }

    // Android 13+ Restricted Settings Hint (only applicable to sideloaded/non-store installs)
    val isRestrictedSettingsApplicable = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val installSource = context.packageManager.getInstallSourceInfo(context.packageName)
                val knownStores = setOf(
                    "com.android.vending",
                    "com.sec.android.app.samsungapps",
                    "com.amazon.venezia",
                    "com.huawei.appmarket"
                )
                installSource.installingPackageName !in knownStores
            } catch (_: Exception) {
                true
            }
        } else {
            false
        }
    }

    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isNarrow = maxWidth < 360.dp
        val horizontalPadding = if (isNarrow) 16.dp else 24.dp
        val spacerSmall = if (isNarrow) 8.dp else 12.dp
        val spacerMedium = if (isNarrow) 14.dp else 20.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.permission_disclosure_title),
                    fontSize = if (isNarrow) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.permission_disclosure_subtitle),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = if (isNarrow) 13.sp else 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(spacerMedium))

                // Permission Cards
                PermissionItem(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.permission_item_app_library_title),
                    description = stringResource(R.string.permission_item_app_library_desc)
                )

                Spacer(modifier = Modifier.height(spacerSmall))

                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.permission_item_notif_filter_title),
                    description = stringResource(R.string.permission_item_notif_filter_desc)
                )

                Spacer(modifier = Modifier.height(spacerMedium))

                // Action 1: Notification Access
                if (isNotifGranted) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.permission_notif_enabled),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.permission_notif_enabled),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onGrantNotificationAccess,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("enable_notification_access_button")
                    ) {
                        Text(stringResource(R.string.permission_enable_notif), fontWeight = FontWeight.Bold)
                    }

                    if (isRestrictedSettingsApplicable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showRestrictedDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.permission_restricted_settings_hint),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.permission_restricted_settings_hint),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacerSmall))

                // Action 3: Continue to Home
                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("go_to_home_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNotifGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.permission_go_to_home),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Privacy Policy Link
                val annotatedString = buildAnnotatedString {
                    append("By continuing, you agree to our ")
                    withLink(
                        LinkAnnotation.Url(
                            url = SettingsRepository.PRIVACY_POLICY_URL,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        )
                    ) {
                        append("Privacy Policy")
                    }
                    append(" regarding on-device data processing.")
                }
                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }

    // Android 13+ Restricted Settings Explanation Dialog
    if (showRestrictedDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            title = {
                AppDialogHeader(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.permission_restricted_settings_title),
                    subtitle = stringResource(R.string.permission_restricted_settings_subtitle)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.permission_restricted_settings_desc, stringResource(R.string.app_name)),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestrictedDialog = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.permission_restricted_settings_open_app_info), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestrictedDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.permission_restricted_settings_close), fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
