package com.simon.harmonichackernews.utils

/** Locale-stable grouping used by Hacker News counts. */
object GroupedNumberFormatter {
    fun format(value: Int): String {
        val digits = value.toString()
        val signLength = if (digits.startsWith('-')) 1 else 0
        val digitCount = digits.length - signLength
        if (digitCount <= 3) return digits
        return buildString(digits.length + (digitCount - 1) / 3) {
            if (signLength == 1) append('-')
            digits.substring(signLength).forEachIndexed { index, character ->
                if (index > 0 && (digitCount - index) % 3 == 0) append(',')
                append(character)
            }
        }
    }
}
