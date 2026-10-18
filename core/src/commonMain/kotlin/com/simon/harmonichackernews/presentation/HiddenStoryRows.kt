package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story

object HiddenStoryPolicy {
    private val supportedTypes = setOf(
        StoryType.TOP_STORIES, StoryType.NEW_STORIES, StoryType.BEST_STORIES,
        StoryType.LAST_24_HOURS, StoryType.LAST_48_HOURS, StoryType.LAST_WEEK,
        StoryType.ASK_HN, StoryType.SHOW_HN, StoryType.HN_JOBS,
        StoryType.CLASSIC, StoryType.ACTIVE, StoryType.FRONT, StoryType.UNSLOP,
    )

    fun supports(type: StoryType): Boolean = type in supportedTypes
}

/** Retains the current feed's hidden rows so Undo, clearing, and disabling work offline. */
class HiddenStoryRows {
    private var order = emptyList<Int>()
    private var retained = emptyMap<Int, Story>()
    private var lastVisible = emptyList<Story>()
    private var lastHiddenIds: Set<Int>? = null

    fun reset() {
        order = emptyList()
        retained = emptyMap()
        lastVisible = emptyList()
        lastHiddenIds = null
    }

    fun apply(
        stories: List<Story>,
        hiddenIds: Set<Int>,
        canRestore: (Story) -> Boolean = { true },
    ): List<Story> {
        if (retained.isEmpty() && hiddenIds.isEmpty()) {
            reset()
            return stories
        }
        // Preview arrivals and viewport changes do not change the feed. Retain the input
        // without rebuilding its sets/maps/lists. Story is mutable, so also check eligibility
        // and restoration policy rather than relying on list identity or IDs alone.
        if (lastHiddenIds == hiddenIds && stories.size == lastVisible.size &&
            stories.indices.all { index ->
                val story = stories[index]
                story === lastVisible[index] &&
                    (story.id !in hiddenIds || story.isComment || story.isFrontpageLink)
            } && retained.values.all { !it.isComment && !it.isFrontpageLink && canRestore(it) }
        ) {
            return stories
        }
        val present = stories.mapTo(mutableSetOf(), Story::id)
        val before = mutableMapOf<Int, List<Story>>()
        var pending = mutableListOf<Story>()
        // Group missing rows by their nearest surviving successor without quadratic insertions.
        for (id in order) {
            if (id in present) {
                if (pending.isNotEmpty()) {
                    before[id] = pending
                    pending = mutableListOf()
                }
            } else {
                retained[id]?.takeIf(canRestore)?.let(pending::add)
            }
        }
        val restored = buildList {
            stories.forEach { story ->
                before[story.id]?.let(::addAll)
                add(story)
            }
            addAll(pending)
        }
        order = restored.map(Story::id)
        retained = restored.filter { it.id in hiddenIds && !it.isComment && !it.isFrontpageLink }
            .associateBy(Story::id)
        return restored.filterNot { it.id in retained }.also {
            lastVisible = it
            lastHiddenIds = hiddenIds.toSet()
        }
    }
}
