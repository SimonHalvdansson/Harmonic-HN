package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.HttpStatusException
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StorySearchStoreTest {
    @Test
    fun paginationUsesUnfilteredCountAndKeepsResultsWhileLoadingMore() = runTest {
        val more = CompletableDeferred<List<Story>>()
        val limits = mutableListOf<Int>()
        val store = store(backgroundScope, search = { url ->
            val limit = Url(url).parameters["hitsPerPage"]!!.toInt()
            limits += limit
            if (limit == 200) (1..200).map(::story) else more.await()
        }, filter = { it.id != 1 })

        store.search("Kotlin")
        runCurrent()
        assertEquals(listOf(1), store.state.value.stories.map(Story::id))
        assertTrue(store.state.value.canLoadMore)

        store.loadMore()
        store.loadMore()
        runCurrent()
        assertTrue(store.state.value.loadingMore)
        assertEquals(listOf(1), store.state.value.stories.map(Story::id))
        assertEquals(listOf(200, 400), limits)

        more.complete(listOf(story(1)))
        runCurrent()
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.loadingMore)
        assertFalse(store.state.value.canLoadMore)
        store.loadMore()
        runCurrent()
        assertEquals(listOf(200, 400), limits)
    }

    @Test
    fun loadMoreFailurePreservesResultsAndUsesSharedFailurePolicy() = runTest {
        val store = store(backgroundScope, search = { url ->
            if (Url(url).parameters["hitsPerPage"] == "200") (1..200).map(::story)
            else throw HttpStatusException(429, "Too many requests", url)
        })
        store.search("Kotlin")
        runCurrent()
        val original = store.state.value.stories

        store.loadMore()
        runCurrent()

        assertEquals(original, store.state.value.stories)
        assertEquals(StoryLoadFailure.RATE_LIMITED, store.state.value.failure)
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.loadingMore)
    }

    @Test
    fun restoringStateRejectsAnOlderResponseEvenIfItDoesNotCooperateWithCancellation() = runTest {
        lateinit var pending: Continuation<List<Story>>
        val store = store(backgroundScope, search = { suspendCoroutine { pending = it } })
        store.search("Old query")
        runCurrent()

        store.restore(
            mode = StorySearchMode.QUERY,
            query = "Restored query",
            stories = listOf(story(42)),
            hitsPerPage = 200,
            topStoriesStartTime = 0,
            options = StorySearchOptions(),
            canLoadMore = false,
            failure = null,
        )
        val restored = store.state.value
        assertEquals("Restored query", restored.query)
        assertEquals(listOf(42), restored.stories.map(Story::id))
        pending.resume(listOf(story(1)))
        runCurrent()

        assertEquals(restored, store.state.value)
    }

    @Test
    fun startingANewQueryRejectsAnOlderResponse() = runTest {
        lateinit var pending: Continuation<List<Story>>
        val store = store(backgroundScope, search = { url ->
            if (Url(url).parameters["query"] == "old") suspendCoroutine { pending = it }
            else listOf(story(42))
        })
        store.search("old")
        runCurrent()
        store.search("new")
        runCurrent()
        val current = store.state.value
        pending.resume(listOf(story(1)))
        runCurrent()

        assertEquals(current, store.state.value)
        assertEquals("new", current.query)
        assertEquals(listOf(42), current.stories.map(Story::id))
    }

    @Test
    fun historyRequestsAreBoundedAndCancellationReleasesTheSlots() = runTest {
        val response = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        val requested = mutableListOf<Int>()
        val store = store(backgroundScope, clickedIds = (1..30).toList(), getStory = { id ->
            requested += id
            active++
            peak = maxOf(peak, active)
            try {
                response.await()
                story(id)
            } finally {
                active--
            }
        })
        store.toggleOnlyClicked()
        store.search("")
        runCurrent()
        assertTrue(active in 1..8, "Expected at most eight active requests, found $active")
        assertEquals(active, requested.size)

        store.cancel()
        runCurrent()
        assertEquals(0, active)
        assertFalse(store.state.value.loading)
        assertNull(store.state.value.failure)

        response.complete(Unit)
        store.search("")
        runCurrent()
        assertEquals((1..30).toList(), store.state.value.stories.map(Story::id))
        assertTrue(peak <= 8)
    }

    @Test
    fun historySearchKeepsPartialResultsAndReportsOnlyCompleteFailure() = runTest {
        val store = store(backgroundScope, clickedIds = listOf(1, 2, 3), getStory = { id ->
            when (id) {
                1 -> story(id)
                2 -> null
                else -> error("Network unavailable")
            }
        })
        store.toggleOnlyClicked()
        store.search("")
        runCurrent()
        assertEquals(listOf(1), store.state.value.stories.map(Story::id))
        assertTrue(store.state.value.stories.single().clicked)
        assertNull(store.state.value.failure)
        assertFalse(store.state.value.canLoadMore)

        val failed = store(backgroundScope, clickedIds = listOf(1, 2), getStory = {
            error("Network unavailable")
        })
        failed.toggleOnlyClicked()
        failed.search("")
        runCurrent()
        assertEquals(StoryLoadFailure.GENERAL, failed.state.value.failure)
        assertFalse(failed.state.value.loading)
    }

    @Test
    fun newestHistoryResultsUsePublicationTimeInsteadOfVisitOrder() = runTest {
        val store = store(backgroundScope, clickedIds = listOf(1, 2, 3), getStory = { id ->
            story(id).apply { time = listOf(100, 300, 200)[id - 1] }
        })
        store.toggleOnlyClicked()
        store.selectSort(1)
        store.search("")
        runCurrent()

        assertEquals(listOf(2, 3, 1), store.state.value.stories.map(Story::id))
    }

    @Test
    fun topStoriesKeepTheirTimeWindowWhenLoadingMore() = runTest {
        val urls = mutableListOf<Url>()
        val store = store(backgroundScope, search = { url ->
            urls += Url(url)
            (1..200).map(::story)
        })
        store.loadTopStories(StoryType.LAST_WEEK, startTime = 123)
        runCurrent()
        store.loadMore()
        runCurrent()

        assertEquals(listOf("200", "400"), urls.map { it.parameters["hitsPerPage"] })
        assertEquals(listOf("created_at_i>123", "created_at_i>123"), urls.map { it.parameters["numericFilters"] })
        assertEquals(StorySearchMode.TOP_STORIES, store.state.value.mode)
        assertEquals(123, store.state.value.topStoriesStartTime)
        assertFalse(store.state.value.canLoadMore)
    }

    private fun store(
        scope: CoroutineScope,
        search: suspend (String) -> List<Story> = { error("Unexpected Algolia request") },
        clickedIds: List<Int> = emptyList(),
        getStory: suspend (Int) -> Story? = { error("Unexpected story request") },
        filter: (Story) -> Boolean = { false },
    ) = StorySearchStore(
        scope = scope,
        algoliaRepository = object : AlgoliaRepository {
            override suspend fun search(url: String) = search.invoke(url)
            override suspend fun getSubmissions(userName: String, limit: Int): List<Story> = error("Not used")
            override suspend fun getItemJson(id: Int): String = error("Not used")
        },
        hackerNewsRepository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = getStory.invoke(id)
            override suspend fun getComment(id: Int): Comment? = error("Not used")
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
        },
        clickedStoryIds = { clickedIds },
        isStoryClicked = { false },
        shouldFilterStory = filter,
        shouldHideClickedStories = { false },
    )

    private fun story(id: Int) = Story("Kotlin", id, true, false)
}
