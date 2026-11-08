package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RowSpacingLayoutTest {
    @Test fun storyAndCommentSpacingAnimateAndMatchRuntimeRowsAcrossSurfaceStyles() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("RowSpacingLayoutTest")
        val settings = bootstrap.app.settings.snapshot()
        try {
            for (display in DisplayStyle.entries) for (story in listOf(true, false)) {
                val spacing = mutableFloatStateOf(1f)
                var animatedHeight = 0
                var runtimeHeight = 0
                val storyStyle = StoryDisplaySettings.from(settings.story).copy(
                    previewImageMode = StoryPreviewMode.OFF, displayStyle = display, tintCardsFromImages = false,
                ).toStoryRowStyle(StoryRowStyleContext(53, 18, false))
                val commentStyle = CommentDisplaySettings.from(settings.comments, false, false, false, false)
                    .toCommentRowStyle(CommentRowStyleContext.Thread(animateChanges = true)).copy(displayStyle = display)
                val comment = PortableCommentItem(
                    comment = CommentSnapshot(id = -408, author = "willow", text = "A short comment.", expandedAnchorText = "A short comment."),
                    presentation = CommentPresentationSnapshot(expanded = true, depth = 1),
                )
                val scene = ImageComposeScene(400, 800) {
                    CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                        MaterialTheme {
                            Column {
                                for (animate in listOf(true, false)) {
                                    val modifier = Modifier.onSizeChanged {
                                        if (animate) animatedHeight = it.height else runtimeHeight = it.height
                                    }
                                    if (story) StoryRow(
                                        SettingsStoryPreviewModel, storyStyle.copy(spacingScale = spacing.floatValue),
                                        modifier, listItem = true, animateChanges = animate,
                                    ) else CommentRow(
                                        comment, commentStyle.copy(spacingScale = spacing.floatValue, animateChanges = animate),
                                        storyAuthor = null, accountUser = null, userTag = null, subtreeReplyCount = 0,
                                        collapseParent = false, showTopLevelIndicator = true, modifier = modifier,
                                        onToggleExpanded = {}, onShowActions = {}, onLinkLongClick = { _, _, _ -> },
                                        onReferenceLongClick = { _, _, _ -> }, age = "1h",
                                    )
                                }
                            }
                        }
                    }
                }
                try {
                    var time = 0L
                    fun frames(): List<Int> = (0..35).map {
                        scene.render(time.also { time += 16_666_667L }).close()
                        animatedHeight
                    }
                    frames()
                    val normal = animatedHeight
                    assertEquals(runtimeHeight, normal)
                    spacing.floatValue = 0.25f
                    val shrinking = frames()
                    val compact = animatedHeight
                    assertTrue(compact < normal, "$display story=$story must become denser")
                    assertEquals(runtimeHeight, compact, "Preview and runtime spacing must agree")
                    assertTrue(shrinking.any { it in (compact + 1) until normal }, "Must animate toward compact spacing")
                    spacing.floatValue = 1f
                    val growing = frames()
                    assertEquals(normal, animatedHeight, "Default restores original geometry")
                    assertTrue(growing.any { it in (compact + 1) until normal }, "Must animate back to default")
                } finally { scene.close() }
            }
        } finally { bootstrap.close() }
    }
}
