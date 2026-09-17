package com.simon.harmonichackernews.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitPaneViewportTest {
    @Test
    fun handleStaysCenteredInGapAtEqualAndUnequalRatios() {
        assertEquals(476, splitHandleOffset(1000, 12f, 0.5f, 48))
        assertEquals(278, splitHandleOffset(1000, 12f, 0.3f, 48))
        assertEquals(674, splitHandleOffset(1000, 12f, 0.7f, 48))
        assertEquals(284, splitHandleOffset(1000, 40f, 0.3f, 48))
    }
}
