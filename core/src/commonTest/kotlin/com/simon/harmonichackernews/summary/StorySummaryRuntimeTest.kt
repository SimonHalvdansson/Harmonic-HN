package com.simon.harmonichackernews.summary

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.SummaryRequest
import com.simon.harmonichackernews.platform.SummaryResult

@OptIn(ExperimentalCoroutinesApi::class)
class StorySummaryRuntimeTest {
    @Test
    fun progressAndSuccessBecomeObservableState() = runTest {
        val cloud = StorySummaryBackend {
            flow {
                emit(StorySummaryEvent.DebugInfo("debug"))
                emit(StorySummaryEvent.Progress("partial"))
                emit(StorySummaryEvent.Success("complete"))
            }
        }
        val runtime = StorySummaryRuntime(this, cloud, failingBackend())

        runtime.start(StorySummaryMode.CLOUD, StorySummaryInput("https://example.com"))
        advanceUntilIdle()

        assertEquals("complete", runtime.state.value.text)
        assertEquals("debug", runtime.state.value.debugInfo)
        assertIs<StorySummaryStatus.Success>(runtime.state.value.status)
    }

    @Test
    fun progressPublishesRunningTimingSnapshot() = runTest {
        val finish = CompletableDeferred<Unit>()
        val backend = StorySummaryBackend {
            flow {
                emit(StorySummaryEvent.Progress("partial"))
                finish.await()
                emit(StorySummaryEvent.Success("complete"))
            }
        }
        val runtime = StorySummaryRuntime(this, backend, failingBackend())

        runtime.start(StorySummaryMode.CLOUD, StorySummaryInput("https://example.com"))
        runCurrent()

        val running = assertNotNull(runtime.state.value.diagnostics)
        assertIs<StorySummaryStatus.Running>(runtime.state.value.status)
        assertNotNull(running.totalTimeMillis)
        assertNotNull(running.timeToFirstOutputMillis)
        assertEquals(0L, running.generationTimeMillis)

        finish.complete(Unit)
        advanceUntilIdle()
        assertIs<StorySummaryStatus.Success>(runtime.state.value.status)
    }

    @Test
    fun failuresUseModeSpecificPresentation() = runTest {
        val runtime = StorySummaryRuntime(this, failingBackend(), failingBackend())

        runtime.start(StorySummaryMode.LOCAL, StorySummaryInput("https://example.com"))
        advanceUntilIdle()

        val failure = assertIs<StorySummaryStatus.Failure>(runtime.state.value.status)
        assertEquals("Failed to generate local summary: unavailable", failure.message)
        assertEquals(failure.message, runtime.state.value.text)
    }

    @Test
    fun geminiNanoPolicyFailuresUseConcisePresentation() = runTest {
        val backend = StorySummaryBackend {
            flow {
                emit(
                    StorySummaryEvent.Failure(
                        "Local summarization failed: [ErrorCode 11] Generated response " +
                            "doesn't pass certain policy check. Please try a different input.",
                    ),
                )
            }
        }
        val runtime = StorySummaryRuntime(this, failingBackend(), backend)

        runtime.start(StorySummaryMode.LOCAL, StorySummaryInput("https://example.com"))
        advanceUntilIdle()

        val failure = assertIs<StorySummaryStatus.Failure>(runtime.state.value.status)
        assertEquals(GEMINI_NANO_POLICY_BLOCKED_MESSAGE, failure.message)
        assertEquals(GEMINI_NANO_POLICY_BLOCKED_MESSAGE, runtime.state.value.text)
    }

    @Test
    fun aNewRequestSupersedesThePreviousGeneration() = runTest {
        var invocation = 0
        val backend = StorySummaryBackend {
            flow {
                invocation++
                emit(StorySummaryEvent.Success("result-$invocation"))
            }
        }
        val runtime = StorySummaryRuntime(this, backend, backend)

        runtime.start(StorySummaryMode.CLOUD, StorySummaryInput("https://first.example"))
        runtime.start(StorySummaryMode.CLOUD, StorySummaryInput("https://second.example"))
        advanceUntilIdle()

        assertEquals("result-1", runtime.state.value.text)
        assertEquals(2, runtime.state.value.generation)
        assertTrue(runtime.state.value.complete)
    }

    @Test
    fun localBehaviorReachesThePlatformAndCanSuppressProgress() = runTest {
        var received: SummaryRequest? = null
        val engine = object : LocalSummaryEngine {
            override suspend fun isAvailable(): Boolean = true
            override suspend fun summarize(request: SummaryRequest): SummaryResult = SummaryResult("done")
            override fun summarizeEvents(request: SummaryRequest) = flow {
                received = request
                emit(StorySummaryEvent.Progress("partial"))
                emit(StorySummaryEvent.Success("done"))
            }
        }
        val backend = PlatformLocalStorySummaryBackend(engine) {
            LocalSummaryBehavior(
                systemPrompt = "custom",
                streamResponses = false,
                useGeminiNanoSummarizationLora = false,
            )
        }
        val events = mutableListOf<StorySummaryEvent>()

        backend.summarize(StorySummaryInput("", "article")).collect(events::add)

        val request = checkNotNull(received)
        assertEquals("custom", request.prompt)
        assertFalse(request.streamResponses)
        assertFalse(request.useGeminiNanoSummarizationLora)
        assertEquals(listOf<StorySummaryEvent>(StorySummaryEvent.Success("done")), events)
    }

    private fun failingBackend() = StorySummaryBackend {
        flow { emit(StorySummaryEvent.Failure("unavailable")) }
    }
}
