package com.simon.harmonichackernews.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryCacheRuntimeTest {
    @Test
    fun progressAndCompletionVisibilityAreOwnedByTheRuntime() = runTest {
        val finish = CompletableDeferred<Unit>()
        val runtime = StoryCacheRuntime(
            scope = this,
            execute = { _, onProgress ->
                onProgress(StoryCacheProgress(completed = 2, total = 4))
                finish.await()
                StoryCacheOutcome.FINISHED
            },
            completionVisibilityMillis = 1_000L,
        )

        assertTrue(runtime.start(StoryCacheRequest(4, cacheArticleSnapshots = false)))
        assertFalse(runtime.start(StoryCacheRequest(8, cacheArticleSnapshots = true)))
        runCurrent()

        assertEquals(
            StoryCacheState(
                status = StoryCacheStatus.CACHING,
                progressVisible = true,
                completed = 2,
                total = 4,
            ),
            runtime.state.value,
        )

        finish.complete(Unit)
        runCurrent()
        assertEquals(StoryCacheStatus.FINISHED, runtime.state.value.status)
        assertTrue(runtime.state.value.progressVisible)

        advanceTimeBy(999L)
        runCurrent()
        assertTrue(runtime.state.value.progressVisible)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(runtime.state.value.progressVisible)
    }

    @Test
    fun outcomesAreExposedAsStructuredStatuses() = runTest {
        val outcomes = listOf(
            StoryCacheOutcome.EMPTY to StoryCacheStatus.EMPTY,
            StoryCacheOutcome.FAILED to StoryCacheStatus.FAILED,
        )

        for ((outcome, expectedStatus) in outcomes) {
            val runtime = StoryCacheRuntime(
                scope = this,
                execute = { _, _ -> outcome },
            )

            runtime.start(StoryCacheRequest(0, cacheArticleSnapshots = false))
            runCurrent()

            assertEquals(expectedStatus, runtime.state.value.status)
            assertEquals(1, runtime.state.value.progressMax)
            runtime.dispose()
        }
    }

    @Test
    fun progressIsNormalizedBeforeItReachesTheUi() = runTest {
        val runtime = StoryCacheRuntime(
            scope = this,
            execute = { _, onProgress ->
                onProgress(StoryCacheProgress(completed = 5, total = 0))
                StoryCacheOutcome.FINISHED
            },
        )

        runtime.start(StoryCacheRequest(0, cacheArticleSnapshots = false))
        runCurrent()

        assertEquals(1, runtime.state.value.completed)
        assertEquals(1, runtime.state.value.total)
    }

    @Test
    fun disposeCancelsWorkAndResetsPresentationState() = runTest {
        val runtime = StoryCacheRuntime(
            scope = this,
            execute = { _, _ -> awaitCancellation() },
        )

        runtime.start(StoryCacheRequest(10, cacheArticleSnapshots = true))
        runCurrent()
        runtime.dispose()
        runCurrent()

        assertEquals(StoryCacheState(), runtime.state.value)
    }
}
