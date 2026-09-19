package com.simon.harmonichackernews.settings

enum class StoryListSelector(val storedValue: String) {
    DROPDOWN("dropdown"),
    CHIPS("chips");

    companion object {
        fun fromStored(value: String?): StoryListSelector =
            entries.firstOrNull { it.storedValue == value } ?: DROPDOWN
    }
}
