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

    @Test fun clickedContentKeepsSeventyPercentOfTheOriginalTitleDimming() {
        for (dark in listOf(false, true)) for (tinted in listOf(false, true)) {
            val previous = when {
                dark && tinted -> 0.65f
                dark -> 0.55f
                tinted -> 0.75f
                else -> 0.70f
            }
            val opacity = storyContentAlpha(true, dark, tinted)
            assertEquals((1f - previous) * 0.7f, 1f - opacity, 0.0001f)
            assertTrue(opacity > previous && opacity < 1f)
        }
    }

    @Test fun tintedContentUsesGentlerDimmingAndDarkModeUsesStrongerDimming() {
        for (dark in listOf(false, true))
            assertTrue(storyContentAlpha(true, dark, true) > storyContentAlpha(true, dark, false))
        for (tinted in listOf(false, true))
            assertTrue(storyContentAlpha(true, true, tinted) < storyContentAlpha(true, false, tinted))
    }
}
