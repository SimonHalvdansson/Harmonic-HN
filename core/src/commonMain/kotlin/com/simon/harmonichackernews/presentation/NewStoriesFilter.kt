package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story

/** Session-only thresholds; other feeds and search keep their own filtering rules. */
data class NewStoriesFilter(
    val minimumPoints: Int = 0,
    val minimumComments: Int = 0,
) {
    val active: Boolean get() = minimumPoints > 0 || minimumComments > 0

    fun shouldHide(story: Story, type: StoryType, searching: Boolean = false): Boolean =
        !searching && type == StoryType.NEW_STORIES && story.loaded &&
            ((minimumPoints > 0 && story.score < minimumPoints) ||
                (minimumComments > 0 && story.descendants < minimumComments))

    companion object {
        val thresholds = listOf(0, 1, 2, 3, 5, 10, 20, 50, 100)
    }
}
