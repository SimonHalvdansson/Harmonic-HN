package com.simon.harmonichackernews.summary

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun failuresUseModeSpecificPresentation() = runTest {
        val runtime = StorySummaryRuntime(this, failingBackend(), failingBackend())

        runtime.start(StorySummaryMode.LOCAL, StorySummaryInput("https://example.com"))
        advanceUntilIdle()

        val failure = assertIs<StorySummaryStatus.Failure>(runtime.state.value.status)
        assertEquals("Failed to generate local summary: unavailable", failure.message)
        assertEquals(failure.message, runtime.state.value.text)
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

    private fun failingBackend() = StorySummaryBackend {
        flow { emit(StorySummaryEvent.Failure("unavailable")) }
    }
}
