package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(listOf(1, 2, 3), state.stories.map(Story::id))
        assertEquals(2, state.visibleStoryCount)
        assertTrue(state.canLoadMore)
        assertTrue(state.showingCached)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
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

    private fun story(id: Int, loaded: Boolean = true) = Story("Story $id", id, loaded, false)
}
