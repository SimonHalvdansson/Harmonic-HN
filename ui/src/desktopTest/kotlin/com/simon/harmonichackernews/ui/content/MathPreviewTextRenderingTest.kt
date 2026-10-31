package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class MathPreviewTextRenderingTest {
    @Test
    fun oversizedFormulasRenderAsTheirOriginalSource() = SwingUtilities.invokeAndWait {
        for (formula in listOf(
            "\\rule{1000000000pt}{1000000000pt}",
            "\\rule{1pt}{1000000000pt}",
            "\\rule{1000000000pt}{1pt}",
        )) {
            for (display in listOf(false, true)) {
                val delimiter = if (display) "${'$'}${'$'}" else "${'$'}"
                val source = "$delimiter$formula$delimiter"
                val text = "Before $source after."
                val fallback = if (display) "Before \n$source\n after." else text
                assertContentEquals(render(fallback, math = false), render(text, math = true), text)
            }
        }
    }

    @Test
    fun ordinaryFormulasStillUseMathRendering() = SwingUtilities.invokeAndWait {
        val text = "Before ${'$'}x^2${'$'} after."
        assertFalse(render(text, math = false).contentEquals(render(text, math = true)))
    }

    private fun render(text: String, math: Boolean): IntArray {
        val scene = ImageComposeScene(360, 200, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                if (math) {
                    MathPreviewText(text, Color.Black, FontFamily.SansSerif, 16.sp, 22.sp, TextStyle.Default)
                } else {
                    Text(text, color = Color.Black, fontFamily = FontFamily.SansSerif,
                        fontSize = 16.sp, lineHeight = 22.sp, style = TextStyle.Default)
                }
            }
        }
        return try {
            repeat(3) { scene.render(it * 16_000_000L).close() }
            scene.render(48_000_000L).use { image ->
                IntArray(360 * 200).also { image.toComposeImageBitmap().readPixels(it) }
            }
        } finally {
            scene.close()
        }
    }
}
