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
    private val stores = mutableMapOf<String, DataStoreKeyValueStore>()

    suspend fun open(
        path: String,
        migrations: List<DataMigration<Preferences>> = emptyList(),
        onWriteFailure: (Throwable) -> Unit = {},
    ): DataStoreKeyValueStore = mutex.withLock {
        stores.getOrPut(path) {
            DataStoreKeyValueStore.openFile(path, scope, migrations, onWriteFailure)
        }
    }
}
