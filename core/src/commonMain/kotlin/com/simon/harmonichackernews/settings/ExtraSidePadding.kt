package com.simon.harmonichackernews.settings

/** Scales the host's tablet gutter, preserving zero padding in other configurations. */
enum class ExtraSidePadding(val storedValue: String, val fraction: Float) {
    None("none", 0f),
    Small("small", 0.5f),
    Standard("standard", 1f);

    companion object {
        fun fromStored(value: String?): ExtraSidePadding =
            entries.firstOrNull { it.storedValue == value } ?: Standard
    }
}
