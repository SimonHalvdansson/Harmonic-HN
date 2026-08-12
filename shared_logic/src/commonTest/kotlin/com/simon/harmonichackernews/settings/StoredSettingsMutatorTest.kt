package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredSettingsMutatorTest {
    @Test
    fun typedStoryAndReadingUpdatesPreserveExistingKeysAndSanitizeValues() {
        val store = TestKeyValueStore()
        val mutator = StoredSettingsMutator(store)

        mutator.setStoryBoolean(StoryBooleanPreference.SHOW_POINTS, false)
        mutator.setStoryString(StoryStringPreference.PREVIEW_IMAGE_MODE, "unsupported")
        mutator.setStoryTextSize(999f)
        mutator.setAdditionalFrontpages(setOf("Front", "unsupported"))
        mutator.setReadingBoolean(ReadingBooleanPreference.READER_MODE_DEFAULT, true)
        mutator.setReaderModeFontSize(999)

        assertFalse(store.getBoolean(UserPreferenceKeys.SHOW_POINTS, true))
        assertEquals(StoryPreviewPreferences.SMALL, store.getString(UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE))
        assertEquals("20.5", store.getString(UserPreferenceKeys.STORY_TEXT_SIZE))
        assertEquals(setOf(AdditionalFrontpagePreferences.FRONT), store.getStringSet(UserPreferenceKeys.ADDITIONAL_FRONTPAGES))
        assertTrue(store.getBoolean(UserPreferenceKeys.READER_MODE_DEFAULT, false))
        assertEquals(24, store.getInt(UserPreferenceKeys.READER_MODE_FONT_SIZE, -1))
    }

    @Test
    fun typedAppearanceAndCommentUpdatesExposeRawChoices() {
        val store = TestKeyValueStore()
        val mutator = StoredSettingsMutator(store)

        mutator.setCommentBoolean(CommentBooleanPreference.HEADER_TINT, false)
        mutator.setAppearanceBoolean(AppearanceBooleanPreference.SPECIAL_NIGHTTIME, true)
        mutator.setTheme("gray")
        mutator.setNighttimeTheme("material_daynight")
        mutator.setCommentSorting(CommentSortingPreference.NEWEST_FIRST)
        mutator.setCommentsProvider(CommentsProvider.OFFICIAL)
        mutator.setCommentsVolumeNavigation(CommentVolumeNavigationMode.ALL)
        mutator.setReaderModeFont(AppFont.GEORGIA)
        mutator.setWebViewPreload(WebViewPreloadMode.WIFI_ONLY, 45)

        val settings = StoredUserSettings(store, kotlinx.coroutines.flow.emptyFlow())
        assertFalse(settings.comments.headerTintEnabled)
        assertTrue(settings.general.specialNighttimeTheme)
        assertEquals("gray", settings.appearance.theme)
        assertEquals(ThemePreferences.DEFAULT_NIGHTTIME, settings.appearance.nighttimeTheme)
        assertEquals(CommentSortingPreference.NEWEST_FIRST, settings.comments.sortingPreference)
        assertEquals(CommentsProvider.OFFICIAL, settings.reading.commentsProvider)
        assertEquals(CommentVolumeNavigationMode.ALL, settings.comments.volumeNavigation)
        assertEquals(AppFont.GEORGIA, settings.reading.readerFont)
        assertEquals(WebViewPreloadMode.WIFI_ONLY, settings.reading.preloadMode)
    }

    @Test
    fun compoundUpdatesAreNormalizedInCommonCode() {
        val store = TestKeyValueStore()
        val mutator = StoredSettingsMutator(store)

        mutator.setWebViewPreload("unsupported", 500)
        mutator.setArchiveRedirectDomains(listOf("https://Example.com/path", "example.com"))
        mutator.setCommentsProvider("unsupported")
        mutator.setCommentsVolumeNavigation("unsupported")
        mutator.applyWelcomePreset(expressive = false)

        assertEquals(WebViewPreferences.PRELOAD_NEVER, store.getString(UserPreferenceKeys.PRELOAD_WEBVIEW))
        assertEquals(100, store.getInt(UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY, -1))
        assertEquals("example.com", store.getString(UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS))
        assertEquals("algolia", store.getString(UserPreferenceKeys.COMMENTS_PROVIDER))
        assertEquals("disabled", store.getString(UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION))
        assertEquals("productsans", store.getString(UserPreferenceKeys.FONT))
        assertEquals(StoryPreviewPreferences.OFF, store.getString(UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE))
    }

    @Test
    fun commentDepthModeKeepsLegacyBooleanInSync() {
        val store = TestKeyValueStore()
        val mutator = StoredSettingsMutator(store)

        mutator.setCommentDepthIndicatorMode(CommentDepthPreferences.MONOCHROME)
        assertEquals(
            CommentDepthPreferences.MONOCHROME,
            store.getString(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS),
        )
        assertTrue(store.getBoolean(UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH, false))

        mutator.setCommentDepthIndicatorMode("unsupported")
        assertEquals(
            CommentDepthPreferences.THEME_DEFAULT,
            store.getString(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS),
        )
        assertFalse(store.getBoolean(UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH, true))
    }

    @Test
    fun paletteWritesAreSanitizedAndReportCacheInvalidation() {
        val store = TestKeyValueStore()
        val mutator = StoredSettingsMutator(store)

        assertTrue(mutator.setPaletteTint("unknown", 400, -1, 80))
        assertEquals(PaletteTintPreferences.DEFAULT, store.getString(UserPreferenceKeys.PALETTE_TINT_MODE))
        assertEquals(200, store.getInt(UserPreferenceKeys.PALETTE_TINT_STRENGTH, -1))
        assertEquals(0, store.getInt(UserPreferenceKeys.PALETTE_TINT_COLORFULNESS, -1))
        assertEquals(20, store.getInt(UserPreferenceKeys.PALETTE_TINT_TONE, -1))
        assertFalse(mutator.setPaletteTint("unknown", 400, -1, 80))
        assertTrue(mutator.clearPaletteTint())
        assertFalse(mutator.clearPaletteTint())
    }
}
