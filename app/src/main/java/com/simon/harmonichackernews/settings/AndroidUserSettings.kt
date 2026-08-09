package com.simon.harmonichackernews.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Android persistence adapter for [UserSettings]. Existing keys and defaults remain authoritative. */
class AndroidUserSettings(context: Context) : UserSettings {
    private val appContext = context.applicationContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)

    override val story: StoryPreferences
        get() = StoryPreferences(
            showPoints = SettingsUtils.shouldShowPoints(appContext),
            compactPoints = SettingsUtils.shouldUseCompactPoints(appContext),
            includeTopLevelDomain = SettingsUtils.shouldIncludeTopLevelDomain(appContext),
            showCommentsCount = SettingsUtils.shouldShowCommentsCount(appContext),
            compactView = SettingsUtils.shouldUseCompactView(appContext),
            thumbnails = SettingsUtils.shouldShowThumbnails(appContext),
            previewImageMode = SettingsUtils.getPreferredStoryPreviewImageMode(appContext),
            borderlessLargePreviewImage =
                SettingsUtils.shouldUseBorderlessLargeStoryPreviewImage(appContext),
            showSummary = SettingsUtils.shouldShowStorySummary(appContext),
            storyTextSize = SettingsUtils.getPreferredStoryTextSize(appContext),
            commentTextSize = SettingsUtils.getPreferredCommentTextSize(appContext),
            showIndex = SettingsUtils.shouldShowIndex(appContext),
            compactHeader = SettingsUtils.shouldUseCompactHeader(appContext),
            leftAlign = SettingsUtils.shouldUseLeftAlign(appContext),
            cardStyle = SettingsUtils.shouldUseCardStoryDisplayStyle(appContext),
            tintCardUsingPreview = SettingsUtils.shouldTintCardUsingPreview(appContext),
            paletteTintConfigKey = SettingsUtils.getPreferredPaletteTintConfigKey(appContext),
            grayOutClicked = SettingsUtils.shouldGrayOutClicked(appContext),
            hotness = SettingsUtils.getPreferredHotness(appContext),
            faviconProvider = SettingsUtils.getPreferredFaviconProvider(appContext),
            font = SettingsUtils.getPreferredFont(appContext),
            hideJobs = SettingsUtils.shouldHideJobs(appContext),
            hideClicked = SettingsUtils.shouldHideClicked(appContext),
            alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(appContext),
            pagination = SettingsUtils.shouldUsePaginationMode(appContext),
            alwaysShowTapToRefresh = SettingsUtils.shouldAlwaysShowTapToRefresh(appContext),
            preferredStoryType = SettingsUtils.getPreferredStoryType(appContext),
        )

    override val comments: CommentPreferences
        get() = CommentPreferences(
            collapseParent = SettingsUtils.shouldCollapseParent(appContext),
            thumbnails = SettingsUtils.shouldShowThumbnails(appContext),
            showHeaderPreviewImage =
                SettingsUtils.shouldShowCommentsHeaderPreviewImage(appContext),
            tintHeader = SettingsUtils.shouldTintCommentsHeader(appContext),
            paletteTintConfigKey = SettingsUtils.getPreferredPaletteTintConfigKey(appContext),
            textSize = SettingsUtils.getPreferredCommentTextSize(appContext),
            depthIndicatorMode =
                SettingsUtils.getPreferredCommentDepthIndicatorMode(appContext).orEmpty(),
            showNavigationButtons = SettingsUtils.shouldShowNavigationButtons(appContext),
            font = SettingsUtils.getPreferredFont(appContext).orEmpty(),
            showTopLevelDepthIndicator =
                SettingsUtils.shouldShowTopLevelDepthIndicator(appContext),
            theme = ThemeUtils.getPreferredTheme(appContext),
            faviconProvider = SettingsUtils.getPreferredFaviconProvider(appContext),
            swapLongPressTap = SettingsUtils.shouldSwapCommentLongPressTap(appContext),
            cardStyle = SettingsUtils.shouldUseCardCommentDisplayStyle(appContext),
            cardBorder = SettingsUtils.shouldShowCommentCardBorder(appContext),
            showDividers = SettingsUtils.shouldShowCommentDividers(appContext),
            highlightMetadata = SettingsUtils.shouldHighlightCommentMeta(appContext),
            collectReferenceLinks = SettingsUtils.shouldCollectLinksInComments(appContext),
            collapseTopLevel = SettingsUtils.shouldCollapseTopLevel(appContext),
            sorting = SettingsUtils.getPreferredCommentSorting(appContext),
        )

    override val reading: ReadingPreferences
        get() = ReadingPreferences(
            integratedWebView = SettingsUtils.shouldUseIntegratedWebView(appContext),
            preloadWebViewMode = SettingsUtils.shouldPreloadWebView(appContext),
            preloadWebViewMinimumBattery =
                SettingsUtils.getPreloadWebViewMinimumBattery(appContext),
            matchWebViewTheme = SettingsUtils.shouldMatchWebViewTheme(appContext),
            readerModeEnabled = SettingsUtils.shouldUseReaderMode(appContext),
            readerModeDefault = SettingsUtils.shouldUseReaderModeByDefault(appContext),
            blockAds = SettingsUtils.shouldBlockAds(appContext),
            closeWebViewOnBack = SettingsUtils.shouldCloseWebViewOnBack(appContext),
            useAlgoliaApi = SettingsUtils.shouldUseAlgoliaAPI(appContext),
        )

    override val cache: CachePreferences
        get() = CachePreferences(
            storiesToCache = SettingsUtils.getStoriesToCache(appContext),
            cacheArticleSnapshots = SettingsUtils.shouldUseIntegratedWebView(appContext),
        )

    override val changes: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
