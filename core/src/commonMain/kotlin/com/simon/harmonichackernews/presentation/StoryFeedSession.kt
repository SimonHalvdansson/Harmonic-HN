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
        val current = config
        if (current.filteredUsers.isEmpty() && current.filteredWords.isEmpty() &&
            current.filteredDomains.isEmpty() && (!current.hideJobs || storyType == StoryType.HN_JOBS)
        ) {
            return false
        }

        var normalizedAuthor: String? = null
        if (current.filteredUsers.isNotEmpty()) {
            val author = story.by.orEmpty().normalizeFilterValue()
            normalizedAuthor = author
            if (author.isNotEmpty() && author in current.filteredUsers) return true
        }

        if (current.filteredWords.isNotEmpty()) {
            val title = story.title.orEmpty().lowercase()
            if (current.filteredWords.any(title::contains)) return true
        }

        if (current.filteredDomains.isNotEmpty()) {
            val domain = runCatching { story.getDisplayDomain(true).orEmpty().lowercase() }
                .getOrDefault("")
            if (domain.isNotEmpty() && current.filteredDomains.any(domain::contains)) return true
        }

        if (!current.hideJobs || storyType == StoryType.HN_JOBS) return false
        if (story.isJob) return true
        val author = normalizedAuthor ?: story.by.orEmpty().normalizeFilterValue()
        return author == WHO_IS_HIRING
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
        // A single queued row is cheaper to find directly. Larger queues use one display-index
        // map for the entire drain instead of rescanning the story list for every queue candidate.
        val displayIndexes = if (queue.size > 1) firstDisplayIndexes(stories) else null
        while (slotsRemaining > 0) {
            val story = removeNext(stories, displayIndexes) ?: break
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

    private fun removeNext(
        stories: List<Story>,
        displayIndexes: Map<Story, Int>?,
    ): Story? {
        while (queue.isNotEmpty()) {
            var bestQueueIndex = 0
            var bestDisplayIndex = displayIndex(queue[0], stories, displayIndexes)
            var bestRank = bestDisplayIndex.takeIf { it >= 0 } ?: Int.MAX_VALUE
            for (queueIndex in 1..<queue.size) {
                val candidateDisplayIndex = displayIndex(queue[queueIndex], stories, displayIndexes)
                val candidateRank = candidateDisplayIndex.takeIf { it >= 0 } ?: Int.MAX_VALUE
                if (candidateRank < bestRank) {
                    bestQueueIndex = queueIndex
                    bestDisplayIndex = candidateDisplayIndex
                    bestRank = candidateRank
                }
            }

            val story = queue.removeAt(bestQueueIndex)
            if (story.id > 0) queuedIds.remove(story.id)
            if (bestDisplayIndex >= 0 && story.loaded && !story.loadingFailed) return story
        }
        return null
    }

    private fun firstDisplayIndexes(stories: List<Story>): Map<Story, Int> =
        HashMap<Story, Int>(stories.size).apply {
            stories.forEachIndexed { index, story ->
                if (story !in this) this[story] = index
            }
        }

    private fun displayIndex(
        story: Story,
        stories: List<Story>,
        displayIndexes: Map<Story, Int>?,
    ): Int = if (displayIndexes == null) stories.indexOf(story) else displayIndexes[story] ?: -1

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
