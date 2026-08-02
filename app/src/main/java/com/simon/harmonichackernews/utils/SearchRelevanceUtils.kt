package com.simon.harmonichackernews.utils

import android.text.TextUtils
import com.simon.harmonichackernews.data.Story
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.max

object SearchRelevanceUtils {
    fun sortStoriesByRelevance(stories: MutableList<Story>, query: String?) {
        val normalizedQuery = normalize(query)
        if (stories == null || stories.size < 2 || TextUtils.isEmpty(normalizedQuery)) {
            return
        }

        val relevanceScores: MutableMap<Story, Int> = IdentityHashMap(stories.size)
        for (story in stories) {
            relevanceScores.put(story, score(story, normalizedQuery))
        }

        stories.sortWith(Comparator { left, right ->
            val leftScore = relevanceScores.getValue(left)
            val rightScore = relevanceScores.getValue(right)
            when {
                leftScore != rightScore -> rightScore.compareTo(leftScore)
                left.score != right.score -> right.score.compareTo(left.score)
                left.descendants != right.descendants -> right.descendants.compareTo(left.descendants)
                else -> right.time.compareTo(left.time)
            }
        })
    }

    private fun score(story: Story?, normalizedQuery: String): Int {
        if (story == null || story.title == null) {
            return 0
        }

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

    private fun normalize(value: String?): String {
        if (value == null) {
            return ""
        }

        return value.trim { it <= ' ' }.lowercase(Locale.getDefault())
    }

    private fun isWordBoundaryMatch(title: String, start: Int, length: Int): Boolean {
        val end = start + length
        return isBoundary(title, start - 1) && isBoundary(title, end)
    }

    private fun isBoundary(title: String, index: Int): Boolean {
        return index < 0 || index >= title.length || !Character.isLetterOrDigit(title.get(index))
    }
}
