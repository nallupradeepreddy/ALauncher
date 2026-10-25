package com.alauncher.app.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alauncher.app.ui.components.AppDialogHeader
import com.alauncher.app.ui.components.rememberDrawablePainter

@Composable
fun AppIcon(
    packageName: String,
    showIcon: Boolean,
    modifier: Modifier = Modifier
) {
    if (!showIcon) return
    val context = LocalContext.current
    var iconDrawable by remember(packageName) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    LaunchedEffect(packageName) {
        val repo = (context.applicationContext as com.alauncher.app.MinimalSmartLauncherApp).appRepository
        iconDrawable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.getIcon(packageName)
        }
    }
    if (iconDrawable != null) {
        androidx.compose.foundation.Image(
            painter = rememberDrawablePainter(drawable = iconDrawable!!),
            contentDescription = null,
            modifier = modifier
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            shape = RoundedCornerShape(16.dp),
            border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
    )
}

@Composable
fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.background,
            checkedTrackColor = MaterialTheme.colorScheme.tertiary,
            checkedBorderColor = Color.Transparent,
            
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            
            disabledCheckedThumbColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
            disabledCheckedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
            disabledCheckedBorderColor = Color.Transparent,
            
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
            disabledUncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        ),
        thumbContent = {
            Spacer(modifier = Modifier.size(SwitchDefaults.IconSize))
        }
    )
}

@Composable
fun PermissionBanner(
    buttonText: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("↳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onGrant,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(buttonText, fontSize = 11.sp)
        }
    }
}

@Composable
fun SettingsDialogHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null
) {
    AppDialogHeader(
        title = title,
        modifier = modifier,
        icon = icon,
        subtitle = subtitle
    )
}

