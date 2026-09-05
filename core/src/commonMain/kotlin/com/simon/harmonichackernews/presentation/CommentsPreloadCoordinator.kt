package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.CommentsPreloadRepository
import com.simon.harmonichackernews.network.CommentThreadSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Debounces scrolling and limits provider-aware preparation to visible discussions. */
class CommentsPreloadCoordinator(
    private val scope: CoroutineScope,
    private val loadFilteredUsers: () -> Set<String>,
    private val preloadAllowed: () -> Boolean = { true },
    private val preloadSource: () -> CommentThreadSource = { CommentThreadSource.ALGOLIA },
    private val isPrepared: suspend (CommentThreadSource, Int, List<Int>, Set<String>) -> Boolean,
    private val preload: suspend (CommentThreadSource, Int, List<Int>, Set<String>) -> Unit,
    maxConcurrentPreloads: Int = DEFAULT_MAX_CONCURRENT_PRELOADS,
    private val scrollSettleDelayMillis: Long = DEFAULT_SCROLL_SETTLE_DELAY_MILLIS,
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    constructor(
        preloads: CommentsPreloadRepository,
        loadFilteredUsers: () -> Set<String>,
        preloadAllowed: () -> Boolean,
        preloadSource: () -> CommentThreadSource,
        scope: CoroutineScope,
    ) : this(
        scope = scope,
        loadFilteredUsers = loadFilteredUsers,
        preloadAllowed = preloadAllowed,
        preloadSource = preloadSource,
        isPrepared = { source, storyId, topLevelCommentIds, filteredUsers ->
            when (source) {
                CommentThreadSource.ALGOLIA ->
                    preloads.isPrepared(storyId, topLevelCommentIds, filteredUsers)
                CommentThreadSource.OFFICIAL ->
                    preloads.isOfficialPrepared(storyId, topLevelCommentIds, filteredUsers)
            }
        },
        preload = { source, storyId, topLevelCommentIds, filteredUsers ->
            when (source) {
                CommentThreadSource.ALGOLIA ->
                    preloads.preload(storyId, topLevelCommentIds, filteredUsers)
                CommentThreadSource.OFFICIAL ->
                    preloads.preloadOfficial(storyId, topLevelCommentIds, filteredUsers)
            }
        },
    )

    private val permits = Semaphore(maxConcurrentPreloads.coerceAtLeast(1))
    private val active = mutableMapOf<PreloadWorkKey, Job>()
    private val started = mutableSetOf<PreloadWorkKey>()
    private var submitJob: Job? = null
    private var enabled: Boolean = false
    private var visibleStories: List<StoryListItemSnapshot> = emptyList()

    fun setEnabled(value: Boolean) {
        if (enabled == value) {
            if (value) schedule()
            return
        }
        enabled = value
        if (value) {
            schedule()
        } else {
            cancelPendingWork()
        }
    }

    fun updateVisibleStories(stories: List<StoryListItemSnapshot>) {
        visibleStories = stories
        schedule()
    }

    fun dispose() {
        enabled = false
        cancelPendingWork()
        visibleStories = emptyList()
    }

    private fun cancelPendingWork() {
        submitJob?.cancel()
        submitJob = null
        active.values.forEach(Job::cancel)
        active.clear()
        started.clear()
    }

    private fun schedule() {
        submitJob?.cancel()
        if (!enabled) return
        // Keep transfers that a comments screen may already be awaiting, but remove obsolete
        // waiters immediately so the newest viewport gets the next available slot.
        active.keys.filter { it !in started && !isVisible(it) }.forEach { key ->
            active.remove(key)?.cancel()
        }
        submitJob = scope.launch {
            delay(scrollSettleDelayMillis)
            if (!enabled || !preloadAllowed()) {
                submitJob = null
                return@launch
            }
            val filteredUsers = withContext(preloadDispatcher) { loadFilteredUsers() }
            val source = preloadSource()
            visibleStories
                .asSequence()
                .filter { it.id > 0 && it.loaded && !it.isComment && it.descendantCount > 0 }
                .distinctBy(StoryListItemSnapshot::id)
                .forEach { story ->
                    val workKey = PreloadWorkKey(story.id, source)
                    if (workKey in active) return@forEach
                    lateinit var job: Job
                    job = scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            permits.withPermit {
                                if (!enabled || !preloadAllowed() || !isVisible(workKey)) return@withPermit
                                started.add(workKey)
                                withContext(preloadDispatcher) {
                                    if (!isPrepared(source, story.id, story.kids, filteredUsers)) {
                                        preload(source, story.id, story.kids, filteredUsers)
                                    }
                                }
                            }
                        } finally {
                            if (active[workKey] === job) {
                                active.remove(workKey)
                                started.remove(workKey)
                            }
                        }
                    }
                    active[workKey] = job
                    job.start()
                }
            submitJob = null
        }
    }

    private fun isVisible(key: PreloadWorkKey): Boolean = key.source == preloadSource() &&
        visibleStories.any {
            it.id == key.storyId && it.loaded && !it.isComment && it.descendantCount > 0
        }

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_PRELOADS = 2
        const val DEFAULT_SCROLL_SETTLE_DELAY_MILLIS = 300L
    }

    private data class PreloadWorkKey(
        val storyId: Int,
        val source: CommentThreadSource,
    )
}
