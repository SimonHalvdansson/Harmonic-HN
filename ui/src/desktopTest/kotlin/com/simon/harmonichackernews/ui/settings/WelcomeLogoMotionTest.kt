package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.harmonic_app_icon
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class WelcomeLogoMotionTest {
    @Test
    fun wavePixelsChangeWithoutMovingTheIconsDisk() = SwingUtilities.invokeAndWait {
        val time = mutableStateOf(5000.0)
        val scene = ImageComposeScene(360, 400, Density(1f)) {
            val theme = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(theme.colorScheme, theme.dark) {
                WelcomeAnimatedHeader(time) {
                    Image(painterResource(Res.drawable.harmonic_app_icon), null, Modifier.size(114.dp))
                }
            }
        }
        try {
            fun capture(label: String, frameTime: Long): List<Int> {
                return scene.render(frameTime).use { image ->
                    System.getenv("HARMONIC_WELCOME_CAPTURE_DIR")?.let { directory ->
                        File(directory).mkdirs()
                        image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                            File(directory, "welcome-waves-$label.png").writeBytes(data.bytes)
                        }
                    }
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    // Inside the peach disk, excluding the independently rotating bloom.
                    (65 until 130).flatMap { y -> (140 until 220).map { x -> pixels[x, y].toArgb() } }
                }
            }
            val first = capture("5s", 0L)
            // The disk's 10-second bob repeats; the waves' 7.5-second flex is in another phase.
            time.value = 15000.0
            val second = capture("15s", 16_666_667L)
            assertTrue(first.zip(second).count { (a, b) -> a != b } > 100,
                "The waves themselves must change, independently of the disk's position")
        } finally {
            scene.close()
        }
    }
}
