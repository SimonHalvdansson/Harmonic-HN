package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommentsHeaderPreferencesTest {
    @Test fun headerTintAndFaviconAreIndependentAndSurviveBackup() {
        val source = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(source, emptyFlow())
        assertTrue(repository.snapshot().comments.showFavicons)
        repository.setStoryBoolean(StoryBooleanPreference.TINT_CARD_USING_PREVIEW, false)
        for (mode in CommentsHeaderTint.entries) {
            repository.setCommentsHeaderTint(mode)
            repository.setCommentBoolean(CommentBooleanPreference.HEADER_FAVICON, false)
            val destination = InMemoryKeyValueStore()
            SettingsTransfer(destination, InMemoryKeyValueStore()).import(SettingsTransfer(source, InMemoryKeyValueStore()).export())
            val settings = AppSettingsRepository(destination, emptyFlow()).snapshot()
            assertEquals(mode, settings.comments.headerTint)
            assertEquals(mode != CommentsHeaderTint.OFF, settings.comments.tintHeader)
            assertFalse(settings.comments.showFavicons)
            assertTrue(settings.story.showFavicons)
        }
        repository.setCommentsHeaderTint(CommentsHeaderTint.THEME)
        SettingsTransfer(source, InMemoryKeyValueStore()).import(
            """{"format":"harmonic-settings","version":1,"settings":{"pref_enable_comments_header_tint":false}}""",
        )
        assertEquals(CommentsHeaderTint.OFF, repository.snapshot().comments.headerTint)
    }

    @Test fun metaInfoMigratesLegacyChoicesAndRoundTripsAllModes() {
        val store = InMemoryKeyValueStore()
        store.putBoolean(UserPreferenceKeys.COMMENTS_COMBINE_HEADER_ACTIONS, true)
        val repository = AppSettingsRepository(store, emptyFlow())
        assertEquals(CommentsHeaderMetaInfo.BUTTONS, repository.snapshot().comments.headerMetaInfo)
        for (mode in CommentsHeaderMetaInfo.entries) {
            repository.setCommentsHeaderMetaInfo(mode)
            val backup = SettingsTransfer(store, InMemoryKeyValueStore()).export()
            val destination = InMemoryKeyValueStore()
            assertIs<SettingsImportResult.Imported>(SettingsTransfer(destination, InMemoryKeyValueStore()).import(backup))
            assertEquals(mode, AppSettingsRepository(destination, emptyFlow()).snapshot().comments.headerMetaInfo)
        }
        store.putString(UserPreferenceKeys.COMMENTS_HEADER_META_INFO, "future")
        assertEquals(CommentsHeaderMetaInfo.SMALL, repository.snapshot().comments.headerMetaInfo)
    }

    @Test fun oldBackupReplacesNewMetaInfoChoice() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setCommentsHeaderMetaInfo(CommentsHeaderMetaInfo.ON_IMAGE)
        val backup = """{"format":"harmonic-settings","version":1,"settings":{"pref_comments_combine_header_author":true}}"""
        assertIs<SettingsImportResult.Imported>(SettingsTransfer(store, InMemoryKeyValueStore()).import(backup))
        assertEquals(CommentsHeaderMetaInfo.BUTTONS, repository.snapshot().comments.headerMetaInfo)
    }

    @Test fun resettingMetaInfoDoesNotReviveTheLegacySwitch() {
        val store = InMemoryKeyValueStore()
        store.putBoolean(UserPreferenceKeys.COMMENTS_COMBINE_HEADER_ACTIONS, true)
        val backup = """{"format":"harmonic-settings","version":1,"settings":{"pref_comments_header_meta_info":null}}"""
        val result = SettingsTransfer(store, InMemoryKeyValueStore()).import(backup)
        assertEquals(SettingsImportResult.Imported(1, 0), result)
        assertEquals(CommentsHeaderMetaInfo.SMALL, AppSettingsRepository(store, emptyFlow()).snapshot().comments.headerMetaInfo)
    }

    @Test fun unavailableMetaLayoutsFallBackWithoutLosingThePreference() {
        assertEquals(CommentsHeaderMetaInfo.SMALL, CommentsHeaderMetaInfo.BUTTONS.resolve(false, true))
        for (signedIn in listOf(true, false)) {
            assertEquals(CommentsHeaderMetaInfo.SMALL, CommentsHeaderMetaInfo.ON_IMAGE.resolve(signedIn, false))
            assertEquals(CommentsHeaderMetaInfo.ON_IMAGE, CommentsHeaderMetaInfo.ON_IMAGE.resolve(signedIn, true))
        }
    }

    @Test
    fun imageChoicesReuseExistingPreferencesAndHardEdgeMigratesToRounded() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        for (mode in CommentsHeaderImage.entries) {
            repository.setCommentsHeaderImage(mode)
            val restored = AppSettingsRepository(store, emptyFlow()).snapshot().comments
            assertEquals(mode, CommentsHeaderImage.from(restored.headerPreviewImageEnabled, restored.headerLayout))
        }
        store.putString(UserPreferenceKeys.COMMENTS_HEADER_EDGE, "hard")
        assertEquals(CommentsHeaderEdge.ROUNDED, repository.snapshot().comments.headerEdge)
    }

    @Test
    fun existingAndUnknownPreferencesKeepTheOriginalHeader() {
        for (stored in listOf(null, "future-layout")) {
            val store = InMemoryKeyValueStore()
            if (stored != null) {
                store.putString(UserPreferenceKeys.COMMENTS_HEADER_LAYOUT, stored)
                store.putString(UserPreferenceKeys.COMMENTS_HEADER_EDGE, stored)
            }
            val comments = AppSettingsRepository(store, emptyFlow()).snapshot().comments
            assertEquals(CommentsHeaderLayout.STANDARD, comments.headerLayout)
            assertEquals(CommentsHeaderEdge.FADE, comments.headerEdge)
            assertEquals(CommentsHeaderMetaInfo.SMALL, comments.headerMetaInfo)
        }
    }

    @Test
    fun optionsSurviveRecreationAndDisabledTintAndImages() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setCommentsHeaderLayout(CommentsHeaderLayout.BALANCED)
        repository.setCommentsHeaderEdge(CommentsHeaderEdge.SHADOW)
        repository.setCommentBoolean(CommentBooleanPreference.COMBINE_HEADER_ACTIONS, true)
        repository.setCommentBoolean(CommentBooleanPreference.HEADER_TINT, false)
        repository.setCommentBoolean(CommentBooleanPreference.HEADER_PREVIEW_IMAGE, false)
        val restored = AppSettingsRepository(store, emptyFlow())
        val display = CommentDisplaySettings.from(restored.snapshot().comments, false, false, false, false)
        assertEquals(CommentsHeaderLayout.BALANCED, display.headerLayout)
        assertEquals(CommentsHeaderEdge.SHADOW, display.headerEdge)
        assertTrue(display.combineHeaderActions)
        assertFalse(display.tintHeader)
        assertFalse(display.showHeaderPreviewImage)
        restored.setCommentBoolean(CommentBooleanPreference.HEADER_TINT, true)
        assertEquals(CommentsHeaderEdge.SHADOW, restored.snapshot().comments.headerEdge)
        assertTrue(restored.snapshot().comments.tintHeader)
    }

    @Test
    fun headerOptionsRoundTripInSettingsBackups() {
        val source = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(source, emptyFlow())
        repository.setCommentsHeaderLayout(CommentsHeaderLayout.BALANCED)
        repository.setCommentsHeaderEdge(CommentsHeaderEdge.ROUNDED)
        repository.setCommentBoolean(CommentBooleanPreference.COMBINE_HEADER_ACTIONS, true)
        val backup = SettingsTransfer(source, InMemoryKeyValueStore()).export()
        val destination = InMemoryKeyValueStore()
        assertIs<SettingsImportResult.Imported>(SettingsTransfer(destination, InMemoryKeyValueStore()).import(backup))
        val comments = AppSettingsRepository(destination, emptyFlow()).snapshot().comments
        assertEquals(CommentsHeaderLayout.BALANCED, comments.headerLayout)
        assertEquals(CommentsHeaderEdge.ROUNDED, comments.headerEdge)
        assertEquals(CommentsHeaderMetaInfo.BUTTONS, comments.headerMetaInfo)
    }
}
