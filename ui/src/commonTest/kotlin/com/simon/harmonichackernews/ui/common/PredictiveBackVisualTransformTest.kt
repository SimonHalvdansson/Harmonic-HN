package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class PredictiveBackVisualTransformTest {
    @Test
    fun predictiveTransformUnwindsWithTheContainerMorph() {
        assertEquals(0.75f, predictiveBackVisualProgress(0.5f), absoluteTolerance = 0.001f)
        assertEquals(
            0.3f,
            predictiveBackVisualProgress(0.5f, transformProgress = 0.4f),
            absoluteTolerance = 0.001f,
        )
        assertEquals(
            0f,
            predictiveBackVisualProgress(0.5f, transformProgress = 0f),
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun childBoundsUseTheContainerTransformPivot() {
        val transformed = Rect(120f, 220f, 180f, 280f).transformedForPredictiveBack(
            scale = 0.9f,
            pivotFractionX = 0f,
            translation = Offset(56f, 18f),
            pivotBounds = Rect(100f, 200f, 300f, 500f),
        )

        assertEquals(174f, transformed.left, absoluteTolerance = 0.001f)
        assertEquals(251f, transformed.top, absoluteTolerance = 0.001f)
        assertEquals(228f, transformed.right, absoluteTolerance = 0.001f)
        assertEquals(305f, transformed.bottom, absoluteTolerance = 0.001f)
    }

    @Test
    fun cropCorrectionMakesNonUniformContainerScalingUniform() {
        val correction = aspectPreservingCropCorrection(scaleX = 0.8f, scaleY = 0.45f)

        assertEquals(1f, correction.scaleX, absoluteTolerance = 0.001f)
        assertEquals(0.8f, 0.45f * correction.scaleY, absoluteTolerance = 0.001f)
    }

    @Test
    fun cropCorrectionBecomesIdentityAtTheDialogBounds() {
        val correction = aspectPreservingCropCorrection(scaleX = 1f, scaleY = 1f)

        assertEquals(1f, correction.scaleX, absoluteTolerance = 0.001f)
        assertEquals(1f, correction.scaleY, absoluteTolerance = 0.001f)
    }

    @Test
    fun targetGeometryIsFrozenDuringPredictiveBackAndItsDismissHandoff() {
        assertEquals(
            true,
            shouldUpdateRestingTargetGeometry(
                predictiveBackProgress = 0f,
                dismissRequestVersion = 0,
            ),
        )
        assertEquals(
            false,
            shouldUpdateRestingTargetGeometry(
                predictiveBackProgress = 0.35f,
                dismissRequestVersion = 0,
            ),
        )
        assertEquals(
            false,
            shouldUpdateRestingTargetGeometry(
                predictiveBackProgress = 0.35f,
                dismissRequestVersion = 1,
            ),
        )
        assertEquals(
            false,
            shouldUpdateRestingTargetGeometry(
                predictiveBackProgress = 0f,
                dismissRequestVersion = 1,
            ),
        )
    }
}
