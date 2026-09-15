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
        assertEquals(40f, sizing.arrowGap)
        assertFalse(sizing.searchInMenu)
    }

    @Test
    fun tightHeaderGivesUpArrowSpacingBeforeShrinkingText() {
        val sizing = storyHeaderSizing(340f, 200f, 0.8f)
        assertEquals(1f, sizing.textScale)
        assertEquals(16f, sizing.arrowGap)
        assertFalse(sizing.searchInMenu)
    }

    @Test
    fun narrowHeaderShrinksBeforeMovingSearchToMenu() {
        val sizing = storyHeaderSizing(300f, 200f, 0.8f)
        assertEquals(0.84f, sizing.textScale)
        assertFalse(sizing.searchInMenu)
        val narrower = storyHeaderSizing(280f, 200f, 0.8f)
        assertTrue(narrower.searchInMenu)
        assertEquals(0.98f, narrower.textScale)
    }

    @Test
    fun titleRemainsReadableEvenWhenItMustTruncate() {
        val sizing = storyHeaderSizing(180f, 220f, 0.8f)
        assertTrue(sizing.searchInMenu)
        assertEquals(0.8f, sizing.textScale)
        assertEquals(8f, sizing.arrowGap)
    }
}
