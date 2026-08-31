package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredUserSettingsTest {
    @Test
    fun themeSemanticsArePortable() {
        assertTrue(ThemePreferences.isAutomatic(ThemePreferences.DEFAULT))
        assertTrue(ThemePreferences.isAutomatic(ThemePreferences.MATERIAL_FIXED_AUTO))
        assertTrue(ThemePreferences.isDark(ThemePreferences.MATERIAL_FIXED_DARK))
        assertTrue(ThemePreferences.isDark("amoled"))
        assertEquals(
            ThemePreferences.MATERIAL_FIXED_AUTO,
            ThemePreferences.fixedMaterialEquivalent(ThemePreferences.DEFAULT),
        )
        assertEquals(
            ThemePreferences.MATERIAL_FIXED_LIGHT,
            ThemePreferences.fixedMaterialEquivalent("material_light"),
        )
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
        assertEquals(StoryPreviewMode.SMALL, settings.story.previewImageMode)
        assertEquals(TextPreferences.DEFAULT_STORY_TEXT_SIZE, settings.story.storyTextSize)
        assertEquals("Top Stories", settings.story.preferredStoryType)
        assertTrue(settings.comments.showHeaderPreviewImage)
        assertTrue(settings.comments.headerPreviewImageEnabled)
        assertTrue(settings.comments.headerTintEnabled)
        assertFalse(settings.comments.showUpButton)
        assertTrue(settings.comments.animateChanges)
        assertTrue(settings.comments.smoothScroll)
        assertFalse(settings.comments.preloadCommentsFromStories)
        assertEquals(WebViewPreloadMode.NEVER, settings.comments.commentsPreloadMode)
        assertTrue(settings.reading.integratedWebView)
        assertTrue(settings.reading.readerModeEnabled)
        assertEquals(18, settings.reading.readerModeFontSize)
        assertTrue(LinkPreviewType.GITHUB_REPOSITORY in settings.reading.enabledLinkPreviews)
        assertTrue(LinkPreviewType.HUGGING_FACE_MODEL in settings.reading.enabledLinkPreviews)
        assertTrue(LinkPreviewType.OPENROUTER_MODEL in settings.reading.enabledLinkPreviews)
        assertEquals(
            LinkPreviewType.entries.filterTo(linkedSetOf()) { it.defaultEnabled },
            settings.reading.enabledLinkPreviews,
        )
        assertEquals(20, settings.cache.storiesToCache)
        assertTrue(settings.general.bookmarksEnabled)
        assertEquals(ThemePreferences.DEFAULT, settings.appearance.theme)
        assertEquals(ThemePreferences.DEFAULT_NIGHTTIME, settings.appearance.nighttimeTheme)
        assertFalse(settings.debug.alwaysShowTapToRefresh)
    }

    @Test
    fun everyLinkPreviewTypeHasAnIndependentStoredSwitch() {
        assertEquals(
            LinkPreviewType.entries.size,
            LinkPreviewType.entries.map { it.preferenceKey }.toSet().size,
        )
        val disabled = LinkPreviewType.entries.associate { type ->
            type.preferenceKey to false
        }
        val settings = StoredUserSettings(TestKeyValueStore(disabled), emptyFlow())

        assertTrue(settings.reading.enabledLinkPreviews.isEmpty())
    }

    @Test
    fun commentUpButtonDefaultIsHostSpecificAndStoredChoiceWins() {
        val hostDefault = StoredUserSettings(
            store = TestKeyValueStore(),
            changes = emptyFlow(),
            showCommentsUpButtonByDefault = true,
        )
        val storedChoice = StoredUserSettings(
            store = TestKeyValueStore(
                mapOf(UserPreferenceKeys.COMMENTS_SHOW_UP_BUTTON to false),
            ),
            changes = emptyFlow(),
            showCommentsUpButtonByDefault = true,
        )

        assertTrue(hostDefault.comments.showUpButton)
        assertFalse(storedChoice.comments.showUpButton)
    }

    @Test
    fun commentsPreloadDefaultIsHostSpecificAndStoredChoiceWins() {
        val desktopDefault = StoredUserSettings(
            store = TestKeyValueStore(),
            changes = emptyFlow(),
            preloadCommentsFromStoriesByDefault = true,
        )
        val storedChoice = StoredUserSettings(
            store = TestKeyValueStore(
                mapOf(
                    UserPreferenceKeys.PRELOAD_COMMENTS_MODE to
                        WebViewPreloadMode.NEVER.storedValue,
                ),
            ),
            changes = emptyFlow(),
            preloadCommentsFromStoriesByDefault = true,
        )

        assertTrue(desktopDefault.comments.preloadCommentsFromStories)
        assertEquals(WebViewPreloadMode.ALWAYS, desktopDefault.comments.commentsPreloadMode)
        assertFalse(storedChoice.comments.preloadCommentsFromStories)
    }

    @Test
    fun malformedAndOutOfRangeValuesAreSanitized() {
        val store = TestKeyValueStore(
            mapOf(
                UserPreferenceKeys.STORY_TEXT_SIZE to "not-a-number",
                UserPreferenceKeys.COMMENT_TEXT_SIZE to "999",
                UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY to 500,
                UserPreferenceKeys.PRELOAD_COMMENTS_MODE to "unexpected",
                UserPreferenceKeys.PRELOAD_COMMENTS_MINIMUM_BATTERY to 500,
                UserPreferenceKeys.STORIES_TO_CACHE to 202,
                UserPreferenceKeys.FAVICON_PROVIDER to "unknown",
                UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE to "unexpected",
                UserPreferenceKeys.READER_MODE_FONT_SIZE to 500,
                UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS to
                    " https://Example.com/path, https://example.com ",
                UserPreferenceKeys.COMMENT_SORTING to "unsupported",
                UserPreferenceKeys.COMMENTS_PROVIDER to "unsupported",
                UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION to "unsupported",
                UserPreferenceKeys.READER_MODE_FONT to "unsupported",
            ),
        )
        val settings = StoredUserSettings(store, emptyFlow())

        assertEquals(TextPreferences.DEFAULT_STORY_TEXT_SIZE, settings.story.storyTextSize)
        assertEquals(TextPreferences.MAX_COMMENT_TEXT_SIZE, settings.comments.textSize)
        assertEquals(100, settings.reading.preloadWebViewMinimumBattery)
        assertEquals(WebViewPreloadMode.NEVER, settings.comments.commentsPreloadMode)
        assertEquals(100, settings.comments.preloadCommentsMinimumBattery)
        assertEquals(200, settings.cache.storiesToCache)
        assertEquals(FaviconPreferences.GOOGLE, settings.story.faviconProvider)
        assertEquals(StoryPreviewMode.SMALL, settings.story.previewImageMode)
        assertEquals(24, settings.reading.readerModeFontSize)
        assertEquals(listOf("example.com"), settings.reading.archiveRedirectDomains)
        assertEquals(CommentSortingPreference.DEFAULT, settings.comments.sortingPreference)
        assertEquals(CommentsProvider.ALGOLIA, settings.reading.commentsProvider)
        assertEquals(CommentVolumeNavigationMode.DISABLED, settings.comments.volumeNavigation)
        assertEquals(AppFont.GOOGLE_SANS_FLEX_ROUNDED, settings.reading.readerFont)
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
        assertTrue(settings.comments.headerPreviewImageEnabled)
        assertFalse(settings.comments.tintHeader)
        assertTrue(settings.comments.headerTintEnabled)
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
