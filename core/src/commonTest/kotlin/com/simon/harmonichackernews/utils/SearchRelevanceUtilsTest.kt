package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchRelevanceUtilsTest {
    @Test
    fun relevanceSortPreservesRankingAndOriginalOrderForTies() {
        val stories = mutableListOf(
            story(id = 4, title = "Using Kotlin"),
            story(id = 1, title = "Kotlin"),
            story(id = 2, title = "Kotlin memory"),
            story(id = 3, title = "Kotlin memory"),
        )

        SearchRelevanceUtils.sortStoriesByRelevance(stories, " kotlin ")

        assertEquals(listOf(1, 2, 3, 4), stories.map(Story::id))
    }

    private fun story(id: Int, title: String) = Story().apply {
        this.id = id
        this.title = title
    }
}
