@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.comments.ZoomableImageViewport
import com.simon.harmonichackernews.ui.common.TransformOverlay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewImageGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var imageCoordinates: LayoutCoordinates
    private val dismissals = mutableIntStateOf(0)

    private fun showImage(aspectRatio: Float = 1.5f, maxHeight: Dp = 520.dp) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("root")) {
                TransformOverlay(
                    contentKey = "image", sourceBounds = null,
                    dismissRequestVersion = 0, predictiveBackProgress = 0f, predictiveBackEdge = 0,
                    maxWidth = 320.dp, horizontalPadding = 24.dp, verticalPadding = 24.dp,
                    targetCornerRadius = 0.dp, containerColor = Color.Transparent,
                    consumeAllGestures = false, verticalSwipeDismissEnabled = true,
                    onDismissRequest = { dismissals.intValue++ }, onDismissAnimationFinished = {},
                ) {
                    ZoomableImageViewport(
                        aspectRatio = aspectRatio,
                        onDismissRequest = { dismissals.intValue++ },
                        modifier = Modifier.heightIn(max = maxHeight).testTag("viewport"),
                    ) { modifier ->
                        Box(modifier.onGloballyPositioned { imageCoordinates = it }.background(Color.Blue).testTag("image"))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun viewportBounds(): Rect = compose.onNodeWithTag("viewport", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun imageBounds(): Rect = compose.runOnIdle {
        Rect(imageCoordinates.localToRoot(Offset.Zero), imageCoordinates.localToRoot(
            Offset(imageCoordinates.size.width.toFloat(), imageCoordinates.size.height.toFloat()),
        ))
    }

    @Test fun doubleTapZoomsAtTheTapAndSecondDoubleTapResets() {
        showImage()
        val before = imageBounds()
        val beforeViewport = viewportBounds()
        compose.onNodeWithTag("viewport", useUnmergedTree = true).performTouchInput { doubleClick(Offset(width * 0.7f, height * 0.6f)) }
        compose.waitForIdle()
        val zoomed = imageBounds()
        assertEquals(before.width * 3f, zoomed.width, 1f)
        val zoomedViewport = viewportBounds()
        assertTrue("Dialog should grow substantially", zoomedViewport.height > beforeViewport.height * 2f)
        assertEquals(beforeViewport.center.y, zoomedViewport.center.y, 1f)
        assertEquals(beforeViewport.width, zoomedViewport.width, 1f)
        assertTrue(zoomed.center.x < before.center.x)
        compose.onNodeWithTag("viewport", useUnmergedTree = true).performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertEquals(before, imageBounds())
        assertEquals(beforeViewport, viewportBounds())
        assertEquals(0, dismissals.intValue)
    }

    @Test fun verticalPinchAndZoomedPanDoNotDismissButOutsideTapDoes() {
        showImage()
        compose.onNodeWithTag("viewport", useUnmergedTree = true).performTouchInput {
            pinch(Offset(centerX, centerY - 20f), Offset(centerX, centerY + 20f),
                Offset(centerX, 30f), Offset(centerX, height - 30f), 500)
        }
        val zoomed = imageBounds()
        assertTrue("Pinch must magnify the image", zoomed.width > 320 * compose.activity.resources.displayMetrics.density * 2f)
        compose.onNodeWithTag("viewport", useUnmergedTree = true).performTouchInput { swipe(center, Offset(centerX, height - 10f), 400) }
        assertTrue("Drag must pan the magnified image", imageBounds().top > zoomed.top)
        compose.waitForIdle()
        assertEquals(0, dismissals.intValue)
        compose.onNodeWithTag("root").performTouchInput { click(Offset(12f, 12f)) }
        compose.runOnIdle { assertEquals(1, dismissals.intValue) }
    }


    @Test fun continuousPinchKeepsGrowingAcrossLayoutPassesAndShrinksBackWithoutDismissal() {
        showImage(aspectRatio = 3f)
        val initial = viewportBounds()
        val initialImage = imageBounds()
        val center = initial.center
        val root = compose.onNodeWithTag("root")
        root.performTouchInput {
            down(0, center - Offset(40f, 0f))
            down(1, center + Offset(40f, 0f))
        }
        var previousHeight = initial.height
        // Separate injections let Compose lay out the taller dialog while both pointers stay down.
        for (span in listOf(80f, 100f, 120f, 160f)) {
            root.performTouchInput {
                updatePointerTo(0, center - Offset(span, 0f))
                updatePointerTo(1, center + Offset(span, 0f))
                move()
            }
            compose.waitForIdle()
            val viewport = viewportBounds()
            assertTrue("Each pinch step must grow the dialog: $previousHeight -> ${viewport.height}", viewport.height > previousHeight)
            assertEquals(initial.center.y, viewport.center.y, 1f)
            previousHeight = viewport.height
        }
        assertEquals(initialImage.width * 4f, imageBounds().width, 3f)
        for (span in listOf(120f, 80f, 40f)) {
            root.performTouchInput {
                updatePointerTo(0, center - Offset(span, 0f))
                updatePointerTo(1, center + Offset(span, 0f))
                move()
            }
            compose.waitForIdle()
            assertTrue(viewportBounds().height < previousHeight)
            previousHeight = viewportBounds().height
        }
        root.performTouchInput { up(0); up(1) }
        assertEquals(initial, viewportBounds())
        assertEquals(initialImage.width, imageBounds().width, 1f)
        assertEquals(0, dismissals.intValue)
    }

    @Test fun portraitZoomKeepsTheHeightLimitAndCanBeReset() {
        showImage(aspectRatio = 0.25f, maxHeight = 400.dp)
        val before = viewportBounds()
        val image = imageBounds()
        compose.onNodeWithTag("viewport", true).performTouchInput { doubleClick(center) }
        assertEquals(before, viewportBounds())
        assertEquals(image.width * 3f, imageBounds().width, 1f)
        compose.onNodeWithTag("viewport", true).performTouchInput { doubleClick(center) }
        assertEquals(before, viewportBounds())
        assertEquals(image, imageBounds())
        assertEquals(0, dismissals.intValue)
    }

    @Test fun growingDialogRevealsTheImageAtBothEdgesDuringDoubleTapAnimation() {
        showImage(aspectRatio = 3f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("viewport", true).performTouchInput { doubleClick(center) }
        val heights = mutableListOf<Float>()
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            val viewport = viewportBounds()
            heights += viewport.height
            val pixels = compose.onNodeWithTag("root").captureToImage().toPixelMap()
            val x = viewport.center.x.toInt()
            for (y in listOf((viewport.top + 4).toInt(), (viewport.bottom - 4).toInt())) {
                assertEquals("Newly revealed image edge must not be clipped (frame $it)", Color.Blue, pixels[x, y])
            }
        }
        assertTrue("The dialog should grow throughout the animation", heights.last() > heights.first() * 2f)
        assertTrue("Growth must be monotonic", heights.zipWithNext().all { (a, b) -> b >= a })
        compose.mainClock.autoAdvance = true
    }

    @Test fun fittedImageStillSupportsSwipeToDismiss() {
        showImage()
        compose.onNodeWithTag("viewport", useUnmergedTree = true).performTouchInput { swipe(center, Offset(centerX, height + 300f), 300) }
        compose.runOnIdle { assertEquals(1, dismissals.intValue) }
    }

    @Test fun tappingLetterboxSpaceDismisses() {
        showImage(aspectRatio = 0.25f, maxHeight = 220.dp)
        compose.onNodeWithTag("viewport", useUnmergedTree = true).assertHeightIsEqualTo(220.dp)
        compose.onNodeWithTag("viewport", useUnmergedTree = true).performTouchInput { click(Offset(10f, centerY)) }
        compose.mainClock.advanceTimeBy(500) // Single taps wait for the double-tap window.
        compose.runOnIdle { assertEquals(1, dismissals.intValue) }
    }
}
