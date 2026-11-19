package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategoryInputSource
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryResult
import com.simon.harmonichackernews.categorization.ArticleCategoryScore
import com.simon.harmonichackernews.ui.content.StoryCategoryInspection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Label presentation only: no model inference, network, or disk access in the measurement. */
@RunWith(AndroidJUnit4::class)
class CategoryPresentationBenchmark {
    @get:Rule val rule = BenchmarkRule()
    @Volatile private var result: Any? = null
    private val preferences = ArticleCategoryPreferences(enabled = true)
    private val scores = ArticleCategoryResult(
        ArticleCategories.defaults.mapIndexed { index, category ->
            ArticleCategoryScore(category.id, 0.5f - index * 0.015f)
        },
        ArticleCategoryInputSource.PREVIEW_TEXT,
    )

    @Test fun repeatedChipReads() {
        val inspection = inspection()
        rule.measureRepeated {
            result = inspection.labels
            repeat(3) { index -> result = inspection.labelScores.getOrNull(index) }
        }
    }

    @Test fun createAndReadChips() {
        rule.measureRepeated {
            val inspection = inspection()
            result = inspection.labels
            repeat(3) { index -> result = inspection.labelScores.getOrNull(index) }
        }
    }

    private fun inspection() = StoryCategoryInspection(
        storyId = 1,
        title = "A repeatable category presentation",
        url = "https://example.com/article",
        result = scores,
        preferences = preferences,
    )
}
