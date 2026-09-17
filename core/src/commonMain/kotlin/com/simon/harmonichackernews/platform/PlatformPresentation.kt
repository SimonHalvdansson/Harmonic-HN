package com.simon.harmonichackernews.platform

/** Locale-aware formatting remains native; feature wording and sequencing remain shared. */
interface PlatformTimeFormatter {
    fun time(epochMillis: Long): String
    fun localDate(epochMillis: Long): LocalCalendarDate
    fun uses24HourClock(): Boolean
}

data class LocalCalendarDate(val year: Int, val month: Int, val day: Int)

object PresentationCopy {
    const val CACHE_STORIES = "Caching stories"
    const val CACHE_FINISHED = "Finished"
    const val CACHE_FAILED = "Caching failed"
    const val CACHE_EMPTY = "No stories to cache"
    const val WRITE_ERROR = "Write error"
    const val READ_ERROR = "Read error"
    const val IMPORT_EMPTY = "File contained no bookmarks"
    const val EXPORT_EMPTY = "No bookmarks to export"
    const val TINT_CACHE_CLEARED = "Tint cache cleared"
    const val SETTINGS_RESET = "Settings reset"

    fun lastUpdated(time: String): String = "Last updated: $time"
    fun lastRefreshed(time: String): String = "Last refreshed: $time"
    fun cachingStories(total: Int): String =
        "Caching $total ${if (total == 1) "story" else "stories"}"

    fun importedBookmarks(count: Int, overwroteExisting: Boolean): String =
        (if (overwroteExisting) "Loaded " else "Added ") +
            "$count ${if (count == 1) "bookmark" else "bookmarks"}"
}
