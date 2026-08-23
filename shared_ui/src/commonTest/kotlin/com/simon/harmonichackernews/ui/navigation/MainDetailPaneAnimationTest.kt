package com.simon.harmonichackernews.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class MainDetailPaneAnimationTest {
    @Test
    fun desktopFirstOpenAnimatesStoryEntrance() {
        assertEquals(
            MainDetailPaneAnimation(storySerial = 42),
            mainDetailPaneAnimation(
                previousStorySerial = null,
                nextStorySerial = 42,
                animateVisibilityChanges = true,
            ),
        )
    }

    @Test
    fun desktopCloseAnimatesEmptyDetailEntrance() {
        assertEquals(
            MainDetailPaneAnimation(animateEmptyDetail = true),
            mainDetailPaneAnimation(
                previousStorySerial = 42,
                nextStorySerial = null,
                animateVisibilityChanges = true,
            ),
        )
    }

    @Test
    fun storyReplacementStillAnimatesOnEveryHost() {
        assertEquals(
            MainDetailPaneAnimation(storySerial = 84),
            mainDetailPaneAnimation(
                previousStorySerial = 42,
                nextStorySerial = 84,
                animateVisibilityChanges = false,
            ),
        )
    }

    @Test
    fun initialCompositionDoesNotAnimateRestoredStory() {
        assertEquals(
            MainDetailPaneAnimation(),
            mainDetailPaneAnimation(
                previousStorySerial = 42,
                nextStorySerial = 42,
                animateVisibilityChanges = true,
            ),
        )
    }
}
