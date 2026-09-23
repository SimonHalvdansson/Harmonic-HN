package com.simon.harmonichackernews.presentation

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

    val requestParameter: String
        get() = formatEpochDay(selectedMillis / MILLIS_PER_DAY)

    fun shift(days: Int): Boolean = select(selectedMillis + days * MILLIS_PER_DAY)

    fun select(millis: Long): Boolean {
        val next = startOfUtcDay(millis).coerceIn(earliestMillis, latestMillis)
        if (next == selectedMillis) return false
        selectedMillis = next
        return true
    }

    private fun startOfUtcDay(millis: Long): Long =
        (millis / MILLIS_PER_DAY) * MILLIS_PER_DAY

    private fun formatEpochDay(epochDay: Long): String {
        // Gregorian civil date conversion; keeps commonMain free of java.time/Calendar.
        val shifted = epochDay + 719_468L
        val era = if (shifted >= 0L) shifted / 146_097L else (shifted - 146_096L) / 146_097L
        val dayOfEra = shifted - era * 146_097L
        val yearOfEra = (
            dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L
        ) / 365L
        var year = yearOfEra + era * 400L
        val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
        val monthPiece = (5L * dayOfYear + 2L) / 153L
        val day = dayOfYear - (153L * monthPiece + 2L) / 5L + 1L
        val month = monthPiece + if (monthPiece < 10L) 3L else -9L
        if (month <= 2L) year++
        return year.toString().padStart(4, '0') + "-" +
            month.toString().padStart(2, '0') + "-" +
            day.toString().padStart(2, '0')
    }
}
