package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoredHistoryStoreTest {
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
    }
}
