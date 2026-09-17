package com.simon.harmonichackernews.settings

/** Supported periodic reply checks, starting at Android's minimum background-job interval. */
enum class ReplyNotificationFrequency(val minutes: Int, val label: String) {
    FIFTEEN_MINUTES(15, "Every 15 minutes"),
    THIRTY_MINUTES(30, "Every 30 minutes"),
    ONE_HOUR(60, "Every hour"),
    TWO_HOURS(120, "Every 2 hours"),
    SIX_HOURS(360, "Every 6 hours"),
    TWELVE_HOURS(720, "Every 12 hours"),
    ONE_DAY(1_440, "Once a day"),
    ;

    val intervalMillis: Long get() = minutes * 60L * 1_000L

    companion object {
        val DEFAULT = THIRTY_MINUTES

        fun fromMinutes(minutes: Int): ReplyNotificationFrequency =
            entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
