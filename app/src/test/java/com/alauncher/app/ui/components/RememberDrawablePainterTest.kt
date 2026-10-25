package com.alauncher.app.ui.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Image
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RememberDrawablePainterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rememberDrawablePainter_withValidDrawable_rendersWithoutCrashing() {
        val drawable = ColorDrawable(Color.RED)
        composeTestRule.setContent {
            val painter = rememberDrawablePainter(drawable)
            assertNotNull(painter)
            Image(painter = painter, contentDescription = "Test Image")
        }
    }

    @Test
    fun rememberDrawablePainter_withNullDrawable_rendersGracefully() {
        composeTestRule.setContent {
            val painter = rememberDrawablePainter(null)
            assertNotNull(painter)
            Image(painter = painter, contentDescription = "Null Drawable Image")
        }
    }
}

