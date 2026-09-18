package com.simon.harmonichackernews.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCommentsWebViewTest {
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
            assertFalse(capabilities.readerMode)
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
