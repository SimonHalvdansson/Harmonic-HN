package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.NitterInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NitterLinkPreviewRuntimeTest {
    @Test
    fun redirectsConvertibleUrlsAndWaitsForThePage() = runTest {
        val extractor = FakeExtractor(NITTER_URL)
        val runtime = runtime(this)

        val target = runtime.prepareLoad(
            requestedUrl = X_URL,
            preferences = preferences(redirect = true),
            alreadyLoaded = false,
            extractor = extractor,
        )

        assertEquals(NITTER_URL, target)
        assertEquals(NitterLinkPreviewPhase.WAITING_FOR_PAGE, runtime.state.value.phase)
        assertTrue(runtime.state.value.loading)
        assertTrue(runtime.shouldInitializeWebPage(X_URL, preferences(redirect = false)))

        advanceTimeBy(499L)
        runCurrent()
        assertEquals(0, extractor.calls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, extractor.calls)
        runtime.cancel()
    }

    @Test
    fun pageFinishedReadsImmediatelyAndRetriesFailures() = runTest {
        val expected = NitterInfo().apply { text = "Loaded" }
        val extractor = FakeExtractor(NITTER_URL, listOf(null, expected))
        val runtime = runtime(this)

        assertTrue(runtime.onPageFinished(
            loadedUrl = NITTER_URL,
            preferences = preferences(redirect = false),
            alreadyLoaded = false,
            extractor = extractor,
        ))
        runCurrent()
        assertEquals(NitterLinkPreviewPhase.RETRY_WAIT, runtime.state.value.phase)

        advanceTimeBy(100L)
        runCurrent()

        assertEquals(expected, runtime.state.value.preview)
        assertEquals(NitterLinkPreviewPhase.FINISHED, runtime.state.value.phase)
        assertFalse(runtime.state.value.loading)
        assertEquals(2, extractor.calls)
    }

    @Test
    fun cancellationInvalidatesAnOutstandingExtractionGeneration() = runTest {
        val result = CompletableDeferred<NitterInfo?>()
        val extractor = object : WebPageExtractor<NitterInfo> {
            override val currentUrl = NITTER_URL
            override suspend fun extract(): NitterInfo? = result.await()
        }
        val runtime = runtime(this)
        runtime.onPageFinished(
            loadedUrl = NITTER_URL,
            preferences = preferences(redirect = false),
            alreadyLoaded = false,
            extractor = extractor,
        )
        runCurrent()
        val activeGeneration = runtime.state.value.generation

        runtime.cancel()
        result.complete(NitterInfo())
        runCurrent()

        assertEquals(NitterLinkPreviewPhase.IDLE, runtime.state.value.phase)
        assertTrue(runtime.state.value.generation > activeGeneration)
        assertNull(runtime.state.value.preview)
    }

    private fun runtime(scope: CoroutineScope) = NitterLinkPreviewRuntime(
        scope = scope,
        maxAttempts = 3,
        pageLoadTimeoutMillis = 500L,
        htmlReadTimeoutMillis = 250L,
        retryDelaysMillis = listOf(100L, 200L),
    )

    private fun preferences(redirect: Boolean) = NitterLinkPreviewPreferences(
        previewEnabled = true,
        redirectEnabled = redirect,
    )

    private class FakeExtractor(
        override val currentUrl: String?,
        private val responses: List<NitterInfo?> = emptyList(),
    ) : WebPageExtractor<NitterInfo> {
        var calls = 0
        override suspend fun extract(): NitterInfo? = responses.getOrNull(calls++)
    }

    private companion object {
        const val X_URL = "https://x.com/example/status/123"
        const val NITTER_URL = "https://nitter.net/example/status/123"
    }
}
