package com.simon.harmonichackernews.ui.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryContentDimmingTest {
    @Test fun unreadContentRemainsFullyOpaqueInEveryAppearance() {
        for (dark in listOf(false, true)) for (tinted in listOf(false, true)) {
            assertEquals(1f, storyContentAlpha(false, dark, tinted))
            assertEquals(1f, storyCategoryAlpha(storyContentAlpha(false, dark, tinted)))
        }
    }

    @Test fun clickedContentUsesExpectedOpacityForEachAppearance() {
        assertEquals(0.70f, storyContentAlpha(true, darkTheme = false, tintedBackground = false))
        assertEquals(0.75f, storyContentAlpha(true, darkTheme = false, tintedBackground = true))
        assertEquals(0.65f, storyContentAlpha(true, darkTheme = true, tintedBackground = false))
        assertEquals(0.67f, storyContentAlpha(true, darkTheme = true, tintedBackground = true))
    }

    @Test fun tintedContentUsesGentlerDimmingAndDarkModeUsesStrongerDimming() {
        for (dark in listOf(false, true))
            assertTrue(storyContentAlpha(true, dark, true) > storyContentAlpha(true, dark, false))
        for (tinted in listOf(false, true))
            assertTrue(storyContentAlpha(true, true, tinted) < storyContentAlpha(true, false, tinted))
    }
}
