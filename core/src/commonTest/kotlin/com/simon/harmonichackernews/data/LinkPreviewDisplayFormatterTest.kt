package com.simon.harmonichackernews.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkPreviewDisplayFormatterTest {
    @Test
    fun modelAndRichPreviewCountsShareCompactUnits() {
        for ((number, expected) in listOf(588L to "588", 12_000L to "12K", 753_000L to "753K",
            10_902L to "10.9K", 114_888L to "115K", 2_780_000_000_000L to "2.78T")) {
            assertEquals(expected, LinkPreviewDisplayFormatter.formatCompactCount(number))
            assertEquals("$expected likes", HuggingFaceModelInfo(likes = number).formatLikes())
        }
    }

    @Test
    fun updatedDatesKeepYearAndRejectMalformedDates() {
        assertEquals("Updated: 2026-09-02", HuggingFaceModelInfo(lastModified = "2026-09-02T12:00:00Z").formatUpdated())
        for (date in listOf(null, "", "2026-13-02", "2026-02-30")) {
            assertNull(HuggingFaceModelInfo(lastModified = date).formatUpdated())
        }
    }
}
