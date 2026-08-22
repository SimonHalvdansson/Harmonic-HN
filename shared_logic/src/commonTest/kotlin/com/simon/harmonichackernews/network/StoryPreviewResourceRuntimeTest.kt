package com.simon.harmonichackernews.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryPreviewResourceRuntimeTest {
    @Test
    fun cachedSummaryHydratesImmutableStateWithoutFetching() = runTest {
        var fetches = 0
        val service = object : StoryPreviewResourceService {
            override suspend fun readCached(request: StoryPreviewResourceRequest) =
                CachedStoryPreviewResource(
                    imageUrlResolved = false,
                    imageUrl = null,
                    summary = LinkSummary(
                        description = "Cached summary",
                        imageUrl = "https://example.com/cached.png",
                    ),
                )

            override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent {
                fetches++
                return PreviewContent(null, null)
            }
        }
        val runtime = StoryPreviewResourceRuntime(this, service)

        assertTrue(runtime.request(request(storyId = 7)))
        runCurrent()

        assertEquals(0, fetches)
        assertEquals(
            StoryPreviewResourceState(
                storyId = 7,
                pageUrl = PAGE_URL,
                loading = false,
                imageUrlResolved = true,
                imageUrl = "https://example.com/cached.png",
                summaryResolved = true,
                summary = LinkSummary(
                    description = "Cached summary",
                    imageUrl = "https://example.com/cached.png",
                ),
            ),
            runtime.stateFor(7),
        )
    }

    @Test
    fun duplicateRequestsShareOneInFlightLoad() = runTest {
        val finish = CompletableDeferred<Unit>()
        var fetches = 0
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(false, null, null)

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent {
                    fetches++
                    finish.await()
                    return PreviewContent("https://example.com/new.png", null)
                }
            },
        )

        assertTrue(runtime.request(request(storyId = 2, loadSummary = false)))
        runCurrent()
        assertFalse(runtime.request(request(storyId = 2, loadSummary = false)))
        assertEquals(1, fetches)
        assertTrue(runtime.stateFor(2)?.loading == true)

        finish.complete(Unit)
        runCurrent()

        assertEquals("https://example.com/new.png", runtime.stateFor(2)?.imageUrl)
        assertTrue(runtime.stateFor(2)?.imageUrlResolved == true)
        assertFalse(runtime.stateFor(2)?.loading == true)

        assertTrue(
            runtime.beginImageLoad(2, PAGE_URL, "https://example.com/new.png"),
        )
        assertTrue(runtime.stateFor(2)?.imageLoading == true)
        runtime.completeImageLoad(
            storyId = 2,
            pageUrl = PAGE_URL,
            imageUrl = "https://example.com/new.png",
            success = true,
        )
        assertTrue(runtime.stateFor(2)?.imageLoaded == true)
        assertFalse(runtime.stateFor(2)?.imageLoading == true)
    }

    @Test
    fun imageFailureIsKeyedAndDoesNotImmediatelyRestartTheSameDrawable() = runTest {
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(false, null, null)

                override suspend fun load(request: StoryPreviewResourceRequest) =
                    PreviewContent("https://example.com/failed.png", null)
            },
        )
        runtime.request(request(storyId = 4, loadSummary = false))
        runCurrent()

        assertTrue(runtime.beginImageLoad(4, PAGE_URL, "https://example.com/failed.png"))
        runtime.completeImageLoad(
            storyId = 4,
            pageUrl = PAGE_URL,
            imageUrl = "https://example.com/failed.png",
            success = false,
        )

        assertTrue(runtime.stateFor(4)?.imageLoadFailed == true)
        assertFalse(runtime.beginImageLoad(4, PAGE_URL, "https://example.com/failed.png"))
    }

    @Test
    fun identicalImageCompletionDoesNotRepublishTheResourceMap() = runTest {
        val imageUrl = "https://example.com/image.png"
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(false, null, null)

                override suspend fun load(request: StoryPreviewResourceRequest) =
                    PreviewContent(imageUrl, null)
            },
        )
        runtime.request(request(storyId = 5, loadSummary = false))
        runCurrent()
        assertTrue(runtime.beginImageLoad(5, PAGE_URL, imageUrl))
        runtime.completeImageLoad(5, PAGE_URL, imageUrl, success = true)
        val completedStates = runtime.states.value

        runtime.completeImageLoad(5, PAGE_URL, imageUrl, success = true)

        assertSame(completedStates, runtime.states.value)
    }

    @Test
    fun aChangedUrlReplacesTheOldRequestForTheSameStoryId() = runTest {
        val firstLoad = CompletableDeferred<Unit>()
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(false, null, null)

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent {
                    if (request.pageUrl == PAGE_URL) firstLoad.await()
                    return PreviewContent(request.pageUrl + "/image.png", null)
                }
            },
        )

        runtime.request(request(storyId = 9, loadSummary = false))
        runCurrent()
        runtime.request(
            request(storyId = 9, loadSummary = false).copy(pageUrl = "https://other.example"),
        )
        runCurrent()

        assertEquals("https://other.example", runtime.stateFor(9)?.pageUrl)
        assertEquals("https://other.example/image.png", runtime.stateFor(9)?.imageUrl)
        firstLoad.complete(Unit)
        runCurrent()
        assertEquals("https://other.example", runtime.stateFor(9)?.pageUrl)
    }

    @Test
    fun disposalCancelsRequestsAndClearsAllKeyedState() = runTest {
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(false, null, null)

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent =
                    CompletableDeferred<PreviewContent>().await()
            },
        )

        runtime.request(request(storyId = 3))
        runCurrent()
        runtime.dispose()
        runCurrent()

        assertNull(runtime.stateFor(3))
        assertTrue(runtime.states.value.isEmpty())
    }

    private fun request(
        storyId: Int,
        loadSummary: Boolean = true,
    ) = StoryPreviewResourceRequest(
        storyId = storyId,
        pageUrl = PAGE_URL,
        loadImage = true,
        loadSummary = loadSummary,
    )

    private companion object {
        const val PAGE_URL = "https://example.com/article"
    }
}
