package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewPrefetchRuntimeTest {
    @Test
    fun visibleStoriesAreDispatchedInDelayedBatches() = runTest {
        val requested = mutableListOf<Int>()
        val stories = (1..5).map { story(it, loaded = it <= 2) }
        val runtime = PreviewPrefetchRuntime(
            scope = this,
            batchSize = 2,
            visibleThreshold = 0,
            batchDelayMillis = 450L,
            onPrefetch = { requested += it.id },
        )

        runtime.prefetchNearViewport(
            stories = stories,
            initialLoadCount = stories.size,
        )
        assertEquals(listOf(1, 2), requested)

        stories.drop(2).forEach { item ->
            item.loaded = true
            runtime.enqueue(item, stories)
        }

        advanceTimeBy(450L)
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4), requested)

        advanceTimeBy(450L)
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5), requested)
    }

    @Test
    fun resetCancelsThePendingBatch() = runTest {
        val requested = mutableListOf<Int>()
        val stories = (1..3).map { story(it, loaded = it == 1) }
        val runtime = PreviewPrefetchRuntime(
            scope = this,
            batchSize = 1,
            visibleThreshold = 0,
            batchDelayMillis = 100L,
            onPrefetch = { requested += it.id },
        )

        runtime.prefetchNearViewport(stories, initialLoadCount = stories.size)
        runtime.reset()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(listOf(1), requested)
    }

    private fun story(id: Int, loaded: Boolean) = Story("Story $id", id, loaded, false)
}
