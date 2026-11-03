package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichLinkPreviewExpansionTest {
    @Test
    fun expandAndCollapseMoveTheToggleThroughIntermediateHeights() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("RichPreviewExpansion")
        val settings = CommentDisplaySettings.from(app.app.userSettings.comments, false, false, false, false)
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(
            loaded = true,
            linkPreviewInfo = LinkPreviewInfo(LinkPreviewType.GITHUB_ISSUE, "A detailed issue",
                description = (1..28).joinToString("\n\n") { "Paragraph $it with **useful details**." },
                url = "https://github.com/example/repo/issues/42"),
        ))
        val platform = CommentsPreviewPlatform(TextStyle.Default, {}, {}, {}, { it })
        val scene = ImageComposeScene(400, 1600) {
            MaterialTheme {
                CommentsPreviewPlatformProvider(platform) {
                    Column { LinkPreviewContent(story, 0, settings) }
                }
            }
        }
        var time = 0L
        fun frames(count: Int) = repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
        fun toggle(label: String) = nodes(scene).single {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }.boundsInRoot
        fun tap(label: String) {
            val point = toggle(label).center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1, type = PointerType.Touch)
        }
        try {
            frames(35)
            val collapsed = toggle("Show more").top
            tap("Show more")
            frames(6)
            val expanding = toggle("Show less").top
            frames(30)
            val expanded = toggle("Show less").top
            assertTrue(expanding > collapsed && expanding < expanded,
                "The toggle must follow the expanding text: $collapsed / $expanding / $expanded")
            tap("Show less")
            frames(6)
            val collapsing = toggle("Show more").top
            assertTrue(collapsing > collapsed && collapsing < expanded,
                "The toggle must follow the collapsing text: $collapsed / $collapsing / $expanded")
            frames(30)
            assertEquals(collapsed, toggle("Show more").top, 1f)
        } finally { scene.close(); app.close() }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
    }
}
