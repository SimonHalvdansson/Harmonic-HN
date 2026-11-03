package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlin.test.*

class AskVisualPolicyTest {
    @Test fun sentBubblesMaintainReadableContrastAcrossThemes() {
        for (name in listOf("classic", "pure", "dynamic", "hacker", "hacker_news", "gray", "rose", "teal")) for (dark in listOf(false, true)) {
            val scheme = HarmonicThemeCatalog.scheme(name, dark).colorScheme
            val (background, foreground) = askUserBubbleColors(scheme)
            val a = background.luminance(); val b = foreground.luminance()
            assertTrue((maxOf(a,b) + .05f) / (minOf(a,b) + .05f) >= 4.49f, "$name / dark=$dark")
        }
    }
    @Test fun predictiveBackdropIsSubtleAndClearsOnCancellationAndDismissal() {
        assertEquals(0f, askBackgroundDimAlpha(1f))
        assertEquals(.12f, askBackgroundDimAlpha(.65f), .001f)
        assertEquals(0f, askBackgroundDimAlpha(0f))
        for (i in -10..110) assertTrue(askBackgroundDimAlpha(i / 100f) in 0f.. .12f)
    }
}
