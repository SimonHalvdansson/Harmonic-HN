package com.simon.harmonichackernews.presentation

/** Tracks rows leaving the top during forward scrolling, without marking restored positions. */
class ScrolledPastStoriesTracker {
    private var previousIds: List<Int> = emptyList()
    private var previousFirst = 0

    fun update(ids: List<Int>, firstVisibleIndex: Int, scrolling: Boolean): List<Int> {
        val first = firstVisibleIndex.coerceIn(0, ids.size)
        // Only forward movement can mark rows. Immutable ID snapshots are normally reused;
        // compare changed snapshots without allocating copies of the already-passed prefix.
        // Appending is safe; replacements, removals and reordering establish a new baseline.
        val passed = if (scrolling && first > previousFirst && hasSamePrefix(ids, first)) {
            ids.subList(previousFirst, first).toList()
        } else emptyList()
        previousIds = ids
        previousFirst = first
        return passed
    }

    private fun hasSamePrefix(ids: List<Int>, count: Int): Boolean {
        if (previousIds.size < count) return false
        if (previousIds === ids) return true
        for (index in 0 until count) {
            if (previousIds[index] != ids[index]) return false
        }
        return true
    }
}
