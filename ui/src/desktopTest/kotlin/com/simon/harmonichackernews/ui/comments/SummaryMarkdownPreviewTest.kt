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
