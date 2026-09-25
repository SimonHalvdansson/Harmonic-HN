@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.os.Bundle
import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.comments.rememberAnimatedCommentRows
import com.simon.harmonichackernews.ui.comments.AnimatedCommentRows
import com.simon.harmonichackernews.ui.content.CommentHtmlTextCache
import com.simon.harmonichackernews.ui.content.CommentRenderModelCache
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Repeatable UI-thread CPU samples plus exact composition counts; no network or app settings. */
@RunWith(AndroidJUnit4::class)
class CommentRenderingPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun coldCommentComposition() {
        val item = mutableStateOf(comment(1, "Initial comment"))
        val style = CommentRowStyle(DisplayStyle.FLAT, 14f, false, false, "none", false, "default", false)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("dark", false)
            CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    // A newly visible comment has its own key in the production LazyColumn.
                    // Reusing one row's remembered typography/layout would favor the old path.
                    key(item.value.id) {
                        CommentRow(
                            comment = item.value, style = style, storyAuthor = null, accountUser = null,
                            userTag = null, subtreeReplyCount = 0, collapseParent = true,
                            showTopLevelIndicator = false, onToggleExpanded = {}, onShowActions = {},
                            onLinkLongClick = { _, _, _ -> }, onReferenceLongClick = { _, _, _ -> },
                        )
                    }
                }
            }
        }
        for (paragraphs in listOf(3, 40)) {
            val samples = mutableListOf<Long>()
            repeat(50) { sample ->
                val marker = "Sample $paragraphs $sample"
                val html = "$marker. " + (1..paragraphs).joinToString("") {
                    "<p>Paragraph $it has <b>evidence</b>, <i>formatting</i> and " +
                        "<a href='https://example.com/$it'>a reference</a>. Consider these tradeoffs.</p>"
                }
                // Snapshot creation is deliberately outside the measured UI rendering interval.
                val next = comment(100 + sample, html)
                var start = 0L
                compose.runOnIdle {
                    CommentRenderModelCache.clearForTest()
                    CommentHtmlTextCache.clearForTest()
                    start = Debug.threadCpuTimeNanos()
                    item.value = next
                }
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                compose.runOnIdle {
                    if (sample >= 30) samples += Debug.threadCpuTimeNanos() - start
                }
            }
            report("coldCommentCpuNs paragraphs=$paragraphs samples=${samples.joinToString(",")}")
        }
        scene.close()
    }

    @Test fun collapseCompositionWork() {
        val expanded = (1..8).map { id ->
            PortableVisibleComment(id, comment(id, "Comment $id", if (id == 1) 0 else 1), 8 - id)
        }
        val collapsed = listOf(expanded.first().let {
            it.copy(comment = it.comment.copy(presentation = it.comment.presentation.copy(expanded = false)))
        })
        val current = mutableStateOf(expanded)
        var compositions = 0
        var displayedIds = emptyList<Int>()
        compose.setContent {
            val state = rememberLazyListState()
            val animated: AnimatedCommentRows = rememberAnimatedCommentRows(current.value, state, true)
            val progress: () -> Float = animated.exitProgress
            SideEffect {
                compositions++
                displayedIds = animated.rows.map { it.comment.id }
            }
            LazyColumn(state = state, modifier = Modifier.height(600.dp)) {
                items(animated.rows, key = { it.comment.id }) { row ->
                    Box(Modifier.height(40.dp).layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val fraction = if (row.comment.id in animated.exitingIds) progress() else 1f
                        layout(placeable.width, (placeable.height * fraction).toInt()) { placeable.place(0, 0) }
                    })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val counts = mutableListOf<Int>()
        repeat(10) {
            compose.runOnIdle { current.value = expanded }
            compose.mainClock.advanceTimeBy(600)
            compose.runOnIdle { compositions = 0; current.value = collapsed }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertTrue(displayedIds.size > 1) }
            repeat(30) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.runOnIdle {
                assertEquals(listOf(1), displayedIds)
                counts += compositions
            }
        }
        report("collapseCompositions samples=${counts.joinToString(",")}")
        assertTrue("Animation frames must not recompose the caller: $counts", counts.all { it <= 3 })
    }

    private fun comment(id: Int, html: String, depth: Int = 0): PortableCommentItem = Comment().also {
        it.id = id; it.by = "reader"; it.text = html; it.depth = depth; it.expanded = true
    }.let { PortableCommentItem(it.toSnapshot(), it.presentationSnapshot()) }

    private fun report(text: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "\nPERF $text\n")
        })
    }
}
