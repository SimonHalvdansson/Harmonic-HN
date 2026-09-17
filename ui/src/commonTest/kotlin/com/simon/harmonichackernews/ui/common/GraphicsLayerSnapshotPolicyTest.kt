package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphicsLayerSnapshotPolicyTest {
    @Test
    fun keepsSmallCommentSnapshotsAtTheirOriginalResolution() {
        val size = IntSize(1080, 1750)
        assertEquals(size, boundedGraphicsLayerSnapshotSize(size))
    }

    @Test
    fun boundsLongCommentSnapshotsAcrossTextSizesAndScreenResolutions() {
        for (size in listOf(IntSize(1080, 2200), IntSize(1344, 2000), IntSize(1344, 20000))) {
            val bounded = boundedGraphicsLayerSnapshotSize(size)!!
            assertTrue(isGraphicsLayerSnapshotSizeSafe(bounded), "$size -> $bounded")
            assertTrue(bounded.width < size.width)
            assertTrue(bounded.height < size.height)
            // Rounding may change each dimension by less than one pixel.
            val scale = bounded.height.toDouble() / size.height
            assertTrue(kotlin.math.abs(bounded.width - size.width * scale) < 1.0)
        }
    }

    @Test
    fun boundsExtremeAspectRatiosAndRejectsEmptyLayers() {
        assertNull(boundedGraphicsLayerSnapshotSize(IntSize.Zero))
        assertNull(boundedGraphicsLayerSnapshotSize(IntSize(-1, 100)))
        for (size in listOf(IntSize(1, Int.MAX_VALUE), IntSize(Int.MAX_VALUE, 1), IntSize(Int.MAX_VALUE, Int.MAX_VALUE))) {
            assertTrue(isGraphicsLayerSnapshotSizeSafe(boundedGraphicsLayerSnapshotSize(size)!!))
        }
    }

    @Test
    fun acceptsAValidSnapshotAtTheByteLimit() {
        val size = IntSize(2048, 1024)

        assertEquals(MaxGraphicsLayerSnapshotBytes, graphicsLayerSnapshotByteCount(size))
        assertTrue(isGraphicsLayerSnapshotSizeSafe(size))
    }

    @Test
    fun rejectsSnapshotsBeyondTheByteOrDimensionLimits() {
        assertFalse(isGraphicsLayerSnapshotSizeSafe(IntSize(2049, 1024)))
        assertFalse(isGraphicsLayerSnapshotSizeSafe(IntSize(1, 4097)))
    }

    @Test
    fun rejectsInvalidSizesWithoutOverflowing() {
        assertNull(graphicsLayerSnapshotByteCount(IntSize(0, 100)))
        assertEquals(
            Long.MAX_VALUE,
            graphicsLayerSnapshotByteCount(IntSize(Int.MAX_VALUE, Int.MAX_VALUE)),
        )
        assertFalse(isGraphicsLayerSnapshotSizeSafe(IntSize(Int.MAX_VALUE, Int.MAX_VALUE)))
    }
}
