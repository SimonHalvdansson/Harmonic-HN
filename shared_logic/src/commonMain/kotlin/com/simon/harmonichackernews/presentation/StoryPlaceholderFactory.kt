package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story

/** Builds canonical loading rows while allowing a platform cache to hydrate optional metadata. */
object StoryPlaceholderFactory {
    fun create(
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        clickedIds: Set<Int> = emptySet(),
        hideClicked: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
    ): MutableList<Story> = itemIds.mapNotNullTo(mutableListOf()) { id ->
        if (hideClicked && id in clickedIds) return@mapNotNullTo null
        Story("Loading...", id, false, id in clickedIds).also { story ->
            story.isComment = id in commentIds
            if (hydrateCachedStory(story) && shouldHideHydratedStory(story)) {
                return@mapNotNullTo null
            }
        }
    }

    fun createNew(
        existingStories: List<Story>,
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        clickedIds: Set<Int> = emptySet(),
        hideClicked: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
    ): MutableList<Story> {
        val existingIds = existingStories.mapTo(mutableSetOf(), Story::id)
        return create(
            itemIds = itemIds.filterNot(existingIds::contains),
            commentIds = commentIds,
            clickedIds = clickedIds,
            hideClicked = hideClicked,
            hydrateCachedStory = hydrateCachedStory,
            shouldHideHydratedStory = shouldHideHydratedStory,
        )
    }
}
