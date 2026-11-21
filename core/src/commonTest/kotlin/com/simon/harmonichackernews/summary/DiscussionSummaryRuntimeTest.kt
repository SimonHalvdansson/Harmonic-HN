package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.network.CloudSummaryConfig
import com.simon.harmonichackernews.network.CloudSummaryEvent
import com.simon.harmonichackernews.network.CloudSummaryRepository
import com.simon.harmonichackernews.network.SummaryUseCase
import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.SummaryRequest
import com.simon.harmonichackernews.platform.SummaryResult
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.AiSummaryTextSetting
import com.simon.harmonichackernews.settings.TestCredentialStore
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionSummaryRuntimeTest {
    private val source = (1..20).map { id ->
        PortableCommentItem(CommentSnapshot(id, parentId = 1, text = "Comment ".repeat(45)),
            CommentPresentationSnapshot(sortOrder = id))
    }
    private val summaries = SummaryUseCase(object : CloudSummaryRepository {
        override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> = error("No model lookup")
        override suspend fun extractMainContent(url: String): String = error("No article extraction")
        override fun summarize(config: CloudSummaryConfig, text: String?) = error("No Cloud calls")
    })

    @Test fun retriesContextLimitAndReopensResultWithoutAnotherRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.LOCAL)
        val requests = mutableListOf<SummaryRequest>()
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest): SummaryResult {
                requests += request
                if (requests.size == 1) error("Context limit")
                return SummaryResult("Discussion result")
            }
        }
        val runtime = DiscussionSummaryRuntime(this, settings, summaries, engine, { false }, dispatcher)
        runtime.open("Title", null, source, false, false)
        runtime.open("Title", null, source, false, false)
        advanceUntilIdle()
        assertEquals("Discussion result", runtime.state.value.text)
        assertEquals(2, requests.size)
        assertTrue(requests[1].text.length < requests[0].text.length)
        assertTrue(requests.all { it.preserveInput && !it.useGeminiNanoSummarizationLora })
        runtime.dismiss()
        runtime.open("Title", null, source, false, false)
        advanceUntilIdle()
        assertTrue(runtime.state.value.visible)
        assertEquals(2, requests.size)
    }

    @Test fun dismissCancelsStreamingRequestAndResetsState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.LOCAL)
        var cancelled = false
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable() = true
            override suspend fun summarize(request: SummaryRequest): SummaryResult = error("Expected streaming")
            override fun summarizeEvents(request: SummaryRequest) = flow {
                try {
                    emit(StorySummaryEvent.Progress("Partial"))
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val runtime = DiscussionSummaryRuntime(this, settings, summaries, engine, { false }, dispatcher)
        runtime.open("Title", null, source, true, false)
        advanceUntilIdle()
        assertTrue(runtime.state.value.running)
        runtime.dismiss()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertFalse(runtime.state.value.visible)
        assertEquals("", runtime.state.value.text)
    }

    @Test fun ninetyOneLongCommentsRecoverWhenCloudNeverStartsStreaming() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.CLOUD)
        settings.setBaseUrl("https://example.com/v1")
        settings.setModel("slow-model")
        assertTrue(settings.setText(AiSummaryTextSetting.API_KEY, "test-key"))
        val lengths = mutableListOf<Int>()
        val cloud = SummaryUseCase(object : CloudSummaryRepository {
            override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> = error("Not used")
            override suspend fun extractMainContent(url: String): String = error("Not used")
            override fun summarize(config: CloudSummaryConfig, text: String?) = flow {
                lengths += text.orEmpty().length
                if (lengths.size == 1) awaitCancellation()
                emit(CloudSummaryEvent.Success("Recovered on smaller input"))
            }
        })
        val comments = listOf(source.first()) + (2..92).map { id ->
            PortableCommentItem(CommentSnapshot(id, parentId = 1, text = "Long reply ".repeat(200)),
                CommentPresentationSnapshot(sortOrder = id))
        }
        val runtime = DiscussionSummaryRuntime(this, settings, cloud, null, { false }, dispatcher,
            cloudFirstOutputTimeoutMillis = 100)
        runtime.open("Post", null, comments, false, false)
        advanceUntilIdle()
        assertEquals(2, lengths.size)
        assertTrue(lengths[1] < lengths[0])
        assertEquals("Recovered on smaller input", runtime.state.value.text)
        assertEquals(null, runtime.state.value.error)
        assertEquals("Summary complete", runtime.state.value.progressMessage)
    }

    @Test fun ninetyOneShortCommentsFailClearlyInsteadOfRepeatedlyWaitingOnSameInput() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.CLOUD)
        settings.setBaseUrl("https://example.com/v1")
        settings.setModel("unresponsive-model")
        assertTrue(settings.setText(AiSummaryTextSetting.API_KEY, "test-key"))
        var requests = 0
        val cloud = SummaryUseCase(object : CloudSummaryRepository {
            override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> = error("Not used")
            override suspend fun extractMainContent(url: String): String = error("Not used")
            override fun summarize(config: CloudSummaryConfig, text: String?) = flow<CloudSummaryEvent> {
                requests++
                awaitCancellation()
            }
        })
        val comments = listOf(source.first()) + (2..92).map { id ->
            PortableCommentItem(CommentSnapshot(id, parentId = 1, text = "Short reply"),
                CommentPresentationSnapshot(sortOrder = id))
        }
        val runtime = DiscussionSummaryRuntime(this, settings, cloud, null, { false }, dispatcher,
            cloudFirstOutputTimeoutMillis = 100)
        runtime.open("Post", null, comments, false, false)
        advanceUntilIdle()
        assertEquals(1, requests)
        assertFalse(runtime.state.value.running)
        assertTrue(runtime.state.value.error.orEmpty().contains("did not respond"))
        assertTrue(runtime.state.value.inputCharacters > 0)
    }

    @Test fun cloudUsesConfiguredProviderAndDoesNotRetryOnceOutputHasStarted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = AiSummarySettingsRepository(TestKeyValueStore(), TestCredentialStore(), emptyFlow(), dispatcher)
        settings.setMode(AiSummaryMode.CLOUD)
        settings.setBaseUrl("https://example.com/v1")
        settings.setModel("configured-model")
        assertTrue(settings.setText(AiSummaryTextSetting.API_KEY, "test-key"))
        settings.setText(AiSummaryTextSetting.SYSTEM_PROMPT, "Custom article prompt")
        val requests = mutableListOf<CloudSummaryConfig>()
        val cloud = SummaryUseCase(object : CloudSummaryRepository {
            override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> = error("No model lookup")
            override suspend fun extractMainContent(url: String): String = error("No article extraction")
            override fun summarize(config: CloudSummaryConfig, text: String?) = flow {
                requests += config
                assertTrue(text.orEmpty().contains("\"comments\""))
                emit(CloudSummaryEvent.Progress("Partial"))
                error("Context limit")
            }
        })
        val runtime = DiscussionSummaryRuntime(this, settings, cloud, null, { false }, dispatcher)
        val largeThread = (1..74).map { id ->
            PortableCommentItem(CommentSnapshot(id, parentId = 1, text = "Comment ".repeat(85)),
                CommentPresentationSnapshot(sortOrder = id))
        }
        runtime.open("Title", null, largeThread, false, false)
        advanceUntilIdle()
        val config = requests.single()
        assertEquals("configured-model", config.model)
        assertEquals("test-key", config.apiKey)
        assertTrue(config.systemPrompt.contains("Hacker News discussion"))
        assertTrue(config.inputCharacterLimit > 15_000)
        assertEquals(73, runtime.state.value.coverage?.included)
        assertEquals("This discussion is too large for the selected model.", runtime.state.value.error)
    }
}
