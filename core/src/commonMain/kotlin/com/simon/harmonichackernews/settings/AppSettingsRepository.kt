package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Typed story toggles accepted by [StoredSettingsMutator]. */
enum class StoryBooleanPreference {
    BORDERLESS_LARGE_IMAGE,
    TINT_CARD_USING_PREVIEW,
    COMPACT_VIEW,
    SHOW_SUMMARY,
    SHOW_THUMBNAILS,
    SHOW_POINTS,
    COMPACT_POINTS,
    INCLUDE_TOP_LEVEL_DOMAIN,
    SHOW_COMMENTS_COUNT,
    SHOW_INDEX,
    LEFT_ALIGN,
    ALWAYS_OPEN_COMMENTS,
    PAGINATION,
    HIDE_CLICKED,
    GRAY_OUT_CLICKED,
    HIDE_JOBS,
}

enum class StoryStringPreference { PREVIEW_IMAGE_MODE, DISPLAY_STYLE }

enum class CommentBooleanPreference {
    CARD_BORDER,
    COLLECT_REFERENCE_LINKS,
    HIGHLIGHT_METADATA,
    SHOW_DIVIDERS,
    TOP_LEVEL_DEPTH_INDICATOR,
    SHOW_SCROLLBAR,
    ANIMATE_CHANGES,
    HEADER_TINT,
    HEADER_PREVIEW_IMAGE,
    SHOW_UP_BUTTON,
    COLLAPSE_PARENT,
    COLLAPSE_TOP_LEVEL,
    HIDE_DELAYED_COMMENTS,
    PRELOAD_COMMENTS_FROM_STORIES,
    SWAP_LONG_PRESS_TAP,
    SHOW_NAVIGATION_BUTTONS,
    SMOOTH_SCROLL,
}

enum class ReadingBooleanPreference {
    INTEGRATED_WEB_VIEW,
    CLOSE_WEB_VIEW_ON_BACK,
    MATCH_WEB_VIEW_THEME,
    BLOCK_ADS,
    READER_MODE_ENABLED,
    READER_MODE_DEFAULT,
    EXTERNAL_BROWSER,
    REDIRECT_NITTER,
}

enum class AppearanceBooleanPreference { SPECIAL_NIGHTTIME, TRANSPARENT_STATUS_BAR, COMPACT_HEADER }

enum class GeneralBooleanPreference { BOOKMARKS_ENABLED, SHOW_CHANGELOG }

enum class DebugBooleanPreference { ALWAYS_SHOW_TAP_TO_REFRESH }

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
