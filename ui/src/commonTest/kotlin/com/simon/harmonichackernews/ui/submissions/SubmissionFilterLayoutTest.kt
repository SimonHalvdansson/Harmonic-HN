package com.simon.harmonichackernews.ui.submissions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubmissionFilterLayoutTest {
    @Test
    fun shrinkingWidthsKeepLabelsWholeAndSwitchAllIconsTogether() {
        val widths = listOf(48f, 16f, 78f)
        val roomy = submissionFilterLayout(400f, widths)
        assertEquals(listOf(1f, 1f, 1f), roomy.weights)
        val uneven = submissionFilterLayout(330f, widths)
        assertTrue(uneven.showIcons && uneven.showLabels)
        assertTrue(uneven.weights[1] < uneven.weights[0])
        assertTrue(uneven.weights[1] < uneven.weights[2])
        val text = submissionFilterLayout(240f, widths)
        assertFalse(text.showIcons)
        assertTrue(text.showLabels)
        val icons = submissionFilterLayout(180f, widths)
        assertTrue(icons.showIcons)
        assertFalse(icons.showLabels)
    }
}
