package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorSchemeSwatchLayoutTest {
    @Test
    fun sparseLastRowsAreBalancedForBothCatalogSizes() {
        assertEquals(5, balancedSwatchesPerRow(13, 6)) // 6 + 6 + 1 -> 5 + 5 + 3
        assertEquals(5, balancedSwatchesPerRow(14, 6)) // Android includes Dynamic.
        assertEquals(7, balancedSwatchesPerRow(13, 8)) // 8 + 5 -> 7 + 6
        assertEquals(7, balancedSwatchesPerRow(14, 8))
    }

    @Test
    fun alreadyOptimalRowsAndNarrowWidthsStayValid() {
        assertEquals(4, balancedSwatchesPerRow(13, 4))
        assertEquals(5, balancedSwatchesPerRow(13, 5))
        assertEquals(6, balancedSwatchesPerRow(12, 6))
        assertEquals(13, balancedSwatchesPerRow(13, 20))
        assertEquals(1, balancedSwatchesPerRow(13, 0))
        assertEquals(1, balancedSwatchesPerRow(0, 6))
    }

    @Test
    fun preservesRowCountAndMinimizesUnevennessAcrossCountsAndWidths() {
        for (count in 1..100) {
            for (capacity in 1..100) {
                val rows = (count - 1) / capacity + 1
                val perRow = balancedSwatchesPerRow(count, capacity)
                val finalRow = count - (rows - 1) * perRow
                assertTrue(perRow <= capacity)
                assertEquals(rows, (count - 1) / perRow + 1)
                assertTrue(finalRow in 1..perRow)
                val difference = perRow - finalRow
                for (candidate in 1..capacity) {
                    val candidateLast = count - (rows - 1) * candidate
                    if (candidateLast in 1..candidate) {
                        assertTrue(difference <= candidate - candidateLast)
                    }
                }
            }
        }
    }
}
