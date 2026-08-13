package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureStateBoundaryTest {
    @Test
    fun storiesDerivedStateFollowsTheActiveImmutableList() {
        val main = PortableStoryListState(
            items = (1..5).map(::story),
            visibleStoryCount = 2,
            paginationEnabled = true,
        )
        val search = PortableStoryListState(items = listOf(story(10)))

        val mainState = StoriesState(mainList = main, searchList = search)
        assertEquals(2, mainState.activeVisibleItemCount)
        assertTrue(mainState.activeHasLoadMore)

        val searchState = mainState.copy(searching = true)
        assertEquals(listOf(10), searchState.activeItems.map { it.id })
        assertEquals(1, searchState.activeVisibleItemCount)
        assertFalse(searchState.activeHasLoadMore)
    }

    @Test
    fun commentsStateCarriesOnlySnapshotStoryData() {
        val snapshot = story(7)
        val state = CommentsState(story = snapshot)

        assertEquals(7, state.story?.id)
        assertTrue(state.story?.loaded == true)
        assertTrue(state.thread.displayedComments.isEmpty())
    }

    private fun story(id: Int) = StoryListItemSnapshot(
        story = StorySnapshot(id = id, title = "Story $id"),
        presentation = StoryPresentationSnapshot(loaded = true),
    )
}
