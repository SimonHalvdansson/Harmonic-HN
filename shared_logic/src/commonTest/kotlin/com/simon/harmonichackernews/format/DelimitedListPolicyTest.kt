package com.simon.harmonichackernews.format

import kotlin.test.Test
import kotlin.test.assertEquals

class DelimitedListPolicyTest {
    @Test
    fun trimsDropsEmptyAndDeduplicatesIgnoringCase() {
        assertEquals(
            listOf("alpha", "Beta", "gamma"),
            DelimitedListPolicy.parseCommaSeparated(" alpha, Beta,,ALPHA, gamma "),
        )
    }
}
