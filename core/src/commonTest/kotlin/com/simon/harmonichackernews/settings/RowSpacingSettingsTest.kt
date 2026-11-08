package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RowSpacingSettingsTest {
    @Test fun spacingAndDividersPersistIndependentlyAndReachDisplaySettings() {
        val store = InMemoryKeyValueStore()
        fun repository() = AppSettingsRepository(store, store.changes)
        store.putBoolean(UserPreferenceKeys.COMMENT_DIVIDERS, true)
        assertEquals(100, repository().snapshot().story.spacing)
        assertEquals(100, repository().snapshot().comments.spacing)
        assertFalse(repository().snapshot().story.showDividers)
        assertTrue(repository().snapshot().comments.showDividers)

        repository().setStorySpacing(25)
        repository().setCommentSpacing(150)
        repository().setStoryBoolean(StoryBooleanPreference.SHOW_DIVIDERS, true)
        val snapshot = repository().snapshot()
        assertEquals(25, StoryDisplaySettings.from(snapshot.story).spacing)
        assertTrue(StoryDisplaySettings.from(snapshot.story).showDividers)
        assertEquals(150, CommentDisplaySettings.from(snapshot.comments, false, false, false, false).spacing)

        repository().resetStoryLayout()
        assertEquals(100, repository().snapshot().story.spacing)
        assertFalse(repository().snapshot().story.showDividers)
        assertEquals(150, repository().snapshot().comments.spacing)
        assertTrue(repository().snapshot().comments.showDividers)
    }

    @Test fun outOfRangeWritesAndStoredValuesAreClamped() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        repository.setStorySpacing(Int.MIN_VALUE)
        repository.setCommentSpacing(Int.MAX_VALUE)
        assertEquals(25, repository.snapshot().story.spacing)
        assertEquals(175, repository.snapshot().comments.spacing)
        store.putInt(UserPreferenceKeys.STORY_SPACING, Int.MAX_VALUE)
        store.putInt(UserPreferenceKeys.COMMENT_SPACING, Int.MIN_VALUE)
        assertEquals(175, repository.snapshot().story.spacing)
        assertEquals(25, repository.snapshot().comments.spacing)
    }

    @Test fun backupRestoresSpacingAndBothDividerChoices() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val transfer = SettingsTransfer(store, InMemoryKeyValueStore())
        repository.setStorySpacing(50)
        repository.setCommentSpacing(125)
        repository.setStoryBoolean(StoryBooleanPreference.SHOW_DIVIDERS, true)
        repository.setCommentBoolean(CommentBooleanPreference.SHOW_DIVIDERS, true)
        val backup = transfer.export()
        repository.setStorySpacing(100)
        repository.setCommentSpacing(100)
        repository.setStoryBoolean(StoryBooleanPreference.SHOW_DIVIDERS, false)
        repository.setCommentBoolean(CommentBooleanPreference.SHOW_DIVIDERS, false)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup))
        assertEquals(50, repository.snapshot().story.spacing)
        assertEquals(125, repository.snapshot().comments.spacing)
        assertTrue(repository.snapshot().story.showDividers)
        assertTrue(repository.snapshot().comments.showDividers)
    }
}
