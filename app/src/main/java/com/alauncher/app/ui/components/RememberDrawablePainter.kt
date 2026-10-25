package com.alauncher.app.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

@Composable
@Suppress("LocalContextResourcesRead")
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    val context = LocalContext.current
    val clonedDrawable = remember(drawable, context) {
        drawable?.constantState?.newDrawable(context.resources)?.mutate() ?: drawable
    }
    return remember(clonedDrawable, context) {
        var currentContext = context
        var activity: Activity? = null
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                activity = currentContext
                break
            }
            currentContext = currentContext.baseContext
        }
        val targetActivity = activity

        object : Painter() {
            override val intrinsicSize: Size
                get() = try {
                    val w = clonedDrawable?.intrinsicWidth?.toFloat() ?: 0f
                    val h = clonedDrawable?.intrinsicHeight?.toFloat() ?: 0f
                    if (w > 0f && h > 0f) {
                        Size(w, h)
                    } else {
                        Size.Unspecified
                    }
                } catch (e: Throwable) {
                    Size.Unspecified
                }

            override fun DrawScope.onDraw() {
                try {
                    if (targetActivity != null && (targetActivity.isFinishing || targetActivity.isDestroyed)) {
                        return
                    }
                    val w = if (size.width.isFinite() && size.width > 0f) size.width.toInt() else 0
                    val h = if (size.height.isFinite() && size.height > 0f) size.height.toInt() else 0
                    if (clonedDrawable != null && w > 0 && h > 0) {
                        clonedDrawable.setBounds(0, 0, w, h)
                        drawIntoCanvas { canvas ->
                            clonedDrawable.draw(canvas.nativeCanvas)
                        }
                    }
                } catch (e: Throwable) {
                    // Fail silently to prevent crashing the window input channel
                }
            }
        }
    }
}
