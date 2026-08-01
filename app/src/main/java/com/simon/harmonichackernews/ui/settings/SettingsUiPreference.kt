package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.ComposeSettingsActivity
import com.simon.harmonichackernews.SettingsActivity

object SettingsUiPreference {
    const val KEY = "pref_settings_implementation"
    const val VIEWS = "views"
    const val COMPOSE = "compose"

    @JvmStatic
    fun selected(context: Context): String = PreferenceManager
        .getDefaultSharedPreferences(context)
        .getString(KEY, COMPOSE)
        .takeIf { it == VIEWS || it == COMPOSE }
        ?: COMPOSE

    @JvmStatic
    fun shouldUseCompose(context: Context): Boolean = selected(context) == COMPOSE

    @JvmStatic
    fun createIntent(context: Context): Intent = if (shouldUseCompose(context)) {
        Intent(context, ComposeSettingsActivity::class.java)
    } else {
        Intent(context, SettingsActivity::class.java)
    }

    @JvmStatic
    fun createAiSummaryIntent(context: Context): Intent = if (shouldUseCompose(context)) {
        ComposeSettingsActivity.createAiSummaryIntent(context)
    } else {
        SettingsActivity.createAiSummaryIntent(context)
    }
}
