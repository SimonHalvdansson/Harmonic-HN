package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserPullStateTest {
    @Test fun shortPullCancelsAndUpwardScrollIsUntouchedAtRest() {
        val state = BrowserPullState(200f, 128f)
        assertEquals(0f, state.drag(-100f))
        state.drag(100f)
        assertFalse(state.release())
        assertEquals(0f, state.distance)
    }

    @Test fun longPullIsCappedAndReleaseResetsBeforeOpeningOnlyOnce() {
        val state = BrowserPullState(200f, 128f)
        assertEquals(1000f, state.drag(1000f))
        assertEquals(200f, state.distance)
        assertTrue(state.ready)
        assertTrue(state.release())
        assertEquals(0f, state.distance)
        assertFalse(state.release())
    }

    @Test fun reversingBelowThresholdDisarmsTheGesture() {
        val state = BrowserPullState(200f, 128f)
        state.drag(300f)
        assertTrue(state.ready)
        state.drag(-200f)
        assertFalse(state.ready)
        assertFalse(state.release())
    }

    @Test fun cancellingOrDisablingNeverOpensTheBrowser() {
        val state = BrowserPullState(200f, 128f)
        state.drag(1000f)
        state.reset()
        assertFalse(state.release())
    }
}
