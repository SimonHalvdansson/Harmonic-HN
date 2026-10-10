package com.simon.harmonichackernews.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.comments.CommentNavigationButtons
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThemeRippleTest {
    @Test
    fun defaultRippleLightensDarkPagesAndCards() = SwingUtilities.invokeAndWait {
        for (id in listOf("classic", "pure", "gray", "hacker_news", "hacker", "blue")) {
            val palette = HarmonicThemeCatalog.scheme(id, true)
            for (background in listOf(palette.colorScheme.pageBackground, palette.colorScheme.cardBackground)) {
                val (resting, pressed) = sampleRipple(palette, background)
                assertTrue(pressed.luminance() > resting.luminance(), "$id dark ripple must lighten $background")
            }
        }
    }

    @Test
    fun defaultRippleDarkensLightPagesAndCards() = SwingUtilities.invokeAndWait {
        for (id in listOf("classic", "pure", "gray", "hacker_news", "hacker", "blue")) {
            val palette = HarmonicThemeCatalog.scheme(id, false)
            for (background in listOf(palette.colorScheme.pageBackground, palette.colorScheme.cardBackground)) {
                val (resting, pressed) = sampleRipple(palette, background)
                assertTrue(pressed.luminance() < resting.luminance(), "$id light ripple must darken $background")
            }
        }
    }

    @Test
    fun brightFilledSurfaceStillUsesItsOwnDarkForeground() = SwingUtilities.invokeAndWait {
        val palette = HarmonicThemeCatalog.scheme("blue", true)
        val (resting, pressed) = sampleRipple(palette, palette.colorScheme.primary, filledSurface = true)
        assertTrue(pressed.luminance() < resting.luminance(), "Bright surfaces must retain their onPrimary ripple")
    }

    @Test
    fun commentNavigationRippleMatchesItsContainerInBothModes() = SwingUtilities.invokeAndWait {
        for (id in listOf("classic", "hacker_news")) for (dark in listOf(false, true)) {
            val palette = HarmonicThemeCatalog.scheme(id, dark)
            val (resting, pressed) = sampleRipple(palette, palette.colorScheme.pageBackground, navigationControls = true)
            if (palette.colorScheme.onPrimaryContainer.luminance() < resting.luminance()) {
                assertTrue(pressed.luminance() < resting.luminance(), "$id/$dark bright navigation pill must darken")
            } else {
                assertTrue(pressed.luminance() > resting.luminance(), "$id/$dark dark navigation pill must lighten")
            }
        }
    }

    @Test
    fun changingAppearanceRetainsScreenState() = SwingUtilities.invokeAndWait {
        val palette = mutableStateOf(HarmonicThemeCatalog.scheme("blue", false))
        var screenState: Any? = null
        val scene = ImageComposeScene(100, 100, Density(1f)) {
            HarmonicTheme(palette.value.colorScheme, palette.value.dark) {
                screenState = remember { Any() }
            }
        }
        try {
            scene.render(0L).close()
            val initialState = screenState
            for ((index, dark) in listOf(true, false).withIndex()) {
                palette.value = HarmonicThemeCatalog.scheme("blue", dark)
                repeat(5) { scene.render((index * 5 + it + 1) * 16_000_000L).close() }
                assertSame(initialState, screenState, "Appearance changes must retain the screen's remembered state")
            }
        } finally {
            scene.close()
        }
    }

    private fun sampleRipple(
        palette: HarmonicThemePalette,
        background: Color,
        filledSurface: Boolean = false,
        navigationControls: Boolean = false,
    ): Pair<Color, Color> {
        val scene = ImageComposeScene(100, 100, Density(1f)) {
            HarmonicTheme(palette.colorScheme, palette.dark) {
                if (navigationControls) {
                    Box(Modifier.fillMaxSize().background(background)) {
                        CommentNavigationButtons({}, {}, {}, {})
                    }
                } else if (filledSurface) {
                    Surface(color = background, contentColor = palette.colorScheme.onPrimary) {
                        Box(Modifier.fillMaxSize().clickable {})
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(background).clickable {})
                }
            }
        }
        fun sample(time: Long): Color = scene.render(time).use {
            it.toComposeImageBitmap().toPixelMap()[50, 50]
        }
        try {
            repeat(5) { sample(it * 16_000_000L) }
            val resting = sample(80_000_000L)
            scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 50f))
            repeat(20) { sample(96_000_000L + it * 16_000_000L) }
            return resting to sample(416_000_000L)
        } finally {
            scene.close()
        }
    }
}
