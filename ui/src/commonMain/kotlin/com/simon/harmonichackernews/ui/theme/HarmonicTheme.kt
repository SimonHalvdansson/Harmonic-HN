package com.simon.harmonichackernews.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/**
 * Shared canvas for screens, headers, navigation layers, and native hosts. Light themes place
 * lighter surfaceBright cards on surfaceContainer; dark themes retain their darker surface.
 * Resolve from the palette so native hosts and previews use the same background as Compose.
 */
val ColorScheme.pageBackground: Color
    get() = if (surface.luminance() > onSurface.luminance()) surfaceContainer else surface

/** Menus stay distinct from the page canvas in both light and dark themes. */
val ColorScheme.menuBackground: Color
    get() = if (surface.luminance() > onSurface.luminance()) surfaceContainerLow else surfaceContainer

/** Light-mode cards follow Android Settings' expressive preference surfaces. */
val ColorScheme.cardBackground: Color
    get() = if (surface.luminance() > onSurface.luminance()) surfaceBright else surfaceContainer

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

/** One frame-clock animation per palette, shared by all Material roles. */
@Composable
internal fun animateThemeColorScheme(target: ColorScheme): ColorScheme {
    var displayed by remember { mutableStateOf(target) }
    // Hosts may supply a new ColorScheme instance with identical roles on recomposition.
    val targetColors = remember(target) { target.colors() }
    LaunchedEffect(targetColors) {
        if (displayed === target) return@LaunchedEffect
        // Capture the currently displayed palette before retargeting, including mid-animation.
        val interpolation = ThemePaletteInterpolation(displayed, target)
        animate(0f, 1f, animationSpec = tween(250)) { fraction, _ ->
            displayed = interpolation.at(fraction)
        }
    }
    return displayed
}

internal fun ColorScheme.colors(): List<Color> = buildList {
    mapColors { color -> color.also { add(it) } }
}

private class ThemePaletteInterpolation(private val from: ColorScheme, private val target: ColorScheme) {
    private val start = ArrayList<Color>()
    private val end = ArrayList<Color>()

    init {
        // Convert endpoints once, rather than converting all roles on every frame. As with
        // animateColorAsState, interpolation is in OKLab and output uses the target color space.
        from.mapColors { color -> color.also { start.add(it.convert(ColorSpaces.Oklab)) } }
        target.mapColors { color -> color.also { end.add(it.convert(ColorSpaces.Oklab)) } }
    }

    fun at(fraction: Float): ColorScheme {
        if (fraction <= 0f) return from
        if (fraction >= 1f) return target
        var index = 0
        return target.mapColors { color ->
            val role = index++
            if (start[role] == end[role]) color
            else lerp(start[role], end[role], fraction).convert(color.colorSpace)
        }
    }
}

/** Keep role ordering shared by endpoint conversion and interpolation. */
private inline fun ColorScheme.mapColors(transform: (Color) -> Color): ColorScheme = copy(
    primary = transform(primary),
    onPrimary = transform(onPrimary),
    primaryContainer = transform(primaryContainer),
    onPrimaryContainer = transform(onPrimaryContainer),
    inversePrimary = transform(inversePrimary),
    secondary = transform(secondary),
    onSecondary = transform(onSecondary),
    secondaryContainer = transform(secondaryContainer),
    onSecondaryContainer = transform(onSecondaryContainer),
    tertiary = transform(tertiary),
    onTertiary = transform(onTertiary),
    tertiaryContainer = transform(tertiaryContainer),
    onTertiaryContainer = transform(onTertiaryContainer),
    error = transform(error),
    onError = transform(onError),
    errorContainer = transform(errorContainer),
    onErrorContainer = transform(onErrorContainer),
    surface = transform(surface),
    onSurface = transform(onSurface),
    surfaceVariant = transform(surfaceVariant),
    onSurfaceVariant = transform(onSurfaceVariant),
    surfaceTint = transform(surfaceTint),
    inverseSurface = transform(inverseSurface),
    inverseOnSurface = transform(inverseOnSurface),
    outline = transform(outline),
    outlineVariant = transform(outlineVariant),
    scrim = transform(scrim),
    surfaceBright = transform(surfaceBright),
    surfaceDim = transform(surfaceDim),
    surfaceContainerLowest = transform(surfaceContainerLowest),
    surfaceContainerLow = transform(surfaceContainerLow),
    surfaceContainer = transform(surfaceContainer),
    surfaceContainerHigh = transform(surfaceContainerHigh),
    surfaceContainerHighest = transform(surfaceContainerHighest),
    primaryFixed = transform(primaryFixed),
    primaryFixedDim = transform(primaryFixedDim),
    onPrimaryFixed = transform(onPrimaryFixed),
    onPrimaryFixedVariant = transform(onPrimaryFixedVariant),
    secondaryFixed = transform(secondaryFixed),
    secondaryFixedDim = transform(secondaryFixedDim),
    onSecondaryFixed = transform(onSecondaryFixed),
    onSecondaryFixedVariant = transform(onSecondaryFixedVariant),
    tertiaryFixed = transform(tertiaryFixed),
    tertiaryFixedDim = transform(tertiaryFixedDim),
    onTertiaryFixed = transform(onTertiaryFixed),
    onTertiaryFixedVariant = transform(onTertiaryFixedVariant),
    background = transform(background),
    onBackground = transform(onBackground),
)

@Composable
internal fun animateThemeColor(target: Color): Color = animateColorAsState(
    targetValue = target,
    // Compose interpolates in OKLab and retargets from the currently displayed color.
    animationSpec = tween(250),
    label = "theme color",
).value
