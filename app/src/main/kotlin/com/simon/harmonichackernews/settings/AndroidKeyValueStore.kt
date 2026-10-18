package com.simon.harmonichackernews.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.SharedPreferencesMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** One DataStore per legacy store name, including all Android 3.1 keys and value types. */
class AndroidKeyValueStore private constructor(
    private val context: Context,
    private val name: String,
    private val path: String,
) : KeyValueStore {
    @Volatile private var loaded: DataStoreKeyValueStore? = null
    private val store: DataStoreKeyValueStore
        get() = loaded ?: runBlocking { load() }

    private suspend fun load(): DataStoreKeyValueStore = loaded ?: withContext(Dispatchers.IO) {
        loaded ?: ProcessPreferenceStores.open(
            path,
            migrations = listOf(legacyPreferenceMigration(context, name)),
            onWriteFailure = { Log.e("HarmonicPreferences", "Preference write failed; retrying", it) },
        ).also { loaded = it }
    }

    /** Load migrated values before synchronous consumers render; never expose temporary defaults. */
    suspend fun awaitLoaded() { load() }
    val changes: Flow<Unit> = flow { emitAll(load().changes) }
    suspend fun awaitPersisted() = load().awaitPersisted()

    override fun contains(key: String) = store.contains(key)
    override fun keys() = store.keys()
    override fun clear() = store.clear()
    override fun remove(key: String) = store.remove(key)
    override fun getString(key: String, default: String?) = store.getString(key, default)
    override fun putString(key: String, value: String?) = store.putString(key, value)
    override fun getBoolean(key: String, default: Boolean) = store.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = store.putBoolean(key, value)
    override fun getInt(key: String, default: Int) = store.getInt(key, default)
    override fun putInt(key: String, value: Int) = store.putInt(key, value)
    override fun getLong(key: String, default: Long) = store.getLong(key, default)
    override fun putLong(key: String, value: Long) = store.putLong(key, value)
    override fun getFloat(key: String, default: Float) = store.getFloat(key, default)
    override fun putFloat(key: String, value: Float) = store.putFloat(key, value)
    override fun getStringSet(key: String) = store.getStringSet(key)
    override fun putStringSet(key: String, value: Set<String>?) = store.putStringSet(key, value)
    override fun update(block: KeyValueStore.Editor.() -> Unit) = store.update(block)

    companion object {
        private val stores = ConcurrentHashMap<String, AndroidKeyValueStore>()
        fun global(context: Context): AndroidKeyValueStore = named(context, AppLaunchPreferenceKeys.STORE_NAME)
        fun defaults(context: Context): AndroidKeyValueStore = named(
            context, context.applicationContext.packageName + "_preferences",
        )

        fun named(context: Context, name: String): AndroidKeyValueStore {
            val appContext = context.applicationContext
            val path = appContext.filesDir.resolve("datastore/$name.preferences_pb").absolutePath
            // Merely constructing the graph must not open widget/cache files. Application startup
            // preloads common stores; synchronous consumers retain their existing contract.
            return stores.getOrPut(path) { AndroidKeyValueStore(appContext, name, path) }
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
