package com.simon.harmonichackernews.desktop

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopCaptchaBrowserSessionTest {
    @Test
    fun initialBlankBrowserSnapshotCannotFailOrReplaceQueuedChallenge() = runBlocking {
        val host = BrowserFixture()
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(host, usesBaseUrl = false)
        session.update(host, loaded().copy(url = "about:blank"))
        session.poll()
        assertTrue(session.loading)
        assertNull(session.error)
        assertEquals(0, host.installCount)
        session.update(host, loaded())
        session.poll()
        assertTrue(session.ready)
    }

    @Test
    fun bootstrapRetainsOriginAndResponseUsesSharedJsonDecoder() = runBlocking {
        val host = BrowserFixture()
        val session = DesktopCaptchaBrowserSession("<p>Generated challenge</p>")
        session.attach(host, usesBaseUrl = false)
        assertEquals("https://news.ycombinator.com/newsfaq.html", host.loadedUrl)
        session.update(host, loaded())
        session.poll()
        assertTrue(session.ready)
        assertFalse(session.loading)
        assertEquals("fixture-\"token\"-λ", session.readResponse())
        assertEquals(1, host.installCount)
        session.poll()
        assertEquals(1, host.installCount, "Status updates must not recreate a solved browser document")
    }

    @Test
    fun redirectedBootstrapCannotSupplyCaptchaToken() = runBlocking {
        val host = BrowserFixture().apply { installationResult = "untrusted" }
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(host, usesBaseUrl = false)
        session.update(host, loaded())
        session.poll()
        assertFalse(session.ready)
        assertFalse(session.loading)
        assertTrue(session.error.orEmpty().contains("origin"))
        assertNull(session.readResponse())
        assertEquals(0, host.responseReadCount)
    }

    @Test
    fun sameOriginNonHtmlBootstrapCannotClaimChallengeInstallation() = runBlocking {
        val host = BrowserFixture().apply { installationResult = "unsupported-document" }
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(host, usesBaseUrl = false)
        session.update(host, loaded())
        session.poll()
        assertFalse(session.ready)
        assertTrue(session.error.orEmpty().contains("HTML page"))
        assertNull(session.readResponse())
        assertEquals(0, host.responseReadCount)
    }

    @Test
    fun expiredChallengeRequiresFreshBrowserWithoutSubmittingAnything() = runBlocking {
        val host = BrowserFixture()
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(host, usesBaseUrl = false)
        session.update(host, loaded())
        session.poll()
        host.status = "expired"
        session.poll()
        assertFalse(session.ready)
        assertTrue(session.error.orEmpty().contains("expired"))
        assertNull(session.readResponse())
        assertEquals(0, host.responseReadCount)
    }

    @Test
    fun changedServerPolicyFailsWithoutReadingOrSubmittingToken() = runBlocking {
        val host = BrowserFixture()
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(host, usesBaseUrl = false)
        session.update(host, loaded())
        session.poll()
        host.status = "policy-error"
        session.poll()
        assertFalse(session.ready)
        assertTrue(session.error.orEmpty().contains("blocked a required script or frame"))
        assertNull(session.readResponse())
        assertEquals(0, host.responseReadCount)
    }

    @Test
    fun closeRejectsLateNativeResponseAndSnapshots() = runBlocking {
        val host = BrowserFixture()
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(host, usesBaseUrl = false)
        session.update(host, loaded())
        session.poll()
        host.deferResponse = true
        val response = async(start = CoroutineStart.UNDISPATCHED) { session.readResponse() }
        assertNotNull(host.pendingResponse)
        session.close()
        host.pendingResponse?.invoke("\"late-token\"")
        assertNull(response.await())
        session.update(host, loaded().copy(loadError = "stale error"))
        assertNull(session.error)
    }

    @Test
    fun browserFailuresAndMissingEnginesHaveActionableRecovery() {
        val host = BrowserFixture()
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(host, usesBaseUrl = false)
        session.update(host, loaded().copy(loadError = "Connection refused"))
        assertFalse(session.loading)
        assertFalse(session.ready)
        assertTrue(session.error.orEmpty().contains("retry"))
        assertTrue(desktopCaptchaStartupError(Exception(), "Windows 11").contains("WebView2"))
        val linux = desktopCaptchaStartupError(Exception(), "Linux")
        assertTrue(linux.contains("WebKitGTK"))
        assertTrue(linux.contains("X11/XWayland"))
    }

    @Test
    fun oldHostCannotChangeReplacementBrowserState() = runBlocking {
        val first = BrowserFixture()
        val second = BrowserFixture()
        val session = DesktopCaptchaBrowserSession("<p>Challenge</p>")
        session.attach(first, usesBaseUrl = false)
        session.update(first, loaded())
        session.poll()
        session.attach(second, usesBaseUrl = false)
        session.update(first, loaded().copy(loadError = "old renderer failed"))
        assertNull(session.error)
        assertTrue(session.loading)
        session.update(second, loaded())
        session.poll()
        assertTrue(session.ready)
    }

    private fun loaded() = DesktopBrowserSnapshot(
        "https://news.ycombinator.com/", "CAPTCHA fixture", false, false, false,
    )

    private class BrowserFixture : DesktopBrowserHost {
        var loadedUrl: String? = null
        var installationResult = "installed"
        var status = "ready"
        var installCount = 0
        var responseReadCount = 0
        var deferResponse = false
        var pendingResponse: ((String?) -> Unit)? = null
        override fun loadUrl(url: String) { loadedUrl = url }
        override fun evaluateJavaScriptResult(script: String, result: (String?) -> Unit) {
            when {
                "document.write" in script -> { installCount++; result(installationResult) }
                "JSON.stringify" in script -> {
                    responseReadCount++
                    if (deferResponse) pendingResponse = result
                    else result("\"fixture-\\\"token\\\"-λ\"")
                }
                else -> result(status)
            }
        }
        override fun loadHtml(html: String) = Unit
        override fun navigateBack() = Unit
        override fun navigateForward() = Unit
        override fun reload(fallbackUrl: String) = Unit
        override fun setBrowserVisible(visible: Boolean) = Unit
        override fun evaluateJavaScript(script: String) = Unit
        override fun disposeBrowser() = Unit
    }
}
