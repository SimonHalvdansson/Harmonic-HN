package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WelcomeBackgroundRenderTest {
    @Test
    fun dotCentersStayAnchoredWhileTheirSizeAndColorChange() = SwingUtilities.invokeAndWait {
        for (mirrored in listOf(false, true)) {
            val time = mutableStateOf(0.0)
            val scene = ImageComposeScene(160, 160, Density(1f)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color.White)
                    drawWelcomeCornerDots(
                        center = if (mirrored) Offset(160f, 160f) else Offset.Zero,
                        extentX = 160f, extentY = 160f, mirrored = mirrored,
                        color = Color.Red, pulseColor = Color.Blue, millis = time.value, dark = false,
                    )
                }
            }
            try {
                val expected = if (mirrored) 154 else 6
                fun sample(millis: Double, nanos: Long): Pair<Float, Color> {
                    time.value = millis
                    return scene.render(nanos).use { image ->
                        val pixels = image.toComposeImageBitmap().toPixelMap()
                        var ink = 0f
                        var centerX = 0f
                        var centerY = 0f
                        for (y in expected - 5..expected + 5) for (x in expected - 5..expected + 5) {
                            val weight = 1f - pixels[x, y].green
                            ink += weight
                            centerX += (x + .5f) * weight
                            centerY += (y + .5f) * weight
                        }
                        assertTrue(ink > 0f)
                        assertEquals(expected.toFloat(), centerX / ink, .2f, "Dot centers must not drift horizontally")
                        assertEquals(expected.toFloat(), centerY / ink, .2f, "Dot centers must not drift vertically")
                        ink to pixels[expected, expected]
                    }
                }
                val first = sample(0.0, 0L)
                val later = sample(6000.0, 16_666_667L)
                assertTrue(abs(first.first - later.first) > .3f, "Dots must visibly pulse")
                assertTrue(abs(first.second.red - later.second.red) > .05f, "Dots must change color")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun narrowAndWideWindowsKeepTheCenterQuietAndAnimateTheirCornersInBothThemes() = SwingUtilities.invokeAndWait {
        fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
        for ((width, height) in listOf(400 to 900, 840 to 820, 1280 to 720)) {
            for (dark in listOf(false, true)) {
                val theme = HarmonicThemeCatalog.scheme("dynamic", dark)
                val time = mutableStateOf(0.0)
                val scene = ImageComposeScene(width, height, Density(1f)) {
                    HarmonicTheme(theme.colorScheme, theme.dark) {
                        WelcomeBackground(time, Modifier.fillMaxSize())
                    }
                }
                try {
                    fun frame(nanos: Long) = scene.render(nanos).use { it.toComposeImageBitmap().toPixelMap() }
                    val first = frame(0L)
                    val cornerX = width / 20
                    val cornerY = height / 20
                    val edgeContrast = distance(first[cornerX, cornerY], theme.colorScheme.pageBackground)
                    val centerContrast = distance(first[width / 2, height / 2], theme.colorScheme.pageBackground)
                    assertTrue(edgeContrast > centerContrast + .02f,
                        "Decorations must favor the edges over the reading area ($width x $height, dark=$dark)")
                    time.value = 6500.0
                    val moved = frame(16_666_667L)
                    assertTrue(distance(first[cornerX, cornerY], moved[cornerX, cornerY]) > .001f,
                        "The glow must visibly change across animation frames")
                    time.value = -1.0
                    val reduced = frame(33_333_334L)
                    assertEquals(first[cornerX, cornerY], reduced[cornerX, cornerY])
                } finally {
                    scene.close()
                }
            }
        }
    }
}
