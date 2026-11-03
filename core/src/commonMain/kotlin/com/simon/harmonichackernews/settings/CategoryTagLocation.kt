package com.simon.harmonichackernews.settings

/** Placement of category labels in story cards; the title placement preserves the existing layout. */
enum class CategoryTagLocation(val storedValue: String, val label: String) {
    TITLE("title", "Under title"),
    METADATA("metadata", "Metadata");

    companion object {
        fun fromStored(value: String?): CategoryTagLocation = entries.firstOrNull { it.storedValue == value } ?: TITLE
    }
}
