package com.alauncher.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alauncher.app.model.ThemeMode

// 1. Nordic Pine Dark
// Alpine evergreen slate with radiant frost teal & jade accents (8.5/10 dark shade).
private val NordicPineColorScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),

    secondary = Color(0xFF9AB2AC),
    onSecondary = Color(0xFF122320),

    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF064E3B),

    background = Color(0xFF121A18),
    surface = Color(0xFF1B2724),

    onBackground = Color(0xFFEFF8F5),
    onSurface = Color(0xFFE0EFEB),

    primaryContainer = Color(0xFF1B443E),
    onPrimaryContainer = Color(0xFFCCFBF1),

    secondaryContainer = Color(0xFF253935),
    onSecondaryContainer = Color(0xFFD2E8E3),

    tertiaryContainer = Color(0xFF1F4C3E),
    onTertiaryContainer = Color(0xFFA7F3D0),

    surfaceVariant = Color(0xFF243430),
    onSurfaceVariant = Color(0xFF8BA59F),

    outline = Color(0xFF45645D),
    outlineVariant = Color(0xFF2B3F3A),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),

    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// 2. Porcelain Blue Light
// Airy porcelain background with crisp surfaces & refined blue accents.
// Note: Identifier retains BotanicalMint for DataStore preference backwards-compatibility.
private val BotanicalMintColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),

    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),

    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),

    background = Color(0xFFF1F4F9),
    surface = Color(0xFFFFFFFF),

    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),

    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),

    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),

    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),

    surfaceVariant = Color(0xFFE2E7F0),
    onSurfaceVariant = Color(0xFF475569),

    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),

    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),

    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
)

// 3. Sunlit Linen & Burnt Terracotta
// Warm organic linen light theme with rich terracotta & sunlit golden accents.
private val SunsetClayColorScheme = lightColorScheme(
    primary = Color(0xFFB33A0A),
    onPrimary = Color(0xFFFFFFFF),

    secondary = Color(0xFF785E4F),
    onSecondary = Color(0xFFFFFFFF),

    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),

    background = Color(0xFFF6F3EB),
    surface = Color(0xFFFFFFFF),

    onBackground = Color(0xFF241C16),
    onSurface = Color(0xFF241C16),

    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),

    secondaryContainer = Color(0xFFF3E9DF),
    onSecondaryContainer = Color(0xFF3B2A22),

    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),

    surfaceVariant = Color(0xFFEBE3D7),
    onSurfaceVariant = Color(0xFF6B584C),

    outline = Color(0xFFA69384),
    outlineVariant = Color(0xFFDDCFC2),

    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),

    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
)

// 4. Obsidian Indigo Dark
// Premium midnight slate with calm blue & violet accents (8.5/10 dark shade).
private val MidnightAuroraColorScheme = darkColorScheme(
    primary = Color(0xFF8BB8FF),
    onPrimary = Color(0xFF082044),

    secondary = Color(0xFFB4C4E2),
    onSecondary = Color(0xFF1C273C),

    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2E1F5C),

    background = Color(0xFF141824),
    surface = Color(0xFF1E2436),

    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFE2E8F0),

    primaryContainer = Color(0xFF243B66),
    onPrimaryContainer = Color(0xFFD8E5FF),

    secondaryContainer = Color(0xFF2C384E),
    onSecondaryContainer = Color(0xFFDAE4F8),

    tertiaryContainer = Color(0xFF42336E),
    onTertiaryContainer = Color(0xFFEADBFF),

    surfaceVariant = Color(0xFF273046),
    onSurfaceVariant = Color(0xFFA0AFC4),

    outline = Color(0xFF475674),
    outlineVariant = Color(0xFF2D374D),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),

    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val ThemeMode.isLight: Boolean
    get() = this == ThemeMode.BotanicalMint || this == ThemeMode.SunsetClay

val LocalIsLightTheme = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun MinimalSmartLauncherTheme(
    themeMode: ThemeMode = ThemeMode.NordicPine,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.NordicPine -> NordicPineColorScheme
        ThemeMode.BotanicalMint -> BotanicalMintColorScheme
        ThemeMode.SunsetClay -> SunsetClayColorScheme
        ThemeMode.MidnightAurora -> MidnightAuroraColorScheme
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsLightTheme provides themeMode.isLight
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Returns the 4 core preview colors (background, surface, primary, tertiary)
 * directly from the authoritative ColorScheme definitions.
 */
fun getThemePreviewColors(themeMode: ThemeMode): List<Color> {
    val scheme = when (themeMode) {
        ThemeMode.NordicPine -> NordicPineColorScheme
        ThemeMode.BotanicalMint -> BotanicalMintColorScheme
        ThemeMode.SunsetClay -> SunsetClayColorScheme
        ThemeMode.MidnightAurora -> MidnightAuroraColorScheme
    }
    return listOf(scheme.background, scheme.surface, scheme.primary, scheme.tertiary)
}

/**
 * Draws a subtle tone-on-tone procedural background texture
 * customized for each theme.
 */
fun Modifier.themeBackgroundTexture(
    themeMode: ThemeMode
): Modifier = drawBehind {
    val width = size.width
    val height = size.height

    if (width == 0f || height == 0f) {
        return@drawBehind
    }

    when (themeMode) {
        ThemeMode.NordicPine -> {
            drawConcentricCirclesPattern(
                lineColor = Color(0xFF2DD4BF).copy(alpha = 0.025f),
                strokeWidth = 1.dp.toPx()
            )
        }

        ThemeMode.SunsetClay -> {
            drawConcentricCirclesPattern(
                lineColor = Color(0xFFB33A0A).copy(alpha = 0.025f),
                strokeWidth = 1.dp.toPx()
            )
        }

        ThemeMode.MidnightAurora -> {
            drawGridBackground(
                gridColor = Color(0xFF8BB8FF).copy(alpha = 0.012f),
                spacing = 60.dp.toPx()
            )

            drawTechCircuitsPattern(
                lineColor = Color(0xFF8BB8FF).copy(alpha = 0.020f),
                strokeWidth = 1.dp.toPx(),
                nodeRadius = 3.dp.toPx()
            )
        }

        ThemeMode.BotanicalMint -> {
            // Intentionally uses deeper cobalt tone (#315EA8) rather than primary (#2563EB) for subtle circuit contrast on light porcelain
            drawTechCircuitsPattern(
                lineColor = Color(0xFF315EA8).copy(alpha = 0.022f),
                strokeWidth = 1.dp.toPx(),
                nodeRadius = 3.dp.toPx()
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope
    .drawConcentricCirclesPattern(
        lineColor: Color,
        strokeWidth: Float
    ) {
    val width = size.width
    val height = size.height

    drawCircle(
        color = lineColor,
        radius = width * 0.4f,
        center = Offset(width * 0.9f, height * 0.1f),
        style = Stroke(width = strokeWidth)
    )

    drawCircle(
        color = lineColor,
        radius = width * 0.6f,
        center = Offset(width * 0.9f, height * 0.1f),
        style = Stroke(width = strokeWidth)
    )

    drawCircle(
        color = lineColor,
        radius = width * 0.8f,
        center = Offset(width * 0.9f, height * 0.1f),
        style = Stroke(width = strokeWidth)
    )

    drawCircle(
        color = lineColor,
        radius = width * 1.0f,
        center = Offset(width * 0.9f, height * 0.1f),
        style = Stroke(width = strokeWidth)
    )

    drawCircle(
        color = lineColor,
        radius = width * 0.3f,
        center = Offset(width * 0.1f, height * 0.85f),
        style = Stroke(width = strokeWidth)
    )

    drawCircle(
        color = lineColor,
        radius = width * 0.5f,
        center = Offset(width * 0.1f, height * 0.85f),
        style = Stroke(width = strokeWidth)
    )

    drawCircle(
        color = lineColor,
        radius = width * 0.7f,
        center = Offset(width * 0.1f, height * 0.85f),
        style = Stroke(width = strokeWidth)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope
    .drawGridBackground(
        gridColor: Color,
        spacing: Float
    ) {
    val width = size.width
    val height = size.height

    var x = 0f

    while (x < width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )

        x += spacing
    }

    var y = 0f

    while (y < height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )

        y += spacing
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope
    .drawTechCircuitsPattern(
        lineColor: Color,
        strokeWidth: Float,
        nodeRadius: Float
    ) {
    val width = size.width
    val height = size.height

    // Track 1: Top-right
    drawLine(color = lineColor, start = Offset(width * 0.5f, height * 0.05f), end = Offset(width * 0.8f, height * 0.05f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.8f, height * 0.05f), end = Offset(width * 0.9f, height * 0.15f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.9f, height * 0.15f), end = Offset(width * 0.9f, height * 0.3f), strokeWidth = strokeWidth)

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.5f, height * 0.05f)
    )

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.9f, height * 0.3f)
    )

    // Track 2: Middle-left
    drawLine(color = lineColor, start = Offset(width * 0.1f, height * 0.25f), end = Offset(width * 0.3f, height * 0.25f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.3f, height * 0.25f), end = Offset(width * 0.4f, height * 0.35f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.4f, height * 0.35f), end = Offset(width * 0.2f, height * 0.55f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.2f, height * 0.55f), end = Offset(width * 0.2f, height * 0.65f), strokeWidth = strokeWidth)

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.1f, height * 0.25f)
    )

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.2f, height * 0.65f)
    )

    // Track 3: Bottom-right
    drawLine(color = lineColor, start = Offset(width * 0.9f, height * 0.9f), end = Offset(width * 0.7f, height * 0.9f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.7f, height * 0.9f), end = Offset(width * 0.6f, height * 0.8f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.6f, height * 0.8f), end = Offset(width * 0.6f, height * 0.6f), strokeWidth = strokeWidth)

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.9f, height * 0.9f)
    )

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.6f, height * 0.6f)
    )

    // Track 4: Top-left
    drawLine(color = lineColor, start = Offset(width * 0.1f, height * 0.05f), end = Offset(width * 0.2f, height * 0.15f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.2f, height * 0.15f), end = Offset(width * 0.2f, height * 0.3f), strokeWidth = strokeWidth)

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.1f, height * 0.05f)
    )

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.2f, height * 0.3f)
    )

    // Track 5: Center-bottom
    drawLine(color = lineColor, start = Offset(width * 0.3f, height * 0.85f), end = Offset(width * 0.5f, height * 0.85f), strokeWidth = strokeWidth)
    drawLine(color = lineColor, start = Offset(width * 0.5f, height * 0.85f), end = Offset(width * 0.6f, height * 0.75f), strokeWidth = strokeWidth)

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.3f, height * 0.85f)
    )

    drawCircle(
        color = lineColor,
        radius = nodeRadius,
        center = Offset(width * 0.6f, height * 0.75f)
    )
}
