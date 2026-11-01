package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.ui.theme.colorContrast
import kotlin.test.Test
import kotlin.test.assertTrue

class StoryCategoryColorsTest {
    @Test
    fun tintedChipsStayReadableAcrossLightDarkAndSaturatedCardColors() {
        val surfaces = listOf(Color.Black, Color.White, Color(0xFF0C4A6E), Color(0xFFE0F2FE),
            Color(0xFF7F1D1D), Color(0xFFFFD700), Color(0xFF15803D), Color(0xFF777777))
        for (surface in surfaces) for (preferred in listOf(Color.Black, Color.White, Color(0xFF606060))) {
            val colors = tintedStoryCategoryColors(surface, preferred)
            assertTrue(colorContrast(colors.foreground, colors.container) >= 4.49f)
            assertTrue(colorContrast(colors.container, surface) > 1f)
        }
    }
}
