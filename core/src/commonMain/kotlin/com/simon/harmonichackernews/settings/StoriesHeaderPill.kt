package com.simon.harmonichackernews.settings

/** The Material container palette used by the frontpage header's rounded surface. */
enum class StoriesHeaderPill(val storedValue: String, val label: String) {
    OFF("off", "Off"),
    PRIMARY("primary", "Primary"),
    SECONDARY("secondary", "Secondary"),
    TERTIARY("tertiary", "Tertiary");

    companion object {
        fun fromStored(value: String?): StoriesHeaderPill =
            entries.firstOrNull { it.storedValue == value } ?: OFF
    }
}
