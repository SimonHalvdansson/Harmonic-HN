package com.simon.harmonichackernews.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    fun unchangedAndNetUnchangedEditsDoNotPublishOrWrite() = runTest {
        val disk = TestDataStore()
        val store = DataStoreKeyValueStore.open(disk, backgroundScope)
        var notifications = 0
        backgroundScope.launch { store.changes.collect { notifications++ } }
        runCurrent()
        store.update {
            putInt("count", 7)
            putStringSet("set", setOf("a", "b"))
        }
        store.awaitPersisted()
        runCurrent()
        val initialNotifications = notifications
        val initialWrites = disk.writes
        repeat(10) {
            store.putInt("count", 7)
            store.putStringSet("set", setOf("b", "a"))
            store.remove("absent")
            store.update { putInt("count", 8); putInt("count", 7) }
            store.update { putString("absent", "temporary"); remove("absent") }
            store.update { }
            runCurrent()
        }
        store.awaitPersisted()
        assertEquals(initialNotifications, notifications)
        assertEquals(initialWrites, disk.writes)
        // An absent value differs from an explicitly stored default, and key types may change.
        store.putInt("zero", 0)
        store.putString("count", "seven")
        store.remove("set")
        store.awaitPersisted()
        assertTrue(store.contains("zero"))
        assertEquals("seven", store.getString("count"))
        assertFalse(store.contains("set"))
        store.clear()
        store.awaitPersisted()
        val afterClear = disk.writes
        store.clear()
        runCurrent()
        assertEquals(afterClear, disk.writes)
    }

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

    @Test
    fun exactSnapshotsRestoreTypesAndAbsenceWithoutTouchingOtherRecords() = runTest {
        val disk = TestDataStore()
        val store = DataStoreKeyValueStore.open(disk, backgroundScope)
        store.update {
            putBoolean("pref_boolean", false)
            putInt("pref_int", 17)
            putLong("pref_long", Long.MAX_VALUE)
            putFloat("pref_float", 0.375f)
            putString("pref_string", "blåbær, \"quoted\"\n")
            putStringSet("pref_set", setOf("", "a,b", "😀"))
            putStringSet("pref_empty", emptySet())
            putString("pref_ai_summary_api_key", "credential")
            putString("account_record", "original")
        }
        val original = store.snapshotValues().filterKeys(DevelopmentSettingsScope::isSetting)
        val document = PreferenceSnapshot.decode(PreferenceSnapshot(settings = original, nighttime = emptyMap()).encode())
        store.putString("pref_boolean", "wrong temporary type")
        store.remove("pref_long")
        store.putBoolean("pref_introduced", true)
        store.putString("pref_ai_summary_api_key", "updated credential")
        store.putString("account_record", "updated")
        store.restoreValues(document.settings, DevelopmentSettingsScope::isSetting)
        store.awaitPersisted()
        val reopened = DataStoreKeyValueStore.open(disk, backgroundScope)
        assertEquals(original, reopened.snapshotValues().filterKeys(DevelopmentSettingsScope::isSetting))
        assertFalse(reopened.contains("pref_introduced"))
        assertEquals(Long.MAX_VALUE, reopened.getLong("pref_long", 0))
        assertEquals("updated credential", reopened.getString("pref_ai_summary_api_key"))
        assertEquals("updated", reopened.getString("account_record"))
    }

    @Test
    fun invalidSnapshotDoesNotPublishPartialChanges() = runTest {
        val store = DataStoreKeyValueStore.open(TestDataStore(), backgroundScope)
        store.putInt("pref_original", 7)
        val bad = mapOf(
            "pref_original" to StoredPreferenceValue.from(8),
            "pref_bad" to StoredPreferenceValue("int", kotlinx.serialization.json.JsonPrimitive("oops")),
        )
        assertFailsWith<IllegalArgumentException> {
            store.restoreValues(bad, DevelopmentSettingsScope::isSetting)
        }
        assertEquals(7, store.getInt("pref_original", 0))
        assertFalse(store.contains("pref_bad"))
        assertFailsWith<IllegalArgumentException> {
            PreferenceSnapshot.decode(PreferenceSnapshot(settings = mapOf(
                "pref_ai_summary_api_key" to StoredPreferenceValue.from("secret"),
            ), nighttime = emptyMap()).encode())
        }
        assertFailsWith<IllegalArgumentException> {
            PreferenceSnapshot.decode(PreferenceSnapshot(version = 2, settings = emptyMap(), nighttime = emptyMap()).encode())
        }
    }

    @Test
    fun scopedRestoreRemovesAddedScheduleKeysButPreservesGlobalContent() = runTest {
        val store = DataStoreKeyValueStore.open(TestDataStore(), backgroundScope)
        store.putString(NighttimeScheduleKeys.FROM_HOUR, "21")
        val saved = store.snapshotValues().filterKeys(DevelopmentSettingsScope::isNighttime)
        store.putString(NighttimeScheduleKeys.FROM_HOUR, "23")
        store.putString(NighttimeScheduleKeys.TO_HOUR, "7")
        store.putString("history", "keep")
        store.restoreValues(saved, DevelopmentSettingsScope::isNighttime)
        store.awaitPersisted()
        assertEquals("21", store.getString(NighttimeScheduleKeys.FROM_HOUR))
        assertFalse(store.contains(NighttimeScheduleKeys.TO_HOUR))
        assertEquals("keep", store.getString("history"))
    }

    private class TestDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var beforeWrite: suspend () -> Unit = {}
        var writes = 0
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            beforeWrite()
            writes++
            return transform(data.value).also { data.value = it }
        }
    }
}
