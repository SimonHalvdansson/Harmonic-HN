package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryPreviewPredictiveBackTransformTest {
    @Test
    fun leftEdgeTransformUsesTheCardsLeftCenterAsItsPivot() {
        val transformed = storyPreviewPredictiveBackBounds(
            bounds = Rect(120f, 220f, 180f, 280f),
            container = Rect(100f, 200f, 300f, 500f),
            scale = 0.9f,
            translationX = 56f,
            translationY = 18f,
            pivotFractionX = 0f,
        )

        assertRectEquals(Rect(174f, 251f, 228f, 305f), transformed)
    }

    @Test
    fun rightEdgeTransformUsesTheCardsRightCenterAsItsPivot() {
        val transformed = storyPreviewPredictiveBackBounds(
            bounds = Rect(100f, 200f, 300f, 500f),
            container = Rect(100f, 200f, 300f, 500f),
            scale = 0.9f,
            translationX = -56f,
            translationY = 18f,
            pivotFractionX = 1f,
        )

        assertRectEquals(Rect(64f, 233f, 244f, 503f), transformed)
    }

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, absoluteTolerance = 0.001f)
        assertEquals(expected.top, actual.top, absoluteTolerance = 0.001f)
        assertEquals(expected.right, actual.right, absoluteTolerance = 0.001f)
        assertEquals(expected.bottom, actual.bottom, absoluteTolerance = 0.001f)
    }
}
