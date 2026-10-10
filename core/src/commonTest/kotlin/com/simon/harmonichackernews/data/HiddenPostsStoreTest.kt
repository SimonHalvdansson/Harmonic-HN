package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.StoredHistoryKeys
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenPostsStoreTest {
    @Test
    fun concurrentWritesSurviveRecreationAndClearDoesNotTouchHistory() = runTest {
        val storage = TestKeyValueStore(mapOf(StoredHistoryKeys.HISTORIES to "99q123"))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val hidden = HiddenPostsStore(storage, dispatcher)
        val jobs = (1..100).map { id -> launch { hidden.hide(id) } }
        jobs.forEach { it.join() }
        assertEquals((1..100).toSet(), hidden.ids.value)
        assertFalse(hidden.hide(1))
        hidden.unhide(42)
        val restored = HiddenPostsStore(storage, dispatcher)
        restored.initialize()
        assertEquals((1..100).toSet() - 42, restored.ids.value)
        restored.clear()
        assertTrue(restored.ids.value.isEmpty())
        assertEquals("99q123", storage.getString(StoredHistoryKeys.HISTORIES))
        val cleared = HiddenPostsStore(storage, dispatcher)
        cleared.initialize()
        assertTrue(cleared.ids.value.isEmpty())
    }

    @Test
    fun doesNotInheritHistoryEvictionAndIgnoresInvalidIds() = runTest {
        val storage = TestKeyValueStore(mapOf(HiddenPostsStore.STORAGE_KEY to
            (1..10_001).joinToString(",") + ",0,-1,bad,1"))
        val hidden = HiddenPostsStore(storage, StandardTestDispatcher(testScheduler))
        hidden.initialize()
        hidden.hide(10_002)
        assertEquals((1..10_002).toSet(), hidden.ids.value)
        assertFalse(hidden.hide(-1))
    }
}
