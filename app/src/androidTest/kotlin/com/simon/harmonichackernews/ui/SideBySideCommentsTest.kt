package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.ui.comments.*
import com.simon.harmonichackernews.ui.navigation.TwoPaneCommentsSurface
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real sheet, movable composition, header actions, and scroll state; no network or account needed. */
@RunWith(AndroidJUnit4::class)
class SideBySideCommentsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var controller: CommentsScreenController
    private lateinit var portal: SideBySideCommentsPortal

    @Test fun enteringAndLeavingSplitPreservesCommentsScrolledInTheLeftPane() = verifyScrollRetention()

    @Test fun unequalPanesRetainScrollWhenReturningToTheSheet() = verifyScrollRetention(leftWeight = 0.43f)

    @Test fun narrowerLeftPaneKeepsItsWidthDuringSheetTransfer() = verifyFixedTransferWidth(0.35f)

    @Test fun widerLeftPaneKeepsItsWidthDuringSheetTransfer() = verifyFixedTransferWidth(0.65f)

    private fun verifyFixedTransferWidth(leftWeight: Float) = withFixture(leftWeight = leftWeight) {
        visibleAction("Read side by side").performClick()
        compose.waitForIdle()
        val initialWidth = compose.runOnIdle { portal.contentSize.width }
        val rightWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width.toInt() - initialWidth
        compose.runOnIdle { controller.beginPredictiveBack(0.8f) }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.runOnIdle { !portal.liveInLeft } }
        compose.runOnIdle {
            assertTrue(portal.sheetExpansion in 0.2f..0.4f)
            assertEquals("The departing left pane must keep its own width", initialWidth, portal.leftSnapshot?.width)
            assertEquals("The right preview must already use its native width", rightWidth, portal.contentSize.width)
            controller.endPredictiveBack()
        }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.runOnIdle { portal.liveInLeft } }
        compose.runOnIdle { assertEquals(initialWidth, portal.contentSize.width) }
    }

    @Test fun enteringFromTheBrowserSlidesAnOpaqueCommentsSurfaceIntoTheLeftPane() = withFixture {
        compose.runOnIdle { controller.requestCollapseSheet() }
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val sampleY = (root.top + root.height * 0.6f).toInt()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.takeScreenshot().let { before ->
            try {
                assertEquals(android.graphics.Color.GREEN, before.getPixel((root.width * 0.4f).toInt(), sampleY))
            } finally {
                before.recycle()
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { controller.toggleSideBySide() }
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        automation.takeScreenshot().let { during ->
            try {
                // Halfway through the horizontal slide, the story pane remains visible at its
                // left edge while an opaque comments surface has entered from the right.
                assertEquals(android.graphics.Color.GREEN, during.getPixel((root.width * 0.02f).toInt(), sampleY))
                val covered = during.getPixel((root.width * 0.45f).toInt(), sampleY)
                assertNotEquals(android.graphics.Color.GREEN, covered)
                assertTrue("The entering comments must not fade through the green story pane",
                    kotlin.math.abs(android.graphics.Color.green(covered) - android.graphics.Color.red(covered)) < 35)
            } finally {
                during.recycle()
                compose.mainClock.autoAdvance = true
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(controller.sideBySideActive) }
        compose.onNodeWithText("A test article").assertIsDisplayed()
    }

    @Test fun enteringSplitRevealsTheLeftCommentsBeforeTheSheetFinishesMoving() = withFixture {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val sampleX = (root.left + root.width * 0.2f).toInt()
        val sampleY = (root.top + root.height * 0.95f).toInt()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.takeScreenshot().let { before ->
            try {
                assertEquals("Verify the screenshot samples the story pane", android.graphics.Color.GREEN, before.getPixel(sampleX, sampleY))
            } finally {
                before.recycle()
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { controller.toggleSideBySide() }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(controller.sideBySideActive)
            assertTrue("Capture must exercise the transfer, not its settled state", portal.sheetExpansion in 0.01f..0.9f)
        }
        val frame = automation.takeScreenshot()
        try {
            // The uncovered story pane is green in this fixture. The lower left comments
            // must already cover it while the upper portion still transfers from the sheet.
            assertNotEquals(android.graphics.Color.GREEN, frame.getPixel(sampleX, sampleY))
        } finally {
            frame.recycle()
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
    }

    private fun verifyScrollRetention(leftWeight: Float = 0.5f) = withFixture(leftWeight = leftWeight) {
        visibleAction("Read side by side").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(controller.sideBySideActive)
            assertTrue(controller.isWebsiteVisible())
            controller.scrollToComment(25, 0, false)
        }
        compose.waitForIdle()
        awaitScrollTo(25)
        val progress = scrollPosition()
        val leftComment = compose.onNodeWithText("reader25").fetchSemanticsNode().boundsInRoot
        assertTrue(leftComment.center.x < compose.onRoot().fetchSemanticsNode().boundsInRoot.width / 2)
        compose.runOnIdle { controller.requestExpandSheet() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(controller.sideBySideActive)
            assertTrue(controller.isSheetExpanded())
        }
        assertEquals(progress, scrollPosition())
        val rightComment = compose.onNodeWithText("reader25").fetchSemanticsNode().boundsInRoot
        assertTrue(rightComment.center.x > compose.onRoot().fetchSemanticsNode().boundsInRoot.width / 2)
    }

    @Test fun predictiveBackCancellationAndCommitRetainScrollAndUseSheetMotion() = withFixture {
        visibleAction("Read side by side").performClick()
        compose.waitForIdle()
        compose.runOnIdle { controller.scrollToComment(20, 0, false) }
        compose.waitForIdle()
        awaitScrollTo(20)
        val progress = scrollPosition()
        compose.runOnIdle { controller.beginPredictiveBack(0.8f) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Back must progressively transfer the comments", portal.sheetExpansion > 0.2f)
            assertTrue(controller.sideBySideActive)
            controller.endPredictiveBack()
        }
        compose.waitForIdle()
        assertEquals(progress, scrollPosition())
        compose.runOnIdle {
            assertEquals(0f, portal.sheetExpansion, 0.001f)
            controller.beginPredictiveBack(0.8f)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            controller.requestExpandSheet()
            controller.endPredictiveBack()
            assertEquals(0.8f, controller.committedSheetBackProgress, 0.001f)
        }
        compose.waitForIdle()
        assertEquals(progress, scrollPosition())
        compose.runOnIdle {
            assertFalse(controller.sideBySideActive)
            assertEquals(0f, controller.committedSheetBackProgress, 0.001f)
        }
    }

    @Test fun browserToolbarReplacesUpWithSplitAndKeepsReaderMode() = withFixture {
        compose.runOnIdle { controller.requestCollapseSheet() }
        compose.waitForIdle()
        visibleAction("Show comments").assertDoesNotExist()
        val split = visibleAction("Read side by side").fetchSemanticsNode().boundsInRoot
        val refresh = visibleAction("Refresh website").fetchSemanticsNode().boundsInRoot
        assertTrue(split.center.x < refresh.center.x)
        visibleAction("Reader mode").assertExists()
        visibleAction("Read side by side").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(controller.sideBySideActive) }
    }

    @Test fun browserToolbarStaysNeutralUntilTheVisibleHeaderIsPulledUp() = withFixture {
        visibleAction("Read side by side").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull("A stale tinted sheet must not flash at the start of the next pull", portal.rightSnapshot)
        }
        val background = HarmonicThemeCatalog.resolve("light", false).colorScheme.pageBackground
        val tint = compose.runOnIdle { requireNotNull(controller.headerBackgroundColor) }
        assertNotEquals("The fixture needs a tinted header", background, tint)
        // A visible (or partially visible) header must not tint the resting browser toolbar.
        for (coverage in listOf(1f, 0.5f, 0f)) {
            compose.runOnIdle { controller.updateStatusBarHeaderCoverage(coverage) }
            compose.waitForIdle()
            assertToolbarColor(background)
            for (backProgress in listOf(0.3f, 0.8f)) {
                compose.runOnIdle { controller.beginPredictiveBack(backProgress) }
                compose.waitForIdle()
                val reveal = compose.runOnIdle { portal.sheetExpansion }
                assertTrue(reveal > 0f && reveal < 1f)
                assertToolbarColor(tint.copy(alpha = coverage * reveal).compositeOver(background))
                if (coverage == 1f) {
                    val pixels = compose.onRoot().captureToImage().toPixelMap()
                    val headerTop = (pixels.height * (1f - reveal)).toInt() + 3
                    val incomingHeader = pixels[pixels.width - 4, headerTop].toArgb()
                    assertSurfaceColor(tint.copy(alpha = reveal).compositeOver(background), incomingHeader,
                        "The status-bar gradient must not leave a fully tinted strip below the controls")
                }
                compose.runOnIdle {
                    assertEquals("The incoming header uses the same fade as a normal sheet",
                        tint.copy(alpha = reveal).compositeOver(background).toArgb(), controller.statusBarHeaderColor?.toArgb())
                }
            }
            compose.runOnIdle { controller.endPredictiveBack() }
            compose.waitForIdle()
            assertToolbarColor(background)
            compose.runOnIdle { assertNull(portal.rightSnapshot) }
        }
        // Real scrolling must clear the tint without leaving split mode or losing position.
        compose.runOnIdle { controller.scrollToComment(20, 0, false) }
        compose.waitForIdle()
        awaitScrollTo(20)
        compose.runOnIdle { assertEquals(0f, controller.statusBarHeaderCoverage, 0.001f) }
        assertToolbarColor(background)
        val progress = scrollPosition()
        compose.runOnIdle { controller.beginPredictiveBack(0.6f) }
        compose.waitForIdle()
        assertToolbarColor(background)
        compose.runOnIdle { controller.endPredictiveBack() }
        compose.waitForIdle()
        assertEquals(progress, scrollPosition())
    }

    private fun assertToolbarColor(expected: Color) {
        val button = visibleAction("Refresh website").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage().toPixelMap()
        val actual = image[image.width - 4, button.center.y.toInt()].toArgb()
        assertSurfaceColor(expected, actual, "Toolbar")
    }

    private fun assertSurfaceColor(expected: Color, actual: Int, context: String) {
        val wanted = expected.toArgb()
        for (shift in listOf(0, 8, 16)) {
            assertTrue("$context color ${Integer.toHexString(actual)} != ${Integer.toHexString(wanted)}",
                kotlin.math.abs(((actual shr shift) and 255) - ((wanted shr shift) and 255)) <= 2)
        }
    }

    @Test fun draggingTheHandleReturnsCommentsWithoutResettingTheList() = withFixture {
        visibleAction("Read side by side").performClick()
        compose.waitForIdle()
        compose.runOnIdle { controller.scrollToComment(30, 0, false) }
        compose.waitForIdle()
        awaitScrollTo(30)
        val progress = scrollPosition()
        val height = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("comments-sheet-handle").performTouchInput {
            swipe(center, center.copy(y = center.y - height * 0.8f), durationMillis = 650)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(controller.sideBySideActive)
            assertTrue(controller.isSheetExpanded())
        }
        assertEquals(progress, scrollPosition())
    }

    @Test fun signedInHeaderPutsSplitFirstInOverflow() = withFixture(signedIn = true) {
        visibleAction("Read side by side").assertDoesNotExist()
        visibleAction("More options").performClick()
        compose.onNodeWithText("Read side by side").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(controller.sideBySideActive) }
    }

    @Test fun disablingCapabilityReturnsToCommentsAndHidesSplitAction() = withFixture {
        visibleAction("Read side by side").performClick()
        compose.waitForIdle()
        compose.runOnIdle { controller.updateSideBySideAvailability(false) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(controller.sideBySideActive)
            assertTrue(controller.isSheetExpanded())
        }
        visibleAction("Read side by side").assertDoesNotExist()
        compose.runOnIdle {
            controller.updateContent(controller.screenState.copy(integratedWebView = false))
            controller.updateSideBySideAvailability(true)
            controller.toggleSideBySide()
            assertFalse(controller.sideBySideAvailable)
            assertFalse(controller.sideBySideActive)
        }
    }

    private fun visibleAction(description: String): SemanticsNodeInteraction = compose.onNode(
        hasContentDescription(description) and SemanticsMatcher("within the visible window") { node ->
            val bounds = node.boundsInRoot
            bounds.height > 0f && bounds.bottom > 0f &&
                bounds.top < compose.activity.window.decorView.height
        },
    )

    private fun scrollPosition() = compose.runOnIdle {
        controller.firstVisibleCommentId to controller.firstVisibleCommentOffset
    }

    private fun awaitScrollTo(commentId: Int) {
        try {
            compose.waitUntil(5_000) {
                compose.runOnIdle {
                    controller.scrollToCommentRequest == null &&
                        controller.firstVisibleCommentId > 0
                }
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Scroll to $commentId did not settle; position=${scrollPosition()}", error)
        }
        // Status-bar clearance can expose multiple preceding short rows. Check the requested
        // row itself, then compare the exact retained index/offset across the pane transfer.
        val target = compose.onNodeWithText("reader$commentId").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(target.top < compose.onRoot().fetchSemanticsNode().boundsInRoot.height * 0.25f)
    }

    private fun withFixture(signedIn: Boolean = false, leftWeight: Float = 0.5f, test: () -> Unit) {
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val story = StoryListItemSnapshot(
            StorySnapshot(42, title = "A test article", url = "https://example.com", author = "author"),
            StoryPresentationSnapshot(loaded = true, isLink = true),
        )
        val comments = (1..80).map { id ->
            PortableCommentItem(
                CommentSnapshot(id, author = "reader$id", text = "Comment $id. " + "A paragraph to read alongside the article. ".repeat(5)),
                CommentPresentationSnapshot(expanded = true, depth = 0),
            )
        }
        controller = CommentsScreenController.create(
            shouldSmoothScroll = { false }, story = story, initialThreadCached = true,
            showWebsite = false, accountUser = if (signedIn) "test-reader" else null,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            },
            listener = Listener(),
        )
        controller.updateContent(CommentsScreenState(
            story = story, comments = comments, commentsLoaded = true, initialThreadCached = true,
            integratedWebView = true, readerModeAvailable = true,
            visibleComments = comments.mapIndexed { index, item -> PortableVisibleComment(index, item, 0) },
            displaySettings = CommentDisplaySettings.from(
                app.userSettings.comments, showInvert = true, isTablet = true,
                hasAccountDetails = signedIn, canProvideSummary = false,
            ).copy(showFavicons = false, showHeaderPreviewImage = false, showUpButton = false, showNavigationBar = false,
                tintHeader = true),
        ))
        controller.updateSideBySideAvailability(true)
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                ProvideHarmonicUiDependencies(HarmonicUiDependencies(app, scene)) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        SideBySideCommentsHost { overlay ->
                            portal = requireNotNull(LocalSideBySideCommentsPortal.current)
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(leftWeight).fillMaxHeight().background(Color.Green)) { overlay() }
                                Box(Modifier.weight(1f - leftWeight).fillMaxHeight().background(Color.White)) {
                                    val retained = rememberSideBySideCommentsContent(controller) {
                                        TwoPaneCommentsSurface(
                                            controller,
                                            controller.headerBackgroundColor ?: palette.colorScheme.pageBackground,
                                            24.dp,
                                        )
                                    }
                                    CommentsScaffold(controller, false, retained)
                                }
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            test()
        } finally {
            compose.mainClock.autoAdvance = true
            scene.close()
        }
    }

    private inner class Listener : CommentsScreenController.Listener {
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
        override fun onCollapseSheetForWebsite() = controller.requestCollapseSheet()
        override fun onSheetProgressChanged(expandedFraction: Float) = Unit
        override fun onSheetSettled(expanded: Boolean) = Unit
        override fun onHeaderColorChanged(color: Int) = Unit
        override fun onHeaderCoverageChanged(coverage: Float) = Unit
        override fun onPollOption(optionId: Int) = Unit
    }
}
