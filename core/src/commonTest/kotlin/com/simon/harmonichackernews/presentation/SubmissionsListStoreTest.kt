package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionCount
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import com.simon.harmonichackernews.network.AlgoliaSubmissionsPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionsListStoreTest {
    @Test
    fun initiallyLoadsAllDirectlyAndFetchesOnlyMetadataForCategoryTotals() = runTest {
        val repository = FakeRepository((500 downTo 1).map { item(it, comment = it > 200) })
        val store = SubmissionsListStore("simon", repository)
        store.ensureLoaded()
        assertEquals((500 downTo 401).toList(), store.ids())
        assertEquals(AlgoliaSubmissionCount(200, true), store.state.value.storyCount)
        assertEquals(AlgoliaSubmissionCount(300, true), store.state.value.commentCount)
        assertEquals(1, repository.requests.count { it.pageSize == 100 })
        assertEquals(setOf(AlgoliaSubmissionType.STORIES, AlgoliaSubmissionType.COMMENTS),
            repository.requests.filter { it.pageSize == 0 }.map { it.type }.toSet())
        store.loadMore()
        assertEquals((500 downTo 301).toList(), store.ids())
        assertEquals(Request(AlgoliaSubmissionType.BOTH, 100, AlgoliaSubmissionsCursor(page = 1)), repository.requests.last())
    }

    @Test
    fun filtersHaveIndependentPagesAndReuseObjectsWhenSwitchingBack() = runTest {
        val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        val originalBoth = store.ids()
        val sharedStory = store.state.value.items.first()
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        assertEquals(listOf(12, 10, 8, 6), store.ids())
        assertSame(sharedStory, store.state.value.items.first())
        store.loadMore()
        assertEquals(listOf(12, 10, 8, 6, 4, 2), store.ids())
        assertFalse(store.state.value.canLoadMore)
        val requests = repository.requests.size
        store.selectFilter(SubmissionFilter.BOTH)
        store.ensureLoaded()
        assertEquals(originalBoth, store.ids())
        assertEquals(requests, repository.requests.size)
        store.loadMore()
        assertEquals((12 downTo 5).toList(), store.ids())
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        assertEquals(listOf(12, 10, 8, 6, 4, 2), store.ids())
    }

    @Test
    fun initialFailureCanBeRetriedAndCountFailureDoesNotBlockContent() = runTest {
        val repository = FakeRepository(listOf(item(2), item(1, comment = true))).apply { fail = true }
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        assertTrue(store.state.value.loadingFailed)
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.loadedSuccessfully)
        repository.fail = false
        repository.failCounts = true
        store.retry()
        assertEquals(listOf(2, 1), store.ids())
        assertEquals(null, store.state.value.storyCount)
        assertTrue(store.state.value.loadedSuccessfully)
        assertFalse(store.state.value.loadingFailed)
        repository.failCounts = false
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        assertEquals(AlgoliaSubmissionCount(1, true), store.state.value.storyCount)
        assertEquals(AlgoliaSubmissionCount(1, true), store.state.value.commentCount)
    }

    @Test
    fun failedPageRetriesTheSameCursorAndRefreshInvalidatesOtherFiltersAndCounts() = runTest {
        val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        val previous = store.ids()
        repository.fail = true
        store.loadMore()
        val failedRequest = repository.requests.last()
        assertEquals(previous, store.ids())
        assertTrue(store.state.value.canLoadMore)
        assertTrue(store.state.value.loadingFailed)
        repository.fail = false
        store.retry()
        assertEquals(failedRequest, repository.requests.last())
        assertFalse(store.state.value.loadingFailed)
        repository.items = listOf(item(20), item(19, comment = true))
        store.refresh()
        assertEquals(listOf(20), store.ids())
        assertEquals(AlgoliaSubmissionCount(1, true), store.state.value.commentCount)
        store.selectFilter(SubmissionFilter.BOTH)
        store.ensureLoaded()
        assertEquals(listOf(20, 19), store.ids())
    }

    @Test
    fun failedRefreshPreservesContentCountsAndPagination() = runTest {
        val repository = FakeRepository((5 downTo 1).map { item(it) })
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        repository.fail = true
        store.refresh()
        assertEquals(listOf(5, 4, 3, 2), store.ids())
        assertEquals(AlgoliaSubmissionCount(5, true), store.state.value.storyCount)
        assertTrue(store.state.value.canLoadMore)
        repository.fail = false
        store.retry()
        assertEquals(listOf(5, 4, 3, 2), store.ids())
        assertFalse(store.state.value.loadingFailed)
    }

    @Test
    fun staleSuccessOrFailureCannotReplaceNewFilterState() = runTest {
        for (fail in listOf(false, true)) {
            val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
            val store = SubmissionsListStore("simon", repository, pageSize = 4)
            store.ensureLoaded()
            store.selectFilter(SubmissionFilter.COMMENTS)
            store.ensureLoaded()
            store.selectFilter(SubmissionFilter.BOTH)
            val gate = CompletableDeferred<Unit>()
            repository.gate = gate
            repository.fail = fail
            val load = async { store.loadMore() }
            runCurrent()
            store.selectFilter(SubmissionFilter.COMMENTS)
            gate.complete(Unit)
            load.await()
            assertEquals(listOf(11, 9, 7, 5), store.ids())
            assertFalse(store.state.value.loading)
            assertFalse(store.state.value.loadingFailed)
            store.selectFilter(SubmissionFilter.BOTH)
            assertEquals((12 downTo 9).toList(), store.ids())
        }
    }

    @Test
    fun zeroAndExactPageSizedCategoriesHaveNoLoadMore() = runTest {
        for (items in listOf(emptyList(), listOf(item(2, true), item(1, true)))) {
            val repository = FakeRepository(items)
            val store = SubmissionsListStore("simon", repository, pageSize = 2)
            store.ensureLoaded()
            for (filter in SubmissionFilter.entries) {
                store.selectFilter(filter)
                store.ensureLoaded()
                assertFalse(store.state.value.canLoadMore)
            }
            assertEquals(AlgoliaSubmissionCount(0, true), store.state.value.storyCount)
            assertEquals(AlgoliaSubmissionCount(items.size, true), store.state.value.commentCount)
            store.selectFilter(SubmissionFilter.STORIES)
            assertTrue(store.state.value.items.isEmpty())
            assertEquals("No stories", store.state.value.emptyText)
        }
    }

    @Test
    fun finalPageUpdatesContentAndLoadingStateTogether() = runTest {
        val repository = FakeRepository(listOf(item(3), item(2), item(1)))
        val store = SubmissionsListStore("simon", repository, pageSize = 2)
        store.ensureLoaded()
        repository.gate = CompletableDeferred()
        val load = async { store.loadMore() }
        runCurrent()
        assertTrue(store.state.value.loading)
        assertEquals(listOf(3, 2), store.ids())
        repository.gate!!.complete(Unit)
        load.await()
        assertEquals(listOf(3, 2, 1), store.ids())
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.canLoadMore)
    }

    private data class Request(val type: AlgoliaSubmissionType, val pageSize: Int, val cursor: AlgoliaSubmissionsCursor)

    private class FakeRepository(var items: List<Story>) : AlgoliaRepository {
        val requests = mutableListOf<Request>()
        var fail = false
        var failCounts = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType, cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage {
            requests += Request(type, pageSize, cursor)
            gate?.await()
            if (fail || (failCounts && pageSize == 0)) error("offline")
            val filtered = items.filter {
                when (type) {
                    AlgoliaSubmissionType.BOTH -> true
                    AlgoliaSubmissionType.STORIES -> !it.isComment
                    AlgoliaSubmissionType.COMMENTS -> it.isComment
                }
            }
            val page = filtered.drop(cursor.page * pageSize).take(pageSize)
            return AlgoliaSubmissionsPage(
                // Fresh instances verify object sharing across filters and overlapping pages.
                items = page.map { original -> item(original.id, original.isComment) },
                nextCursor = if (pageSize > 0 && (cursor.page + 1) * pageSize < filtered.size) {
                    AlgoliaSubmissionsCursor(page = cursor.page + 1)
                } else null,
                totalCount = AlgoliaSubmissionCount(filtered.size, true),
            )
        }
        override suspend fun search(url: String): List<Story> = error("Not used")
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private fun SubmissionsListStore.ids() = state.value.items.map(Story::id)

    private companion object {
        fun item(id: Int, comment: Boolean = false) = Story().also {
            it.id = id
            it.createdAtEpochSeconds = id
            it.loaded = true
            it.isComment = comment
        }
    }
}
