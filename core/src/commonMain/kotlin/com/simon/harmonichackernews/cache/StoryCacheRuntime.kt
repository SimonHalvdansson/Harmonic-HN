package com.simon.harmonichackernews.cache

import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StoryCacheStatus {
    IDLE,
    CACHING,
    FINISHED,
    EMPTY,
    FAILED,
}

data class StoryCacheState(
    val status: StoryCacheStatus = StoryCacheStatus.IDLE,
    val progressVisible: Boolean = false,
    val completed: Int = 0,
    val total: Int = 1,
) {
    val isCaching: Boolean get() = status == StoryCacheStatus.CACHING
    val progressMax: Int get() = max(total, 1)
}

/** Lifecycle-neutral progress and completion presentation for the story-cache workflow. */
class StoryCacheRuntime(
    private val scope: CoroutineScope,
    private val execute: suspend (
        StoryCacheRequest,
        (StoryCacheProgress) -> Unit,
    ) -> StoryCacheOutcome,
    private val completionVisibilityMillis: Long = DEFAULT_COMPLETION_VISIBILITY_MILLIS,
) {
    private val mutableState = MutableStateFlow(StoryCacheState())
    val state: StateFlow<StoryCacheState> = mutableState.asStateFlow()

    private var cacheJob: Job? = null
    private var visibilityJob: Job? = null

    fun start(request: StoryCacheRequest): Boolean {
        if (mutableState.value.isCaching) return false

        visibilityJob?.cancel()
        mutableState.value = StoryCacheState(
            status = StoryCacheStatus.CACHING,
            progressVisible = true,
            total = max(request.storyCount, 1),
        )
        val launchedCacheJob = scope.launch {
            val outcome = try {
                execute(request) { progress ->
                    val total = max(progress.total, 1)
                    mutableState.value = StoryCacheState(
                        status = StoryCacheStatus.CACHING,
                        progressVisible = true,
                        completed = progress.completed.coerceIn(0, total),
                        total = total,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                StoryCacheOutcome.FAILED
            }
            mutableState.value = mutableState.value.copy(
                status = outcome.toStatus(),
                progressVisible = true,
            )
            val launchedVisibilityJob = scope.launch {
                delay(completionVisibilityMillis)
                mutableState.value = mutableState.value.copy(progressVisible = false)
            }
            visibilityJob = launchedVisibilityJob
            launchedVisibilityJob.invokeOnCompletion {
                if (visibilityJob === launchedVisibilityJob) visibilityJob = null
            }
        }
        cacheJob = launchedCacheJob
        launchedCacheJob.invokeOnCompletion {
            if (cacheJob === launchedCacheJob) cacheJob = null
        }
        return true
    }

    fun dispose() {
        cacheJob?.cancel()
        visibilityJob?.cancel()
        cacheJob = null
        visibilityJob = null
        mutableState.value = StoryCacheState()
    }

    private fun StoryCacheOutcome.toStatus(): StoryCacheStatus = when (this) {
        StoryCacheOutcome.FINISHED -> StoryCacheStatus.FINISHED
        StoryCacheOutcome.EMPTY -> StoryCacheStatus.EMPTY
        StoryCacheOutcome.FAILED -> StoryCacheStatus.FAILED
    }

    private companion object {
        const val DEFAULT_COMPLETION_VISIBILITY_MILLIS = 1_000L
    }
}
