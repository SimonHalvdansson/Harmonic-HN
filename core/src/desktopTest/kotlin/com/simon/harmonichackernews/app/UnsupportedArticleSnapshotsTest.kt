package com.simon.harmonichackernews.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UnsupportedArticleSnapshotsTest {
    @Test
    fun aHostWithoutASnapshotStoreCannotSaveWebsiteContent() = runBlocking {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("HarmonicTest")
        try {
            assertFalse(bootstrap.app.supportsArticleSnapshots)
            assertFalse(bootstrap.app.storyCache.cacheArticle(42, "https://example.com/article"))
            assertNull(bootstrap.app.storyCache.loadArticle(42))
        } finally { bootstrap.close() }
    }
}
