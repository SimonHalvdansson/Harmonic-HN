package com.simon.harmonichackernews.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Holds real preview gestures across rendered frames, including the card's nested scroll area. */
@RunWith(AndroidJUnit4::class)
class StoryPreviewGestureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun slowUpwardDragInsideCardStaysUnderFinger() = assertHeldDrag(insideCard = true, direction = -1f)
    @Test fun slowDownwardDragInsideCardStaysUnderFinger() = assertHeldDrag(insideCard = true, direction = 1f)
    @Test fun slowDragOutsideCardStaysUnderFinger() = assertHeldDrag(insideCard = false, direction = -1f)
    @Test fun cancelledCardDragReturnsToCenter() = assertHeldDrag(insideCard = true, direction = -1f, cancelled = true)

    private fun assertHeldDrag(insideCard: Boolean, direction: Float, cancelled: Boolean = false) {
        val controller = requireNotNull(compose.activity.navigationController.storiesComposeController)
        val stories = (1..3).map { id ->
            StoryListItemSnapshot(
                StorySnapshot(id, title = "Gesture preview $id"),
                StoryPresentationSnapshot(loaded = true, isLink = false),
            )
        }
        compose.runOnIdle {
            controller.showStoryPreview(stories, List(3) { 0xffeeeeee.toInt() }, 2)
        }
        val title = compose.onNodeWithText("Gesture preview 2")
        val root = compose.onRoot()
        val resting = title.fetchSemanticsNode().boundsInRoot
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val step = rootBounds.height * 0.015f * direction
        var pointerPressed = false
        compose.mainClock.autoAdvance = false
        try {
            root.performTouchInput {
                down(Offset(if (insideCard) resting.center.x else 8f, resting.center.y) - rootBounds.topLeft)
            }
            pointerPressed = true
            // Separate events and rendered frames reproduce slow nested scrolling. A single
            // swipe injection can finish before the erroneous centering animation is observed.
            repeat(4) {
                root.performTouchInput { moveBy(Offset(0f, step), delayMillis = 100) }
                compose.mainClock.advanceTimeBy(100)
            }
            val dragged = title.fetchSemanticsNode().boundsInRoot.top
            assertTrue("The card must follow the slow drag", (dragged - resting.top) * direction > kotlin.math.abs(step))
            compose.mainClock.advanceTimeBy(600)
            val held = title.fetchSemanticsNode().boundsInRoot.top
            assertEquals("The card must not recenter before touch-up", dragged, held, 1f)
            root.performTouchInput {
                advanceEventTime(600)
                if (cancelled) cancel() else up()
            }
            pointerPressed = false
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals("A released short drag must recenter", resting.top, title.fetchSemanticsNode().boundsInRoot.top, 1f)
        } finally {
            if (pointerPressed) root.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { controller.completeStoryPreviewDismiss() }
        }
    }
}
