package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.categorization.ArticleCategories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryCoverageTest {
    @Test
    fun curveKeepsScoresOutsideTheRangeWithoutRescalingItsHeight() {
        val coverage = CategoryCoverage(listOf(0.50f, 0.24f, 0.05f, -0.10f, -0.30f, 0.40f, 0.28f))
        assertEquals(4, coverage.matches(0.24f))
        assertEquals(3, coverage.matches(0.24001f))
        val curve = coverage.curve()
        assertEquals(CategoryCoveragePoint(ArticleCategories.MIN_CUTOFF, 4 / 7f), curve.first())
        assertEquals(CategoryCoveragePoint(ArticleCategories.MAX_CUTOFF, 1 / 7f), curve.last())
        assertTrue(curve.zipWithNext().all { (a, b) -> a.cutoff <= b.cutoff && a.fraction >= b.fraction })
        assertEquals(listOf(4 / 7f, 3 / 7f), curve.filter { it.cutoff == 0.24f }.map { it.fraction })
        assertTrue(curve.all { it.cutoff in ArticleCategories.MIN_CUTOFF..ArticleCategories.MAX_CUTOFF })
    }

    @Test
    fun bundledCurveHasOneHundredStoriesAndUsesTheirActualCoverage() {
        val scores = CategoryCoverageReference.bestScores
        assertEquals(100, scores.size)
        assertTrue(scores.all { it.isFinite() && it in -1f..1f })
        assertEquals(66, CategoryCoverage(scores).matches(0.24f))
        assertEquals(37, CategoryCoverage(scores).matches(0.30f))
        val curve = CategoryCoverageReference.curve
        assertEquals(scores.count { it >= ArticleCategories.MIN_CUTOFF } / 100f, curve.first().fraction)
        assertEquals(scores.count { it >= ArticleCategories.MAX_CUTOFF } / 100f, curve.last().fraction)
        assertTrue(curve.zipWithNext().all { (a, b) -> a.cutoff <= b.cutoff && a.fraction >= b.fraction })
    }
}
