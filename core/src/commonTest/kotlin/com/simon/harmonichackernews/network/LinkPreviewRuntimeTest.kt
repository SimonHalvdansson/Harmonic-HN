package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.HuggingFaceModelInfo
import com.simon.harmonichackernews.data.OpenRouterModelInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.loadedLinkPreviewType

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

    @Test
    fun selectsHuggingFaceModelProvider() = runTest {
        val repository = RecordingRepository()
        val runtime = LinkPreviewRuntime(this, LinkPreviewUseCase(repository))

        assertTrue(runtime.load(
            "https://huggingface.co/moonshotai/Kimi-K3",
            preferences(github = false, huggingFace = true),
            alreadyLoaded = false,
        ))
        advanceUntilIdle()

        assertEquals("https://huggingface.co/moonshotai/Kimi-K3", repository.loadedUrl)
        assertIs<LinkPreviewData.HuggingFace>(runtime.state.value.preview)
    }

    @Test
    fun selectsOpenRouterModelProvider() = runTest {
        val repository = RecordingRepository()
        val runtime = LinkPreviewRuntime(this, LinkPreviewUseCase(repository))

        assertTrue(runtime.load(
            "https://openrouter.ai/openai/gpt-5.6-sol",
            preferences(github = false, openRouter = true),
            alreadyLoaded = false,
        ))
        advanceUntilIdle()

        assertEquals("https://openrouter.ai/openai/gpt-5.6-sol", repository.loadedUrl)
        assertIs<LinkPreviewData.OpenRouter>(runtime.state.value.preview)
    }

    @Test
    fun previewPayloadCarriesItsTypeAndAppliesToAStory() {
        val story = Story()
        val preview = LinkPreviewData.Rich(
            LinkPreviewInfo(
                type = LinkPreviewType.CROSSREF_ARTICLE,
                title = "Article",
                url = "https://doi.org/10.1000/example",
            ),
        )

        preview.applyTo(story)

        assertEquals(LinkPreviewType.CROSSREF_ARTICLE, preview.type)
        assertEquals(LinkPreviewType.CROSSREF_ARTICLE, story.loadedLinkPreviewType())
    }

    @Test
    fun timeoutClearsLoadingState() = runTest {
        val repository = object : RecordingRepository() {
            override suspend fun getRichInfo(type: LinkPreviewType, url: String): LinkPreviewInfo =
                withTimeout(1) {
                    delay(10)
                    LinkPreviewInfo(type = type, title = "Too late", url = url)
                }
        }
        val runtime = LinkPreviewRuntime(this, LinkPreviewUseCase(repository))

        assertTrue(runtime.load(
            "https://doi.org/10.1000/example",
            LinkPreviewPreferences(setOf(LinkPreviewType.CROSSREF_ARTICLE)),
            alreadyLoaded = false,
        ))
        advanceUntilIdle()

        assertFalse(runtime.state.value.loading)
        assertEquals("Preview timed out", runtime.state.value.failure)
    }

    private fun preferences(
        github: Boolean,
        huggingFace: Boolean = false,
        openRouter: Boolean = false,
    ) = LinkPreviewPreferences(buildSet {
        if (github) add(LinkPreviewType.GITHUB_REPOSITORY)
        if (huggingFace) add(LinkPreviewType.HUGGING_FACE_MODEL)
        if (openRouter) add(LinkPreviewType.OPENROUTER_MODEL)
    })

    private open class RecordingRepository : LinkPreviewRepository {
        var loadedUrl: String? = null

        override suspend fun getGitHubInfo(url: String): RepoInfo {
            loadedUrl = url
            return RepoInfo()
        }

        override suspend fun getArxivInfo(url: String) = error("Unexpected provider")
        override suspend fun getGitLabInfo(url: String) = error("Unexpected provider")
        override suspend fun getHuggingFaceInfo(url: String): HuggingFaceModelInfo {
            loadedUrl = url
            return HuggingFaceModelInfo()
        }
        override suspend fun getOpenRouterInfo(url: String): OpenRouterModelInfo {
            loadedUrl = url
            return OpenRouterModelInfo()
        }
        override suspend fun getStackExchangeInfo(url: String) = error("Unexpected provider")
        override suspend fun getWikipediaInfo(url: String) = error("Unexpected provider")
        override suspend fun getArchiveUrl(url: String) = error("Unexpected provider")
    }
}
