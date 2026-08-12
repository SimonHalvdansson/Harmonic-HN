package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.platform.CredentialIds
import com.simon.harmonichackernews.platform.CredentialStore

data class DataSettingsSnapshot(
    val bookmarksEnabled: Boolean,
    val bookmarkCount: Int,
    val loggedIn: Boolean,
    val historyCount: Int,
    val postCacheCount: Int,
    val tintCacheCount: Int,
    val showChangelog: Boolean,
)

data class DataSettingsCounts(
    val bookmarks: Int,
    val history: Int,
    val postCache: Int,
    val tintCache: Int,
)

sealed interface BookmarkExportDecision {
    data object Empty : BookmarkExportDecision
    data object Ready : BookmarkExportDecision
}

object DataSettingsPolicy {
    fun snapshot(
        settings: AppSettings,
        counts: DataSettingsCounts,
        loggedIn: Boolean,
    ): DataSettingsSnapshot = DataSettingsSnapshot(
        bookmarksEnabled = settings.general.bookmarksEnabled,
        bookmarkCount = counts.bookmarks.coerceAtLeast(0),
        loggedIn = loggedIn,
        historyCount = counts.history.coerceAtLeast(0),
        postCacheCount = counts.postCache.coerceAtLeast(0),
        tintCacheCount = counts.tintCache.coerceAtLeast(0),
        showChangelog = settings.general.showChangelog,
    )

    fun exportDecision(bookmarkCount: Int): BookmarkExportDecision =
        if (bookmarkCount > 0) BookmarkExportDecision.Ready else BookmarkExportDecision.Empty

    fun clearedItemsMessage(count: Int, singular: String, plural: String): String? =
        count.takeIf { it > 0 }?.let { "Cleared $it ${if (it == 1) singular else plural}" }

    fun bookmarksFilename(year: Int, month: Int, day: Int): String =
        "HarmonicBookmarks$year-$month-$day.txt"
}

/** Resets settings while deliberately preserving user content and Hacker News login credentials. */
class SettingsResetUseCase(
    private val defaultSettings: KeyValueStore,
    private val globalSettings: KeyValueStore,
    private val credentials: CredentialStore,
) {
    fun execute() {
        defaultSettings.clear()
        credentials.remove(CredentialIds.AI_SUMMARY_API_KEY)
        globalSettings.remove(NighttimeScheduleKeys.FROM_HOUR)
        globalSettings.remove(NighttimeScheduleKeys.FROM_MINUTE)
        globalSettings.remove(NighttimeScheduleKeys.TO_HOUR)
        globalSettings.remove(NighttimeScheduleKeys.TO_MINUTE)
    }
}
