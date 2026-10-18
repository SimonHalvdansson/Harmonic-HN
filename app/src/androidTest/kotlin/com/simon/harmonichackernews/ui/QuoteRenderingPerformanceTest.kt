@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.os.Bundle
import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.QuoteSpacing
import com.simon.harmonichackernews.ui.content.CommentHtmlTextCache
import com.simon.harmonichackernews.ui.content.CommentRenderModelCache
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.prefetchCommentRenderModels
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run unchanged on both revisions with a non-debuggable test target. No network or settings writes. */
@RunWith(AndroidJUnit4::class)
class QuoteRenderingPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun prefetchedCommentComposition() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("profileQuoteRendering") == "true")
        val item = mutableStateOf(comment(0, "Initial comment"))
        val baseStyle = CommentRowStyle(
            DisplayStyle.FLAT, 14f, false, false, "none", false, "default", false,
        )
        val style = mutableStateOf(baseStyle)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        var start = 0L
        var completedSample = 0
        var elapsed = 0L
        var completedDrawSample = 0
        var drawElapsed = 0L
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    val displayedItem = item.value
                    key(displayedItem.id) {
                        CommentRow(
                            modifier = Modifier.onGloballyPositioned {
                                if (completedSample != displayedItem.id) {
                                    elapsed = Debug.threadCpuTimeNanos() - start
                                    completedSample = displayedItem.id
                                }
                            }.drawWithContent {
                                drawContent()
                                if (completedDrawSample != displayedItem.id) {
                                    drawElapsed = Debug.threadCpuTimeNanos() - start
                                    completedDrawSample = displayedItem.id
                                }
                            },
                            comment = displayedItem, style = style.value, storyAuthor = null, accountUser = null,
                            userTag = null, subtreeReplyCount = 0, collapseParent = true,
                            showTopLevelIndicator = false, onToggleExpanded = {}, onShowActions = {},
                            onLinkLongClick = { _, _, _ -> }, onReferenceLongClick = { _, _, _ -> },
                        )
                    }
                }
            }
        }
        try {
            var id = 1
            for (paragraphs in listOf(3, 20)) {
                for (mode in listOf("plainOff", "quotesOff", "quotesOn", "quotesCompact")) {
                    val samples = mutableListOf<Long>()
                    val drawSamples = mutableListOf<Long>()
                    val nextStyle = baseStyle.copy(
                        highlightQuotes = mode == "quotesOn" || mode == "quotesCompact",
                        quoteSpacing = if (mode == "quotesCompact") QuoteSpacing.COMPACT else QuoteSpacing.COMFORTABLE,
                    )
                    repeat(40) { sample ->
                        val html = buildString {
                            append("Comment $id.")
                            repeat(paragraphs) { paragraph ->
                                append("<p>")
                                if (mode != "plainOff") append("&gt; ")
                                append("A <b>quoted claim $paragraph</b> with <i>context</i>.")
                                append("<p>A reply with <a href='https://example.com/$paragraph'>supporting evidence</a>.")
                            }
                        }
                        val next = comment(id++, html)
                        compose.runOnIdle {
                            CommentRenderModelCache.clearForTest()
                            CommentHtmlTextCache.clearForTest()
                            runBlocking { prefetchCommentRenderModels(listOf(next), collectLinks = false) }
                            start = Debug.threadCpuTimeNanos()
                            style.value = nextStyle
                            item.value = next
                        }
                        compose.waitForIdle()
                        compose.runOnIdle {
                            assertTrue("The new row completed layout", completedSample == next.id && elapsed > 0)
                            assertTrue("The new row completed drawing", completedDrawSample == next.id && drawElapsed > 0)
                            if (sample >= 15) {
                                samples += elapsed
                                drawSamples += drawElapsed
                            }
                        }
                    }
                    InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                        putString("stream", "\nPERF quotePrefetchedLayoutCpuNs mode=$mode paragraphs=$paragraphs samples=${samples.joinToString(",")}\n" +
                            "PERF quotePrefetchedDrawCpuNs mode=$mode paragraphs=$paragraphs samples=${drawSamples.joinToString(",")}\n")
                    })
                }
            }
        } finally {
            scene.close()
        }
    }

    private fun comment(id: Int, html: String): PortableCommentItem = Comment().also {
        it.id = id
        it.by = "reader"
        it.text = html
        it.expanded = true
    }.let { PortableCommentItem(it.toSnapshot(), it.presentationSnapshot()) }
}
