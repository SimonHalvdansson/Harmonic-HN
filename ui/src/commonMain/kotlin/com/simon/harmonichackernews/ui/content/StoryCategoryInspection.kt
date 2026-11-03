package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryResult

/** The exact cached result behind a row's labels; no inference is started by opening it. */
data class StoryCategoryInspection(
    val storyId: Int,
    val title: String,
    val url: String?,
    val result: ArticleCategoryResult,
    val preferences: ArticleCategoryPreferences,
    val sample: Boolean = false,
) {
    val labelScores get() = ArticleCategories.labelScores(result.scores, preferences)
    val labels get() = labelScores.mapNotNull { score ->
        preferences.categories.firstOrNull { it.id == score.categoryId }?.let(preferences::name)
    }
}

internal data class ArticleCategoryLabelPresentation(
    val labels: List<String> = emptyList(),
    val inspection: StoryCategoryInspection? = null,
) {
    companion object { val Empty = ArticleCategoryLabelPresentation() }
}
