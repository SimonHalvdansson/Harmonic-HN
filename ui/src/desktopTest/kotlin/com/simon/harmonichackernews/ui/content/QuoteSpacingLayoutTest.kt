package com.simon.harmonichackernews.ui.content

import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuoteSpacingLayoutTest {
    @Test
    fun quoteGapHasIntermediateHeightsWithoutChangingTextOrOrdinaryParagraphGaps() = SwingUtilities.invokeAndWait {
        val body = prepareCommentHtml("&gt; Quote<p>Reply<p>Ordinary paragraph")
        fun measure(gap: Int): Int {
            var height = 0
            val scene = ImageComposeScene(400, 300, Density(1f)) {
                QuoteSpacedContent(body, gap.dp, Modifier.onSizeChanged { height = it.height }) {
                    Text(it, fontSize = 20.sp, style = TextStyle())
                }
            }
            try {
                repeat(3) { scene.render(it * 16_000_000L).close() }
                return height
            } finally { scene.close() }
        }
        val comfortable = measure(24)
        val middle = measure(18)
        val compact = measure(12)
        assertTrue(compact < middle && middle < comfortable)
        assertEquals(6, comfortable - middle)
        assertEquals(6, middle - compact)
        assertEquals(listOf("Quote", "Reply\n\nOrdinary paragraph"), body.quoteSpacedSegments().map { it.text })
    }
}
