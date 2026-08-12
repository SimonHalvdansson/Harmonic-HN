package com.simon.harmonichackernews.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.utils.ThemeUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Android persistence adapter for the shared typed settings implementation. */
class AndroidUserSettings private constructor(
    delegate: UserSettings,
    editor: StoredSettingsMutator,
) : UserSettings by delegate {
    val repository = AppSettingsRepository(delegate, editor)

    constructor(context: Context) : this(
        createDelegate(context.applicationContext),
        StoredSettingsMutator(AndroidKeyValueStore.defaults(context.applicationContext)),
    )

    companion object {
        @Volatile
        private var sharedInstance: AndroidUserSettings? = null

        fun get(context: Context): AndroidUserSettings = sharedInstance ?: synchronized(this) {
            sharedInstance ?: AndroidUserSettings(context.applicationContext).also {
                sharedInstance = it
            }
        }

        private fun createDelegate(context: Context): UserSettings {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val changes = callbackFlow {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    trySend(Unit)
                }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            return StoredUserSettings(
                store = AndroidKeyValueStore.defaults(context),
                changes = changes,
                theme = { ThemeUtils.getPreferredTheme(context) },
            )
        }
    }
}
