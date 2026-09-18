package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentsScrollbarPolicyTest {
    @Test
    fun equalHeightRowsFillTheTrackAtStartMiddleAndEnd() {
        val top = commentsScrollbarMetrics(100, 0, 0f, 19, 1f, false, true)!!
        val middle = commentsScrollbarMetrics(100, 40, 0f, 59, 1f, true, true)!!
        val bottom = commentsScrollbarMetrics(100, 80, 0f, 99, 1f, true, false)!!
        assertEquals(0.2f, middle.visibleFraction)
        assertEquals(0f, top.scrollPosition)
        assertEquals(0.5f, middle.scrollPosition)
        assertEquals(1f, bottom.scrollPosition)
    }

    @Test
    fun partialVariableHeightRowsStillReachBothEnds() {
        assertEquals(0f, commentsScrollbarMetrics(8, 0, 0f, 2, 0.3f, false, true)!!.scrollPosition)
        assertEquals(1f, commentsScrollbarMetrics(8, 6, 0.7f, 7, 1f, true, false)!!.scrollPosition)
    }

    @Test
    fun oneTallCommentStillHasAScrollbarButContentThatFitsDoesNot() {
        val single = commentsScrollbarMetrics(1, 0, 0.75f, 0, 1f, true, false)!!
        assertEquals(0.25f, single.visibleFraction)
        assertEquals(1f, single.scrollPosition)
        assertNull(commentsScrollbarMetrics(1, 0, 0f, 0, 1f, false, false))
    }
}
