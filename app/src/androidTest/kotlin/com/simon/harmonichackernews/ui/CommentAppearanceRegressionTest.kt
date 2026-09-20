package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.comments.*
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.content.UserAvatar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CommentAppearanceRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun comment(id: Int, depth: Int) = PortableCommentItem(
        CommentSnapshot(id, author = "reader$id", text = "Body $id", expandedAnchorText = "Body $id"),
        CommentPresentationSnapshot(expanded = true, depth = depth),
    )

    @Test
    fun continuousLinesCrossChildrenButStopAtNewRootAndDividersAreCentered() {
        val style = mutableStateOf(CommentItemStyle(
            displayStyle = DisplayStyle.FLAT, textSize = 14f, collectLinks = false,
            emphasizeMeta = false, depthIndicatorMode = "colors", showDivider = true,
            preferredFont = "default",
            animateChanges = false, continuousDepthIndicators = true, roundedDepthIndicators = true,
        ))
        val rows = listOf(comment(1, 0), comment(2, 1), comment(3, 2), comment(4, 0))
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                    HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                        Column(Modifier.fillMaxWidth().background(palette.colors.settingsPageBackground).testTag("thread")) {
                            rows.forEachIndexed { index, row ->
                                CommentItem(
                                    comment = row, style = style.value, storyAuthor = null, accountUser = null,
                                    userTag = null, hiddenReplyCount = 0, collapseParent = false,
                                    showTopLevelIndicator = true, nextCommentDepth = rows.getOrNull(index + 1)?.depth,
                                    modifier = Modifier.testTag("row-$index"),
                                    onToggleExpanded = {}, onShowActions = {}, onLinkLongClick = { _, _, _ -> },
                                    onReferenceLongClick = { _, _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
            for (display in DisplayStyle.entries) {
                for (thickness in CommentIndicatorThickness.entries) {
                    compose.runOnIdle { style.value = style.value.copy(displayStyle = display, indicatorThickness = thickness) }
                    compose.waitForIdle()
                    val pixels = compose.onNodeWithTag("thread").captureToImage().toPixelMap()
                    val density = compose.activity.resources.displayMetrics.density
                    val rootTop = compose.onNodeWithTag("thread").fetchSemanticsNode().boundsInRoot.top
                    val child = compose.onNodeWithTag("row-2").fetchSemanticsNode().boundsInRoot
                    val nextRoot = compose.onNodeWithTag("row-3").fetchSemanticsNode().boundsInRoot
                    val x = ((16f + thickness.widthDp / 2f) * density).toInt()
                    val childY = (child.center.y - rootTop).toInt()
                    val backgroundX = (8 * density).toInt()
                    assertNotEquals("Ancestor line crosses its grandchild ($display/$thickness)", pixels[backgroundX, childY], pixels[x, childY])
                    val railStart = (16 * density).toInt()
                    val renderedWidth = (railStart until (28 * density).toInt()).count {
                        pixels[it, childY] != pixels[backgroundX, childY]
                    }
                    assertEquals("Selected thickness is rendered ($display/$thickness)", thickness.widthDp * density, renderedWidth.toFloat(), 1f)
                    val gapY = (nextRoot.top - rootTop + density).toInt()
                    assertEquals("New root starts a separate line ($display/$thickness)", pixels[backgroundX, gapY], pixels[x, gapY])
                    // Rows use the same vertical gutter before and after each surface. Test with
                    // real measured clickable surface bounds, including the Filled/Raised padding.
                    val surfaces = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                    val before = surfaces.first { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text) && it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any { text -> text.text == "Body 1" } }.boundsInRoot
                    val after = surfaces.first { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text) && it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].any { text -> text.text == "Body 2" } }.boundsInRoot
                    val boundary = compose.onNodeWithTag("row-0").fetchSemanticsNode().boundsInRoot.bottom
                    assertEquals("Divider lies halfway between surfaces ($display)", (before.bottom + after.top) / 2f, boundary, 1f)
                    val dividerY = (boundary - rootTop).toInt().coerceAtMost(pixels.height - 1)
                    assertNotEquals("Divider is actually drawn", pixels[backgroundX, dividerY], pixels[pixels.width / 2, dividerY])
                }
            }
        } finally { scene.close() }
    }

    @Test
    fun generatedAvatarsAreStableDistinctAndNoneOccupiesNoSpace() {
        val mode = mutableStateOf(UserAvatarMode.GENERATED)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Row {
                    UserAvatar("willow", mode.value, Modifier.size(40.dp).testTag("first"))
                    UserAvatar("willow", mode.value, Modifier.size(40.dp).testTag("same"))
                    UserAvatar("compass", mode.value, Modifier.size(40.dp).testTag("different"))
                }
            }
        }
        fun difference(a: String, b: String): Float {
            val first = compose.onNodeWithTag(a).captureToImage().toPixelMap()
            val second = compose.onNodeWithTag(b).captureToImage().toPixelMap()
            var difference = 0f
            for (y in 0 until first.height) for (x in 0 until first.width) {
                difference += abs(first[x, y].red - second[x, y].red) +
                    abs(first[x, y].green - second[x, y].green) + abs(first[x, y].blue - second[x, y].blue)
            }
            return difference / (first.height * first.width * 3)
        }
        // GPU antialiasing/dithering can differ slightly with the on-screen pixel origin.
        val same = difference("first", "same")
        val different = difference("first", "different")
        assertTrue("Same username pixels differ by $same", same < 0.015f)
        assertTrue("Different username pixels differ by $different", different > 0.04f)
        compose.runOnIdle { mode.value = UserAvatarMode.GENERIC }
        val generic = difference("first", "different")
        assertTrue("Generic icons differ by $generic", generic < 0.015f)
        compose.runOnIdle { mode.value = UserAvatarMode.NONE }
        compose.onNodeWithTag("first").assertDoesNotExist()
        compose.onNodeWithTag("different").assertDoesNotExist()
    }

    @Test
    fun dialogSurvivesGeometryChangesBackgroundingAndCanBeDismissed() {
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(loaded = true))
        val controller = CommentsComposeController.create(
            shouldSmoothScroll = { true }, story = story, initialThreadCached = true,
            showWebsite = false, initialScrollRestorationPending = false, accountUser = null,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            }, listener = NoOpListener(),
        )
        val inset = mutableStateOf(0.dp)
        var scrim = 0f
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.fillMaxSize().padding(horizontal = inset.value)) {
                    CommentActionOverlay(controller, settings, false, false, TextStyle.Default,
                        onOpenLink = {}, onScrimAlphaChanged = { scrim = it })
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { controller.restoreCommentActions(comment(1, 0)) }
        repeat(12) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.runOnUiThread { inset.value = (frame % 3 * 8).dp }
        }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { abs(scrim - 0.32f) < 0.001f }
        compose.onNodeWithText("Body 1").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Body 1").assertIsDisplayed()
        compose.runOnIdle { controller.requestDismissCommentActions() }
        compose.waitUntil(5_000) { !controller.isCommentActionOverlayShowing() }
        compose.onNodeWithText("Body 1").assertDoesNotExist()
    }
    private val settings = CommentDisplaySettings(
        collapseParent = false, showThumbnail = false, showHeaderPreviewImage = false,
        tintHeader = false, showUpButton = false, paletteTintMode = "default",
        preferredTextSize = 14f, commentDepthIndicatorMode = "threads", showNavigationBar = false,
        font = "default", showInvert = false, showTopLevelDepthIndicator = false, theme = null,
        isTablet = false, faviconProvider = "default", swapLongPressTap = false,
        displayStyle = DisplayStyle.RAISED, showDividers = false,
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
