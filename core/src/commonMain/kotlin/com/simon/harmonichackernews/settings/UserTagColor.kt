package com.simon.harmonichackernews.settings

/** Stable, portable tag colors; Theme follows the current Material palette. */
enum class UserTagColor(val storageValue: String, val label: String) {
    Theme("theme", "Theme"),
    Red("red", "Red"),
    Orange("orange", "Orange"),
    Green("green", "Green"),
    Blue("blue", "Blue"),
    Purple("purple", "Purple");

    companion object {
        fun fromStorage(value: String?): UserTagColor =
            entries.firstOrNull { it.storageValue == value } ?: Theme
    }
}
