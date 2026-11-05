package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_forum
import dev.chrisbanes.haze.rememberHazeState
import javax.swing.SwingUtilities
import kotlin.test.*

class StoryMetricPillMotionTest {
    @Test fun countUpdatesResizeThePillContinuouslyWithAndWithoutAnImage() = SwingUtilities.invokeAndWait {
        for (image in listOf(false, true)) {
            var count by mutableStateOf("9")
            val scene = ImageComposeScene(300, 100) {
                MaterialTheme {
                    val typography = rememberContentTypography("devicedefault")
                    Row {
                        StoryMetricPill(Res.drawable.ic_forum, count, "Comments", typography, 1f,
                            hazeState = if (image) rememberHazeState() else null, iconSize = 12.dp,
                            modifier = Modifier.testTag("pill"))
                    }
                }
            }
            fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::nodes)
            fun width() = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }.single {
                it.config.getOrNull(SemanticsProperties.TestTag) == "pill"
            }.boundsInRoot.width
            var time = 0L
            fun frame() { scene.render(time.also { time += 16_666_667L }).close() }
            try {
                repeat(5) { frame() }
                for (next in listOf("123", "8", "1.2k")) {
                    val start = width()
                    count = next
                    val widths = List(35) { frame(); width() }
                    val end = widths.last()
                    assertTrue(widths.distinct().size > 4, "Count change must animate, image=$image: $widths")
                    assertEquals(start, widths.first(), 1f)
                    assertTrue(widths.all { it >= minOf(start, end) - 1f && it <= maxOf(start, end) + 1f })
                }
            } finally { scene.close() }
        }
    }
}
