package com.simon.harmonichackernews.categorization

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleCategoryInspectionTest {
    @Test
    fun scoredLabelsKeepTheirIdsWhenTwoCategoriesHaveTheSameDisplayName() {
        val settings = ArticleCategoryPreferences(enabled = true,
            names = mapOf("ai" to "Research", "software" to "Research"))
        val scores = listOf(ArticleCategoryScore("ai", .40f), ArticleCategoryScore("software", .35f),
            ArticleCategoryScore("security", .29f))
        assertEquals(listOf("ai", "software"), ArticleCategories.labelScores(scores, settings).map { it.categoryId })
        assertEquals(listOf("Research", "Research"), ArticleCategories.labels(scores, settings))
        assertEquals(listOf("software"), ArticleCategories.labelScores(scores,
            settings.copy(selected = setOf("software", "security"))).map { it.categoryId })
    }

    @Test
    fun scoreSelectionUsesTheSameCutoffAndMaximumAsVisibleLabels() {
        val settings = ArticleCategoryPreferences(enabled = true, cutoff = .34f, maxLabels = 1)
        val scores = listOf(ArticleCategoryScore("ai", .40f), ArticleCategoryScore("software", .35f))
        assertEquals(listOf(scores.first()), ArticleCategories.labelScores(scores, settings))
        assertEquals(emptyList(), ArticleCategories.labelScores(scores, settings.copy(enabled = false)))
        assertEquals(emptyList(), ArticleCategories.labelScores(scores, settings.copy(cutoff = .45f)))
    }
}
