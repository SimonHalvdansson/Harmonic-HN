package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.geometry.Rect
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StorySearchOption
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoriesComposeControllerPreviewNavigationTest {
    @Test
    fun commentsNavigationKeepsPreviewReadyForBack() {
        val controller = controller(destinationRemainsBesideStories = false)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = false)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(0, controller.storyPreviewDismissRequest)
    }

    @Test
    fun commentsNavigationKeepsPreviewOpenWhenStoriesRemainVisible() {
        val controller = controller(destinationRemainsBesideStories = true)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = false)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(0, controller.storyPreviewDismissRequest)
    }

    @Test
    fun websiteNavigationStillDismissesPreviewInSinglePane() {
        val controller = controller(destinationRemainsBesideStories = false)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = true)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(1, controller.storyPreviewDismissRequest)
    }

    @Test
    fun websiteNavigationKeepsPreviewInSplitLayout() {
        val controller = controller(destinationRemainsBesideStories = true)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = true)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(0, controller.storyPreviewDismissRequest)
    }

    @Test
    fun pagingUpdatesOnlyRowLocalAlphaStatesAndCanResetThem() {
        val controller = controller(destinationRemainsBesideStories = false)

        controller.onStoryPreviewPagePosition(lowerPage = 0, upperPage = 1, offset = 0.25f)

        assertEquals(0.25f, controller.storyPagingAlphaState(1).floatValue)
        assertEquals(0.75f, controller.storyPagingAlphaState(2).floatValue)
        assertEquals(emptyMap(), controller.storyPagingAlphas)

        controller.clearStoryPagingAlphas()

        assertEquals(1f, controller.storyPagingAlphaState(1).floatValue)
        assertEquals(1f, controller.storyPagingAlphaState(2).floatValue)
    }

    @Test
    fun contentReplacementPrunesGeometryForRemovedStories() {
        val controller = controller(destinationRemainsBesideStories = false)
        controller.updateContent(
            StoriesScreenState(mainStories = listOf(storySnapshot(1), storySnapshot(2))),
        )
        controller.updateStoryBounds(1, Rect(0f, 0f, 10f, 10f))

        controller.updateContent(StoriesScreenState(mainStories = listOf(storySnapshot(2))))

        assertNull(controller.sourceBoundsForStory(1))
    }

    @Test
    fun geometryTrackingEvictsLeastRecentlyUpdatedEntries() {
        val controller = controller(destinationRemainsBesideStories = false)

        repeat(300) { index ->
            controller.updateStoryBounds(index + 1, Rect(0f, 0f, 10f, 10f))
        }

        assertNull(controller.sourceBoundsForStory(1))
        assertNotNull(controller.sourceBoundsForStory(300))
    }

    @Test
    fun previewAndShellUpdatesPreserveTheUnchangedStoryListSlice() {
        val controller = controller(destinationRemainsBesideStories = false)
        val stories = listOf(storySnapshot(1), storySnapshot(2))
        controller.updateContent(StoriesScreenState(mainStories = stories))
        val retainedStories = controller.mainStories

        controller.updateContent(
            StoriesScreenState(
                mainStories = stories,
                refreshing = true,
                previewResources = mapOf(
                    1 to StoryPreviewResourceState(
                        storyId = 1,
                        pageUrl = "https://example.com",
                        imageUrlResolved = true,
                    ),
                ),
            ),
        )

        assertSame(retainedStories, controller.mainStories)
        assertEquals(true, controller.refreshing)
        assertEquals(true, controller.previewResource(1)?.imageUrlResolved)
    }

    @Test
    fun terminalPreviewFailureIsRememberedAsAnImageMissForTheSession() {
        val controller = controller(destinationRemainsBesideStories = false)
        val stories = listOf(storySnapshot(1), storySnapshot(2))

        controller.updateContent(
            StoriesScreenState(
                mainStories = stories,
                previewResources = mapOf(
                    1 to StoryPreviewResourceState(
                        storyId = 1,
                        pageUrl = "https://example.com/large-page",
                        contentLoadFailed = true,
                    ),
                ),
            ),
        )
        assertTrue(controller.isStoryPreviewImageKnownAbsent(1))

        // Retrying summary metadata may put the resource back in a loading state, but it must not
        // reintroduce image space after this dialog already observed the miss.
        controller.updateContent(
            StoriesScreenState(
                mainStories = stories,
                previewResources = mapOf(
                    1 to StoryPreviewResourceState(
                        storyId = 1,
                        pageUrl = "https://example.com/large-page",
                        loading = true,
                    ),
                ),
            ),
        )
        assertTrue(controller.isStoryPreviewImageKnownAbsent(1))
    }

    @Test
    fun inFlightPreviewIsNotPrematurelyRememberedAsAnImageMiss() {
        val controller = controller(destinationRemainsBesideStories = false)

        controller.updateContent(
            StoriesScreenState(
                mainStories = listOf(storySnapshot(1)),
                previewResources = mapOf(
                    1 to StoryPreviewResourceState(
                        storyId = 1,
                        pageUrl = "https://example.com/article",
                        loading = true,
                    ),
                ),
            ),
        )

        assertFalse(controller.isStoryPreviewImageKnownAbsent(1))
    }

    @Test
    fun pullToRefreshOwnershipIsIndependentFromGenericRefreshingState() {
        val controller = controller(destinationRemainsBesideStories = false)

        controller.updateContent(StoriesScreenState(refreshing = true))

        assertTrue(controller.refreshing)
        assertFalse(controller.pullToRefreshInProgress)

        controller.beginPullToRefresh()
        assertTrue(controller.pullToRefreshInProgress)

        controller.finishPullToRefresh()
        assertFalse(controller.pullToRefreshInProgress)
        assertTrue(controller.refreshing)
    }

    @Test
    fun completedUserRefreshRequestsAnExactScrollToTop() {
        val listener = TestListener(destinationRemainsBesideStories = false)
        val controller = controller(
            destinationRemainsBesideStories = false,
            listener = listener,
        )

        controller.refresh()
        assertEquals(listOf(false), listener.refreshLoadingModes)
        controller.updateContent(StoriesScreenState(refreshing = true))
        assertEquals(0, controller.scrollToTopRequestVersion)

        controller.updateContent(
            StoriesScreenState(
                mainStories = listOf(storySnapshot(2), storySnapshot(1)),
                refreshing = false,
            ),
        )
        assertEquals(1, controller.scrollToTopRequestVersion)

        controller.refresh()
        controller.updateContent(StoriesScreenState(refreshing = true))
        controller.updateContent(StoriesScreenState(refreshing = false))
        assertEquals(2, controller.scrollToTopRequestVersion)
    }

    @Test
    fun tapToUpdateRetainsStoriesUntilReplacementRefreshCompletes() {
        val listener = TestListener(destinationRemainsBesideStories = false)
        val controller = controller(
            destinationRemainsBesideStories = false,
            listener = listener,
        )
        controller.updateContent(
            StoriesScreenState(mainStories = listOf(storySnapshot(1), storySnapshot(2))),
        )

        controller.beginTapToUpdateExit()

        assertTrue(controller.tapToUpdateExitInProgress)
        assertTrue(listener.refreshLoadingModes.isEmpty())

        controller.completeTapToUpdateExit()

        assertEquals(listOf(false), listener.refreshLoadingModes)
        controller.updateContent(
            StoriesScreenState(
                refreshing = true,
                mainStories = listOf(storySnapshot(1), storySnapshot(2)),
            ),
        )
        assertTrue(controller.tapToUpdateExitInProgress)
        assertEquals(0, controller.scrollToTopRequestVersion)

        controller.updateContent(
            StoriesScreenState(
                refreshing = false,
                mainStories = listOf(storySnapshot(3), storySnapshot(4)),
            ),
        )
        assertFalse(controller.tapToUpdateExitInProgress)
        assertFalse(controller.tapToUpdateRefreshStarted)
        assertEquals(1, controller.scrollToTopRequestVersion)
    }

    @Test
    fun tapToUpdateReleasesTheHiddenLayerWhenRefreshFails() {
        val listener = TestListener(destinationRemainsBesideStories = false)
        val controller = controller(
            destinationRemainsBesideStories = false,
            listener = listener,
        )
        val retainedStories = listOf(storySnapshot(1), storySnapshot(2))
        controller.updateContent(StoriesScreenState(mainStories = retainedStories))

        controller.beginTapToUpdateExit()
        controller.completeTapToUpdateExit()
        controller.updateContent(
            StoriesScreenState(refreshing = true, mainStories = retainedStories),
        )
        controller.updateContent(
            StoriesScreenState(
                refreshing = false,
                loadingFailed = true,
                mainStories = retainedStories,
            ),
        )

        assertFalse(controller.tapToUpdateExitInProgress)
        assertFalse(controller.tapToUpdateRefreshStarted)
        assertEquals(1, controller.scrollToTopRequestVersion)
    }

    @Test
    fun durableRuntimeStateClearsPreviewActionLoadingWithoutATerminalEffect() {
        val controller = controller(destinationRemainsBesideStories = false)
        val stories = listOf(storySnapshot(1), storySnapshot(2))

        controller.onStoryPreviewAction(page = 0, action = StoryPreviewActionKind.Vote)
        assertTrue(controller.isStoryPreviewVoteLoading(1))
        controller.updateContent(
            StoriesScreenState(mainStories = stories, previewVoteLoadingIds = setOf(1)),
        )
        controller.updateContent(
            StoriesScreenState(mainStories = stories, previewVoteLoadingIds = emptySet()),
        )

        assertFalse(controller.isStoryPreviewVoteLoading(1))
    }

    @Test
    fun readActionUpdatesThePreviewStateImmediately() {
        val controller = controller(destinationRemainsBesideStories = false)

        assertFalse(controller.isStoryPreviewRead(1, initialValue = false))
        controller.onStoryPreviewAction(page = 0, action = StoryPreviewActionKind.Read)
        assertTrue(controller.isStoryPreviewRead(1, initialValue = false))
        controller.onStoryPreviewAction(page = 0, action = StoryPreviewActionKind.Read)
        assertFalse(controller.isStoryPreviewRead(1, initialValue = true))
    }

    private fun storySnapshot(id: Int) = StoryListItemSnapshot(
        story = StorySnapshot(id = id, title = "Story $id"),
        presentation = StoryPresentationSnapshot(loaded = true),
    )

    private fun controller(
        destinationRemainsBesideStories: Boolean,
        listener: TestListener = TestListener(destinationRemainsBesideStories),
    ): StoriesComposeController =
        StoriesComposeController.create(
            defaultStoryHeightPx = 100,
            savedItemState = EmptySavedItemState,
            listener = listener,
        ).also { controller ->
            controller.showStoryPreview(
                stories = listOf(
                    StoryListItemSnapshot(
                        story = StorySnapshot(id = 1, title = "Story"),
                        presentation = StoryPresentationSnapshot(loaded = true),
                    ),
                    StoryListItemSnapshot(
                        story = StorySnapshot(id = 2, title = "Next story"),
                        presentation = StoryPresentationSnapshot(loaded = true),
                    ),
                ),
                cardColors = intArrayOf(0, 0),
                openedStoryId = 1,
            )
        }

    private data object EmptySavedItemState : SavedItemStateReader {
        override fun isBookmarked(itemId: Int): Boolean = false
        override fun isFavorited(itemId: Int): Boolean = false
        override fun isUpvoted(itemId: Int, isComment: Boolean): Boolean = false
    }

    private class TestListener(
        private val destinationRemainsBesideStories: Boolean,
    ) : StoriesComposeController.Listener {
        val refreshLoadingModes = mutableListOf<Boolean>()
        override fun onTypeSelected(index: Int) = Unit
        override fun onOpenSearch() = Unit
        override fun onCloseSearch() = Unit
        override fun onSearch(query: String) = Unit
        override fun onSearchOption(kind: StorySearchOption, index: Int) = Unit
        override fun onToggleOnlyClicked() = Unit
        override fun onRefresh(showMainLoadingIndicator: Boolean) {
            refreshLoadingModes += showMainLoadingIndicator
        }
        override fun onShowCached() = Unit
        override fun onLoadMore() = Unit
        override fun onSavedFilterSelected(filter: SavedItemFilter) = Unit
        override fun onShiftFrontDate(days: Int) = Unit
        override fun onPickFrontDate() = Unit
        override fun onFrontDateSelected(day: Long) = Unit
        override fun onMoreAction(action: StoriesMenuAction) = Unit
        override fun onCacheStoriesConfirmed(
            storyCount: Int,
            downloadWebViewContents: Boolean,
        ) = Unit
        override fun onLinkClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentStoryClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentRepliesClick(story: StoryListItemSnapshot) = Unit
        override fun onStoryLongClick(
            story: StoryListItemSnapshot,
            tintBaseColorArgb: Int,
        ) = null
        override fun onStoryPreviewImageLoaded(storyId: Int, pageUrl: String, imageUrl: String) = Unit
        override fun onStoryPreviewImageLoadFailed(
            storyId: Int,
            pageUrl: String,
            imageUrl: String,
        ) = Unit
        override fun onStoryTintExtracted(
            story: StoryListItemSnapshot,
            sourceUrl: String,
            baseColorArgb: Int,
            paletteConfigKey: String,
            tintColorArgb: Int,
            favicon: Boolean,
        ) = Unit
        override fun onVisibleStoryRange(lastVisibleIndex: Int) = Unit
        override fun onStoryPreviewStopScroll() = Unit
        override fun onStoryPreviewVisibilityChanged(showing: Boolean) = Unit
        override fun onStoryPreviewNavigate(
            story: StoryListItemSnapshot,
            showWebsite: Boolean,
        ): Boolean = destinationRemainsBesideStories
        override fun onStoryPreviewAction(
            story: StoryListItemSnapshot,
            action: StoryPreviewActionKind,
        ) = Unit
    }
}
