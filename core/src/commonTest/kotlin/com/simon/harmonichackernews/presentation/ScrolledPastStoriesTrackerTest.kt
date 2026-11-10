package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrolledPastStoriesTrackerTest {
    @Test
    fun onlyRowsPassedDuringForwardScrollingAreMarked() {
        val tracker = ScrolledPastStoriesTracker()
        val ids = (1..20).toList()
        assertTrue(tracker.update(ids, 5, false).isEmpty()) // Restored position.
        assertEquals(listOf(6, 7), tracker.update(ids, 7, true))
        assertTrue(tracker.update(ids, 7, false).isEmpty())
        assertTrue(tracker.update(ids, 3, true).isEmpty()) // Scrolling up.
        assertEquals(listOf(4), tracker.update(ids, 4, true))
        assertTrue(tracker.update(ids, 10, false).isEmpty()) // Layout change.
        assertEquals(listOf(11), tracker.update(ids, 11, true))
    }

    @Test
    fun replacementsResetTheBaselineButAppendingDoesNot() {
        val tracker = ScrolledPastStoriesTracker()
        tracker.update(listOf(1, 2, 3), 0, false)
        assertEquals(listOf(1, 2), tracker.update(listOf(1, 2, 3, 4), 2, true))
        assertTrue(tracker.update(listOf(10, 20, 30, 40), 3, true).isEmpty())
        tracker.update(listOf(10, 20, 30, 40), 1, false)
        assertTrue(tracker.update(listOf(10, 30, 40), 2, true).isEmpty())
        assertTrue(tracker.update(emptyList(), 0, false).isEmpty())
    }
}
