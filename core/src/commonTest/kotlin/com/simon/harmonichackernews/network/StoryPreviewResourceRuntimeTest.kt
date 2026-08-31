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
    fun cachedSummaryImageTakesPrecedenceOverCachedImageUrl() = runTest {
        val summary = LinkSummary(
            description = "Cached summary",
            imageUrl = "https://example.com/summary.png",
        )
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(
                        imageUrlResolved = true,
                        imageUrl = "https://example.com/cached.png",
                        summary = summary,
                    )

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent =
                    error("Resolved cached resources should not be loaded")
            },
        )

        runtime.request(request(storyId = 10))
        runCurrent()

        assertEquals("https://example.com/summary.png", runtime.stateFor(10)?.imageUrl)
        assertEquals(summary, runtime.stateFor(10)?.summary)
    }

    @Test
    fun knownImageUrlTakesPrecedenceOverKnownSummaryImage() = runTest {
        var cacheReads = 0
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(
                        imageUrlResolved = true,
                        imageUrl = "https://example.com/initial.png",
                        summary = null,
                    ).also { cacheReads++ }

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent =
                    error("Resolved known resources should not be loaded")
            },
        )
        runtime.request(request(storyId = 11, loadSummary = false))
        runCurrent()
        val summary = LinkSummary(
            description = "Known summary",
            imageUrl = "https://example.com/summary.png",
        )

        assertFalse(
            runtime.request(
                request(storyId = 11).copy(
                    knownImageUrl = "https://example.com/known.png",
                    imageUrlAlreadyResolved = true,
                    knownSummary = summary,
                ),
            ),
        )

        assertEquals(1, cacheReads)
        assertEquals("https://example.com/known.png", runtime.stateFor(11)?.imageUrl)
        assertEquals(summary, runtime.stateFor(11)?.summary)
    }

    @Test
    fun cachedRetryWithUnchangedImagePreservesImageStateAndClearsContentFailure() = runTest {
        val imageUrl = "https://example.com/image.png"
        val previewTint = tint(imageUrl, tintColorArgb = 4)
        val faviconTint = tint("https://example.com/favicon.ico", tintColorArgb = 5)
        var cacheReads = 0
        var loads = 0
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    if (cacheReads++ == 0) {
                        CachedStoryPreviewResource(false, null, null)
                    } else {
                        CachedStoryPreviewResource(true, imageUrl, null)
                    }

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent {
                    loads++
                    return PreviewContent(null, null, PreviewImageResult.TRANSIENT_FAILURE)
                }
            },
        )
        val unresolvedKnownImage = request(storyId = 12, loadSummary = false).copy(
            knownImageUrl = imageUrl,
            imageUrlAlreadyResolved = false,
        )

        runtime.request(unresolvedKnownImage)
        runCurrent()
        assertTrue(runtime.stateFor(12)?.contentLoadFailed == true)
        assertTrue(runtime.recordTint(12, PAGE_URL, StoryResourceTintKind.PREVIEW_IMAGE, previewTint))
        assertTrue(runtime.recordTint(12, PAGE_URL, StoryResourceTintKind.FAVICON, faviconTint))

        runtime.request(unresolvedKnownImage)
        runCurrent()

        val state = requireNotNull(runtime.stateFor(12))
        assertEquals(2, cacheReads)
        assertEquals(1, loads)
        assertTrue(state.imageUrlResolved)
        assertFalse(state.contentLoadFailed)
        assertTrue(state.imageLoaded)
        assertEquals(previewTint, state.previewTint)
        assertEquals(faviconTint, state.faviconTint)
    }

    @Test
    fun changedImageUrlClearsOnlyStateBelongingToThePreviousImage() = runTest {
        val firstImageUrl = "https://example.com/first.png"
        val faviconTint = tint("https://example.com/favicon.ico", tintColorArgb = 5)
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(true, firstImageUrl, null)

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent =
                    error("Resolved known resources should not be loaded")
            },
        )
        runtime.request(request(storyId = 13, loadSummary = false))
        runCurrent()
        assertTrue(runtime.recordTint(13, PAGE_URL, StoryResourceTintKind.FAVICON, faviconTint))
        assertTrue(runtime.beginImageLoad(13, PAGE_URL, firstImageUrl))

        val secondImageUrl = "https://example.com/second.png"
        assertFalse(runtime.request(knownImageRequest(storyId = 13, imageUrl = secondImageUrl)))
        var state = requireNotNull(runtime.stateFor(13))
        assertFalse(state.imageLoading)
        assertEquals(faviconTint, state.faviconTint)

        assertTrue(runtime.beginImageLoad(13, PAGE_URL, secondImageUrl))
        runtime.completeImageLoad(13, PAGE_URL, secondImageUrl, success = true)
        val previewTint = tint(secondImageUrl, tintColorArgb = 4)
        assertTrue(runtime.recordTint(13, PAGE_URL, StoryResourceTintKind.PREVIEW_IMAGE, previewTint))

        val thirdImageUrl = "https://example.com/third.png"
        assertFalse(runtime.request(knownImageRequest(storyId = 13, imageUrl = thirdImageUrl)))
        state = requireNotNull(runtime.stateFor(13))
        assertFalse(state.imageLoaded)
        assertNull(state.previewTint)
        assertEquals(faviconTint, state.faviconTint)

        assertTrue(runtime.beginImageLoad(13, PAGE_URL, thirdImageUrl))
        runtime.completeImageLoad(13, PAGE_URL, thirdImageUrl, success = false)
        assertFalse(
            runtime.request(
                knownImageRequest(
                    storyId = 13,
                    imageUrl = "https://example.com/fourth.png",
                ),
            ),
        )
        state = requireNotNull(runtime.stateFor(13))
        assertFalse(state.imageLoadFailed)
        assertEquals(faviconTint, state.faviconTint)
    }

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
    fun transientContentFailureRemainsUnresolvedAndCanRetry() = runTest {
        var attempts = 0
        val runtime = StoryPreviewResourceRuntime(
            scope = this,
            service = object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(false, null, null)

                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent {
                    attempts++
                    return if (attempts == 1) {
                        PreviewContent(null, null, PreviewImageResult.TRANSIENT_FAILURE)
                    } else {
                        PreviewContent(null, null)
                    }
                }
            },
        )

        assertTrue(runtime.request(request(storyId = 6, loadSummary = false)))
        runCurrent()
        assertTrue(runtime.stateFor(6)?.contentLoadFailed == true)
        assertFalse(runtime.stateFor(6)?.imageUrlResolved == true)

        assertTrue(runtime.request(request(storyId = 6, loadSummary = false)))
        runCurrent()
        assertEquals(2, attempts)
        assertTrue(runtime.stateFor(6)?.imageUrlResolved == true)
        assertFalse(runtime.stateFor(6)?.contentLoadFailed == true)
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

    private fun knownImageRequest(
        storyId: Int,
        imageUrl: String,
    ) = request(storyId = storyId, loadSummary = false).copy(
        knownImageUrl = imageUrl,
        imageUrlAlreadyResolved = true,
    )

    private fun tint(
        sourceUrl: String,
        tintColorArgb: Int,
    ) = StoryResourceTintState(
        sourceUrl = sourceUrl,
        baseColorArgb = 1,
        paletteConfigKey = "test-palette",
        tintColorArgb = tintColorArgb,
    )

    private companion object {
        const val PAGE_URL = "https://example.com/article"
    }
}
