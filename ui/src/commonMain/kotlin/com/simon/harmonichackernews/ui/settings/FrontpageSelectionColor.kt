package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Light cards use a subtle wash; dark cards retain their separation from the page. */
internal fun frontpageSelectionColor(
    pageBackground: Color,
    cardBackground: Color,
    accent: Color,
): Color {
    val pageLuminance = pageBackground.luminance()
    if (pageLuminance > 0.5f) return accent.copy(alpha = 0.05f).compositeOver(cardBackground)
    val tinted = accent.copy(alpha = 0.16f).compositeOver(cardBackground)
    val contrastingTone = Color.White
    for (step in 0..100) {
        val candidate = lerp(tinted, contrastingTone, step / 100f)
        val luminance = candidate.luminance()
        val contrast = (maxOf(pageLuminance, luminance) + 0.05f) /
            (minOf(pageLuminance, luminance) + 0.05f)
        if (contrast >= 1.3f) return candidate
    }
    return contrastingTone
}
