package com.alauncher.app.ui.home

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.Modifier
import com.alauncher.app.model.ThemeMode
import com.alauncher.app.ui.theme.isLight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeIconBorderTest {

    @Test
    fun testThemeModeIsLightClassification() {
        // Light themes must return true
        assertTrue("BotanicalMint should be light", ThemeMode.BotanicalMint.isLight)
        assertTrue("SunsetClay should be light", ThemeMode.SunsetClay.isLight)

        // Dark themes must return false
        assertFalse("NordicPine should be dark", ThemeMode.NordicPine.isLight)
        assertFalse("MidnightAurora should be dark", ThemeMode.MidnightAurora.isLight)
    }

    @Test
    fun testHomeIconBorderNoOpOnDarkThemes() {
        val originalModifier = Modifier
        val resultModifier = originalModifier.homeIconBorder(
            isLight = false,
            drawable = ColorDrawable(android.graphics.Color.RED)
        )
        // Must return identical reference without adding any draw node when not light
        assertSame("On dark theme, homeIconBorder must be a strict no-op", originalModifier, resultModifier)
    }

    @Test
    fun testHomeIconBorderAppliedOnLightThemes() {
        val originalModifier = Modifier
        val resultModifier = originalModifier.homeIconBorder(
            isLight = true,
            drawable = ColorDrawable(android.graphics.Color.WHITE)
        )
        // Must create a draw modifier when light
        assertTrue("On light theme, homeIconBorder modifier must wrap with drawing logic", resultModifier !== originalModifier)
    }

    @Test
    fun testHomeIconBorderNullDrawableIsNoOp() {
        val originalModifier = Modifier
        val resultModifier = originalModifier.homeIconBorder(
            isLight = true,
            drawable = null
        )
        assertSame("When drawable is null, homeIconBorder must be a strict no-op", originalModifier, resultModifier)
    }

    @Test
    fun testAllThemeModesExplicitlyTestedForLightClassification() {
        // Exhaustive verification of all ThemeMode entries
        for (mode in ThemeMode.entries) {
            when (mode) {
                ThemeMode.BotanicalMint,
                ThemeMode.SunsetClay -> assertTrue("Theme $mode must be classified as light", mode.isLight)
                ThemeMode.NordicPine,
                ThemeMode.MidnightAurora -> assertFalse("Theme $mode must be classified as dark", mode.isLight)
            }
        }
    }

    @Test
    fun testDifferentDrawableSubclassesHandledSafely() {
        val originalModifier = Modifier
        val gradientDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE)
        }
        val resultModifier = originalModifier.homeIconBorder(
            isLight = true,
            drawable = gradientDrawable
        )
        assertTrue("GradientDrawable on light theme must wrap with drawing logic", resultModifier !== originalModifier)
    }

    @Test
    fun testHomeIconBorderWithAdaptiveIconDrawable_extractsAndCachesMaskWithoutPerFrameAllocations() {
        val originalModifier = Modifier
        val foreground = ColorDrawable(android.graphics.Color.WHITE)
        val background = ColorDrawable(android.graphics.Color.BLUE)
        val adaptiveIcon = android.graphics.drawable.AdaptiveIconDrawable(background, foreground)

        // 1. Initial mask retrieval & cache insertion
        val mask1 = getOrCreateAdaptiveMask(adaptiveIcon, 100, 100)
        assertNotNull("AdaptiveIconDrawable must produce an icon mask on Android 8.0+", mask1)

        // 2. Subsequent call must return the cached instance (identical reference, 0 allocations)
        val mask2 = getOrCreateAdaptiveMask(adaptiveIcon, 100, 100)
        assertSame("Subsequent mask queries must return the cached Path instance to prevent frame allocations", mask1, mask2)

        // 3. Modifier application
        val resultModifier = originalModifier.homeIconBorder(
            isLight = true,
            drawable = adaptiveIcon
        )
        assertTrue("AdaptiveIconDrawable on light theme must wrap with drawing modifier", resultModifier !== originalModifier)
    }
}

