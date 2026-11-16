package com.simon.harmonichackernews.utils

internal data class CivilDate(val year: Long, val month: Int, val day: Int)

/** Converts an epoch day to its Gregorian calendar date. */
internal fun civilDateFromEpochDay(epochDay: Long): CivilDate {
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
    return CivilDate(year, month.toInt(), day.toInt())
}
