package com.simon.harmonichackernews.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.SharedPreferencesMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/** One DataStore per legacy store name, including all Android 3.1 keys and value types. */
class AndroidKeyValueStore private constructor(
    private val store: DataStoreKeyValueStore,
) : KeyValueStore by store {
    val changes: Flow<Unit> get() = store.changes
    suspend fun awaitPersisted() = store.awaitPersisted()

    companion object {
        fun global(context: Context): AndroidKeyValueStore = named(context, AppLaunchPreferenceKeys.STORE_NAME)
        fun defaults(context: Context): AndroidKeyValueStore = named(
            context, context.applicationContext.packageName + "_preferences",
        )

        fun named(context: Context, name: String): AndroidKeyValueStore {
            val appContext = context.applicationContext
            val path = appContext.filesDir.resolve("datastore/$name.preferences_pb").absolutePath
            // Legacy consumers need a complete migrated snapshot on their first synchronous read.
            return AndroidKeyValueStore(runBlocking(Dispatchers.IO) {
                ProcessPreferenceStores.open(
                    path,
                    migrations = listOf(legacyPreferenceMigration(appContext, name)),
                    onWriteFailure = { Log.e("HarmonicPreferences", "Preference write failed; retrying", it) },
                )
            })
        }
    }
}

/** Leave pre-encryption secrets for the Keystore adapter, even when its migration must retry. */
internal fun legacyPreferenceMigration(context: Context, name: String) = SharedPreferencesMigration(
    context = context,
    sharedPreferencesName = name,
    keysToMigrate = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.keys -
        if (name == context.packageName + "_preferences") setOf("pref_ai_summary_api_key") else emptySet(),
)
