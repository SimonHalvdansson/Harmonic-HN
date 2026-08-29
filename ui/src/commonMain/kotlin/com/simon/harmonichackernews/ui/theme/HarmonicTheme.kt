package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class HarmonicColors(
    val background: Color,
    val accent: Color,
    val onSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val link: Color,
    val surfaceContainerHigh: Color,
    val storyCardBackground: Color,
    val surfaceContainerHighest: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val storyNormal: Color,
    val storyDisabled: Color,
    val outlineVariant: Color,
    val commentDivider: Color,
    val commentCountIndicator: Color,
    val drawable: Color,
    val popupMenuBackground: Color,
    val settingsSegment: Color,
    val settingsPageBackground: Color,
    val settingsItemBackground: Color,
    val settingsHeaderSelected: Color,
    val settingsMainToggle: Color,
    val settingsMainToggleText: Color,
    val overlayButton: Color,
    val submissionsCommentTimeBackground: Color,
    val submissionsCommentTimeOutline: Color,
)

private val LocalHarmonicColors = staticCompositionLocalOf<HarmonicColors> {
    error("HarmonicTheme is not present")
}

object HarmonicTheme {
    val colors: HarmonicColors
        @Composable get() = LocalHarmonicColors.current
}

/** Platform-neutral entry point for a palette resolved by an Android, iOS, or desktop shell. */
@Composable
fun HarmonicTheme(
    colors: HarmonicColors,
    colorScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHarmonicColors provides colors) {
        MaterialTheme(colorScheme = colorScheme) {
            CompositionLocalProvider(
                LocalRippleConfiguration provides RippleConfiguration(
                    color = if (colorScheme.background.luminance() < 0.5f) {
                        Color.White
                    } else {
                        Color.Unspecified
                    },
                ),
                content = content,
            )
        }
    }
}
