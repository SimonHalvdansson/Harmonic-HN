package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals

class StorySearchRankingTest {
    @Test
    fun relevanceSortPreservesRankingAndOriginalOrderForTies() {
        val stories = mutableListOf(
            story(id = 4, title = "Using Kotlin"),
            story(id = 1, title = "Kotlin"),
            story(id = 2, title = "Kotlin memory"),
            story(id = 3, title = "Kotlin memory"),
        )

        StorySearchRanking.sortStoriesByRelevance(stories, " kotlin ")

        assertEquals(listOf(1, 2, 3, 4), stories.map(Story::id))
    }

    @Test
    fun rankingBreaksTiesByScoreRepliesTimeAndInputOrderWithoutOverflow() {
        val stories = mutableListOf(
            story(1, "Kotlin").apply { score = Int.MIN_VALUE },
            story(2, "Kotlin").apply { score = Int.MAX_VALUE; descendants = Int.MIN_VALUE },
            story(3, "Kotlin").apply {
                score = Int.MAX_VALUE; descendants = Int.MAX_VALUE; createdAtEpochSeconds = Int.MIN_VALUE
            },
            story(4, "Kotlin").apply {
                score = Int.MAX_VALUE; descendants = Int.MAX_VALUE; createdAtEpochSeconds = Int.MAX_VALUE
            },
            story(5, "Kotlin").apply {
                score = Int.MAX_VALUE; descendants = Int.MAX_VALUE; createdAtEpochSeconds = Int.MAX_VALUE
            },
            story(6, "Kotlin memory").apply { score = Int.MAX_VALUE },
        )

        StorySearchRanking.sortStoriesByRelevance(stories, "kotlin")

        assertEquals(listOf(4, 5, 3, 2, 1, 6), stories.map(Story::id))
    }

    private fun story(id: Int, title: String) = Story().apply {
        this.id = id
        this.title = title
    }
}
