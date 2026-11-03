package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StoriesHeaderPillTest {
    @Test
    fun defaultsToOffAndUnknownStoredColorsFallBackToOff() {
        for (store in listOf(
            TestKeyValueStore(),
            TestKeyValueStore(mapOf(UserPreferenceKeys.STORIES_HEADER_PILL to "future-color")),
        )) {
            assertEquals(StoriesHeaderPill.OFF, AppSettingsRepository(store, emptyFlow()).snapshot().story.headerPill)
        }
    }

    @Test
    fun allPillColorsPersistAndReachStoryPresentation() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        for (pill in StoriesHeaderPill.entries) {
            repository.setStoriesHeaderPill(pill)
            val restored = AppSettingsRepository(store, emptyFlow()).snapshot().story
            assertEquals(pill, restored.headerPill)
            assertEquals(pill, StoryDisplaySettings.from(restored).headerPill)
        }
    }

    @Test
    fun pillSelectionSurvivesSettingsTransfer() {
        val source = TestKeyValueStore()
        AppSettingsRepository(source, emptyFlow()).setStoriesHeaderPill(StoriesHeaderPill.TERTIARY)
        val backup = SettingsTransfer(source, TestKeyValueStore()).export()
        val target = TestKeyValueStore()
        assertIs<SettingsImportResult.Imported>(SettingsTransfer(target, TestKeyValueStore()).import(backup))
        assertEquals(StoriesHeaderPill.TERTIARY, AppSettingsRepository(target, emptyFlow()).snapshot().story.headerPill)
    }
}
