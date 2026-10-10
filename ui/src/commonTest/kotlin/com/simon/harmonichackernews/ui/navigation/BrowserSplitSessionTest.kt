package com.simon.harmonichackernews.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserSplitSessionTest {
    @Test fun browserAdjustmentsRestoreTheOpeningRatioAndNeverBecomeTheNextOpening() {
        val session = BrowserSplitSession()
        session.update(0.43f, true, true, 1f)
        session.update(0.43f, true, false, 0f)
        session.adjust(0.65f)
        assertEquals(0.65f, session.target(0.43f, false, true, 0f))
        assertEquals(0.43f, session.target(0.43f, false, true, 0.2f))
        session.update(0.43f, true, false, 1f)
        assertNull(session.openingRatio)
        session.update(0.43f, true, true, 1f)
        assertEquals(0.43f, session.ratio)
    }

    @Test fun cancellingBackReturnsToFullWidthWithoutLosingTheOriginalRatio() {
        val session = BrowserSplitSession()
        session.update(0.6f, true, false, 0f)
        session.adjust(0f)
        assertEquals(0.6f, session.target(0.6f, false, false, 0.15f))
        assertEquals(0f, session.target(0.6f, false, false, 0f))
        assertEquals(0.6f, session.openingRatio)
    }

    @Test fun standaloneStoriesRevealFromZeroAndReturnToZero() {
        val session = BrowserSplitSession()
        assertEquals(0f, session.target(0.5f, true, false, 1f))
        session.update(0.5f, true, true, 1f)
        assertEquals(0.5f, session.target(0.5f, true, true, 1f))
        session.update(0.5f, true, false, 0f)
        assertEquals(0f, session.target(0.5f, true, true, 0.2f))
    }

    @Test fun releasingTheDividerSettlesToFullWidthOrTheMinimumUsablePane() {
        val session = BrowserSplitSession()
        session.adjust(0.1f)
        session.settle()
        assertEquals(0f, session.ratio)
        session.adjust(0.2f)
        session.settle()
        assertEquals(0.3f, session.ratio)
        session.adjust(1f)
        assertEquals(0.7f, session.ratio)
        session.adjust(Float.NaN)
        assertEquals(0.7f, session.ratio)
    }
}
