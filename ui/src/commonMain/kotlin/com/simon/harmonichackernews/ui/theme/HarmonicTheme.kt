package com.simon.harmonichackernews.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
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
private val LocalTargetColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

object HarmonicTheme {
    val isDark: Boolean
        @Composable get() = LocalHarmonicDarkTheme.current

    /** Stable destination palette for image extraction and other cached color calculations. */
    val targetColorScheme: ColorScheme
        @Composable get() = LocalTargetColorScheme.current ?: MaterialTheme.colorScheme
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
    // Scheme/style changes blend within the current appearance. Reset only the color animation
    // on light/dark changes, avoiding an intermediate palette that flips the surface hierarchy.
    val displayedScheme = key(darkTheme) { animateThemeColorScheme(colorScheme) }
    CompositionLocalProvider(
        LocalHarmonicDarkTheme provides darkTheme,
        LocalTargetColorScheme provides colorScheme,
    ) {
        MaterialTheme(colorScheme = displayedScheme, typography = typography, content = content)
    }
}

/** Animate every Material role with the same motion as the theme preview. */
@Composable
private fun animateThemeColorScheme(target: ColorScheme): ColorScheme = target.copy(
    primary = animateThemeColor(target.primary),
    onPrimary = animateThemeColor(target.onPrimary),
    primaryContainer = animateThemeColor(target.primaryContainer),
    onPrimaryContainer = animateThemeColor(target.onPrimaryContainer),
    inversePrimary = animateThemeColor(target.inversePrimary),
    secondary = animateThemeColor(target.secondary),
    onSecondary = animateThemeColor(target.onSecondary),
    secondaryContainer = animateThemeColor(target.secondaryContainer),
    onSecondaryContainer = animateThemeColor(target.onSecondaryContainer),
    tertiary = animateThemeColor(target.tertiary),
    onTertiary = animateThemeColor(target.onTertiary),
    tertiaryContainer = animateThemeColor(target.tertiaryContainer),
    onTertiaryContainer = animateThemeColor(target.onTertiaryContainer),
    error = animateThemeColor(target.error),
    onError = animateThemeColor(target.onError),
    errorContainer = animateThemeColor(target.errorContainer),
    onErrorContainer = animateThemeColor(target.onErrorContainer),
    surface = animateThemeColor(target.surface),
    onSurface = animateThemeColor(target.onSurface),
    surfaceVariant = animateThemeColor(target.surfaceVariant),
    onSurfaceVariant = animateThemeColor(target.onSurfaceVariant),
    surfaceTint = animateThemeColor(target.surfaceTint),
    inverseSurface = animateThemeColor(target.inverseSurface),
    inverseOnSurface = animateThemeColor(target.inverseOnSurface),
    outline = animateThemeColor(target.outline),
    outlineVariant = animateThemeColor(target.outlineVariant),
    scrim = animateThemeColor(target.scrim),
    surfaceBright = animateThemeColor(target.surfaceBright),
    surfaceDim = animateThemeColor(target.surfaceDim),
    surfaceContainerLowest = animateThemeColor(target.surfaceContainerLowest),
    surfaceContainerLow = animateThemeColor(target.surfaceContainerLow),
    surfaceContainer = animateThemeColor(target.surfaceContainer),
    surfaceContainerHigh = animateThemeColor(target.surfaceContainerHigh),
    surfaceContainerHighest = animateThemeColor(target.surfaceContainerHighest),
    primaryFixed = animateThemeColor(target.primaryFixed),
    primaryFixedDim = animateThemeColor(target.primaryFixedDim),
    onPrimaryFixed = animateThemeColor(target.onPrimaryFixed),
    onPrimaryFixedVariant = animateThemeColor(target.onPrimaryFixedVariant),
    secondaryFixed = animateThemeColor(target.secondaryFixed),
    secondaryFixedDim = animateThemeColor(target.secondaryFixedDim),
    onSecondaryFixed = animateThemeColor(target.onSecondaryFixed),
    onSecondaryFixedVariant = animateThemeColor(target.onSecondaryFixedVariant),
    tertiaryFixed = animateThemeColor(target.tertiaryFixed),
    tertiaryFixedDim = animateThemeColor(target.tertiaryFixedDim),
    onTertiaryFixed = animateThemeColor(target.onTertiaryFixed),
    onTertiaryFixedVariant = animateThemeColor(target.onTertiaryFixedVariant),
    background = animateThemeColor(target.background),
    onBackground = animateThemeColor(target.onBackground),
)

@Composable
internal fun animateThemeColor(target: Color): Color = animateColorAsState(
    targetValue = target,
    // Compose interpolates in OKLab and retargets from the currently displayed color.
    animationSpec = tween(250),
    label = "theme color",
).value
