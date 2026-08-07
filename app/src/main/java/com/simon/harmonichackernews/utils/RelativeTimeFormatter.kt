package com.simon.harmonichackernews.utils

/** Pure relative-time formatting that can move to commonMain without Android dependencies. */
internal object RelativeTimeFormatter {
    private const val SECOND_MILLIS = 1_000L
    private const val MINUTE_MILLIS = 60 * SECOND_MILLIS
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
    private const val YEAR_MILLIS = 365 * DAY_MILLIS

    fun format(time: Long, nowMillis: Long): String {
        val timestampMillis = if (time < 1_000_000_000_000L) time * SECOND_MILLIS else time
        if (timestampMillis > nowMillis || timestampMillis <= 0) {
            return "?"
        }

        val elapsed = nowMillis - timestampMillis
        return when {
            elapsed < MINUTE_MILLIS -> "just now"
            elapsed < 2 * MINUTE_MILLIS -> "1m"
            elapsed < 50 * MINUTE_MILLIS -> "${elapsed / MINUTE_MILLIS}m"
            elapsed < 120 * MINUTE_MILLIS -> "1h"
            elapsed < 24 * HOUR_MILLIS -> "${elapsed / HOUR_MILLIS}h"
            elapsed < 48 * HOUR_MILLIS -> "1d"
            elapsed < 365 * DAY_MILLIS -> "${elapsed / DAY_MILLIS}d"
            elapsed < 2 * YEAR_MILLIS -> "1y"
            else -> "${elapsed / YEAR_MILLIS}y"
        }
    }
}
