package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoriesInteractionStoreTest {
    @Test
    fun searchAndPredictiveBackPreserveTheAutoFocusPolicy() {
        val store = store()

        store.updateContent(emptyList(), emptyList(), searching = true, lastSearch = "kmp")
        assertTrue(store.state.searching)
        assertEquals("kmp", store.state.searchDraft)

        store.beginPredictiveBack(1.4f)
        assertEquals(1f, store.state.predictiveBackProgress)
        assertTrue(store.state.suppressSearchAutoFocus)
        store.settlePredictiveBack(0f)
        val cancelled = requireNotNull(store.state.predictiveBackSettleRequest)

        store.settlePredictiveBack(1f)
        store.endPredictiveBack(cancelled)
        assertTrue(store.state.predictiveBackActive)

        store.endPredictiveBack(requireNotNull(store.state.predictiveBackSettleRequest))
        assertFalse(store.state.predictiveBackActive)
        assertTrue(store.state.suppressSearchAutoFocus)

        store.updateContent(emptyList(), emptyList(), searching = false, lastSearch = "kmp")
        assertEquals("", store.state.searchDraft)
        store.updateContent(emptyList(), emptyList(), searching = true, lastSearch = "again")
        assertFalse(store.state.suppressSearchAutoFocus)
        assertEquals("again", store.state.searchDraft)
    }

    @Test
    fun dateSelectionAndScrollRequestsAreClampedAndOrdered() {
        val store = store()

        store.showFrontDatePicker(initialDay = 50, earliestDay = 100, latestDay = 200)
        assertEquals(100, store.state.frontDatePickerRequest?.initialDay)
        assertEquals(200, store.selectFrontDate(250))
        assertNull(store.state.frontDatePickerRequest)

        store.requestScrollBy(20)
        val first = requireNotNull(store.state.scrollRequest)
        store.requestScrollBy(-5)
        val second = requireNotNull(store.state.scrollRequest)
        assertEquals(15, second.dy)
        assertEquals(LayoutDelta(15), second.delta)

        store.consumeScrollRequest(first)
        val reversedRemainder = requireNotNull(store.state.scrollRequest)
        assertEquals(-5, reversedRemainder.dy)
        store.consumeScrollRequest(reversedRemainder)
        assertNull(store.state.scrollRequest)

        store.requestScrollBy(20)
        val partial = requireNotNull(store.state.scrollRequest)
        store.requestScrollBy(10)
        store.consumeScrollRequest(partial, consumedDy = 12)
        assertEquals(18, store.state.scrollRequest?.dy)
        store.consumeScrollRequest(requireNotNull(store.state.scrollRequest))
        assertNull(store.state.scrollRequest)
    }

    @Test
    fun previewPagingOwnsSuppressionAlphasAndDismissal() {
        val store = store()
        val stories = listOf(story(1), story(2), story(3))

        assertFalse(store.showStoryPreview(stories, listOf(10), openedStoryId = 2))
        assertTrue(store.showStoryPreview(stories, listOf(10, 20, 30), 2))
        assertEquals(
            listOf(ArgbColor(10), ArgbColor(20), ArgbColor(30)),
            store.state.storyPreviewOverlay?.cardBackgrounds,
        )
        val overlay = requireNotNull(store.state.storyPreviewOverlay)
        assertEquals(listOf(10, 20, 30), overlay.cardBackgrounds.map(ArgbColor::value))
        assertEquals(1, store.state.storyPreviewOverlay?.initialPage)
        assertEquals(2, store.state.visibleStoryPreviewId)
        assertEquals(setOf(2), store.state.suppressedStoryIds)

        store.updateStoryPreviewBackGesture(
            BackGesture(0.5f, BackGestureEdge.RIGHT, pointerY = 30f),
        )
        assertEquals(BackGestureEdge.RIGHT, store.state.storyPreviewBackGesture.edge)

        store.updateStoryPreviewPagePosition(lowerPage = 1, upperPage = 2, offset = 0.25f)
        assertTrue(store.state.suppressedStoryIds.isEmpty())
        assertEquals(mapOf(2 to 0.25f, 3 to 0.75f), store.state.storyPagingAlphas)
        store.settleStoryPreviewPage(2)
        assertEquals(3, store.state.visibleStoryPreviewId)
        assertEquals(3, store.storyPreviewTarget(2)?.story?.id)

        store.requestDismissStoryPreview()
        val dismissVersion = store.state.storyPreviewDismissRequestVersion
        store.requestDismissStoryPreview()
        assertEquals(dismissVersion, store.state.storyPreviewDismissRequestVersion)
        store.requestScrollBy(50)
        assertTrue(store.completeStoryPreviewDismiss())
        assertNull(store.state.storyPreviewOverlay)
        assertNull(store.state.scrollRequest)
        assertTrue(store.state.storyPagingAlphas.isEmpty())
        assertFalse(store.completeStoryPreviewDismiss())
    }

    @Test
    fun previewActionsAndPagingDistanceOnlyChangeMatchingState() {
        val store = store(defaultHeight = 96)
        val stories = listOf(story(1), story(2), story(3))
        store.updateContent(stories, emptyList(), searching = false, lastSearch = "")
        assertTrue(store.showStoryPreview(stories, listOf(1, 2, 3), 1))

        assertEquals(stories[1], store.beginStoryPreviewAction(1, StoryPreviewActionKind.Vote)?.story)
        assertEquals(setOf(2), store.state.storyPreviewVoteLoadingIds)
        assertNull(store.beginStoryPreviewAction(1, StoryPreviewActionKind.Vote))
        store.finishStoryPreviewAction(3, StoryPreviewActionKind.Vote)
        assertEquals(setOf(2), store.state.storyPreviewVoteLoadingIds)
        store.finishStoryPreviewAction(2, StoryPreviewActionKind.Vote)
        assertTrue(store.state.storyPreviewVoteLoadingIds.isEmpty())

        store.reconcileStoryPreviewActionLoading(
            voteLoadingIds = setOf(2, 3),
            favoriteLoadingIds = setOf(1),
        )
        assertEquals(setOf(2, 3), store.state.storyPreviewVoteLoadingIds)
        assertEquals(setOf(1), store.state.storyPreviewFavoriteLoadingIds)
        assertNull(store.beginStoryPreviewAction(1, StoryPreviewActionKind.Vote))
        store.finishStoryPreviewAction(3, StoryPreviewActionKind.Vote)
        assertEquals(setOf(2), store.state.storyPreviewVoteLoadingIds)
        store.reconcileStoryPreviewActionLoading(emptySet(), emptySet())
        assertTrue(store.state.storyPreviewVoteLoadingIds.isEmpty())
        assertTrue(store.state.storyPreviewFavoriteLoadingIds.isEmpty())

        store.updateStoryItemHeight(1, 80)
        store.updateStoryItemHeight(2, 120)
        assertEquals(80, store.getAdjacentStoryPagingDistance(1))
        assertEquals(120, store.getAdjacentStoryPagingDistance(2))
        assertEquals(100, store.getAdjacentStoryPagingDistance(3))

        store.updateContent(listOf(story(3)), emptyList(), searching = false, lastSearch = "")
        assertEquals(96, store.getAdjacentStoryPagingDistance(3))
    }

    @Test
    fun dismissingAndReopeningPreviewRetainsAuthoritativePendingActions() {
        val store = store(defaultHeight = 96)
        val stories = listOf(story(1), story(2))
        store.updateContent(stories, emptyList(), searching = false, lastSearch = "")
        assertTrue(store.showStoryPreview(stories, listOf(1, 2), 0))
        store.beginStoryPreviewAction(0, StoryPreviewActionKind.Vote)

        store.requestDismissStoryPreview()
        assertTrue(store.completeStoryPreviewDismiss())
        assertEquals(setOf(1), store.state.storyPreviewVoteLoadingIds)
        assertTrue(store.showStoryPreview(stories, listOf(1, 2), 0))

        assertEquals(setOf(1), store.state.storyPreviewVoteLoadingIds)
        assertNull(store.beginStoryPreviewAction(0, StoryPreviewActionKind.Vote))
    }

    @Test
    fun storyItemExtentsIgnoreInvalidAndDuplicateMeasurements() {
        val store = store()

        assertFalse(store.updateStoryItemHeight(1, 0))
        assertTrue(store.updateStoryItemHeight(1, 80))
        assertFalse(store.updateStoryItemHeight(1, 80))
        assertTrue(store.updateStoryItemHeight(1, 96))
    }

    @Test
    fun removingVisibleStoryPagesForwardBeforePruningTheDeck() {
        val store = previewStore(openedId = 2)
        val original = requireNotNull(store.state.storyPreviewOverlay)

        store.updateContent(listOf(story(1), story(3)), emptyList(), false, "")

        val request = requireNotNull(store.state.storyPreviewRemovalRequest)
        assertEquals(3, request.targetStoryId)
        assertEquals(original, store.state.storyPreviewOverlay)
        assertNull(store.beginStoryPreviewAction(1, StoryPreviewActionKind.Bookmark))
        assertEquals(1, store.completeStoryPreviewRemoval(request))
        val updated = requireNotNull(store.state.storyPreviewOverlay)
        assertEquals(listOf(1, 3), updated.stories.map { it.id })
        assertEquals(listOf(10, 30), updated.cardBackgrounds.map { it.value })
        assertEquals(original.sessionId, updated.sessionId)
        assertEquals(3, store.state.visibleStoryPreviewId)
        assertNull(store.state.storyPreviewRemovalRequest)
        assertEquals(0, store.state.storyPreviewDismissRequestVersion)
    }

    @Test
    fun removingFinalPageMovesToPreviousSurvivingStory() {
        val store = previewStore(openedId = 3)

        store.updateContent(listOf(story(1), story(2)), emptyList(), false, "")

        val request = requireNotNull(store.state.storyPreviewRemovalRequest)
        assertEquals(2, request.targetStoryId)
        assertEquals(1, store.completeStoryPreviewRemoval(request))
        assertEquals(2, store.state.visibleStoryPreviewId)
    }

    @Test
    fun removingEarlierPagePreservesVisibleStoryAndCorrectsItsIndex() {
        val store = previewStore(openedId = 3)

        store.updateContent(listOf(story(2), story(3)), emptyList(), false, "")

        val request = requireNotNull(store.state.storyPreviewRemovalRequest)
        assertEquals(3, request.targetStoryId)
        assertEquals(1, store.completeStoryPreviewRemoval(request))
        assertEquals(3, store.state.visibleStoryPreviewId)
    }

    @Test
    fun removingLastStoryRequestsDismissalWhileRetainingItsCardForAnimation() {
        val store = store()
        store.updateContent(listOf(story(1)), emptyList(), false, "")
        store.showStoryPreview(listOf(story(1)), listOf(10), 1)

        store.updateContent(emptyList(), emptyList(), false, "")

        assertTrue(store.state.storyPreviewDismissRequestVersion > 0)
        assertEquals(listOf(1), store.state.storyPreviewOverlay?.stories?.map { it.id })
        assertNull(store.state.storyPreviewRemovalRequest)
        store.completeStoryPreviewDismiss()
        assertNull(store.state.storyPreviewOverlay)
    }

    @Test
    fun optimisticRemovalRollbackInvalidatesPendingDeckChange() {
        val store = previewStore(openedId = 2)
        store.updateContent(listOf(story(1), story(3)), emptyList(), false, "")
        val request = requireNotNull(store.state.storyPreviewRemovalRequest)

        store.updateContent(listOf(story(1), story(2), story(3)), emptyList(), false, "")

        assertNull(store.state.storyPreviewRemovalRequest)
        assertNull(store.completeStoryPreviewRemoval(request))
        assertEquals(listOf(1, 2, 3), store.state.storyPreviewOverlay?.stories?.map { it.id })
    }

    @Test
    fun unchangedFeedMembershipDoesNotPageAfterBookmarkOrFavoriteToggle() {
        val store = previewStore(openedId = 2)

        store.updateContent(listOf(story(1), story(2), story(3)), emptyList(), false, "")

        assertNull(store.state.storyPreviewRemovalRequest)
        assertEquals(0, store.state.storyPreviewDismissRequestVersion)
        assertEquals(2, store.state.visibleStoryPreviewId)
    }

    @Test
    fun dismissalDuringRemovalInvalidatesThePendingPageChange() {
        val store = previewStore(openedId = 2)
        store.updateContent(listOf(story(1), story(3)), emptyList(), false, "")
        val request = requireNotNull(store.state.storyPreviewRemovalRequest)

        store.requestDismissStoryPreview()

        assertNull(store.state.storyPreviewRemovalRequest)
        assertNull(store.completeStoryPreviewRemoval(request))
        assertTrue(store.state.storyPreviewDismissRequestVersion > 0)
    }

    private fun previewStore(openedId: Int) = store().apply {
        val stories = listOf(story(1), story(2), story(3))
        updateContent(stories, emptyList(), false, "")
        showStoryPreview(stories, listOf(10, 20, 30), openedId)
    }

    private fun store(defaultHeight: Int = 100) = StoriesInteractionStore(defaultHeight)

    private fun story(id: Int) = StoryListItemSnapshot(
        story = StorySnapshot(id = id, title = "Story $id"),
        presentation = StoryPresentationSnapshot(loaded = true),
    )
}
