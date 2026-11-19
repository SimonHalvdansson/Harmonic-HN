package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.utils.civilDateFromEpochDay

private const val MILLIS_PER_DAY = 86_400_000L

private const val EARLIEST_FRONT_PAGE_EPOCH_DAY = 13_563L // 2007-02-19 UTC

/** Portable UTC-day state used by the historic front-page feed and its date picker. */
class FrontPageDayState(
    restoredMillis: Long,
    nowMillis: Long,
) {
    val earliestMillis: Long = EARLIEST_FRONT_PAGE_EPOCH_DAY * MILLIS_PER_DAY
    val latestMillis: Long = ((nowMillis / MILLIS_PER_DAY) - 1L)
        .coerceAtLeast(EARLIEST_FRONT_PAGE_EPOCH_DAY) * MILLIS_PER_DAY

    var selectedMillis: Long = restoredMillis
        .takeIf { it >= 0L }
        ?.let(::startOfUtcDay)
        ?.coerceIn(earliestMillis, latestMillis)
        ?: latestMillis
        private set

    // Included in every Stories snapshot, even when the historic feed is not selected.
    var requestParameter: String = formatEpochDay(selectedMillis / MILLIS_PER_DAY)
        private set

    fun shift(days: Int): Boolean = select(selectedMillis + days * MILLIS_PER_DAY)

    fun select(millis: Long): Boolean {
        val next = startOfUtcDay(millis).coerceIn(earliestMillis, latestMillis)
        if (next == selectedMillis) return false
        selectedMillis = next
        requestParameter = formatEpochDay(next / MILLIS_PER_DAY)
        return true
    }

    private fun startOfUtcDay(millis: Long): Long =
        (millis / MILLIS_PER_DAY) * MILLIS_PER_DAY

    private fun formatEpochDay(epochDay: Long): String {
        val date = civilDateFromEpochDay(epochDay)
        return date.year.toString().padStart(4, '0') + "-" +
            date.month.toString().padStart(2, '0') + "-" +
            date.day.toString().padStart(2, '0')
    }
}
