package com.simon.harmonichackernews.ui.editor

import android.content.Context
import androidx.preference.PreferenceManager

object ComposeEditorPreference {
    const val KEY = "pref_compose_editor_implementation"
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
