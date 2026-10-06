package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenCornersTest {
    private val corners = ScreenCorners(44f, 42f, 30f, 32f)
    private val window = IntSize(400, 800)

    @Test
    fun onlyFullWindowViewportUsesScreenCorners() {
        assertEquals(corners, corners.forViewport(Rect(0f, 0f, 400f, 800f), window))
        assertEquals(ScreenCorners(), corners.forViewport(Rect(200f, 0f, 400f, 800f), window))
        assertEquals(ScreenCorners(), corners.forViewport(Rect(0f, 24f, 400f, 800f), window))
        assertEquals(ScreenCorners(), corners.forViewport(null, window))
        assertEquals(ScreenCorners(), corners.forViewport(Rect.Zero, IntSize.Zero))
    }

    @Test
    fun morphPreservesEachPhysicalCornerAtBothEndsAndInBetween() {
        assertEquals(ScreenCorners(28f, 28f, 28f, 28f), corners.interpolateFrom(28f, 0f))
        assertEquals(ScreenCorners(36f, 35f, 29f, 30f), corners.interpolateFrom(28f, 0.5f))
        assertEquals(corners, corners.interpolateFrom(28f, 1f))
        assertEquals(corners, corners.interpolateFrom(28f, 2f))
    }

    @Test
    fun unsupportedWindowsStillMorphToSquareCorners() {
        assertEquals(ScreenCorners(7f, 7f, 7f, 7f), ScreenCorners().interpolateFrom(14f, 0.5f))
        assertEquals(ScreenCorners(), ScreenCorners().interpolateFrom(14f, 1f))
    }
}
