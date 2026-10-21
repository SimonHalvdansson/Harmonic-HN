package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSearchPage
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import com.simon.harmonichackernews.network.AlgoliaSubmissionsPage
import com.simon.harmonichackernews.network.AlgoliaUserComment
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentedStoriesStoreTest {
    @Test
    fun recordsOnlyForSignedInUser() = runTest {
        var user: String? = null
        val store = CommentedStoriesStore(
            TestKeyValueStore(), FakeAlgolia(), { user }, { 0L }, StandardTestDispatcher(testScheduler),
        )
        assertFalse(store.record(10))
        assertTrue(store.ids.value.isEmpty())
        user = "alice"
        assertTrue(store.record(10))
        assertFalse(store.record(10))
        assertFalse(store.record(0))
        assertEquals(setOf(10), store.ids.value)
    }

    @Test
    fun syncIsIncrementalThrottledAndRetriedAfterFailure() = runTest {
        val algolia = FakeAlgolia()
        var now = 1_000_000L
        val store = CommentedStoriesStore(
            TestKeyValueStore(), algolia, { "alice" }, { now }, StandardTestDispatcher(testScheduler),
        )
        algolia.comments = listOf(AlgoliaUserComment(3, 300), AlgoliaUserComment(2, 200))
        assertTrue(store.sync())
        assertEquals(setOf(2, 3), store.ids.value)
        assertEquals(listOf<Int?>(null), algolia.requests.map { it.second })

        now += 1_000L
        assertFalse(store.sync())
        assertEquals(1, algolia.requests.size)

        now += CommentedStoriesStore.SYNC_INTERVAL_MILLIS
        algolia.failure = IllegalStateException("offline")
        assertFalse(store.sync())
        algolia.failure = null
        algolia.comments = listOf(AlgoliaUserComment(4, 400), AlgoliaUserComment(3, 300))
        assertTrue(store.sync())
        assertEquals(listOf(null, 300, 300), algolia.requests.map { it.second })
        assertEquals(setOf(2, 3, 4), store.ids.value)

        assertFalse(store.sync(force = true))
        assertEquals(4, algolia.requests.size)
    }

    @Test
    fun survivesRecreationAndDiscardsAnotherAccountsStories() = runTest {
        val storage = TestKeyValueStore()
        val algolia = FakeAlgolia()
        val dispatcher = StandardTestDispatcher(testScheduler)
        var user: String? = "alice"
        CommentedStoriesStore(storage, algolia, { user }, { 1L }, dispatcher).record(7)

        val restored = CommentedStoriesStore(storage, algolia, { user }, { 1L }, dispatcher)
        assertTrue(restored.record(8))
        assertEquals(setOf(7, 8), restored.ids.value)

        user = "bob"
        algolia.comments = listOf(AlgoliaUserComment(9, 900))
        assertTrue(restored.sync())
        assertEquals(setOf(9), restored.ids.value)
        assertEquals("bob", algolia.requests.single().first)

        user = null
        assertFalse(restored.sync())
        assertTrue(restored.ids.value.isEmpty())
        // Signing out discarded the stored set, so signing back in starts with a full sync.
        val signedInAgain = CommentedStoriesStore(storage, algolia, { "bob" }, { 1L }, dispatcher)
        assertTrue(signedInAgain.sync())
        assertEquals(listOf<Int?>(null, null), algolia.requests.map { it.second })
    }

    private class FakeAlgolia : AlgoliaRepository {
        var comments: List<AlgoliaUserComment> = emptyList()
        var failure: Exception? = null
        val requests = mutableListOf<Pair<String, Int?>>()

        override suspend fun getUserComments(
            userName: String,
            sinceEpochSeconds: Int?,
        ): List<AlgoliaUserComment> {
            requests += userName to sinceEpochSeconds
            failure?.let { throw it }
            return comments
        }

        override suspend fun getSubmissions(
            userName: String,
            pageSize: Int,
            type: AlgoliaSubmissionType,
            cursor: AlgoliaSubmissionsCursor,
        ): AlgoliaSubmissionsPage = error("Unused")

        override suspend fun search(url: String): AlgoliaSearchPage = error("Unused")
        override suspend fun getItemJson(id: Int): String = error("Unused")
    }
}
