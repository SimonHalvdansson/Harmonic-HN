@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionHandleInfoKey
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.ui.common.ScrollableTextDecorations
import com.simon.harmonichackernews.ui.editor.EditorScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplyPreviewInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun originalCommentSupportsSelectionBelowTheCompactReplyToolbar() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                EditorScreen(
                    type = EditorType.COMMENT_REPLY,
                    parentText = "Selectable original comment.<p>More context.</p>".repeat(12),
                    postTitle = "A discussion",
                    user = "author",
                    submitting = false,
                    onClose = {},
                    onSubmit = { error("The test must never submit a reply") },
                )
            }
        }
        val toolbar = compose.onNodeWithTag("compose_editor_top_app_bar").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("compose_editor_replying_scrollview").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(64f * density, toolbar.height, 1f)
        assertEquals("No extra gap below the app bar", toolbar.bottom, preview.top, 1f)
        compose.onNodeWithTag("compose_editor_replying_scrollview", useUnmergedTree = true)
            .performTouchInput { longClick(Offset(32f * density, 12f * density)) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SelectionHandleInfoKey), useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Test fun replyFadeEdgesAreOpaqueEvenNearEitherScrollBoundary() {
        val scroll = ScrollState(0)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Box(Modifier.size(160.dp, 100.dp).background(Color.White).testTag("fade")) {
                    Box(Modifier.verticalScroll(scroll)) {
                        Box(Modifier.fillMaxWidth().height(400.dp).background(Color.Black))
                    }
                    ScrollableTextDecorations(scroll, Color.White, Modifier.matchParentSize())
                }
            }
        }
        compose.waitForIdle()
        for (position in listOf(3, scroll.maxValue / 2, scroll.maxValue - 3)) {
            compose.runOnIdle { runBlocking { scroll.scrollTo(position) } }
            val pixels = compose.onNodeWithTag("fade").captureToImage().toPixelMap()
            val x = pixels.width / 2
            assertTrue("Top edge must fully cover the text at $position", pixels[x, 0].red > 0.99f)
            assertTrue("Bottom edge must fully cover the text at $position", pixels[x, pixels.height - 1].red > 0.99f)
            assertTrue("The middle must remain readable", pixels[x, pixels.height / 2].red < 0.01f)
        }
    }
}
