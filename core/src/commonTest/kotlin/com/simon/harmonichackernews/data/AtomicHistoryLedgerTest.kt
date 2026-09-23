package com.simon.harmonichackernews.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicHistoryLedgerTest {
    @Test
    fun serializationPreservesOrderDuplicatesAndNumericBoundaries() {
        val ledger = HistoryLedger()
        assertEquals("", ledger.serialize())
        ledger.initialize("1q100-1q200-2q200")
        assertEquals("1q200-2q200-1q100", ledger.serialize())

        ledger.record(Int.MIN_VALUE, Long.MIN_VALUE)
        ledger.record(Int.MAX_VALUE, Long.MAX_VALUE)
        ledger.record(0, 0)
        val version = ledger.changeVersion
        val entries = ledger.load()
        assertEquals(
            "1q200-2q200-1q100--2147483648q-9223372036854775808-2147483647q9223372036854775807-0q0",
            ledger.serialize(),
        )
        assertEquals(entries, ledger.load())
        assertEquals(version, ledger.changeVersion)
    }

    @Test
    fun stateTracksOnlyEffectiveMutations() = runTest {
        val ledger = AtomicHistoryLedger("1q100")
        val initialVersion = ledger.state.value.changeVersion

        assertFalse(ledger.record(1, 999))
        assertEquals(initialVersion, ledger.state.value.changeVersion)

        assertTrue(ledger.record(2, 200))
        assertEquals(listOf(1, 2), ledger.state.value.histories.map(History::id))
        assertEquals("1q100-2q200", ledger.state.value.serialized)
        assertEquals(initialVersion + 1, ledger.state.value.changeVersion)
    }

    @Test
    fun removeAndClearPublishNewSnapshots() = runTest {
        val ledger = AtomicHistoryLedger("1q100-2q200")

        assertTrue(ledger.remove(1))
        assertEquals(listOf(2), ledger.current().histories.map(History::id))

        ledger.clear()
        assertTrue(ledger.current().histories.isEmpty())
        assertEquals("", ledger.current().serialized)
    }

    @Test
    fun historyCapacityRetainsNewestEntries() {
        val ledger = HistoryLedger(maximumEntries = 3)

        assertTrue(ledger.initialize("1q100-2q200-3q300-4q400"))
        assertEquals(listOf(4, 3, 2), ledger.load().map(History::id))
        assertTrue(ledger.record(5, 500))
        assertEquals(listOf(4, 3, 5), ledger.load().map(History::id))
    }
}
