package com.alauncher.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alauncher.app.MinimalSmartLauncherApp
import com.alauncher.app.data.settings.SettingsState
import com.alauncher.app.ui.theme.MinimalSmartLauncherTheme
import com.alauncher.app.ui.utils.LauncherUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class ThumbDockService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var settingsJob: Job? = null
    
    // Core dock settings
    private var isDockEnabled = true
    private var isRightEdge = false

    // State
    private var isExpandedState = mutableStateOf(false)
    private var activeToolState = mutableStateOf<String?>(null) // "notes", "clipboard"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Start as Foreground Service
        val channelId = "thumb_dock_channel"
        val channelName = "ALauncher Thumb Dock Service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows that ALauncher Thumb Dock is active"
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ALauncher Dock is Active")
            .setContentText("The interactive utilities panel is active.")
            .setSmallIcon(com.alauncher.app.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1001, notification)
        }

        // Startup guard: Check overlay permission, dock setting, and default launcher status
        if (!Settings.canDrawOverlays(this) || !LauncherUtils.isDefaultLauncher(this) || !isDockEnabled) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        overlayLifecycleOwner = lifecycleOwner

        // Set up initial WindowManager LayoutParams for the thin visible edge trigger
        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val handleHeightPx = dpToPx(80)
        layoutParams = WindowManager.LayoutParams(
            dpToPx(24), // Width: 24dp balanced touch target
            handleHeightPx, // Height: 80dp
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isRightEdge) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            
            // Intercept Back button presses to close the overlay
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                    if (isExpandedState.value || activeToolState.value != null) {
                        closeFloatingTool()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            // Ensure view is focusable to receive key events
            isFocusable = true
            isFocusableInTouchMode = true

            setContent {
                val themeMode = remember { mutableStateOf(com.alauncher.app.model.ThemeMode.NordicPine) }
                
                // Keep theme updated
                LaunchedEffect(Unit) {
                    val app = applicationContext as? MinimalSmartLauncherApp
                    app?.settingsRepository?.settingsFlow?.collectLatest { settings ->
                        themeMode.value = settings.themeMode
                    }
                }

                MinimalSmartLauncherTheme(themeMode = themeMode.value) {
                    ThumbDockOverlayContent()
                }
            }
        }
        composeView = view

        // Add view to WindowManager
        try {
            windowManager.addView(view, layoutParams)
        } catch (e: Exception) {
            Log.e("ThumbDockService", "Failed to add overlay view", e)
            stopSelf()
            return
        }

        // Listen for setting changes to reposition or stop service
        val app = applicationContext as? MinimalSmartLauncherApp
        if (app != null) {
            settingsJob = serviceScope.launch {
                app.settingsRepository.settingsFlow
                    .distinctUntilChangedBy { Pair(it.thumbDockEnabled, it.thumbDockEdgeRight) }
                    .collectLatest { settings ->
                        isDockEnabled = settings.thumbDockEnabled
                        isRightEdge = settings.thumbDockEdgeRight
                        
                        if (!settings.thumbDockEnabled) {
                            stopSelf()
                            return@collectLatest
                        }
                        
                        // Update trigger position
                        updateTriggerLayout(false)
                    }
            }
        }

        // Periodic check to stop service if launcher is changed
        serviceScope.launch {
            while (true) {
                if (!LauncherUtils.isDefaultLauncher(this@ThumbDockService)) {
                    stopSelf()
                    return@launch
                }
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    private fun updateTriggerLayout(isFullscreen: Boolean) {
        val view = composeView ?: return
        val isDefault = LauncherUtils.isDefaultLauncher(this)
        if (!isDefault || !isDockEnabled) {
            layoutParams.width = 0
            layoutParams.height = 0
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {}
            return
        }

        if (isFullscreen) {
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.gravity = Gravity.FILL
            layoutParams.x = 0
            layoutParams.y = 0
            layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            val handleHeightPx = dpToPx(80)
            layoutParams.width = dpToPx(24) // 24dp balanced touch target
            layoutParams.height = handleHeightPx
            layoutParams.gravity = (if (isRightEdge) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            layoutParams.x = 0
            layoutParams.y = 0
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        
        try {
            windowManager.updateViewLayout(view, layoutParams)
            if (isFullscreen) {
                view.post {
                    view.requestFocus()
                }
            }
            
            // Exclude the edge trigger from system back gestures on Android Q (10) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.post {
                    try {
                        val rects = if (isFullscreen) {
                            emptyList()
                        } else {
                            listOf(android.graphics.Rect(0, 0, view.width, view.height))
                        }
                        view.systemGestureExclusionRects = rects
                    } catch (e: Exception) {
                        Log.e("ThumbDockService", "Failed to update exclusion rects", e)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore potential view not attached
        }
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        serviceJob.cancel()
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
        val viewToRemove = composeView
        composeView = null
        if (viewToRemove != null) {
            try {
                viewToRemove.disposeComposition()
                windowManager.removeView(viewToRemove)
            } catch (e: Exception) {
                Log.e("ThumbDockService", "Error removing composeView on destroy", e)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    // Compose overlay representing the clickable handle and expanded popup card
    @Composable
    private fun ThumbDockOverlayContent() {
        var isExpanded by remember { isExpandedState }
        val activeTool by activeToolState
        
        // Dynamically resize window based on whether the dock is expanded or a tool sheet is visible
        LaunchedEffect(isExpanded, activeTool) {
            updateTriggerLayout(isFullscreen = (isExpanded || activeTool != null))
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (activeTool != null) {
                // Dimmed background behind active floating utility sheet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { closeFloatingTool() },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .width(320.dp)
                            .wrapContentHeight()
                            .clickable(enabled = false) {} // Trap clicks
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (activeTool) {
                                        "notes" -> "Quick Notes"
                                        "clipboard" -> "Clipboard History"
                                        else -> ""
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { closeFloatingTool() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Body contents
                            when (activeTool) {
                                "notes" -> FloatingNotesContent()
                                "clipboard" -> FloatingClipboardContent()
                            }
                        }
                    }
                }
            } else if (isExpanded) {
                // Dimmed background behind expanded dock popup card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { isExpanded = false }
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .align(if (isRightEdge) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(
                                start = if (isRightEdge) 0.dp else 16.dp,
                                end = if (isRightEdge) 16.dp else 0.dp
                            )
                            .width(200.dp)
                            .wrapContentHeight()
                            .clickable(enabled = false) {} // Prevent click-through to background
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = "ALauncher Dock",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val items = listOf(
                                Triple("gemini", Icons.Default.AutoAwesome, "Gemini"),
                                Triple("clipboard", Icons.Default.ContentPaste, "Clipboard"),
                                Triple("notes", Icons.Default.Edit, "Quick Notes")
                            )
                            
                            items.forEach { (id, icon, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (id == "gemini") {
                                                launchGeminiAssistant()
                                                isExpanded = false
                                            } else {
                                                openFloatingTool(id)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Clickable edge handle display at 50% height
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isExpanded = true },
                    contentAlignment = if (isRightEdge) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(8.dp)
                            .height(80.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }

    @Composable
    private fun FloatingNotesContent() {
        var notesText by remember { mutableStateOf("") }
        val sharedPreferences = remember { getSharedPreferences("launcher_notes", Context.MODE_PRIVATE) }
        val context = LocalContext.current
        val clipboardManager = remember { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
        
        LaunchedEffect(Unit) {
            notesText = sharedPreferences.getString("notes", "") ?: ""
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = notesText,
                onValueChange = {
                    notesText = it
                    sharedPreferences.edit().putString("notes", it).apply()
                },
                placeholder = { Text("Write something down...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        if (notesText.isNotEmpty()) {
                            try {
                                val clip = ClipData.newPlainText("alauncher_notes_copied", notesText)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Notes copied to clipboard!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to copy", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Notes are empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Notes",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Note", fontSize = 13.sp)
                }
            }
        }
    }

    @Composable
    private fun FloatingClipboardContent() {
        val clipboardManager = remember { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
        val sharedPreferences = remember { getSharedPreferences("clipboard_history", Context.MODE_PRIVATE) }
        val historyList = remember { mutableStateListOf<String>() }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            val savedHistory = sharedPreferences.getString("history", "") ?: ""
            if (savedHistory.isNotEmpty()) {
                historyList.addAll(savedHistory.split("|||").filter { it.isNotEmpty() })
            }
            
            try {
                if (clipboardManager.hasPrimaryClip()) {
                    val clip = clipboardManager.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString()
                        if (!text.isNullOrEmpty()) {
                            if (historyList.isEmpty() || historyList.first() != text) {
                                historyList.remove(text)
                                historyList.add(0, text)
                                if (historyList.size > 5) {
                                    historyList.removeAt(historyList.lastIndex)
                                }
                                sharedPreferences.edit().putString("history", historyList.joinToString("|||")).apply()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (historyList.isEmpty()) {
                Text(
                    "No clipboard entries found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList) { text ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = text,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = {
                                        try {
                                            val clip = ClipData.newPlainText("alauncher_copied", text)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to copy", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Item",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        historyList.clear()
                        sharedPreferences.edit().putString("history", "").apply()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            clipboardManager.clearPrimaryClip()
                        } else {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            }
        }
    }



    private fun openFloatingTool(tool: String) {
        activeToolState.value = tool
        updateTriggerLayout(isFullscreen = true)
    }

    private fun closeFloatingTool() {
        activeToolState.value = null
        isExpandedState.value = false
        updateTriggerLayout(isFullscreen = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun launchGeminiAssistant() {
        try {
            // Try launching the digital assistant in voice listening mode
            val voiceIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(voiceIntent)
        } catch (e: Exception) {
            try {
                val geminiIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
                if (geminiIntent != null) {
                    geminiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(geminiIntent)
                    return
                }
                
                val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://gemini.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Gemini not available", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
