package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryPreviewRepositoryTest {
    @Test
    fun failedListImageIsSuppressedOnTheFirstCommentsStateAndAfterRepositoryRecreation() = runTest {
        val store = TestKeyValueStore()
        val repository = repository(backgroundScope, store)
        val stories = StoryPreviewResourceRuntime(backgroundScope, repository)
        stories.request(request)
        runCurrent()
        assertEquals(IMAGE, stories.stateFor(1)?.imageUrl)
        stories.completeImageLoad(1, PAGE, IMAGE, success = false)

        val comments = StoryPreviewResourceRuntime(backgroundScope, repository)
        comments.request(request.copy(loadSummary = false))
        // Check before the cache-reading coroutine runs: no initial image space or request.
        assertTrue(comments.stateFor(1)?.imageLoadFailed == true)
        assertFalse(comments.beginImageLoad(1, PAGE, IMAGE))

        val reopened = StoryPreviewResourceRuntime(backgroundScope, repository(backgroundScope, store))
        reopened.request(request)
        assertTrue(reopened.stateFor(1)?.imageLoadFailed == true)
        runCurrent()
        assertTrue(reopened.stateFor(1)?.imageLoadFailed == true)
        assertEquals(IMAGE, reopened.stateFor(1)?.imageUrl)
    }

    @Test
    fun failuresPropagateToAnAlreadyOpenScreenAndExpireOnRevisit() = runTest {
        var now = 1_000L
        val store = TestKeyValueStore()
        val failures = PreviewImageFailureCache(store, cooldownMillis = 50L, nowMillis = { now })
        val repository = repository(backgroundScope, store, failures)
        val stories = StoryPreviewResourceRuntime(backgroundScope, repository)
        val comments = StoryPreviewResourceRuntime(backgroundScope, repository)
        stories.request(request)
        runCurrent()
        comments.request(request.copy(loadSummary = false))
        runCurrent()

        comments.completeImageLoad(1, PAGE, IMAGE, success = false)
        runCurrent()
        assertTrue(stories.stateFor(1)?.imageLoadFailed == true)

        now += 51L
        stories.request(request)
        assertFalse(stories.stateFor(1)?.imageLoadFailed == true)
        assertTrue(stories.beginImageLoad(1, PAGE, IMAGE))
        stories.completeImageLoad(1, PAGE, IMAGE, success = true)
        runCurrent()
        assertFalse(comments.stateFor(1)?.imageLoadFailed == true)
    }

    @Test
    fun changedImageAndExplicitRefreshCanRecoverWithoutWaitingForCooldown() = runTest {
        val store = TestKeyValueStore()
        val repository = repository(backgroundScope, store)
        repository.load(request)
        repository.imageFailures.record(IMAGE, success = false)
        assertFalse(repository.imageFailures.isFailed("https://example.com/new.png"))

        repository.load(1, PAGE, requireSummary = true, forceRefresh = true)
        assertFalse(repository.imageFailures.isFailed(IMAGE))

        repository.imageFailures.record(IMAGE, success = false)
        repository.clear()
        assertFalse(repository.imageFailures.isFailed(IMAGE))
    }

    @Test
    fun cachedAbsenceWinsOverStaleStoryAndSummaryBeforeAndAfterHydration() = runTest {
        val store = TestKeyValueStore()
        val cache = PreviewContentCache()
        cache.savePreviewImage(store, "1", null)
        cache.saveLinkSummary(store, PAGE, LinkSummary(imageUrl = IMAGE, description = "Old"))
        val runtime = StoryPreviewResourceRuntime(backgroundScope, repository(backgroundScope, store))
        val staleRequest = request.copy(knownImageUrl = IMAGE, imageUrlAlreadyResolved = true)
        runtime.request(staleRequest)
        assertTrue(runtime.stateFor(1)?.imageUrlResolved == true)
        assertNull(runtime.stateFor(1)?.imageUrl)
        runCurrent()
        assertNull(runtime.stateFor(1)?.imageUrl)
        runtime.request(staleRequest)
        assertNull(runtime.stateFor(1)?.imageUrl)
    }

    private fun repository(
        scope: CoroutineScope,
        store: TestKeyValueStore,
        failures: PreviewImageFailureCache = PreviewImageFailureCache(store),
    ) = StoryPreviewRepository(
        coordinator = PreviewContentCoordinator(scope),
        linkSummaries = object : LinkSummaryRepository {
            override suspend fun load(pageUrl: String, fallbackTitle: String?) =
                LinkSummary(imageUrl = IMAGE, description = "Article summary")
        },
        store = store,
        imageFailures = failures,
    )

    private companion object {
        const val PAGE = "https://example.com/article"
        const val IMAGE = "https://example.com/broken.png"
        val request = StoryPreviewResourceRequest(1, PAGE, loadImage = true, loadSummary = true)
    }
}
