package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorySearchRuntimeTest {
    @Test
    fun appliesLoadMoreAndPreservesRequestedPaginationWindow() {
        val store = StoryListStore()
        store.setPaginationEnabled(true)
        store.replace((1..40).map(::story), canLoadMore = true)
        val runtime = StorySearchRuntime()
        runtime.beginLoadMore(store)

        val loading = runtime.apply(
            store,
            StorySearchUiState(
                mode = StorySearchMode.QUERY,
                loading = true,
                loadingMore = true,
            ),
            searching = true,
            activeTypeIsAlgolia = false,
        )
        assertTrue(loading.loading)

        val applied = runtime.apply(
            store,
            StorySearchUiState(
                mode = StorySearchMode.QUERY,
                stories = (1..70).map(::story),
                canLoadMore = true,
            ),
            searching = true,
            activeTypeIsAlgolia = false,
        )

        assertTrue(applied.contentApplied)
        assertEquals(60, store.state.value.visibleStoryCount)
        assertEquals(69, store.state.value.loadedThroughIndex)
        assertFalse(store.state.value.loadMoreInProgress)
    }

    @Test
    fun failedPageKeepsTheListAndPaginationWindowReadyForRetry() {
        val store = StoryListStore()
        store.setPaginationEnabled(true)
        store.replace((1..200).map(::story), canLoadMore = true)
        store.setVisibleStoryCount(200)
        val retained = store.stories.toList()
        val runtime = StorySearchRuntime()
        runtime.beginLoadMore(store)
        runtime.apply(
            store,
            StorySearchUiState(
                mode = StorySearchMode.QUERY,
                stories = retained,
                canLoadMore = true,
                failure = StoryLoadFailure.GENERAL,
                nextPage = 1,
            ),
            searching = true,
            activeTypeIsAlgolia = false,
        )
        assertEquals(retained, store.stories)
        assertEquals(200, store.state.value.visibleStoryCount)
        assertTrue(store.state.value.canLoadMore)
        assertFalse(store.state.value.loadMoreInProgress)
        runtime.beginLoadMore(store)
        runtime.apply(
            store,
            StorySearchUiState(
                mode = StorySearchMode.QUERY,
                stories = retained + (201..400).map(::story),
                canLoadMore = false,
                nextPage = 2,
            ),
            searching = true,
            activeTypeIsAlgolia = false,
        )
        assertEquals(230, store.state.value.visibleStoryCount)
        assertEquals((1..400).toList(), store.stories.map(Story::id))
        assertFalse(store.state.value.canLoadMore)
    }

    private fun story(id: Int) = Story("Story $id", id, false, false)
}
