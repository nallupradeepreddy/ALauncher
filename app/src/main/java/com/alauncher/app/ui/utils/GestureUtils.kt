package com.alauncher.app.ui.utils

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Custom gesture handler for app icons to support tap and swipe right (or swipe left in RTL)
 * to trigger the shortcuts popup.
 *
 * It consumes horizontal drag events to trigger [onSwipeRight], while leaving vertical drag
 * events unconsumed so they can bubble up to parent containers (e.g. for vertical scrolling in lists).
 */
fun Modifier.appIconGestureHandler(
    onTap: () -> Unit,
    onSwipeRight: () -> Unit,
    isLaunching: Boolean = false,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
): Modifier {
    return this.composed {
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)

        this.pointerInput(isLaunching, layoutDirection) {
            if (isLaunching) return@pointerInput

            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown()
                    var isDragDetected = false
                    var isHorizontalDrag = false
                    val touchSlop = viewConfiguration.touchSlop
                    // Lock early (at 40% touch slop) to claim the horizontal gesture before parent (e.g. scroll/pager) starts tracking
                    val lockThreshold = touchSlop * 0.4f

                    while (true) {
                        // Intercept events in the Initial (tunneling) pass before parents process them in the Main pass
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }

                        // Abort if the gesture is cancelled or consumed by a parent container in the initial pass
                        if (change == null || change.isConsumed) {
                            break
                        }

                        val currentDragY = change.position.y - down.position.y
                        val currentDragX = change.position.x - down.position.x

                        if (change.pressed) {
                            val absX = abs(currentDragX)
                            val absY = abs(currentDragY)

                            // Track if touch slop has ever been exceeded in either direction to distinguish drag from tap
                            if (absX > touchSlop || absY > touchSlop) {
                                isDragDetected = true
                            }

                            if (!isHorizontalDrag) {
                                if (absX > lockThreshold) {
                                    val isTowardEnd = if (layoutDirection == LayoutDirection.Ltr) {
                                        currentDragX > 0
                                    } else {
                                        currentDragX < 0
                                    }

                                    if (isTowardEnd && absX >= absY) {
                                        isHorizontalDrag = true
                                    } else if (!isTowardEnd && absX >= absY) {
                                        // Abort early if the user is swiping left (backwards) so the parent can handle it immediately
                                        break
                                    } else if (absY > touchSlop && absY > absX) {
                                        // Abort early if the drag is clearly vertical-dominant and exceeds touch slop so parent can scroll/drag immediately
                                        break
                                    }
                                }
                            }

                            if (isHorizontalDrag) {
                                change.consume()
                            }
                        } else {
                            // Pointer released
                            if (!isDragDetected && abs(currentDragX) < touchSlop && abs(currentDragY) < touchSlop) {
                                currentOnTap()
                            } else if (isHorizontalDrag) {
                                val isTowardEnd = if (layoutDirection == LayoutDirection.Ltr) {
                                    currentDragX > 0
                                } else {
                                    currentDragX < 0
                                }
                                if (isTowardEnd && abs(currentDragX) >= touchSlop) {
                                    currentOnSwipeRight()
                                }
                            }
                            break
                        }
                    }
                }
            }
        }
    }
}
