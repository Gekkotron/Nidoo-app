package com.gekkotron.nidoo.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class ThemeChoice { SYSTEM, NIGHT_GLASS, IVOIRE }

private val LocalNidooColors = staticCompositionLocalOf { NightGlassColors }
private val LocalNidooType = staticCompositionLocalOf { nidooType(dark = true) }

private val NightScheme = darkColorScheme(
    primary = Color(0xFFFFC457), onPrimary = Color(0xFF0B1020), secondary = Color(0xFF6FF0BF),
    background = Color(0xFF0B1020), onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF151B33), onSurface = Color(0xFFE8ECFF), onSurfaceVariant = Color(0xFFB9BFD6),
    surfaceContainerLow = Color(0xFF151B33), surfaceContainer = Color(0xFF151B33), surfaceContainerHigh = Color(0xFF1B2240),
    secondaryContainer = Color(0xFF2A3358), onSecondaryContainer = Color(0xFFE8ECFF),
    inverseSurface = Color(0xFFE8ECFF), inverseOnSurface = Color(0xFF0B1020), outline = Color(0x33FFFFFF),
)

private val IvoireScheme = lightColorScheme(
    primary = Color(0xFF232A26), onPrimary = Color(0xFFFFFDF8), secondary = Color(0xFF4F6B4A),
    background = Color(0xFFF3F0E8), onBackground = Color(0xFF1F2420),
    surface = Color(0xFFFFFDF8), onSurface = Color(0xFF1F2420), onSurfaceVariant = Color(0xFF5F665C),
    surfaceContainerLow = Color(0xFFFFFDF8), surfaceContainer = Color(0xFFFFFDF8), surfaceContainerHigh = Color(0xFFF7F5EF),
    secondaryContainer = Color(0xFFE3EADF), onSecondaryContainer = Color(0xFF2F4A2B),
    inverseSurface = Color(0xFF1F2420), inverseOnSurface = Color(0xFFF3F0E8), outline = Color(0xFFD9D0C2),
)

/** Follows the system unless the user picked a theme (spec §1.1). */
@Composable
fun NidooTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.NIGHT_GLASS -> true
        ThemeChoice.IVOIRE -> false
    }
    val colors = if (dark) NightGlassColors else IvoireColors
    CompositionLocalProvider(LocalNidooColors provides colors, LocalNidooType provides nidooType(dark)) {
        MaterialTheme(colorScheme = if (dark) NightScheme else IvoireScheme, typography = materialTypography(), content = content)
    }
}

object NidooTheme {
    val colors: NidooColors
        @Composable @ReadOnlyComposable get() = LocalNidooColors.current
    val type: NidooType
        @Composable @ReadOnlyComposable get() = LocalNidooType.current
}

/** Night Glass: radial gradient from the top-left; Ivoire: flat paper. */
@Composable
fun NidooBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = NidooTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(c.backgroundBottom)
                if (c.backgroundTop != c.backgroundBottom) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(c.backgroundTop, c.backgroundBottom),
                            center = Offset(size.width * 0.2f, 0f),
                            radius = size.maxDimension * 0.9f,
                        ),
                    )
                }
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides c.content) { content() }
    }
}
