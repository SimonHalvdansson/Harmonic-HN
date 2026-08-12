package com.simon.harmonichackernews.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicHistoryLedgerTest {
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
}
