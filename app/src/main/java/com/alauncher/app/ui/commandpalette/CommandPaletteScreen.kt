package com.alauncher.app.ui.commandpalette

import com.alauncher.app.ui.components.rememberDrawablePainter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.alauncher.app.ui.utils.appIconGestureHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import com.alauncher.app.R
import androidx.compose.ui.res.stringResource
import com.alauncher.app.ui.components.AppDialogHeader
import com.alauncher.app.ui.components.BottomSheetAppHeader
import com.alauncher.app.ui.settings.components.AppIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ContentCopy
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.alauncher.app.domain.app.resolveAutoLaunchCandidate
import com.alauncher.app.domain.math.MathResult
import com.alauncher.app.model.LauncherApp
import com.alauncher.app.model.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteScreen(
    apps: List<LauncherApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    isFocusModeActive: Boolean,
    onToggleFocusMode: (Boolean, Int) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onAddToHomeScreen: (LauncherApp) -> Unit,
    onHideFromList: (LauncherApp) -> Unit,
    showIcons: Boolean,
    showFocusModeButton: Boolean = true,
    focusEndTime: Long = 0L,
    mathResult: MathResult? = null,
    sortMode: SortMode = SortMode.AlphabeticalAsc,
    onBack: () -> Unit,
    isDefaultLauncher: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedAppForMenu by remember { mutableStateOf<LauncherApp?>(null) }
    var showFocusDurationDialog by remember { mutableStateOf(false) }
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isDraggingScrubber by remember { mutableStateOf(false) }
    var activeScrubberLetter by remember { mutableStateOf<Char?>(null) }

    val alphabet = remember { AlphabetIndexer.ALPHABET }

    val availableLetters = remember(apps) {
        AlphabetIndexer.computeAvailableLetters(apps)
    }

    val letterIndexMap = remember(apps) {
        AlphabetIndexer.computeLetterIndexMap(apps)
    }

    fun getTargetIndex(letter: Char): Int? =
        AlphabetIndexer.getTargetIndex(letterIndexMap, letter)

    LaunchedEffect(isFocusModeActive, focusEndTime) {
        if (isFocusModeActive && focusEndTime > 0L) {
            while (true) {
                currentTimeMs = System.currentTimeMillis()
                if (currentTimeMs >= focusEndTime) break
                delay(1000L)
            }
        }
    }

    // Instant system keyboard pull-up on launch
    LaunchedEffect(focusRequester) {
        try {
            delay(150)
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore if view tree is not active
        }
    }

    // Auto-launch on confident exact match or single remaining match (suppressed when calculating math)
    LaunchedEffect(apps, query, mathResult) {
        if (mathResult == null) {
            val targetApp = resolveAutoLaunchCandidate(apps, query)
            if (targetApp != null) {
                // Brief delay to allow keyboard hide animation before launching
                delay(100)
                keyboardController?.hide()
                onLaunchApp(targetApp)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().testTag("command_palette_scaffold")
    ) {
        // Full-screen Sheet Card with frosted-glass semi-transparency
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxSize()
                .testTag("command_palette_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. The app list — reverse layout so items stack from bottom up above the search bar
                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 64.dp) // Offset upwards for balance
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No apps found",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try a different search term or spelling",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Tap anywhere to go back",
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                } else {
                    val listState = rememberLazyListState()

                    LaunchedEffect(listState.isScrollInProgress) {
                        if (listState.isScrollInProgress) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }

                    // Key on query, list size, top match package, and sortMode so async search emissions
                    // from Dispatchers.Default reset scroll to item 0 without fighting user scroll if a
                    // background notification badge count updates on an item.
                    LaunchedEffect(query, apps.size, apps.firstOrNull()?.packageName, sortMode) {
                        if (apps.isNotEmpty()) {
                            listState.scrollToItem(0)
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        // Clicking outside rows on the empty background area will dismiss the sheet
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { onBack() }
                        )

                        val isScrubberVisible = isAlphabetScrubberVisible(
                            isDefaultLauncher = isDefaultLauncher,
                            query = query,
                            hasApps = apps.isNotEmpty(),
                            sortMode = sortMode
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            state = listState,
                            reverseLayout = query.isNotEmpty(),
                            verticalArrangement = Arrangement.spacedBy(
                                space = 4.dp,
                                alignment = Alignment.Bottom
                            ),
                            contentPadding = PaddingValues(
                                top = 24.dp,
                                bottom = 16.dp,
                                end = if (isScrubberVisible) 28.dp else 0.dp
                            )
                        ) {
                            items(
                                items = apps,
                                key = { "palette:${it.packageName}" }
                            ) { app ->
                                AppPaletteRowItem(
                                    app = app,
                                    showIcon = showIcons,
                                    onClick = {
                                        keyboardController?.hide()
                                        onLaunchApp(app)
                                    },
                                    onLongClick = { selectedAppForMenu = app },
                                    modifier = Modifier.testTag("app_palette_item_${app.packageName}")
                                )
                            }
                        }

                        // Fast Alphabet Scroller (A-Z Thumb Scrubber)
                        if (isScrubberVisible) {
                            var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                            AlphabetScrubber(
                                alphabet = alphabet,
                                availableLetters = availableLetters,
                                activeLetter = activeScrubberLetter,
                                onLetterSelected = { letter ->
                                    activeScrubberLetter = letter
                                    getTargetIndex(letter)?.let { targetIdx ->
                                        scrollJob?.cancel()
                                        scrollJob = coroutineScope.launch {
                                            listState.scrollToItem(targetIdx)
                                        }
                                    }
                                },
                                onDragStateChanged = { dragging ->
                                    isDraggingScrubber = dragging
                                    if (dragging) {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    } else {
                                        activeScrubberLetter = null
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .testTag("alphabet_scrubber")
                            )
                        }

                        // Floating Letter Preview Bubble
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isDraggingScrubber && activeScrubberLetter != null,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut() + scaleOut(targetScale = 0.8f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .testTag("scrubber_preview_bubble")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = activeScrubberLetter?.toString() ?: "",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Math & Unit Conversion Result Pill
                AnimatedVisibility(
                    visible = mathResult != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (mathResult != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Math Result", mathResult.resultText))
                                    Toast.makeText(context, "Copied: ${mathResult.resultText}", Toast.LENGTH_SHORT).show()
                                }
                                .testTag("math_result_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "= ${mathResult.resultText}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${mathResult.expression} • Tap to copy",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy result",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Text Input Box (Search bar) at the bottom - Styled as a floating glass bar
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            "Search apps...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("command_input"),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                         imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (apps.isNotEmpty()) {
                                keyboardController?.hide()
                                onLaunchApp(apps.first())
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    )
                )

                // 3. Focus Mode toggle button below the search bar
                if (showFocusModeButton) {
                    Button(
                        onClick = {
                            if (isFocusModeActive) {
                                onToggleFocusMode(false, 0)
                            } else {
                                showFocusDurationDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activate_focus_mode_button"),
                        colors = if (isFocusModeActive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val buttonText = if (isFocusModeActive) {
                                if (focusEndTime > 0L) {
                                    val remainingMs = focusEndTime - currentTimeMs
                                    if (remainingMs > 0L) {
                                        val remainingMins = (remainingMs + 59999L) / 60000L
                                        "Deactivate Focus Mode (${remainingMins}m left)"
                                    } else {
                                        "Deactivate Focus Mode"
                                    }
                                } else {
                                    "Deactivate Focus Mode"
                                }
                            } else {
                                "Activate Focus Mode"
                            }
                            Text(
                                text = buttonText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Focus Mode Duration Presets Dialog
        if (showFocusDurationDialog) {
            AlertDialog(
                onDismissRequest = { showFocusDurationDialog = false },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                title = {
                    AppDialogHeader(
                        icon = Icons.Default.FilterCenterFocus,
                        title = "Focus Mode Duration",
                        subtitle = stringResource(R.string.focus_mode_duration_dialog_subtitle)
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            Triple("25 minutes", "Pomodoro sprint", 25),
                            Triple("45 minutes", "Deep work block", 45),
                            Triple("60 minutes", "Extended focus hour", 60),
                            Triple("Until turned off", "Manual silence mode", 0)
                        )
                        presets.forEach { (label, desc, duration) ->
                            Surface(
                                onClick = {
                                    showFocusDurationDialog = false
                                    onToggleFocusMode(true, duration)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (duration == 0) Icons.Default.AllInclusive else Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = desc,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showFocusDurationDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.close_button), fontSize = 13.sp)
                    }
                }
            )
        }

        // App List Context Menu (ModalBottomSheet)
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

            ModalBottomSheet(
                onDismissRequest = { selectedAppForMenu = null },
                sheetState = sheetState,
                contentWindowInsets = { WindowInsets.systemBars },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                modifier = Modifier.testTag("app_palette_bottom_sheet")
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

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                dismissMenu { onAddToHomeScreen(app) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add to Home Screen", fontSize = 14.sp)
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
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("App Info", fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                dismissMenu { onHideFromList(app) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hide from List", fontSize = 14.sp)
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DELETE).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot uninstall this app", Toast.LENGTH_SHORT).show()
                                }
                                dismissMenu()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uninstall App", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlphabetScrubber(
    alphabet: List<Char>,
    availableLetters: Set<Char>,
    activeLetter: Char?,
    onLetterSelected: (Char) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (alphabet.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    var lastSelectedLetter by remember { mutableStateOf<Char?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .pointerInput(alphabet) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onDragStateChanged(true)

                    fun updateSelection(y: Float) {
                        val fraction = (y / size.height).coerceIn(0f, 1f)
                        val index = (fraction * (alphabet.size - 1)).roundToInt().coerceIn(0, alphabet.size - 1)
                        val letter = alphabet[index]
                        if (lastSelectedLetter != letter) {
                            lastSelectedLetter = letter
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letter)
                        }
                    }

                    updateSelection(down.position.y)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            break
                        }
                        change.consume()
                        updateSelection(change.position.y)
                    }

                    lastSelectedLetter = null
                    onDragStateChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val slotHeight = (maxHeight - 8.dp) / alphabet.size
        val dynamicFontSize = (slotHeight.value * 0.72f).coerceIn(6f, 9f).sp
        val dynamicLineHeight = (slotHeight.value * 0.85f).coerceIn(7f, 11f).sp

        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { letter ->
                val isPresent = availableLetters.contains(letter)
                val isActive = activeLetter == letter
                Text(
                    text = letter.toString(),
                    fontSize = dynamicFontSize,
                    lineHeight = dynamicLineHeight,
                    fontWeight = if (isActive) FontWeight.ExtraBold else if (isPresent) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isPresent -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPaletteRowItem(
    app: LauncherApp,
    showIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val repo = remember { (context.applicationContext as com.alauncher.app.MinimalSmartLauncherApp).appRepository }
    var iconDrawable by remember(app.packageName, showIcon) {
        mutableStateOf(if (showIcon) (app.icon ?: repo.getCachedIcon(app.packageName)) else null)
    }
    LaunchedEffect(app.packageName, showIcon) {
        if (showIcon && iconDrawable == null) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repo.getIcon(app.packageName)
            }
            iconDrawable = loaded
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = if (MaterialTheme.colorScheme.outline == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .appIconGestureHandler(
                onTap = onClick,
                onSwipeRight = onLongClick,
                layoutDirection = layoutDirection
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIcon && iconDrawable != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = iconDrawable!!),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

internal fun isAlphabetScrubberVisible(
    isDefaultLauncher: Boolean,
    query: String,
    hasApps: Boolean,
    sortMode: SortMode
): Boolean {
    return isDefaultLauncher && query.isEmpty() && hasApps && sortMode == SortMode.AlphabeticalAsc
}


