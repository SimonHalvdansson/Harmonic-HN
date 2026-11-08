package com.simon.harmonichackernews.ui.content

import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.simon.harmonichackernews.settings.QuoteHighlightColor
import com.simon.harmonichackernews.settings.QuoteHighlightStyle
import com.simon.harmonichackernews.settings.QuoteSpacing
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuoteSpacingLayoutTest {
    @Test
    fun quotePaddingAnimatesThroughIntermediateHeightsInBothDirections() = SwingUtilities.invokeAndWait {
        val padding = mutableStateOf(false)
        val heights = mutableListOf<Int>()
        var height = 0
        val scene = ImageComposeScene(800, 600, Density(2f)) {
            Box(Modifier.fillMaxSize()) {
                CommentBodyText(
                    highlightQuotes = true, quoteSpacing = QuoteSpacing.COMFORTABLE,
                    quotePadding = padding.value,
                    quoteHighlightColor = QuoteHighlightColor.MATERIAL,
                    quoteHighlightStyle = QuoteHighlightStyle.BOTH,
                    html = "&gt; Quote<br>Reply", searchTerm = "", markedColor = Color.Yellow,
                    modifier = Modifier.onSizeChanged { height = it.height },
                    fontFamily = FontFamily.SansSerif, fontSize = 16f,
                    animateSearchMatches = false, onLinkClick = {}, onLinkLongClick = { _, _, _ -> },
                )
            }
        }
        try {
            var time = 0L
            fun settle() = repeat(24) {
                scene.render(time.also { time += 16_666_667L }).close()
                heights += height
            }
            settle()
            val unpadded = height
            heights.clear()
            padding.value = true
            settle()
            val padded = height
            assertEquals(unpadded + 8, padded, "2dp above and below at 2x density")
            assertTrue(heights.any { it > unpadded && it < padded }, "Padding must animate in")
            heights.clear()
            padding.value = false
            settle()
            assertEquals(unpadded, height)
            assertTrue(heights.any { it > unpadded && it < padded }, "Padding must animate out")
        } finally { scene.close() }
    }

    @Test
    fun quoteSettingsLeaveOrdinaryBodiesUnchangedAndQuotedBodiesStillRoundTrip() = SwingUtilities.invokeAndWait {
        for (html in listOf(
            "Ordinary <b>text</b><p>A comparison: 4 &gt; 3<pre><code>&gt; code</code></pre>",
            "&gt; A <b>quoted</b> paragraph<p>Reply<p>Ordinary paragraph",
        )) {
            val enabled = mutableStateOf(false)
            val spacing = mutableStateOf(QuoteSpacing.COMFORTABLE)
            val padding = mutableStateOf(true)
            val color = mutableStateOf(QuoteHighlightColor.MATERIAL)
            val style = mutableStateOf(QuoteHighlightStyle.BOTH)
            val scene = ImageComposeScene(400, 300, Density(1f)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    CommentBodyText(
                        highlightQuotes = enabled.value, quoteSpacing = spacing.value,
                        quotePadding = padding.value,
                        quoteHighlightColor = color.value, quoteHighlightStyle = style.value,
                        html = html, searchTerm = "", markedColor = Color.Yellow,
                        fontFamily = FontFamily.SansSerif, fontSize = 16f,
                        animateSearchMatches = false, onLinkClick = {}, onLinkLongClick = { _, _, _ -> },
                    )
                }
            }
            try {
                var time = 0L
                fun render(frames: Int = 24): IntArray {
                    repeat(frames) { scene.render(time.also { time += 16_666_667L }).close() }
                    return scene.render(time.also { time += 16_666_667L }).use { image ->
                        IntArray(400 * 300).also { image.toComposeImageBitmap().readPixels(it) }
                    }
                }
                val original = render()
                val hasQuotes = prepareCommentHtmlContent(html).hasQuotes
                enabled.value = true
                for (nextStyle in QuoteHighlightStyle.entries) {
                    style.value = nextStyle
                    spacing.value = QuoteSpacing.COMPACT
                    color.value = if (nextStyle == QuoteHighlightStyle.SIDE_LINE) {
                        QuoteHighlightColor.MATERIAL
                    } else QuoteHighlightColor.NEUTRAL
                    val during = render(4)
                    val settled = render()
                    if (hasQuotes) {
                        assertFalse(original.contentEquals(settled), "Quotes must still change presentation")
                    } else {
                        assertContentEquals(original, during, "Ordinary text must not animate with quote settings")
                        assertContentEquals(original, settled)
                    }
                }
                val padded = render()
                padding.value = false
                val unpadded = render()
                if (hasQuotes) assertFalse(padded.contentEquals(unpadded), "Padding changes quoted text layout")
                else assertContentEquals(padded, unpadded, "Padding leaves ordinary text unchanged")
                padding.value = true
                assertContentEquals(padded, render(), "Padding restores the same layout when re-enabled")
                enabled.value = false
                assertContentEquals(original, render(), "Disabling quotes restores original text and spacing")
            } finally { scene.close() }
        }
    }

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
