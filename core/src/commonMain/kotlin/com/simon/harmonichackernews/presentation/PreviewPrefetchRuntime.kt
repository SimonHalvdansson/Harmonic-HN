package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coroutine scheduler around [PreviewPrefetchPlanner]; platforms only execute each image request. */
class PreviewPrefetchRuntime(
    private val scope: CoroutineScope,
    batchSize: Int,
    visibleThreshold: Int,
    private val batchDelayMillis: Long,
    private val onPrefetch: (Story) -> Unit,
) {
    private val planner = PreviewPrefetchPlanner(batchSize, visibleThreshold)
    private var stories: List<Story> = emptyList()
    private var nextBatchJob: Job? = null

    fun enqueue(story: Story, stories: List<Story>) {
        this.stories = stories
        dispatch(planner.enqueue(story, stories))
        scheduleNextBatch()
    }

    fun prefetchNearViewport(
        stories: List<Story>,
        initialLoadCount: Int,
        firstVisibleItem: Int = -1,
        lastVisibleItem: Int = -1,
        paginationVisibleCount: Int? = null,
    ) {
        if (stories.isEmpty()) return
        this.stories = stories
        val range = planner.prefetchRange(
            storyCount = stories.size,
            initialLoadCount = initialLoadCount,
            firstVisibleItem = firstVisibleItem,
            lastVisibleItem = lastVisibleItem,
            paginationVisibleCount = paginationVisibleCount,
        ) ?: return
        planner.begin(range.last, enabled = true)
        range.forEach { enqueue(stories[it], stories) }
    }

    fun reset() {
        nextBatchJob?.cancel()
        nextBatchJob = null
        planner.reset()
        stories = emptyList()
    }

    private fun dispatch(selected: List<Story>) = selected.forEach(onPrefetch)

    private fun scheduleNextBatch() {
        if (!planner.requestNextBatchSchedule()) return
        nextBatchJob = scope.launch {
            delay(batchDelayMillis)
            planner.startNextBatch()
            dispatch(planner.drain(stories))
            nextBatchJob = null
            scheduleNextBatch()
        }
    }
}
