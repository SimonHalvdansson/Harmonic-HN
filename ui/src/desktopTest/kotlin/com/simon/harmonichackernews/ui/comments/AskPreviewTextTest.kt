package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskPreviewTextTest {
    @Test
    fun textSurvivesCollapseAndOpeningLinesStayStill() = SwingUtilities.invokeAndWait {
        for (markdown in listOf(false, true)) {
            val expanded = mutableStateOf(false)
            var height = 0
            val text = if (markdown) {
                "- First line\n- Second line\n- Third line\n- Fourth line\n- Fifth line\n- Sixth line"
            } else {
                "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line"
            }
            val scene = ImageComposeScene(240, 240, Density(1f)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AskPreviewText(expanded.value, 22.sp, Modifier.onSizeChanged { height = it.height }) { lines ->
                        if (markdown) {
                            SummaryMarkdownText(text, Color.Black, Color.Blue, FontFamily.SansSerif,
                                16.sp, 22.sp, {}, maxLines = lines, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(text, color = Color.Black, fontFamily = FontFamily.SansSerif,
                                fontSize = 16.sp, lineHeight = 22.sp, maxLines = lines,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            var time = 0L
            fun frame() = scene.render(time.also { time += 16_000_000 }).use {
                it.toComposeImageBitmap().toPixelMap()
            }
            fun ink(pixels: PixelMap, start: Int, end: Int): Float {
                var sum = 0f
                for (y in start until end) for (x in 0 until 240) sum += 1f - pixels[x, y].red
                return sum
            }
            try {
                repeat(4) { frame() }
                val collapsed = frame()
                val collapsedHeight = height
                expanded.value = true
                repeat(25) { frame() }
                val full = frame()
                val fullHeight = height
                assertTrue(fullHeight > collapsedHeight)
                expanded.value = false
                repeat(3) { frame() }
                val closing = frame()
                assertTrue(height in (collapsedHeight + 1) until fullHeight, "Height must shrink gradually")
                val outgoingInk = ink(closing, collapsedHeight, minOf(height, collapsedHeight + 22))
                assertTrue(outgoingInk > 0f, "Outgoing lines must survive the start of collapse")
                assertTrue(outgoingInk < ink(full, collapsedHeight, minOf(height, collapsedHeight + 22)),
                    "Outgoing text must fade while the card shrinks")
                for (y in 0 until 43) for (x in 0 until 240) {
                    assertEquals(full[x, y], closing[x, y], "Opening lines moved or faded at $x,$y")
                }
                // Reverse before collapse finishes: the same layouts must remain available.
                expanded.value = true
                repeat(25) { frame() }
                assertEquals(fullHeight, height)
                expanded.value = false
                repeat(25) { frame() }
                val restored = frame()
                assertEquals(collapsedHeight, height)
                for (y in 0 until collapsedHeight) for (x in 0 until 240) {
                    assertEquals(collapsed[x, y], restored[x, y], "Collapsed ellipsis must be restored")
                }
            } finally {
                scene.close()
            }
        }
    }
}
