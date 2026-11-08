package com.simon.harmonichackernews.settings

/** Percentage of the existing vertical padding; horizontal layout and type stay unchanged. */
object RowSpacingPreferences {
    const val DEFAULT = 100
    const val MIN = 25
    const val MAX = 175
    const val STEP = 25

    fun sanitize(value: Int): Int = value.coerceIn(MIN, MAX)
    fun scale(value: Int): Float = sanitize(value) / 100f
}
