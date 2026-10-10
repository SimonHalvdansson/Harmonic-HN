package com.simon.harmonichackernews

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.linkpreview.LinkPreviewController
import com.simon.harmonichackernews.network.FileDownloadStore
import com.simon.harmonichackernews.network.PdfDownloadService
import com.simon.harmonichackernews.platform.FileAccessTimeStore
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.WebViewPreloadMode
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real WebView and HTTP transfers, isolated from the user's pages, files and account. */
@RunWith(AndroidJUnit4::class)
class CommentsWebViewLifecycleTest {
    @Test
    fun forbiddenHtmlChallengeCanRunScriptsAndNavigateToArticle() {
        TestServer().use { server ->
            fixture().use { browser ->
                onMain { browser.webView.loadUrl(server.url("/challenge")) }
                awaitTitle(browser.webView, PAGE_TITLE)
                onMain { assertEquals(server.url("/next-article"), browser.webView.url) }
                assertEquals(1, server.challengeRequests.get())
                assertEquals(1, server.pageRequests.get())
            }
        }
    }

    @Test
    fun nonHtmlHttpFailureStillShowsCustomErrorPage() {
        TestServer().use { server ->
            fixture().use { browser ->
                onMain { browser.webView.loadUrl(server.url("/unavailable")) }
                awaitJavascript(browser.webView,
                    "location.href.includes('webview_error.html') && location.hash === '#generic'")
            }
        }
    }

    @Test
    fun concurrentRequestsWaitForStartupAndCreateOnlyOneWebView() {
        val startup = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        fixture(awaitStartup = false, startWebView = { calls.incrementAndGet(); startup.await() }).use { browser ->
            onMain {
                browser.controller.initializeForVisibleWebsite()
                browser.controller.initializeForVisibleWebsite()
                assertFalse(browser.controller.hasWebView())
                assertEquals(0, browser.host.webViewContainer.children.count { it is WebView })
            }
            assertEquals(1, calls.get())
            startup.complete(Unit)
            browser.awaitStartup()
            onMain { assertEquals(1, browser.host.webViewContainer.children.count { it is WebView }) }
        }
    }

    @Test
    fun leavingWhileStartupIsPendingDoesNotAttachAWebView() {
        val startup = CompletableDeferred<Unit>()
        fixture(awaitStartup = false, startWebView = { startup.await() }).use { browser ->
            onMain { browser.controller.destroy() }
            startup.complete(Unit)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            onMain {
                assertFalse(browser.controller.hasWebView())
                assertEquals(0, browser.host.webViewContainer.children.count { it is WebView })
            }
        }
    }

    @Test
    fun startupFailureCompletesQueuedSummaryWithoutCreatingWebView() {
        val startup = CompletableDeferred<Unit>()
        val summary = CountDownLatch(1)
        fixture(awaitStartup = false, startWebView = { startup.await() }).use { browser ->
            onMain {
                browser.controller.requestSummary { text ->
                    assertEquals(null, text)
                    summary.countDown()
                }
            }
            startup.completeExceptionally(IllegalStateException("Test provider unavailable"))
            assertTrue(summary.await(5, TimeUnit.SECONDS))
            onMain { assertFalse(browser.controller.hasWebView()) }
        }
    }

    @Test
    fun readerModeLoadsResourcesAndRestoresTheOriginalPage() {
        fixture(readerMode = true).use { browser ->
            val paragraphs = (1..20).joinToString("") {
                "<p>A substantial article paragraph about testing reading experiences. " +
                    "It contains enough prose, punctuation, and detail for article extraction. " +
                    "The original page should remain available when reader mode is closed.</p>"
            }
            onMain {
                browser.setReaderFont(AppFont.JETBRAINS_MONO, 16)
                browser.controller.initializeForVisibleWebsite()
                browser.webView.loadDataWithBaseURL(
                    "https://example.invalid/reader-test",
                    "<html><head><title>Reader regression</title></head><body>" +
                        "<nav id='original-navigation'>Original navigation</nav>" +
                        "<article><h1>Reader regression</h1>$paragraphs</article></body></html>",
                    "text/html", "UTF-8", null,
                )
            }
            awaitTitle(browser.webView, "Reader regression")
            onMain { browser.controller.toggleReaderMode() }
            awaitJavascript(browser.webView, "document.getElementById('harmonic-reader-mode') !== null")
            onMain { assertTrue(browser.controller.isReaderModeEnabled()) }
            awaitJavascript(browser.webView, "__harmonicReaderModeTheme.fontFaceCss.length > 0")
            evaluate(browser.webView, "window.firstReaderFont = __harmonicReaderModeTheme.fontFaceCss; true")
            onMain { browser.controller.disableReaderMode() }
            awaitJavascript(browser.webView, "document.getElementById('original-navigation') !== null")
            onMain { assertFalse(browser.controller.isReaderModeEnabled()) }
            // A compact update keeps the real bundled font and applies the new size.
            onMain {
                browser.setReaderFont(AppFont.JETBRAINS_MONO, 24)
                browser.controller.toggleReaderMode()
            }
            awaitJavascript(browser.webView, "document.getElementById('harmonic-reader-mode') !== null")
            awaitJavascript(browser.webView, "__harmonicReaderModeTheme.fontFaceCss === firstReaderFont && " +
                "__harmonicReaderModeTheme.fontSizePx === 24")
            onMain { browser.controller.disableReaderMode() }
            awaitJavascript(browser.webView, "document.getElementById('original-navigation') !== null")
            // Changing font invalidates the installation; device default removes the font bytes.
            for (font in listOf(AppFont.GEORGIA, AppFont.DEVICE_DEFAULT)) {
                onMain {
                    browser.setReaderFont(font, 22)
                    browser.controller.toggleReaderMode()
                }
                awaitJavascript(browser.webView, "document.getElementById('harmonic-reader-mode') !== null")
                awaitJavascript(browser.webView, if (font == AppFont.DEVICE_DEFAULT) {
                    "__harmonicReaderModeTheme.fontFaceCss === ''"
                } else {
                    "__harmonicReaderModeTheme.fontFaceCss.length > 0 && " +
                        "__harmonicReaderModeTheme.fontFaceCss !== firstReaderFont"
                })
                onMain { browser.controller.disableReaderMode() }
                awaitJavascript(browser.webView, "document.getElementById('original-navigation') !== null")
            }
        }
    }

    @Test
    fun readerTransitionsKeepCustomScrollbarsQuietAndRestoreScrollAndLiveNodes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture(readerMode = true).use { browser ->
                val paragraphs = (1..40).joinToString("") {
                    "<p>Reader transition paragraph $it. " +
                        "A detailed article must stay scrollable and preserve the original page when reading ends. ".repeat(4) + "</p>"
                }
                scenario.onActivity { activity ->
                    (activity.window.decorView as ViewGroup).addView(browser.host.root,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    browser.host.root.bringToFront()
                    browser.controller.initializeForVisibleWebsite()
                    browser.webView.loadDataWithBaseURL("https://example.invalid/reader-scrollbar", """
                        <!doctype html><html><head><title>Scrollbar regression</title>
                        <meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>html::-webkit-scrollbar{width:16px;background:black}
                        html::-webkit-scrollbar-thumb{background:rgb(255,0,0);border-radius:8px}</style>
                        </head><body><input id="draft" value="original"><article><h1>Scrollbar regression</h1>
                        $paragraphs</article><script>
                        window.originalInput = document.getElementById('draft');
                        originalInput.value = 'Unsaved draft';
                        </script></body></html>
                    """.trimIndent(), "text/html", "UTF-8", null)
                }
                awaitTitle(browser.webView, "Scrollbar regression")
                awaitJavascript(browser.webView, "document.documentElement.scrollHeight > innerHeight * 2")
                val geometry = "JSON.stringify({width:document.documentElement.clientWidth,inner:innerWidth," +
                    "scale:visualViewport.scale,scroll:scrollY,reader:!!document.getElementById('harmonic-reader-article')})"
                // The first overview zoom can finish after onPageFinished on a visible WebView.
                Thread.sleep(500)
                evaluate(browser.webView, "window.scrollTo(0,400); window.sourceWidth = document.documentElement.clientWidth; true")
                awaitJavascript(browser.webView, "scrollY === 400")
                val originalGeometry = evaluate(browser.webView, geometry)
                repeat(2) {
                    onMain { browser.controller.toggleReaderMode() }
                    awaitJavascript(browser.webView, "!!document.getElementById('harmonic-reader-transition-scrollbar')")
                    awaitJavascript(browser.webView, "!!document.getElementById('harmonic-reader-article') && " +
                        "!document.getElementById('harmonic-reader-transition-scrollbar')")
                    onMain { assertTrue(browser.webView.canScrollVertically(1)) }
                    swipeUp(browser.webView)
                    awaitJavascript(browser.webView, "scrollY > 0")
                    // Wait for the touch fling to settle before testing scroll restoration.
                    var previousScroll: String? = null
                    val scrollDeadline = SystemClock.uptimeMillis() + 5_000
                    while (SystemClock.uptimeMillis() < scrollDeadline) {
                        val scroll = evaluate(browser.webView, "scrollY")
                        if (scroll == previousScroll) break
                        previousScroll = scroll
                        Thread.sleep(150)
                    }
                    onMain { browser.controller.disableReaderMode() }
                    awaitJavascript(browser.webView, "!!document.getElementById('harmonic-reader-transition-scrollbar')")
                    awaitJavascript(browser.webView, "!document.getElementById('harmonic-reader-transition-scrollbar') && " +
                        "document.getElementById('draft') === originalInput")
                    assertEquals("Original $originalGeometry; restored ${evaluate(browser.webView, geometry)}",
                        "400", evaluate(browser.webView, "scrollY"))
                    assertEquals("\"Unsaved draft\"", evaluate(browser.webView, "originalInput.value"))
                    assertEquals(evaluate(browser.webView, "sourceWidth"),
                        evaluate(browser.webView, "document.documentElement.clientWidth"))
                    assertEquals("\"rgb(255, 0, 0)\"", evaluate(browser.webView,
                        "getComputedStyle(document.documentElement, '::-webkit-scrollbar-thumb').backgroundColor"))
                }
                swipeUp(browser.webView)
                awaitJavascript(browser.webView, "scrollY > 400")
            }
        }
    }

    @Test
    fun coveredPageStopsAnimatingAndResumesWithoutReloading() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture().use { browser ->
                scenario.onActivity { activity ->
                    (activity.window.decorView as ViewGroup).addView(browser.host.root,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT))
                    browser.webView.loadDataWithBaseURL("https://example.invalid/animation", """
                        <html><body style="height:10000px"><script>
                        window.framesDrawn = 0;
                        window.retainedValue = 'unchanged';
                        function frame() { framesDrawn++; requestAnimationFrame(frame); }
                        requestAnimationFrame(frame);
                        </script></body></html>
                    """.trimIndent(), "text/html", "UTF-8", null)
                }
                awaitJavascript(browser.webView, "framesDrawn > 3 && !document.hidden")
                onMain {
                    browser.webView.scrollTo(0, 300)
                    browser.controller.setCoveredByComments(true)
                    assertEquals(View.INVISIBLE, browser.host.webViewContainer.visibility)
                }
                awaitJavascript(browser.webView, "document.hidden")
                val pausedFrames = evaluate(browser.webView, "framesDrawn")!!.toInt()
                Thread.sleep(250)
                assertEquals("The covered page must stop requesting animation frames",
                    pausedFrames, evaluate(browser.webView, "framesDrawn")!!.toInt())
                onMain {
                    browser.controller.setCoveredByComments(false)
                    assertEquals(View.VISIBLE, browser.host.webViewContainer.visibility)
                    assertEquals(300, browser.webView.scrollY)
                    assertTrue(browser.webView === browser.host.webViewContainer
                        .findViewById<WebView>(R.id.comments_webview))
                }
                awaitJavascript(browser.webView, "!document.hidden && framesDrawn > $pausedFrames")
                assertEquals("\"unchanged\"", evaluate(browser.webView, "retainedValue"))
            }
        }
    }

    @Test
    fun preloadedPageStaysCoveredAcrossHostRestartAndCanBeRevealed() {
        fixture(coveredByComments = true).use { browser ->
            onMain {
                assertEquals(View.INVISIBLE, browser.host.webViewContainer.visibility)
                browser.controller.setHostStarted(false)
                browser.controller.setHostStarted(true)
                assertEquals(View.INVISIBLE, browser.host.webViewContainer.visibility)
                browser.controller.setCoveredByComments(false)
                assertEquals(View.VISIBLE, browser.host.webViewContainer.visibility)
                browser.controller.setIntegratedWebview(false)
                assertEquals(View.INVISIBLE, browser.host.webViewContainer.visibility)
                browser.controller.setIntegratedWebview(true)
                assertEquals(View.VISIBLE, browser.host.webViewContainer.visibility)
            }
        }
    }

    @Test
    fun fullscreenKeepsOwnershipOfVisibilityUntilItCloses() {
        fixture().use { browser ->
            onMain {
                browser.webView.webChromeClient!!.onShowCustomView(
                    View(browser.webView.context), {},
                )
                browser.controller.setCoveredByComments(true)
                assertTrue(browser.controller.isShowingCustomView)
                assertEquals(View.VISIBLE, browser.host.fullscreenContainer.visibility)
                assertEquals(View.GONE, browser.host.webViewContainer.visibility)
                browser.controller.hideCustomView(true)
                assertFalse(browser.controller.isShowingCustomView)
                assertEquals(View.INVISIBLE, browser.host.webViewContainer.visibility)
            }
        }
    }

    @Test
    fun visiblePdfSupportsTouchScrolling() {
        TestServer().use { server ->
            server.releasePdf.countDown()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fixture().use { browser ->
                    scenario.onActivity { activity ->
                        browser.host.root.setBackgroundColor(Color.WHITE)
                        (activity.window.decorView as ViewGroup).addView(browser.host.root,
                            // Keep both PDF pages taller than the viewport on large tablets too.
                            ViewGroup.LayoutParams(480, 800))
                        browser.host.root.bringToFront()
                        browser.webView.loadUrl(server.url("/document.pdf"))
                    }
                    awaitJavascript(browser.webView, paintedPage(1))
                    awaitJavascript(browser.webView,
                        "document.getElementById('viewerContainer').scrollHeight > " +
                            "document.getElementById('viewerContainer').clientHeight")
                    swipeUp(browser.webView)
                    awaitJavascript(browser.webView,
                        "document.getElementById('viewerContainer').scrollTop > 100")
                    awaitJavascript(browser.webView, paintedPage(2))
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    try {
                        File(instrumentation.targetContext.filesDir, "pdf-viewer-review.png")
                            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally {
                        screenshot.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun bundledPdfViewerRendersPagesLinksSelectionResizeAndZoom() {
        TestServer().use { server ->
            // Other cases delay the same real download to exercise navigation races.
            server.releasePdf.countDown()
            fixture().use { browser ->
                onMain { browser.webView.loadUrl(server.url("/document.pdf")) }
                awaitJavascript(browser.webView, "document.documentElement.dataset.pdfState === 'ready' && " +
                    "document.documentElement.dataset.pdfjsVersion === '6.3.289' && " +
                    "document.querySelectorAll('.page').length === 2")
                assertEquals("JavaScript code generation must be blocked by the viewer's CSP", "true",
                    evaluate(browser.webView, "(function(){try{new Function('return true');return false;}" +
                        "catch(e){return e instanceof EvalError;}})()"))
                awaitJavascript(browser.webView, paintedPage(1))
                onMain {
                    browser.controller.openCurrentOrStoryUrlInBrowser()
                    assertEquals("External PDF actions must open the original document",
                        server.url("/document.pdf"), browser.externalUrl)
                }
                assertEquals("A late render must recover from the loading watchdog", "true",
                    evaluate(browser.webView, "harmonicPdfFailure(); harmonicPdfReady(); " +
                        "!document.getElementById('viewerContainer').hidden && " +
                        "document.getElementById('pdfStatus').hidden"))
                awaitJavascript(browser.webView,
                    "document.querySelector('.page[data-page-number=\"1\"] .textLayer')" +
                        "?.textContent.includes('Harmonic PDF page 1') === true")
                assertEquals("true", evaluate(browser.webView, """
                    (function() {
                        var range = document.createRange();
                        range.selectNodeContents(document.querySelector('.page[data-page-number="1"] .textLayer'));
                        var selection = window.getSelection();
                        selection.removeAllRanges(); selection.addRange(range);
                        var selected = selection.toString().includes('Harmonic PDF page 1');
                        selection.removeAllRanges();
                        return selected;
                    })()
                """.trimIndent()))
                evaluate(browser.webView, "document.getElementById('viewerContainer').scrollTop = 100000")
                awaitJavascript(browser.webView, paintedPage(2))
                evaluate(browser.webView, "document.getElementById('viewerContainer').scrollTop = 0")
                awaitJavascript(browser.webView, "document.getElementById('viewerContainer').scrollTop === 0")
                awaitJavascript(browser.webView,
                    "document.querySelector('.page[data-page-number=\"1\"] .annotationLayer a') !== null")
                evaluate(browser.webView,
                    "document.querySelector('.page[data-page-number=\"1\"] .annotationLayer a').click()")
                awaitJavascript(browser.webView, "document.getElementById('viewerContainer').scrollTop > 0")
                val previousWidth = evaluate(browser.webView,
                    "document.getElementById('viewerContainer').clientWidth")!!.toInt()
                onMain {
                    assertEquals("Internal PDF links must retain the trusted viewer URL",
                        AndroidPdfWebViewAssets.VIEWER_URL, browser.webView.url)
                    browser.resize(800, 480)
                }
                awaitJavascript(browser.webView,
                    "document.getElementById('viewerContainer').clientWidth > $previousWidth")
                awaitJavascript(browser.webView, paintedPage(2))
                val previousScale = onMain { browser.webView.scale }
                onMain { assertTrue("Native WebView zoom must remain available", browser.webView.zoomIn()) }
                val zoomDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (onMain { browser.webView.scale <= previousScale } && System.nanoTime() < zoomDeadline) {
                    Thread.sleep(25)
                }
                onMain { assertTrue(browser.webView.scale > previousScale) }
                awaitJavascript(browser.webView, "document.documentElement.dataset.pdfState === 'ready'")
            }
        }
    }

    @Test
    fun localPdfAssetsLoadWithAdBlockingEnabled() {
        TestServer().use { server ->
            server.releasePdf.countDown()
            fixture(blockAds = true).use { browser ->
                onMain { browser.webView.loadUrl(server.url("/document.pdf")) }
                awaitJavascript(browser.webView, paintedPage(1))
            }
        }
    }

    @Test
    fun corruptPdfShowsAnActionableFailureInsteadOfABlankViewer() {
        TestServer(pdfBytes = "%PDF-1.4\ninvalid\n%%EOF\n".toByteArray()).use { server ->
            server.releasePdf.countDown()
            fixture().use { browser ->
                onMain { browser.webView.loadUrl(server.url("/document.pdf")) }
                awaitJavascript(browser.webView, "document.documentElement.dataset.pdfState === 'error' && " +
                    "document.getElementById('pdfStatus').textContent.includes('could not be displayed') && " +
                    "!document.getElementById('pdfStatus').hidden")
            }
        }
    }

    @Test
    fun subframeNavigationDoesNotCancelPendingPdf() {
        TestServer().use { server ->
            fixture().use { browser ->
                startPendingPdf(server, browser)
                onMain {
                    val request = object : WebResourceRequest {
                        override fun getUrl(): Uri = Uri.parse(server.url("/frame"))
                        override fun isForMainFrame() = false
                        override fun isRedirect() = false
                        override fun hasGesture() = false
                        override fun getMethod() = "GET"
                        override fun getRequestHeaders() = emptyMap<String, String>()
                    }
                    browser.webView.webViewClient.shouldOverrideUrlLoading(browser.webView, request)
                    assertTrue(checkNotNull(browser.scope.coroutineContext[Job]).children.any { it.isActive })
                }
                server.releasePdf.countDown()
                awaitJavascript(browser.webView, paintedPage(1))
            }
        }
    }

    @Test
    fun closingArticleKeepsItsHttpCacheAvailableForOfflineRevisit() {
        TestServer().use { server ->
            val url = server.url("/cached-article")
            fixture().use { first ->
                onMain { first.webView.loadUrl(url) }
                awaitTitle(first.webView, PAGE_TITLE)
            }
            val firstRequests = server.pageRequests.get()
            assertTrue(firstRequests > 0)
            fixture().use { second ->
                onMain {
                    second.webView.settings.cacheMode = WebSettings.LOAD_CACHE_ONLY
                    second.webView.loadUrl(url)
                }
                awaitTitle(second.webView, PAGE_TITLE)
                assertEquals("An offline revisit must not fetch the article again", firstRequests,
                    server.pageRequests.get())
            }
        }
    }

    @Test
    fun navigatingAwayFromPendingPdfDoesNotReplaceTheNewPage() {
        TestServer().use { server ->
            fixture().use { browser ->
                startPendingPdf(server, browser)
                val nextPage = server.url("/next-article")
                onMain { browser.webView.loadUrl(nextPage) }
                awaitTitle(browser.webView, PAGE_TITLE)
                server.releasePdf.countDown()
                browser.awaitDownloads()
                onMain {
                    assertEquals(nextPage, browser.webView.url)
                    assertEquals(View.VISIBLE, browser.webView.visibility)
                    assertNull(browser.host.overlayState.onDownload)
                }
            }
        }
    }

    @Test
    fun destroyingBrowserCancelsPendingPdfWithoutRecreatingAWebView() {
        TestServer().use { server ->
            fixture().use { browser ->
                startPendingPdf(server, browser)
                onMain { browser.controller.destroy() }
                server.releasePdf.countDown()
                browser.awaitDownloads()
                onMain { assertFalse(browser.controller.hasWebView()) }
            }
        }
    }

    @Test
    fun cancellingScreenWorkDoesNotPresentPdfCancellationAsDownloadFailure() {
        TestServer().use { server ->
            fixture().use { browser ->
                startPendingPdf(server, browser)
                onMain { browser.scope.cancel() }
                server.releasePdf.countDown()
                browser.awaitDownloads()
                onMain {
                    assertEquals(View.VISIBLE, browser.webView.visibility)
                    assertNull(browser.host.overlayState.onDownload)
                }
            }
        }
    }

    private fun startPendingPdf(server: TestServer, browser: BrowserFixture) {
        onMain { browser.webView.loadUrl(server.url("/document.pdf")) }
        assertTrue("WebView must hand the PDF to the production downloader",
            server.pdfDownloadStarted.await(15, TimeUnit.SECONDS))
    }

    private fun fixture(
        blockAds: Boolean = false,
        coveredByComments: Boolean = false,
        readerMode: Boolean = false,
        awaitStartup: Boolean = true,
        startWebView: suspend (Context) -> Unit = ::awaitAndroidWebViewStartup,
    ): BrowserFixture = onMain {
        BrowserFixture(blockAds, coveredByComments, readerMode, startWebView)
    }.also { if (awaitStartup) it.awaitStartup() }

    private fun swipeUp(view: View) {
        // A warm WebView can paint before ActivityScenario's window receives input focus.
        val focusDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!onMain { view.hasWindowFocus() }) {
            if (System.nanoTime() >= focusDeadline) throw AssertionError("Browser window has no input focus")
            Thread.sleep(25)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bounds = onMain {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            floatArrayOf(location[0] + view.width * .6f,
                location[1] + view.height * .8f, location[1] + view.height * .2f)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, bounds[0], y, 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, bounds[1])
        for (step in 1..12) {
            Thread.sleep(20)
            send(MotionEvent.ACTION_MOVE, bounds[1] + (bounds[2] - bounds[1]) * step / 12f)
        }
        send(MotionEvent.ACTION_UP, bounds[2])
    }

    private fun awaitJavascript(view: WebView, condition: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (evaluate(view, condition) == "true") return
            Thread.sleep(50)
        }
        val status = evaluate(view, "JSON.stringify({state: document.documentElement.dataset.pdfState, " +
            "scroll: (() => { const v = document.getElementById('viewerContainer'); " +
            "return v && [v.scrollTop, v.scrollHeight, v.clientHeight]; })(), text: document.body.innerText})")
        throw AssertionError("Page condition did not become true: $condition; status=$status")
    }

    private fun awaitTitle(view: WebView, title: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (onMain { view.title == title && view.progress == 100 }) return
            Thread.sleep(25)
        }
        onMain { assertEquals("Page did not load: ${view.url}", title, view.title) }
    }

    private fun evaluate(view: WebView, script: String): String? {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String?>()
        onMain {
            view.evaluateJavascript(script) {
                result.set(it)
                completed.countDown()
            }
        }
        assertTrue("JavaScript evaluation did not finish", completed.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private class BrowserFixture(
        blockAds: Boolean,
        coveredByComments: Boolean,
        readerMode: Boolean,
        startWebView: suspend (Context) -> Unit,
    ) : Closeable {
        private val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.AppThemeMaterialFixedLight,
        )
        private val app = context.harmonicAppComposition
        private var reading = app.userSettings.reading.copy(
            integratedWebView = true,
            preloadWebViewMode = WebViewPreloadMode.NEVER,
            matchWebViewTheme = false,
            readerModeEnabled = readerMode,
            readerModeDefault = false,
            blockAds = blockAds,
            redirectNitter = false,
            archiveRedirectDomains = emptyList(),
            enabledLinkPreviews = emptySet(),
        )
        private val directory = File.createTempFile("webview-pdf-test-", "", context.cacheDir).apply {
            check(delete())
            check(mkdir())
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val host = CommentsWebViewHost(context)
        private val previews = LinkPreviewController(null, app, reading) {}
        var externalUrl: String? = null
        val controller = AndroidCommentsWebViewController(
            hostGateway = object : CommentsWebViewHostGateway {
                override val context: Context get() = this@BrowserFixture.context
                override val isAttached = true
            },
            story = null,
            linkPreviewController = previews,
            webContentRuntime = app.webContent.createRuntime(),
            storyCache = app.storyCache,
            pdfDownloads = PdfDownloadService(
                app.network.httpClient,
                FileDownloadStore(
                    root = Path(directory.absolutePath),
                    fileNameForKey = { "document.pdf" },
                    targetSuffix = ".pdf",
                    accessTimes = FileAccessTimeStore(InMemoryKeyValueStore()),
                    nowMillis = System::currentTimeMillis,
                ),
                nowMillis = System::currentTimeMillis,
            ),
            coroutineScope = scope,
            startWebView = startWebView,
            callbacks = object : AndroidCommentsWebViewController.Callbacks {
                override fun openExternalLink(url: String) { externalUrl = url }
                override fun showMessage(message: String?, duration: UserMessageDuration) = Unit
                override fun setFullscreenSystemBarsHidden(hidden: Boolean) = Unit
                override fun syncOnBackPressedCallbackEnabledState() = Unit
                override fun onReaderModeChanged(enabled: Boolean) = Unit
                override fun onReaderModeAvailabilityChanged(available: Boolean) = Unit
                override fun onFullscreenChanged(fullscreen: Boolean) = Unit
            },
        ).apply {
            bindViews(host)
            setCoveredByComments(coveredByComments)
            configure(false, true, reading, reading.blockAds)
            initialize()
        }
        val webView: WebView get() = host.webViewContainer.findViewById(R.id.comments_webview)

        fun setReaderFont(font: AppFont, size: Int) {
            reading = reading.copy(readerModeFont = font, readerModeFontSize = size)
            controller.configure(false, true, reading, reading.blockAds)
        }

        fun awaitStartup() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!onMain { controller.hasWebView() }) {
                if (System.nanoTime() >= deadline) throw AssertionError("WebView did not start")
                Thread.sleep(25)
            }
            onMain { resize(480, 800) }
        }

        init {
            // Give the offscreen browser a real viewport so PDF.js schedules visible pages.
            resize(480, 800)
        }

        fun resize(width: Int, height: Int) {
            host.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            host.root.layout(0, 0, width, height)
        }

        fun awaitDownloads() = runBlocking {
            withTimeout(15_000) { checkNotNull(scope.coroutineContext[Job]).children.toList().joinAll() }
        }

        override fun close() {
            onMain {
                (host.root.parent as? ViewGroup)?.removeView(host.root)
                controller.onDestroyView(host.root)
                controller.clearViewReferences()
                previews.dispose()
                scope.cancel()
            }
            awaitDownloads()
            check(directory.deleteRecursively())
        }
    }

    private class TestServer(private val pdfBytes: ByteArray = minimalPdf()) : Closeable {
        private val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        private val workers = Executors.newCachedThreadPool()
        val pageRequests = AtomicInteger()
        val challengeRequests = AtomicInteger()
        private val pdfRequests = AtomicInteger()
        val pdfDownloadStarted = CountDownLatch(1)
        val releasePdf = CountDownLatch(1)

        init {
            workers.submit {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    workers.submit {
                        socket.use {
                            val reader = socket.getInputStream().bufferedReader()
                            val request = reader.readLine().orEmpty()
                            while (!reader.readLine().isNullOrEmpty()) Unit
                            val path = request.substringAfter(' ').substringBefore(' ')
                            val pdf = path == "/document.pdf"
                            if (path == "/challenge") challengeRequests.incrementAndGet()
                            if (pdf && pdfRequests.incrementAndGet() > 1) {
                                pdfDownloadStarted.countDown()
                                check(releasePdf.await(20, TimeUnit.SECONDS))
                            } else if (path == "/cached-article" || path == "/next-article") {
                                pageRequests.incrementAndGet()
                            }
                            val bytes = if (pdf) pdfBytes else when (path) {
                                "/challenge" -> "<html><head><title>Browser check</title></head>" +
                                    "<body><script src='/challenge.js'></script></body></html>"
                                "/challenge.js" -> "window.location.replace('/next-article');"
                                "/unavailable" -> "Service unavailable"
                                else -> "<html><head><title>$PAGE_TITLE</title></head><body>Cached article</body></html>"
                            }.toByteArray()
                            val type = when (path) {
                                "/document.pdf" -> "application/pdf"
                                "/challenge.js" -> "application/javascript"
                                "/unavailable" -> "text/plain"
                                else -> "text/html"
                            }
                            val status = when (path) {
                                "/challenge" -> "403 Forbidden"
                                "/unavailable" -> "503 Service Unavailable"
                                else -> "200 OK"
                            }
                            val headers = "HTTP/1.1 $status\r\nContent-Type: $type\r\n" +
                                "Content-Length: ${bytes.size}\r\nCache-Control: " +
                                (if (pdf) "no-store" else "public, max-age=3600") +
                                "\r\nConnection: close\r\n\r\n"
                            socket.getOutputStream().apply {
                                write(headers.toByteArray())
                                write(bytes)
                                flush()
                            }
                        }
                    }
                }
            }
        }

        fun url(path: String) = "http://127.0.0.1:${server.localPort}$path"

        override fun close() {
            releasePdf.countDown()
            server.close()
            workers.shutdownNow()
            workers.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val PAGE_TITLE = "Harmonic cached article regression"

        private fun paintedPage(page: Int) = """
            (function() {
                var page = document.querySelector('.page[data-page-number="$page"][data-rendered="true"]');
                var canvas = page && page.querySelector('canvas');
                if (!canvas || !canvas.width || !canvas.height) return false;
                var pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
                for (var i = 0; i < pixels.length; i += 4) {
                    if (pixels[i + 3] > 0 && pixels[i] < 100) return true;
                }
                return false;
            })()
        """.trimIndent()

        private fun minimalPdf(): ByteArray {
            val content = "BT /F1 18 Tf 30 400 Td (Harmonic PDF page 1) Tj ET\n"
            val secondContent = "BT /F1 18 Tf 30 400 Td (Harmonic PDF page 2) Tj ET\n"
            val objects = listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 6 0 R] /Count 2 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 500] " +
                    "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R /Annots [8 0 R] >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length ${content.length} >>\nstream\n${content}endstream",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 500] " +
                    "/Resources << /Font << /F1 4 0 R >> >> /Contents 7 0 R >>",
                "<< /Length ${secondContent.length} >>\nstream\n${secondContent}endstream",
                "<< /Type /Annot /Subtype /Link /Rect [25 380 270 430] " +
                    "/Border [0 0 0] /Dest [6 0 R /Fit] >>",
            )
            val pdf = StringBuilder("%PDF-1.4\n")
            val offsets = objects.mapIndexed { index, body ->
                pdf.length.also { pdf.append("${index + 1} 0 obj\n$body\nendobj\n") }
            }
            // Cross the native 256 KiB chunk boundary without embedding a large binary fixture.
            pdf.append('%').append("x".repeat(300_000)).append('\n')
            val xref = pdf.length
            pdf.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { pdf.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
            pdf.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
            pdf.append("startxref\n$xref\n%%EOF\n")
            return pdf.toString().toByteArray(Charsets.US_ASCII)
        }

        private fun <T> onMain(block: () -> T): T {
            val result = AtomicReference<Result<T>>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                result.set(runCatching(block))
            }
            return result.get().getOrThrow()
        }
    }
}
