package com.simon.harmonichackernews.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One writer per file for the process, including applications with multiple native windows. */
object ProcessPreferenceStores {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private class Entry {
        val mutex = Mutex()
        var store: DataStoreKeyValueStore? = null
    }
    private val stores = mutableMapOf<String, Entry>()

    suspend fun open(
        path: String,
        migrations: List<DataMigration<Preferences>> = emptyList(),
        onWriteFailure: (Throwable) -> Unit = {},
    ): DataStoreKeyValueStore {
        // Unrelated files can initialize concurrently. The per-file lock still guarantees one
        // DataStore writer, and a failed/cancelled open can be retried after its owner is closed.
        val entry = mutex.withLock { stores.getOrPut(path) { Entry() } }
        return entry.mutex.withLock {
            entry.store ?: DataStoreKeyValueStore.openFile(path, scope, migrations, onWriteFailure)
                .also { entry.store = it }
        }
    }
}
