package com.simon.harmonichackernews.ios

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosStatusBarPlacementTest {
    @Test
    fun ordinaryTopBarsAndSymmetricLandscapeCutoutsKeepTopProtection() {
        assertFalse(iosHasSideStatusBarInsets(0.dp, 0.dp))
        assertFalse(iosHasSideStatusBarInsets(59.dp, 59.dp))
        assertFalse(iosHasSideStatusBarInsets(28.dp, 16.dp))
    }

    @Test
    fun compactAndExpandedSideStripsDoNotNeedTopProtection() {
        assertTrue(iosHasSideStatusBarInsets(0.dp, 84.dp))
        assertTrue(iosHasSideStatusBarInsets(16.dp, 84.dp))
        assertTrue(iosHasSideStatusBarInsets(84.dp, 16.dp))
        assertFalse(iosHasSideStatusBarInsets(0.dp, 55.dp))
        assertTrue(iosHasSideStatusBarInsets(0.dp, 56.dp))
    }

    @Test
    fun browserProtectsTopBarsAndLeavesSideStripPanesFlush() {
        assertEquals(32.dp, iosBrowserTopInset(32.dp, false, false, false))
        assertEquals(0.dp, iosBrowserTopInset(16.dp, true, false, false))
        assertEquals(0.dp, iosBrowserTopInset(80.dp, true, false, true))
    }

    @Test
    fun topUpButtonKeepsItsOwnClearanceIncludingWhenItFitsInsideTheSafeArea() {
        assertEquals(123.dp, iosBrowserTopInset(59.dp, false, true, false))
        assertEquals(64.dp, iosBrowserTopInset(16.dp, true, true, false))
        assertEquals(80.dp, iosBrowserTopInset(80.dp, true, true, true))
    }
}
