package com.alauncher.app.ui.walkthrough

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alauncher.app.R
import kotlinx.coroutines.delay

enum class WalkthroughStep(
    @param:androidx.annotation.StringRes val badgeRes: Int,
    @param:androidx.annotation.StringRes val titleRes: Int,
    @param:androidx.annotation.StringRes val descriptionRes: Int
) {
    WELCOME(
        badgeRes = R.string.walkthrough_welcome_badge,
        titleRes = R.string.walkthrough_welcome_title,
        descriptionRes = R.string.walkthrough_welcome_desc
    ),
    COMMAND_PALETTE(
        badgeRes = R.string.walkthrough_command_palette_badge,
        titleRes = R.string.walkthrough_command_palette_title,
        descriptionRes = R.string.walkthrough_command_palette_desc
    ),
    NOTIFICATION_CENTER(
        badgeRes = R.string.walkthrough_notification_center_badge,
        titleRes = R.string.walkthrough_notification_center_title,
        descriptionRes = R.string.walkthrough_notification_center_desc
    ),
    CONTEXTUAL_ISLAND(
        badgeRes = R.string.walkthrough_contextual_island_badge,
        titleRes = R.string.walkthrough_contextual_island_title,
        descriptionRes = R.string.walkthrough_contextual_island_desc
    ),
    APP_CONTROLS(
        badgeRes = R.string.walkthrough_app_controls_badge,
        titleRes = R.string.walkthrough_app_controls_title,
        descriptionRes = R.string.walkthrough_app_controls_desc
    ),
    THUMB_DOCK_AND_LIMITS(
        badgeRes = R.string.walkthrough_thumb_dock_badge,
        titleRes = R.string.walkthrough_thumb_dock_title,
        descriptionRes = R.string.walkthrough_thumb_dock_desc
    ),
    GET_STARTED(
        badgeRes = R.string.walkthrough_get_started_badge,
        titleRes = R.string.walkthrough_get_started_title,
        descriptionRes = R.string.walkthrough_get_started_desc
    )
}

@Composable
fun DynamicWalkthroughOverlay(
    currentStepIndex: Int = 0,
    onStepChange: (Int) -> Unit = {},
    onDismiss: () -> Unit,
    onRequestDefaultLauncher: () -> Unit,
    onGrantNotificationAccess: () -> Unit = {},
    isDefaultLauncher: Boolean = true,
    modifier: Modifier = Modifier
) {
    val totalSteps = WalkthroughStep.entries.size
    val currentStep = WalkthroughStep.entries[currentStepIndex.coerceIn(0, totalSteps - 1)]
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            // Swallow taps and vertical drags so they don't leak to LauncherHomeScreen behind us.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, _ -> }
            }
            .pointerInput(currentStepIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragAccumulator += dragAmount },
                    onDragCancel = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator < -80f) {
                            onStepChange((currentStepIndex + 1).coerceAtMost(totalSteps - 1))
                        } else if (dragAccumulator > 80f) {
                            onStepChange((currentStepIndex - 1).coerceAtLeast(0))
                        }
                    }
                )
            }
            .windowInsetsPadding(WindowInsets.systemBars)
            .testTag("dynamic_walkthrough_overlay")
    ) {
        val isNarrow = maxWidth < 360.dp
        val outerHorizPadding = if (isNarrow) 14.dp else 20.dp
        val outerVertPadding = if (isNarrow) 14.dp else 22.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = outerHorizPadding, vertical = outerVertPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar: Step Counter & Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isNarrow) 6.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = stringResource(R.string.walkthrough_step_counter, currentStepIndex + 1, totalSteps),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("walkthrough_skip_button")
                ) {
                    Text(
                        text = stringResource(R.string.walkthrough_skip),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            // Animated Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val fade = tween<Float>(300)
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeIn(animationSpec = fade))
                                .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeOut(animationSpec = fade))
                        } else {
                            (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeIn(animationSpec = fade))
                                .togetherWith(slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeOut(animationSpec = fade))
                        }
                    },
                    label = "walkthrough_step_anim"
                ) { step ->
                    WalkthroughStepContent(
                        step = step,
                        onRequestDefaultLauncher = onRequestDefaultLauncher,
                        onGrantNotificationAccess = onGrantNotificationAccess,
                        isNarrow = isNarrow,
                        isDefaultLauncher = isDefaultLauncher
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isNarrow) 8.dp else 14.dp))

            // Bottom Navigation: Indicator Dots and Next / Back Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button
                if (currentStepIndex > 0) {
                    OutlinedButton(
                        onClick = { onStepChange((currentStepIndex - 1).coerceAtLeast(0)) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = if (isNarrow) 10.dp else 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("walkthrough_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.walkthrough_back),
                            modifier = Modifier.size(15.dp)
                        )
                        if (!isNarrow) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.walkthrough_back), fontSize = 13.sp)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(if (isNarrow) 40.dp else 70.dp))
                }

                // Progress Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 4.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalSteps) { idx ->
                        val isSelected = idx == currentStepIndex
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) (if (isNarrow) 16.dp else 22.dp) else 5.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dot_width"
                        )
                        val dotColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                        val a11yStepDesc = stringResource(R.string.walkthrough_step_counter, idx + 1, totalSteps)

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(48.dp)
                                .clickable { onStepChange(idx) }
                                .semantics { contentDescription = a11yStepDesc }
                                .testTag("walkthrough_dot_$idx")
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(5.dp)
                                    .width(dotWidth)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(dotColor)
                            )
                        }
                    }
                }

                // Next / Finish Button
                Button(
                    onClick = {
                        if (currentStepIndex < totalSteps - 1) {
                            onStepChange((currentStepIndex + 1).coerceAtMost(totalSteps - 1))
                        } else {
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = if (isNarrow) 12.dp else 18.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("walkthrough_next_button")
                ) {
                    Text(
                        text = if (currentStepIndex == totalSteps - 1) {
                            stringResource(R.string.walkthrough_get_started)
                        } else {
                            stringResource(R.string.walkthrough_next)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentStepIndex < totalSteps - 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.walkthrough_next),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkthroughStepContent(
    step: WalkthroughStep,
    onRequestDefaultLauncher: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    isNarrow: Boolean,
    isDefaultLauncher: Boolean = true
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Step Interactive Visual Preview Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isNarrow) 6.dp else 10.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isNarrow) 14.dp else 18.dp),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    WalkthroughStep.WELCOME -> WelcomeInteractivePreview(isNarrow)
                    WalkthroughStep.COMMAND_PALETTE -> CommandPaletteInteractivePreview()
                    WalkthroughStep.NOTIFICATION_CENTER -> NotificationCenterInteractivePreview()
                    WalkthroughStep.CONTEXTUAL_ISLAND -> ContextualIslandInteractivePreview(onGrantNotificationAccess)
                    WalkthroughStep.APP_CONTROLS -> AppControlsInteractivePreview()
                    WalkthroughStep.THUMB_DOCK_AND_LIMITS -> ThumbDockInteractivePreview()
                    WalkthroughStep.GET_STARTED -> GetStartedInteractivePreview(onRequestDefaultLauncher, isDefault = isDefaultLauncher)
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isNarrow) 8.dp else 12.dp))

        // Badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Text(
                text = stringResource(step.badgeRes),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(if (isNarrow) 6.dp else 8.dp))

        // Title
        Text(
            text = stringResource(step.titleRes),
            fontSize = if (isNarrow) 19.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(if (isNarrow) 4.dp else 6.dp))

        // Description
        Text(
            text = stringResource(step.descriptionRes),
            fontSize = if (isNarrow) 13.sp else 14.sp,
            lineHeight = if (isNarrow) 18.sp else 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

// -------------------------------------------------------------
// Interactive Feature Previews
// -------------------------------------------------------------

/**
 * Decorative mockup of a minimal launcher home screen.
 * Strings here ("09:41 AM", "Phone", etc.) are intentionally static demo content
 * representing a visual illustration — they are NOT user-facing localizable text
 * and should NOT be moved to strings.xml.
 */
@Composable
private fun WelcomeInteractivePreview(isNarrow: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(if (isNarrow) 56.dp else 68.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Spa,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (isNarrow) 30.dp else 36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "09:41 AM",
            fontSize = if (isNarrow) 28.sp else 32.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Thursday, August 27",
            fontSize = if (isNarrow) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Phone", "Messages", "Browser", "Camera").forEach { label ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = label,
                        fontSize = if (isNarrow) 10.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = if (isNarrow) 6.dp else 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandPaletteInteractivePreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Animated Gesture Cue: Swipe Up Icon
        val infiniteTransition = rememberInfiniteTransition(label = "swipe_up_trans")
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 4f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "swipe_up_offset"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset { IntOffset(x = 0, y = offsetY.dp.roundToPx()) }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(R.string.walkthrough_preview_swipe_up_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar Simulation
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.walkthrough_preview_search_query),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(13.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Result Item
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.walkthrough_preview_search_result_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.walkthrough_preview_search_result_subtitle),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCenterInteractivePreview() {
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_right_trans")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipe_right_offset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset { IntOffset(x = offsetX.dp.roundToPx(), y = 0) }
        ) {
            Text(
                text = stringResource(R.string.walkthrough_preview_swipe_right_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Stacked Notification Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.walkthrough_preview_center_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = stringResource(R.string.walkthrough_preview_center_bundle_count),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.walkthrough_preview_center_msg_1),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.walkthrough_preview_center_msg_2),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContextualIslandInteractivePreview(onGrantNotificationAccess: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionRefreshKey = com.alauncher.app.ui.permissions.rememberPermissionRefreshKey()
    var otpCopied by remember { mutableStateOf(false) }

    LaunchedEffect(otpCopied) {
        if (otpCopied) {
            delay(2000)
            otpCopied = false
        }
    }

    val isNotifGranted = remember(permissionRefreshKey) {
        com.alauncher.app.ui.utils.isNotificationAccessGranted(context)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Interactive OTP Live Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (otpCopied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    otpCopied = true
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = if (otpCopied) Icons.Default.CheckCircle else Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (otpCopied) stringResource(R.string.walkthrough_preview_otp_copied_label) else stringResource(R.string.walkthrough_preview_otp_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = if (otpCopied) stringResource(R.string.walkthrough_preview_otp_action_copied) else stringResource(R.string.walkthrough_preview_otp_action_tap),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Permission Guidance Prompt or Active Badge
        if (!isNotifGranted) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGrantNotificationAccess() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.walkthrough_island_enable_permission),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.walkthrough_island_permission_active),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppControlsInteractivePreview() {
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_right_app_trans")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipe_right_app_offset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset { IntOffset(x = offsetX.dp.roundToPx(), y = 0) }
        ) {
            Text(
                text = stringResource(R.string.walkthrough_preview_swipe_right_app_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.walkthrough_preview_app_controls_menu),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Internal Shortcuts Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.walkthrough_preview_app_shortcut_1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.walkthrough_preview_app_shortcut_2),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.walkthrough_action_favorite), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.walkthrough_action_hide), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.walkthrough_action_app_info), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbDockInteractivePreview() {
    var isFocusActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Focus Mode Interactive Pill
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isFocusActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, if (isFocusActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isFocusActive = !isFocusActive }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.walkthrough_preview_focus_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isFocusActive) stringResource(R.string.walkthrough_preview_focus_active) else stringResource(R.string.walkthrough_focus_toggle_hint),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFocusActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isFocusActive) stringResource(R.string.walkthrough_focus_on) else stringResource(R.string.walkthrough_focus_off),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocusActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Edge Productivity Tools
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.walkthrough_edge_tools_hint),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EdgeToolIcon(Icons.Default.AutoAwesome)
                    EdgeToolIcon(Icons.Default.ContentPaste)
                    EdgeToolIcon(Icons.Default.EditNote)
                }
            }
        }
    }
}

@Composable
private fun EdgeToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(26.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GetStartedInteractivePreview(
    onRequestDefaultLauncher: () -> Unit,
    isDefault: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.RocketLaunch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = stringResource(R.string.walkthrough_preview_summary_title),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(stringResource(R.string.walkthrough_preview_summary_swipe_up), fontSize = 10.sp)
                Text(stringResource(R.string.walkthrough_preview_summary_swipe_right), fontSize = 10.sp)
                Text(stringResource(R.string.walkthrough_preview_summary_swipe_down), fontSize = 10.sp)
                Text(stringResource(R.string.walkthrough_preview_summary_swipe_right_app), fontSize = 10.sp)
                Text(stringResource(R.string.walkthrough_preview_summary_double_tap), fontSize = 10.sp)
                Text(stringResource(R.string.walkthrough_preview_summary_long_press), fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (isDefault) {
                    android.widget.Toast.makeText(context, R.string.already_default, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    onRequestDefaultLauncher()
                }
            },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isDefault) Icons.Default.CheckCircle else Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isDefault) stringResource(R.string.already_default) else stringResource(R.string.walkthrough_set_default),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
