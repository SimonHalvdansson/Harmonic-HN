package com.simon.harmonichackernews.desktop

import java.awt.EventQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.*

class DesktopArticleSessionTest {
    private val articleUrl = "https://example.com/article"
    private fun finished(url: String) = DesktopBrowserSnapshot(url, "Article", false, false, false)

    @Test fun offlineArticleLoadsSavedHtmlWithAnOpaqueOriginAndTheOriginalDisplayUrl() = runBlocking {
        val browser = RecordingBrowser()
        val session = DesktopCommentsWebViewSession(articleUrl, scope = this, isOnline = { false },
            loadCachedArticle = { DesktopCachedArticle(articleUrl, "<base href='https://wrong.test'><p>Saved article</p>") })
        var completed: String? = null
        session.onPageFinished = { completed = it }
        session.attach(browser)
        yield()
        assertTrue(browser.urls.isEmpty(), "Offline opening must not start a network load")
        assertContains(browser.html.orEmpty(), "Saved article")
        assertContains(browser.html.orEmpty(), "<base href=\"$articleUrl\">")
        assertFalse(browser.html.orEmpty().contains("wrong.test"))
        session.updateFromBrowser(browser, finished("about:blank"))
        assertEquals(articleUrl, session.currentUrl())
        assertEquals(articleUrl, completed)
        assertFalse(session.isLoading)
        assertTrue(session.hasLoadedContent)
        session.detach(browser)
    }

    @Test fun failedOnlineLoadFallsBackToSavedArticleAndReloadReturnsToNetwork() = runBlocking {
        val browser = RecordingBrowser()
        val session = DesktopCommentsWebViewSession(articleUrl, scope = this,
            loadCachedArticle = { DesktopCachedArticle(articleUrl, "<p>Saved</p>") })
        session.attach(browser)
        session.updateFromBrowser(browser, DesktopBrowserSnapshot(articleUrl, null, false, false, false, "Offline"))
        yield()
        assertNotNull(browser.html)
        session.updateFromBrowser(browser, finished("about:blank"))
        assertNull(session.loadError)
        session.reload()
        assertEquals(listOf(articleUrl, articleUrl), browser.urls)
        session.detach(browser)
    }

    @Test fun missingCacheStillAttemptsTheWebsiteAndStaleCacheCannotReplaceANewerPage() = runBlocking {
        val browser = RecordingBrowser()
        val waiting = CompletableDeferred<DesktopCachedArticle?>()
        val session = DesktopCommentsWebViewSession(articleUrl, scope = this, isOnline = { false },
            loadCachedArticle = { if (it == articleUrl) waiting.await() else null })
        session.attach(browser)
        yield()
        session.load("https://other.test/new")
        yield()
        waiting.complete(DesktopCachedArticle(articleUrl, "Stale article"))
        yield()
        assertNull(browser.html)
        assertEquals(listOf("https://other.test/new"), browser.urls)
        session.detach(browser)
    }

    @Test fun initialAndFollowedLinksApplyArchiveAndNitterPoliciesWithoutRedirectLoops() {
        val browser = RecordingBrowser()
        val policy = DesktopArticleNavigation(listOf("example.com"), true, "https://nitter.example.org")
        val session = DesktopCommentsWebViewSession(articleUrl, policy)
        session.attach(browser)
        val archiveUrl = policy.resolve(articleUrl)
        assertNotEquals(articleUrl, archiveUrl)
        assertEquals(listOf(archiveUrl), browser.urls)
        assertTrue(session.allowNavigation(browser, archiveUrl, true))
        assertTrue(session.allowNavigation(browser, "https://example.com/iframe", false))
        val next = "https://example.com/next"
        assertFalse(session.allowNavigation(browser, next, true))
        EventQueue.invokeAndWait { }
        assertEquals(policy.resolve(next), browser.urls.last())
        val tweet = "https://x.com/example/status/123"
        assertFalse(session.allowNavigation(browser, tweet, true))
        EventQueue.invokeAndWait { }
        assertEquals("https://nitter.example.org/example/status/123", browser.urls.last())
        assertTrue(session.allowNavigation(browser, browser.urls.last(), true))
        session.detach(browser)
    }

    @Test fun pageCompletionAndFailureReachPreviewCallbacksButDetachedBrowsersDoNot() {
        val browser = RecordingBrowser()
        val session = DesktopCommentsWebViewSession(articleUrl)
        val completed = mutableListOf<String>()
        val started = mutableListOf<String>()
        var failures = 0
        session.onNavigationStarted = started::add
        session.onPageFinished = completed::add
        session.onLoadFailed = { failures++ }
        session.attach(browser)
        session.updateFromBrowser(browser, finished(articleUrl))
        assertEquals(listOf(articleUrl), completed)
        session.reload()
        assertEquals(listOf(articleUrl, articleUrl), started)
        session.updateFromBrowser(browser, finished(articleUrl).copy(loadError = "Failed"))
        assertEquals(1, failures)
        session.detach(browser)
        session.updateFromBrowser(browser, finished("https://stale.test"))
        assertEquals(listOf(articleUrl), completed)
    }

    private class RecordingBrowser : DesktopBrowserHost {
        val urls = mutableListOf<String>()
        var html: String? = null
        override fun loadUrl(url: String) { urls += url }
        override fun loadHtml(html: String) { this.html = html }
        override fun evaluateJavaScriptResult(script: String, result: (String?) -> Unit) = result(null)
        override fun navigateBack() = Unit
        override fun navigateForward() = Unit
        override fun reload(fallbackUrl: String) = Unit
        override fun setBrowserVisible(visible: Boolean) = Unit
        override fun evaluateJavaScript(script: String) = Unit
        override fun disposeBrowser() = Unit
    }
}
