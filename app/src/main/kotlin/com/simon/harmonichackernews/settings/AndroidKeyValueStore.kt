package com.simon.harmonichackernews.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Android adapter that preserves the app's existing SharedPreferences storage. */
class AndroidKeyValueStore private constructor(
    private val preferences: SharedPreferences,
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
        preferences.edit { clear() }
    }

    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun keys(): Set<String> = preferences.all.keys

    override fun remove(key: String) {
        preferences.edit { remove(key) }
    }

    override fun getString(key: String, default: String?): String? =
        preferences.getString(key, default)

    override fun putString(key: String, value: String?) {
        preferences.edit { putString(key, value) }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        preferences.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }

    override fun getInt(key: String, default: Int): Int = preferences.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        preferences.edit { putInt(key, value) }
    }

    override fun getLong(key: String, default: Long): Long = preferences.getLong(key, default)

    override fun putLong(key: String, value: Long) {
        preferences.edit { putLong(key, value) }
    }

    override fun getFloat(key: String, default: Float): Float =
        preferences.getFloat(key, default)

    override fun putFloat(key: String, value: Float) {
        preferences.edit { putFloat(key, value) }
    }

    override fun getStringSet(key: String): Set<String> =
        preferences.getStringSet(key, emptySet())?.toSet().orEmpty()

    override fun putStringSet(key: String, value: Set<String>?) {
        preferences.edit { putStringSet(key, value?.toSet()) }
    }

    override fun update(block: KeyValueStore.Editor.() -> Unit) {
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
        fun global(context: Context): AndroidKeyValueStore = AndroidKeyValueStore(
            context.applicationContext.getSharedPreferences(
                AppLaunchPreferenceKeys.STORE_NAME,
                Context.MODE_PRIVATE,
            ),
        )

        fun defaults(context: Context): AndroidKeyValueStore = AndroidKeyValueStore(
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext),
        )

        fun named(context: Context, name: String): AndroidKeyValueStore = AndroidKeyValueStore(
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE),
        )
    }
}
