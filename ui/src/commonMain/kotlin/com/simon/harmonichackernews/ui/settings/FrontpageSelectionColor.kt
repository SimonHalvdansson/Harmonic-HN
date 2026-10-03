package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Use the expressive list color role, adjusting its tone only if it blends into the page. */
internal fun frontpageSelectionColor(
    pageBackground: Color,
    selectedContainer: Color,
): Color {
    val pageLuminance = pageBackground.luminance()
    val light = pageLuminance > 0.5f
    val contrastingTone = if (light) Color.Black else Color.White
    for (step in 0..100) {
        val candidate = lerp(selectedContainer, contrastingTone, step / 100f)
        val luminance = candidate.luminance()
        val contrast = (maxOf(pageLuminance, luminance) + 0.05f) /
            (minOf(pageLuminance, luminance) + 0.05f)
        if (contrast >= 1.3f) return candidate
    }
    return contrastingTone
}

/** Keep the preferred role when readable, including custom themes migrated from Android XML. */
internal fun frontpageSelectionContentColor(background: Color, preferred: Color): Color {
    fun contrast(foreground: Color): Float =
        (maxOf(background.luminance(), foreground.luminance()) + 0.05f) /
            (minOf(background.luminance(), foreground.luminance()) + 0.05f)
    return if (contrast(preferred) >= 4.5f) preferred
    else if (contrast(Color.White) >= contrast(Color.Black)) Color.White else Color.Black
}
