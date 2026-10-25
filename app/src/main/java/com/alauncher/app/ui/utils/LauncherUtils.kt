package com.alauncher.app.ui.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.alauncher.app.R

object LauncherUtils {
    /**
     * Checks if the current app is the default launcher.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent, 
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val currentDefaultHomePackage = resolveInfo?.activityInfo?.packageName
        return currentDefaultHomePackage == context.packageName
    }

    /**
     * Requests the system to set the current app as the default home launcher.
     * Uses RoleManager on Android 10+ (Q), falling back to ACTION_HOME_SETTINGS or ACTION_SETTINGS.
     */
    fun requestDefaultLauncher(
        context: Context,
        roleLauncher: ActivityResultLauncher<Intent>? = null
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        Toast.makeText(context, R.string.already_default, Toast.LENGTH_SHORT).show()
                    } else {
                        val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        if (roleLauncher != null) {
                            roleLauncher.launch(roleIntent)
                        } else {
                            roleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(roleIntent)
                        }
                    }
                    return
                }
            }
            val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, R.string.toast_search_default_apps, Toast.LENGTH_LONG).show()
                } catch (exc: Exception) {
                    Toast.makeText(context, R.string.toast_system_configs_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Resiliently launches an app by package name for mobile Android.
     * First attempts LauncherApps.startMainActivity across all user profiles (official Android Launcher API),
     * then falls back to PackageManager getLaunchIntentForPackage.
     */
    fun launchAppPackage(context: Context, packageName: String): Boolean {
        // 1. Primary: Use LauncherApps framework service across user profiles
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
            if (launcherApps != null && userManager != null) {
                val profiles = userManager.userProfiles
                for (profile in profiles) {
                    val activities = launcherApps.getActivityList(packageName, profile)
                    if (activities.isNotEmpty()) {
                        launcherApps.startMainActivity(activities[0].componentName, profile, null, null)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherUtils", "LauncherApps startMainActivity failed for $packageName", e)
        }

        // 2. Secondary fallback: PackageManager getLaunchIntentForPackage
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherUtils", "getLaunchIntentForPackage failed for $packageName", e)
        }

        return false
    }
}
