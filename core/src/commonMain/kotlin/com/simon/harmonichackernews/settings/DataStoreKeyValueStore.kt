package com.simon.harmonichackernews.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import okio.Path.Companion.toPath

/**
 * A loaded DataStore snapshot for the synchronous reads used by rendering and existing repositories.
 * Edits publish atomically in memory and are serialized to disk by one writer. Opening waits for
 * DataStore (including Android's legacy migration); a failed read never becomes empty preferences.
 */
class DataStoreKeyValueStore private constructor(
    private val dataStore: DataStore<Preferences>,
    initial: Preferences,
    scope: CoroutineScope,
    private val onWriteFailure: (Throwable) -> Unit,
) : KeyValueStore {
    private data class Snapshot(val revision: Long, val preferences: Preferences)
    private data class Persistence(val revision: Long, val failure: Throwable? = null)
    private val snapshot = MutableStateFlow(Snapshot(0, initial))
    private val persistence = MutableStateFlow(Persistence(0))
    val changes: Flow<Unit> = snapshot.map { it.revision }.distinctUntilChanged().map { }
    val writeFailure: Flow<Throwable?> = persistence.map { it.failure }.distinctUntilChanged()

    private val writer: Job = scope.launch {
        snapshot.collect { pending ->
            if (pending.revision <= persistence.value.revision) return@collect
            while (true) {
                try {
                    dataStore.updateData { pending.preferences }
                    persistence.value = Persistence(pending.revision)
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    persistence.value = persistence.value.copy(failure = error)
                    onWriteFailure(error)
                    // Keep this write pending, preserving ordering and newer in-memory edits.
                    delay(1_000)
                }
            }
        }
    }

    /** Waits for every edit published before this call; storage failures remain visible to callers. */
    suspend fun awaitPersisted() {
        val revision = snapshot.value.revision
        val result = persistence.first { it.revision >= revision || it.failure != null }
        result.failure?.let { throw it }
    }

    override fun contains(key: String): Boolean = snapshot.value.preferences.contains(stringPreferencesKey(key))
    override fun keys(): Set<String> = snapshot.value.preferences.asMap().keys.mapTo(mutableSetOf()) { it.name }
    override fun getString(key: String, default: String?): String? =
        snapshot.value.preferences[stringPreferencesKey(key)] ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean =
        snapshot.value.preferences[booleanPreferencesKey(key)] ?: default
    override fun getInt(key: String, default: Int): Int =
        snapshot.value.preferences[intPreferencesKey(key)] ?: default
    override fun getLong(key: String, default: Long): Long =
        snapshot.value.preferences[longPreferencesKey(key)] ?: default
    override fun getFloat(key: String, default: Float): Float =
        snapshot.value.preferences[floatPreferencesKey(key)] ?: default
    override fun getStringSet(key: String): Set<String> =
        snapshot.value.preferences[stringSetPreferencesKey(key)]?.toSet() ?: emptySet()

    override fun clear() = edit(unchanged = { it.asMap().isEmpty() }) { clear() }
    override fun remove(key: String) = update { remove(key) }
    override fun putString(key: String, value: String?) = update { putString(key, value) }
    override fun putBoolean(key: String, value: Boolean) = update { putBoolean(key, value) }
    override fun putInt(key: String, value: Int) = update { putInt(key, value) }
    override fun putLong(key: String, value: Long) = update { putLong(key, value) }
    override fun putFloat(key: String, value: Float) = update { putFloat(key, value) }
    override fun putStringSet(key: String, value: Set<String>?) = update { putStringSet(key, value) }

    override fun update(block: KeyValueStore.Editor.() -> Unit) {
        // Evaluate callers once, before publishing anything. CAS retries only replay immutable edits.
        val operations = mutableMapOf<String, PreferenceEdit>()
        fun <T> set(key: Preferences.Key<T>, value: T?) {
            operations[key.name] = PreferenceEdit(
                unchanged = { it[key] == value },
                apply = { if (value == null) remove(key) else this[key] = value },
            )
        }
        block(object : KeyValueStore.Editor {
            override fun remove(key: String) = set(stringPreferencesKey(key), null)
            override fun putString(key: String, value: String?) {
                set(stringPreferencesKey(key), value)
            }
            override fun putBoolean(key: String, value: Boolean) = set(booleanPreferencesKey(key), value)
            override fun putInt(key: String, value: Int) = set(intPreferencesKey(key), value)
            override fun putLong(key: String, value: Long) = set(longPreferencesKey(key), value)
            override fun putFloat(key: String, value: Float) = set(floatPreferencesKey(key), value)
            override fun putStringSet(key: String, value: Set<String>?) {
                set(stringSetPreferencesKey(key), value?.toSet())
            }
        })
        edit(unchanged = { current -> operations.values.all { it.unchanged(current) } }) {
            operations.values.forEach { it.apply(this) }
        }
    }

    private data class PreferenceEdit(
        val unchanged: (Preferences) -> Boolean,
        val apply: MutablePreferences.() -> Unit,
    )

    private fun edit(unchanged: (Preferences) -> Boolean, block: MutablePreferences.() -> Unit) {
        check(writer.isActive) { "Preference writer is closed" }
        snapshot.update { current ->
            if (unchanged(current.preferences)) current else
                Snapshot(current.revision + 1, current.preferences.toMutablePreferences().apply(block).toPreferences())
        }
    }

    companion object {
        suspend fun open(
            dataStore: DataStore<Preferences>,
            scope: CoroutineScope,
            onWriteFailure: (Throwable) -> Unit = {},
        ): DataStoreKeyValueStore = DataStoreKeyValueStore(
            dataStore, dataStore.data.first(), scope, onWriteFailure,
        )

        suspend fun openFile(
            path: String,
            scope: CoroutineScope,
            migrations: List<DataMigration<Preferences>> = emptyList(),
            onWriteFailure: (Throwable) -> Unit = {},
        ): DataStoreKeyValueStore {
            val job = SupervisorJob(scope.coroutineContext[Job])
            val fileScope = CoroutineScope(scope.coroutineContext + job)
            return try {
                open(
                    PreferenceDataStoreFactory.createWithPath(
                        scope = fileScope,
                        migrations = migrations,
                        produceFile = { path.toPath() },
                    ),
                    fileScope,
                    onWriteFailure,
                )
            } catch (error: Throwable) {
                // Failed initialization must release DataStore's single-file ownership for retry.
                job.cancelAndJoin()
                throw error
            }
        }
    }
}
