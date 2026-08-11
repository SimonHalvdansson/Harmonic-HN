package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredUserSettingsTest {
    @Test
    fun themeSemanticsArePortable() {
        assertTrue(ThemePreferences.isAutomatic(ThemePreferences.DEFAULT))
        assertTrue(ThemePreferences.isDark("amoled"))
        assertEquals("gray", ThemePreferences.selectableNighttimeTheme("gray"))
        assertEquals(
            ThemePreferences.DEFAULT_NIGHTTIME,
            ThemePreferences.selectableNighttimeTheme("material_daynight"),
        )
    }

    @Test
    fun defaultsMatchExistingAndroidBehaviour() {
        val settings = StoredUserSettings(TestKeyValueStore(), emptyFlow())

        assertTrue(settings.story.showPoints)
        assertEquals("small", settings.story.previewImageMode)
        assertEquals(TextPreferences.DEFAULT_STORY_TEXT_SIZE, settings.story.storyTextSize)
        assertEquals("Top Stories", settings.story.preferredStoryType)
        assertTrue(settings.comments.showHeaderPreviewImage)
        assertTrue(settings.comments.animateChanges)
        assertTrue(settings.comments.smoothScroll)
        assertTrue(settings.reading.integratedWebView)
        assertTrue(settings.reading.readerModeEnabled)
        assertEquals(18, settings.reading.readerModeFontSize)
        assertTrue(settings.reading.previewGithub)
        assertEquals(20, settings.cache.storiesToCache)
        assertTrue(settings.general.bookmarksEnabled)
    }

    @Test
    fun malformedAndOutOfRangeValuesAreSanitized() {
        val store = TestKeyValueStore(
            mapOf(
                UserPreferenceKeys.STORY_TEXT_SIZE to "not-a-number",
                UserPreferenceKeys.COMMENT_TEXT_SIZE to "999",
                UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY to 500,
                UserPreferenceKeys.STORIES_TO_CACHE to 202,
                UserPreferenceKeys.FAVICON_PROVIDER to "unknown",
                UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE to "unexpected",
                UserPreferenceKeys.READER_MODE_FONT_SIZE to 500,
                UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS to
                    " https://Example.com/path, https://example.com ",
            ),
        )
        val settings = StoredUserSettings(store, emptyFlow())

        assertEquals(TextPreferences.DEFAULT_STORY_TEXT_SIZE, settings.story.storyTextSize)
        assertEquals(TextPreferences.MAX_COMMENT_TEXT_SIZE, settings.comments.textSize)
        assertEquals(100, settings.reading.preloadWebViewMinimumBattery)
        assertEquals(200, settings.cache.storiesToCache)
        assertEquals(FaviconPreferences.GOOGLE, settings.story.faviconProvider)
        assertEquals("small", settings.story.previewImageMode)
        assertEquals(24, settings.reading.readerModeFontSize)
        assertEquals(listOf("example.com"), settings.reading.archiveRedirectDomains)
    }

    @Test
    fun dependentPreferencesCannotEnableUnavailableFeatures() {
        val store = TestKeyValueStore(
            mapOf(
                UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE to "off",
                UserPreferenceKeys.COMMENTS_HEADER_PREVIEW_IMAGE to true,
                UserPreferenceKeys.TINT_CARD_USING_PREVIEW to false,
                UserPreferenceKeys.COMMENTS_HEADER_TINT to true,
                UserPreferenceKeys.READER_MODE_ENABLED to false,
                UserPreferenceKeys.READER_MODE_DEFAULT to true,
            ),
        )
        val settings = StoredUserSettings(store, emptyFlow())

        assertFalse(settings.comments.showHeaderPreviewImage)
        assertFalse(settings.comments.tintHeader)
        assertFalse(settings.reading.readerModeDefault)
    }

    @Test
    fun unavailableOrPrivateDefaultStoryTypesFallBackToTopStories() {
        val unavailableFrontpage = StoredUserSettings(
            TestKeyValueStore(
                mapOf(
                    UserPreferenceKeys.DEFAULT_STORY_TYPE to AdditionalFrontpagePreferences.FRONT,
                    UserPreferenceKeys.ADDITIONAL_FRONTPAGES to emptySet<String>(),
                ),
            ),
            emptyFlow(),
        )
        val bookmarks = StoredUserSettings(
            TestKeyValueStore(mapOf(UserPreferenceKeys.DEFAULT_STORY_TYPE to "Bookmarks")),
            emptyFlow(),
        )

        assertEquals("Top Stories", unavailableFrontpage.story.preferredStoryType)
        assertEquals("Top Stories", bookmarks.story.preferredStoryType)
    }
}
