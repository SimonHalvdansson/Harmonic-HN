package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.dto.applyTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface StoryRowLoadEffect {
    val story: Story
    val generation: Int

    data class Loaded(
        override val story: Story,
        override val generation: Int,
    ) : StoryRowLoadEffect

    data class Rejected(
        override val story: Story,
        override val generation: Int,
    ) : StoryRowLoadEffect

    data class AttemptFailed(
        override val story: Story,
        override val generation: Int,
        val attempt: Int,
        val finalAttempt: Boolean,
        val cause: Throwable,
    ) : StoryRowLoadEffect
}

/** Portable owner for story-row request deduplication, retries and stale-generation rejection. */
class StoryRowLoadOrchestrator(
    private val scope: CoroutineScope,
    private val hackerNewsApi: HackerNewsApi,
    staleLoadMillis: Long,
    private val nowMillis: () -> Long,
) {
    private val session = StoryFeedLoadSession(staleLoadMillis)
    private val jobsByStoryId = mutableMapOf<Int, Job>()
    private val mutableEffects = MutableSharedFlow<StoryRowLoadEffect>(extraBufferCapacity = 64)
    val effects: SharedFlow<StoryRowLoadEffect> = mutableEffects.asSharedFlow()

    val generation: Int get() = session.generation

    fun beginGeneration(): Int {
        jobsByStoryId.values.forEach(Job::cancel)
        jobsByStoryId.clear()
        return session.beginGeneration()
    }

    fun isCurrent(requestGeneration: Int): Boolean = session.isCurrent(requestGeneration)

    fun isInProgress(storyId: Int): Boolean =
        jobsByStoryId[storyId]?.isActive == true ||
            session.isStoryInProgress(storyId, nowMillis())

    fun cancel(storyId: Int) {
        jobsByStoryId.remove(storyId)?.cancel()
        session.clearStory(storyId)
    }

    fun clear() {
        jobsByStoryId.values.forEach(Job::cancel)
        jobsByStoryId.clear()
        session.clearStoryLoads()
    }

    fun load(
        story: Story,
        preserveTime: Boolean,
        requestGeneration: Int = generation,
    ) {
        if (!session.isCurrent(requestGeneration) || story.loaded || isInProgress(story.id)) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (attempt in 0 until MAX_ATTEMPTS) {
                    val startedAt = session.markStoryStarted(story.id, nowMillis())
                    try {
                        val item = hackerNewsApi.getItem(story.id)
                        if (!session.isCurrentStoryLoad(story.id, startedAt)) return@launch
                        session.clearStory(story.id, startedAt)
                        if (!session.isCurrent(requestGeneration)) return@launch
                        if (item == null || !item.applyTo(story, preserveTime)) {
                            mutableEffects.emit(
                                StoryRowLoadEffect.Rejected(story, requestGeneration),
                            )
                            return@launch
                        }
                        mutableEffects.emit(StoryRowLoadEffect.Loaded(story, requestGeneration))
                        return@launch
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        if (!session.isCurrentStoryLoad(story.id, startedAt)) return@launch
                        session.clearStory(story.id, startedAt)
                        if (!session.isCurrent(requestGeneration) || story.loaded) return@launch
                        story.loadingFailed = true
                        val finalAttempt = attempt == MAX_ATTEMPTS - 1
                        mutableEffects.emit(
                            StoryRowLoadEffect.AttemptFailed(
                                story = story,
                                generation = requestGeneration,
                                attempt = attempt,
                                finalAttempt = finalAttempt,
                                cause = error,
                            ),
                        )
                        if (finalAttempt) return@launch
                    }
                }
            } finally {
                if (jobsByStoryId[story.id] === currentCoroutineContext()[Job]) {
                    jobsByStoryId.remove(story.id)
                }
            }
        }
        jobsByStoryId[story.id] = job
        job.start()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
