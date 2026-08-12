package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.settings.ContentFilters

data class StoryVisibilityConfig(
    val filteredWords: List<String> = emptyList(),
    val filteredDomains: List<String> = emptyList(),
    val filteredUsers: Set<String> = emptySet(),
    val hideJobs: Boolean = false,
)

/** Portable filtering policy for every story source, including search results. */
class StoryVisibilityPolicy(
    config: StoryVisibilityConfig = StoryVisibilityConfig(),
) {
    var config: StoryVisibilityConfig = config
        set(value) {
            field = value.normalized()
        }

    init {
        this.config = config
    }

    fun update(filters: ContentFilters, hideJobs: Boolean): Boolean {
        val previous = config
        config = StoryVisibilityConfig(
            filteredWords = filters.words,
            filteredDomains = filters.domains,
            filteredUsers = filters.users,
            hideJobs = hideJobs,
        )
        return config != previous
    }

    fun shouldHide(story: Story, storyType: StoryType): Boolean {
        val author = story.by.orEmpty().normalizeFilterValue()
        if (author.isNotEmpty() && author in config.filteredUsers) return true

        val title = story.title.orEmpty().lowercase()
        if (config.filteredWords.any(title::contains)) return true

        val domain = runCatching { story.getDisplayDomain(true).orEmpty().lowercase() }
            .getOrDefault("")
        if (domain.isNotEmpty() && config.filteredDomains.any(domain::contains)) return true

        return config.hideJobs && storyType != StoryType.HN_JOBS &&
            (story.isJob || author == WHO_IS_HIRING)
    }

    private fun StoryVisibilityConfig.normalized() = copy(
        filteredWords = filteredWords.mapNotNull { it.normalizedFilterOrNull() },
        filteredDomains = filteredDomains.mapNotNull { it.normalizedFilterOrNull() },
        filteredUsers = filteredUsers.mapNotNullTo(mutableSetOf()) {
            it.normalizedFilterOrNull()
        },
    )

    private companion object {
        const val WHO_IS_HIRING = "whoishiring"
        fun String.normalizedFilterOrNull(): String? =
            normalizeFilterValue().takeIf(String::isNotEmpty)

        fun String.normalizeFilterValue(): String = lowercase().trim()
    }
}

/**
 * Request-generation and in-flight item tracking for a story feed.
 * Platform shells use this only for individual row loads; feed request jobs are owned by the
 * shared stories presenter.
 */
class StoryFeedLoadSession(
    private val staleLoadMillis: Long,
) {
    var generation: Int = 0
        private set

    private val startedAtByStoryId = mutableMapOf<Int, Long>()

    fun beginGeneration(): Int {
        generation++
        startedAtByStoryId.clear()
        return generation
    }

    fun isCurrent(requestGeneration: Int): Boolean = requestGeneration == generation

    fun markStoryStarted(storyId: Int, nowMillis: Long): Long {
        startedAtByStoryId[storyId] = nowMillis
        return nowMillis
    }

    fun isStoryInProgress(storyId: Int, nowMillis: Long): Boolean {
        val startedAt = startedAtByStoryId[storyId] ?: return false
        if (nowMillis - startedAt <= staleLoadMillis) return true
        startedAtByStoryId.remove(storyId)
        return false
    }

    fun isCurrentStoryLoad(storyId: Int, startedAt: Long): Boolean =
        startedAtByStoryId[storyId] == startedAt

    fun clearStory(storyId: Int, startedAt: Long? = null) {
        if (startedAt == null || startedAtByStoryId[storyId] == startedAt) {
            startedAtByStoryId.remove(storyId)
        }
    }

    fun clearStoryLoads() {
        startedAtByStoryId.clear()
    }
}

/** Pure queue and batch planner for story preview-image prefetching. */
class PreviewPrefetchPlanner(
    private val batchSize: Int,
    private val visibleThreshold: Int,
) {
    private val queue = mutableListOf<Story>()
    private val queuedIds = mutableSetOf<Int>()
    private val requestedIds = mutableSetOf<Int>()
    private var slotsRemaining = batchSize
    private var targetIndex = -1

    var complete: Boolean = false
        private set
    var scheduled: Boolean = false
        private set

    fun begin(targetIndex: Int, enabled: Boolean) {
        if (enabled && targetIndex >= 0 && !complete) {
            this.targetIndex = maxOf(this.targetIndex, targetIndex)
        }
    }

    fun enqueue(story: Story, stories: List<Story>): List<Story> {
        if (!story.loaded || story.loadingFailed) return emptyList()
        if (complete || targetIndex < 0) return listOf(story)
        if (story.id > 0 && (story.id in requestedIds || !queuedIds.add(story.id))) {
            return emptyList()
        }
        queue += story
        return drain(stories)
    }

    fun drain(stories: List<Story>): List<Story> {
        if (scheduled) return emptyList()
        val selected = mutableListOf<Story>()
        while (slotsRemaining > 0) {
            val story = removeNext(stories) ?: break
            if (story.id > 0) requestedIds += story.id
            slotsRemaining--
            selected += story
        }
        updateCompletion(stories)
        return selected
    }

    fun requestNextBatchSchedule(): Boolean {
        if (complete || slotsRemaining > 0 || scheduled) return false
        scheduled = true
        return true
    }

    fun startNextBatch() {
        scheduled = false
        slotsRemaining = batchSize
    }

    fun reset() {
        queue.clear()
        queuedIds.clear()
        requestedIds.clear()
        slotsRemaining = batchSize
        targetIndex = -1
        complete = false
        scheduled = false
    }

    fun prefetchRange(
        storyCount: Int,
        initialLoadCount: Int,
        firstVisibleItem: Int,
        lastVisibleItem: Int,
        paginationVisibleCount: Int?,
    ): IntRange? {
        if (storyCount <= 0) return null
        val first = if (firstVisibleItem < 0) 0 else firstVisibleItem.coerceAtLeast(0)
        var last = if (lastVisibleItem < 0) {
            (initialLoadCount - 1).coerceAtMost(storyCount - 1)
        } else {
            (lastVisibleItem + visibleThreshold).coerceAtMost(storyCount - 1)
        }
        paginationVisibleCount?.let { last = last.coerceAtMost(it - 1) }
        return if (last >= first) first..last else null
    }

    private fun removeNext(stories: List<Story>): Story? {
        while (true) {
            val bestIndex = queue.indices.minByOrNull { index ->
                stories.indexOf(queue[index]).takeIf { it >= 0 } ?: Int.MAX_VALUE
            } ?: return null
            val story = queue[bestIndex]
            val storyIndex = stories.indexOf(story)
            if (storyIndex >= 0 && story.loaded && !story.loadingFailed) {
                queue.removeAt(bestIndex)
                if (story.id > 0) queuedIds.remove(story.id)
                return story
            }
            queue.removeAt(bestIndex)
            if (story.id > 0) queuedIds.remove(story.id)
        }
    }

    private fun updateCompletion(stories: List<Story>) {
        if (complete || targetIndex < 0 || queue.isNotEmpty()) return
        val cappedTarget = targetIndex.coerceAtMost(stories.lastIndex)
        if (cappedTarget >= 0 && (0..cappedTarget).any { index ->
                !stories[index].loaded && !stories[index].loadingFailed
            }
        ) {
            return
        }
        complete = true
        targetIndex = -1
        scheduled = false
        queuedIds.clear()
        requestedIds.clear()
    }
}
