package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.data.Story
import kotlin.math.max

object StorySearchRanking {
    fun sortStoriesByRelevance(stories: MutableList<Story>, query: String?) {
        val normalizedQuery = normalize(query)
        if (stories.size < 2 || normalizedQuery.isEmpty()) {
            return
        }

        val scoredStories = stories.mapIndexedTo(ArrayList(stories.size)) { index, story ->
            ScoredStory(
                story = story,
                relevanceScore = score(story, normalizedQuery),
                originalIndex = index,
            )
        }

        scoredStories.sortWith(relevanceOrder)
        scoredStories.forEachIndexed { index, scored -> stories[index] = scored.story }
    }

    // Compare primitive values directly: selector-based comparators box each score on the JVM.
    private val relevanceOrder = Comparator<ScoredStory> { first, second ->
        var order = second.relevanceScore.compareTo(first.relevanceScore)
        if (order == 0) order = second.story.score.compareTo(first.story.score)
        if (order == 0) order = second.story.descendants.compareTo(first.story.descendants)
        if (order == 0) order = second.story.createdAtEpochSeconds.compareTo(first.story.createdAtEpochSeconds)
        if (order == 0) order = first.originalIndex.compareTo(second.originalIndex)
        order
    }

    private fun score(story: Story, normalizedQuery: String): Int {
        val title = normalize(story.title)
        val phraseIndex = title.indexOf(normalizedQuery)
        if (phraseIndex < 0) {
            return 0
        }

        var score = 10000
        if (title == normalizedQuery) {
            score += 20000
        }
        if (phraseIndex == 0) {
            score += 8000
        }
        if (isWordBoundaryMatch(title, phraseIndex, normalizedQuery.length)) {
            score += 4000
        }

        score += max(0, 2000 - (phraseIndex * 100))
        score += max(0, 1000 - title.length)

        return score
    }

    private fun normalize(value: String?): String =
        value.orEmpty().trim { it <= ' ' }.lowercase()

    private fun isWordBoundaryMatch(title: String, start: Int, length: Int): Boolean {
        val end = start + length
        return isBoundary(title, start - 1) && isBoundary(title, end)
    }

    private fun isBoundary(title: String, index: Int): Boolean {
        return index !in title.indices || !title[index].isLetterOrDigit()
    }

    private data class ScoredStory(
        val story: Story,
        val relevanceScore: Int,
        val originalIndex: Int,
    )
}
