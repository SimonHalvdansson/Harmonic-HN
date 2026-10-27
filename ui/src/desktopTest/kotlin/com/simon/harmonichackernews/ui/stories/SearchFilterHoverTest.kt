package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchFilterHoverTest {
    @Test fun lightThemeHoverCoversTheAnimatedLabelAndChipPaddingEvenly() = verifyHover(dark = false)

    @Test fun darkThemeHoverCoversTheAnimatedLabelAndChipPaddingEvenly() = verifyHover(dark = true)

    private fun verifyHover(dark: Boolean) = SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(600, 160, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "material_light", dark)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                val colors = MaterialTheme.colorScheme
                Box(Modifier.background(colors.pageBackground)) {
                    StorySearchHeader(
                        state = StorySearchPresentationState(
                            active = false, draft = "", suppressAutoFocus = true,
                            sortLabel = "Relevance", dateLabel = "All time", pointsLabel = "Any points",
                            commentsLabel = "Any comments", sortLabels = listOf("Relevance", "Date"),
                            dateLabels = listOf("All time"), pointsLabels = listOf("Any points"),
                            commentsLabels = listOf("Any comments"), onlyRead = false,
                        ),
                        sideStart = 16.dp, sideEnd = 16.dp,
                        iconColor = colors.onSurfaceVariant, menuColor = colors.surface,
                        menuTextColor = colors.onSurface, fontFamily = FontFamily.Default,
                        onDraftChanged = {}, onSearch = {}, onClose = {},
                        consumeFocusRequest = { false },
                        onOptionSelected = { _, _ -> }, onToggleOnlyRead = {},
                    )
                }
            }
        }
        try {
            var time = 0L
            fun settle() = repeat(30) { scene.render(time.also { time += 16_666_667L }).close() }
            settle()
            val idle = scene.render(time).use { it.toComposeImageBitmap().toPixelMap() }
            scene.sendPointerEvent(PointerEventType.Move, Offset(65f, 88f), type = PointerType.Mouse)
            settle()
            scene.render(time).use { image ->
                val hovered = image.toComposeImageBitmap().toPixelMap()
                // Sample the first chip's interior, excluding text pixels and its rounded border.
                val background = idle[28, 88]
                val hoverBackground = hovered[28, 88]
                assertTrue(background != hoverBackground, "Mouse hover must have visible feedback")
                for (y in 80..96) for (x in 28..110) {
                    if (idle[x, y] == background) {
                        assertTrue(hovered[x, y] == hoverBackground,
                            "Hover must tint the blank label area and padding equally at ($x, $y)")
                    }
                }
            }
            scene.sendPointerEvent(PointerEventType.Move, Offset(599f, 150f), type = PointerType.Mouse)
            settle()
            scene.render(time).use { image ->
                assertEquals(idle[28, 88], image.toComposeImageBitmap().toPixelMap()[28, 88],
                    "Leaving the chip must restore its original background")
            }
        } finally { scene.close() }
    }
}
