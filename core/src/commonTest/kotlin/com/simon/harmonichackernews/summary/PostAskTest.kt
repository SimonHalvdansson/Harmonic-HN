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
    fun postFollowUpsUseAvailableArticleSummaryAndHistoryWithoutRetrieval() = runTest {
        for (mode in listOf(AiSummaryMode.CLOUD, AiSummaryMode.LOCAL)) {
          for (article in listOf(null, "", "The original article supplies additional evidence.")) {
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
                summary = "The visible AI summary.",
                articleText = article,
            )
            val conversation = AskConversation(this, noNetwork, SummaryUseCase(repo), settings, engine,
                source, contextDispatcher = dispatcher)
            conversation.ask("What details were left out?")
            advanceUntilIdle()
            conversation.ask("Give me an example")
            advanceUntilIdle()
            val input = if (mode == AiSummaryMode.CLOUD) repo.requests.last() else localRequests.last().text
            val json = Json.parseToJsonElement(input).jsonObject
            assertNull(json["source_context"]!!.jsonObject["summary_source"])
            assertEquals(!article.isNullOrBlank(), json["source_context"]!!.jsonObject["original_article_included"]!!.jsonPrimitive.boolean)
            assertEquals(article?.takeIf { it.isNotBlank() }, json["source_context"]!!.jsonObject["article_text"]?.jsonPrimitive?.content)
            assertEquals(!article.isNullOrBlank(), conversation.state.value.articleIncluded)
            assertEquals(source.summary, json["source_context"]!!.jsonObject["displayed_summary"]!!.jsonPrimitive.content)
            assertEquals(3, json["conversation"]!!.jsonArray.size)
            assertEquals(0, repo.extractions, "Ask must never retrieve a different version of the article")
            val prompt = if (mode == AiSummaryMode.CLOUD) repo.configs.last().systemPrompt else localRequests.last().prompt
            assertTrue(assertNotNull(prompt).contains("displayed_summary"))
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
    }

    @Test
    fun articleNearTheLimitFallsBackBeforeAnsweringAndResetRechecksIt() = runTest {
        for (mode in listOf(AiSummaryMode.LOCAL, AiSummaryMode.CLOUD)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
            settings.setMode(mode)
            val repo = Repository(rejectArticle = true)
            val requests = mutableListOf<SummaryRequest>()
            val engine = object : LocalSummaryEngine {
                override suspend fun isAvailable() = true
                override suspend fun summarize(request: SummaryRequest): SummaryResult {
                    requests += request
                    if (request.text.contains("article_text")) {
                        assertTrue(request.reserveDiscussionSpace)
                        error("Input token count exceeds the model's context limit")
                    }
                    assertFalse(request.reserveDiscussionSpace)
                    return SummaryResult("answer")
                }
            }
            val source = AskSource.Post(
                StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot()), "Summary", "Article",
            )
            val conversation = AskConversation(this, noNetwork, SummaryUseCase(repo), settings, engine,
                source, builtInModelSelected = { true }, contextDispatcher = dispatcher)
            conversation.ask("first")
            advanceUntilIdle()
            assertFalse(assertNotNull(conversation.state.value.articleIncluded))
            assertNull(conversation.state.value.error)
            conversation.ask("second")
            advanceUntilIdle()
            val inputs = if (mode == AiSummaryMode.LOCAL) requests.map { it.text } else repo.requests
            assertEquals(3, inputs.size, "Article is retried once, then omitted from follow-ups")
            assertEquals(3, Json.parseToJsonElement(inputs.last()).jsonObject["conversation"]!!.jsonArray.size)
            assertEquals(0, conversation.state.value.omittedTurns)
            conversation.reset()
            assertNull(conversation.state.value.articleIncluded)
            conversation.ask("fresh")
            advanceUntilIdle()
            assertEquals(5, if (mode == AiSummaryMode.LOCAL) requests.size else repo.requests.size)
        }
    }

    @Test
    fun providersWithoutTokenCountingOnlyReceiveSmallArticleContexts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.CLOUD)
        val repo = Repository()
        val source = AskSource.Post(
            StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot()), "Summary", "x".repeat(3_000),
        )
        val conversation = AskConversation(this, noNetwork, SummaryUseCase(repo), settings, null,
            source, contextDispatcher = dispatcher)
        conversation.ask("Explain")
        advanceUntilIdle()
        assertFalse(repo.requests.single().contains("article_text"))
        assertEquals(false, conversation.state.value.articleIncluded)
        assertTrue(conversation.state.value.turns.single().complete)
    }

    @Test
    fun laterContextPressureDropsArticleBeforeConversationHistory() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.LOCAL)
        val requests = mutableListOf<SummaryRequest>()
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest): SummaryResult {
                requests += request
                val json = Json.parseToJsonElement(request.text).jsonObject
                if (json["conversation"]!!.jsonArray.size > 1 && request.text.contains("article_text")) {
                    assertFalse(request.reserveDiscussionSpace, "Follow-ups can use the reserved space")
                    error("context window exceeded")
                }
                return SummaryResult("answer")
            }
        }
        val source = AskSource.Post(
            StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot()), "Summary", "Article",
        )
        val conversation = AskConversation(this, noNetwork, SummaryUseCase(Repository()), settings, engine,
            source, contextDispatcher = dispatcher)
        conversation.ask("first")
        advanceUntilIdle()
        assertEquals(true, conversation.state.value.articleIncluded)
        assertTrue(requests.first().reserveDiscussionSpace)
        conversation.ask("second")
        advanceUntilIdle()
        assertEquals(3, requests.size)
        assertEquals(false, conversation.state.value.articleIncluded)
        assertEquals(0, conversation.state.value.omittedTurns)
        assertEquals(3, Json.parseToJsonElement(requests.last().text).jsonObject["conversation"]!!.jsonArray.size)
    }

    @Test
    fun oversizedHistoryDropsWholeOldestTurnsAndKeepsVisibleTranscript() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.LOCAL)
        val requests = mutableListOf<JsonObject>()
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest): SummaryResult {
                val json = Json.parseToJsonElement(request.text).jsonObject
                requests += json
                if (json["conversation"]!!.jsonArray.size > 3) {
                    error("Input text length exceeds the limit. Please check the countTokens API.")
                }
                return SummaryResult("answer")
            }
        }
        val source = AskSource.Post(
            StoryListItemSnapshot(StorySnapshot(42, title = "Post"), StoryPresentationSnapshot()), "Summary",
        )
        val conversation = AskConversation(this, noNetwork, SummaryUseCase(Repository()), settings, engine,
            source, contextDispatcher = dispatcher)
        listOf("first", "second", "third").forEach { conversation.ask(it); advanceUntilIdle() }
        assertEquals(4, requests.size, "Only the oversized request is retried")
        assertEquals(3, conversation.state.value.turns.size, "The visible history is retained")
        assertTrue(conversation.state.value.turns.all { it.complete })
        assertEquals(1, conversation.state.value.omittedTurns)
        val kept = requests.last()["conversation"]!!.jsonArray
        assertEquals(listOf("second", "answer", "third"), kept.map { it.jsonObject["content"]!!.jsonPrimitive.content })
        assertEquals(requests.first()["source_context"], requests.last()["source_context"])
        conversation.reset()
        assertEquals(0, conversation.state.value.omittedTurns)
        conversation.ask("fresh")
        advanceUntilIdle()
        assertEquals(1, requests.last()["conversation"]!!.jsonArray.size)
    }

    @Test
    fun sourceTooLargeDoesNotRetryForeverAndShorterQuestionCanRecover() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.LOCAL)
        var calls = 0
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest): SummaryResult {
                calls++
                if (calls == 1) error("Input token count exceeds the model's context limit")
                val turns = Json.parseToJsonElement(request.text).jsonObject["conversation"]!!.jsonArray
                assertEquals(1, turns.size, "Failed questions must not accumulate in the request")
                return SummaryResult("answer")
            }
        }
        val source = AskSource.Post(
            StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot()), "Summary",
        )
        val conversation = AskConversation(this, noNetwork, SummaryUseCase(Repository()), settings, engine,
            source, contextDispatcher = dispatcher)
        conversation.ask("long question")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(conversation.state.value.inputTooLarge)
        conversation.retry()
        advanceUntilIdle()
        assertEquals(1, calls)
        conversation.ask("short")
        advanceUntilIdle()
        assertFalse(conversation.state.value.inputTooLarge)
        assertTrue(conversation.state.value.turns.last().complete)
    }

    @Test
    fun partialAnswersAreNotSilentlyRegeneratedOnContextErrors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.LOCAL)
        var calls = 0
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest) = error("Use streaming")
            override fun summarizeEvents(request: SummaryRequest) = flow {
                calls++
                if (calls == 1) emit(StorySummaryEvent.Success("first answer")) else {
                    emit(StorySummaryEvent.Progress("partial answer"))
                    emit(StorySummaryEvent.Failure("context window exceeded"))
                }
            }
        }
        val source = AskSource.Post(
            StoryListItemSnapshot(StorySnapshot(42), StoryPresentationSnapshot()), "Summary",
        )
        val conversation = AskConversation(this, noNetwork, SummaryUseCase(Repository()), settings, engine,
            source, contextDispatcher = dispatcher)
        conversation.ask("first")
        advanceUntilIdle()
        conversation.ask("second")
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("partial answer", conversation.state.value.turns.last().answer)
        assertTrue(conversation.state.value.contextLimitReached)
        assertFalse(conversation.state.value.inputTooLarge)
    }

    private class Repository(val rejectArticle: Boolean = false) : CloudSummaryRepository {
        val article = "  Extracted article with details and evidence beyond its summary  "
        var extractions = 0
        val requests = mutableListOf<String>()
        val configs = mutableListOf<CloudSummaryConfig>()
        override suspend fun fetchModelIds(baseUrl: String, apiKey: String) = emptyList<String>()
        override suspend fun extractMainContent(url: String): String { extractions++; return article }
        override fun summarize(config: CloudSummaryConfig, text: String?) = flow {
            requests += text.orEmpty()
            configs += config
            if (rejectArticle && text.orEmpty().contains("article_text")) error("context window exceeded")
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
