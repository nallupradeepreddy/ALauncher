package com.alauncher.app.data.calendar

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val location: String? = null,
    val description: String? = null
)

class CalendarRepository(
    context: Context
) {
    private val context = context.applicationContext

    private fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getTodayEvents(): List<CalendarEvent> {
        if (!isPermissionGranted()) {
            return emptyList()
        }
        
        val events = mutableListOf<CalendarEvent>()
        try {
            val contentResolver = context.contentResolver
            
            // Calculate start and end of today
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val endOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            // Query instances of events for today
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, startOfDay)
            android.content.ContentUris.appendId(builder, endOfDay)
            
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION
            )
            
            val selection = "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED} OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL"
            
            val cursor = contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            
            cursor?.use { c ->
                val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val locationIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val descriptionIdx = c.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                
                while (c.moveToNext()) {
                    val title = if (titleIdx >= 0) c.getString(titleIdx) else ""
                    val begin = if (beginIdx >= 0) c.getLong(beginIdx) else 0L
                    val end = if (endIdx >= 0) c.getLong(endIdx) else 0L
                    val allDay = if (allDayIdx >= 0) c.getInt(allDayIdx) != 0 else false
                    val location = if (locationIdx >= 0) c.getString(locationIdx) else null
                    val description = if (descriptionIdx >= 0) c.getString(descriptionIdx) else null
                    
                    if (title.isNotEmpty() && begin > 0L) {
                        events.add(CalendarEvent(title, begin, end, allDay, location, description))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error querying calendar content provider", e)
        }
        return events
    }

    val calendarUpdatesFlow: Flow<List<CalendarEvent>> = callbackFlow {
        var isObserverRegistered = false
        val emissionLock = Any()
        var lastEmittedEvents: List<CalendarEvent>? = null

        fun sendEventsIfChanged() {
            synchronized(emissionLock) {
                val events = getTodayEvents()
                if (events != lastEmittedEvents) {
                    lastEmittedEvents = events
                    trySend(events)
                }
            }
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    sendEventsIfChanged()
                }
            }
        }
        
        val registerObserverIfGranted = {
            if (!isObserverRegistered && isPermissionGranted()) {
                try {
                    context.contentResolver.registerContentObserver(
                        CalendarContract.CONTENT_URI,
                        true,
                        observer
                    )
                    isObserverRegistered = true
                } catch (e: Exception) {
                    Log.e("CalendarRepository", "Failed to register content observer", e)
                }
            }
        }

        // Try registering initially
        registerObserverIfGranted()

        // Initial emission
        launch(Dispatchers.IO) {
            sendEventsIfChanged()
        }

        // Periodic maintenance check every 10 minutes to catch date boundaries or delayed permission grant
        val job = launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(10 * 60_000L)
                registerObserverIfGranted()
                sendEventsIfChanged()
            }
        }

        awaitClose {
            job.cancel()
            if (isObserverRegistered) {
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (e: Exception) {}
            }
        }
    }
}
