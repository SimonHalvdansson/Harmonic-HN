package com.simon.harmonichackernews.ui.content

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuoteSpacingLayoutTest {
    @Test
    fun animatingGapDoesNotRecomposeOrRemeasureParagraphs() = SwingUtilities.invokeAndWait {
        val body = prepareCommentHtml("&gt; Quote<p>Reply")
        val gap = mutableStateOf(24.dp)
        var compositions = 0
        var measurements = 0
        var height = 0
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            QuoteSpacedContent(body.quoteSpacedSegments(), { gap.value }, Modifier.wrapContentHeight(unbounded = true).onSizeChanged { height = it.height }) { text, modifier ->
                SideEffect { compositions++ }
                Text(text, modifier = modifier.layout { measurable, constraints ->
                    measurements++
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                })
            }
        }
        try {
            var time = 0L
            fun settle() = repeat(4) { scene.render(time.also { time += 16_666_667 }).close() }
            settle()
            val initialCompositions = compositions
            val initialMeasurements = measurements
            val initialHeight = height
            gap.value = 12.dp
            settle()
            assertEquals(initialHeight - 12, height)
            assertEquals(initialCompositions, compositions)
            assertEquals(initialMeasurements, measurements)
        } finally { scene.close() }
    }

    @Test
    fun quoteGapHasIntermediateHeightsWithoutChangingTextOrOrdinaryParagraphGaps() = SwingUtilities.invokeAndWait {
        val body = prepareCommentHtml("&gt; Quote<p>Reply<p>Ordinary paragraph")
        fun measure(gap: Int): Int {
            var height = 0
            val scene = ImageComposeScene(400, 300, Density(1f)) {
                QuoteSpacedContent(body.quoteSpacedSegments(), { gap.dp }, Modifier.onSizeChanged { height = it.height }) { text, modifier ->
                    Text(text, modifier = modifier, fontSize = 20.sp, style = TextStyle())
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
