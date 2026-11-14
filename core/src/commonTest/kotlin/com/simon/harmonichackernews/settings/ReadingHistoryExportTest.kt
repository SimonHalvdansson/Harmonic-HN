package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.History
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingHistoryExportTest {
    @Test
    fun csvUsesUtcMillisStableOrderingAndPreservesTheInput() {
        val histories = listOf(History(9, 1_000), History(3, 0), History(2, 1_000), History(4, 123))
        val original = histories.toList()
        assertEquals(
            "hn_id,recorded_at_utc,hn_url\r\n" +
                "2,1970-01-01T00:00:01.000Z,https://news.ycombinator.com/item?id=2\r\n" +
                "9,1970-01-01T00:00:01.000Z,https://news.ycombinator.com/item?id=9\r\n" +
                "4,1970-01-01T00:00:00.123Z,https://news.ycombinator.com/item?id=4\r\n" +
                "3,1970-01-01T00:00:00.000Z,https://news.ycombinator.com/item?id=3\r\n",
            ReadingHistoryExport.csv(histories),
        )
        assertEquals(original, histories)
    }

    @Test
    fun allTenThousandStoredEntriesAreExportedWithoutPagination() {
        val histories = (1..10_000).map { History(it, it.toLong()) }
        val rows = ReadingHistoryExport.csv(histories).split("\r\n")
        assertEquals(10_002, rows.size)
        assertEquals("10000,1970-01-01T00:00:10.000Z,https://news.ycombinator.com/item?id=10000", rows[1])
        assertEquals("1,1970-01-01T00:00:00.001Z,https://news.ycombinator.com/item?id=1", rows[10_000])
        assertEquals("", rows.last())
    }

    @Test
    fun filenameUsesPaddedLocalCalendarDate() {
        assertEquals("HarmonicReadingHistory2026-01-02.csv", ReadingHistoryExport.filename(2026, 1, 2))
    }
}
