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
    fun cachedLabelFollowsRetainedContentUntilSuccessfulReplacement() {
        val store = StoryListStore()
        store.replace(listOf(story(1)), showingCached = true)
        store.beginLoad(refreshing = true)
        assertTrue(store.state.value.showingCached)
        assertEquals(listOf(1), store.state.value.items.map { it.story.id })

        store.fail(StoryLoadFailure.GENERAL)
        assertTrue(store.state.value.showingCached)
        store.beginLoad(refreshing = true)
        store.replace(listOf(story(2)))
        assertFalse(store.state.value.showingCached)
        assertEquals(listOf(2), store.state.value.items.map { it.story.id })

        store.replace(listOf(story(1)), showingCached = true)
        store.beginLoad(refreshing = false, clearItems = true)
        assertFalse(store.state.value.showingCached)
        assertTrue(store.state.value.items.isEmpty())
    }

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
            previewImageUrlResolved = true
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
    fun historySynchronizationUpdatesReadStateWithoutPlatformCallbacks() {
        val store = StoryListStore()
        store.replace(listOf(story(1), story(2).also { it.isRead = true }))

        val result = store.syncHistory(
            readStoryIds = setOf(1),
            searchingOnlyRead = false,
            showingHistory = false,
            hideRead = false,
        )

        assertEquals(StoryHistorySyncResult.CONTENT_CHANGED, result)
        assertTrue(store.stories.single { it.id == 1 }.isRead)
        assertFalse(store.stories.single { it.id == 2 }.isRead)
    }

    @Test
    fun historySynchronizationOwnsHideReadRemovalAndRefreshDecision() {
        val store = StoryListStore()
        store.replace(listOf(story(1), story(2), story(3)))

        assertEquals(
            StoryHistorySyncResult.ITEMS_REMOVED,
            store.syncHistory(
                readStoryIds = setOf(1, 3),
                searchingOnlyRead = false,
                showingHistory = false,
                hideRead = true,
            ),
        )
        assertEquals(listOf(2), store.stories.map(Story::id))
        assertEquals(
            StoryHistorySyncResult.REFRESH_REQUIRED,
            store.syncHistory(
                readStoryIds = emptySet(),
                searchingOnlyRead = false,
                showingHistory = false,
                hideRead = true,
            ),
        )
    }

    @Test
    fun onlyReadSearchClearsTransientReadStyling() {
        val store = StoryListStore()
        store.replace(listOf(story(1).also { it.isRead = true }))

        assertEquals(
            StoryHistorySyncResult.CONTENT_CHANGED,
            store.syncHistory(
                readStoryIds = setOf(1),
                searchingOnlyRead = true,
                showingHistory = false,
                hideRead = false,
            ),
        )
        assertFalse(store.stories.single().isRead)
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
        store.updateStory(2) { title = "Updated" }

        val after = store.state.value.items
        assertSame(before[0], after[0])
        assertNotSame(before[1], after[1])
        assertEquals("Updated", after[1].title)
        assertSame(before[2], after[2])
    }

    @Test
    fun markingReadPublishesWithoutMutatingPreviouslyPublishedSnapshots() {
        val store = StoryListStore()
        store.replace(listOf(story(1), story(2)))
        val before = store.state.value

        assertTrue(store.markRead(1, true))

        assertFalse(before.items[0].isRead)
        assertTrue(store.state.value.items[0].isRead)
        assertSame(before.items[1], store.state.value.items[1])
        val current = store.state.value
        assertFalse(store.markRead(99, true))
        assertSame(current, store.state.value)
    }

    private fun story(id: Int, loaded: Boolean = true) = Story("Story $id", id, loaded, false)
}
