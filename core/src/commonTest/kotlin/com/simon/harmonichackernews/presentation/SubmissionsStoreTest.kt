package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
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
class SubmissionsStoreTest {
    @Test
    fun initiallyLoadsOneHundredOfEachCategoryAndSwitchesWithoutRequests() = runTest {
        val repository = FakeRepository((500 downTo 1).map { item(it, comment = it > 200) })
        val store = SubmissionsStore("simon", repository)
        store.ensureLoaded()
        assertEquals(listOf(AlgoliaSubmissionType.STORIES to 100, AlgoliaSubmissionType.COMMENTS to 100), repository.requests)
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        assertEquals((200 downTo 101).toList(), store.ids())
        assertTrue(store.state.value.canLoadMore)
        store.selectFilter(SubmissionFilter.COMMENTS)
        store.ensureLoaded()
        assertEquals((500 downTo 401).toList(), store.ids())
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun loadingStoriesDoesNotExtendBothAndBothReusesPooledObjects() = runTest {
        val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
        val store = SubmissionsStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        val originalBoth = store.ids()
        assertEquals(listOf(12, 11), originalBoth)
        val sharedStory = store.state.value.items.first()
        store.selectFilter(SubmissionFilter.STORIES)
        assertSame(sharedStory, store.state.value.items.first())
        store.loadMore()
        assertEquals(listOf(12, 10, 8, 6), store.ids())
        store.selectFilter(SubmissionFilter.BOTH)
        assertEquals(originalBoth, store.ids())
        store.loadMore()
        assertEquals((12 downTo 5).toList(), store.ids())
        assertEquals(AlgoliaSubmissionType.BOTH to 8, repository.requests.last())
        assertSame(sharedStory, store.state.value.items.first())
        store.selectFilter(SubmissionFilter.COMMENTS)
        assertEquals(listOf(11, 9, 7, 5), store.ids())
    }

    @Test
    fun finalSingleItemArrivesWithLoadingAndPaginationCleared() = runTest {
        val repository = FakeRepository(listOf(item(3), item(2), item(1)))
        val store = SubmissionsStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        store.selectFilter(SubmissionFilter.STORIES)
        assertEquals(listOf(3, 2), store.ids())
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val load = async { store.loadMore() }
        runCurrent()
        assertTrue(store.state.value.loading)
        assertTrue(store.state.value.canLoadMore)
        assertEquals(listOf(3, 2), store.ids())
        gate.complete(Unit)
        load.await()
        assertEquals(listOf(3, 2, 1), store.ids())
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.canLoadMore)
    }

    @Test
    fun zeroStoriesHasNoPaginationEvenWithManyComments() = runTest {
        val repository = FakeRepository((20 downTo 1).map { item(it, comment = true) })
        val store = SubmissionsStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        store.selectFilter(SubmissionFilter.STORIES)
        store.ensureLoaded()
        assertTrue(store.state.value.items.isEmpty())
        assertEquals("No stories", store.state.value.emptyText)
        assertFalse(store.state.value.canLoadMore)
        assertTrue(store.state.value.hasUnfilteredItems)
        store.loadMore()
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun exactFullFinalPageHasNoLoadMoreAndEmptyAccountHasNoPagination() = runTest {
        for (items in listOf(emptyList(), listOf(item(2), item(1, comment = true)))) {
            val store = SubmissionsStore("simon", FakeRepository(items), pageSize = 2)
            store.ensureLoaded()
            for (filter in SubmissionFilter.entries) {
                store.selectFilter(filter)
                store.ensureLoaded()
                assertFalse(store.state.value.canLoadMore)
            }
        }
    }

    @Test
    fun exhaustedCategoryDoesNotLimitBothAndTimestampTiesAreExcludedAtBoundary() = runTest {
        val store = SubmissionsStore("simon", FakeRepository(listOf(
            item(10), item(9, comment = true), item(8, comment = true).also { it.time = 9 },
            item(7, comment = true).also { it.time = 9 }, item(6, comment = true),
        )), pageSize = 4)
        store.ensureLoaded()
        assertEquals(listOf(10), store.ids())
        store.loadMore()
        assertEquals(listOf(10, 9, 8, 7, 6), store.ids())
        assertFalse(store.state.value.canLoadMore)
    }

    @Test
    fun failureDoesNotAdvanceLimitAndRefreshResetsOtherRanges() = runTest {
        val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
        val store = SubmissionsStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        store.selectFilter(SubmissionFilter.STORIES)
        val previous = store.ids()
        repository.fail = true
        store.loadMore()
        assertEquals(previous, store.ids())
        assertTrue(store.state.value.canLoadMore)
        assertFalse(store.state.value.loading)
        repository.fail = false
        store.loadMore()
        assertEquals(repository.requests[2], repository.requests[3])
        store.refresh()
        assertEquals(listOf(12, 10), store.ids())
        store.selectFilter(SubmissionFilter.BOTH)
        store.ensureLoaded()
        assertEquals((12 downTo 9).toList(), store.ids())
    }

    @Test
    fun staleResponseCannotReplaceNewFilterOrItsPagination() = runTest {
        val repository = FakeRepository((12 downTo 1).map { item(it, comment = it % 2 == 1) })
        val store = SubmissionsStore("simon", repository, pageSize = 4)
        store.ensureLoaded()
        store.selectFilter(SubmissionFilter.STORIES)
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val load = async { store.loadMore() }
        runCurrent()
        store.selectFilter(SubmissionFilter.COMMENTS)
        gate.complete(Unit)
        load.await()
        assertEquals(listOf(11, 9), store.ids())
        assertFalse(store.state.value.loading)
        store.selectFilter(SubmissionFilter.STORIES)
        assertEquals(listOf(12, 10), store.ids())
    }

    private class FakeRepository(val items: List<Story>) : AlgoliaRepository {
        val requests = mutableListOf<Pair<AlgoliaSubmissionType, Int>>()
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getSubmissions(userName: String, limit: Int, type: AlgoliaSubmissionType): AlgoliaSubmissionsPage {
            requests += type to limit
            gate?.await()
            if (fail) error("offline")
            val filtered = items.filter {
                when (type) {
                    AlgoliaSubmissionType.BOTH -> true
                    AlgoliaSubmissionType.STORIES -> !it.isComment
                    AlgoliaSubmissionType.COMMENTS -> it.isComment
                }
            }
            // Return fresh objects to verify that the store actually deduplicates them.
            return AlgoliaSubmissionsPage(filtered.take(limit).map { original ->
                item(original.id, original.isComment).also { it.time = original.time }
            }, filtered.size > limit)
        }
        override suspend fun search(url: String): List<Story> = error("Not used")
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private fun SubmissionsStore.ids() = state.value.items.map(Story::id)

    private companion object {
        fun item(id: Int, comment: Boolean = false) = Story().also {
            it.id = id
            it.time = id
            it.loaded = true
            it.isComment = comment
        }
    }
}
