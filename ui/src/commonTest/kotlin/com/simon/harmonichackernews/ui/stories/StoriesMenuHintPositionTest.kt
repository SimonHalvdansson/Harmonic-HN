package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoriesMenuHintPositionTest {
    @Test
    fun popupHostsWithAndWithoutAppliedInsetsProduceTheSameRailPlacement() {
        val insets = StoriesMenuHintInsets(top = 2, right = 84, bottom = 8)
        val fullWindow = IntSize(466, 678)
        val popup = IntSize(320, 164)
        val full = StoriesMenuHintPositionProvider(insets, insets, fullWindow, 16, 8)
        val inset = StoriesMenuHintPositionProvider(insets, insets, fullWindow, 16, 8)
        val fullPosition = full.calculatePosition(IntRect(390, 168, 446, 224), fullWindow, LayoutDirection.Ltr, popup)
        val insetPosition = inset.calculatePosition(IntRect(390, 166, 446, 222), IntSize(382, 668), LayoutDirection.Ltr, popup)
        assertEquals(IntOffset(46, 114), fullPosition)
        assertEquals(fullPosition, insetPosition + IntOffset(0, 2))
        assertEquals(StoriesMenuHintPlacement.Left, full.placement)
        assertEquals(full.placement, inset.placement)
        assertEquals(82f, inset.caretOffset)
    }

    @Test
    fun headerMenuKeepsTheHintBelowAndPointsAtItsCenter() {
        val insets = StoriesMenuHintInsets(top = 62, bottom = 34)
        val position = StoriesMenuHintPositionProvider(insets, insets, IntSize(402, 874), 16, 8)
        val offset = position.calculatePosition(IntRect(338, 28, 386, 76), IntSize(402, 778), LayoutDirection.Ltr, IntSize(320, 164))
        assertEquals(IntOffset(66, 84), offset)
        assertEquals(StoriesMenuHintPlacement.Below, position.placement)
        assertEquals(362f, offset.x + position.caretOffset)
    }

    @Test
    fun railPlacementAdaptsToDifferentWindowAndRailProportions() {
        for ((width, height, rail) in listOf(
            Triple(360, 480, 56), Triple(720, 400, 96), Triple(540, 300, 120),
            Triple(900, 1000, 72), Triple(240, 220, 84),
        )) {
            val insets = StoriesMenuHintInsets(top = 2, right = rail, bottom = 8)
            val position = StoriesMenuHintPositionProvider(insets, insets, IntSize(width, height), 16, 8)
            val window = IntSize(width - rail, height - 10)
            val buttonWidth = minOf(56, rail - 12)
            val center = width - rail + (rail - 12) / 2
            val anchor = IntRect(center - buttonWidth / 2, height / 2 - 30, center + buttonWidth / 2, height / 2 + 26)
            val popup = IntSize(minOf(320, window.width - 32), minOf(280, window.height - 32))
            val offset = position.calculatePosition(anchor, window, LayoutDirection.Ltr, popup)
            assertEquals(StoriesMenuHintPlacement.Left, position.placement)
            assertTrue(offset.x >= 16 && offset.x + popup.width <= window.width - 16)
            assertTrue(offset.y >= 16 && offset.y + popup.height <= window.height - 16)
            assertEquals(anchor.center.y.toFloat(), offset.y + position.caretOffset)
        }
    }

    @Test
    fun leftRailUsesTheOppositeCaretInEitherLayoutDirection() {
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            val insets = StoriesMenuHintInsets(left = 84)
            val position = StoriesMenuHintPositionProvider(insets, insets, IntSize(466, 678), 16, 8)
            val offset = position.calculatePosition(IntRect(-64, 168, -8, 224), IntSize(382, 678), direction, IntSize(320, 164))
            assertEquals(StoriesMenuHintPlacement.Right, position.placement)
            assertEquals(IntOffset(16, 114), offset)
            assertEquals(82f, position.caretOffset)
        }
    }

    @Test
    fun headerHintFlipsAboveWhenThereIsNoRoomBelow() {
        val position = StoriesMenuHintPositionProvider(StoriesMenuHintInsets(), StoriesMenuHintInsets(), IntSize(400, 300), 16, 8)
        val offset = position.calculatePosition(IntRect(336, 220, 384, 268), IntSize(400, 300), LayoutDirection.Ltr, IntSize(320, 164))
        assertEquals(StoriesMenuHintPlacement.Above, position.placement)
        assertEquals(48, offset.y)
        assertEquals(360f, offset.x + position.caretOffset)
    }
}
