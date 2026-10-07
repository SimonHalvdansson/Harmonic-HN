package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Shared canvas for screens, headers, navigation layers, and native hosts. Light themes place
 * lighter surfaceBright cards on surfaceContainer; dark themes retain their darker surface.
 * Resolve from the palette so native hosts and previews use the same background as Compose.
 */
val ColorScheme.pageBackground: Color
    get() = if (surface.luminance() > onSurface.luminance()) surfaceContainer else surface

/** Light-mode cards follow Android Settings' expressive preference surfaces. */
val ColorScheme.cardBackground: Color
    get() = if (surface.luminance() > onSurface.luminance()) surfaceBright else surfaceContainerLow

private val LocalHarmonicDarkTheme = staticCompositionLocalOf { false }

object HarmonicTheme {
    val isDark: Boolean
        @Composable get() = LocalHarmonicDarkTheme.current
}

/**
 * All UI colors come from MaterialTheme.colorScheme, including custom components.
 * Use pageBackground for the canvas, cardBackground + onSurface for cards, primary + onPrimary for actions,
 * secondaryContainer + onSecondaryContainer for selections, and outlineVariant for dividers.
 * Hosts use the same ColorScheme for native views, widgets, and reader-mode CSS.
 */
@Composable
fun HarmonicTheme(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val fontFamily = ProductSansFontFamily
    val typography = remember(fontFamily) { Typography(fontFamily = fontFamily) }
    CompositionLocalProvider(LocalHarmonicDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
