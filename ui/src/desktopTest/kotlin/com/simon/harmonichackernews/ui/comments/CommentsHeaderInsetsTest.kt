package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.settings.HeaderPreviewActions
import kotlinx.coroutines.flow.emptyFlow
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsHeaderInsetsTest {
    @Test
    fun imageUsesPaneSafeAreaFromFirstFrameWithoutWaitingForController() = SwingUtilities.invokeAndWait {
        // The right pane consumes the opposite screen edge; only its right safe area remains.
        val safeArea = WindowInsets(left = 24, right = 84)
        val actions = HeaderPreviewActions()
        val controller = CommentsScreenController.create(
            shouldSmoothScroll = { false },
            story = StoryListItemSnapshot(
                story = StorySnapshot(id = 42, title = "A story with an image", url = "https://example.com"),
                presentation = StoryPresentationSnapshot(loaded = true, isLink = true),
            ),
            initialThreadCached = true,
            showWebsite = false,
            accountUser = null,
            savedItemState = actions,
            listener = actions,
        )
        val settings = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, false, false,
        )
        var bounds = Rect.Zero
        val scene = ImageComposeScene(520, 900, Density(1f)) {
            MaterialTheme {
                LookaheadScope {
                    Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets(left = 24))) {
                        CommentsHeader(
                            controller = controller,
                            settings = settings,
                            contentVersion = 0,
                            storyPosterTag = "",
                            tintBaseColor = 0,
                            initialTint = null,
                            headerTopPadding = 24.dp,
                            bookmarksEnabled = true,
                            lastRefreshedText = null,
                            textStyle = TextStyle.Default,
                            previewPlatform = CommentsPreviewPlatform(TextStyle.Default, {}, {}, {}, { it }),
                            includeStatusBarSpacer = false,
                            horizontalContentInsets = safeArea,
                            headerPreviewImageDisplayed = true,
                            headerPreviewImage = { modifier, _, _ ->
                                Box(modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
                            },
                        )
                    }
                }
            }
        }
        try {
            repeat(24) { frame ->
                if (frame == 2) {
                    // The host's effect catches up. It must not resize or double-inset the image.
                    controller.updateContent(controller.screenState.copy(contentInsetRightPx = 84))
                }
                scene.render(frame * 16_666_667L).close()
                assertEquals(16f, bounds.left, "Image left edge at frame $frame")
                assertEquals(420f, bounds.right, "Image right edge at frame $frame")
            }
        } finally { scene.close() }
    }
}
