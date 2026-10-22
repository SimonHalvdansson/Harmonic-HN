package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSearchPage
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import com.simon.harmonichackernews.network.AlgoliaSubmissionsPage
import com.simon.harmonichackernews.network.AlgoliaUserComment
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun logoutClearsStoriesImmediatelyAndDiscardsInFlightResponse() = runTest {
        val storage = TestKeyValueStore()
        val response = CompletableDeferred<List<AlgoliaUserComment>>()
        val algolia = FakeAlgolia().apply { pendingComments = response }
        var user: String? = "alice"
        val store = CommentedStoriesStore(
            storage, algolia, { user }, { 1_000L }, StandardTestDispatcher(testScheduler),
        )
        store.record(7)
        val sync = async { store.sync() }
        runCurrent()
        assertEquals(listOf<Pair<String, Int?>>("alice" to null), algolia.requests)
        assertFalse(store.sync(force = true))
        assertEquals(1, algolia.requests.size)

        user = null
        assertFalse(store.sync())
        assertTrue(store.ids.value.isEmpty())
        assertEquals(null, storage.getString(CommentedStoriesStore.OWNER_KEY))
        assertEquals("", storage.getString(CommentedStoriesStore.STORY_IDS_KEY))

        response.complete(listOf(AlgoliaUserComment(8, 800)))
        assertFalse(sync.await())
        assertTrue(store.ids.value.isEmpty())
        assertEquals(0, storage.getInt(CommentedStoriesStore.NEWEST_COMMENT_KEY, -1))
        assertEquals(0L, storage.getLong(CommentedStoriesStore.LAST_SYNC_KEY, -1L))
    }

    @Test
    fun switchingAccountsStartsFreshSyncAndIgnoresPreviousResponse() = runTest {
        val storage = TestKeyValueStore()
        val aliceResponse = CompletableDeferred<List<AlgoliaUserComment>>()
        val bobResponse = CompletableDeferred<List<AlgoliaUserComment>>()
        val algolia = FakeAlgolia().apply { pendingComments = aliceResponse }
        var user = "alice"
        val store = CommentedStoriesStore(
            storage, algolia, { user }, { 1_000L }, StandardTestDispatcher(testScheduler),
        )
        store.record(7)
        val aliceSync = async { store.sync() }
        runCurrent()

        user = "bob"
        algolia.pendingComments = bobResponse
        val bobSync = async { store.sync() }
        runCurrent()
        assertTrue(store.ids.value.isEmpty())
        assertEquals(listOf<Pair<String, Int?>>("alice" to null, "bob" to null), algolia.requests)

        aliceResponse.complete(listOf(AlgoliaUserComment(8, 800)))
        assertFalse(aliceSync.await())
        assertTrue(store.ids.value.isEmpty())
        // Completing Alice's request must not release Bob's in-flight request guard.
        assertFalse(store.sync(force = true))
        assertEquals(2, algolia.requests.size)

        bobResponse.complete(listOf(AlgoliaUserComment(9, 900)))
        assertTrue(bobSync.await())
        assertEquals(setOf(9), store.ids.value)
        assertEquals("bob", storage.getString(CommentedStoriesStore.OWNER_KEY))
        assertEquals("9", storage.getString(CommentedStoriesStore.STORY_IDS_KEY))
    }

    @Test
    fun signingBackIntoSameAccountDoesNotAcceptItsPreviousSessionResponse() = runTest {
        val storage = TestKeyValueStore()
        val oldResponse = CompletableDeferred<List<AlgoliaUserComment>>()
        val algolia = FakeAlgolia().apply { pendingComments = oldResponse }
        var user: String? = "alice"
        val store = CommentedStoriesStore(
            storage, algolia, { user }, { 1_000L }, StandardTestDispatcher(testScheduler),
        )
        val oldSync = async { store.sync() }
        runCurrent()
        user = null
        store.sync()

        user = "alice"
        algolia.pendingComments = null
        algolia.comments = listOf(AlgoliaUserComment(9, 900))
        assertTrue(store.sync())
        oldResponse.complete(listOf(AlgoliaUserComment(8, 800)))
        assertFalse(oldSync.await())
        assertEquals(setOf(9), store.ids.value)
        assertEquals(900, storage.getInt(CommentedStoriesStore.NEWEST_COMMENT_KEY, 0))
        assertEquals(listOf<Pair<String, Int?>>("alice" to null, "alice" to null), algolia.requests)
    }

    @Test
    fun responseRechecksAccountBeforeAccountObserverRuns() = runTest {
        val response = CompletableDeferred<List<AlgoliaUserComment>>()
        val algolia = FakeAlgolia().apply { pendingComments = response }
        var user: String? = "alice"
        val store = CommentedStoriesStore(
            TestKeyValueStore(), algolia, { user }, { 1_000L }, StandardTestDispatcher(testScheduler),
        )
        store.record(7)
        val sync = async { store.sync() }
        runCurrent()

        user = null
        response.complete(listOf(AlgoliaUserComment(8, 800)))
        assertFalse(sync.await())
        assertTrue(store.ids.value.isEmpty())
    }

    @Test
    fun cancelledSyncCanBeRetried() = runTest {
        val algolia = FakeAlgolia().apply { pendingComments = CompletableDeferred() }
        val store = CommentedStoriesStore(
            TestKeyValueStore(), algolia, { "alice" }, { 1_000L }, StandardTestDispatcher(testScheduler),
        )
        val sync = async { store.sync() }
        runCurrent()
        sync.cancelAndJoin()
        assertTrue(sync.isCancelled)

        algolia.pendingComments = null
        algolia.comments = listOf(AlgoliaUserComment(9, 900))
        assertTrue(store.sync())
        assertEquals(setOf(9), store.ids.value)
        assertEquals(listOf<Pair<String, Int?>>("alice" to null, "alice" to null), algolia.requests)
    }

    private class FakeAlgolia : AlgoliaRepository {
        var comments: List<AlgoliaUserComment> = emptyList()
        var failure: Exception? = null
        var pendingComments: CompletableDeferred<List<AlgoliaUserComment>>? = null
        val requests = mutableListOf<Pair<String, Int?>>()

        override suspend fun getUserComments(
            userName: String,
            sinceEpochSeconds: Int?,
        ): List<AlgoliaUserComment> {
            requests += userName to sinceEpochSeconds
            failure?.let { throw it }
            return pendingComments?.await() ?: comments
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
