package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.AlgoliaSearchPage
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
    fun firstCategoryPagePreservesRowsAlreadyVisibleFromLongerAllHistory() = runTest {
        val repository = FakeRepository((300 downTo 1).map { item(it) })
        val store = SubmissionsListStore("simon", repository)
        store.ensureLoaded(); runCurrent()
        store.loadMore(); runCurrent()
        store.selectFilter(SubmissionFilter.STORIES)
        assertEquals(200, store.ids().size)
        store.ensureLoaded(); runCurrent()
        assertEquals((300 downTo 101).toList(), store.ids())
        store.loadMore(); runCurrent()
        assertEquals(200, store.ids().size)
        store.loadMore(); runCurrent()
        assertEquals((300 downTo 1).toList(), store.ids())
    }

    @Test
    fun sparseFilterShowsCachedRowsAndKeepsPrefetchAcrossSwitches() = runTest {
        val repository = FakeRepository((200 downTo 1).map { item(it, comment = it > 100 && it != 150) })
        val store = SubmissionsListStore("simon", repository)
        store.ensureLoaded(); runCurrent()
        val sharedStory = store.state.value.items.single { !it.isComment }
        repository.gate = CompletableDeferred()
        store.prefetchSparseFilters(backgroundScope); runCurrent()
        assertFalse(store.state.value.loading)
        assertEquals(2, repository.requests.count { it.pageSize > 0 })
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded(); runCurrent()
        assertEquals(listOf(150), store.ids())
        assertTrue(store.state.value.loading)
        assertTrue(store.state.value.canLoadMore)
        assertFalse(store.state.value.showInitialLoading)
        store.selectFilter(SubmissionFilter.COMMENTS)
        assertEquals(99, store.state.value.items.size)
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        assertEquals(2, repository.requests.count { it.pageSize > 0 })
        repository.gate!!.complete(Unit); runCurrent()
        assertEquals(100, store.state.value.items.size)
        assertSame(sharedStory, store.state.value.items.first())
        assertFalse(store.state.value.loading)
        store.loadMore(); runCurrent()
        assertEquals(101, store.ids().distinct().size)
        assertFalse(store.state.value.canLoadMore)
    }

    @Test
    fun prefetchSkipsDenseCompleteAndKnownEmptyCategories() = runTest {
        for (items in listOf(
            (200 downTo 1).map { item(it, it % 2 == 0) },
            (200 downTo 1).map { item(it, true) },
            listOf(item(1)),
        )) {
            val repository = FakeRepository(items)
            val store = SubmissionsListStore("simon", repository)
            store.ensureLoaded(); runCurrent()
            store.prefetchSparseFilters(backgroundScope); runCurrent()
            assertEquals(1, repository.requests.count { it.pageSize > 0 })
        }
    }

    @Test
    fun failedPrefetchRetainsSeedRowsAndCanBeRetried() = runTest {
        val repository = FakeRepository((200 downTo 1).map { item(it, it > 100 && it != 150) })
        val store = SubmissionsListStore("simon", repository)
        store.ensureLoaded(); runCurrent()
        repository.gate = CompletableDeferred()
        store.prefetchSparseFilters(backgroundScope); runCurrent()
        store.selectFilter(SubmissionFilter.STORIES)
        repository.fail = true
        repository.gate!!.complete(Unit); runCurrent()
        assertEquals(listOf(150), store.ids())
        assertTrue(store.state.value.loadingFailed)
        assertFalse(store.state.value.loading)
        repository.fail = false
        store.retry(); runCurrent()
        assertEquals(100, store.ids().size)
        assertFalse(store.state.value.loadingFailed)
    }

    @Test
    fun refreshCancelsPrefetchAndRejectsItsOldSnapshot() = runTest {
        val repository = FakeRepository((200 downTo 1).map { item(it, it > 100 && it != 150) })
        val store = SubmissionsListStore("simon", repository)
        store.ensureLoaded(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        store.prefetchSparseFilters(backgroundScope); runCurrent()
        repository.gate = null
        repository.items = listOf(item(300))
        store.refresh(); runCurrent()
        gate.complete(Unit); runCurrent()
        store.selectFilter(SubmissionFilter.STORIES)
        assertEquals(listOf(300), store.ids())
        assertFalse(store.state.value.loading)
    }

    @Test
    fun initiallyLoadsAllDirectlyAndFetchesOnlyMetadataForCategoryTotals() = runTest {
        val repository = FakeRepository((500 downTo 1).map { item(it, comment = it > 200) })
        val store = SubmissionsListStore("simon", repository)
        store.ensureLoaded()
        runCurrent()
        assertEquals((500 downTo 401).toList(), store.ids())
        assertEquals(AlgoliaSubmissionCount(200, true), store.state.value.storyCount)
        assertEquals(AlgoliaSubmissionCount(300, true), store.state.value.commentCount)
        assertEquals(1, repository.requests.count { it.pageSize == 100 })
        assertEquals(setOf(AlgoliaSubmissionType.STORIES, AlgoliaSubmissionType.COMMENTS),
            repository.requests.filter { it.pageSize == 0 }.map { it.type }.toSet())
        store.loadMore()
        runCurrent()
        assertEquals((500 downTo 301).toList(), store.ids())
        assertEquals(Request(AlgoliaSubmissionType.BOTH, 100, AlgoliaSubmissionsCursor(page = 1)), repository.requests.last())
    }

    @Test
    fun filtersHaveIndependentPagesAndReuseObjectsWhenSwitchingBack() = runTest {
        val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        runCurrent()
        val originalBoth = store.ids()
        val sharedStory = store.state.value.items.first()
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        runCurrent()
        assertEquals(listOf(12, 10, 8, 6), store.ids())
        assertSame(sharedStory, store.state.value.items.first())
        store.loadMore()
        runCurrent()
        assertEquals(listOf(12, 10, 8, 6, 4, 2), store.ids())
        assertFalse(store.state.value.canLoadMore)
        val requests = repository.requests.size
        store.selectFilter(SubmissionFilter.BOTH)
        store.ensureLoaded()
        runCurrent()
        assertEquals(originalBoth, store.ids())
        assertEquals(requests, repository.requests.size)
        store.loadMore()
        runCurrent()
        assertEquals((12 downTo 5).toList(), store.ids())
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        runCurrent()
        assertEquals(listOf(12, 10, 8, 6, 4, 2), store.ids())
    }

    @Test
    fun initialFailureCanBeRetriedAndCountFailureDoesNotBlockContent() = runTest {
        val repository = FakeRepository(listOf(item(2), item(1, comment = true))).apply { fail = true }
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        runCurrent()
        assertTrue(store.state.value.loadingFailed)
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.loadedSuccessfully)
        repository.fail = false
        repository.failCounts = true
        store.retry()
        runCurrent()
        assertEquals(listOf(2, 1), store.ids())
        assertEquals(null, store.state.value.storyCount)
        assertTrue(store.state.value.loadedSuccessfully)
        assertFalse(store.state.value.loadingFailed)
        repository.failCounts = false
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        runCurrent()
        assertEquals(AlgoliaSubmissionCount(1, true), store.state.value.storyCount)
        assertEquals(AlgoliaSubmissionCount(1, true), store.state.value.commentCount)
    }

    @Test
    fun failedPageRetriesTheSameCursorAndRefreshInvalidatesOtherFiltersAndCounts() = runTest {
        val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        runCurrent()
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        runCurrent()
        val previous = store.ids()
        repository.fail = true
        store.loadMore()
        runCurrent()
        val failedRequest = repository.requests.last()
        assertEquals(previous, store.ids())
        assertTrue(store.state.value.canLoadMore)
        assertTrue(store.state.value.loadingFailed)
        repository.fail = false
        store.retry()
        runCurrent()
        assertEquals(failedRequest, repository.requests.last())
        assertFalse(store.state.value.loadingFailed)
        repository.items = listOf(item(20), item(19, comment = true))
        store.refresh()
        runCurrent()
        assertEquals(listOf(20), store.ids())
        assertEquals(AlgoliaSubmissionCount(1, true), store.state.value.commentCount)
        store.selectFilter(SubmissionFilter.BOTH)
        store.ensureLoaded()
        runCurrent()
        assertEquals(listOf(20, 19), store.ids())
    }

    @Test
    fun failedRefreshPreservesContentCountsAndPagination() = runTest {
        val repository = FakeRepository((5 downTo 1).map { item(it) })
        val store = SubmissionsListStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        runCurrent()
        repository.fail = true
        store.refresh()
        runCurrent()
        assertEquals(listOf(5, 4, 3, 2), store.ids())
        assertEquals(AlgoliaSubmissionCount(5, true), store.state.value.storyCount)
        assertTrue(store.state.value.canLoadMore)
        repository.fail = false
        store.retry()
        runCurrent()
        assertEquals(listOf(5, 4, 3, 2), store.ids())
        assertFalse(store.state.value.loadingFailed)
    }

    @Test
    fun staleSuccessOrFailureCannotReplaceNewFilterState() = runTest {
        for (fail in listOf(false, true)) {
            val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
            val store = SubmissionsListStore("simon", repository, pageSize = 4)
            store.ensureLoaded()
            runCurrent()
            store.selectFilter(SubmissionFilter.COMMENTS)
            store.ensureLoaded()
            runCurrent()
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
            runCurrent()
            for (filter in SubmissionFilter.entries) {
                store.selectFilter(filter)
                store.ensureLoaded()
                runCurrent()
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
        runCurrent()
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

    @Test
    fun contentAndPaginationProceedWhileCountsAreOutstandingWithoutDuplicatingThem() = runTest {
        val repository = FakeRepository((8 downTo 1).map { item(it, it % 2 == 1) })
        repository.countGate = CompletableDeferred()
        val store = SubmissionsListStore("simon", repository, pageSize = 2)
        store.ensureLoaded()
        runCurrent()
        assertEquals(listOf(8, 7), store.ids())
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.showInitialLoading)
        assertEquals(null, store.state.value.storyCount)
        store.loadMore()
        runCurrent()
        assertEquals(listOf(8, 7, 6, 5), store.ids())
        assertEquals(2, repository.requests.count { it.pageSize == 0 })
        repository.countGate!!.complete(Unit)
        runCurrent()
        assertEquals(AlgoliaSubmissionCount(4, true), store.state.value.storyCount)
        assertEquals(AlgoliaSubmissionCount(4, true), store.state.value.commentCount)
        assertFalse(store.state.value.loading)
    }

    @Test
    fun refreshAndFilterChangesRejectOldCountsEvenWhenCancellationIsIgnored() = runTest {
        for (refresh in listOf(false, true)) {
            val repository = FakeRepository((8 downTo 1).map { item(it, it % 2 == 1) })
            val oldCounts = CompletableDeferred<Unit>()
            repository.countGate = oldCounts
            repository.ignoreCountCancellation = true
            val store = SubmissionsListStore("simon", repository, pageSize = 2)
            store.ensureLoaded(); runCurrent()
            repository.items = listOf(item(20))
            repository.countGate = null
            if (refresh) store.refresh() else {
                store.selectFilter(SubmissionFilter.STORIES)
                store.ensureLoaded()
            }
            runCurrent()
            assertEquals(listOf(20), store.ids())
            oldCounts.complete(Unit); runCurrent()
            assertEquals(AlgoliaSubmissionCount(1, true), store.state.value.storyCount)
            assertEquals(AlgoliaSubmissionCount(0, true), store.state.value.commentCount)
            assertFalse(store.state.value.loading)
        }
    }

    @Test
    fun switchingBackToRetainedRowsResumesMissingCountsWithoutReloadingPosts() = runTest {
        val repository = FakeRepository((8 downTo 1).map { item(it, it % 2 == 1) })
        repository.countGate = CompletableDeferred()
        val store = SubmissionsListStore("simon", repository, pageSize = 2)
        store.ensureLoaded(); runCurrent()
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded(); runCurrent()
        store.selectFilter(SubmissionFilter.BOTH)
        repository.countGate!!.complete(Unit)
        store.ensureLoaded(); runCurrent()
        assertEquals(listOf(8, 7), store.ids())
        assertEquals(2, repository.requests.count { it.pageSize > 0 })
        assertEquals(AlgoliaSubmissionCount(4, true), store.state.value.storyCount)
        assertEquals(AlgoliaSubmissionCount(4, true), store.state.value.commentCount)
    }

    private data class Request(val type: AlgoliaSubmissionType, val pageSize: Int, val cursor: AlgoliaSubmissionsCursor)

    private class FakeRepository(var items: List<Story>) : AlgoliaRepository {
        val requests = mutableListOf<Request>()
        var fail = false
        var failCounts = false
        var gate: CompletableDeferred<Unit>? = null
        var countGate: CompletableDeferred<Unit>? = null
        var ignoreCountCancellation = false
        override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType, cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage {
            requests += Request(type, pageSize, cursor)
            val snapshot = items
            val countWait = countGate
            if (pageSize == 0 && countWait != null) {
                if (ignoreCountCancellation) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { countWait.await() }
                else countWait.await()
            }
            gate?.await()
            if (fail || (failCounts && pageSize == 0)) error("offline")
            val filtered = snapshot.filter {
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
        override suspend fun search(url: String): AlgoliaSearchPage = error("Not used")
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
