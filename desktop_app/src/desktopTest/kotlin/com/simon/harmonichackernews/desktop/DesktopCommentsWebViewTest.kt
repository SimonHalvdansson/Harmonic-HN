package com.simon.harmonichackernews.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCommentsWebViewTest {
    @Test
    fun firstNavigationFailureStopsLoadingAndRetryCanRecover() {
        val session = DesktopCommentsWebViewSession("http://127.0.0.1:1/article")
        val host = RecordingBrowser()
        session.attach(host)
        session.updateFromBrowser(host, DesktopBrowserSnapshot(
            "", null, false, false, false, "Could not connect to the server",
        ))
        assertFalse(session.isLoading)
        assertEquals("Could not connect to the server", session.loadError)
        assertEquals("http://127.0.0.1:1/article", session.currentUrl())
        session.reload()
        assertTrue(session.isLoading)
        assertEquals(null, session.loadError)
        assertEquals(session.currentUrl(), host.reloaded)
        session.updateFromBrowser(host, DesktopBrowserSnapshot(
            session.currentUrl(), "Recovered article", false, false, false,
        ))
        assertFalse(session.isLoading)
        assertTrue(session.hasLoadedContent)
        assertEquals("Recovered article", session.pageTitle)
        session.detach(host)
        session.updateFromBrowser(host, DesktopBrowserSnapshot(
            "http://stale.invalid", null, false, false, false, "Stale failure",
        ))
        assertEquals(null, session.loadError)
    }

    private class RecordingBrowser : DesktopBrowserHost {
        var reloaded: String? = null
        override fun loadUrl(url: String) = Unit
        override fun loadHtml(html: String) = Unit
        override fun evaluateJavaScriptResult(script: String, result: (String?) -> Unit) = result(null)
        override fun navigateBack() = Unit
        override fun navigateForward() = Unit
        override fun reload(fallbackUrl: String) { reloaded = fallbackUrl }
        override fun setBrowserVisible(visible: Boolean) = Unit
        override fun evaluateJavaScript(script: String) = Unit
        override fun disposeBrowser() = Unit
    }

    @Test
    fun unsupportedHostsDoNotOfferIntegratedBrowserSettings() {
        val capabilities = desktopWebLinksSettingsCapabilities(desktopEmbeddedBrowserBackend("Linux"))
        assertFalse(capabilities.integratedWebView)
        assertFalse(capabilities.readerMode)
        assertFalse(capabilities.adBlocking)
        assertFalse(capabilities.preloadWebsites)
    }

    @Test
    fun supportedDesktopBrowsersOfferOnlyImplementedFacilities() {
        listOf("Windows 11", "Mac OS X").forEach { os ->
            val capabilities = desktopWebLinksSettingsCapabilities(desktopEmbeddedBrowserBackend(os))
            assertTrue(capabilities.integratedWebView)
            assertTrue(capabilities.readerMode)
            assertFalse(capabilities.adBlocking)
            assertFalse(capabilities.preloadWebsites)
            assertFalse(capabilities.closeWebViewOnBack)
        }
    }

    @Test
    fun matchingDarkThemeRequestsNativeColorsWithoutInvertingMedia() {
        val script = desktopWebViewAppearanceScript(dark = true, matchTheme = true, manualInversion = false)
        assertTrue(script.contains("color-scheme:dark"))
        assertFalse(script.contains("invert(1)"))
        assertFalse(script.contains("backgroundColor"))
    }

    @Test
    fun explicitInversionWorksIndependentlyOfThemeMatching() {
        val script = desktopWebViewAppearanceScript(dark = false, matchTheme = false, manualInversion = true)
        assertTrue(script.contains("invert(1)"))
        assertFalse(script.contains("color-scheme:"))
        val cleared = desktopWebViewAppearanceScript(dark = true, matchTheme = false, manualInversion = false)
        assertTrue(cleared.contains("style.textContent=''"))
    }

    @Test
    fun desktopBrowserBackendsStayPlatformSpecific() {
        assertEquals(
            DesktopEmbeddedBrowserBackend.WINDOWS_EDGE,
            desktopEmbeddedBrowserBackend("Windows 11"),
        )
        assertEquals(
            DesktopEmbeddedBrowserBackend.MAC_WEBKIT,
            desktopEmbeddedBrowserBackend("Mac OS X"),
        )
        assertEquals(
            DesktopEmbeddedBrowserBackend.UNSUPPORTED,
            desktopEmbeddedBrowserBackend("Linux"),
        )
    }

    @Test
    fun commentsUseHackerNewsStoryTitleBeforeArticleLoads() {
        assertEquals(
            "The Hacker News title",
            desktopWebViewToolbarTitle(
                showWebsite = false,
                storyTitle = "The Hacker News title",
                pageTitle = null,
                currentPageUrl = "https://example.com/article",
            ),
        )
    }

    @Test
    fun commentsKeepHackerNewsStoryTitleAfterArticleLoads() {
        assertEquals(
            "The Hacker News title",
            desktopWebViewToolbarTitle(
                showWebsite = false,
                storyTitle = "The Hacker News title",
                pageTitle = "The website title",
                currentPageUrl = "https://example.com/article",
            ),
        )
    }

    @Test
    fun articleUsesResolvedWebsiteTitle() {
        assertEquals(
            "The website title",
            desktopWebViewToolbarTitle(
                showWebsite = true,
                storyTitle = "The Hacker News title",
                pageTitle = "The website title",
                currentPageUrl = "https://example.com/article",
            ),
        )
    }

    @Test
    fun missingPreferredTitleFallsBackToUrl() {
        assertEquals(
            "https://example.com/article",
            desktopWebViewToolbarTitle(
                showWebsite = false,
                storyTitle = "",
                pageTitle = null,
                currentPageUrl = "https://example.com/article",
            ),
        )
    }
}
