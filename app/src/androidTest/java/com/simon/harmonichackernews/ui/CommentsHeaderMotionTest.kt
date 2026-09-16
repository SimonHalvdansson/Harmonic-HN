package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.comments.*
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Header size animations must move the list directly, without a trailing row-placement spring. */
@RunWith(AndroidJUnit4::class)
class CommentsHeaderMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rowsStayBelowPreviewWhileItExpandsAndShrinksAtRestoredPosition() {
        val height = mutableStateOf(180.dp)
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(loaded = true))
        val comments = (1..8).map { id ->
            PortableCommentItem(
                CommentSnapshot(id, author = "reader", text = "A comment beneath the preview."),
                CommentPresentationSnapshot(expanded = true),
            )
        }
        val controller = CommentsComposeController.create(
            shouldSmoothScroll = { true }, story = story, initialThreadCached = true,
            showWebsite = false, initialScrollRestorationPending = true, accountUser = null,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            },
            listener = NoOpListener(),
        )
        controller.updateContent(CommentsScreenState(
            story = story, comments = comments, commentsLoaded = true, initialThreadCached = true,
            visibleComments = comments.mapIndexed { index, item -> PortableVisibleComment(index, item, 0) },
            displaySettings = settings,
        ))
        controller.restoreReadingPosition(0, -200)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("dark", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                    HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                        CommentsScreen(
                            controller = controller, listModifier = Modifier, reserveUpButtonInset = false,
                            pullToRefreshEnabled = false, showNavigationControls = false,
                            animateComments = true, showScrollbar = false, smoothScroll = true,
                            userTags = emptyMap(), onOpenLink = {}, searchDialog = {}, actionOverlay = {},
                            headerContent = {
                                val animatedHeight by animateDpAsState(height.value, tween(240))
                                Box(Modifier.fillMaxWidth().height(animatedHeight).testTag("preview-header"))
                            },
                        )
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            for (target in listOf(300.dp, 180.dp)) {
                compose.runOnUiThread { height.value = target }
                repeat(20) {
                    compose.mainClock.advanceTimeByFrame()
                    val headerBottom = compose.onNodeWithTag("preview-header").fetchSemanticsNode().boundsInRoot.bottom
                    val rowTop = compose.onAllNodesWithTag("comment-row")[0].fetchSemanticsNode().boundsInRoot.top
                    assertEquals("Rows must follow the preview on every frame", headerBottom, rowTop, 1f)
                }
            }
        } finally {
            scene.close()
        }
    }

    private val settings = CommentDisplaySettings(
        collapseParent = false, showThumbnail = false, showHeaderPreviewImage = false,
        tintHeader = false, showUpButton = false, paletteTintMode = "default",
        preferredTextSize = 14f, commentDepthIndicatorMode = "threads", showNavigationBar = false,
        font = "default", showInvert = false, showTopLevelDepthIndicator = false, theme = null,
        isTablet = false, faviconProvider = "default", swapLongPressTap = false,
        displayStyle = DisplayStyle.RAISED, cardBorder = false, showDividers = false,
        highlightCommentMeta = false, collectReferenceLinks = false, hasAccountDetails = false,
        canProvideSummary = false, showAdditionalSummaryInfo = false, enableSummaryBoldFormatting = true,
    )

    private class NoOpListener : CommentsComposeController.Listener {
        override fun onToggleComment(comment: PortableCommentItem, position: Int) = Unit
        override fun onCommentAction(comment: PortableCommentItem, action: CommentMenuAction) = Unit
        override fun onCommentActionOverlayVisibilityChanged(showing: Boolean) = Unit
        override fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean) = Unit
        override fun onHeaderClick() = Unit
        override fun onHeaderPreviewImageResult(imageUrl: String, success: Boolean) = Unit
        override fun onHeaderPreviewTintExtracted(sourceUrl: String, baseColorArgb: Int, paletteConfigKey: String, tintColorArgb: Int): Int? = null
        override fun onHeaderAction(action: CommentsHeaderAction) = Unit
        override fun onShareAction(action: CommentsShareAction) = Unit
        override fun onMoreAction(action: CommentsMoreAction) = Unit
        override fun onSearchResultSelected(comment: PortableCommentItem) = Unit
        override fun onSearchQueryChanged(query: String) = Unit
        override fun onSortComments(sortType: String) = Unit
        override fun onSheetAction(action: CommentsSheetAction) = Unit
        override fun onCollapseSheetForWebsite() = Unit
        override fun onSheetProgressChanged(expandedFraction: Float) = Unit
        override fun onSheetSettled(expanded: Boolean) = Unit
        override fun onHeaderColorChanged(color: Int) = Unit
        override fun onHeaderCoverageChanged(coverage: Float) = Unit
        override fun onPollOption(optionId: Int) = Unit
    }
}
