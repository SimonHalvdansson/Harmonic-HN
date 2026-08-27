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

@OptIn(ExperimentalCoroutinesApi::class)
class StoredHistoryStoreTest {
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
