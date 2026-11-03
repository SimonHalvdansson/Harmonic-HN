package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CategoryTagLocationTest {
    @Test fun selectionPersistsTransfersAndResetsWithoutChangingTheDefault() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        assertEquals(CategoryTagLocation.TITLE, repository.snapshot().story.categoryTagLocation)
        repository.setStoryString(StoryStringPreference.CATEGORY_TAG_LOCATION, "metadata")
        assertEquals(CategoryTagLocation.METADATA, StoryDisplaySettings.from(repository.snapshot().story).categoryTagLocation)
        val restored = TestKeyValueStore()
        assertIs<SettingsImportResult.Imported>(SettingsTransfer(restored, TestKeyValueStore()).import(SettingsTransfer(store, TestKeyValueStore()).export()))
        assertEquals(CategoryTagLocation.METADATA, AppSettingsRepository(restored, emptyFlow()).snapshot().story.categoryTagLocation)
        repository.resetStoryLayout()
        assertEquals(CategoryTagLocation.TITLE, repository.snapshot().story.categoryTagLocation)
        repository.setStoryString(StoryStringPreference.CATEGORY_TAG_LOCATION, "future")
        assertEquals(CategoryTagLocation.TITLE, repository.snapshot().story.categoryTagLocation)
    }

    @Test fun activeTagObserversReceiveColorEditsAndRemovalWithoutAStoryReload() = runTest {
        val repository = UserTagsRepository(TestKeyValueStore())
        val added = async(start = CoroutineStart.UNDISPATCHED) { repository.updates.drop(1).first() }
        repository.setTag("Alice", "Maintainer", UserTagColor.Purple)
        assertEquals("Maintainer", added.await().tags["Alice"])
        val recolored = async(start = CoroutineStart.UNDISPATCHED) { repository.updates.drop(1).first() }
        repository.setTag("Alice", "Maintainer", UserTagColor.Green)
        assertEquals(UserTagColor.Green, recolored.await().colors["Alice"])
        val removed = async(start = CoroutineStart.UNDISPATCHED) { repository.updates.drop(1).first() }
        repository.setTag("Alice", "")
        assertTrue(removed.await().tags.isEmpty())
    }
}
