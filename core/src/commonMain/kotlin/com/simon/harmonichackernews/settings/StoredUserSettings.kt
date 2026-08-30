package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import kotlinx.coroutines.flow.Flow

/** Preference keys consumed by common settings snapshots. */
object UserPreferenceKeys {
    const val SHOW_POINTS = "pref_show_points"
    const val COMPACT_POINTS = "pref_compact_points"
    const val INCLUDE_TOP_LEVEL_DOMAIN = "pref_include_top_level_domain"
    const val SHOW_COMMENTS_COUNT = "pref_show_comments_count"
    const val COMPACT_VIEW = "pref_compact_view"
    const val THUMBNAILS = "pref_thumbnails"
    const val STORY_PREVIEW_IMAGE_MODE = "pref_story_preview_image_mode"
    const val STORY_PREVIEW_IMAGE_BORDERLESS = "pref_story_preview_image_borderless"
    const val SHOW_STORY_SUMMARY = "pref_show_story_summary"
    const val STORY_TEXT_SIZE = "pref_story_text_size"
    const val COMMENT_TEXT_SIZE = "pref_comment_text_size"
    const val SHOW_INDEX = "pref_show_index"
    const val COMPACT_HEADER = "pref_compact_header"
    const val LEFT_ALIGN = "pref_left_align"
    const val STORY_DISPLAY_STYLE = "pref_story_display_style"
    const val COMMENT_DISPLAY_STYLE = "pref_comment_display_style"
    const val TINT_CARD_USING_PREVIEW = "pref_tint_card_using_preview"
    const val PALETTE_TINT_MODE = "pref_palette_tint_mode"
    const val PALETTE_TINT_STRENGTH = "pref_palette_tint_strength"
    const val PALETTE_TINT_COLORFULNESS = "pref_palette_tint_colorfulness"
    const val PALETTE_TINT_TONE = "pref_palette_tint_tone"
    const val GRAY_OUT_CLICKED = "pref_gray_out_clicked"
    const val HOTNESS = "pref_hotness"
    const val FAVICON_PROVIDER = "pref_favicon_provider"
    const val FONT = "pref_font"
    const val HIDE_JOBS = "pref_hide_jobs"
    const val HIDE_CLICKED = "pref_hide_clicked"
    const val ALWAYS_OPEN_COMMENTS = "pref_always_open_comments"
    const val PAGINATION_MODE = "pref_pagination_mode"
    const val ALWAYS_SHOW_TAP_TO_REFRESH = "pref_always_show_tap_to_refresh"
    const val DEFAULT_STORY_TYPE = "pref_default_story_type"
    const val ADDITIONAL_FRONTPAGES = "pref_additional_frontpages"

    const val COLLAPSE_PARENT = "pref_collapse_parent"
    const val COMMENTS_HEADER_PREVIEW_IMAGE = "pref_enable_comments_header_preview_image"
    const val COMMENTS_HEADER_TINT = "pref_enable_comments_header_tint"
    const val COMMENTS_SHOW_UP_BUTTON = "pref_comments_show_up_button"
    const val COMMENT_DEPTH_INDICATORS = "pref_comment_depth_indicators"
    const val MONOCHROME_COMMENT_DEPTH = "pref_monochrome_comment_depth"
    const val SCROLL_NAVIGATION = "pref_scroll_navigation"
    const val TOP_LEVEL_THREAD_INDICATORS = "pref_top_level_thread_indicators"
    const val COMMENTS_SWAP_LONG = "pref_comments_swap_long"
    const val COMMENT_CARD_BORDER = "pref_comment_card_border"
    const val COMMENT_DIVIDERS = "pref_comment_dividers"
    const val HIGHLIGHT_COMMENT_META = "pref_highlight_comment_meta"
    const val COLLECT_LINKS_IN_COMMENTS = "pref_collect_links_in_comments"
    const val COLLAPSE_TOP_LEVEL = "pref_collapse_top_level"
    const val HIDE_DELAYED_COMMENTS = "pref_hide_delayed_comments"
    const val PRELOAD_COMMENTS_FROM_STORIES = "pref_preload_comments_from_stories"
    const val COMMENT_SORTING = "pref_comment_sorting"
    const val COMMENTS_SCROLLBAR = "pref_comments_scrollbar"
    const val COMMENTS_ANIMATION = "pref_comments_animation"
    const val COMMENTS_SMOOTH_SCROLL = "pref_comments_animation_navigation"
    const val COMMENTS_VOLUME_NAVIGATION = "pref_comments_volume_navigation"

    const val WEBVIEW = "pref_webview"
    const val PRELOAD_WEBVIEW = "pref_preload_webview"
    const val PRELOAD_WEBVIEW_MINIMUM_BATTERY = "pref_preload_webview_minimum_battery"
    const val WEBVIEW_MATCH_THEME = "pref_webview_match_theme"
    const val READER_MODE_ENABLED = "pref_webview_reader_mode_enabled"
    const val READER_MODE_DEFAULT = "pref_webview_reader_mode_default"
    const val WEBVIEW_ADBLOCK = "pref_webview_adblock"
    const val CLOSE_WEBVIEW_ON_BACK = "pref_close_webview_on_back"
    const val COMMENTS_PROVIDER = "pref_comments_provider"
    const val STORIES_TO_CACHE = "pref_stories_to_cache"
    const val READER_MODE_FONT = "pref_webview_reader_mode_font"
    const val READER_MODE_FONT_SIZE = "pref_webview_reader_mode_font_size"
    const val EXTERNAL_BROWSER = "pref_external_browser"
    const val REDIRECT_NITTER = "pref_redirect_nitter"
    const val ARCHIVE_REDIRECT_DOMAINS = "pref_archive_redirect_domains"
    const val BOOKMARKS_ENABLED = "pref_bookmarks_enabled"
    const val TRANSPARENT_STATUS_BAR = "pref_transparent_status_bar"
    const val SPECIAL_NIGHTTIME = "pref_special_nighttime"
    const val SHOW_CHANGELOG = "pref_show_changelog"
}

/**
 * Common interpretation of the app's persisted settings.
 *
 * Android supplies the key/value adapter, change flow and current theme; every default, clamp and
 * cross-preference rule below is reusable by later platforms.
 */
class StoredUserSettings(
    private val store: KeyValueStore,
    override val changes: Flow<Unit>,
    private val theme: () -> String? = { null },
    private val showCommentsUpButtonByDefault: Boolean = false,
    private val preloadCommentsFromStoriesByDefault: Boolean = false,
) : UserSettings {
    override val story: StoryPreferences
        get() {
            val paletteConfig = paletteTintConfigKey()
            return StoryPreferences(
                showPoints = boolean(UserPreferenceKeys.SHOW_POINTS, true),
                compactPoints = boolean(UserPreferenceKeys.COMPACT_POINTS, false),
                includeTopLevelDomain =
                    boolean(UserPreferenceKeys.INCLUDE_TOP_LEVEL_DOMAIN, true),
                showCommentsCount = boolean(UserPreferenceKeys.SHOW_COMMENTS_COUNT, true),
                compactView = boolean(UserPreferenceKeys.COMPACT_VIEW, false),
                thumbnails = boolean(UserPreferenceKeys.THUMBNAILS, true),
                previewImageMode = previewImageMode(),
                borderlessLargePreviewImage =
                    boolean(UserPreferenceKeys.STORY_PREVIEW_IMAGE_BORDERLESS, false),
                showSummary = boolean(UserPreferenceKeys.SHOW_STORY_SUMMARY, false),
                storyTextSize = storyTextSize(),
                commentTextSize = commentTextSize(),
                showIndex = boolean(UserPreferenceKeys.SHOW_INDEX, true),
                compactHeader = boolean(UserPreferenceKeys.COMPACT_HEADER, false),
                leftAlign = boolean(UserPreferenceKeys.LEFT_ALIGN, false),
                cardStyle = string(UserPreferenceKeys.STORY_DISPLAY_STYLE, STANDARD) == CARD,
                tintCardUsingPreview =
                    boolean(UserPreferenceKeys.TINT_CARD_USING_PREVIEW, true),
                paletteTintConfigKey = paletteConfig,
                grayOutClicked = boolean(UserPreferenceKeys.GRAY_OUT_CLICKED, true),
                hotness = string(UserPreferenceKeys.HOTNESS, "-1").toIntOrNull() ?: -1,
                faviconProvider = FaviconPreferences.sanitizeProvider(
                    string(UserPreferenceKeys.FAVICON_PROVIDER, FaviconPreferences.GOOGLE),
                ),
                font = preferredFont(),
                hideJobs = boolean(UserPreferenceKeys.HIDE_JOBS, false),
                hideClicked = boolean(UserPreferenceKeys.HIDE_CLICKED, false),
                alwaysOpenComments = boolean(UserPreferenceKeys.ALWAYS_OPEN_COMMENTS, false),
                pagination = boolean(UserPreferenceKeys.PAGINATION_MODE, false),
                alwaysShowTapToRefresh =
                    boolean(UserPreferenceKeys.ALWAYS_SHOW_TAP_TO_REFRESH, false),
                preferredStoryType = preferredStoryType(),
                additionalFrontpages = AdditionalFrontpagePreferences.sanitize(
                    runCatching {
                        store.getStringSet(UserPreferenceKeys.ADDITIONAL_FRONTPAGES)
                    }.getOrDefault(emptySet()),
                ),
            )
        }

    override val comments: CommentPreferences
        get() {
            val tintCard = boolean(UserPreferenceKeys.TINT_CARD_USING_PREVIEW, true)
            val headerPreviewImageEnabled =
                boolean(UserPreferenceKeys.COMMENTS_HEADER_PREVIEW_IMAGE, true)
            val headerTintEnabled = boolean(UserPreferenceKeys.COMMENTS_HEADER_TINT, true)
            return CommentPreferences(
                collapseParent = boolean(UserPreferenceKeys.COLLAPSE_PARENT, false),
                thumbnails = boolean(UserPreferenceKeys.THUMBNAILS, true),
                headerPreviewImageEnabled = headerPreviewImageEnabled,
                showHeaderPreviewImage = previewImageMode() != StoryPreviewPreferences.OFF &&
                    headerPreviewImageEnabled,
                headerTintEnabled = headerTintEnabled,
                tintHeader = tintCard && headerTintEnabled,
                showUpButton = boolean(
                    UserPreferenceKeys.COMMENTS_SHOW_UP_BUTTON,
                    showCommentsUpButtonByDefault,
                ),
                paletteTintConfigKey = paletteTintConfigKey(),
                textSize = commentTextSize(),
                depthIndicatorMode = commentDepthMode(),
                showNavigationButtons = boolean(UserPreferenceKeys.SCROLL_NAVIGATION, false),
                font = preferredFont(),
                showTopLevelDepthIndicator =
                    boolean(UserPreferenceKeys.TOP_LEVEL_THREAD_INDICATORS, false),
                theme = theme(),
                faviconProvider = FaviconPreferences.sanitizeProvider(
                    string(UserPreferenceKeys.FAVICON_PROVIDER, FaviconPreferences.GOOGLE),
                ),
                swapLongPressTap = boolean(UserPreferenceKeys.COMMENTS_SWAP_LONG, false),
                cardStyle = string(UserPreferenceKeys.COMMENT_DISPLAY_STYLE, STANDARD) == CARD,
                cardBorder = boolean(UserPreferenceKeys.COMMENT_CARD_BORDER, true),
                showDividers = boolean(UserPreferenceKeys.COMMENT_DIVIDERS, false),
                highlightMetadata = boolean(UserPreferenceKeys.HIGHLIGHT_COMMENT_META, false),
                collectReferenceLinks =
                    boolean(UserPreferenceKeys.COLLECT_LINKS_IN_COMMENTS, true),
                collapseTopLevel = boolean(UserPreferenceKeys.COLLAPSE_TOP_LEVEL, false),
                hideDelayedComments = boolean(UserPreferenceKeys.HIDE_DELAYED_COMMENTS, false),
                preloadCommentsFromStories = boolean(
                    UserPreferenceKeys.PRELOAD_COMMENTS_FROM_STORIES,
                    preloadCommentsFromStoriesByDefault,
                ),
                sorting = CommentSortingPreference.fromStored(
                    string(UserPreferenceKeys.COMMENT_SORTING, CommentSortingPreference.DEFAULT.storedValue),
                ).storedValue,
                showScrollbar = boolean(UserPreferenceKeys.COMMENTS_SCROLLBAR, false),
                animateChanges = boolean(UserPreferenceKeys.COMMENTS_ANIMATION, true),
                smoothScroll = boolean(UserPreferenceKeys.COMMENTS_SMOOTH_SCROLL, true),
                volumeNavigationMode = CommentVolumeNavigationMode.fromStored(
                    string(
                        UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION,
                        CommentVolumeNavigationMode.DISABLED.storedValue,
                    ),
                ).storedValue,
            )
        }

    override val reading: ReadingPreferences
        get() {
            val readerModeEnabled = boolean(UserPreferenceKeys.READER_MODE_ENABLED, true)
            return ReadingPreferences(
                integratedWebView = boolean(UserPreferenceKeys.WEBVIEW, true),
                preloadWebViewMode = WebViewPreferences.sanitizePreloadMode(
                    string(UserPreferenceKeys.PRELOAD_WEBVIEW, WebViewPreferences.PRELOAD_NEVER),
                ),
                preloadWebViewMinimumBattery = WebViewPreferences.clampBatteryPercent(
                    integer(UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY, 0),
                ),
                matchWebViewTheme = boolean(UserPreferenceKeys.WEBVIEW_MATCH_THEME, false),
                readerModeEnabled = readerModeEnabled,
                readerModeDefault = readerModeEnabled &&
                    boolean(UserPreferenceKeys.READER_MODE_DEFAULT, false),
                blockAds = boolean(UserPreferenceKeys.WEBVIEW_ADBLOCK, false),
                closeWebViewOnBack = boolean(UserPreferenceKeys.CLOSE_WEBVIEW_ON_BACK, false),
                useAlgoliaApi = CommentsProvider.fromStored(
                    string(UserPreferenceKeys.COMMENTS_PROVIDER, CommentsProvider.ALGOLIA.storedValue),
                ) == CommentsProvider.ALGOLIA,
                readerModeFont = TextPreferences.sanitizeFont(
                    string(UserPreferenceKeys.READER_MODE_FONT, "googlesansflexrounded"),
                ),
                readerModeFontSize = TextPreferences.clampReaderModeFontSize(
                    integer(UserPreferenceKeys.READER_MODE_FONT_SIZE, 18),
                ),
                externalBrowser = boolean(UserPreferenceKeys.EXTERNAL_BROWSER, false),
                redirectNitter = boolean(UserPreferenceKeys.REDIRECT_NITTER, false),
                archiveRedirectDomains = ArchiveRedirectPolicy.parseDomains(
                    string(UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS, ""),
                ),
                enabledLinkPreviews = LinkPreviewType.entries.filterTo(linkedSetOf()) { type ->
                    boolean(type.preferenceKey, type.defaultEnabled)
                },
            )
        }

    override val cache: CachePreferences
        get() = CachePreferences(
            storiesToCache = StoryCachePreferences.sanitizeCount(
                integer(UserPreferenceKeys.STORIES_TO_CACHE, StoryCachePreferences.DEFAULT_COUNT),
            ),
            cacheArticleSnapshots = boolean(UserPreferenceKeys.WEBVIEW, true),
        )

    override val general: GeneralPreferences
        get() = GeneralPreferences(
            bookmarksEnabled = boolean(UserPreferenceKeys.BOOKMARKS_ENABLED, true),
            transparentStatusBar = boolean(UserPreferenceKeys.TRANSPARENT_STATUS_BAR, false),
            specialNighttimeTheme = boolean(UserPreferenceKeys.SPECIAL_NIGHTTIME, false),
            showChangelog = boolean(UserPreferenceKeys.SHOW_CHANGELOG, true),
        )

    override val appearance: AppearancePreferences
        get() = AppearancePreferences(
            theme = string(ThemePreferences.KEY, ThemePreferences.DEFAULT),
            nighttimeTheme = ThemePreferences.selectableNighttimeTheme(
                string(ThemePreferences.NIGHTTIME_KEY, ThemePreferences.DEFAULT_NIGHTTIME),
            ),
        )

    override val debug: DebugPreferences
        get() = DebugPreferences(
            alwaysShowTapToRefresh = boolean(UserPreferenceKeys.ALWAYS_SHOW_TAP_TO_REFRESH, false),
        )

    override fun setStoriesToCache(count: Int) {
        store.putInt(
            UserPreferenceKeys.STORIES_TO_CACHE,
            StoryCachePreferences.sanitizeCount(count),
        )
    }

    private fun paletteTintConfigKey(): String = PaletteTintPreferences.configKey(
        string(UserPreferenceKeys.PALETTE_TINT_MODE, PaletteTintPreferences.DEFAULT),
        integer(UserPreferenceKeys.PALETTE_TINT_STRENGTH, PaletteTintPreferences.DEFAULT_STRENGTH),
        integer(
            UserPreferenceKeys.PALETTE_TINT_COLORFULNESS,
            PaletteTintPreferences.DEFAULT_COLORFULNESS,
        ),
        integer(UserPreferenceKeys.PALETTE_TINT_TONE, PaletteTintPreferences.DEFAULT_TONE),
    )

    private fun previewImageMode(): String = StoryPreviewPreferences.sanitize(
        string(UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE, StoryPreviewPreferences.SMALL),
    )

    private fun storyTextSize(): Float = TextPreferences.clampStoryTextSize(
        numericPreference(UserPreferenceKeys.STORY_TEXT_SIZE, TextPreferences.DEFAULT_STORY_TEXT_SIZE),
    )

    private fun commentTextSize(): Float = TextPreferences.clampCommentTextSize(
        numericPreference(
            UserPreferenceKeys.COMMENT_TEXT_SIZE,
            TextPreferences.DEFAULT_COMMENT_TEXT_SIZE,
        ),
    )

    private fun preferredFont(): String = if (theme() == "hacker") {
        "jetbrainsmono"
    } else {
        TextPreferences.sanitizeFont(string(UserPreferenceKeys.FONT, "googlesansflexrounded"))
    }

    private fun commentDepthMode(): String {
        if (store.contains(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS)) {
            return CommentDepthPreferences.sanitizeMode(
                string(
                    UserPreferenceKeys.COMMENT_DEPTH_INDICATORS,
                    CommentDepthPreferences.THEME_DEFAULT,
                ),
            )
        }
        return if (boolean(UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH, false)) {
            CommentDepthPreferences.MONOCHROME
        } else {
            CommentDepthPreferences.THEME_DEFAULT
        }
    }

    private fun preferredStoryType(): String {
        val preferred = string(UserPreferenceKeys.DEFAULT_STORY_TYPE, TOP_STORIES)
        val enabled = AdditionalFrontpagePreferences.sanitize(
            runCatching { store.getStringSet(UserPreferenceKeys.ADDITIONAL_FRONTPAGES) }
                .getOrDefault(emptySet()),
        )
        return if (
            preferred == "Bookmarks" ||
            preferred == "History" ||
            (AdditionalFrontpagePreferences.isLabel(preferred) && preferred !in enabled)
        ) {
            TOP_STORIES
        } else {
            preferred
        }
    }

    private fun numericPreference(key: String, default: Float): Float {
        val stringValue = runCatching { store.getString(key) }.getOrNull()
        if (stringValue != null) return stringValue.toFloatOrNull() ?: default
        return runCatching { store.getFloat(key, default) }
            .recoverCatching { store.getInt(key, default.toInt()).toFloat() }
            .getOrDefault(default)
    }

    private fun string(key: String, default: String): String =
        runCatching { store.getString(key, default) }.getOrNull() ?: default

    private fun boolean(key: String, default: Boolean): Boolean =
        runCatching { store.getBoolean(key, default) }.getOrDefault(default)

    private fun integer(key: String, default: Int): Int =
        runCatching { store.getInt(key, default) }.getOrDefault(default)

    private companion object {
        const val STANDARD = "standard"
        const val CARD = "card"
        const val TOP_STORIES = "Top Stories"
    }
}
