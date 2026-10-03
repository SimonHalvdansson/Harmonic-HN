package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewStoriesFilterTest {
    private fun story(points: Int, comments: Int, loaded: Boolean = true) = Story().apply {
        score = points
        descendants = comments
        this.loaded = loaded
    }

    @Test
    fun bothMinimumsAreInclusiveAndRequired() {
        val filter = NewStoriesFilter(5, 2)
        assertFalse(filter.shouldHide(story(5, 2), StoryType.NEW_STORIES))
        assertFalse(filter.shouldHide(story(6, 3), StoryType.NEW_STORIES))
        assertTrue(filter.shouldHide(story(4, 2), StoryType.NEW_STORIES))
        assertTrue(filter.shouldHide(story(5, 1), StoryType.NEW_STORIES))
    }

    @Test
    fun filtersNeverAffectOtherFeedsSearchOrUnloadedPlaceholders() {
        val filter = NewStoriesFilter(5, 2)
        for (type in StoryType.entries.filter { it != StoryType.NEW_STORIES }) {
            assertFalse(filter.shouldHide(story(0, 0), type))
        }
        assertFalse(filter.shouldHide(story(0, 0), StoryType.NEW_STORIES, searching = true))
        assertFalse(filter.shouldHide(story(0, 0, loaded = false), StoryType.NEW_STORIES))
    }

    @Test
    fun eitherThresholdCanBeDisabledAndClearingRestoresStories() {
        assertFalse(NewStoriesFilter(5, 0).shouldHide(story(5, 0), StoryType.NEW_STORIES))
        assertFalse(NewStoriesFilter(0, 2).shouldHide(story(0, 2), StoryType.NEW_STORIES))
        assertFalse(NewStoriesFilter().active)
        assertFalse(NewStoriesFilter().shouldHide(story(0, 0), StoryType.NEW_STORIES))
        assertTrue(NewStoriesFilter(minimumComments = 2).active)
    }
}
