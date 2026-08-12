package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.utils.GroupedNumberFormatter
import com.simon.harmonichackernews.utils.HtmlTextUtils

data class UserProfilePresentation(
    val id: String,
    val meta: String,
    val about: String,
    val hasSubmissions: Boolean,
)

/** Converts the network DTO to locale-independent profile content for every UI host. */
object UserProfilePresenter {
    fun present(user: HackerNewsUserDto, monthNames: List<String>): UserProfilePresentation {
        require(monthNames.size >= 12) { "Twelve localized month names are required" }
        val date = civilDateFromEpochSeconds(user.created)
        return UserProfilePresentation(
            id = user.id,
            meta = "${GroupedNumberFormatter.format(user.karma)} karma since " +
                "${monthNames[date.month - 1]} ${date.day}, ${date.year}",
            about = HtmlTextUtils.plainText(user.about).trim(),
            hasSubmissions = user.submitted.isNotEmpty(),
        )
    }

    private fun civilDateFromEpochSeconds(epochSeconds: Long): CivilDate {
        val epochDay = floorDiv(epochSeconds, SECONDS_PER_DAY)
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
        return CivilDate(year.toInt(), month.toInt(), day.toInt())
    }

    private fun floorDiv(value: Long, divisor: Long): Long {
        val quotient = value / divisor
        return if (value < 0L && value % divisor != 0L) quotient - 1L else quotient
    }

    private data class CivilDate(val year: Int, val month: Int, val day: Int)
    private const val SECONDS_PER_DAY = 86_400L
}
