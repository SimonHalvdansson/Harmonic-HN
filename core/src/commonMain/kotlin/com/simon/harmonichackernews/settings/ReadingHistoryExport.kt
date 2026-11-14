package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.History
import kotlin.time.Instant

/** Exports the stored timestamps, which are not updated when an item is opened again. */
object ReadingHistoryExport {
    fun filename(year: Int, month: Int, day: Int): String =
        "HarmonicReadingHistory$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}.csv"

    fun csv(histories: List<History>): String = buildString {
        append("hn_id,recorded_at_utc,hn_url\r\n")
        for (history in histories.sortedWith(compareByDescending<History> { it.created }.thenBy { it.id })) {
            // Instant preserves millisecond precision and always formats in UTC. These three
            // fields cannot contain CSV delimiters, quotes, or line breaks.
            append(history.id)
            append(',')
            val timestamp = Instant.fromEpochMilliseconds(history.created).toString()
            append(if ('.' in timestamp) timestamp else timestamp.replace("Z", ".000Z"))
            append(",https://news.ycombinator.com/item?id=")
            append(history.id)
            append("\r\n")
        }
    }
}
