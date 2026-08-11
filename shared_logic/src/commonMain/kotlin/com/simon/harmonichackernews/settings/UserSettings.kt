package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.Flow

/**
 * Typed, platform-neutral view of the user's persisted preferences.
 *
 * The Android implementation deliberately remains backed by the app's existing preference keys.
 * Consumers depend on these snapshots rather than Context or SharedPreferences, which lets another
 * platform provide the same contract later without changing business or presentation logic.
 */
interface UserSettings {
    val story: StoryPreferences
    val comments: CommentPreferences
    val reading: ReadingPreferences
    val cache: CachePreferences
    val general: GeneralPreferences

    /** Emits after persisted user preferences change. Consumers can then read fresh snapshots. */
    val changes: Flow<Unit>

    fun setStoriesToCache(count: Int)
}

data class StoryPreferences(
    val showPoints: Boolean,
    val compactPoints: Boolean,
    val includeTopLevelDomain: Boolean,
    val showCommentsCount: Boolean,
    val compactView: Boolean,
    val thumbnails: Boolean,
    val previewImageMode: String,
    val borderlessLargePreviewImage: Boolean,
    val showSummary: Boolean,
    val storyTextSize: Float,
    val commentTextSize: Float,
    val showIndex: Boolean,
    val compactHeader: Boolean,
    val leftAlign: Boolean,
    val cardStyle: Boolean,
    val tintCardUsingPreview: Boolean,
    val paletteTintConfigKey: String,
    val grayOutClicked: Boolean,
    val hotness: Int,
    val faviconProvider: String,
    val font: String,
    val hideJobs: Boolean,
    val hideClicked: Boolean,
    val alwaysOpenComments: Boolean,
    val pagination: Boolean,
    val alwaysShowTapToRefresh: Boolean,
    val preferredStoryType: String,
    val additionalFrontpages: Set<String>,
)

data class CommentPreferences(
    val collapseParent: Boolean,
    val thumbnails: Boolean,
    val showHeaderPreviewImage: Boolean,
    val tintHeader: Boolean,
    val paletteTintConfigKey: String,
    val textSize: Float,
    val depthIndicatorMode: String,
    val showNavigationButtons: Boolean,
    val font: String,
    val showTopLevelDepthIndicator: Boolean,
    val theme: String?,
    val faviconProvider: String,
    val swapLongPressTap: Boolean,
    val cardStyle: Boolean,
    val cardBorder: Boolean,
    val showDividers: Boolean,
    val highlightMetadata: Boolean,
    val collectReferenceLinks: Boolean,
    val collapseTopLevel: Boolean,
    val sorting: String,
    val showScrollbar: Boolean,
    val animateChanges: Boolean,
    val smoothScroll: Boolean,
    val volumeNavigationMode: String,
)

data class ReadingPreferences(
    val integratedWebView: Boolean,
    val preloadWebViewMode: String,
    val preloadWebViewMinimumBattery: Int,
    val matchWebViewTheme: Boolean,
    val readerModeEnabled: Boolean,
    val readerModeDefault: Boolean,
    val blockAds: Boolean,
    val closeWebViewOnBack: Boolean,
    val useAlgoliaApi: Boolean,
    val readerModeFont: String,
    val readerModeFontSize: Int,
    val externalBrowser: Boolean,
    val redirectNitter: Boolean,
    val archiveRedirectDomains: List<String>,
    val previewArxiv: Boolean,
    val previewGithub: Boolean,
    val previewGitlab: Boolean,
    val previewStackExchange: Boolean,
    val previewWikipedia: Boolean,
    val previewX: Boolean,
)

data class CachePreferences(
    val storiesToCache: Int,
    val cacheArticleSnapshots: Boolean,
)

data class GeneralPreferences(
    val bookmarksEnabled: Boolean,
    val transparentStatusBar: Boolean,
    val specialNighttimeTheme: Boolean,
    val showChangelog: Boolean,
)
