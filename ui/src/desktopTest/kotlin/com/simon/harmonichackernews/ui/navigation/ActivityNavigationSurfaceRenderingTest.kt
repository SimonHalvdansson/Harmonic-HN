package com.simon.harmonichackernews.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Pixel contract for the shared compositor; Android HWUI still needs on-device verification. */
class ActivityNavigationSurfaceRenderingTest {
    @Test
    fun sampledEdgesAndNestedClipsSurviveFadeCompletionAndBothTranslationDirections() = SwingUtilities.invokeAndWait {
        val offset = mutableFloatStateOf(0f)
        val alpha = mutableFloatStateOf(1f)
        val card = mutableStateOf(Color.Red)
        val scene = ImageComposeScene(320, 240, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ActivityNavigationViewportLayout(
                    translationX = { offset.floatValue },
                    alpha = { alpha.floatValue },
                    modifier = Modifier,
                    contentModifier = Modifier,
                ) {
                    Box(Modifier.fillMaxSize().drawBehind {
                        drawRect(Color.White)
                        drawRect(Color.Green, size = Size(10f, size.height))
                        drawRect(Color.Blue, topLeft = Offset(size.width - 10f, 0f), size = Size(10f, size.height))
                    }) {
                        Box(Modifier.offset(100.dp, 40.dp).size(100.dp)
                            .clip(RoundedCornerShape(24.dp)).background(card.value)) {
                            Box(Modifier.offset(30.dp, 30.dp).size(40.dp)
                                .clip(CircleShape).background(Color.Yellow))
                        }
                    }
                }
            }
        }
        var time = 0L
        fun pixels(): PixelMap {
            // Let composition and layer invalidations settle without changing the sampled offset.
            repeat(3) { scene.render(time.also { time += 16_666_667L }).close() }
            return scene.render(time.also { time += 16_666_667L }).use {
                it.toComposeImageBitmap().toPixelMap()
            }
        }
        fun assertPixel(pixels: PixelMap, x: Int, y: Int, color: Color, label: String) {
            val actual = pixels[x, y]
            val opacity = alpha.floatValue
            assertTrue(
                abs(actual.red - color.red * opacity) < 0.02f &&
                    abs(actual.green - color.green * opacity) < 0.02f &&
                    abs(actual.blue - color.blue * opacity) < 0.02f && actual.alpha == 1f,
                "$label at offset=${offset.floatValue}, alpha=$opacity: $actual",
            )
        }
        try {
            // Full opacity while still moving is the problematic interval in #416. Include
            // returning to rest and moving again so a stale recording cannot satisfy the test.
            for (translation in listOf(96, 24, 1, 0, -1, -24, -96, 0, 96, 0)) {
                offset.floatValue = translation.toFloat()
                card.value = if (card.value == Color.Red) Color.Magenta else Color.Red
                for (opacity in listOf(0f, 0.5f, 1f)) {
                    alpha.floatValue = opacity
                    val image = pixels()
                    assertPixel(image, 150 + translation, 55, card.value, "Rounded card background")
                    assertPixel(image, 150 + translation, 90, Color.Yellow, "Nested circular layer")
                    assertPixel(image, 101 + translation, 41, Color.White, "Clipped card corner")
                    assertPixel(image, 160, 200, Color.White, "Opaque page hides predecessor")
                    assertPixel(image, 2, 200, if (translation > -10) Color.Green else Color.White, "Left edge")
                    assertPixel(image, 317, 200, if (translation < 10) Color.Blue else Color.White, "Right edge")
                }
            }
        } finally {
            scene.close()
        }
    }
}
