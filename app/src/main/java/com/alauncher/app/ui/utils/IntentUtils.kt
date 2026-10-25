package com.alauncher.app.ui.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast

private const val TAG = "IntentUtils"

fun openClockApp(context: Context) {
    val intents = listOf(
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        Intent(AlarmClock.ACTION_SET_ALARM)
    )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Clock intent not found: ${intent.action}", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception launching clock intent: ${intent.action}", e)
        }
    }
    val clockPackages = listOf(
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock"
    )
    val pm = context.packageManager
    for (pkg in clockPackages) {
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch clock package: $pkg", e)
            }
        }
    }
    Toast.makeText(context, "No clock app found", Toast.LENGTH_SHORT).show()
}

fun openCalendarApp(context: Context) {
    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
        Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI),
        Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time"))
    )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Calendar intent not found: ${intent.data ?: intent.categories}", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception launching calendar intent", e)
        }
    }
    Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
}

fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Direct alarm activity not found, falling back to clock app", e)
        openClockApp(context)
    } catch (e: SecurityException) {
        Log.w(TAG, "Security exception launching alarm activity, falling back to clock app", e)
        openClockApp(context)
    }
}
