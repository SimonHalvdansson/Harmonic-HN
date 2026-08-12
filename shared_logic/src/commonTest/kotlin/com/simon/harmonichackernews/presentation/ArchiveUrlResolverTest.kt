package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.data.WikipediaInfo
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
    }

    @Test
    fun archiveOrgUsesTheRepositoryLookup() = runTest {
        assertEquals(
            "https://web.archive.org/example",
            ArchiveUrlResolver(Repository()).resolve(ArchiveProvider.ORG, "https://example.com"),
        )
    }

    private class Repository : LinkPreviewRepository {
        override suspend fun getArchiveUrl(url: String) = "https://web.archive.org/example"
        override suspend fun getArxivInfo(url: String): ArxivInfo = error("Not used")
        override suspend fun getGitHubInfo(url: String): RepoInfo = error("Not used")
        override suspend fun getGitLabInfo(url: String): GitLabInfo = error("Not used")
        override suspend fun getStackExchangeInfo(url: String): StackExchangeInfo = error("Not used")
        override suspend fun getWikipediaInfo(url: String): WikipediaInfo = error("Not used")
    }
}
