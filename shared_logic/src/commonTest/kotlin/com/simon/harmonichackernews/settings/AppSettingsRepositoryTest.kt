package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class AppSettingsRepositoryTest {
    @Test
    fun updatesEmitInitialAndChangedTypedSnapshots() = runTest {
        val store = TestKeyValueStore()
        val changes = flow {
            emit(Unit)
            store.putBoolean(UserPreferenceKeys.SHOW_POINTS, false)
            emit(Unit)
        }
        val repository = AppSettingsRepository(store, changes)

        val snapshots = repository.updates.take(2).toList()

        assertTrue(snapshots.first().story.showPoints)
        assertFalse(snapshots.last().story.showPoints)
    }

    @Test
    fun editorAndReaderShareTheExistingPreferenceSchema() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())

        repository.setStoryBoolean(StoryBooleanPreference.COMPACT_VIEW, true)
        repository.setReadingBoolean(ReadingBooleanPreference.PREVIEW_X, true)
        repository.setAppearanceBoolean(AppearanceBooleanPreference.COMPACT_HEADER, true)
        repository.setStoryBoolean(StoryBooleanPreference.HIDE_JOBS, true)
        repository.setDebugBoolean(DebugBooleanPreference.SHOW_AI_SUMMARY_INFO, true)

        val snapshot = repository.snapshot()
        assertTrue(snapshot.story.compactView)
        assertTrue(snapshot.reading.previewX)
        assertTrue(snapshot.story.compactHeader)
        assertTrue(snapshot.story.hideJobs)
        assertTrue(snapshot.debug.showAiSummaryDebugInfo)
        assertEquals(true, store.getBoolean(UserPreferenceKeys.COMPACT_VIEW, false))
    }
}
