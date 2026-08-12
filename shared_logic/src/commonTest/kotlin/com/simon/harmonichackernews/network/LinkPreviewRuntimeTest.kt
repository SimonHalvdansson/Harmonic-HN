package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.RepoInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LinkPreviewRuntimeTest {
    @Test
    fun selectsProviderAndPublishesPreview() = runTest {
        val repository = RecordingRepository()
        val runtime = LinkPreviewRuntime(this, LinkPreviewUseCase(repository))

        assertTrue(runtime.load(
            "https://github.com/example/project",
            preferences(github = true),
            alreadyLoaded = false,
        ))
        advanceUntilIdle()

        assertEquals("https://github.com/example/project", repository.loadedUrl)
        assertIs<LinkPreviewData.GitHub>(runtime.state.value.preview)
        assertFalse(runtime.state.value.loading)
    }

    @Test
    fun skipsDisabledAndAlreadyLoadedProviders() = runTest {
        val runtime = LinkPreviewRuntime(this, LinkPreviewUseCase(RecordingRepository()))

        assertFalse(runtime.load(
            "https://github.com/example/project",
            preferences(github = false),
            alreadyLoaded = false,
        ))
        assertFalse(runtime.load(
            "https://github.com/example/project",
            preferences(github = true),
            alreadyLoaded = true,
        ))
    }

    private fun preferences(github: Boolean) = LinkPreviewPreferences(
        arxiv = false,
        github = github,
        gitLab = false,
        stackExchange = false,
        wikipedia = false,
    )

    private class RecordingRepository : LinkPreviewRepository {
        var loadedUrl: String? = null

        override suspend fun getGitHubInfo(url: String): RepoInfo {
            loadedUrl = url
            return RepoInfo()
        }

        override suspend fun getArxivInfo(url: String) = error("Unexpected provider")
        override suspend fun getGitLabInfo(url: String) = error("Unexpected provider")
        override suspend fun getStackExchangeInfo(url: String) = error("Unexpected provider")
        override suspend fun getWikipediaInfo(url: String) = error("Unexpected provider")
        override suspend fun getArchiveUrl(url: String) = error("Unexpected provider")
    }
}
