package com.simon.harmonichackernews.ui.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryContentDimmingTest {
    @Test
    fun adjustmentIsReciprocalAndRespectsEveryThemeAndTintDefault() {
        for (dark in listOf(false, true)) for (tinted in listOf(false, true)) {
            val base = storyContentAlpha(true, dark, tinted)
            val right = storyContentAlpha(true, dark, tinted, 100)
            val left = storyContentAlpha(true, dark, tinted, -100)
            assertEquals(base + (1f - base) * 0.75f, right, 0.00001f)
            assertEquals(base * base / right, left, 0.00001f)
            assertEquals(base * base, left * right, 0.00001f)
            assertEquals(base, storyContentAlpha(true, dark, tinted, 0))
            assertEquals(right, storyContentAlpha(true, dark, tinted, 200))
            for (adjustment in -100..100) {
                assertEquals(1f, storyContentAlpha(false, dark, tinted, adjustment))
                assertTrue(storyContentAlpha(true, dark, tinted, adjustment) in left..right)
            }
        }
    }

    @Test fun unreadContentRemainsFullyOpaqueInEveryAppearance() {
        for (dark in listOf(false, true)) for (tinted in listOf(false, true)) {
            assertEquals(1f, storyContentAlpha(false, dark, tinted))
            assertEquals(1f, storyCategoryAlpha(storyContentAlpha(false, dark, tinted)))
        }
    }

    @Test fun clickedContentUsesExpectedOpacityForEachAppearance() {
        assertEquals(0.65f, storyContentAlpha(true, darkTheme = false, tintedBackground = false))
        assertEquals(0.70f, storyContentAlpha(true, darkTheme = false, tintedBackground = true))
        assertEquals(0.60f, storyContentAlpha(true, darkTheme = true, tintedBackground = false))
        assertEquals(0.62f, storyContentAlpha(true, darkTheme = true, tintedBackground = true))
    }

    @Test fun tintedContentUsesGentlerDimmingAndDarkModeUsesStrongerDimming() {
        for (dark in listOf(false, true))
            assertTrue(storyContentAlpha(true, dark, true) > storyContentAlpha(true, dark, false))
        for (tinted in listOf(false, true))
            assertTrue(storyContentAlpha(true, true, tinted) < storyContentAlpha(true, false, tinted))
    }
}
