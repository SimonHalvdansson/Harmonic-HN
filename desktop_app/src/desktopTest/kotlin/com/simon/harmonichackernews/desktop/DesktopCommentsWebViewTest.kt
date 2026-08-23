package com.simon.harmonichackernews.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopCommentsWebViewTest {
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
