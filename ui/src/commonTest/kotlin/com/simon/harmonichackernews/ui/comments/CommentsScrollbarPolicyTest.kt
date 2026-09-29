package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentsScrollbarPolicyTest {
    @Test
    fun equalHeightRowsFillTheTrackAtStartMiddleAndEnd() {
        val top = commentsScrollbarMetrics(0, 10_000, 2_000, false, true)!!
        val middle = commentsScrollbarMetrics(4_000, 10_000, 2_000, true, true)!!
        val bottom = commentsScrollbarMetrics(8_000, 10_000, 2_000, true, false)!!
        assertEquals(0.2f, middle.visibleFraction)
        assertEquals(0f, top.scrollPosition)
        assertEquals(0.5f, middle.scrollPosition)
        assertEquals(1f, bottom.scrollPosition)
    }

    @Test
    fun inaccurateEstimatesStillReachBothEnds() {
        assertEquals(0f, commentsScrollbarMetrics(50, 8_000, 2_300, false, true)!!.scrollPosition)
        assertEquals(1f, commentsScrollbarMetrics(6_700, 8_000, 1_000, true, false)!!.scrollPosition)
    }

    @Test
    fun oneTallCommentStillHasAScrollbarButContentThatFitsDoesNot() {
        val single = commentsScrollbarMetrics(3_000, 4_000, 1_000, true, false)!!
        assertEquals(0.25f, single.visibleFraction)
        assertEquals(1f, single.scrollPosition)
        assertNull(commentsScrollbarMetrics(0, 1_000, 1_000, false, false))
    }

    @Test
    fun unknownAndEmptyMeasurementsAreHiddenAndOffsetsAreClamped() {
        assertNull(commentsScrollbarMetrics(Int.MAX_VALUE, 4_000, 1_000, true, true))
        assertNull(commentsScrollbarMetrics(0, Int.MAX_VALUE, 1_000, false, true))
        assertNull(commentsScrollbarMetrics(0, 0, 0, false, true))
        assertEquals(0f, commentsScrollbarMetrics(-20, 4_000, 1_000, true, true)!!.scrollPosition)
        assertEquals(1f, commentsScrollbarMetrics(5_000, 4_000, 1_000, true, true)!!.scrollPosition)
    }
}
