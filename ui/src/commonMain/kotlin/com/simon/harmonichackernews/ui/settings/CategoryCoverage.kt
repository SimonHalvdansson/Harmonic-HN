package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.categorization.ArticleCategories

internal data class CategoryCoveragePoint(val cutoff: Float, val fraction: Float)

/** Share of the fixed sample above each cutoff, including scores outside the slider range. */
internal class CategoryCoverage(scores: List<Float>) {
    val total = scores.size
    private val eligible = scores.sorted()

    fun matches(cutoff: Float): Int = eligible.count { it >= cutoff }
    fun fraction(cutoff: Float): Float = if (total == 0) 0f else matches(cutoff).toFloat() / total

    fun curve(): List<CategoryCoveragePoint> {
        if (total == 0) return emptyList()
        val minimum = ArticleCategories.MIN_CUTOFF
        val maximum = ArticleCategories.MAX_CUTOFF
        var remaining = matches(minimum)
        return buildList {
            add(CategoryCoveragePoint(minimum, remaining.toFloat() / total))
            for ((score, count) in eligible.filter { it >= minimum && it < maximum }.groupingBy { it }.eachCount()) {
                add(CategoryCoveragePoint(score, remaining.toFloat() / total))
                remaining -= count
                add(CategoryCoveragePoint(score, remaining.toFloat() / total))
            }
            add(CategoryCoveragePoint(maximum, fraction(maximum)))
        }
    }
}
