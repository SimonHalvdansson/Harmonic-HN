package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story

/** Builds canonical loading rows while allowing a platform cache to hydrate optional metadata. */
object StoryPlaceholderFactory {
    fun create(
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        readIds: Set<Int> = emptySet(),
        hideRead: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
        cachedStories: Map<Int, Story> = emptyMap(),
    ): MutableList<Story> = itemIds.mapNotNullTo(mutableListOf()) { id ->
        val isRead = id in readIds
        if (hideRead && isRead) return@mapNotNullTo null
        val cachedStory = cachedStories[id]
        (cachedStory ?: Story("Loading...", id, false, isRead)).also { story ->
            story.isRead = isRead
            story.isComment = id in commentIds
            if ((cachedStory != null || hydrateCachedStory(story)) && shouldHideHydratedStory(story)) {
                return@mapNotNullTo null
            }
        }
    }

    fun createNew(
        existingStories: List<Story>,
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        readIds: Set<Int> = emptySet(),
        hideRead: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
        cachedStories: Map<Int, Story> = emptyMap(),
    ): MutableList<Story> {
        val existingIds = existingStories.mapTo(mutableSetOf(), Story::id)
        return create(
            itemIds = itemIds.filterNot(existingIds::contains),
            commentIds = commentIds,
            readIds = readIds,
            hideRead = hideRead,
            hydrateCachedStory = hydrateCachedStory,
            shouldHideHydratedStory = shouldHideHydratedStory,
            cachedStories = cachedStories,
        )
    }

    /**
     * Reorders a refreshed feed while retaining the complete live objects for IDs already shown.
     * New IDs still follow the normal cache hydration and visibility policies.
     */
    fun reconcile(
        existingStories: List<Story>,
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        readIds: Set<Int> = emptySet(),
        hideRead: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
        cachedStories: Map<Int, Story> = emptyMap(),
    ): MutableList<Story> {
        val existingById = existingStories.associateBy(Story::id)
        return itemIds.mapNotNullTo(mutableListOf()) { id ->
            if (hideRead && id in readIds) return@mapNotNullTo null
            existingById[id]?.also { story ->
                story.isComment = id in commentIds
            } ?: (cachedStories[id] ?: Story("Loading...", id, false, id in readIds)).also { story ->
                story.isRead = id in readIds
                story.isComment = id in commentIds
                if ((id in cachedStories || hydrateCachedStory(story)) && shouldHideHydratedStory(story)) {
                    return@mapNotNullTo null
                }
            }
        }
    }
}
