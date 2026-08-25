package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryPlaceholderFactoryTest {
    @Test
    fun placeholdersApplyHistoryCommentAndExistingItemPolicies() {
        val stories = StoryPlaceholderFactory.createNew(
            existingStories = listOf(Story("Existing", 1, true, false)),
            itemIds = listOf(1, 2, 3, 4),
            commentIds = setOf(3),
            clickedIds = setOf(2),
            hideClicked = true,
        )

        assertEquals(listOf(3, 4), stories.map(Story::id))
        assertTrue(stories.first().isComment)
        assertFalse(stories.last().isComment)
    }

    @Test
    fun hydratedRowsCanBeFilteredWithoutPlatformListLogic() {
        val stories = StoryPlaceholderFactory.create(
            itemIds = listOf(1, 2),
            hydrateCachedStory = { story ->
                story.loaded = story.id == 2
                story.loaded
            },
            shouldHideHydratedStory = { it.id == 2 },
        )

        assertEquals(listOf(1), stories.map(Story::id))
    }
}
