package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.cache.SharedStoryCacheService
import com.simon.harmonichackernews.data.BookmarkImportPolicy
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.StoryResourceTintRepository
import com.simon.harmonichackernews.network.StoryPreviewRepository
import com.simon.harmonichackernews.platform.CredentialIds
import com.simon.harmonichackernews.platform.CredentialStore
import com.simon.harmonichackernews.platform.HackerNewsAccountRepository
import com.simon.harmonichackernews.platform.HistoryStore
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.summary.formatDecimalBytes

data class DataSettingsSnapshot(
    val bookmarksEnabled: Boolean,
    val bookmarkCount: Int,
    val loggedIn: Boolean,
    val historyCount: Int,
    val postCacheCount: Int,
    val tintCacheCount: Int,
    val showChangelog: Boolean,
    val aiModelBytes: Long? = null,
)

data class DataSettingsCounts(
    val bookmarks: Int,
    val history: Int,
    val postCache: Int,
    val tintCache: Int,
    val aiModelsBytes: Long? = null,
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
        aiModelBytes = counts.aiModelsBytes?.coerceAtLeast(0L),
    )

    fun exportDecision(bookmarkCount: Int): BookmarkExportDecision =
        if (bookmarkCount > 0) BookmarkExportDecision.Ready else BookmarkExportDecision.Empty

    fun clearedItemsMessage(count: Int, singular: String, plural: String): String? =
        count.takeIf { it > 0 }?.let { "Cleared $it ${if (it == 1) singular else plural}" }

    fun bookmarksFilename(year: Int, month: Int, day: Int): String =
        "HarmonicBookmarks$year-$month-$day.txt"
}

sealed interface BookmarkImportResult {
    data object Empty : BookmarkImportResult
    data class Imported(val count: Int, val overwroteExisting: Boolean) : BookmarkImportResult
}

/**
 * Portable Data-settings workflow. Document selection and OS app-link settings are intentionally
 * left to the host; all repository reads, import/export policy and destructive actions live here.
 */
class DataSettingsService(
    private val settings: AppSettingsRepository,
    private val settingsReset: SettingsResetUseCase,
    private val savedItems: SavedItemsRepository,
    private val accounts: HackerNewsAccountRepository,
    private val history: HistoryStore?,
    private val storyCache: SharedStoryCacheService,
    private val previewResources: StoryPreviewRepository,
    private val storyResourceTints: StoryResourceTintRepository,
    private val localModels: LocalModelService?,
) {
    fun snapshot(): DataSettingsSnapshot = DataSettingsPolicy.snapshot(
        settings = settings.snapshot(),
        counts = DataSettingsCounts(
            bookmarks = bookmarkCount(),
            history = history?.size ?: 0,
            postCache = storyCache.itemCount(),
            tintCache = storyResourceTints.count(),
            aiModelsBytes = localModels?.storedModelBytes(),
        ),
        loggedIn = accounts.load() != null,
    )

    fun bookmarkCount(): Int = savedItems.loadItems(SavedItemSource.BOOKMARKS).size

    fun bookmarkIdsByNewest(): List<Int> = savedItems.loadItems(
        source = SavedItemSource.BOOKMARKS,
        sortedByCreated = true,
    ).map { it.id }

    fun exportBookmarks(): String? = savedItems.loadItems(SavedItemSource.BOOKMARKS)
        .takeIf { it.isNotEmpty() }
        ?.let(SavedItemCodec::encode)

    fun importBookmarks(content: String, overwrite: Boolean): BookmarkImportResult {
        val result = BookmarkImportPolicy.apply(
            content = content,
            current = savedItems.loadItems(SavedItemSource.BOOKMARKS),
            overwrite = overwrite,
        ) ?: return BookmarkImportResult.Empty
        savedItems.saveItems(SavedItemSource.BOOKMARKS, result.items)
        return BookmarkImportResult.Imported(result.importedCount, overwrite)
    }

    fun clearHistory(): String? {
        val count = history?.size ?: 0
        history?.clear()
        return DataSettingsPolicy.clearedItemsMessage(count, "entry", "entries")
    }

    suspend fun clearPostCache(): String? {
        val count = storyCache.clear()
        previewResources.clear()
        return DataSettingsPolicy.clearedItemsMessage(count, "cached post", "cached posts")
    }

    fun clearTintCache() {
        storyResourceTints.clear()
    }

    suspend fun clearAiModels(): String {
        val models = localModels ?: return "AI model storage is not available"
        val bytes = models.storedModelBytes()
        return if (models.clearStoredModels()) {
            if (bytes > 0L) "Cleared ${formatDecimalBytes(bytes)} of AI models"
            else "No AI models to clear"
        } else {
            "Could not clear AI models"
        }
    }

    fun resetSettings() = settingsReset.execute()
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
