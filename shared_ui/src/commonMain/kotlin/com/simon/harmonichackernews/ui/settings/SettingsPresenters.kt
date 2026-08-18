package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.painter.Painter
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.settings.AdditionalFrontpagePreferences
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.AppSettings
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.AppearanceBooleanPreference
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import com.simon.harmonichackernews.settings.CommentSortingPreference
import com.simon.harmonichackernews.settings.CommentVolumeNavigationMode
import com.simon.harmonichackernews.settings.CommentsProvider
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.ContentFilterType
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.DisplayStylePreferences
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.ReadingBooleanPreference
import com.simon.harmonichackernews.settings.StoryBooleanPreference
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.settings.StoryStringPreference
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.settings.WebViewPreloadMode
import com.simon.harmonichackernews.ui.content.StoryItemUiModel

/** Platform work requested by a portable settings action. */
enum class SettingsPlatformEffect { RefreshStoryWidgets, RequestRestart, ThemeChanged }

class StoriesSettingsPresenter(
    private val repository: AppSettingsRepository,
) {
    fun state(
        settings: AppSettings = repository.snapshot(),
        previewModel: StoryItemUiModel,
        faviconIcon: Painter,
    ): StoriesSettingsUiState {
        val story = settings.story
        return StoriesSettingsUiState(
            previewModel = previewModel,
            previewImageMode = story.previewImageMode,
            previewOffValue = StoryPreviewPreferences.OFF,
            previewSmallValue = StoryPreviewPreferences.SMALL,
            previewLargeValue = StoryPreviewPreferences.LARGE,
            borderlessLargeImage = story.borderlessLargePreviewImage,
            compact = story.compactView,
            showSummary = story.showSummary,
            showThumbnails = story.thumbnails,
            showPoints = story.showPoints,
            compactPoints = story.compactPoints,
            includeTopLevelDomain = story.includeTopLevelDomain,
            showComments = story.showCommentsCount,
            showIndex = story.showIndex,
            leftAlignComments = story.leftAlign,
            tint = story.tintCardUsingPreview,
            displayStyle = if (story.cardStyle) {
                DisplayStylePreferences.CARD
            } else {
                DisplayStylePreferences.STANDARD
            },
            standardStyleValue = DisplayStylePreferences.STANDARD,
            cardStyleValue = DisplayStylePreferences.CARD,
            textSize = story.storyTextSize,
            textSizeOffset = TextPreferences.storyTextSizeOffset(story.storyTextSize),
            minTextSizeOffset = TextPreferences.MIN_TEXT_SIZE_OFFSET,
            maxTextSizeOffset = TextPreferences.MAX_TEXT_SIZE_OFFSET,
            hotnessEnabled = story.hotness != -1,
            hotnessLabel = hotnessLabel(story.hotness),
            preferredFont = story.font,
            paletteTintConfigKey = story.paletteTintConfigKey,
            startingPage = story.preferredStoryType,
            additionalFrontpagesSummary = AdditionalFrontpagePreferences.summary(
                story.additionalFrontpages,
            ),
            alwaysOpenComments = story.alwaysOpenComments,
            pagination = story.pagination,
            hideClicked = story.hideClicked,
            grayOutClicked = story.grayOutClicked,
            faviconProvider = story.faviconProvider,
            faviconIcon = faviconIcon,
        )
    }

    fun setBoolean(
        setting: StoriesBooleanSetting,
        value: Boolean,
    ): Set<SettingsPlatformEffect> {
        repository.setStoryBoolean(setting.preference, value)
        return buildSet {
            if (setting == StoriesBooleanSetting.IncludeTopLevelDomain ||
                setting == StoriesBooleanSetting.ShowIndex
            ) add(SettingsPlatformEffect.RefreshStoryWidgets)
            if (setting == StoriesBooleanSetting.HideClicked) {
                add(SettingsPlatformEffect.RequestRestart)
            }
        }
    }

    fun setString(setting: StoriesStringSetting, value: String) =
        repository.setStoryString(setting.preference, value)

    fun setTextSizeOffset(offset: Int) =
        repository.setStoryTextSize(TextPreferences.storyTextSizeForOffset(offset))

    fun setHotness(value: String) = repository.setHotness(value.toIntOrNull() ?: -1)

    fun setStartingPage(value: String): Set<SettingsPlatformEffect> {
        repository.setPreferredStoryType(value)
        return setOf(SettingsPlatformEffect.RequestRestart)
    }

    fun setAdditionalFrontpages(value: Set<String>): Set<SettingsPlatformEffect> {
        repository.setAdditionalFrontpages(value)
        return setOf(SettingsPlatformEffect.RequestRestart)
    }

    fun setFaviconProvider(value: String) = repository.setFaviconProvider(value)

    val snapshot: AppSettings get() = repository.snapshot()
}

class CommentsSettingsPresenter(
    private val repository: AppSettingsRepository,
) {
    fun state(settings: AppSettings = repository.snapshot()): CommentsSettingsUiState {
        val comments = settings.comments
        return CommentsSettingsUiState(
            displayStyle = comments.displayStyle,
            showBorder = comments.cardBorder,
            textSize = comments.textSize,
            textSizeOffset = TextPreferences.commentTextSizeOffset(comments.textSize),
            minTextSizeOffset = TextPreferences.MIN_TEXT_SIZE_OFFSET,
            maxTextSizeOffset = TextPreferences.MAX_TEXT_SIZE_OFFSET,
            collectLinks = comments.collectReferenceLinks,
            emphasizeMetadata = comments.highlightMetadata,
            depthMode = comments.depthIndicatorMode,
            depthModeLabel = com.simon.harmonichackernews.settings.CommentDepthPreferences
                .modeLabel(comments.depthIndicatorMode),
            showDividers = comments.showDividers,
            preferredFont = comments.font,
            topLevelIndicators = comments.showTopLevelDepthIndicator,
            showScrollbar = comments.showScrollbar,
            animateChanges = comments.animateChanges,
            storyTintEnabled = settings.story.tintCardUsingPreview,
            showUpButton = comments.showUpButton,
            headerTint = comments.headerTintEnabled,
            storyPreviewEnabled = settings.story.previewImageMode != StoryPreviewPreferences.OFF,
            headerPreviewImage = comments.headerPreviewImageEnabled,
            collapseParent = comments.collapseParent,
            collapseTopLevel = comments.collapseTopLevel,
            swapTap = comments.swapLongPressTap,
            sorting = comments.sortingPreference,
            provider = settings.reading.commentsProvider,
            showNavigationButtons = comments.showNavigationButtons,
            volumeNavigation = comments.volumeNavigation,
            smoothScroll = comments.smoothScroll,
        )
    }

    fun setDisplayStyle(value: DisplayStyle) = repository.setCommentDisplayStyle(value)
    fun setTextSizeOffset(offset: Int) =
        repository.setCommentTextSize(TextPreferences.commentTextSizeForOffset(offset))

    fun setBoolean(setting: CommentsBooleanSetting, value: Boolean) =
        repository.setCommentBoolean(setting.preference, value)

    fun setSorting(value: String) =
        repository.setCommentSorting(CommentSortingPreference.fromStored(value))

    fun setProvider(value: String) =
        repository.setCommentsProvider(CommentsProvider.fromStored(value))

    fun setVolumeNavigation(value: String) = repository.setCommentsVolumeNavigation(
        CommentVolumeNavigationMode.fromStored(value),
    )

    fun setDepthIndicatorMode(value: String) = repository.setCommentDepthIndicatorMode(value)
}

class WebLinksSettingsPresenter(
    private val repository: AppSettingsRepository,
) {
    fun state(
        fontLabel: String,
        settings: AppSettings = repository.snapshot(),
    ): WebLinksSettingsUiState {
        val reading = settings.reading
        return WebLinksSettingsUiState(
            integratedWebView = reading.integratedWebView,
            closeWebViewOnBack = reading.closeWebViewOnBack,
            preloadSummary = reading.preloadMode.summary(reading.preloadWebViewMinimumBattery),
            matchWebViewTheme = reading.matchWebViewTheme,
            blockWebViewAds = reading.blockAds,
            readerModeEnabled = reading.readerModeEnabled,
            readerModeDefault = reading.readerModeDefault,
            readerModeFontLabel = fontLabel,
            readerModeFontSize = reading.readerModeFontSize,
            readerModeFontSizeDefault = TextPreferences.DEFAULT_READER_MODE_FONT_SIZE,
            readerModeFontSizeRange = TextPreferences.MIN_READER_MODE_FONT_SIZE..
                TextPreferences.MAX_READER_MODE_FONT_SIZE,
            externalBrowser = reading.externalBrowser,
            redirectNitter = reading.redirectNitter,
            archiveDomainCount = reading.archiveRedirectDomains.size,
            enabledLinkPreviews = reading.enabledLinkPreviews,
        )
    }

    fun setBoolean(setting: WebLinksBooleanSetting, value: Boolean) =
        repository.setReadingBoolean(setting.preference, value)

    fun setLinkPreview(type: LinkPreviewType, enabled: Boolean) =
        repository.setLinkPreviewEnabled(type, enabled)

    fun setReaderFontSize(value: Int) = repository.setReaderModeFontSize(value)
    fun setReaderFont(value: AppFont) = repository.setReaderModeFont(value)
    fun setPreload(mode: WebViewPreloadMode, minimumBattery: Int) =
        repository.setWebViewPreload(mode, minimumBattery)

    fun setArchiveDomains(domains: List<String>) = repository.setArchiveRedirectDomains(domains)
    val snapshot: AppSettings get() = repository.snapshot()
}

class AppearanceSettingsPresenter(
    private val repository: AppSettingsRepository,
) {
    fun state(
        themeLabel: String,
        nighttimeRangeLabel: String,
        nighttimeThemeLabel: String,
        fontLabel: String,
        showTransparentStatusBar: Boolean,
        settings: AppSettings = repository.snapshot(),
    ): AppearanceSettingsUiState {
        val tintEnabled = settings.story.tintCardUsingPreview
        return AppearanceSettingsUiState(
            themeLabel = themeLabel,
            specialNighttime = settings.general.specialNighttimeTheme,
            nighttimeRangeLabel = nighttimeRangeLabel,
            nighttimeThemeLabel = nighttimeThemeLabel,
            fontLabel = fontLabel,
            paletteTintSummary = if (tintEnabled) {
                PaletteTintPreferences.summary(settings.story.paletteTintConfigKey)
            } else {
                "Enable in Stories settings"
            },
            paletteTintEnabled = tintEnabled,
            showTransparentStatusBar = showTransparentStatusBar,
            transparentStatusBar = settings.general.transparentStatusBar,
            compactHeader = settings.story.compactHeader,
        )
    }

    fun setBoolean(
        setting: AppearanceBooleanSetting,
        value: Boolean,
    ): Set<SettingsPlatformEffect> {
        repository.setAppearanceBoolean(setting.preference, value)
        return if (setting == AppearanceBooleanSetting.CompactHeader) {
            emptySet()
        } else {
            setOf(SettingsPlatformEffect.ThemeChanged)
        }
    }

    fun setTheme(value: String, nighttime: Boolean): Set<SettingsPlatformEffect> {
        if (nighttime) repository.setNighttimeTheme(value) else repository.setTheme(value)
        return setOf(SettingsPlatformEffect.ThemeChanged)
    }

    fun setFont(value: AppFont) = repository.setFont(value)
    fun applyWelcomePreset(expressive: Boolean) = repository.applyWelcomePreset(expressive)
    fun setPaletteTint(mode: String?, strength: Int, colorfulness: Int, tone: Int): Boolean =
        repository.setPaletteTint(mode, strength, colorfulness, tone)

    fun clearPaletteTint(): Boolean = repository.clearPaletteTint()
    val snapshot: AppSettings get() = repository.snapshot()
}

class FiltersTagsSettingsPresenter(
    private val settings: AppSettingsRepository,
    private val filters: ContentFilterRepository,
    private val userTags: UserTagsRepository,
) {
    fun state(snapshot: AppSettings = settings.snapshot()): FiltersTagsSettingsUiState =
        FiltersTagsSettingsUiState(
            tags = userTags.tags(normalizeUsernames = false)
                .map { TaggedUserUi(it.key, it.value) }
                .sortedBy { it.username.lowercase() },
            hideJobs = snapshot.story.hideJobs,
        )

    fun setHideJobs(value: Boolean) =
        settings.setStoryBoolean(StoryBooleanPreference.HIDE_JOBS, value)

    fun filterItems(type: ContentFilterType): List<String> = filters.items(type)
    fun setFilterItems(type: ContentFilterType, items: List<String>) = filters.setItems(type, items)
    fun tagFor(userName: String): String = userTags.tagFor(userName)
    fun setTag(userName: String, tag: String) = userTags.setTag(userName, tag)
}

data class FilterDialogContent(
    val type: ContentFilterType,
    val title: String,
    val subtitle: String,
    val inputLabel: String,
    val emptyMessage: String,
)

val ContentFilterDialog.content: FilterDialogContent
    get() = when (this) {
        ContentFilterDialog.StoryTitle -> FilterDialogContent(
            ContentFilterType.STORY_TITLE,
            "Filter by story title",
            "Hide stories containing these words or phrases in the title",
            "Word or phrase",
            "No story title filters",
        )
        ContentFilterDialog.Domain -> FilterDialogContent(
            ContentFilterType.DOMAIN,
            "Filter by domain",
            "Hide stories from these domains",
            "Domain",
            "No domain filters",
        )
        ContentFilterDialog.User -> FilterDialogContent(
            ContentFilterType.USER,
            "Blocked users",
            "Hide stories and comments posted by these users",
            "Username",
            "No blocked users",
        )
    }

private val StoriesBooleanSetting.preference: StoryBooleanPreference
    get() = when (this) {
        StoriesBooleanSetting.BorderlessLargeImage -> StoryBooleanPreference.BORDERLESS_LARGE_IMAGE
        StoriesBooleanSetting.Tint -> StoryBooleanPreference.TINT_CARD_USING_PREVIEW
        StoriesBooleanSetting.Compact -> StoryBooleanPreference.COMPACT_VIEW
        StoriesBooleanSetting.ShowSummary -> StoryBooleanPreference.SHOW_SUMMARY
        StoriesBooleanSetting.ShowThumbnails -> StoryBooleanPreference.SHOW_THUMBNAILS
        StoriesBooleanSetting.ShowPoints -> StoryBooleanPreference.SHOW_POINTS
        StoriesBooleanSetting.CompactPoints -> StoryBooleanPreference.COMPACT_POINTS
        StoriesBooleanSetting.IncludeTopLevelDomain -> StoryBooleanPreference.INCLUDE_TOP_LEVEL_DOMAIN
        StoriesBooleanSetting.ShowComments -> StoryBooleanPreference.SHOW_COMMENTS_COUNT
        StoriesBooleanSetting.ShowIndex -> StoryBooleanPreference.SHOW_INDEX
        StoriesBooleanSetting.LeftAlignComments -> StoryBooleanPreference.LEFT_ALIGN
        StoriesBooleanSetting.AlwaysOpenComments -> StoryBooleanPreference.ALWAYS_OPEN_COMMENTS
        StoriesBooleanSetting.Pagination -> StoryBooleanPreference.PAGINATION
        StoriesBooleanSetting.HideClicked -> StoryBooleanPreference.HIDE_CLICKED
        StoriesBooleanSetting.GrayOutClicked -> StoryBooleanPreference.GRAY_OUT_CLICKED
    }

private val StoriesStringSetting.preference: StoryStringPreference
    get() = when (this) {
        StoriesStringSetting.PreviewImageMode -> StoryStringPreference.PREVIEW_IMAGE_MODE
        StoriesStringSetting.DisplayStyle -> StoryStringPreference.DISPLAY_STYLE
    }

private val CommentsBooleanSetting.preference: CommentBooleanPreference
    get() = when (this) {
        CommentsBooleanSetting.Border -> CommentBooleanPreference.CARD_BORDER
        CommentsBooleanSetting.CollectLinks -> CommentBooleanPreference.COLLECT_REFERENCE_LINKS
        CommentsBooleanSetting.EmphasizeMetadata -> CommentBooleanPreference.HIGHLIGHT_METADATA
        CommentsBooleanSetting.Dividers -> CommentBooleanPreference.SHOW_DIVIDERS
        CommentsBooleanSetting.TopLevelIndicators -> CommentBooleanPreference.TOP_LEVEL_DEPTH_INDICATOR
        CommentsBooleanSetting.Scrollbar -> CommentBooleanPreference.SHOW_SCROLLBAR
        CommentsBooleanSetting.AnimateChanges -> CommentBooleanPreference.ANIMATE_CHANGES
        CommentsBooleanSetting.ShowUpButton -> CommentBooleanPreference.SHOW_UP_BUTTON
        CommentsBooleanSetting.HeaderTint -> CommentBooleanPreference.HEADER_TINT
        CommentsBooleanSetting.HeaderPreviewImage -> CommentBooleanPreference.HEADER_PREVIEW_IMAGE
        CommentsBooleanSetting.CollapseParent -> CommentBooleanPreference.COLLAPSE_PARENT
        CommentsBooleanSetting.CollapseTopLevel -> CommentBooleanPreference.COLLAPSE_TOP_LEVEL
        CommentsBooleanSetting.SwapTap -> CommentBooleanPreference.SWAP_LONG_PRESS_TAP
        CommentsBooleanSetting.NavigationButtons -> CommentBooleanPreference.SHOW_NAVIGATION_BUTTONS
        CommentsBooleanSetting.SmoothScroll -> CommentBooleanPreference.SMOOTH_SCROLL
    }

private val WebLinksBooleanSetting.preference: ReadingBooleanPreference
    get() = when (this) {
        WebLinksBooleanSetting.IntegratedWebView -> ReadingBooleanPreference.INTEGRATED_WEB_VIEW
        WebLinksBooleanSetting.CloseWebViewOnBack -> ReadingBooleanPreference.CLOSE_WEB_VIEW_ON_BACK
        WebLinksBooleanSetting.MatchWebViewTheme -> ReadingBooleanPreference.MATCH_WEB_VIEW_THEME
        WebLinksBooleanSetting.BlockWebViewAds -> ReadingBooleanPreference.BLOCK_ADS
        WebLinksBooleanSetting.ReaderModeEnabled -> ReadingBooleanPreference.READER_MODE_ENABLED
        WebLinksBooleanSetting.ReaderModeDefault -> ReadingBooleanPreference.READER_MODE_DEFAULT
        WebLinksBooleanSetting.ExternalBrowser -> ReadingBooleanPreference.EXTERNAL_BROWSER
        WebLinksBooleanSetting.RedirectNitter -> ReadingBooleanPreference.REDIRECT_NITTER
    }

private val AppearanceBooleanSetting.preference: AppearanceBooleanPreference
    get() = when (this) {
        AppearanceBooleanSetting.SpecialNighttime -> AppearanceBooleanPreference.SPECIAL_NIGHTTIME
        AppearanceBooleanSetting.TransparentStatusBar ->
            AppearanceBooleanPreference.TRANSPARENT_STATUS_BAR
        AppearanceBooleanSetting.CompactHeader -> AppearanceBooleanPreference.COMPACT_HEADER
    }

private fun hotnessLabel(value: Int): String = when (value) {
    100, 200, 300, 400 -> "Points + comments > $value"
    else -> "Never"
}
