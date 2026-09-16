package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryHeaderSizingTest {
    @Test
    fun wideHeaderKeepsFullTitleAndSearch() {
        val sizing = storyHeaderSizing(500f, 200f, 0.8f)
        assertEquals(1f, sizing.textScale)
        assertEquals(20f, sizing.arrowGap)
        assertFalse(sizing.searchInMenu)
    }

    @Test
    fun tightHeaderReducesCaretGapBeforeShrinkingTitle() {
        val sizing = storyHeaderSizing(340f, 200f, 0.8f)
        assertEquals(1f, sizing.textScale)
        assertEquals(8f, sizing.arrowGap)
        assertFalse(sizing.searchInMenu)
    }

    @Test
    fun narrowHeaderShrinksBeforeMovingSearchToMenu() {
        val sizing = storyHeaderSizing(300f, 200f, 0.8f)
        assertEquals(0.82f, sizing.textScale)
        assertFalse(sizing.searchInMenu)
        val narrower = storyHeaderSizing(280f, 200f, 0.8f)
        assertTrue(narrower.searchInMenu)
        assertEquals(0.96f, narrower.textScale)
    }

    @Test
    fun titleRemainsReadableEvenWhenItMustTruncate() {
        val sizing = storyHeaderSizing(180f, 220f, 0.8f)
        assertTrue(sizing.searchInMenu)
        assertEquals(0.8f, sizing.textScale)
        assertEquals(4f, sizing.arrowGap)
    }

    @Test
    fun shortTitleKeepsFullSizeOnPhoneWithBothActionsAndWiderHitArea() {
        val sizing = storyHeaderSizing(300f, 160f, 0.65f)
        assertEquals(1f, sizing.textScale)
        assertFalse(sizing.searchInMenu)
        assertTrue(160f + 12f + 24f + sizing.arrowGap + 96f <= 300f)
    }
}
