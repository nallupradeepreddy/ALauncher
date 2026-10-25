package com.alauncher.app.ui.utils

import android.content.Context
import android.provider.Settings
import android.content.ComponentName
import androidx.core.net.toUri

/**
 * Checks system settings programmatically to see if notification listener permission is granted
 */
fun isNotificationAccessGranted(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}

/**
 * Checks if a specific package currently has the SYSTEM_ALERT_WINDOW (Display over other apps)
 * permission granted.
 */
@Suppress("DEPRECATION")
fun hasOverlayPermission(context: Context, packageName: String): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
    return try {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        val uid = packageInfo.applicationInfo?.uid ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName)
        }
        mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

/**
 * Returns a set of package names for installed applications that currently have
 * the SYSTEM_ALERT_WINDOW permission granted (excluding ALauncher itself).
 */
fun getAppsWithOverlayPermission(context: Context): Set<String> {
    val pm = context.packageManager
    val result = mutableSetOf<String>()
    try {
        val packages = pm.getInstalledPackages(android.content.pm.PackageManager.GET_PERMISSIONS)
        for (pkg in packages) {
            val pkgName = pkg.packageName
            if (pkgName == context.packageName) continue
            val requested = pkg.requestedPermissions ?: continue
            if (requested.contains(android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                if (hasOverlayPermission(context, pkgName)) {
                    result.add(pkgName)
                }
            }
        }
    } catch (_: Exception) {
        // Safe fallback
    }
    return result
}

private val VALID_PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

/**
 * Validates whether a given package name follows standard Android package naming specifications.
 */
fun isValidPackageName(packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    return VALID_PACKAGE_REGEX.matches(packageName)
}

/**
 * Creates an Intent to navigate the user directly to the "Display over other apps"
 * permission screen for a specific package, or the system-wide list if no package is provided.
 *
 * Includes defensive validation against malformed or non-installed package names.
 */
fun createManageOverlayIntent(packageName: String? = null, context: Context? = null): android.content.Intent {
    val sanitizedPackage = packageName?.trim()?.takeIf { isValidPackageName(it) }?.let { pkg ->
        if (context != null) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                pkg
            } catch (_: Exception) {
                null
            }
        } else {
            pkg
        }
    }

    return if (sanitizedPackage != null) {
        android.content.Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$sanitizedPackage".toUri()
        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
    } else {
        android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}


