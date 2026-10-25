package com.alauncher.app.ui.settings.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Standardized permission handler for settings toggles.
 * Provides instant switch feedback, runtime permission dialogs, state reversion on denial,
 * and non-intrusive Snackbar notifications with direct system settings navigation.
 */
class PermissionSettingHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState
) {
    /**
     * Shows a Snackbar with a SETTINGS action button that opens system settings.
     */
    fun showPermissionDeniedSnackbar(
        message: String,
        settingsIntent: Intent? = null
    ) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "SETTINGS",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                val defaultIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

                try {
                    val intent = settingsIntent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } ?: defaultIntent
                    context.startActivity(intent)
                } catch (_: Exception) {
                    if (settingsIntent != null) {
                        try {
                            context.startActivity(defaultIntent)
                        } catch (_: Exception) {
                            // Safe fallback handling
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles standard runtime permissions (e.g. READ_CALENDAR).
     */
    fun handleRuntimeToggle(
        permission: String,
        isGranted: Boolean,
        onSetEnabled: (Boolean) -> Unit,
        requestPermission: (String) -> Unit,
        newChecked: Boolean
    ) {
        onSetEnabled(newChecked)
        if (newChecked && !isGranted) {
            requestPermission(permission)
        }
    }

    /**
     * Called when standard runtime permission request completes.
     */
    fun onRuntimePermissionResult(
        isGranted: Boolean,
        featureName: String,
        onSetEnabled: (Boolean) -> Unit
    ) {
        if (!isGranted) {
            onSetEnabled(false)
            showPermissionDeniedSnackbar(
                message = "$featureName permission is required to enable this feature."
            )
        }
    }

}

@Composable
fun rememberPermissionSettingHandler(
    snackbarHostState: SnackbarHostState
): PermissionSettingHandler {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope, snackbarHostState) {
        PermissionSettingHandler(context, scope, snackbarHostState)
    }
}
