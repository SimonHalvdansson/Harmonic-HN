package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.utils.RelativeTimeFormatter

fun accountLastCheckedLabel(checkedMillis: Long, nowMillis: Long): String {
    if (checkedMillis <= 0) return "Not checked yet"
    // A check can finish after the UI's sampled clock, or the device clock can move backward.
    val relative = RelativeTimeFormatter.format(checkedMillis, maxOf(nowMillis, checkedMillis))
    return if (relative == "just now") "Last checked just now" else "Last checked $relative ago"
}
