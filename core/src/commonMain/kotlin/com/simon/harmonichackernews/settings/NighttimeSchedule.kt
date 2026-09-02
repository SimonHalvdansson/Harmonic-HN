package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.utils.TimeWindowPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    val accentPreset: String = ThemePreferences.ACCENT_DEFAULT,
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
        followSystem: Boolean = ThemePreferences.isAutomatic(configuredTheme),
        manualDark: Boolean = ThemePreferences.isDark(configuredTheme),
        lightTheme: String? = null,
        darkTheme: String? = null,
        accentPreset: String = ThemePreferences.ACCENT_DEFAULT,
    ): ThemeSelection {
        val base = configuredTheme ?: ThemePreferences.DEFAULT
        val selectedLight = ThemePreferences.selectableLightTheme(
            lightTheme ?: ThemePreferences.pairedLightTheme(base),
        )
        val selectedDark = ThemePreferences.selectableDarkTheme(
            darkTheme ?: ThemePreferences.pairedDarkTheme(base),
        )
        val selected = if (
            useSpecialNighttimeTheme && schedule.containsMinutes(currentMinutesFromMidnight)
        ) {
            ThemePreferences.selectableNighttimeTheme(nighttimeTheme)
        } else {
            when {
                followSystem && systemDark -> selectedDark
                followSystem -> selectedLight
                manualDark -> selectedDark
                else -> selectedLight
            }
        }
        return ThemeSelection(
            theme = selected,
            dark = ThemePreferences.isDark(selected),
            accentPreset = ThemePreferences.sanitizeAccent(accentPreset),
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
    settingsChanges: Flow<Unit>,
    appearanceChanges: Flow<Unit>,
    private val currentMinutesFromMidnight: () -> Int,
    private val systemDark: () -> Boolean,
) {
    private val manualRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val schedule: NighttimeSchedule get() = scheduleStore.load()

    /** Current-value-first theme selections derived directly from persistent appearance changes. */
    val selections: Flow<ThemeSelection> = channelFlow {
        send(selection())
        launch {
            settingsChanges.collect { send(selection()) }
        }
        launch {
            appearanceChanges.collect { send(selection()) }
        }
        launch {
            manualRefreshes.collect { send(selection()) }
        }
    }.distinctUntilChanged()

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
        followSystem = if (settings.contains(ThemePreferences.FOLLOW_SYSTEM_KEY)) {
            settings.getBoolean(ThemePreferences.FOLLOW_SYSTEM_KEY, true)
        } else {
            ThemePreferences.isAutomatic(
                settings.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT),
            )
        },
        manualDark = if (settings.contains(ThemePreferences.MANUAL_DARK_KEY)) {
            settings.getBoolean(ThemePreferences.MANUAL_DARK_KEY, false)
        } else {
            ThemePreferences.isDark(
                settings.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT),
            )
        },
        lightTheme = settings.getString(ThemePreferences.LIGHT_KEY),
        darkTheme = settings.getString(ThemePreferences.DARK_KEY),
        accentPreset = settings.getString(
            ThemePreferences.ACCENT_KEY,
            ThemePreferences.ACCENT_DEFAULT,
        ) ?: ThemePreferences.ACCENT_DEFAULT,
    )

    fun saveSchedule(schedule: NighttimeSchedule) {
        scheduleStore.save(schedule)
        refreshSelection()
    }

    /** Re-evaluates time- or platform-derived appearance inputs without changing preferences. */
    fun refreshSelection() {
        manualRefreshes.tryEmit(Unit)
    }

    fun markWelcomeShown() = launchState.markWelcomeDialogShown()
}
