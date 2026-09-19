package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.ThemeAccentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalModelTagContrastTest {
    @Test
    fun legacyLightAndMaterialContainersReceiveReadableForegrounds() {
        assertEquals(Color.Black, localModelTagForeground(Color(0xff478a74), Color(0xff21005d)))
        assertEquals(Color.Black, localModelTagForeground(Color(0xff478a74), Color.White))
        assertEquals(Color.White, localModelTagForeground(Color(0xff374955), Color(0xff21005d)))
    }

    @Test
    fun existingReadableThemeForegroundIsPreserved() {
        val container = Color(0xffc6e9d8)
        val foreground = Color(0xff245b46)

        assertEquals(foreground, localModelTagForeground(container, foreground))
    }

    @Test
    fun badgesMeetSmallTextContrastAcrossStoredThemesAndAccentPresets() {
        val themes = listOf(
            "light", "white", "dark", "gray", "amoled", "hacker", "hacker_news",
            "material_light", "material_dark", "material_fixed_light", "material_fixed_dark",
        )
        for (theme in themes) for (accent in ThemeAccentCatalog.options) {
            val palette = HarmonicThemeCatalog.resolve(theme, systemDark = false, accentPreset = accent.value)
            val scheme = palette.colorScheme
            val pairs = listOf(
                scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.tertiaryContainer to scheme.onTertiaryContainer,
                palette.colors.surfaceContainerHighest to scheme.onSurfaceVariant,
            )
            for ((background, preferred) in pairs) {
                val foreground = localModelTagForeground(background, preferred)
                assertTrue(
                    contrast(background, foreground.compositeOver(background)) >= 4.5f,
                    "Unreadable badge in $theme/${accent.value}",
                )
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
