package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A stable palette whose roles can be observed independently, including from draw callbacks. */
@Stable
class HarmonicColors internal constructor(initial: ColorScheme) {
    internal var colorScheme by mutableStateOf(initial)
        private set
    var primary by mutableStateOf(initial.primary)
        private set
    var onPrimary by mutableStateOf(initial.onPrimary)
        private set
    var primaryContainer by mutableStateOf(initial.primaryContainer)
        private set
    var onPrimaryContainer by mutableStateOf(initial.onPrimaryContainer)
        private set
    var inversePrimary by mutableStateOf(initial.inversePrimary)
        private set
    var secondary by mutableStateOf(initial.secondary)
        private set
    var onSecondary by mutableStateOf(initial.onSecondary)
        private set
    var secondaryContainer by mutableStateOf(initial.secondaryContainer)
        private set
    var onSecondaryContainer by mutableStateOf(initial.onSecondaryContainer)
        private set
    var tertiary by mutableStateOf(initial.tertiary)
        private set
    var onTertiary by mutableStateOf(initial.onTertiary)
        private set
    var tertiaryContainer by mutableStateOf(initial.tertiaryContainer)
        private set
    var onTertiaryContainer by mutableStateOf(initial.onTertiaryContainer)
        private set
    var error by mutableStateOf(initial.error)
        private set
    var onError by mutableStateOf(initial.onError)
        private set
    var errorContainer by mutableStateOf(initial.errorContainer)
        private set
    var onErrorContainer by mutableStateOf(initial.onErrorContainer)
        private set
    var surface by mutableStateOf(initial.surface)
        private set
    var onSurface by mutableStateOf(initial.onSurface)
        private set
    var surfaceVariant by mutableStateOf(initial.surfaceVariant)
        private set
    var onSurfaceVariant by mutableStateOf(initial.onSurfaceVariant)
        private set
    var surfaceTint by mutableStateOf(initial.surfaceTint)
        private set
    var inverseSurface by mutableStateOf(initial.inverseSurface)
        private set
    var inverseOnSurface by mutableStateOf(initial.inverseOnSurface)
        private set
    var outline by mutableStateOf(initial.outline)
        private set
    var outlineVariant by mutableStateOf(initial.outlineVariant)
        private set
    var scrim by mutableStateOf(initial.scrim)
        private set
    var surfaceBright by mutableStateOf(initial.surfaceBright)
        private set
    var surfaceDim by mutableStateOf(initial.surfaceDim)
        private set
    var surfaceContainerLowest by mutableStateOf(initial.surfaceContainerLowest)
        private set
    var surfaceContainerLow by mutableStateOf(initial.surfaceContainerLow)
        private set
    var surfaceContainer by mutableStateOf(initial.surfaceContainer)
        private set
    var surfaceContainerHigh by mutableStateOf(initial.surfaceContainerHigh)
        private set
    var surfaceContainerHighest by mutableStateOf(initial.surfaceContainerHighest)
        private set
    var primaryFixed by mutableStateOf(initial.primaryFixed)
        private set
    var primaryFixedDim by mutableStateOf(initial.primaryFixedDim)
        private set
    var onPrimaryFixed by mutableStateOf(initial.onPrimaryFixed)
        private set
    var onPrimaryFixedVariant by mutableStateOf(initial.onPrimaryFixedVariant)
        private set
    var secondaryFixed by mutableStateOf(initial.secondaryFixed)
        private set
    var secondaryFixedDim by mutableStateOf(initial.secondaryFixedDim)
        private set
    var onSecondaryFixed by mutableStateOf(initial.onSecondaryFixed)
        private set
    var onSecondaryFixedVariant by mutableStateOf(initial.onSecondaryFixedVariant)
        private set
    var tertiaryFixed by mutableStateOf(initial.tertiaryFixed)
        private set
    var tertiaryFixedDim by mutableStateOf(initial.tertiaryFixedDim)
        private set
    var onTertiaryFixed by mutableStateOf(initial.onTertiaryFixed)
        private set
    var onTertiaryFixedVariant by mutableStateOf(initial.onTertiaryFixedVariant)
        private set
    var background by mutableStateOf(initial.background)
        private set
    var onBackground by mutableStateOf(initial.onBackground)
        private set
    var pageBackground by mutableStateOf(initial.pageBackground)
        private set
    var cardBackground by mutableStateOf(initial.cardBackground)
        private set
    var menuBackground by mutableStateOf(initial.menuBackground)
        private set

    internal fun update(scheme: ColorScheme) {
        colorScheme = scheme
        primary = scheme.primary
        onPrimary = scheme.onPrimary
        primaryContainer = scheme.primaryContainer
        onPrimaryContainer = scheme.onPrimaryContainer
        inversePrimary = scheme.inversePrimary
        secondary = scheme.secondary
        onSecondary = scheme.onSecondary
        secondaryContainer = scheme.secondaryContainer
        onSecondaryContainer = scheme.onSecondaryContainer
        tertiary = scheme.tertiary
        onTertiary = scheme.onTertiary
        tertiaryContainer = scheme.tertiaryContainer
        onTertiaryContainer = scheme.onTertiaryContainer
        error = scheme.error
        onError = scheme.onError
        errorContainer = scheme.errorContainer
        onErrorContainer = scheme.onErrorContainer
        surface = scheme.surface
        onSurface = scheme.onSurface
        surfaceVariant = scheme.surfaceVariant
        onSurfaceVariant = scheme.onSurfaceVariant
        surfaceTint = scheme.surfaceTint
        inverseSurface = scheme.inverseSurface
        inverseOnSurface = scheme.inverseOnSurface
        outline = scheme.outline
        outlineVariant = scheme.outlineVariant
        scrim = scheme.scrim
        surfaceBright = scheme.surfaceBright
        surfaceDim = scheme.surfaceDim
        surfaceContainerLowest = scheme.surfaceContainerLowest
        surfaceContainerLow = scheme.surfaceContainerLow
        surfaceContainer = scheme.surfaceContainer
        surfaceContainerHigh = scheme.surfaceContainerHigh
        surfaceContainerHighest = scheme.surfaceContainerHighest
        primaryFixed = scheme.primaryFixed
        primaryFixedDim = scheme.primaryFixedDim
        onPrimaryFixed = scheme.onPrimaryFixed
        onPrimaryFixedVariant = scheme.onPrimaryFixedVariant
        secondaryFixed = scheme.secondaryFixed
        secondaryFixedDim = scheme.secondaryFixedDim
        onSecondaryFixed = scheme.onSecondaryFixed
        onSecondaryFixedVariant = scheme.onSecondaryFixedVariant
        tertiaryFixed = scheme.tertiaryFixed
        tertiaryFixedDim = scheme.tertiaryFixedDim
        onTertiaryFixed = scheme.onTertiaryFixed
        onTertiaryFixedVariant = scheme.onTertiaryFixedVariant
        background = scheme.background
        onBackground = scheme.onBackground
        pageBackground = scheme.pageBackground
        cardBackground = scheme.cardBackground
        menuBackground = scheme.menuBackground
    }
}
