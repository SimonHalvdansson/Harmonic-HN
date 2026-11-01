package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryCategoryArrivalTest {
    @Test
    fun cachedTagsReserveTheirFullHeightInTheFirstFrame() = SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(170, 300, Density(1f)) {
            MaterialTheme {
                Column {
                    Text("Story title")
                    StoryCategoryLabels(listOf("Programming", "World news", "Security"), 1f)
                    Text("Domain", Modifier.testTag("metadata"))
                }
            }
        }
        try {
            scene.render(0).close()
            val first = metadataTop(scene)
            assertTrue(first > 40f, "Cached tags must already occupy their rows")
            scene.render(500_000_000L).close()
            assertEquals(first, metadataTop(scene), "Initial cached content must not replay an arrival animation")
        } finally { scene.close() }
    }

    @Test
    fun lateTagsAndTheirRemovalAnimateTheHeightInsteadOfJumping() = SwingUtilities.invokeAndWait {
        val labels = mutableStateOf<List<String>>(emptyList())
        val scene = ImageComposeScene(170, 300, Density(1f)) {
            MaterialTheme {
                Column {
                    Text("Story title")
                    StoryCategoryLabels(labels.value, 1f)
                    Text("Domain", Modifier.testTag("metadata"))
                }
            }
        }
        var time = 0L
        fun frame(milliseconds: Long) { time += milliseconds * 1_000_000; scene.render(time).close() }
        try {
            repeat(10) { frame(16) }
            val empty = metadataTop(scene)
            labels.value = listOf("Programming", "World news", "Security")
            repeat(8) { frame(16) }
            val arriving = metadataTop(scene)
            repeat(30) { frame(16) }
            val full = metadataTop(scene)
            assertTrue(arriving > empty && arriving < full, "Late results must pass through intermediate heights: empty=$empty, arriving=$arriving, full=$full")
            labels.value = emptyList()
            repeat(8) { frame(16) }
            val leaving = metadataTop(scene)
            repeat(30) { frame(16) }
            assertTrue(leaving > empty && leaving < full, "Removal heights: empty=$empty, leaving=$leaving, full=$full")
            assertEquals(empty, metadataTop(scene))
        } finally { scene.close() }
    }

    private fun metadataTop(scene: ImageComposeScene): Float {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }.first {
            it.config.getOrNull(SemanticsProperties.TestTag) == "metadata"
        }.boundsInRoot.top
    }
}
