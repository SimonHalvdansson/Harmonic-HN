package com.simon.harmonichackernews.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class GroupedNumberFormatterTest {
    @Test
    fun groupsPositiveNegativeAndBoundaryValues() {
        assertEquals("0", GroupedNumberFormatter.format(0))
        assertEquals("999", GroupedNumberFormatter.format(999))
        assertEquals("1,000", GroupedNumberFormatter.format(1_000))
        assertEquals("12,345,678", GroupedNumberFormatter.format(12_345_678))
        assertEquals("-12,345", GroupedNumberFormatter.format(-12_345))
        assertEquals("-2,147,483,648", GroupedNumberFormatter.format(Int.MIN_VALUE))
    }
}
