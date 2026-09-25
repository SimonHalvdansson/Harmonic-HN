package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StorySearchController
import com.simon.harmonichackernews.network.AlgoliaSearchPage
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import com.simon.harmonichackernews.network.AlgoliaSubmissionsPage
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
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StorySearchStoreTest {
    @Test
    fun paginationUsesMetadataAndKeepsResultsWhileLoadingMore() = runTest {
        val more = CompletableDeferred<AlgoliaSearchPage>()
        val urls = mutableListOf<Url>()
        val store = store(backgroundScope, search = { url ->
            urls += Url(url)
            if (urls.size == 1) page(0, 2, 1, 2, 3) else more.await()
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
        assertEquals(listOf("200", "200"), urls.map { it.parameters["hitsPerPage"] })
        assertEquals(listOf("0", "1"), urls.map { it.parameters["page"] })
        more.complete(page(1, 2, 4))
        runCurrent()
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.loadingMore)
        assertFalse(store.state.value.canLoadMore)
        store.loadMore()
        runCurrent()
        assertEquals(2, urls.size)
    }

    @Test
    fun failureRetainsContentAndRetriesTheSamePageThroughEitherAction() = runTest {
        val requested = mutableListOf<Int>()
        var fail = true
        val store = store(backgroundScope, search = { url ->
            val requestedPage = Url(url).parameters["page"]!!.toInt()
            requested += requestedPage
            if (requestedPage == 1 && fail) throw HttpStatusException(429, "Too many requests", url)
            page(requestedPage, 3, requestedPage + 1)
        })
        store.search("Kotlin")
        runCurrent()
        val original = store.state.value.stories
        store.loadMore()
        runCurrent()
        assertEquals(original, store.state.value.stories)
        assertEquals(StoryLoadFailure.RATE_LIMITED, store.state.value.failure)
        assertTrue(store.state.value.canLoadMore)
        assertFalse(store.state.value.loading)
        store.retry()
        runCurrent()
        fail = false
        store.loadMore()
        runCurrent()
        assertEquals(listOf(0, 1, 1, 1), requested)
        assertEquals(listOf(1, 2), store.state.value.stories.map(Story::id))
        assertEquals(2, store.state.value.nextPage)
        assertNull(store.state.value.failure)
    }

    @Test
    fun restoringStateRejectsAnOlderResponseEvenIfItDoesNotCooperateWithCancellation() = runTest {
        lateinit var pending: Continuation<AlgoliaSearchPage>
        val store = store(backgroundScope, search = { suspendCoroutine { pending = it } })
        store.search("Old query")
        runCurrent()

        store.restore(
            mode = StorySearchMode.QUERY,
            query = "Restored query",
            stories = listOf(story(42)),
            nextPage = 1,
            topStoriesStartTime = 0,
            options = StorySearchOptions(),
            canLoadMore = false,
            failure = null,
        )
        val restored = store.state.value
        assertEquals("Restored query", restored.query)
        assertEquals(listOf(42), restored.stories.map(Story::id))
        pending.resume(page(0, 2, 1))
        runCurrent()

        assertEquals(restored, store.state.value)
    }

    @Test
    fun startingANewQueryRejectsAnOlderResponse() = runTest {
        lateinit var pending: Continuation<AlgoliaSearchPage>
        val store = store(backgroundScope, search = { url ->
            if (Url(url).parameters["query"] == "old") suspendCoroutine { pending = it }
            else page(0, 1, 42)
        })
        store.search("old")
        runCurrent()
        store.search("new")
        runCurrent()
        val current = store.state.value
        pending.resume(page(0, 2, 1))
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
        val store = store(backgroundScope, readIds = (1..30).toList(), getStory = { id ->
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
        store.toggleOnlyRead()
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
        val store = store(backgroundScope, readIds = listOf(1, 2, 3), getStory = { id ->
            when (id) {
                1 -> story(id)
                2 -> null
                else -> error("Network unavailable")
            }
        })
        store.toggleOnlyRead()
        store.search("")
        runCurrent()
        assertEquals(listOf(1), store.state.value.stories.map(Story::id))
        assertTrue(store.state.value.stories.single().isRead)
        assertNull(store.state.value.failure)
        assertFalse(store.state.value.canLoadMore)

        val failed = store(backgroundScope, readIds = listOf(1, 2), getStory = {
            error("Network unavailable")
        })
        failed.toggleOnlyRead()
        failed.search("")
        runCurrent()
        assertEquals(StoryLoadFailure.GENERAL, failed.state.value.failure)
        assertFalse(failed.state.value.loading)
    }

    @Test
    fun newestHistoryResultsUsePublicationTimeInsteadOfVisitOrder() = runTest {
        val store = store(backgroundScope, readIds = listOf(1, 2, 3), getStory = { id ->
            story(id).apply { createdAtEpochSeconds = listOf(100, 300, 200)[id - 1] }
        })
        store.toggleOnlyRead()
        store.selectSort(1)
        store.search("")
        runCurrent()

        assertEquals(listOf(2, 3, 1), store.state.value.stories.map(Story::id))
    }

    @Test
    fun topStoriesKeepTheirTimeWindowAndDeduplicateShiftingRanks() = runTest {
        val urls = mutableListOf<Url>()
        val original = story(2)
        val store = store(backgroundScope, search = { url ->
            urls += Url(url)
            if (urls.size == 1) AlgoliaSearchPage(listOf(story(1), original), 0, 2)
            else page(1, 2, 2, 3, 3)
        })
        store.loadTopStories(StoryType.LAST_WEEK, startTime = 123)
        runCurrent()
        original.title = "Locally retained content"
        store.loadMore()
        runCurrent()
        assertEquals(listOf("200", "200"), urls.map { it.parameters["hitsPerPage"] })
        assertEquals(listOf("0", "1"), urls.map { it.parameters["page"] })
        assertEquals(listOf("created_at_i>123", "created_at_i>123"), urls.map { it.parameters["numericFilters"] })
        assertEquals(listOf(1, 2, 3), store.state.value.stories.map(Story::id))
        assertSame(original, store.state.value.stories[1])
        assertEquals("Locally retained content", store.state.value.stories[1].title)
        assertEquals(StorySearchMode.TOP_STORIES, store.state.value.mode)
        assertEquals(123, store.state.value.topStoriesStartTime)
        assertFalse(store.state.value.canLoadMore)
    }

    @Test
    fun filteredEmptyAndDuplicateOnlyPagesDoNotPrematurelyEndPagination() = runTest {
        val requested = mutableListOf<Int>()
        val store = store(backgroundScope, search = { url ->
            val n = Url(url).parameters["page"]!!.toInt()
            requested += n
            when (n) {
                0 -> page(n, 5, 1, 2)
                1 -> page(n, 5, 2, 3) // Entirely filtered.
                2 -> page(n, 5) // No mapped stories, but more pages exist.
                3 -> page(n, 5, 1) // Live rank shifted an already displayed ID.
                else -> page(n, 5, 4)
            }
        }, filter = { it.id == 2 || it.id == 3 })
        store.search("fixture")
        runCurrent()
        repeat(4) {
            assertTrue(store.state.value.canLoadMore)
            store.loadMore()
            runCurrent()
        }
        assertEquals((0..4).toList(), requested)
        assertEquals(listOf(1, 4), store.state.value.stories.map(Story::id))
        assertFalse(store.state.value.canLoadMore)
    }

    @Test
    fun refreshStartsAtZeroAndAcceptsUpdatedRankingAndContent() = runTest {
        val requested = mutableListOf<Int>()
        var refreshed = false
        val store = store(backgroundScope, search = { url ->
            val n = Url(url).parameters["page"]!!.toInt()
            requested += n
            if (refreshed) page(n, 2, 2, 1) else page(n, 2, n + 1)
        })
        store.search("fixture")
        runCurrent()
        store.loadMore()
        runCurrent()
        refreshed = true
        store.search("fixture")
        runCurrent()
        assertEquals(listOf(0, 1, 0), requested)
        assertEquals(listOf(2, 1), store.state.value.stories.map(Story::id))
        assertEquals(1, store.state.value.nextPage)
    }

    @Test
    fun refreshRejectsAnOutstandingPageWithoutAdvancingTheNewCursor() = runTest {
        lateinit var pending: Continuation<AlgoliaSearchPage>
        var refreshed = false
        val store = store(backgroundScope, search = { url ->
            if (Url(url).parameters["page"] == "1") suspendCoroutine { pending = it }
            else if (refreshed) page(0, 3, 42) else page(0, 3, 1)
        })
        store.search("fixture")
        runCurrent()
        store.loadMore()
        runCurrent()
        refreshed = true
        store.search("fixture")
        runCurrent()
        val current = store.state.value
        pending.resume(page(1, 3, 2))
        runCurrent()
        assertEquals(current, store.state.value)
        assertEquals(listOf(42), current.stories.map(Story::id))
        assertEquals(1, current.nextPage)
    }

    @Test
    fun localReadAndHiddenChangesApplyToRetainedRowsAndNewRows() = runTest {
        val read = mutableSetOf<Int>()
        val hidden = mutableSetOf<Int>()
        val store = store(backgroundScope, search = { url ->
            val n = Url(url).parameters["page"]!!.toInt()
            if (n == 0) page(n, 2, 1, 2) else page(n, 2, 1, 2, 3, 4)
        }, filter = { it.id in hidden }, read = { it in read }, hideRead = { true })
        store.search("fixture")
        runCurrent()
        read += listOf(1, 3)
        hidden += 2
        store.loadMore()
        runCurrent()
        assertEquals(listOf(4), store.state.value.stories.map(Story::id))
    }

    @Test
    fun newlyUnreadOrUnhiddenRecordsReturnInTheirOriginalOrderWithoutRedownloading() = runTest {
        val read = mutableSetOf(1)
        val hidden = mutableSetOf(2)
        val requested = mutableListOf<Int>()
        val store = store(backgroundScope, search = { url ->
            val n = Url(url).parameters["page"]!!.toInt()
            requested += n
            if (n == 0) page(n, 2, 1, 2, 3) else page(n, 2, 4)
        }, filter = { it.id in hidden }, read = { it in read }, hideRead = { true })
        store.search("fixture")
        runCurrent()
        assertEquals(listOf(3), store.state.value.stories.map(Story::id))
        read.clear()
        hidden.clear()
        store.loadMore()
        runCurrent()
        assertEquals(listOf(0, 1), requested)
        assertEquals(listOf(1, 2, 3, 4), store.state.value.stories.map(Story::id))
    }

    @Test
    fun searchOptionsFreezeTheDateBoundaryAcrossPagesAndRefreshResetsIt() = runTest {
        var now = 1_700_000_000L
        val controller = StorySearchController(object : Clock {
            override fun now() = Instant.fromEpochSeconds(now)
        })
        val urls = mutableListOf<Url>()
        val store = store(backgroundScope, controller = controller, search = { url ->
            val parsed = Url(url)
            urls += parsed
            val n = parsed.parameters["page"]!!.toInt()
            page(n, 3, n + 1)
        })
        store.selectSort(1)
        store.selectDateRange(1)
        store.selectMinimumPoints(2)
        store.selectMinimumComments(3)
        store.search("C++ & Kotlin")
        runCurrent()
        now += 3600
        store.loadMore()
        runCurrent()
        assertEquals(urls[0].parameters["numericFilters"], urls[1].parameters["numericFilters"])
        assertEquals("/api/v1/search_by_date", urls[1].encodedPath)
        assertEquals("C++ & Kotlin", urls[1].parameters["query"])
        assertEquals("min", urls[1].parameters["typoTolerance"])
        assertTrue(urls[1].parameters["numericFilters"]!!.contains("points>=25,num_comments>=100"))
        store.search("C++ & Kotlin")
        runCurrent()
        assertEquals("created_at_i>=${now - 86400},points>=25,num_comments>=100", urls.last().parameters["numericFilters"])
    }

    @Test
    fun optionsAndFeedChangesRejectNonCooperativeOldPages() = runTest {
        lateinit var pending: Continuation<AlgoliaSearchPage>
        val store = store(backgroundScope, search = { url ->
            if (Url(url).parameters["query"] == "old") suspendCoroutine { pending = it }
            else page(0, 1, 42)
        })
        store.search("old")
        runCurrent()
        store.selectSort(1)
        val canceled = store.state.value
        pending.resume(page(0, 2, 1))
        runCurrent()
        assertEquals(canceled, store.state.value)
        store.search("old")
        runCurrent()
        store.loadTopStories(StoryType.LAST_24_HOURS, startTime = 123)
        runCurrent()
        val top = store.state.value
        pending.resume(page(0, 2, 1))
        runCurrent()
        assertEquals(top, store.state.value)
        assertEquals(listOf(42), top.stories.map(Story::id))
    }

    @Test
    fun cancelStopsTheActiveRequestAndPreventsFurtherPages() = runTest {
        var canceled = false
        val pending = CompletableDeferred<AlgoliaSearchPage>()
        val store = store(backgroundScope, search = {
            try { pending.await() } finally { canceled = true }
        })
        store.search("fixture")
        runCurrent()
        store.cancel(clearResults = true)
        runCurrent()
        assertTrue(canceled)
        assertEquals(StorySearchMode.NONE, store.state.value.mode)
        assertFalse(store.state.value.canLoadMore)
        assertFalse(store.state.value.loading)
    }

    @Test
    fun unexpectedResponsePageDoesNotAdvanceAndCanBeRetried() = runTest {
        var response = page(4, 5, 1)
        val store = store(backgroundScope, search = { response })
        store.search("fixture")
        runCurrent()
        assertEquals(StoryLoadFailure.GENERAL, store.state.value.failure)
        assertEquals(0, store.state.value.nextPage)
        response = page(0, 1, 42)
        store.retry()
        runCurrent()
        assertEquals(listOf(42), store.state.value.stories.map(Story::id))
        assertNull(store.state.value.failure)
    }

    private fun page(page: Int, count: Int, vararg ids: Int) =
        AlgoliaSearchPage(ids.map(::story), page, count)

    private fun store(
        scope: CoroutineScope,
        search: suspend (String) -> AlgoliaSearchPage = { error("Unexpected Algolia request") },
        readIds: List<Int> = emptyList(),
        getStory: suspend (Int) -> Story? = { error("Unexpected story request") },
        filter: (Story) -> Boolean = { false },
        read: (Int) -> Boolean = { false },
        hideRead: () -> Boolean = { false },
        controller: StorySearchController = StorySearchController(),
    ) = StorySearchStore(
        scope = scope,
        algoliaRepository = object : AlgoliaRepository {
            override suspend fun search(url: String) = search.invoke(url)
            override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType, cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage = error("Not used")
            override suspend fun getItemJson(id: Int): String = error("Not used")
        },
        hackerNewsRepository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = getStory.invoke(id)
            override suspend fun getComment(id: Int): Comment? = error("Not used")
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
        },
        readStoryIds = { readIds },
        isStoryRead = read,
        shouldFilterStory = filter,
        shouldHideReadStories = hideRead,
        controller = controller,
    )

    private fun story(id: Int) = Story("Kotlin", id, true, false)
}
