package com.simon.harmonichackernews.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class PredictiveBackDialogTest {
    @Test
    fun restingDialogIsUnchanged() {
        val visuals = dialogPredictiveBackVisuals(0f)

        assertEquals(1f, visuals.scale, absoluteTolerance = 0.001f)
        assertEquals(1f, visuals.alpha, absoluteTolerance = 0.001f)
        assertEquals(0f, visuals.translationXDp, absoluteTolerance = 0.001f)
        assertEquals(0f, visuals.translationYDp, absoluteTolerance = 0.001f)
    }

    @Test
    fun committedDialogShrinksAndMostlyFadesAtTheScreenCenter() {
        val visuals = dialogPredictiveBackVisuals(1f)

        assertEquals(0.82f, visuals.scale, absoluteTolerance = 0.001f)
        assertEquals(0.08f, visuals.alpha, absoluteTolerance = 0.001f)
        assertEquals(0f, visuals.translationXDp, absoluteTolerance = 0.001f)
        assertEquals(0f, visuals.translationYDp, absoluteTolerance = 0.001f)
    }

    @Test
    fun heldGestureOnlyPreviewsPartOfDismissalAndPullsFromTheEdge() {
        val heldProgress = dialogPredictiveBackGestureProgress(1f)
        val leftEdge = dialogPredictiveBackVisuals(heldProgress, swipeDirection = 1f)
        val rightEdge = dialogPredictiveBackVisuals(heldProgress, swipeDirection = -1f)

        assertEquals(0.42f, heldProgress, absoluteTolerance = 0.001f)
        assertEquals(0.924f, leftEdge.scale, absoluteTolerance = 0.001f)
        assertEquals(1f, leftEdge.alpha, absoluteTolerance = 0.001f)
        assertEquals(31.0f, leftEdge.translationXDp, absoluteTolerance = 0.1f)
        assertEquals(-leftEdge.translationXDp, rightEdge.translationXDp, 0.001f)
        assertEquals(leftEdge.translationYDp, rightEdge.translationYDp, 0.001f)
    }

    @Test
    fun committedSettleStartsFadingOnlyAfterHeldGesturePhase() {
        val heldProgress = dialogPredictiveBackGestureProgress(1f)

        assertEquals(
            1f,
            dialogPredictiveBackVisuals(heldProgress).alpha,
            absoluteTolerance = 0.001f,
        )
        assertEquals(
            1f,
            dialogPredictiveBackVisuals(heldProgress - 0.01f).alpha,
            absoluteTolerance = 0.001f,
        )
        assertEquals(
            0.984f,
            dialogPredictiveBackVisuals(heldProgress + 0.01f).alpha,
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun visualProgressIsClamped() {
        assertEquals(
            dialogPredictiveBackVisuals(0f),
            dialogPredictiveBackVisuals(-1f),
        )
        assertEquals(
            dialogPredictiveBackVisuals(1f),
            dialogPredictiveBackVisuals(2f),
        )
    }
}
