package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.utils.TimeWindowPolicy

object NighttimeScheduleKeys {
    const val FROM_HOUR = "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_HOUR"
    const val FROM_MINUTE = "com.simon.harmonichackernews.KEY_NIGHTTIME_FROM_MINUTE"
    const val TO_HOUR = "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_HOUR"
    const val TO_MINUTE = "com.simon.harmonichackernews.KEY_NIGHTTIME_TO_MINUTE"
}

data class NighttimeSchedule(
    val fromHour: Int = 21,
    val fromMinute: Int = 0,
    val toHour: Int = 6,
    val toMinute: Int = 0,
) {
    fun toIntArray(): IntArray = intArrayOf(fromHour, fromMinute, toHour, toMinute)

    val startMinutes: Int get() = fromHour * 60 + fromMinute
    val endMinutes: Int get() = toHour * 60 + toMinute

    fun containsMinutes(minutesFromMidnight: Int): Boolean = TimeWindowPolicy.containsMinutes(
        startMinutes.toLong(),
        endMinutes.toLong(),
        minutesFromMidnight.coerceIn(0, 24 * 60 - 1).toLong(),
    )
}

data class ThemeSelection(
    val theme: String,
    val dark: Boolean,
)

/** Shared automatic/nighttime theme selection; hosts only supply system mode and local time. */
object ThemeSelectionPolicy {
    fun select(
        configuredTheme: String?,
        nighttimeTheme: String?,
        useSpecialNighttimeTheme: Boolean,
        schedule: NighttimeSchedule,
        currentMinutesFromMidnight: Int,
        systemDark: Boolean,
    ): ThemeSelection {
        val base = configuredTheme ?: ThemePreferences.DEFAULT
        val selected = if (
            useSpecialNighttimeTheme && schedule.containsMinutes(currentMinutesFromMidnight)
        ) {
            ThemePreferences.selectableNighttimeTheme(nighttimeTheme)
        } else {
            base
        }
        return ThemeSelection(
            theme = selected,
            dark = if (ThemePreferences.isAutomatic(selected)) systemDark
            else ThemePreferences.isDark(selected),
        )
    }

    fun formatSchedule(
        schedule: NighttimeSchedule,
        use24HourClock: Boolean,
        amLabel: String = "AM",
        pmLabel: String = "PM",
    ): String = listOf(
        schedule.fromHour to schedule.fromMinute,
        schedule.toHour to schedule.toMinute,
    ).joinToString(" - ") { (hour, minute) ->
        if (use24HourClock) {
            hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
        } else {
            val suffix = if (hour < 12) amLabel else pmLabel
            val displayHour = when (val normalized = hour % 12) {
                0 -> 12
                else -> normalized
            }
            "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
        }
    }
}

/** Portable persistence and malformed-value fallback for the automatic nighttime theme. */
class NighttimeScheduleStore(
    private val store: KeyValueStore,
) {
    fun load(): NighttimeSchedule = NighttimeSchedule(
        fromHour = readInt(NighttimeScheduleKeys.FROM_HOUR, 21),
        fromMinute = readInt(NighttimeScheduleKeys.FROM_MINUTE, 0),
        toHour = readInt(NighttimeScheduleKeys.TO_HOUR, 6),
        toMinute = readInt(NighttimeScheduleKeys.TO_MINUTE, 0),
    )

    fun save(schedule: NighttimeSchedule) {
        store.putString(NighttimeScheduleKeys.FROM_HOUR, schedule.fromHour.toString())
        store.putString(NighttimeScheduleKeys.FROM_MINUTE, schedule.fromMinute.toString())
        store.putString(NighttimeScheduleKeys.TO_HOUR, schedule.toHour.toString())
        store.putString(NighttimeScheduleKeys.TO_MINUTE, schedule.toMinute.toString())
    }

    private fun readInt(key: String, default: Int): Int =
        store.getString(key, default.toString())?.toIntOrNull() ?: default
}

/** Application-scoped appearance policy and persistence shared by every host UI. */
class AppearanceRuntime(
    private val settings: KeyValueStore,
    private val scheduleStore: NighttimeScheduleStore,
    private val launchState: AppLaunchStateStore,
    private val currentMinutesFromMidnight: () -> Int,
    private val systemDark: () -> Boolean,
) {
    val schedule: NighttimeSchedule get() = scheduleStore.load()

    fun selection(): ThemeSelection = ThemeSelectionPolicy.select(
        configuredTheme = settings.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT),
        nighttimeTheme = settings.getString(
            ThemePreferences.NIGHTTIME_KEY,
            ThemePreferences.DEFAULT_NIGHTTIME,
        ),
        useSpecialNighttimeTheme = settings.getBoolean(
            UserPreferenceKeys.SPECIAL_NIGHTTIME,
            false,
        ),
        schedule = schedule,
        currentMinutesFromMidnight = currentMinutesFromMidnight(),
        systemDark = systemDark(),
    )

    fun saveSchedule(schedule: NighttimeSchedule) = scheduleStore.save(schedule)

    fun markWelcomeShown() = launchState.markWelcomeDialogShown()
}
