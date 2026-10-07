package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.settings.PreviewTintPolicy

/**
 * Resolve a raw, cached palette tint against the surface's current surroundings. Keep correction
 * at presentation time so theme changes never reuse a color adjusted for a different background.
 * A missing tint stays null so each caller retains its existing untinted fallback.
 */
@Composable
internal fun rememberStoryTintColor(
    rawTint: Int?,
    paletteTintConfigKey: String,
    background: Color = MaterialTheme.colorScheme.pageBackground,
): Color? = remember(rawTint, background, paletteTintConfigKey) {
    rawTint?.let {
        Color(PreviewTintPolicy.ensureCardTintContrast(it, background.toArgb(), paletteTintConfigKey))
    }
}
