package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.ThemeAccentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentCountContrastTest {
    @Test
    fun collapsedReplyLabelsRemainReadableAcrossThemesAndAccents() {
        val themes = listOf(
            ThemePreferences.DEFAULT, "material_dark", "material_light", "material_fixed_dark",
            "material_fixed_light", "dark", "light", "hacker", "hacker_news",
            "amoled", "white", "gray",
        )
        for (theme in themes) for (dark in listOf(false, true)) {
            for (accent in ThemeAccentCatalog.options) {
                val background = HarmonicThemeCatalog.resolve(theme, dark, accent.value)
                    .colors.commentCountIndicator
                val foreground = commentCountContentColor(background)
                val contrast = (maxOf(background.luminance(), foreground.luminance()) + 0.05f) /
                    (minOf(background.luminance(), foreground.luminance()) + 0.05f)
                assertTrue(contrast >= 4.5f, "$theme / $dark / ${accent.value}: $contrast")
            }
        }
    }

    @Test
    fun legacyMaterialBadgeKeepsWhiteText() {
        assertEquals(Color.White, commentCountContentColor(Color(0xFF00668B)))
    }
}
