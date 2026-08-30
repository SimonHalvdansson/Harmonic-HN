package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.network.LinkPreviewData
import com.simon.harmonichackernews.network.LinkPreviewRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ArchiveUrlResolverTest {
    @Test
    fun directProvidersEncodeTheArticleUrl() = runTest {
        val resolver = ArchiveUrlResolver(Repository())

        assertEquals(
            "https://archive.is/newest/https%3A%2F%2Fexample.com%2Fa%20b%3Fx%3D1%26y%3D2",
            resolver.resolve(ArchiveProvider.IS, "https://example.com/a b?x=1&y=2"),
        )
        assertEquals(
            "https://archive.today/newest/https%3A%2F%2Fexample.com%2Fa%20b%3Fx%3D1%26y%3D2",
            resolver.resolve(ArchiveProvider.TODAY, "https://example.com/a b?x=1&y=2"),
        )
        assertEquals(
            "https://archive.ph/newest/https%3A%2F%2Fexample.com%2Fa%20b%3Fx%3D1%26y%3D2",
            resolver.resolve(ArchiveProvider.PH, "https://example.com/a b?x=1&y=2"),
        )
    }

    @Test
    fun archiveOrgUsesTheRepositoryLookup() = runTest {
        assertEquals(
            "https://web.archive.org/example",
            ArchiveUrlResolver(Repository()).resolve(ArchiveProvider.ORG, "https://example.com"),
        )
    }

    private class Repository : LinkPreviewRepository {
        override suspend fun load(type: LinkPreviewType, url: String): LinkPreviewData =
            error("Not used")

        override suspend fun getArchiveUrl(url: String) = "https://web.archive.org/example"
    }
}
