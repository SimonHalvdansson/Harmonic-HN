package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.Density
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Header size animations must move the list directly, without a trailing row-placement spring. */
@RunWith(AndroidJUnit4::class)
class CommentsHeaderMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshMovesCommentsAndFadesIndicatorAtPhoneWidth() = assertRefreshMotion(400)

    @Test
    fun refreshMovesCommentsAndFadesIndicatorAtTabletWidth() = assertRefreshMotion(840)

    private fun assertRefreshMotion(widthDp: Int) {
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(loaded = true))
        val comments = (1..8).map { id ->
            PortableCommentItem(
                CommentSnapshot(id, author = "reader", text = "Short", expandedAnchorText = "Short"),
                CommentPresentationSnapshot(expanded = true),
            )
        }
        val controller = CommentsComposeController.create(
            shouldSmoothScroll = { true }, story = story, initialThreadCached = true,
            showWebsite = false, initialScrollRestorationPending = false, accountUser = null,
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
            displaySettings = settings.copy(isTablet = widthDp >= 600, displayStyle = DisplayStyle.FLAT),
        ))
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        try {
            compose.setContent {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Exercise both logical window widths on the same emulator without changing
                    // persistent display settings. HeaderStatus is shared by both hosts.
                    val density = Density(constraints.maxWidth.toFloat() / widthDp)
                    val palette = HarmonicThemeCatalog.resolve("light", false)
                    CompositionLocalProvider(
                        LocalHarmonicUiDependencies provides dependencies,
                        LocalDensity provides density,
                    ) {
                        HarmonicTheme(
                            palette.colors.copy(settingsPageBackground = Color.White),
                            palette.colorScheme, palette.dark,
                        ) {
                            Box(Modifier.fillMaxSize().background(Color.White).testTag("refresh-root")) {
                                CommentsScreen(
                                    controller = controller, listModifier = Modifier, reserveUpButtonInset = false,
                                    pullToRefreshEnabled = false, showNavigationControls = false,
                                    animateComments = true, showScrollbar = false, smoothScroll = true,
                                    userTags = emptyMap(), onOpenLink = {}, searchDialog = {}, actionOverlay = {},
                                    headerContent = {
                                        Column(Modifier.fillMaxWidth().background(Color.White).testTag("refresh-header")) {
                                            Box(Modifier.height(80.dp))
                                            HeaderStatus(controller, lastRefreshedText = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            fun rowTop() = compose.onAllNodesWithTag("comment-row")[0].fetchSemanticsNode().boundsInRoot.top
            fun indicatorContrast(): Float {
                val root = compose.onNodeWithTag("refresh-root")
                val rootBounds = root.fetchSemanticsNode().boundsInRoot
                val headerBounds = compose.onNodeWithTag("refresh-header").fetchSemanticsNode().boundsInRoot
                val pixels = root.captureToImage().toPixelMap()
                val scale = pixels.width.toFloat() / widthDp
                val centerX = pixels.width / 2
                val centerY = (headerBounds.top - rootBounds.top + (80 + 16 + 21) * scale).toInt()
                val radius = (3 * scale).toInt().coerceAtLeast(1)
                var contrast = 0f
                // The filled center of the morphing indicator remains present in every shape.
                // Short, left-aligned comment text leaves this sample region white underneath it.
                for (y in centerY - radius..centerY + radius) {
                    for (x in centerX - radius..centerX + radius) {
                        val pixel = pixels[x, y]
                        contrast = maxOf(contrast, 1f - minOf(pixel.red, pixel.green, pixel.blue))
                    }
                }
                return contrast
            }
            val before = rowTop()
            compose.runOnUiThread { controller.beginHeaderRefresh() }
            val tops = mutableListOf<Float>()
            val contrasts = mutableListOf<Float>()
            repeat(24) { frame ->
                compose.mainClock.advanceTimeByFrame()
                val top = rowTop()
                tops += top
                val headerBottom = compose.onNodeWithTag("refresh-header").fetchSemanticsNode().boundsInRoot.bottom
                assertEquals("Refresh must move comments with the header at ${widthDp}dp", headerBottom, top, 1f)
                if (frame in listOf(2, 9, 21)) contrasts += indicatorContrast()
            }
            val after = tops.last()
            assertTrue("Refresh must reserve indicator space", after > before + 20f)
            assertTrue("Refresh must move comments through intermediate positions", tops.any { it > before + 1f && it < after - 1f })
            val (early, middle, settled) = contrasts
            assertTrue("Settled indicator must be visible", settled > 0.2f)
            assertTrue("Indicator must begin transparent", early < settled * 0.1f)
            assertTrue("Indicator must fade through partial opacity", middle > settled * 0.1f && middle < settled * 0.95f)

            compose.runOnUiThread { controller.finishHeaderRefresh() }
            val exiting = (1..24).map {
                compose.mainClock.advanceTimeByFrame()
                rowTop()
            }
            assertEquals("Refresh must release its space", before, exiting.last(), 1f)
            assertTrue("Refresh removal must also move comments smoothly", exiting.any { it > before + 1f && it < after - 1f })
        } finally {
            scene.close()
        }
    }

    @Test
    fun rowsStayBelowPreviewWhileItExpandsAndShrinksAtRestoredPosition() {
        val height = mutableStateOf(180.dp)
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(loaded = true))
        val comments = (1..8).map { id ->
            PortableCommentItem(
                CommentSnapshot(
                    id, author = "reader", text = "Comment $id beneath the preview.",
                    expandedAnchorText = "Comment $id beneath the preview.",
                ),
                CommentPresentationSnapshot(expanded = true, depth = if (id == 2) 1 else 0),
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
        val expandedState = CommentsScreenState(
            story = story, comments = comments, commentsLoaded = true, initialThreadCached = true,
            visibleComments = comments.mapIndexed { index, item -> PortableVisibleComment(index, item, 0) },
            displaySettings = settings,
        )
        controller.updateContent(expandedState)
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

            val collapsedComments = comments.map { item ->
                if (item.id == 1) item.copy(presentation = item.presentation.copy(expanded = false)) else item
            }
            val collapsedState = expandedState.copy(
                comments = collapsedComments,
                visibleComments = collapsedComments.mapIndexedNotNull { index, item ->
                    if (item.id == 2) null else PortableVisibleComment(index, item, if (item.id == 1) 1 else 0)
                },
            )
            fun siblingGap(): Float {
                val parentBottom = compose.onNodeWithText("Comment 1 beneath the preview.")
                    .fetchSemanticsNode().boundsInRoot.bottom
                val siblingTop = compose.onNodeWithText("Comment 3 beneath the preview.")
                    .fetchSemanticsNode().boundsInRoot.top
                return siblingTop - parentBottom
            }
            for (state in listOf(collapsedState, expandedState)) {
                val before = siblingGap()
                compose.runOnUiThread { controller.updateContent(state) }
                val frames = (1..24).map {
                    compose.mainClock.advanceTimeByFrame()
                    siblingGap()
                }
                val after = frames.last()
                assertTrue("Changing subtree visibility must move its following sibling", kotlin.math.abs(after - before) > 20f)
                assertTrue(
                    "The sibling must pass through intermediate positions instead of jumping",
                    frames.any { it > minOf(before, after) + 1f && it < maxOf(before, after) - 1f },
                )
            }
            // Structural motion must not leave placement animation enabled for later header changes.
            compose.runOnUiThread { height.value = 300.dp }
            repeat(20) {
                compose.mainClock.advanceTimeByFrame()
                val headerBottom = compose.onNodeWithTag("preview-header").fetchSemanticsNode().boundsInRoot.bottom
                val rowTop = compose.onAllNodesWithTag("comment-row")[0].fetchSemanticsNode().boundsInRoot.top
                assertEquals("Rows must still follow the header after a subtree animation", headerBottom, rowTop, 1f)
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
        displayStyle = DisplayStyle.RAISED, outline = false, showDividers = false,
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
