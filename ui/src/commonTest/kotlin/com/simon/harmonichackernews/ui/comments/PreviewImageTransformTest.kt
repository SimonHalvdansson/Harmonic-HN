package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewImageTransformTest {
    private val viewport = Size(400f, 300f)

    @Test fun landscapeDialogGrowsWithZoomUntilItReachesTheAvailableHeight() {
        val geometry = PreviewImageGeometry(Size(400f, 800f), 2f)
        assertEquals(Size(400f, 200f), geometry.baseViewport)
        assertEquals(Size(400f, 300f), geometry.viewport(1.5f))
        assertEquals(Size(400f, 600f), geometry.viewport(3f))
        assertEquals(Size(400f, 800f), geometry.viewport(5f))
        assertEquals(Size(400f, 200f), geometry.image, "Growing the viewport must not refit the image")
    }

    @Test fun portraitRemainsWithinTheSafeAreaAndPanoramaUsesOnlyTheHeightItNeeds() {
        val portrait = PreviewImageGeometry(Size(400f, 800f), 0.25f)
        assertEquals(Size(400f, 800f), portrait.viewport(1f))
        assertEquals(portrait.viewport(1f), portrait.viewport(5f))
        assertEquals(Size(200f, 800f), portrait.image)
        assertEquals(Size(400f, 200f), PreviewImageGeometry(Size(400f, 800f), 10f).viewport(5f))
    }

    @Test fun growingViewportCentersTheImageUntilVerticalPanningIsNeededAndResetRemovesPan() {
        val geometry = PreviewImageGeometry(Size(400f, 800f), 2f)
        val zoomed = geometry.zoomBy(PreviewImageTransform(), 3f, Offset(260f, 120f), Offset(0f, 100f))
        assertEquals(Offset(-120f, 0f), zoomed.offset)
        val capped = geometry.zoomBy(zoomed, 2f, Offset(200f, 300f), Offset(10000f, 10000f))
        assertEquals(Offset(800f, 100f), capped.offset)
        assertEquals(PreviewImageTransform(), geometry.zoomBy(capped, 0.1f, Offset.Zero, Offset.Zero))
    }

    @Test fun zoomKeepsTheTouchedImagePointUnderTheFinger() {
        val focus = Offset(260f, 170f)
        val pan = Offset(12f, -8f)
        val result = PreviewImageTransform().zoomBy(3f, focus, pan, viewport, viewport)
        val center = Offset(200f, 150f)
        assertEquals(focus + pan, center + (focus - center) * result.scale + result.offset)
    }

    @Test fun panningCannotExposeEmptySpacePastImageEdges() {
        val result = PreviewImageTransform(3f, Offset(10000f, -10000f)).bounded(viewport, viewport)
        assertEquals(Offset(400f, -300f), result.offset)
    }

    @Test fun zoomLimitsAndResetRemoveResidualPan() {
        val zoomed = PreviewImageTransform().zoomBy(100f, Offset(260f, 170f), Offset.Zero, viewport, viewport)
        assertEquals(PreviewImageMaxZoom, zoomed.scale)
        assertEquals(PreviewImageTransform(), zoomed.zoomBy(0.001f, Offset.Zero, Offset(50f, 50f), viewport, viewport))
    }

    @Test fun portraitAndPanoramaStayFittedAndCenterOnTheShortAxis() {
        val portrait = fittedPreviewImageSize(viewport, 0.1f)
        assertEquals(Size(30f, 300f), portrait)
        val result = PreviewImageTransform(3f, Offset(100f, 100f)).bounded(viewport, portrait)
        assertEquals(0f, result.offset.x)
        assertEquals(Size(400f, 40f), fittedPreviewImageSize(viewport, 10f))
    }

    @Test fun outsideHitTestingFollowsVisibleImageAfterZoomAndPan() {
        val image = Size(400f, 100f)
        val transform = PreviewImageTransform(2f, Offset(100f, 0f)).bounded(viewport, image)
        val bounds = transform.imageBounds(viewport, image)
        assertFalse(bounds.contains(Offset(200f, 20f)))
        assertTrue(bounds.contains(Offset(200f, 100f)))
    }

    @Test fun resizingReclampsTheExistingPan() {
        val result = PreviewImageTransform(2f, Offset(200f, 150f))
            .bounded(Size(200f, 100f), Size(200f, 100f))
        assertEquals(Offset(100f, 50f), result.offset)
    }
}
