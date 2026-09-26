package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoredHistoryStoreTest {
    @Test
    fun membershipTracksInitializationEvictionRemovalAndClearAtomically() = runTest {
        val history = StoredHistoryStore(
            TestKeyValueStore(mapOf(StoredHistoryKeys.HISTORIES to
                (1..10_000).joinToString("-") { "${it}q$it" })),
            storageDispatcher = StandardTestDispatcher(testScheduler),
        )
        try {
            runCurrent()
            val original = history.historyState.value
            assertTrue(history.contains(1))
            assertTrue(history.contains(10_000))
            history.recordHistory(10_001, 10_001)
            assertFalse(history.contains(1))
            assertTrue(history.contains(10_001))
            assertTrue(1 in original.ids)
            assertFalse(10_001 in original.ids)
            history.removeHistory(10_000)
            assertFalse(history.contains(10_000))
            assertEquals(history.load().map { it.id }.toSet(), history.historyState.value.ids)
            history.clearHistory()
            assertFalse(history.contains(10_001))
            assertTrue(history.historyState.value.ids.isEmpty())
        } finally {
            history.close()
        }
    }

    @Test
    fun initializationIsDeferredAndPublishedFromStorageDispatcher() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val history = StoredHistoryStore(
            store = TestKeyValueStore(mapOf(StoredHistoryKeys.HISTORIES to "1q100-2q200")),
            storageDispatcher = dispatcher,
        )

        assertEquals(emptyList(), history.load())
        runCurrent()
        assertEquals(listOf(2, 1), history.load().map { it.id })
        history.close()
    }

    @Test
    fun concurrentSuspendRecordsDoNotLoseHistoryEntries() = runTest {
        val history = StoredHistoryStore(TestKeyValueStore())

        coroutineScope {
            repeat(100) { index ->
                launch(Dispatchers.Default) {
                    history.recordHistory(index + 1, index.toLong())
                }
            }
        }

        assertEquals(100, history.size)
        assertEquals((1..100).toSet(), history.load().map { it.id }.toSet())
        history.close()
    }
}
