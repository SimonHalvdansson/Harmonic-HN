package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MissingTopLevelCommentsTest {
    @Test
    fun mergesOnlyMissingRootsInHnOrderAndPersistsThemWithoutFetchingReplies() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher)
        val requested = mutableListOf<Int>()
        val official = official { id ->
            requested += id
            comment(id).apply { kidsIds = intArrayOf(99) }
        }
        val response = """{"id":42,"children":[{"id":2,"author":"two","text":"Existing","children":[{"id":20,"author":"reply","text":"Reply"}]}]}"""
        val parsed = parser.parseForDisplay(response, listOf(3, 2, 1))
        val result = MissingTopLevelComments(official, parser, dispatcher).reconcile(
            CommentThreadLoadResult.Algolia(response, parsed), listOf(3, 2, 1, 1), emptySet(), {},
        )
        assertEquals(listOf(3, 1), requested)
        assertEquals(listOf(3, 2, 20, 1), result.parsed.comments.map { it.id })
        assertEquals(listOf(0, 0, 1, 0), result.parsed.comments.map { it.depth })
        val restored = parser.prepare(result.response, listOf(3, 2, 1)).restore()
        assertEquals(result.parsed.comments.map { it.id }, restored.comments.map { it.id })
        assertEquals(4, restored.comments.size)
    }

    @Test
    fun filteredAndDeletedAlgoliaRootsAreNotMissingAndRecoveredRootsRespectFilters() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher)
        val requested = mutableListOf<Int>()
        val response = """{"id":42,"children":[{"id":1,"author":"blocked","text":"Hidden"},{"id":2,"text":null}]}"""
        val result = MissingTopLevelComments(official { id -> requested += id; comment(id) }, parser, dispatcher)
            .reconcile(CommentThreadLoadResult.Algolia(response, parser.parseForDisplay(response, filteredUsers = setOf("blocked"))),
                listOf(1, 2, 3), setOf("reader"), {})
        assertEquals(listOf(3), requested)
        assertEquals(listOf(1), result.parsed.comments.map { it.id })
        // User-independent cache retains the recovered root for a later unfiltered opening.
        assertEquals(listOf(1, 3), parser.parseForDisplay(result.response).comments.map { it.id })
    }

    @Test
    fun failuresNullDeletedAndWrongParentDoNotDiscardSuccessfulRoots() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher)
        val official = official { id -> when (id) {
            1 -> error("Unavailable")
            2 -> null
            3 -> comment(id).apply { text = "" }
            4 -> comment(id).apply { parent = 9 }
            5 -> comment(id).apply { by = "" }
            else -> comment(id)
        } }
        val result = MissingTopLevelComments(official, parser, dispatcher).reconcile(
            CommentThreadLoadResult.Algolia(emptyResponse, parser.parseForDisplay(emptyResponse)),
            (1..6).toList(), emptySet(), {},
        )
        assertEquals(listOf(6), result.parsed.comments.map { it.id })
    }

    @Test
    fun capsRequestsAndConcurrencyRegardlessOfStorySize() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher)
        var active = 0
        var peak = 0
        val requested = mutableListOf<Int>()
        val official = official { id ->
            requested += id
            active++
            peak = maxOf(peak, active)
            try { delay(100) } finally { active-- }
            comment(id)
        }
        val result = MissingTopLevelComments(official, parser, dispatcher).reconcile(
            CommentThreadLoadResult.Algolia(emptyResponse, parser.parseForDisplay(emptyResponse)),
            (1..1_000).toList(), emptySet(), {},
        )
        assertEquals((1..8).toList(), requested)
        assertEquals(4, peak)
        assertEquals((1..8).toList(), result.parsed.comments.map { it.id })
    }

    @Test
    fun timesOutSlowRequestsButKeepsSuccessfulOnes() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher)
        val official = official { id -> if (id == 1) awaitCancellation() else comment(id) }
        val result = MissingTopLevelComments(official, parser, dispatcher).reconcile(
            CommentThreadLoadResult.Algolia(emptyResponse, parser.parseForDisplay(emptyResponse)),
            listOf(1, 2), emptySet(), {},
        )
        assertEquals(listOf(2), result.parsed.comments.map { it.id })
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test
    fun cancellationStopsRecoveryAndDoesNotReturnACompletedThread() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher)
        val started = CompletableDeferred<Unit>()
        var stopped = false
        val official = official {
            started.complete(Unit)
            try { awaitCancellation() } finally { stopped = true }
        }
        val parsed = parser.parseForDisplay(emptyResponse)
        val job = async(dispatcher) {
            MissingTopLevelComments(official, parser, dispatcher).reconcile(
                CommentThreadLoadResult.Algolia(emptyResponse, parsed), listOf(1), emptySet(), {},
            )
        }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(stopped)
    }

    @Test
    fun completeResponseIsReturnedUnchangedWithoutExtraRequestsOrPendingPublication() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher)
        val response = """{"id":42,"children":[{"id":1,"author":"reader","text":"Ready"}]}"""
        val original = CommentThreadLoadResult.Algolia(response, parser.parseForDisplay(response))
        val result = MissingTopLevelComments(official { error("Unexpected fetch") }, parser, dispatcher)
            .reconcile(original, listOf(1), emptySet()) { error("Unexpected loading") }
        assertSame(original, result)
    }

    private fun comment(id: Int) = Comment().apply {
        this.id = id; parent = 42; by = "reader"; text = "Recovered $id"; time = 123
    }

    private fun official(fetch: suspend (Int) -> Comment?) = object : HackerNewsRepository {
        override suspend fun getComment(id: Int) = fetch(id)
        override suspend fun getStory(id: Int): Story? = error("Unexpected story fetch")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
    }

    private val emptyResponse = """{"id":42,"children":[]}"""
}
