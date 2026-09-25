package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReferencePreviewSharingTest {
    @Test
    fun referenceTitleAndOverlaySharePreloadAndDismissalReleasesOnlyOneConsumer() = runTest {
        val response = CompletableDeferred<LinkSummary>()
        var requests = 0
        var cancelled = false
        val repository = repository {
            requests++
            try { response.await() } finally { if (!response.isCompleted) cancelled = true }
        }
        val title = async { repository.loadLinkSummary(PAGE) }
        runCurrent()
        val overlay = runtime(repository)
        overlay.load(PAGE, "Fallback", null)
        runCurrent()
        assertEquals(1, requests)
        overlay.dispose()
        runCurrent()
        assertFalse(cancelled)
        val summary = LinkSummary(title = "Resolved", finalUrl = "$PAGE/final", imageUrl = "$PAGE/image.png")
        response.complete(summary)
        assertEquals(summary, title.await())
        assertNull(overlay.state.value.summary)
        overlay.load(PAGE, "Fallback", null)
        runCurrent()
        assertEquals(summary, overlay.state.value.summary)
        assertEquals(summary.finalUrl, overlay.state.value.url)
        assertEquals(1, requests)
    }

    @Test
    fun allConsumersLeavingCancelsAndReopeningRetries() = runTest {
        var requests = 0
        var cancellations = 0
        val repository = repository {
            requests++
            try { awaitCancellation() } finally { cancellations++ }
        }
        val title = async { repository.loadLinkSummary(PAGE) }
        val overlay = runtime(repository)
        overlay.load(PAGE, "Fallback", null)
        runCurrent()
        title.cancel(); runCurrent()
        assertEquals(0, cancellations)
        overlay.dispose(); runCurrent()
        assertEquals(1, cancellations)
        overlay.load(PAGE, "Fallback", null); runCurrent()
        assertEquals(2, requests)
        overlay.dispose(); runCurrent()
        assertEquals(2, cancellations)
    }

    @Test
    fun validResolvedSummaryAvoidsDownloadAndLegacyProviderSummaryIsRefreshed() = runTest {
        var requests = 0
        val repository = repository { requests++; LinkSummary(title = "New provider", contentType = LinkSummaryParser.XKCD_COMIC_CONTENT_TYPE) }
        val resolved = LinkSummary(title = "Known", finalUrl = "$PAGE/redirect")
        val overlay = runtime(repository)
        overlay.load(PAGE, "Fallback", "Known", resolvedSummary = resolved)
        assertEquals(resolved, overlay.state.value.summary)
        assertEquals(0, requests)
        overlay.load("https://xkcd.com/123/", "Fallback", null, resolvedSummary = LinkSummary(title = "Old HTML"))
        runCurrent()
        assertEquals(1, requests)
        assertEquals("New provider", overlay.state.value.summary?.title)
    }

    @Test
    fun forcedRefreshCannotBeOverwrittenInMemoryOrOnDiskByOlderResponse() = runTest {
        val old = CompletableDeferred<LinkSummary>()
        var requests = 0
        val storage = TestKeyValueStore()
        val repository = repository(storage) {
            if (++requests == 1) old.await() else LinkSummary(title = "New")
        }
        val first = async { repository.loadLinkSummary(PAGE) }
        runCurrent()
        assertEquals("New", repository.loadLinkSummary(PAGE, forceRefresh = true).title)
        old.complete(LinkSummary(title = "Old"))
        assertEquals("Old", first.await().title)
        assertEquals("New", repository.cachedLinkSummary(PAGE)?.title)
        val reopened = repository(storage) { error("Must be persisted") }
        assertEquals("New", reopened.loadLinkSummary(PAGE).title)
    }

    @Test
    fun rapidSwitchRejectsUncooperativeResponseAndPdfAndOfflineErrorsRetainFallback() = runTest {
        val old = CompletableDeferred<LinkSummary>()
        var requests = 0
        val repository = repository {
            if (++requests == 1) withContext(NonCancellable) { old.await() } else LinkSummary(title = "Second")
        }
        val overlay = runtime(repository)
        overlay.load(PAGE, "First", null); runCurrent()
        overlay.load("$PAGE/other", "Other", null); runCurrent()
        old.complete(LinkSummary(title = "Old")); runCurrent()
        assertEquals("Second", overlay.state.value.summary?.title)
        val pdf = runtime(repository { error("This link contains application/pdf, not a web page") })
        pdf.load(PAGE, "File", null); runCurrent()
        assertTrue(pdf.state.value.showFallback)
        assertNull(pdf.state.value.error)
        val offline = runtime(repository { error("Network unavailable") }, online = false)
        offline.load(PAGE, "Fallback", "Known title"); runCurrent()
        assertTrue(offline.state.value.offline)
        assertNull(offline.state.value.error)
        assertFalse(offline.retry(PAGE, "Fallback", null))
    }

    @Test
    fun compatibleFeedPreloadSharesSummaryAndClearingInvalidatesBothCacheLayers() = runTest {
        val response = CompletableDeferred<LinkSummary>()
        var requests = 0
        val repository = repository { requests++; response.await() }
        val feed = async { repository.load(42, PAGE, requireSummary = false) }
        val title = async { repository.loadLinkSummary(PAGE) }
        runCurrent()
        assertEquals(1, requests)
        val summary = LinkSummary(title = "Shared", imageUrl = "$PAGE/image.png")
        response.complete(summary)
        assertEquals(summary.imageUrl, feed.await().imageUrl)
        assertEquals(summary, title.await())
        assertEquals(summary.imageUrl, repository.readCached(StoryPreviewResourceRequest(42, PAGE, true, true)).imageUrl)
        repository.clear()
        repository.loadLinkSummary(PAGE)
        assertEquals(2, requests)
    }

    private fun TestScope.repository(
        storage: TestKeyValueStore = TestKeyValueStore(),
        fetch: suspend () -> LinkSummary,
    ) = StoryPreviewRepository(
        PreviewContentCoordinator(backgroundScope),
        object : LinkSummaryRepository {
            override suspend fun load(pageUrl: String, fallbackTitle: String?) = fetch()
        }, storage, dispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.runtime(repository: StoryPreviewRepository, online: Boolean = true) =
        ReferenceLinkPreviewRuntime(backgroundScope, repository, object : ConnectivityService {
            override fun isOnline() = online
            override fun isUnmetered() = online
        })

    companion object { private const val PAGE = "https://example.com/article" }
}
