package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.comments.AskContainer
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class DeferredUiLayoutTest {
    @Test
    fun headerPaddingIsCorrectOnFirstLayoutAndResizesWithoutRecomposition() = SwingUtilities.invokeAndWait {
        val height = mutableIntStateOf(48)
        val list = LazyListState()
        var commits = 0
        val scene = ImageComposeScene(400, 800, Density(1f)) {
            val measured = remember { MeasuredHeaderState() }
            val padding = remember { MeasuredHeaderPadding(PaddingValues(bottom = 8.dp), measured.height, Density(1f)) }
            SideEffect { commits++ }
            MeasuredHeaderLayout(measured, Modifier.fillMaxSize(), header = {
                Spacer(Modifier.layout { measurable, constraints ->
                    val child = measurable.measure(Constraints.fixed(constraints.maxWidth, height.intValue))
                    layout(child.width, child.height) { child.place(0, 0) }
                })
            }) {
                LookaheadScope {
                    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize().measuredHeaderContent(measured)) {
                        items(30, key = { it }, contentType = { "row" }) {
                            Spacer(Modifier.layout { measurable, constraints ->
                                val child = measurable.measure(Constraints.fixed(constraints.maxWidth, 40))
                                layout(child.width, child.height) { child.place(0, 0) }
                            })
                        }
                    }
                }
            }
        }
        try {
            scene.render(0L).close()
            assertEquals(48, list.layoutInfo.beforeContentPadding)
            val initialCommits = commits
            height.intValue = 96
            Snapshot.sendApplyNotifications()
            scene.render(16_666_667L).close()
            assertEquals(96, list.layoutInfo.beforeContentPadding)
            assertEquals(initialCommits, commits)
        } finally { scene.close() }
    }

    @Test
    fun askAnimationKeepsConversationCompositionStable() = SwingUtilities.invokeAndWait {
        val progress = mutableFloatStateOf(0f)
        var commits = 0
        val scene = ImageComposeScene(400, 800, Density(1f)) {
            val source = rememberGraphicsLayer()
            AskContainer(Rect(20f, 80f, 380f, 200f), { progress.floatValue }, Color.White,
                source, summarySource = true) {
                SideEffect { commits++ }
                Spacer(Modifier.fillMaxSize())
            }
        }
        try {
            repeat(3) { scene.render(it * 16_666_667L).close() }
            val initialCommits = commits
            for (frame in 1..20) {
                progress.floatValue = frame / 20f
                Snapshot.sendApplyNotifications()
                scene.render((frame + 3) * 16_666_667L).close()
            }
            assertEquals(initialCommits, commits)
        } finally { scene.close() }
    }

    @Test
    fun cachedShimmerMatchesMovingGradientStop() = SwingUtilities.invokeAndWait {
        val progress = mutableFloatStateOf(0f)
        val base = Color(0xff334455)
        val highlight = Color(0xffbbccdd)
        val scene = ImageComposeScene(240, 80, Density(1f)) {
            Column {
                Box(Modifier.fillMaxWidth().height(40.dp).shimmerBackground(base, highlight) { progress.floatValue })
                Box(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(
                    0f to base, progress.floatValue.coerceIn(0f, 1f) to highlight, 1f to base,
                )))
            }
        }
        try {
            for ((frame, value) in listOf(0f, 0.1f, 0.5f, 0.9f, 1f).withIndex()) {
                progress.floatValue = value
                Snapshot.sendApplyNotifications()
                val image = scene.render(frame * 16_666_667L)
                try {
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    for (x in 0 until 240) {
                        val actual = pixels[x, 20]
                        val expected = pixels[x, 60]
                        assertTrue(abs(actual.red - expected.red) < 0.01f &&
                            abs(actual.green - expected.green) < 0.01f &&
                            abs(actual.blue - expected.blue) < 0.01f,
                            "Gradient changed at progress=$value x=$x: $actual vs $expected")
                    }
                } finally { image.close() }
            }
        } finally { scene.close() }
    }
}
