package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.CommentsOpeningRequests
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AlgoliaCommentRequestsTest {
    @Test
    fun navigationRetainsPreloadBeforeFeedCancellationWithoutParsingOrRestart() = runTest {
        var calls = 0
        var cancelled = false
        val body = CompletableDeferred<String>()
        val source = source {
            calls++
            try { body.await() } finally { cancelled = !body.isCompleted }
        }
        val preloads = CommentsPreloadRepository(source, nowMillis = { 0L },
            requestDispatcher = StandardTestDispatcher(testScheduler))
        val feed = launch { preloads.preload(42, listOf(7)) }
        runCurrent()
        assertEquals(1, calls)
        val openings = CommentsOpeningRequests(preloads) { true }
        val navigation = MainNavigationStore(beforePublish = openings::navigationChanged)
        navigation.openStory(StoryDestination(42))
        val request = assertNotNull(openings.requestFor(navigation.state.value.storyRequest!!.serial, 42))
        feed.cancelAndJoin()
        assertFalse(cancelled)
        // No parser runs just because navigation reserves or downloads an HTTP response.
        body.complete("opaque response not yet parsed")
        assertEquals("opaque response not yet parsed", request.await())
        assertEquals(1, calls)
        navigation.detailRemovedFromBackStack()
        assertTrue(request.isClosed)
        openings.close()
    }

    @Test
    fun lastOwnerCancelsAndANewOpenStartsANewRequest() = runTest {
        var calls = 0
        var cancellations = 0
        val pool = AlgoliaCommentRequests(source {
            calls++
            try { awaitCancellation() } finally { cancellations++ }
        }, StandardTestDispatcher(testScheduler))
        val feed = pool.acquire(42)
        val screen = pool.acquire(42)
        runCurrent()
        feed.close()
        runCurrent()
        assertEquals(0, cancellations)
        screen.close()
        screen.close()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(1, cancellations)
        val reopened = pool.acquire(42)
        runCurrent()
        assertEquals(2, calls)
        reopened.close()
        runCurrent()
        assertEquals(2, cancellations)
    }

    @Test
    fun backingOutBeforeWorkerStartsSendsNothingAndOfficialSelectionNeverStartsAlgolia() = runTest {
        var calls = 0
        val preloads = CommentsPreloadRepository(source { calls++; "{}" }, nowMillis = { 0L },
            requestDispatcher = StandardTestDispatcher(testScheduler))
        var useAlgolia = false
        val openings = CommentsOpeningRequests(preloads) { useAlgolia }
        val navigation = MainNavigationStore(beforePublish = openings::navigationChanged)
        navigation.openStory(StoryDestination(42))
        runCurrent()
        assertEquals(0, calls)
        navigation.returnToStories()
        useAlgolia = true
        navigation.openStory(StoryDestination(42))
        val request = assertNotNull(openings.requestFor(navigation.state.value.storyRequest!!.serial, 42))
        navigation.returnToStories()
        runCurrent()
        assertTrue(request.isClosed)
        assertEquals(0, calls)
        openings.close()
    }

    @Test
    fun completedPreloadBytesSurviveSingleConsumerPreparedThreadHandoff() = runTest {
        var calls = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val preloads = CommentsPreloadRepository(source { calls++; RESPONSE },
            parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher), nowMillis = { 0L },
            requestDispatcher = dispatcher)
        val prepared = assertNotNull(preloads.preload(42, listOf(7)))
        val opening = preloads.acquireAlgoliaRequest(42)
        val consumed = preloads.takeOrAwait(42, listOf(7))
        assertEquals(prepared, consumed)
        assertEquals(RESPONSE, opening.await())
        assertEquals(1, calls)
        opening.close()
    }

    @Test
    fun nestedNavigationAndSceneClosureReleaseOnlyTheirOwnRequests() = runTest {
        var calls = 0
        var cancellations = 0
        val preloads = CommentsPreloadRepository(source {
            calls++
            try { awaitCancellation() } finally { cancellations++ }
        }, nowMillis = { 0L }, requestDispatcher = StandardTestDispatcher(testScheduler))
        val openings = CommentsOpeningRequests(preloads) { true }
        val navigation = MainNavigationStore(beforePublish = openings::navigationChanged)
        navigation.openStory(StoryDestination(42))
        val parent = assertNotNull(openings.requestFor(navigation.state.value.storyRequest!!.serial, 42))
        navigation.openLinkedStory(StoryDestination(43))
        val child = assertNotNull(openings.requestFor(navigation.state.value.storyRequest!!.serial, 43))
        runCurrent()
        navigation.detailRemovedFromBackStack()
        runCurrent()
        assertTrue(child.isClosed)
        assertFalse(parent.isClosed)
        assertEquals(2, calls)
        assertEquals(1, cancellations)
        openings.close()
        openings.close()
        runCurrent()
        assertTrue(parent.isClosed)
        assertEquals(2, cancellations)
    }

    @Test
    fun expiredPreloadStillRequiresAFreshDownload() = runTest {
        var now = 0L
        var calls = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val preloads = CommentsPreloadRepository(source { calls++; RESPONSE },
            parser = AlgoliaCommentsParser(parsingDispatcher = dispatcher), nowMillis = { now },
            maxAgeMillis = 100, requestDispatcher = dispatcher)
        preloads.preload(42, listOf(7))
        now = 101
        val request = preloads.acquireAlgoliaRequest(42)
        assertEquals(RESPONSE, request.await())
        assertEquals(2, calls)
        assertEquals(null, preloads.takeOrAwait(42, listOf(7)))
        request.close()
    }

    private fun source(item: suspend () -> String) = object : AlgoliaRepository {
        override suspend fun getItemJson(id: Int) = item()
        override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType, cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage = error("Unused")
        override suspend fun search(url: String): List<Story> = error("Unused")
    }
    private companion object {
        const val RESPONSE = """{"id":42,"title":"Story","children":[{"id":7,"author":"author","text":"Comment"}]}"""
    }
}
