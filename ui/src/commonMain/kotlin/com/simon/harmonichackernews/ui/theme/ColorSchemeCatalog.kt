package com.simon.harmonichackernews.ui.theme

import com.simon.harmonichackernews.settings.ColorSchemePreferences

data class ColorSchemeOption(val value: String, val label: String)

/** One list for all appearances; every option resolves to a complete light and dark scheme. */
object ColorSchemeCatalog {
    val options = ColorSchemePreferences.values.map { value ->
        ColorSchemeOption(value, if (value == ColorSchemePreferences.HACKER_NEWS) "Hacker News"
            else value.replaceFirstChar { it.uppercaseChar() })
    }

    fun label(value: String) = options.first { it.value == ColorSchemePreferences.sanitize(value) }.label
}
