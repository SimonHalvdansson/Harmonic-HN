package com.simon.harmonichackernews.widget

import android.content.Context
import androidx.preference.PreferenceManager

object WidgetConfigUiPreference {
    const val KEY = "pref_widget_config_implementation"
    const val VIEWS = "views"
    const val COMPOSE = "compose"

    @JvmStatic
    fun selected(context: Context): String = PreferenceManager
        .getDefaultSharedPreferences(context)
        .getString(KEY, VIEWS)
        .takeIf { it == VIEWS || it == COMPOSE }
        ?: VIEWS

    @JvmStatic
    fun shouldUseCompose(context: Context): Boolean = selected(context) == COMPOSE
}
