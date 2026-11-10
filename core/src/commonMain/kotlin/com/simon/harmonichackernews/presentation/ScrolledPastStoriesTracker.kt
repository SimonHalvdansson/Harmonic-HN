package com.simon.harmonichackernews.presentation

/** Tracks rows leaving the top during forward scrolling, without marking restored positions. */
class ScrolledPastStoriesTracker {
    private var previousIds: List<Int> = emptyList()
    private var previousFirst = 0

    fun update(ids: List<Int>, firstVisibleIndex: Int, scrolling: Boolean): List<Int> {
        val first = firstVisibleIndex.coerceIn(0, ids.size)
        // Appending is safe; replacements, removals and reordering establish a new baseline.
        val samePrefix = previousIds.size >= first &&
            previousIds.take(first) == ids.take(first)
        val passed = if (scrolling && first > previousFirst && samePrefix) {
            ids.subList(previousFirst, first).toList()
        } else emptyList()
        previousIds = ids
        previousFirst = first
        return passed
    }
}
