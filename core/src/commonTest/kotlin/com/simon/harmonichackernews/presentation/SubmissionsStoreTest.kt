package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionsStoreTest {
    @Test
    fun filtersStoriesAndCommentsWithoutReloading() = runTest {
        val repository = FakeAlgoliaRepository(
            items = listOf(story(1), comment(2), story(3)),
        )
        val store = SubmissionsStore("simon", repository, pageSize = 10)

        store.refresh()
        assertEquals(listOf(1, 2, 3), store.state.value.items.map(Story::id))

        store.selectFilter(SubmissionFilter.COMMENTS)
        assertEquals(listOf(2), store.state.value.items.map(Story::id))
        assertEquals("No comments", store.state.value.emptyText)

        store.selectFilter(SubmissionFilter.STORIES)
        assertEquals(listOf(1, 3), store.state.value.items.map(Story::id))
        assertEquals(1, repository.requestedLimits.size)
    }

    @Test
    fun changingFilterDuringInitialLoadPreservesLoadingPresentation() = runTest {
        val response = CompletableDeferred<List<Story>>()
        val repository = DeferredAlgoliaRepository(response)
        val store = SubmissionsStore("simon", repository, pageSize = 10)

        val load = async { store.refresh() }
        runCurrent()
        assertTrue(store.state.value.loading)
        assertTrue(store.state.value.showInitialLoading)

        store.selectFilter(SubmissionFilter.COMMENTS)
        assertTrue(store.state.value.loading)
        assertTrue(store.state.value.showInitialLoading)

        response.complete(listOf(comment(2)))
        load.await()
        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.showInitialLoading)
        assertEquals(listOf(2), store.state.value.items.map(Story::id))
    }

    @Test
    fun loadMoreIncreasesTheLimitAndStopsWhenTheRepositoryReturnsLess() = runTest {
        val repository = FakeAlgoliaRepository(
            items = listOf(story(1), comment(2), story(3)),
        )
        val store = SubmissionsStore("simon", repository, pageSize = 2)

        store.refresh()
        assertTrue(store.state.value.canLoadMore)
        store.loadMore()

        assertEquals(listOf(2, 4), repository.requestedLimits)
        assertEquals(listOf(1, 2, 3), store.state.value.items.map(Story::id))
        assertFalse(store.state.value.canLoadMore)
    }

    private class FakeAlgoliaRepository(
        private val items: List<Story>,
    ) : AlgoliaRepository {
        val requestedLimits = mutableListOf<Int>()

        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> {
            requestedLimits += limit
            return items.take(limit)
        }

        override suspend fun search(url: String): List<Story> = error("Not used")

        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private class DeferredAlgoliaRepository(
        private val response: CompletableDeferred<List<Story>>,
    ) : AlgoliaRepository {
        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> =
            response.await()

        override suspend fun search(url: String): List<Story> = error("Not used")

        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private companion object {
        fun story(id: Int) = Story().also {
            it.id = id
            it.loaded = true
        }

        fun comment(id: Int) = story(id).also { it.isComment = true }
    }
}
