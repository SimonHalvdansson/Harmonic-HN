package com.simon.harmonichackernews.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreKeyValueStoreTest {
    @Test
    fun editsStayVisibleAndOrderedWhileDiskIsBlocked() = runTest {
        val disk = TestDataStore()
        val gate = CompletableDeferred<Unit>()
        disk.beforeWrite = { gate.await() }
        val store = DataStoreKeyValueStore.open(disk, backgroundScope)
        store.putString("old", "value")
        runCurrent()
        store.remove("old")
        store.putInt("count", 1)
        store.clear()
        store.update { putInt("count", 2); putBoolean("ready", true) }
        assertNull(store.getString("old"))
        assertEquals(setOf("count", "ready"), store.keys())
        assertEquals(2, store.getInt("count", 0))
        gate.complete(Unit)
        store.awaitPersisted()
        val reopened = DataStoreKeyValueStore.open(disk, backgroundScope)
        assertEquals(2, reopened.getInt("count", 0))
        assertTrue(reopened.getBoolean("ready", false))
        assertFalse(reopened.contains("old"))
    }

    @Test
    fun transactionFailureAndCallerMutationCannotPublishPartialValues() = runTest {
        val disk = TestDataStore()
        val store = DataStoreKeyValueStore.open(disk, backgroundScope)
        store.putInt("count", 7)
        assertFailsWith<IllegalStateException> {
            store.update { putInt("count", 8); putString("partial", "bad"); error("abort") }
        }
        val values = mutableSetOf("a,b", "blåbær", "")
        store.putStringSet("set", values)
        values.clear()
        store.awaitPersisted()
        assertEquals(7, store.getInt("count", 0))
        assertFalse(store.contains("partial"))
        assertEquals(setOf("a,b", "blåbær", ""), store.getStringSet("set"))
        assertEquals(7, disk.data.value[intPreferencesKey("count")])
    }

    @Test
    fun failedWriteIsReportedAndRetriedWithoutLosingLaterEdits() = runTest {
        val disk = TestDataStore()
        var fail = true
        disk.beforeWrite = { if (fail) error("disk unavailable") }
        val store = DataStoreKeyValueStore.open(disk, backgroundScope)
        store.putInt("count", 1)
        runCurrent()
        assertFailsWith<IllegalStateException> { store.awaitPersisted() }
        store.putInt("count", 2)
        assertEquals(2, store.getInt("count", 0))
        fail = false
        advanceTimeBy(1_001)
        runCurrent()
        store.awaitPersisted()
        assertEquals(2, disk.data.value[intPreferencesKey("count")])
    }

    private class TestDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var beforeWrite: suspend () -> Unit = {}
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            beforeWrite()
            return transform(data.value).also { data.value = it }
        }
    }
}
