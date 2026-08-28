package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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

    @Test
    fun refreshedFeedRetainsEveryDetailOfMatchingLiveStories() {
        val retained = Story("Already loaded", 2, true, false)

        val stories = StoryPlaceholderFactory.reconcile(
            existingStories = listOf(Story("Old first", 1, true, false), retained),
            itemIds = listOf(3, 2, 4),
            commentIds = setOf(2),
        )

        assertEquals(listOf(3, 2, 4), stories.map(Story::id))
        assertSame(retained, stories[1])
        assertEquals("Already loaded", stories[1].title)
        assertTrue(stories[1].loaded)
        assertTrue(stories[1].isComment)
        assertFalse(stories[0].loaded)
    }
}
