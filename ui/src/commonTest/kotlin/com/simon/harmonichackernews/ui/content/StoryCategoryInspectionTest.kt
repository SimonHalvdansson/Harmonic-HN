package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.categorization.ArticleCategoryInputSource
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryResult
import com.simon.harmonichackernews.categorization.ArticleCategoryScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StoryCategoryInspectionTest {
    private val original = StoryCategoryInspection(
        storyId = 42,
        title = "A new compiler",
        url = null,
        result = ArticleCategoryResult(
            listOf(ArticleCategoryScore("ai", 0.4f), ArticleCategoryScore("software", 0.5f)),
            ArticleCategoryInputSource.TITLE_ONLY,
        ),
        preferences = ArticleCategoryPreferences(enabled = true),
    )

    @Test fun repeatedReadsReuseTheRankedPresentation() {
        assertEquals(listOf("software", "ai"), original.labelScores.map { it.categoryId })
        assertEquals(listOf("Programming", "AI"), original.labels)
        assertSame(original.labelScores, original.labelScores)
        assertSame(original.labels, original.labels)
    }

    @Test fun newPreferencesRebuildLabelsWithoutChangingThePreviousSnapshot() {
        val renamed = original.copy(preferences = original.preferences.copy(
            cutoff = 0.45f,
            names = mapOf("software" to "Development"),
        ))
        assertEquals(listOf("software"), renamed.labelScores.map { it.categoryId })
        assertEquals(listOf("Development"), renamed.labels)
        assertEquals(listOf("Programming", "AI"), original.labels)
        assertEquals(emptyList(), renamed.copy(preferences = renamed.preferences.copy(enabled = false)).labels)
        assertEquals(listOf("AI"), original.copy(preferences = original.preferences.copy(selected = setOf("ai"))).labels)
    }

    @Test fun newScoresRebuildTheRankedPresentation() {
        val updated = original.copy(result = original.result.copy(
            scores = listOf(ArticleCategoryScore("ai", 0.7f), ArticleCategoryScore("software", 0.2f)),
        ))
        assertEquals(listOf("ai"), updated.labelScores.map { it.categoryId })
        assertEquals(listOf("AI"), updated.labels)
        assertEquals(listOf("Programming", "AI"), original.labels)
    }
}
