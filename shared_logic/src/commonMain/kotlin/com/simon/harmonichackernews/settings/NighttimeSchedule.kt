package com.simon.harmonichackernews.settings

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
