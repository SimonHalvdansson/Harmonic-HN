package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryMapMotionTest {
    @Test
    fun pointLabelAndConnectorRetargetFromTheSameDisplayedFrame() {
        val first = CategoryMapLayout("umap", listOf(Offset(10f, 20f)), listOf(Offset(30f, 40f)), listOf(Rect(0f, 0f, 40f, 20f)))
        val second = CategoryMapLayout("tsne", listOf(Offset(110f, 220f)), listOf(Offset(130f, 240f)), listOf(Rect(100f, 100f, 140f, 120f)))
        val middle = sampleCategoryMap(first, second, 0.5f)
        assertEquals(Offset(60f, 120f), middle.points.single())
        assertEquals(Offset(80f, 140f), middle.centers.single())
        assertEquals(Rect(50f, 50f, 90f, 70f), middle.labels.single())
        assertEquals(middle.copy(method = first.method), sampleCategoryMap(middle, first, 0f))
        assertEquals(first, sampleCategoryMap(middle, first, 1f))
    }
}
