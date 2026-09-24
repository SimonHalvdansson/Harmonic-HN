package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import kotlin.test.Test
import kotlin.test.assertTrue

class StoryTintContrastTest {
    @Test
    fun blendedTintsRemainDistinctFromActualPageAcrossThemesAndPaletteAdjustments() {
        val themes = listOf("dark", "gray", "amoled", "hacker", "light", "white", "hacker_news", "material_dark", "material_light", "material_fixed_dark", "material_fixed_light")
        for (theme in themes) {
            val colors = HarmonicThemeCatalog.resolve(theme, systemDark = false).colors
            for (hue in 0 until 360 step 30) {
                for (saturation in listOf(0f, 0.5f, 1f)) {
                    for (adjustments in listOf("100|110|0", "100|200|20", "100|0|-20", "200|200|20")) {
                        val config = "dominant|$adjustments"
                        val palette = PreviewTintPalette(dominant = PreviewTintSwatch(hue.toFloat(), saturation))
                        val raw = PreviewTintPolicy.calculateCardTint(colors.contentCardBackground.toArgb(), palette, config)
                        val tint = Color(PreviewTintPolicy.ensureCardTintContrast(raw, colors.background.toArgb(), config))
                        val a = tint.luminance()
                        val b = colors.background.luminance()
                        val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                        assertTrue(contrast >= 1.19999f, "$theme, hue=$hue, saturation=$saturation, $config: $contrast")
                    }
                }
            }
        }
    }
}
