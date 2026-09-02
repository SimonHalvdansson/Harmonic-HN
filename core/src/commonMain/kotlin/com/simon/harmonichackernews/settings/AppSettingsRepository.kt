package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Typed story toggles accepted by [StoredSettingsMutator]. */
enum class StoryBooleanPreference(internal val storageKey: String) {
    BORDERLESS_LARGE_IMAGE(UserPreferenceKeys.STORY_PREVIEW_IMAGE_BORDERLESS),
    TINT_CARD_USING_PREVIEW(UserPreferenceKeys.TINT_CARD_USING_PREVIEW),
    COMPACT_VIEW(UserPreferenceKeys.COMPACT_VIEW),
    SHOW_SUMMARY(UserPreferenceKeys.SHOW_STORY_SUMMARY),
    SHOW_THUMBNAILS(UserPreferenceKeys.THUMBNAILS),
    SHOW_POINTS(UserPreferenceKeys.SHOW_POINTS),
    COMPACT_POINTS(UserPreferenceKeys.COMPACT_POINTS),
    INCLUDE_TOP_LEVEL_DOMAIN(UserPreferenceKeys.INCLUDE_TOP_LEVEL_DOMAIN),
    SHOW_COMMENTS_COUNT(UserPreferenceKeys.SHOW_COMMENTS_COUNT),
    SHOW_INDEX(UserPreferenceKeys.SHOW_INDEX),
    LEFT_ALIGN(UserPreferenceKeys.LEFT_ALIGN),
    ALWAYS_OPEN_COMMENTS(UserPreferenceKeys.ALWAYS_OPEN_COMMENTS),
    PAGINATION(UserPreferenceKeys.PAGINATION_MODE),
    HIDE_CLICKED(UserPreferenceKeys.HIDE_CLICKED),
    GRAY_OUT_CLICKED(UserPreferenceKeys.GRAY_OUT_CLICKED),
    HIDE_JOBS(UserPreferenceKeys.HIDE_JOBS),
}

enum class StoryStringPreference { DISPLAY_STYLE }

enum class CommentBooleanPreference(internal val storageKey: String) {
    CARD_BORDER(UserPreferenceKeys.COMMENT_CARD_BORDER),
    COLLECT_REFERENCE_LINKS(UserPreferenceKeys.COLLECT_LINKS_IN_COMMENTS),
    HIGHLIGHT_METADATA(UserPreferenceKeys.HIGHLIGHT_COMMENT_META),
    SHOW_DIVIDERS(UserPreferenceKeys.COMMENT_DIVIDERS),
    TOP_LEVEL_DEPTH_INDICATOR(UserPreferenceKeys.TOP_LEVEL_THREAD_INDICATORS),
    SHOW_SCROLLBAR(UserPreferenceKeys.COMMENTS_SCROLLBAR),
    ANIMATE_CHANGES(UserPreferenceKeys.COMMENTS_ANIMATION),
    HEADER_TINT(UserPreferenceKeys.COMMENTS_HEADER_TINT),
    HEADER_PREVIEW_IMAGE(UserPreferenceKeys.COMMENTS_HEADER_PREVIEW_IMAGE),
    SHOW_UP_BUTTON(UserPreferenceKeys.COMMENTS_SHOW_UP_BUTTON),
    COLLAPSE_PARENT(UserPreferenceKeys.COLLAPSE_PARENT),
    COLLAPSE_TOP_LEVEL(UserPreferenceKeys.COLLAPSE_TOP_LEVEL),
    HIDE_DELAYED_COMMENTS(UserPreferenceKeys.HIDE_DELAYED_COMMENTS),
    SWAP_LONG_PRESS_TAP(UserPreferenceKeys.COMMENTS_SWAP_LONG),
    SHOW_NAVIGATION_BUTTONS(UserPreferenceKeys.SCROLL_NAVIGATION),
    SMOOTH_SCROLL(UserPreferenceKeys.COMMENTS_SMOOTH_SCROLL),
}

enum class ReadingBooleanPreference(internal val storageKey: String) {
    INTEGRATED_WEB_VIEW(UserPreferenceKeys.WEBVIEW),
    CLOSE_WEB_VIEW_ON_BACK(UserPreferenceKeys.CLOSE_WEBVIEW_ON_BACK),
    MATCH_WEB_VIEW_THEME(UserPreferenceKeys.WEBVIEW_MATCH_THEME),
    BLOCK_ADS(UserPreferenceKeys.WEBVIEW_ADBLOCK),
    READER_MODE_ENABLED(UserPreferenceKeys.READER_MODE_ENABLED),
    READER_MODE_DEFAULT(UserPreferenceKeys.READER_MODE_DEFAULT),
    EXTERNAL_BROWSER(UserPreferenceKeys.EXTERNAL_BROWSER),
    REDIRECT_NITTER(UserPreferenceKeys.REDIRECT_NITTER),
}

enum class AppearanceBooleanPreference(internal val storageKey: String) {
    SPECIAL_NIGHTTIME(UserPreferenceKeys.SPECIAL_NIGHTTIME),
    TRANSPARENT_STATUS_BAR(UserPreferenceKeys.TRANSPARENT_STATUS_BAR),
    COMPACT_HEADER(UserPreferenceKeys.COMPACT_HEADER),
}

enum class GeneralBooleanPreference(internal val storageKey: String) {
    BOOKMARKS_ENABLED(UserPreferenceKeys.BOOKMARKS_ENABLED),
    SHOW_CHANGELOG(UserPreferenceKeys.SHOW_CHANGELOG),
}

enum class DebugBooleanPreference(internal val storageKey: String) {
    ALWAYS_SHOW_TAP_TO_REFRESH(UserPreferenceKeys.ALWAYS_SHOW_TAP_TO_REFRESH),
}

/**
 * Observable, platform-neutral settings facade.
 *
 * The platform owns the [KeyValueStore] and supplies change notifications through [UserSettings].
 * Every read and write remains typed here, so UI code never needs a platform preferences API.
 */
class AppSettingsRepository(
    private val reader: UserSettings,
    private val mutator: StoredSettingsMutator,
) {
    constructor(
        store: KeyValueStore,
        changes: Flow<Unit>,
        theme: () -> String? = { null },
        showCommentsUpButtonByDefault: Boolean = false,
        preloadCommentsFromStoriesByDefault: Boolean = false,
    ) : this(
        reader = StoredUserSettings(
            store,
            changes,
            theme,
            showCommentsUpButtonByDefault,
            preloadCommentsFromStoriesByDefault,
        ),
        mutator = StoredSettingsMutator(store),
    )

    fun snapshot(): AppSettings = AppSettings(
        story = reader.story,
        comments = reader.comments,
        reading = reader.reading,
        cache = reader.cache,
        general = reader.general,
        appearance = reader.appearance,
        debug = reader.debug,
    )

    val updates: Flow<AppSettings> = flow {
        emit(snapshot())
        reader.changes.collect { emit(snapshot()) }
    }.distinctUntilChanged()

    fun setStoryBoolean(preference: StoryBooleanPreference, value: Boolean) =
        mutator.setStoryBoolean(preference, value)

    fun setStoryString(preference: StoryStringPreference, value: String) =
        mutator.setStoryString(preference, value)

    fun setStoryPreviewMode(value: StoryPreviewMode) = mutator.setStoryPreviewMode(value)

    fun setStoryTextSize(value: Float) = mutator.setStoryTextSize(value)
    fun setHotness(value: Int) = mutator.setHotness(value)
    fun setPreferredStoryType(value: String) = mutator.setPreferredStoryType(value)
    fun setAdditionalFrontpages(value: Set<String>) = mutator.setAdditionalFrontpages(value)
    fun setFaviconProvider(value: String) = mutator.setFaviconProvider(value)
    fun setFont(value: String) = mutator.setFont(value)
    fun setFont(value: AppFont) = mutator.setFont(value)

    fun setCommentBoolean(preference: CommentBooleanPreference, value: Boolean) =
        mutator.setCommentBoolean(preference, value)

    fun setCommentDisplayStyle(value: String) = mutator.setCommentDisplayStyle(value)
    fun setCommentDisplayStyle(value: DisplayStyle) = mutator.setCommentDisplayStyle(value)
    fun setCommentTextSize(value: Float) = mutator.setCommentTextSize(value)
    fun setCommentSorting(value: String) = mutator.setCommentSorting(value)
    fun setCommentSorting(value: CommentSortingPreference) = mutator.setCommentSorting(value)
    fun setCommentsProvider(value: String) = mutator.setCommentsProvider(value)
    fun setCommentsProvider(value: CommentsProvider) = mutator.setCommentsProvider(value)
    fun setCommentsVolumeNavigation(value: String) = mutator.setCommentsVolumeNavigation(value)
    fun setCommentsVolumeNavigation(value: CommentVolumeNavigationMode) =
        mutator.setCommentsVolumeNavigation(value)
    fun setCommentDepthIndicatorMode(value: String) = mutator.setCommentDepthIndicatorMode(value)
    fun setCommentsPreload(mode: WebViewPreloadMode, minimumBattery: Int) =
        mutator.setCommentsPreload(mode, minimumBattery)

    fun setReadingBoolean(preference: ReadingBooleanPreference, value: Boolean) =
        mutator.setReadingBoolean(preference, value)

    fun setLinkPreviewEnabled(type: LinkPreviewType, enabled: Boolean) =
        mutator.setLinkPreviewEnabled(type, enabled)

    fun setReaderModeFontSize(value: Int) = mutator.setReaderModeFontSize(value)
    fun setReaderModeFont(value: String) = mutator.setReaderModeFont(value)
    fun setReaderModeFont(value: AppFont) = mutator.setReaderModeFont(value)
    fun setWebViewPreload(mode: String, minimumBattery: Int) =
        mutator.setWebViewPreload(mode, minimumBattery)
    fun setWebViewPreload(mode: WebViewPreloadMode, minimumBattery: Int) =
        mutator.setWebViewPreload(mode, minimumBattery)

    fun setArchiveRedirectDomains(domains: List<String>) =
        mutator.setArchiveRedirectDomains(domains)

    fun setAppearanceBoolean(preference: AppearanceBooleanPreference, value: Boolean) =
        mutator.setAppearanceBoolean(preference, value)

    fun setTheme(value: String) = mutator.setTheme(value)
    fun setNighttimeTheme(value: String) = mutator.setNighttimeTheme(value)
    fun setFollowSystemTheme(value: Boolean) = mutator.setFollowSystem(value)
    fun setManualDarkTheme(value: Boolean) = mutator.setManualDark(value)
    fun setLightTheme(value: String) = mutator.setLightTheme(value)
    fun setDarkTheme(value: String) = mutator.setDarkTheme(value)
    fun setThemeAccent(value: String) = mutator.setAccentPreset(value)
    fun setThemePair(lightTheme: String, darkTheme: String) =
        mutator.setThemePair(lightTheme, darkTheme)

    fun setGeneralBoolean(preference: GeneralBooleanPreference, value: Boolean) =
        mutator.setGeneralBoolean(preference, value)

    fun setDebugBoolean(preference: DebugBooleanPreference, value: Boolean) =
        mutator.setDebugBoolean(preference, value)

    fun setStoriesToCache(value: Int) = reader.setStoriesToCache(value)
    fun applyWelcomePreset(expressive: Boolean) = mutator.applyWelcomePreset(expressive)

    fun setPaletteTint(mode: String?, strength: Int, colorfulness: Int, tone: Int): Boolean =
        mutator.setPaletteTint(mode, strength, colorfulness, tone)

    fun clearPaletteTint(): Boolean = mutator.clearPaletteTint()
}
