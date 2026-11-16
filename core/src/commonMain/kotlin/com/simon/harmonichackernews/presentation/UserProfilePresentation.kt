package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.utils.GroupedNumberFormatter
import com.simon.harmonichackernews.utils.civilDateFromEpochDay

data class UserProfilePresentation(
    val id: String,
    val meta: String,
    val about: String,
    val hasSubmissions: Boolean,
)

/** Converts the network DTO to locale-independent profile content for every UI host. */
object UserProfilePresenter {
    fun present(user: HackerNewsUserDto, monthNames: List<String>): UserProfilePresentation {
        return present(UserProfileData.from(user), monthNames)
    }

    fun present(user: UserProfileData, monthNames: List<String>): UserProfilePresentation {
        require(monthNames.size >= 12) { "Twelve localized month names are required" }
        val date = civilDateFromEpochDay(floorDiv(user.created, SECONDS_PER_DAY))
        return UserProfilePresentation(
            id = user.id,
            meta = "${GroupedNumberFormatter.format(user.karma)} karma since " +
                "${monthNames[date.month - 1]} ${date.day}, ${date.year.toInt()}",
            // Keep the API's HTML so shared UI hosts can render links and formatting.
            about = user.about.trim(),
            hasSubmissions = user.hasSubmissions,
        )
    }

    private fun floorDiv(value: Long, divisor: Long): Long {
        val quotient = value / divisor
        return if (value < 0L && value % divisor != 0L) quotient - 1L else quotient
    }

    private const val SECONDS_PER_DAY = 86_400L
}
