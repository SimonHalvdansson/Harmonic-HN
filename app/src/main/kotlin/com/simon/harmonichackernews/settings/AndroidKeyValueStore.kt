package com.simon.harmonichackernews.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Android adapter that preserves the app's existing SharedPreferences storage. */
class AndroidKeyValueStore private constructor(
    private val preferences: SharedPreferences,
    private val queued: QueuedPreferenceWrites? = null,
) : KeyValueStore {
    val changes: Flow<Unit>
        get() = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                trySend(Unit)
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    override fun clear() {
        if (queued != null) queued.update(emptyMap(), clear = true)
        else preferences.edit { clear() }
    }

    override fun contains(key: String): Boolean =
        queued?.read<Any?>(key, null) { if (preferences.contains(key)) true else null } != null
            || (queued == null && preferences.contains(key))

    override fun keys(): Set<String> = queued?.keys() ?: preferences.all.keys

    override fun remove(key: String) {
        if (queued != null) queued.update(mapOf(key to null))
        else preferences.edit { remove(key) }
    }

    override fun getString(key: String, default: String?): String? =
        if (queued != null) queued.read(key, default) { preferences.getString(key, default) }
        else preferences.getString(key, default)

    override fun putString(key: String, value: String?) {
        if (queued != null) queued.update(mapOf(key to value))
        else preferences.edit { putString(key, value) }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (queued != null) queued.read(key, default) { preferences.getBoolean(key, default) }
        else preferences.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        if (queued != null) queued.update(mapOf(key to value))
        else preferences.edit { putBoolean(key, value) }
    }

    override fun getInt(key: String, default: Int): Int = queued?.read(key, default) { preferences.getInt(key, default) }
            ?: preferences.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        if (queued != null) queued.update(mapOf(key to value))
        else preferences.edit { putInt(key, value) }
    }

    override fun getLong(key: String, default: Long): Long = queued?.read(key, default) { preferences.getLong(key, default) }
            ?: preferences.getLong(key, default)

    override fun putLong(key: String, value: Long) {
        if (queued != null) queued.update(mapOf(key to value))
        else preferences.edit { putLong(key, value) }
    }

    override fun getFloat(key: String, default: Float): Float =
        if (queued != null) queued.read(key, default) { preferences.getFloat(key, default) }
        else preferences.getFloat(key, default)

    override fun putFloat(key: String, value: Float) {
        if (queued != null) queued.update(mapOf(key to value))
        else preferences.edit { putFloat(key, value) }
    }

    override fun getStringSet(key: String): Set<String> =
        if (queued != null) queued.read(key, emptySet()) {
            preferences.getStringSet(key, emptySet())?.toSet().orEmpty()
        } else preferences.getStringSet(key, emptySet())?.toSet().orEmpty()

    override fun putStringSet(key: String, value: Set<String>?) {
        if (queued != null) queued.update(mapOf(key to value?.toSet()))
        else preferences.edit { putStringSet(key, value?.toSet()) }
    }

    override fun update(block: KeyValueStore.Editor.() -> Unit) {
        if (queued != null) {
            val values = linkedMapOf<String, Any?>()
            block(object : KeyValueStore.Editor {
                override fun remove(key: String) { values[key] = null }
                override fun putString(key: String, value: String?) { values[key] = value }
                override fun putBoolean(key: String, value: Boolean) { values[key] = value }
                override fun putInt(key: String, value: Int) { values[key] = value }
                override fun putLong(key: String, value: Long) { values[key] = value }
                override fun putFloat(key: String, value: Float) { values[key] = value }
                override fun putStringSet(key: String, value: Set<String>?) { values[key] = value?.toSet() }
            })
            queued.update(values)
            return
        }
        preferences.edit {
            val sharedPreferencesEditor = this
            block(object : KeyValueStore.Editor {
                override fun remove(key: String) {
                    sharedPreferencesEditor.remove(key)
                }

                override fun putString(key: String, value: String?) {
                    sharedPreferencesEditor.putString(key, value)
                }

                override fun putBoolean(key: String, value: Boolean) {
                    sharedPreferencesEditor.putBoolean(key, value)
                }

                override fun putInt(key: String, value: Int) {
                    sharedPreferencesEditor.putInt(key, value)
                }

                override fun putLong(key: String, value: Long) {
                    sharedPreferencesEditor.putLong(key, value)
                }

                override fun putFloat(key: String, value: Float) {
                    sharedPreferencesEditor.putFloat(key, value)
                }

                override fun putStringSet(key: String, value: Set<String>?) {
                    sharedPreferencesEditor.putStringSet(key, value?.toSet())
                }
            })
        }
    }

    companion object {
        private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val contentStores = mutableMapOf<String, AndroidKeyValueStore>()

        fun global(context: Context): AndroidKeyValueStore = named(context, AppLaunchPreferenceKeys.STORE_NAME)

        fun defaults(context: Context): AndroidKeyValueStore = AndroidKeyValueStore(
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext),
        )

        fun named(context: Context, name: String): AndroidKeyValueStore {
            val key = context.applicationContext.packageName + ":" + name
            return synchronized(contentStores) {
                contentStores.getOrPut(key) {
                    val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                    AndroidKeyValueStore(preferences, QueuedPreferenceWrites(preferences, persistenceScope))
                }
            }
        }
    }
}
