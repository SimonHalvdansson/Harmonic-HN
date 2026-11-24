package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.UserAvatarOptions
import kotlin.test.assertTrue
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentRowAccessibilityTest {
    @Test
    fun trailingActionMovesAsDotAndReplyCountMakeRoom() = SwingUtilities.invokeAndWait {
        var fresh by mutableStateOf(false)
        var collapsed by mutableStateOf(false)
        var actionX = 0f
        val scene = ImageComposeScene(450, 80) {
            CommentMeta(
                author = "alice", age = "1h", isNew = fresh, avatarsEnabled = false,
                avatarOptions = UserAvatarOptions(), byOp = false, byUser = false, userTag = null,
                hiddenPreview = null, subtreeReplyCount = 12, showHiddenReplyCount = collapsed,
                emphasized = false, fontFamily = FontFamily.Default, animateChanges = true,
                trailingAction = {
                    Box(Modifier.size(36.dp).onGloballyPositioned { actionX = it.boundsInRoot().left })
                },
            )
        }
        var time = 0L
        fun positions(): List<Float> = List(36) {
            scene.render(time).close()
            time += 16_666_667L
            actionX
        }
        try {
            val initial = positions().last()
            fresh = true
            val dot = positions()
            assertTrue(dot.any { it < initial && it > dot.last() })
            collapsed = true
            val count = positions()
            assertTrue(count.any { it < dot.last() && it > count.last() })
            collapsed = false
            fresh = false
            assertEquals(initial, positions().last())
        } finally {
            scene.close()
        }
    }

    @Test
    fun actionLabelsTrackExpandedStateGesturePreferenceAndSearchContext() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("CommentRowAccessibility")
        val dependencies = HarmonicUiDependencies(bootstrap.app, bootstrap.scene)
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        val style = CommentRowStyle(DisplayStyle.FLAT, 16f, false, false, "none", false, "devicedefault", animateChanges = false)
        var expanded by mutableStateOf(true)
        var swapped by mutableStateOf(false)
        var search by mutableStateOf(false)
        val scene = ImageComposeScene(450, 200) {
            CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    CommentRow(
                        comment = PortableCommentItem(CommentSnapshot(id = 2, author = "alice", text = "Hello", expandedAnchorText = "Hello"),
                            CommentPresentationSnapshot(expanded = expanded)),
                        style = style, storyAuthor = null, accountUser = null, userTag = null, age = "1h",
                        subtreeReplyCount = 3, collapseParent = true, showTopLevelIndicator = false,
                        isThreadRow = !search, showActionsOnClick = swapped && !search, enableLongClick = !search,
                        clickActionLabel = if (search) "Show comment in thread" else null,
                        onToggleExpanded = {}, onShowActions = {}, onLinkLongClick = { _, _, _ -> },
                        onReferenceLongClick = { _, _, _ -> },
                    )
                }
            }
        }
        var time = 0L
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun assertActions(click: String, longClick: String?, state: String?) {
            repeat(4) { scene.render(time.also { time += 16_666_667L }).close() }
            val row = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }.single {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == click
            }
            assertEquals(longClick, row.config.getOrNull(SemanticsActions.OnLongClick)?.label)
            if (longClick == null) assertEquals(null, row.config.getOrNull(SemanticsActions.OnLongClick))
            assertEquals(state, row.config.getOrNull(SemanticsProperties.StateDescription))
        }
        try {
            assertActions("Collapse 3 replies", "Open comment actions", "Expanded")
            expanded = false
            assertActions("Expand 3 replies", "Open comment actions", "Collapsed")
            swapped = true
            assertActions("Open comment actions", "Expand 3 replies", "Collapsed")
            search = true
            assertActions("Show comment in thread", null, null)
        } finally {
            scene.close()
            bootstrap.close()
        }
    }
}
