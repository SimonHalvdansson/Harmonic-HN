package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.platform.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CommentQuestionSuggestionsTest {
    private val questions = listOf("How does an inversion bend light?", "Why count the lighthouse flashes?")
    private val response = "[\"${questions[0]}\", \"${questions[1]}\"]"

    @Test
    fun parsesJsonAndNumberedResponsesButRejectsIncompleteOrDuplicateSuggestions() {
        assertEquals(questions, CommentQuestionSuggestions.parse("```json\n$response\n```", emptyList()))
        assertEquals(questions, CommentQuestionSuggestions.parse("1. ${questions[0]}\n2. ${questions[1]}", emptyList()))
        assertTrue(CommentQuestionSuggestions.parse(response, listOf(questions[0])).isEmpty())
        assertTrue(CommentQuestionSuggestions.parse("[\"${questions[0]}\", \"${questions[0]}\"]", emptyList()).isEmpty())
        assertTrue(CommentQuestionSuggestions.parse("Sorry, I cannot help.", emptyList()).isEmpty())
    }

    @Test
    fun onlyBuiltInLocalModelsGenerateSuggestions() = runTest {
        for ((mode, builtIn) in listOf(AiSummaryMode.CLOUD to true, AiSummaryMode.LOCAL to false)) {
            val engine = Engine { error("Must not run automatic inference") }
            val discussion = discussion(engine, mode, builtIn)
            discussion.generateSuggestedQuestions()
            advanceUntilIdle()
            assertEquals(0, engine.calls)
            assertFalse(discussion.state.value.loadingSuggestions)
        }
    }

    @Test
    fun completedSuggestionsAreCachedAcrossResetAndUseCommentContext() = runTest {
        val engine = Engine { request ->
            assertTrue(request.preserveInput)
            assertFalse(request.useGeminiNanoSummarizationLora)
            assertTrue(request.text.contains("selected_comment"))
            assertTrue(request.text.contains("temperature inversion"))
            SummaryResult(response)
        }
        val discussion = discussion(engine)
        discussion.generateSuggestedQuestions()
        advanceUntilIdle()
        assertEquals(questions, discussion.state.value.suggestedQuestions)
        discussion.reset()
        discussion.generateSuggestedQuestions()
        advanceUntilIdle()
        assertEquals(questions, discussion.state.value.suggestedQuestions)
        assertEquals(1, engine.calls)
    }

    @Test
    fun sendingAQuestionCancelsSuggestionsBeforeAnswering() = runTest {
        var cancelled = false
        val engine = Engine { request ->
            if (request.prompt == CommentQuestionSuggestions.PROMPT) {
                try { awaitCancellation() } finally { cancelled = true }
            } else {
                assertTrue(cancelled)
                SummaryResult("An inversion can bend light.")
            }
        }
        val discussion = discussion(engine)
        discussion.generateSuggestedQuestions()
        runCurrent()
        assertTrue(discussion.state.value.loadingSuggestions)
        discussion.ask("Explain this comment")
        advanceUntilIdle()
        assertFalse(discussion.state.value.loadingSuggestions)
        assertTrue(discussion.state.value.suggestedQuestions.isEmpty())
        assertEquals("An inversion can bend light.", discussion.state.value.turns.single().answer)
        assertNull(discussion.state.value.error)
    }

    @Test
    fun closingCancelsAndReopeningRestartsButTimeoutDoesNotBlockChat() = runTest {
        val engine = Engine { awaitCancellation() }
        val discussion = discussion(engine)
        discussion.generateSuggestedQuestions()
        runCurrent()
        discussion.cancelSuggestedQuestions()
        runCurrent()
        assertFalse(discussion.state.value.loadingSuggestions)
        discussion.generateSuggestedQuestions()
        runCurrent()
        assertEquals(2, engine.calls)
        advanceUntilIdle()
        assertFalse(discussion.state.value.loadingSuggestions)
        assertNull(discussion.state.value.error)
        assertFalse(discussion.state.value.running)
    }

    @Test
    fun mockModeShowsDelayedSuggestionsWithoutInference() = runTest {
        val engine = Engine { error("Mock mode must not use the model") }
        val discussion = discussion(engine, AiSummaryMode.CLOUD, builtIn = false, mock = true)
        discussion.generateSuggestedQuestions()
        runCurrent()
        assertTrue(discussion.state.value.loadingSuggestions)
        assertTrue(discussion.state.value.suggestedQuestions.isEmpty())
        advanceUntilIdle()
        assertEquals(2, discussion.state.value.suggestedQuestions.size)
        assertFalse(discussion.state.value.loadingSuggestions)
        assertEquals(0, engine.calls)
    }

    private fun TestScope.discussion(
        engine: LocalSummaryEngine,
        mode: AiSummaryMode = AiSummaryMode.LOCAL,
        builtIn: Boolean = true,
        mock: Boolean = false,
    ): CommentDiscussion {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(mode)
        return CommentDiscussion(
            this,
            object : HackerNewsApi {
                override suspend fun getItem(id: Int) = error("Unexpected network call")
                override suspend fun getUser(username: String) = error("Unexpected network call")
                override suspend fun getMaxItemId(): Int = error("Unexpected network call")
                override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unexpected network call")
            },
            SummaryUseCase(object : CloudSummaryRepository {
                override suspend fun fetchModelIds(baseUrl: String, apiKey: String) = error("Unexpected cloud call")
                override suspend fun extractMainContent(url: String) = error("Unexpected cloud call")
                override fun summarize(config: CloudSummaryConfig, text: String?) = error("Unexpected cloud call")
            }),
            settings, engine,
            StoryListItemSnapshot(StorySnapshot(1, title = "Lighthouses"), StoryPresentationSnapshot()),
            PortableCommentItem(CommentSnapshot(2, parentId = 1, text = "Could a temperature inversion explain this?"),
                CommentPresentationSnapshot()),
            emptyList(), mockAnswers = { mock }, builtInModelSelected = { builtIn }, contextDispatcher = dispatcher,
        )
    }

    private class Engine(private val answer: suspend (SummaryRequest) -> SummaryResult) : LocalSummaryEngine {
        var calls = 0
        override suspend fun isAvailable() = true
        override suspend fun summarize(request: SummaryRequest): SummaryResult {
            calls++
            return answer(request)
        }
    }
}
