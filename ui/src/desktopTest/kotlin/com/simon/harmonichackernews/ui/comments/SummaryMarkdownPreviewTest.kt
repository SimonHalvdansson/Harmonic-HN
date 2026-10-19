package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

class SummaryMarkdownPreviewTest {
    @Test
    fun streamingFadePreservesExistingTextLayoutAndFinalLinkColors() = SwingUtilities.invokeAndWait {
        val markdown = mutableStateOf("Existing ")
        val streaming = mutableStateOf(true)
        var layouts = 0
        val scene = ImageComposeScene(400, 100, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SummaryMarkdownText(markdown.value, Color.Black, Color.Blue, FontFamily.SansSerif,
                    18.sp, 24.sp, {}, animateStreamingText = streaming.value,
                    onOverflowChanged = { layouts++ })
            }
        }
        var time = 0L
        fun frame() = scene.render(time.also { time += 16_000_000 }).use {
            it.toComposeImageBitmap().toPixelMap()
        }
        fun ink(pixels: androidx.compose.ui.graphics.PixelMap, startX: Int): Float {
            var total = 0f
            for (y in 0 until 40) for (x in startX until 400) {
                total += 1f - pixels[x, y].red
            }
            return total
        }
        try {
            repeat(20) { frame() }
            markdown.value = "Existing **new** [link](https://example.com)"
            repeat(2) { frame() }
            val early = frame()
            val layoutCount = layouts
            repeat(5) { frame() }
            val middle = frame()
            repeat(15) { frame() }
            val final = frame()
            assertEquals(layoutCount, layouts, "Alpha-only frames must not relayout text")
            assertTrue(ink(early, 90) < ink(middle, 90),
                "New text must fade in: ${ink(early, 90)}, ${ink(middle, 90)}, ${ink(final, 90)}")
            assertTrue(ink(middle, 90) <= ink(final, 90))
            for (y in 0 until 40) for (x in 0 until 65) {
                assertEquals(final[x, y], early[x, y], "Existing text must not fade")
            }
            streaming.value = false
            repeat(3) { frame() }
            val static = frame()
            for (y in 0 until 40) for (x in 0 until 400) {
                assertEquals(static[x, y], final[x, y], "Streaming must preserve final span/link colors")
            }
        } finally { scene.close() }
    }

    @Test
    fun collapsedListsKeepMarkerSpacingAndHangingIndentWithAGlobalLineLimit() = SwingUtilities.invokeAndWait {
        for (marker in listOf("-", "10.")) {
            val expanded = mutableStateOf(true)
            var height = 0
            val markdown = "$marker This first item wraps across more than one line.\n" +
                "$marker Another item with **bold text** and more detail.\n$marker A final item."
            val scene = ImageComposeScene(240, 240, Density(1f)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    SummaryMarkdownText(markdown, Color.Black, Color.Blue, FontFamily.SansSerif,
                        16.sp, 22.sp, {}, maxLines = if (expanded.value) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.onSizeChanged { height = it.height })
                }
            }
            var time = 0L
            fun frame() = scene.render(time.also { time += 16_000_000 }).use {
                it.toComposeImageBitmap().toPixelMap()
            }
            try {
                repeat(3) { frame() }
                val full = frame()
                val fullHeight = height
                expanded.value = false
                repeat(3) { frame() }
                val shortened = frame()
                assertTrue(height < fullHeight)
                assertTrue(height <= 66, "Three lines must cap the entire list, not each bullet")
                // Includes the wrapped second line: both marker gap and hanging indent
                // must match the expanded list exactly, before the truncated last line.
                for (y in 0 until 43) for (x in 0 until 240) {
                    assertEquals(full[x, y], shortened[x, y], "List alignment changed at $x,$y for $marker")
                }
            } finally {
                scene.close()
            }
        }
    }
}
