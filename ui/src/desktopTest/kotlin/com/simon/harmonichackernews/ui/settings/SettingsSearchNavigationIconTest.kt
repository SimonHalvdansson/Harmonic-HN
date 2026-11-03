package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import com.simon.harmonichackernews.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import java.io.File
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

class SettingsSearchNavigationIconTest {
    @Test fun endpointsRetainTheMaterialSymbolsGeometryAndOpacity() = SwingUtilities.invokeAndWait {
        val progress = mutableFloatStateOf(0f)
        val reference = mutableStateOf(false)
        val scene = ImageComposeScene(240, 240, Density(10f)) {
            if (reference.value) {
                Icon(painterResource(if (progress.floatValue == 0f) Res.drawable.ic_search else Res.drawable.ic_arrow_back),
                    null, Modifier.size(24.dp), tint = Color.Black)
            } else SettingsSearchNavigationIcon({ progress.floatValue }, tint = { Color.Black })
        }
        var time = 0L
        fun pixels(): FloatArray {
            repeat(3) { scene.render(time.also { time += 16_666_667 }).close() }
            return scene.render(time.also { time += 16_666_667 }).use {
                val map = it.toComposeImageBitmap().toPixelMap()
                FloatArray(240 * 240) { index -> map[index % 240, index / 240].alpha }
            }
        }
        try {
            for (endpoint in listOf(0f, 1f)) {
                progress.floatValue = endpoint
                reference.value = false
                val morph = pixels()
                reference.value = true
                val material = pixels()
                val error = morph.indices.sumOf { abs(morph[it] - material[it]).toDouble() } / material.sum()
                assertTrue(error < .08, "Endpoint $endpoint must match the existing Material symbol: $error; " +
                    "areas ${morph.sum()} / ${material.sum()}, opacity ${morph.max()} / ${material.max()}")
            }
        } finally { scene.close() }
    }

    @Test fun intermediateFramesMorphWithoutFadingAndReverseWithoutRestarting() = SwingUtilities.invokeAndWait {
        val progress = mutableFloatStateOf(0f)
        val scene = ImageComposeScene(240, 240, Density(10f)) {
            SettingsSearchNavigationIcon({ progress.floatValue }, tint = { Color.Black })
        }
        var time = 0L
        fun frame(p: Float): FloatArray {
            progress.floatValue = p
            repeat(3) { scene.render(time.also { time += 16_666_667 }).close() }
            return scene.render(time.also { time += 16_666_667 }).use { image ->
                System.getenv("HARMONIC_SEARCH_MORPH_EVIDENCE")?.let { directory ->
                    File(directory).mkdirs()
                    image.encodeToData(EncodedImageFormat.PNG)!!.use {
                        File(directory, "icon-${(p * 100).toInt()}.png").writeBytes(it.bytes)
                    }
                }
                val map = image.toComposeImageBitmap().toPixelMap()
                FloatArray(240 * 240) { index -> map[index % 240, index / 240].alpha }
            }
        }
        try {
            val search = frame(0f)
            val back = frame(1f)
            val forward = (0..20).map { frame(it / 20f) }
            for ((index, pixels) in forward.withIndex()) {
                assertEquals(1f, pixels.max(), .005f, "Keep the symbol's opacity at every frame")
                assertTrue(pixels.sum() > back.sum() * .65f, "The icon must remain visible, including midway")
                assertTrue(pixels.contentEquals(frame(index / 20f)), "Rendering depends only on progress")
            }
            // A cross-fade would simply be the weighted sum of the endpoint images.
            val middle = forward[10]
            val difference = middle.indices.sumOf { abs(middle[it] - (search[it] + back[it]) / 2f).toDouble() }
            assertTrue(difference / back.sum() > .3, "The midway frame must be a new shape")
            for (index in 20 downTo 0) {
                assertTrue(forward[index].contentEquals(frame(index / 20f)), "Back/cancellation must retrace the same geometry")
            }
        } finally { scene.close() }
    }
}
