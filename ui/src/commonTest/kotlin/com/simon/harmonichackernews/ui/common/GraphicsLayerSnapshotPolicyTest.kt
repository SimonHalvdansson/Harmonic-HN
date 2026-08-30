package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphicsLayerSnapshotPolicyTest {
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
