package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.simon.harmonichackernews.data.Story

class StoriesCoordinatorPolicyTest {
    @Test
    fun frontPageDateDefaultsToYesterdayAndFormatsUtc() {
        val state = FrontPageDayState(
            restoredMillis = -1L,
            nowMillis = 1_704_153_600_000L, // 2024-01-02T00:00:00Z
        )

        assertEquals("2024-01-01", state.requestParameter)
    }

    @Test
    fun frontPageDateClampsAtBothArchiveBounds() {
        val state = FrontPageDayState(-1L, 1_704_153_600_000L)

        assertFalse(state.shift(1))
        assertTrue(state.select(0L))
        assertEquals("2007-02-19", state.requestParameter)
        assertFalse(state.shift(-1))
    }

    @Test
    fun savedListMessagesDistinguishEmptySourceFromFilteredResults() {
        assertEquals(
            "No favorites",
            SavedListPresentationPolicy.emptyMessage(
                SavedListKind.FAVORITES,
                SavedItemFilter.STORIES,
                sourceHasItems = false,
            ),
        )
        assertEquals(
            "No favorite stories",
            SavedListPresentationPolicy.emptyMessage(
                SavedListKind.FAVORITES,
                SavedItemFilter.STORIES,
                sourceHasItems = true,
            ),
        )
        assertEquals(
            "No bookmarked comments",
            SavedListPresentationPolicy.emptyMessage(
                SavedListKind.BOOKMARKS,
                SavedItemFilter.COMMENTS,
                sourceHasItems = true,
            ),
        )
    }

    @Test
    fun savedItemReconciliationPreservesLoadedInstancesAndCreatesPlaceholders() {
        val loaded = Story("Loaded", 2, false, true)

        val result = SavedItemStoryReconciler.reconcile(
            currentStories = listOf(loaded),
            currentCommentIds = emptySet(),
            itemIds = listOf(3, 2),
            commentIds = setOf(3),
        )

        assertTrue(result.changed)
        assertEquals(listOf(3, 2), result.stories.map { it.id })
        assertTrue(result.stories[0].isComment)
        assertTrue(result.stories[1] === loaded)
    }

    @Test
    fun storyRowMergeUpdatesOnlyMatchingSummaryFields() {
        val target = Story("Old", 7, false, true).apply { score = 1 }
        val source = Story("New", 7, false, true).apply {
            score = 2
            descendants = 4
            url = "https://example.com"
        }

        assertTrue(StoryRowMergePolicy.mergeSummaryFields(target, source))
        assertEquals("New", target.title)
        assertEquals(2, target.score)
        assertEquals(4, target.descendants)
        assertEquals("https://example.com", target.url)
    }

    @Test
    fun shellPresentationDistinguishesSearchSavedLoadingAndCacheStates() {
        val search = StoriesShellPresentationPolicy.present(
            shellInput(
                searching = true,
                submittedSearch = true,
                storyCount = 0,
            ),
        )
        val filteredSaved = StoriesShellPresentationPolicy.present(
            shellInput(
                storyCount = 0,
                userItems = true,
            ),
        )
        val onlineStories = StoriesShellPresentationPolicy.present(
            shellInput(
                storyCount = 3,
                visibleStoryCount = 3,
            ),
        )

        assertTrue(search.showEmptySearch)
        assertFalse(search.showLoading)
        assertTrue(filteredSaved.showEmptySavedList)
        assertTrue(onlineStories.canCacheStories)
    }

    @Test
    fun shellFailureMessagePrioritizesRateLimitThenConnectivity() {
        assertEquals(
            "Rate limited",
            StoriesShellPresentationPolicy.present(
                shellInput(rateLimited = true, online = false),
            ).loadingFailureMessage,
        )
        assertEquals(
            "No internet connection",
            StoriesShellPresentationPolicy.present(
                shellInput(online = false),
            ).loadingFailureMessage,
        )
    }

    private fun shellInput(
        searching: Boolean = false,
        submittedSearch: Boolean = false,
        storyCount: Int = 1,
        searchLoading: Boolean = false,
        loadingFailed: Boolean = false,
        notFound: Boolean = false,
        rateLimited: Boolean = false,
        online: Boolean = true,
        bookmarks: Boolean = false,
        history: Boolean = false,
        userItems: Boolean = false,
        userItemsInitialLoadInProgress: Boolean = false,
        refreshIndicatorShowing: Boolean = false,
        showingCached: Boolean = false,
        cacheInProgress: Boolean = false,
        visibleStoryCount: Int = 0,
    ) = StoriesShellPresentationInput(
        searching = searching,
        submittedSearch = submittedSearch,
        storyCount = storyCount,
        searchLoading = searchLoading,
        loadingFailed = loadingFailed,
        notFound = notFound,
        rateLimited = rateLimited,
        online = online,
        bookmarks = bookmarks,
        history = history,
        userItems = userItems,
        userItemsInitialLoadInProgress = userItemsInitialLoadInProgress,
        refreshIndicatorShowing = refreshIndicatorShowing,
        showingCached = showingCached,
        cacheInProgress = cacheInProgress,
        visibleStoryCount = visibleStoryCount,
    )
}
