package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoryListStoreTest {
    @Test
    fun replacementPublishesAnImmutableSnapshotAndResetsTransientState() {
        val store = StoryListStore(pageSize = 2)
        store.setPaginationEnabled(true)
        store.beginLoad(refreshing = true)
        val input = mutableListOf(story(1), story(2), story(3))

        store.replace(input, canLoadMore = true, showingCached = true)
        input.clear()

        val state = store.state.value
        assertEquals(listOf(1, 2, 3), state.items.map { it.story.id })
        assertEquals(2, state.visibleStoryCount)
        assertTrue(state.canLoadMore)
        assertTrue(state.showingCached)
        assertFalse(state.loading)
        assertFalse(state.refreshing)

        val portable = store.portableState.value
        assertEquals(listOf(1, 2, 3), portable.items.map { it.story.id })
        store.stories.first().apply {
            title = "mutated"
            previewImageUrl = "https://example.com/mutated.png"
            previewImageUrlLoaded = true
            kids = intArrayOf(99)
        }
        assertEquals("Story 1", state.items.first().story.title)
        assertEquals(null, state.items.first().presentation.previewImage.url)
        assertEquals(emptyList(), state.items.first().story.childIds)
    }

    @Test
    fun paginationRevealsOnePageAtATime() {
        val store = StoryListStore(pageSize = 2)
        store.setPaginationEnabled(true)
        store.replace((1..5).map(::story))

        assertEquals(2, store.state.value.visibleStoryCount)
        assertEquals(4, store.revealNextPage())
        assertEquals(5, store.revealNextPage())
        assertEquals(5, store.revealNextPage())
    }

    @Test
    fun pageBoundaryAndPendingRowsAreOwnedByTheListStore() {
        val store = StoryListStore(pageSize = 2)
        store.setPaginationEnabled(true)
        store.replace((1..5).map { id -> story(id, loaded = id <= 2) })
        store.markLoadedThrough(1)

        val plan = requireNotNull(store.beginNextPage(requestGeneration = 9))

        assertEquals(3, plan.targetLoadedIndex)
        assertEquals(4, plan.nextVisibleCount)
        assertEquals(setOf(3, 4), plan.pendingStoryIds)
        assertTrue(store.state.value.loadMoreInProgress)
        assertFalse(store.finishNextPageStory(3, requestGeneration = 9))
        assertTrue(store.finishNextPageStory(4, requestGeneration = 9))
        assertFalse(store.state.value.loadMoreInProgress)
    }

    @Test
    fun savedItemFilterKeepsUnloadedPlaceholdersUntilTheirTypeIsKnown() {
        val store = StoryListStore()
        val unloaded = story(1, loaded = false)
        val story = story(2, loaded = true)
        val comment = story(3, loaded = true).also { it.isComment = true }

        assertEquals(
            listOf(1, 2),
            store.filteredSavedItems(
                listOf(unloaded, story, comment),
                SavedItemFilter.STORIES,
                keepUnloadedItems = true,
            ).map(Story::id),
        )
        assertEquals(
            listOf(3),
            store.filteredSavedItems(
                listOf(unloaded, story, comment),
                SavedItemFilter.COMMENTS,
                keepUnloadedItems = false,
            ).map(Story::id),
        )
    }

    @Test
    fun failureEndsAllTransientLoadingStates() {
        val store = StoryListStore()
        store.beginLoad(refreshing = false)
        store.beginLoadMore()

        store.fail(StoryLoadFailure.RATE_LIMITED)

        val state = store.state.value
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertFalse(state.loadMoreInProgress)
        assertEquals(StoryLoadFailure.RATE_LIMITED, state.failure)
    }

    @Test
    fun historySynchronizationUpdatesClickedStateWithoutPlatformCallbacks() {
        val store = StoryListStore()
        store.replace(listOf(story(1), story(2).also { it.clicked = true }))

        val result = store.syncHistory(
            clickedStoryIds = setOf(1),
            searchingOnlyClicked = false,
            showingHistory = false,
            hideClicked = false,
        )

        assertEquals(StoryHistorySyncResult.CONTENT_CHANGED, result)
        assertTrue(store.stories.single { it.id == 1 }.clicked)
        assertFalse(store.stories.single { it.id == 2 }.clicked)
    }

    @Test
    fun historySynchronizationOwnsHideClickedRemovalAndRefreshDecision() {
        val store = StoryListStore()
        store.replace(listOf(story(1), story(2), story(3)))

        assertEquals(
            StoryHistorySyncResult.ITEMS_REMOVED,
            store.syncHistory(
                clickedStoryIds = setOf(1, 3),
                searchingOnlyClicked = false,
                showingHistory = false,
                hideClicked = true,
            ),
        )
        assertEquals(listOf(2), store.stories.map(Story::id))
        assertEquals(
            StoryHistorySyncResult.REFRESH_REQUIRED,
            store.syncHistory(
                clickedStoryIds = emptySet(),
                searchingOnlyClicked = false,
                showingHistory = false,
                hideClicked = true,
            ),
        )
    }

    @Test
    fun onlyClickedSearchClearsTransientClickedStyling() {
        val store = StoryListStore()
        store.replace(listOf(story(1).also { it.clicked = true }))

        assertEquals(
            StoryHistorySyncResult.CONTENT_CHANGED,
            store.syncHistory(
                clickedStoryIds = setOf(1),
                searchingOnlyClicked = true,
                showingHistory = false,
                hideClicked = false,
            ),
        )
        assertFalse(store.stories.single().clicked)
    }

    @Test
    fun metadataUpdatesReuseTheExistingItemSnapshots() {
        val store = StoryListStore()
        store.replace(listOf(story(1), story(2)))
        val items = store.state.value.items

        store.beginLoadMore()

        assertSame(items, store.state.value.items)
    }

    @Test
    fun targetedContentChangeOnlyRebuildsTheChangedStorySnapshot() {
        val store = StoryListStore()
        store.replace(listOf(story(1), story(2), story(3)))
        val before = store.state.value.items
        val changed = store.stories[1].also { it.title = "Updated" }

        store.contentChanged(changed)

        val after = store.state.value.items
        assertSame(before[0], after[0])
        assertNotSame(before[1], after[1])
        assertEquals("Updated", after[1].title)
        assertSame(before[2], after[2])
    }

    private fun story(id: Int, loaded: Boolean = true) = Story("Story $id", id, loaded, false)
}
