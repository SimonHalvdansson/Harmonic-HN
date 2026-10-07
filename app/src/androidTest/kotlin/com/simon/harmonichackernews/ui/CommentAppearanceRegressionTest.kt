@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.comments.*
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.sharedHazeDialogBackground
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.SettingsCommentPreviewModel
import com.simon.harmonichackernews.ui.content.UserAvatar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import java.io.File

@RunWith(AndroidJUnit4::class)
class CommentAppearanceRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun headerMetadataWrapsWholeAuthorAndTimeGroups() {
        val width = mutableStateOf(260.dp)
        val story = StoryListItemSnapshot(
            StorySnapshot(42, author = "fifaase", score = 997, descendantCount = 479,
                createdAtEpochSeconds = (System.currentTimeMillis() / 1000 - 86400).toInt()),
            StoryPresentationSnapshot(loaded = true, isLink = true),
        )
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Box(Modifier.width(width.value)) {
                    CommentsHeaderMetadata(story, settings, textStyle = TextStyle.Default)
                }
            }
        }
        val author = compose.onNodeWithText("fifaase")
        val originalHeight = author.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { width.value = 230.dp }
        val wrappedAuthor = author.fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithText("997").fetchSemanticsNode().boundsInRoot
        assertTrue("Author moves to the next line", wrappedAuthor.top >= score.bottom)
        assertEquals("Author stays on a single line", originalHeight, wrappedAuthor.height, 1f)
        assertEquals("Author keeps its leading icon and gap", score.left, wrappedAuthor.left, 1f)
        compose.runOnIdle { width.value = 165.dp }
        val time = compose.onNodeWithText(story.timeFormatted).fetchSemanticsNode().boundsInRoot
        val narrowScore = compose.onNodeWithText("997").fetchSemanticsNode().boundsInRoot
        assertTrue("Time also moves as a group when needed", time.top >= narrowScore.bottom)
        assertEquals(narrowScore.left, time.left, 1f)
    }

    @Test
    fun upButtonRemainsAboveOverlappingModalContent() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            CompositionLocalProvider(LocalHazePreferences provides SurfaceEffectPreferences(mode = SurfaceEffectMode.Solid)) {
                HarmonicTheme(palette.colorScheme.copy(surfaceContainerHigh = Color.Blue, onSurfaceVariant = Color.Blue), palette.dark) {
                    Box(Modifier.size(120.dp).testTag("layers")) {
                        CommentsUpButton({}, Modifier.padding(16.dp).zIndex(101f), modalScrimAlpha = 0.32f)
                        Box(Modifier.fillMaxSize().zIndex(100f).background(Color.Magenta))
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("layers").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val center = button.center - root.topLeft
        val pixel = compose.onNodeWithTag("layers").captureToImage().toPixelMap()[center.x.toInt(), center.y.toInt()]
        assertTrue("The modal must stay behind the dimmed blue button: $pixel", pixel.red < 0.05f && pixel.blue > 0.5f)
    }

    @Test
    fun blurredCommentDialogDoesNotExposeSharpRailsNearItsEdge() {
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(loaded = true))
        val controller = CommentsScreenController.create(
            shouldSmoothScroll = { true }, story = story, initialThreadCached = true,
            showWebsite = false, initialScrollRestorationPending = false, accountUser = null,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            }, listener = NoOpListener(),
        )
        controller.updateContent(CommentsScreenState(
            story = story, commentsLoaded = true, initialThreadCached = true, displaySettings = settings,
        ))
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(
                    LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene),
                    LocalHazePreferences provides SurfaceEffectPreferences(mode = SurfaceEffectMode.Frosted),
                ) {
                    HarmonicTheme(palette.colorScheme.copy(surface = Color.White), palette.dark) {
                        HazeHost {
                            Box(Modifier.size(260.dp).testTag("blurred-rails")) {
                                CommentsScreen(
                                    controller, Modifier, false, pullToRefreshEnabled = false,
                                    showNavigationControls = false, animateComments = false, showScrollbar = false,
                                    smoothScroll = false, userTags = emptyMap(), onOpenLink = {},
                                    headerContent = {
                                        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                                            for (x in listOf(20.dp, 110.dp)) {
                                                drawRect(Color.Black, Offset(x.toPx(), 0f), Size(4.dp.toPx(), size.height))
                                            }
                                        }
                                    }, searchDialog = {}, actionOverlay = {},
                                )
                                Box(Modifier.padding(12.dp).size(236.dp)
                                    .sharedHazeDialogBackground(Color.White, RoundedCornerShape(28.dp)))
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("blurred-rails").captureToImage().toPixelMap()
            val density = compose.activity.resources.displayMetrics.density
            for (railX in listOf(20, 110)) {
                val y = pixels.height / 2
                val edgeJump = ((railX - 2) * density).toInt().rangeTo(((railX + 6) * density).toInt())
                    .maxOf { x -> abs(pixels[x + 1, y].red - pixels[x, y].red) }
                assertTrue("Rail at ${railX}dp must be blurred across the dialog, edge jump=$edgeJump", edgeJump < 0.04f)
            }
        } finally { scene.close() }
    }

    @Test
    fun previewNewCommentDotFadesQuicklyInBothDirections() {
        val showDot = mutableStateOf(true)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                HarmonicTheme(palette.colorScheme.copy(primary = Color.Red), palette.dark) {
                    CommentRow(
                        model = SettingsCommentPreviewModel,
                        style = CommentRowStyle(
                            displayStyle = DisplayStyle.FLAT, textSize = 14f, collectLinks = false,
                            emphasizeMeta = false, depthIndicatorMode = "none", showDivider = false,
                            preferredFont = "default", animateChanges = true, markNewComments = showDot.value,
                        ),
                        modifier = Modifier.background(Color.White),
                    )
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val center = compose.onNodeWithContentDescription("New comment", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - root.topLeft
        fun dotOpacity(): Float {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            return 1f - pixels[center.x.toInt(), center.y.toInt()].green
        }
        compose.mainClock.autoAdvance = false
        try {
            assertTrue(dotOpacity() > 0.95f)
            compose.runOnIdle { showDot.value = false }
            compose.mainClock.advanceTimeBy(64)
            assertTrue("Dot must fade out through partial opacity", dotOpacity() in 0.05f..0.95f)
            compose.mainClock.advanceTimeBy(120)
            assertTrue(dotOpacity() < 0.05f)
            compose.onNodeWithContentDescription("New comment").assertDoesNotExist()
            compose.runOnIdle { showDot.value = true }
            compose.mainClock.advanceTimeBy(64)
            assertTrue("Dot must fade in through partial opacity", dotOpacity() in 0.05f..0.95f)
            compose.mainClock.advanceTimeBy(120)
            assertTrue(dotOpacity() > 0.95f)
        } finally {
            compose.mainClock.autoAdvance = true
            scene.close()
        }
    }

    @Test
    fun newCommentDotMovesSmoothlyToTheLeftOfCollapsedReplyCount() = assertReplyCountMotion(isNew = true)

    @Test
    fun olderCommentReplyCountFadesAtFullWidthWithoutClipping() = assertReplyCountMotion(isNew = false)

    private fun assertReplyCountMotion(isNew: Boolean) {
        val store = CommentThreadStore()
        val story = Story().apply { id = 100 }
        store.reset(story)
        if (isNew) store.replaceParsedComments(story, emptyList(), "Default", false)
        store.replaceParsedComments(story, listOf(
            Comment().apply { id = 1; by = "reader"; text = "New root"; depth = 0; parent = -1; expanded = true },
            Comment().apply { id = 2; by = "reply"; text = "New reply"; depth = 1; parent = 1 },
        ), "Default", false)
        val item = mutableStateOf(store.state.value.visibleComments.first().comment)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                    HarmonicTheme(palette.colorScheme.copy(tertiary = Color(0xFF0066CC)), palette.dark) {
                        CommentRow(
                            modifier = Modifier.background(Color.White),
                            comment = item.value,
                            style = CommentRowStyle(
                                displayStyle = DisplayStyle.FLAT, textSize = 14f, collectLinks = false,
                                emphasizeMeta = false, depthIndicatorMode = "colors", showDivider = true,
                                preferredFont = "default", animateChanges = true,
                            ),
                            storyAuthor = null, accountUser = null, userTag = null,
                            subtreeReplyCount = 1, collapseParent = false, showTopLevelIndicator = true,
                            onToggleExpanded = {}, onShowActions = {}, onLinkLongClick = { _, _, _ -> },
                            onReferenceLongClick = { _, _, _ -> },
                        )
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            fun dotX() = compose.onNodeWithContentDescription("New comment", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.center.x
            val expandedX = if (isNew) dotX() else 0f
            compose.runOnIdle {
                store.toggleExpanded(1)
                item.value = store.state.value.visibleComments.first().comment
            }
            compose.mainClock.advanceTimeBy(64)
            val movingX = if (isNew) dotX() else 0f
            val fadingCount = compose.onNodeWithText("+1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // Bounds alone miss offscreen alpha layers clipping raised drawing. Inspect the
            // actual pill pixels while it is still fading, including its topmost straight edge.
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val density = compose.activity.resources.displayMetrics.density
            val pillCenterX = fadingCount.center.x + if (isNew) 3 * density else 0f
            val top = pixels[(pillCenterX - rootBounds.left).toInt(), (fadingCount.top - rootBounds.top + 1).toInt()]
            val middle = pixels[(fadingCount.right - rootBounds.left - 2 * density).toInt(), (fadingCount.center.y - rootBounds.top).toInt()]
            fun contrastFromWhite(color: Color) = abs(1f - color.red) + abs(1f - color.green) + abs(1f - color.blue)
            assertTrue("Pill top must remain painted during fade: top=$top middle=$middle",
                contrastFromWhite(top) > contrastFromWhite(middle) * 0.7f)
            compose.mainClock.advanceTimeBy(800)
            val collapsedX = if (isNew) dotX() else 0f
            val count = compose.onNodeWithText("+1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val author = compose.onNodeWithText("reader", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(author.center.y - 2 * compose.activity.resources.displayMetrics.density, count.center.y, 1f)
            if (!isNew) {
                assertEquals("Fading count retains full width", count.width, fadingCount.width, 1f)
                assertEquals("Fading count retains its position", count.left, fadingCount.left, 1f)
                compose.onNodeWithContentDescription("New comment").assertDoesNotExist()
                return
            }
            val dot = compose.onNodeWithContentDescription("New comment", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("Dot and count share the raised center", count.center.y, dot.center.y, 1f)
            assertTrue("Dot moves gradually while the count appears", movingX < expandedX && movingX > collapsedX)
            assertTrue("Reply count is to the right of the dot", count.left > collapsedX)
            compose.runOnIdle {
                store.toggleExpanded(1)
                item.value = store.state.value.visibleComments.first().comment
            }
            compose.mainClock.advanceTimeBy(64)
            assertTrue("Dot animates back when expanded", dotX() > collapsedX && dotX() < expandedX)
            compose.mainClock.advanceTimeBy(800)
            assertEquals(expandedX, dotX(), 1f)
        } finally {
            compose.mainClock.autoAdvance = true
            scene.close()
        }
    }

    private fun comment(id: Int, depth: Int) = PortableCommentItem(
        CommentSnapshot(id, author = "reader$id", text = "Body $id", expandedAnchorText = "Body $id"),
        CommentPresentationSnapshot(expanded = true, depth = depth),
    )

    @Test
    fun failedExtendedReferenceRendersTheUrlOnlyOnce() {
        val url = "https://unresolvable.invalid/reference"
        val html = "<p>[1] <a href=\"$url\">$url</a></p>"
        val row = PortableCommentItem(
            CommentSnapshot(1, author = "reader", text = html, expandedAnchorText = html),
            CommentPresentationSnapshot(expanded = true),
        )
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        CommentRow(
                            comment = row,
                            style = CommentRowStyle(
                                displayStyle = DisplayStyle.FLAT, textSize = 14f, collectLinks = true,
                                emphasizeMeta = false, depthIndicatorMode = "colors", showDivider = true,
                                preferredFont = "default", expandedReferenceLinks = true,
                            ),
                            storyAuthor = null, accountUser = null, userTag = null,
                            subtreeReplyCount = 0, collapseParent = false, showTopLevelIndicator = true,
                            onToggleExpanded = {}, onShowActions = {}, onLinkLongClick = { _, _, _ -> },
                            onReferenceLongClick = { _, _, _ -> },
                        )
                    }
                }
            }
            compose.onAllNodesWithText(url, useUnmergedTree = true).assertCountEquals(1)
        } finally { scene.close() }
    }

    @Test
    fun continuousLinesCrossChildrenButStopAtNewRootAndDividersAreCentered() {
        val style = mutableStateOf(CommentRowStyle(
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
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        Column(Modifier.fillMaxWidth().background(palette.colorScheme.pageBackground).testTag("thread")) {
                            rows.forEachIndexed { index, row ->
                                CommentRow(
                                    comment = row, style = style.value, storyAuthor = null, accountUser = null,
                                    userTag = null, subtreeReplyCount = 0, collapseParent = false,
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
    fun avatarArtworkAndFrameAnimateThroughIntermediatePixels() {
        val author = mutableStateOf("willow")
        val options = mutableStateOf(UserAvatarOptions(setOf(UserAvatarStyle.ROBOT)))
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                UserAvatar(author.value, Modifier.size(64.dp).testTag("animated-avatar"), options.value)
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            fun difference(a: androidx.compose.ui.graphics.ImageBitmap, b: androidx.compose.ui.graphics.ImageBitmap): Float {
                val first = a.toPixelMap()
                val second = b.toPixelMap()
                var sum = 0f
                for (y in 0 until first.height) for (x in 0 until first.width) {
                    sum += abs(first[x, y].red - second[x, y].red) +
                        abs(first[x, y].green - second[x, y].green) +
                        abs(first[x, y].blue - second[x, y].blue) +
                        abs(first[x, y].alpha - second[x, y].alpha)
                }
                return sum / (first.width * first.height * 4)
            }
            fun assertAnimated(label: String, change: () -> Unit) {
                val before = compose.onNodeWithTag("animated-avatar").captureToImage()
                compose.runOnIdle(change)
                compose.mainClock.advanceTimeBy(120)
                val during = compose.onNodeWithTag("animated-avatar").captureToImage()
                compose.mainClock.advanceTimeBy(350)
                val after = compose.onNodeWithTag("animated-avatar").captureToImage()
                assertTrue("$label must leave the starting image", difference(before, during) > .001f)
                assertTrue("$label must have an intermediate image", difference(during, after) > .001f)
            }
            assertAnimated("Identity") { author.value = "compass" }
            assertAnimated("Expressive to generic") { options.value = options.value.copy(generic = true) }
            assertAnimated("Generic to expressive") { options.value = options.value.copy(generic = false) }
            assertAnimated("Frame corners") { options.value = options.value.copy(shape = UserAvatarShape.SQUARE) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun avatarStylesAreStableDistinctAndDisabledOccupiesNoSpace() {
        val enabled = mutableStateOf(true)
        val options = mutableStateOf(UserAvatarOptions())
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                if (enabled.value) Row {
                    UserAvatar("willow", Modifier.size(40.dp).testTag("first"), options.value)
                    UserAvatar("willow", Modifier.size(40.dp).testTag("same"), options.value)
                    UserAvatar("compass", Modifier.size(40.dp).testTag("different"), options.value)
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
        // Exercise every renderer, including path parsing, on the real Android canvas.
        UserAvatarStyle.entries.forEach { style ->
            compose.runOnIdle { options.value = UserAvatarOptions(setOf(style)) }
            // GPU antialiasing/dithering can differ slightly with the on-screen pixel origin.
            val same = difference("first", "same")
            val different = difference("first", "different")
            assertTrue("$style same username pixels differ by $same", same < 0.015f)
            assertTrue("$style different username pixels differ by $different", different > 0.01f)
        }
        compose.runOnIdle { options.value = options.value.copy(generic = true) }
        val genericDifference = difference("first", "different")
        assertTrue("Generic icons differ by $genericDifference", genericDifference < 0.015f)
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("first").assertDoesNotExist()
        compose.onNodeWithTag("different").assertDoesNotExist()
    }

    @Test
    fun codeLinesUseTheSameSpacingInTheRowAndDetailDialog() {
        val html = com.simon.harmonichackernews.network.StoryTextProcessor.preprocessHtml(
            "<pre><code>first line\nsecond line\nthird line</code></pre>",
        )!!
        val item = PortableCommentItem(
            CommentSnapshot(73, author = "coder", text = html, expandedAnchorText = html),
            CommentPresentationSnapshot(expanded = true),
        )
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(loaded = true))
        val controller = CommentsScreenController.create(
            shouldSmoothScroll = { true }, story = story, initialThreadCached = true,
            showWebsite = false, initialScrollRestorationPending = false, accountUser = null,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            }, listener = NoOpListener(),
        )
        val showDialog = mutableStateOf(false)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        if (showDialog.value) {
                            CommentActionOverlay(controller, settings, false, false, TextStyle.Default, onOpenLink = {})
                        } else {
                            CommentRow(
                                model = SettingsCommentPreviewModel.copy(body = html),
                                style = CommentRowStyle(
                                    displayStyle = DisplayStyle.FLAT, textSize = settings.preferredTextSize,
                                    collectLinks = false, emphasizeMeta = false, depthIndicatorMode = "none",
                                    showDivider = false, preferredFont = settings.font, animateChanges = false,
                                ),
                            )
                        }
                    }
                }
            }
            fun lineSpacing(): Float {
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithText("first line\nsecond line\nthird line", useUnmergedTree = true)
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                        it(layouts)
                    }
                val layout = layouts.single()
                assertEquals(3, layout.lineCount)
                return layout.getLineBaseline(1) - layout.getLineBaseline(0)
            }
            val rowSpacing = lineSpacing()
            compose.runOnIdle {
                controller.restoreCommentActions(item)
                showDialog.value = true
            }
            compose.waitForIdle()
            assertEquals("Monospace baseline spacing should match the row", rowSpacing, lineSpacing(), 1f)
        } finally { scene.close() }
    }

    @Test
    fun dialogSurvivesGeometryChangesBackgroundingAndCanBeDismissed() {
        val story = StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot(loaded = true))
        val controller = CommentsScreenController.create(
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
            HarmonicTheme(palette.colorScheme, palette.dark) {
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
    /** Also exports clock-matched frames when commentComparisonDir is supplied to instrumentation. */
    @Test
    fun nestedCollapsePreservesMotionContentAndScrollAcrossDisplayStyles() {
        val store = CommentThreadStore()
        val story = Story().apply { id = 100 }
        store.reset(story)
        store.replaceParsedComments(story, (1..40).map { id ->
            Comment().apply {
                this.id = id
                by = "reader$id"
                text = "Body $id with <b>formatted text</b>.<p>A second paragraph."
                depth = when (id) { 1 -> 0; 2 -> 1; 3 -> 2; 4 -> 1; else -> 0 }
                parent = when (id) { 2, 4 -> 1; 3 -> 2; else -> -1 }
                expanded = true
            }
        }, "Default", false)
        val storySnapshot = StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot())
        lateinit var controller: CommentsScreenController
        fun publish() = controller.updateContent(controller.screenState.copy(
            comments = store.state.value.allComments,
            visibleComments = store.state.value.visibleComments,
        ))
        controller = CommentsScreenController.create(
            shouldSmoothScroll = { true }, story = storySnapshot, initialThreadCached = true,
            showWebsite = false, accountUser = null,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            }, listener = object : NoOpListener() {
                override fun onToggleComment(comment: PortableCommentItem, position: Int) {
                    store.toggleExpanded(comment.id)
                    publish()
                }
            },
        )
        controller.updateContent(CommentsScreenState(
            story = storySnapshot, commentsLoaded = true, initialThreadCached = true,
            displaySettings = settings, comments = store.state.value.allComments,
            visibleComments = store.state.value.visibleComments,
        ))
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(
                    LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene),
                    LocalHazePreferences provides SurfaceEffectPreferences(mode = SurfaceEffectMode.Solid),
                ) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        Box(Modifier.fillMaxWidth().height(600.dp).testTag("comparison-thread")) {
                            CommentsScreen(
                                controller, Modifier, false, pullToRefreshEnabled = false,
                                showNavigationControls = false, animateComments = true, showScrollbar = true,
                                smoothScroll = true, userTags = emptyMap(), onOpenLink = {},
                                headerContent = {}, searchDialog = {}, actionOverlay = {},
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            fun toggle(author: String) {
                // Invoke the accessible action directly: Android ripple time is independent of
                // the Compose test clock and would make before/after frame captures nondeterministic.
                compose.onNodeWithText(author).performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.OnClick,
                ) { it() }
            }
            for (display in DisplayStyle.entries) for (collapseBody in listOf(false, true)) {
                val label = "${display.name}-$collapseBody"
                compose.runOnIdle {
                    store.restoreCollapsedComments(emptySet())
                    publish()
                    controller.updateContent(controller.screenState.copy(displaySettings = settings.copy(
                        displayStyle = display, collapseParent = collapseBody,
                        continuousDepthIndicators = true, roundedDepthIndicators = true,
                        showTopLevelDepthIndicator = true, showDividers = true,
                    )))
                    controller.scrollToComment(0, 0, false)
                }
                compose.mainClock.advanceTimeBy(1_000)
                captureComparisonFrame("$label-expanded")
                toggle("reader2")
                compose.mainClock.advanceTimeBy(400)
                compose.onNodeWithText("reader3").assertDoesNotExist()
                captureComparisonFrame("$label-nested")
                toggle("reader1")
                compose.mainClock.advanceTimeBy(64)
                captureComparisonFrame("$label-collapse64")
                compose.mainClock.advanceTimeBy(64)
                captureComparisonFrame("$label-collapse128")
                compose.mainClock.advanceTimeBy(400)
                compose.onNodeWithText("reader2").assertDoesNotExist()
                captureComparisonFrame("$label-collapsed")
                toggle("reader1")
                compose.mainClock.advanceTimeBy(64)
                captureComparisonFrame("$label-expand64")
                compose.mainClock.advanceTimeBy(400)
                compose.onNodeWithText("reader2").assertExists()
                compose.onNodeWithText("reader3").assertDoesNotExist()
                captureComparisonFrame("$label-restored-nested")
                // Reverse an in-flight collapse, then verify the nested state survives again.
                toggle("reader1")
                compose.mainClock.advanceTimeBy(64)
                toggle("reader1")
                compose.mainClock.advanceTimeBy(600)
                compose.onNodeWithText("reader2").assertExists()
                compose.onNodeWithText("reader3").assertDoesNotExist()
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(12)
                compose.mainClock.advanceTimeBy(400)
                captureComparisonFrame("$label-scrolled")
            }
        } finally {
            compose.mainClock.autoAdvance = true
            scene.close()
        }
    }

    private fun captureComparisonFrame(name: String) {
        val label = InstrumentationRegistry.getArguments().getString("commentComparisonDir") ?: return
        val directory = File(compose.activity.getExternalFilesDir(null), "comment-comparison/$label").apply { mkdirs() }
        val node = compose.onNodeWithTag("comparison-thread")
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        File(directory, "$name.txt").writeText(node.printToString(maxDepth = 20))
    }

    private val settings = CommentDisplaySettings(
        collapseParent = false, showFavicons = false, showHeaderPreviewImage = false,
        tintHeader = false, showUpButton = false, paletteTintMode = "default",
        preferredTextSize = 14f, commentDepthIndicatorMode = "threads", showNavigationBar = false,
        font = "default", showInvert = false, showTopLevelDepthIndicator = false, theme = null,
        isTablet = false, faviconProvider = "default", swapLongPressTap = false,
        displayStyle = DisplayStyle.RAISED, showDividers = false,
        highlightCommentMeta = false, collectReferenceLinks = false, hasAccountDetails = false,
        canProvideSummary = false, showAdditionalSummaryInfo = false, enableSummaryBoldFormatting = true,
    )

    private open class NoOpListener : CommentsScreenController.Listener {
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
