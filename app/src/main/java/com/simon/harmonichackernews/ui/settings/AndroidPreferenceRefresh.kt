package com.simon.harmonichackernews.ui.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

/** Android preference listener adapter used until settings screens observe a common repository. */
@Composable
fun rememberPreferenceRefresh(): Int {
    val context = LocalContext.current
    val preferences = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return refresh
}
