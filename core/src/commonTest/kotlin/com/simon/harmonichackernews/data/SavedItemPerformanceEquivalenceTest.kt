package com.simon.harmonichackernews.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SavedItemPerformanceEquivalenceTest {
    @Test
    fun historyDecodePreservesLenientParsingAndStableTimestampOrder() {
        val inputs = listOf(
            null, "", "broken", "-", "1q100-2q200-3q100-1q300",
            "0q0-2147483647q9223372036854775807-2147483648q1-1q9223372036854775808",
            "1q100-broken-2q200-3q-4qq300-+5q400-6q+500-007q000600-8q700-",
        )
        for (input in inputs) {
            for (sorted in listOf(false, true)) {
                val expected = SavedItemCodec.decode(input, sorted).map { History(it.id, it.created) }
                val actual = HistoryLedger.decodeHistories(input, sorted)
                assertEquals(expected, actual, "input=$input, sorted=$sorted")
                // The history API has always returned a mutable list, including empty inputs.
                actual.add(History(99, 0))
                assertEquals(expected + History(99, 0), actual)
            }
        }
        assertEquals(
            listOf(History(1, 300), History(2, 200), History(1, 100), History(3, 100)),
            HistoryLedger.decodeHistories("1q100-2q200-3q100-1q300", sorted = true),
        )
    }
}
