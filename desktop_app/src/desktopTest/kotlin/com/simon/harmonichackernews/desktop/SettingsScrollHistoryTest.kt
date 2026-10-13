package com.simon.harmonichackernews.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsScrollHistoryTest {
    @Test
    fun layoutClampingDoesNotAccumulateButDeliberateScrollingTransfers() {
        val history = SettingsScrollHistory()
        val state = history.createState("appearance")
        state.requestScrollToItem(3, 120)
        history.record("appearance", state)
        repeat(4) {
            // Reflow can fit the page into a smaller scroll range while fonts/panes remeasure.
            state.requestScrollToItem(1, 60)
            history.restore("appearance", state)
            assertEquals(3, state.firstVisibleItemIndex)
            assertEquals(120, state.firstVisibleItemScrollOffset)
            history.createState("settings-list") // A second page cannot erase this page's anchor.
            assertEquals(3, history.createState("appearance").firstVisibleItemIndex)
        }
        state.requestScrollToItem(7, 25)
        history.record("appearance", state)
        val recreated = history.createState("appearance")
        assertEquals(7, recreated.firstVisibleItemIndex)
        assertEquals(25, recreated.firstVisibleItemScrollOffset)
    }
}
