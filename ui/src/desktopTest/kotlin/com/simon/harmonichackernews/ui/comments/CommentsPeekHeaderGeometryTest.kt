package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CommentsPeekHeaderGeometryTest {
    @Test
    fun commentNavigationImmediatelyDismissesPeekAndResetsDirectionTravel() = runBlocking {
        val peek = CommentsPeekHeaderState(LazyListState(20), 24, 8f)
        peek.onPostScroll(Offset(0f, 100f), Offset.Zero, NestedScrollSource.UserInput)
        peek.revealAnimation.snapTo(1f)
        assertTrue(peek.visible)

        peek.dismissForNavigation(toHeader = false)
        assertFalse(peek.requested)
        assertFalse(peek.visible, "A jump target must not be covered even for non-animated jumps")
        peek.onPostScroll(Offset(0f, 100f), Offset.Zero, NestedScrollSource.SideEffect)
        assertFalse(peek.requested)
        peek.onPostScroll(Offset(0f, 4f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(peek.requested, "Navigation must discard the previous gesture's accumulated travel")
        peek.onPostScroll(Offset(0f, 4f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(peek.requested)
    }

    @Test
    fun returnToTopDisarmsPeekWithoutSnappingAwayTheHandoff() = runBlocking {
        val peek = CommentsPeekHeaderState(LazyListState(20), 24, 8f)
        peek.onPostScroll(Offset(0f, 100f), Offset.Zero, NestedScrollSource.UserInput)
        peek.revealAnimation.snapTo(1f)
        peek.dismissForNavigation(toHeader = true)
        assertFalse(peek.requested, "Later programmatic jumps must not resurrect the peek")
        assertEquals(1f, peek.reveal, "The composable can animate the remaining reveal to zero")
    }

    @Test
    fun animationAndScrollFramesDoNotRecomposeTheVisibleHeader() = SwingUtilities.invokeAndWait {
        val list = LazyListState()
        val peek = CommentsPeekHeaderState(list, topInsetPx = 24, directionThresholdPx = 8f)
        peek.titleBounds = Rect(16f, 100f, 380f, 160f)
        var commits = 0
        val scene = ImageComposeScene(400, 700, Density(1f)) {
            val visible = peek.visible
            SideEffect { commits++ }
            Box {
                LazyColumn(state = list) {
                    items(20, key = { it }, contentType = { "row" }) {
                        Box(Modifier.fillMaxWidth().height(600.dp))
                    }
                }
                if (visible) Text("Peek title")
            }
        }
        var time = 0L
        fun frames() { repeat(3) { scene.render(time++ * 16_666_667L).close() } }
        try {
            frames()
            list.dispatchRawDelta(200f)
            runBlocking { peek.revealAnimation.snapTo(0.1f) }
            frames()
            assertTrue(peek.visible)
            val visibleCommits = commits
            for (reveal in listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f)) {
                runBlocking { peek.revealAnimation.snapTo(reveal) }
                frames()
                assertEquals(visibleCommits, commits, "Reveal frames should only update graphics layers")
            }
            for (offset in listOf(180, 160, 140, 130, 110, 90, 80)) {
                list.dispatchRawDelta((offset - list.firstVisibleItemScrollOffset).toFloat())
                frames()
                assertEquals(visibleCommits, commits, "Scroll/handoff frames should not recompose text")
            }
            list.dispatchRawDelta(-80f)
            frames()
            assertFalse(peek.visible)
            assertEquals(visibleCommits + 1, commits, "Only the visibility boundary needs composition")
        } finally { scene.close() }
    }

    @Test
    fun handoffKeepsTitleAtItsNaturalPositionAndSize() = SwingUtilities.invokeAndWait {
        val list = LazyListState()
        val peek = CommentsPeekHeaderState(list, topInsetPx = 24, directionThresholdPx = 8f)
        var titleCoordinates: LayoutCoordinates? = null
        val scene = ImageComposeScene(400, 700, Density(1f)) {
            MaterialTheme {
                LazyColumn(state = list) {
                    item(key = "header", contentType = "header") {
                        Box(Modifier.fillMaxWidth().height(600.dp)
                            .onPlaced { peek.headerCoordinates = it }) {
                            Text("A wrapping story title with a stable layout anchor",
                                fontSize = 24.sp,
                                modifier = Modifier.padding(top = 200.dp)
                                    .measureCommentsPeekTitle(peek).onPlaced { titleCoordinates = it })
                        }
                    }
                    items(20, key = { it }, contentType = { "row" }) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        var frame = 0L
        fun render() { scene.render(frame++ * 16_666_667L).close() }
        try {
            repeat(3) { render() }
            val anchor = assertNotNull(peek.titleBounds)
            list.dispatchRawDelta(anchor.center.y - 24f)
            repeat(3) { render() }
            runBlocking { peek.revealAnimation.snapTo(1f) }
            repeat(20) {
                render()
                assertEquals(anchor, peek.titleBounds)
                val drawnBounds = assertNotNull(peek.headerCoordinates).localBoundingBoxOf(
                    assertNotNull(titleCoordinates), clipBounds = false,
                )
                assertEquals(anchor, drawnBounds,
                    "Handoff must not translate or scale the scrolling title (frame $it)")
            }
        } finally {
            scene.close()
        }
    }
}
