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
) : UserSettings by delegate {
    constructor(context: Context) : this(createDelegate(context.applicationContext))

    private companion object {
        fun createDelegate(context: Context): UserSettings {
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
