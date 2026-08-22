package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Debounces scrolling and limits Algolia preparation to two simultaneous visible discussions. */
internal class DesktopCommentsPreloadCoordinator(
    private val app: HarmonicAppComposition,
    private val scope: CoroutineScope,
) {
    private val permits = Semaphore(MAX_CONCURRENT_PRELOADS)
    private val active = mutableMapOf<Int, Job>()
    private var submitJob: Job? = null
    private var enabled: Boolean = false
    private var visibleStories: List<StoryListItemSnapshot> = emptyList()

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            schedule()
        } else {
            submitJob?.cancel()
            submitJob = null
            active.values.forEach(Job::cancel)
            active.clear()
        }
    }

    fun updateVisibleStories(stories: List<StoryListItemSnapshot>) {
        visibleStories = stories
        schedule()
    }

    fun dispose() {
        submitJob?.cancel()
        active.values.forEach(Job::cancel)
        active.clear()
    }

    private fun schedule() {
        submitJob?.cancel()
        if (!enabled) return
        submitJob = scope.launch {
            delay(SCROLL_SETTLE_DELAY_MILLIS)
            val filteredUsers = app.contentFilters.load().users
            visibleStories
                .asSequence()
                .filter { it.id > 0 && it.loaded && !it.isComment && it.descendantCount > 0 }
                .distinctBy(StoryListItemSnapshot::id)
                .forEach { story ->
                    if (story.id in active || app.commentsPreloads.isPrepared(
                            story.id,
                            story.kids,
                            filteredUsers,
                        )
                    ) return@forEach
                    lateinit var job: Job
                    job = scope.launch {
                        try {
                            permits.withPermit {
                                app.commentsPreloads.preload(
                                    story.id,
                                    story.kids,
                                    filteredUsers,
                                )
                            }
                        } finally {
                            if (active[story.id] === job) active.remove(story.id)
                        }
                    }
                    active[story.id] = job
                }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_PRELOADS = 2
        const val SCROLL_SETTLE_DELAY_MILLIS = 300L
    }
}
