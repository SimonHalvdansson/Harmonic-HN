package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PollResultsTest {
    @Test
    fun partialResultsDoNotClaimOneOptionHasAllThePoints() {
        val options = listOf(option(1, 20), option(2, 0).copy(loaded = false))
        val total = pollTotalPoints(options)
        assertNull(total)
        assertNull(pollPointShare(20, total))
        assertNull(pollTotalPoints(options.map { if (!it.loaded) it.copy(loadFailed = true) else it }))
        assertNull(pollTotalPoints(emptyList()))
    }

    @Test
    fun completedResultsUseTheTotalAcrossAllOptions() {
        val total = pollTotalPoints(listOf(option(1, 30), option(2, 10)))
        assertEquals(40L, total)
        assertEquals(0.75f, pollPointShare(30, total))
        assertEquals(0.25f, pollPointShare(10, total))
    }

    @Test
    fun zeroPointPollHasEmptyBarsWithoutDivisionByZero() {
        val total = pollTotalPoints(listOf(option(1, 0), option(2, 0)))
        assertEquals(0L, total)
        assertEquals(0f, pollPointShare(0, total))
    }

    @Test
    fun pointTotalsDoNotOverflowAndBarWidthsStayInBounds() {
        val total = pollTotalPoints(listOf(option(1, Int.MAX_VALUE), option(2, Int.MAX_VALUE)))
        assertEquals(4_294_967_294L, total)
        assertEquals(0.5f, pollPointShare(Int.MAX_VALUE, total))
        assertEquals(0f, pollPointShare(-1, 10L))
        assertEquals(1f, pollPointShare(11, 10L))
    }

    private fun option(id: Int, points: Int) = PollOptionUi(
        id = id, loaded = true, loadFailed = false, text = "Option $id", points = points,
    )
}
