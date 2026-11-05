package com.simon.harmonichackernews.ui.stories

import kotlin.test.*

class StoryPreviewAccessoryAlphaTest {
    @Test fun closingAccessoriesBeginFadingInImmediatelyAndFinishBeforeHandoff() {
        assertEquals(0f, storyPreviewAccessoryAlpha(1f, closing = true))
        assertTrue(storyPreviewAccessoryAlpha(.95f, closing = true) > 0f)
        assertEquals(1f, storyPreviewAccessoryAlpha(.15f, closing = true))
        assertEquals(1f, storyPreviewAccessoryAlpha(0f, closing = true))
        val frames = (100 downTo 0).map { storyPreviewAccessoryAlpha(it / 100f, closing = true) }
        assertTrue(frames.zipWithNext().all { (before, after) -> after >= before })
    }
}
