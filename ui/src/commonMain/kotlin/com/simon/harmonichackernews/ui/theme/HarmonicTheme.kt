package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalHarmonicDarkTheme = staticCompositionLocalOf { false }

object HarmonicTheme {
    val isDark: Boolean
        @Composable get() = LocalHarmonicDarkTheme.current
}

/**
 * All UI colors come from MaterialTheme.colorScheme, including custom components.
 * Use surface/container + onSurface for content, primary + onPrimary for actions,
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
