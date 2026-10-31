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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryMarkdownPreviewTest {
    @Test
    fun collapsedListRetainsEllipsisAndRefreshesLinkCallbacksAndFormatting() = SwingUtilities.invokeAndWait {
        val firstOpened = mutableListOf<String>()
        val nextOpened = mutableListOf<String>()
        val openLink = mutableStateOf<(String) -> Unit>({ firstOpened += it })
        val baseUrl = mutableStateOf("https://example.com/first/")
        val linkColor = mutableStateOf(Color.Blue)
        val bold = mutableStateOf(true)
        val expanded = mutableStateOf(false)
        val scene = ImageComposeScene(400, 100, Density(1f)) {
            SummaryMarkdownText(
                "- [Details](guide) **bold**\n- Hidden item", Color.Black, linkColor.value,
                FontFamily.SansSerif, 16.sp, 22.sp, openLink.value, baseUrl = baseUrl.value,
                maxLines = if (expanded.value) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis, enableBoldFormatting = bold.value,
            )
        }
        var time = 0L
        fun frames() = repeat(3) { scene.render(time.also { time += 16_000_000 }).close() }
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun text() = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .single { it.text.startsWith("Details") }
        fun link() = text().getLinkAnnotations(0, text().length).single().item as LinkAnnotation.Url
        try {
            frames()
            assertEquals("Details bold…", text().text)
            assertTrue(text().spanStyles.any { it.item.fontWeight == FontWeight.Bold })
            link().let { it.linkInteractionListener!!.onClick(it) }
            assertEquals(listOf("https://example.com/first/guide"), firstOpened)

            openLink.value = { nextOpened += it }
            frames()
            link().let { it.linkInteractionListener!!.onClick(it) }
            assertEquals(listOf("https://example.com/first/guide"), nextOpened)
            assertEquals(listOf("https://example.com/first/guide"), firstOpened)

            baseUrl.value = "https://example.com/next/"
            linkColor.value = Color.Red
            bold.value = false
            frames()
            assertEquals("Details bold…", text().text)
            assertTrue(text().spanStyles.none { it.item.fontWeight == FontWeight.Bold })
            assertEquals(Color.Red, link().styles!!.style!!.color)
            link().let { it.linkInteractionListener!!.onClick(it) }
            assertEquals(listOf("https://example.com/first/guide", "https://example.com/next/guide"), nextOpened)
            assertEquals(listOf("https://example.com/first/guide"), firstOpened)

            expanded.value = true
            frames()
            assertEquals("Details bold", text().text)
            expanded.value = false
            frames()
            assertEquals("Details bold…", text().text)
        } finally {
            scene.close()
        }
    }

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
