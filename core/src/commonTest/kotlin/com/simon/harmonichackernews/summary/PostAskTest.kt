package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.SummaryRequest
import com.simon.harmonichackernews.platform.SummaryResult
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PostAskTest {
    @Test
    fun cloudSummaryRetainsTheExactSubmittedTextForExtractedAndProvidedSources() = runTest {
        for (provided in listOf(null, "  Browser extraction with additional context  ")) {
            val repo = Repository()
            val useCase = SummaryUseCase(repo)
            val backend = CloudStorySummaryBackend(useCase) { CloudSummaryConfig("url", "key", "model", inputCharacterLimit = 23) }
            val runtime = StorySummaryRuntime(this, backend, backend)
            runtime.start(StorySummaryMode.CLOUD, StorySummaryInput("https://example.com", provided))
            advanceUntilIdle()
            assertEquals((provided ?: repo.article).trim().take(23), runtime.state.value.sourceInput)
            assertEquals(repo.requests.single(), runtime.state.value.sourceInput)
            assertEquals(if (provided == null) 1 else 0, repo.extractions)
        }
    }

    @Test
    fun localExtractionRetainsTheSameSourcePassedToTheEngine() = runTest {
        val repo = Repository()
        var received: SummaryRequest? = null
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest): SummaryResult {
                received = request
                return SummaryResult("A short summary")
            }
        }
        val backend = ExtractingStorySummaryBackend(SummaryUseCase(repo), PlatformLocalStorySummaryBackend(engine))
        val runtime = StorySummaryRuntime(this, backend, backend)
        runtime.start(StorySummaryMode.LOCAL, StorySummaryInput("https://example.com"))
        advanceUntilIdle()
        assertEquals(repo.article, runtime.state.value.sourceInput)
        assertEquals(received?.text, runtime.state.value.sourceInput)
    }

    @Test
    fun postFollowUpsUseCapturedSourceAndHistoryWithoutRetrievalAndResetKeepsSource() = runTest {
        for (mode in listOf(AiSummaryMode.CLOUD, AiSummaryMode.LOCAL)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
            settings.setMode(mode)
            val repo = Repository()
            val localRequests = mutableListOf<SummaryRequest>()
            val engine = object : LocalSummaryEngine {
                override suspend fun isAvailable() = true
                override suspend fun summarize(request: SummaryRequest): SummaryResult {
                    localRequests += request
                    return SummaryResult("An answer")
                }
            }
            val source = AskSource.Post(
                StoryListItemSnapshot(StorySnapshot(42, title = "A post", url = "https://example.com"), StoryPresentationSnapshot()),
                input = "Original article with details missing from the summary.",
                summary = "The visible AI summary.",
            )
            val conversation = AskConversation(this, noNetwork, SummaryUseCase(repo), settings, engine,
                source, contextDispatcher = dispatcher)
            conversation.ask("What details were left out?")
            advanceUntilIdle()
            conversation.ask("Give me an example")
            advanceUntilIdle()
            val input = if (mode == AiSummaryMode.CLOUD) repo.requests.last() else localRequests.last().text
            val json = Json.parseToJsonElement(input).jsonObject
            assertEquals(source.input, json["source_context"]!!.jsonObject["summary_source"]!!.jsonPrimitive.content)
            assertEquals(source.summary, json["source_context"]!!.jsonObject["displayed_summary"]!!.jsonPrimitive.content)
            assertEquals(3, json["conversation"]!!.jsonArray.size)
            assertEquals(0, repo.extractions, "Ask must never retrieve a different version of the article")
            val prompt = if (mode == AiSummaryMode.CLOUD) repo.configs.last().systemPrompt else localRequests.last().prompt
            assertTrue(assertNotNull(prompt).contains("summary_source"))
            assertFalse(assertNotNull(prompt).contains("Focus on selected_comment"))
            assertTrue(AskConversation.suggestedQuestions(source).none { "comment" in it })
            conversation.reset()
            conversation.ask("Explain it again")
            advanceUntilIdle()
            val resetInput = if (mode == AiSummaryMode.CLOUD) repo.requests.last() else localRequests.last().text
            val resetJson = Json.parseToJsonElement(resetInput).jsonObject
            assertEquals(1, resetJson["conversation"]!!.jsonArray.size)
            assertEquals(json["source_context"], resetJson["source_context"])
            if (mode == AiSummaryMode.LOCAL) assertTrue(localRequests.all { it.preserveInput && !it.useGeminiNanoSummarizationLora })
        }
    }

    private class Repository : CloudSummaryRepository {
        val article = "  Extracted article with details and evidence beyond its summary  "
        var extractions = 0
        val requests = mutableListOf<String>()
        val configs = mutableListOf<CloudSummaryConfig>()
        override suspend fun fetchModelIds(baseUrl: String, apiKey: String) = emptyList<String>()
        override suspend fun extractMainContent(url: String): String { extractions++; return article }
        override fun summarize(config: CloudSummaryConfig, text: String?) = flow {
            requests += text.orEmpty()
            configs += config
            emit(CloudSummaryEvent.Success("An answer"))
        }
    }

    private val noNetwork = object : HackerNewsApi {
        override suspend fun getItem(id: Int) = error("Unexpected HN retrieval")
        override suspend fun getUser(username: String) = error("Unexpected HN retrieval")
        override suspend fun getMaxItemId(): Int = error("Unexpected HN retrieval")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unexpected HN retrieval")
    }
}
