package com.simon.harmonichackernews.app

/** Stable storage names and clock conversion shared by all platform composition roots. */
object AppBootstrapPolicy {
    const val WIDGET_CONFIGURATION_STORE = "widget_config"
    const val WIDGET_RUNTIME_STORE = "widget_stories_cache"

    fun minutesFromMidnight(hourOfDay: Int, minute: Int): Int =
        hourOfDay.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
}
