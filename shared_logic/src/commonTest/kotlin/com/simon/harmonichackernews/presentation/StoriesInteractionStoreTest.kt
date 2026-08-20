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
        assertTrue(store.state.headerPinnedForPreview)

        store.consumeScrollRequest(first)
        assertEquals(second, store.state.scrollRequest)
        store.consumeScrollRequest(second)
        assertNull(store.state.scrollRequest)
        store.unpinPreviewHeader()
        assertFalse(store.state.headerPinnedForPreview)
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
        assertTrue(store.completeStoryPreviewDismiss())
        assertNull(store.state.storyPreviewOverlay)
        assertTrue(store.state.storyPagingAlphas.isEmpty())
        assertFalse(store.completeStoryPreviewDismiss())
    }

    @Test
    fun previewActionsAndPagingDistanceOnlyChangeMatchingState() {
        val store = store(defaultHeight = 96)
        val stories = listOf(story(1), story(2), story(3))
        store.updateContent(stories, emptyList(), searching = false, lastSearch = "")
        assertTrue(store.showStoryPreview(stories, listOf(1, 2, 3), 1))

        store.beginStoryPreviewAction(1, StoryPreviewActionKind.Vote)
        assertEquals(2, store.state.storyPreviewVoteLoadingId)
        store.finishStoryPreviewAction(3, StoryPreviewActionKind.Vote)
        assertEquals(2, store.state.storyPreviewVoteLoadingId)
        store.finishStoryPreviewAction(2, StoryPreviewActionKind.Vote)
        assertEquals(-1, store.state.storyPreviewVoteLoadingId)

        store.updateStoryItemHeight(1, 80)
        store.updateStoryItemHeight(2, 120)
        assertEquals(200, store.getStoryPagingDistance(1, 3))
        assertEquals(100, store.getStoryPagingDistance(3, 3))

        store.updateContent(listOf(story(3)), emptyList(), searching = false, lastSearch = "")
        assertEquals(96, store.getStoryPagingDistance(3, 999))
    }

    @Test
    fun storyItemExtentsIgnoreInvalidAndDuplicateMeasurements() {
        val store = store()

        assertFalse(store.updateStoryItemHeight(1, 0))
        assertTrue(store.updateStoryItemHeight(1, 80))
        assertFalse(store.updateStoryItemHeight(1, 80))
        assertTrue(store.updateStoryItemHeight(1, 96))
    }

    private fun store(defaultHeight: Int = 100) = StoriesInteractionStore(defaultHeight)

    private fun story(id: Int) = StoryListItemSnapshot(
        story = StorySnapshot(id = id, title = "Story $id"),
        presentation = StoryPresentationSnapshot(loaded = true),
    )
}
