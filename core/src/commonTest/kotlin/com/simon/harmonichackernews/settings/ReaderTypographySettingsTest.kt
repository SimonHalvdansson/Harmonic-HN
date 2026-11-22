package com.simon.harmonichackernews.settings

import kotlin.test.*

class ReaderTypographySettingsTest {
    @Test fun naturalVoiceIsPreferredUntilTheUserChoosesSystem() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        assertTrue(repository.snapshot().reading.preferNaturalVoice)
        repository.setReadingBoolean(ReadingBooleanPreference.PREFER_NATURAL_VOICE, false)
        val transfer = SettingsTransfer(store, InMemoryKeyValueStore())
        val backup = transfer.export()
        repository.setReadingBoolean(ReadingBooleanPreference.PREFER_NATURAL_VOICE, true)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup))
        assertFalse(AppSettingsRepository(store, store.changes).snapshot().reading.preferNaturalVoice)
    }

    @Test fun disablingReadAloudPreservesPlaybackPreferencesAndSurvivesTransfer() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val transfer = SettingsTransfer(store, InMemoryKeyValueStore())
        assertTrue(repository.snapshot().reading.readAloudEnabled)
        repository.setReadingBoolean(ReadingBooleanPreference.READ_ALOUD_MINI_PLAYER, true)
        repository.setReadingBoolean(ReadingBooleanPreference.READ_ALOUD_ENABLED, false)
        val backup = transfer.export()
        repository.setReadingBoolean(ReadingBooleanPreference.READ_ALOUD_ENABLED, true)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup))
        val reading = repository.snapshot().reading
        assertFalse(reading.readAloudEnabled)
        assertTrue(reading.readAloudMiniPlayer)
        assertTrue(reading.preferNaturalVoice)
    }

    @Test fun miniPlayerIsOptInAndSurvivesSettingsTransfer() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val transfer = SettingsTransfer(store, InMemoryKeyValueStore())
        assertFalse(repository.snapshot().reading.readAloudMiniPlayer)
        repository.setReadingBoolean(ReadingBooleanPreference.READ_ALOUD_MINI_PLAYER, true)
        val backup = transfer.export()
        repository.setReadingBoolean(ReadingBooleanPreference.READ_ALOUD_MINI_PLAYER, false)
        assertFalse(repository.snapshot().reading.readAloudMiniPlayer)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup))
        assertTrue(AppSettingsRepository(store, store.changes).snapshot().reading.readAloudMiniPlayer)
    }

    @Test
    fun defaultsBoundsAndExplicitExistingSizes() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        assertEquals(17, repository.snapshot().reading.readerModeFontSize)
        assertEquals(ReaderLineHeight.STANDARD, repository.snapshot().reading.readerModeLineHeight)
        repository.setReaderModeFontSize(1)
        assertEquals(13, repository.snapshot().reading.readerModeFontSize)
        repository.setReaderModeFontSize(18)
        assertEquals(18, repository.snapshot().reading.readerModeFontSize)
        store.putString(UserPreferenceKeys.READER_MODE_LINE_HEIGHT, "future-value")
        assertEquals(ReaderLineHeight.STANDARD, repository.snapshot().reading.readerModeLineHeight)
    }

    @Test
    fun transferRestoresSpacingAndRejectsInvalidSpacing() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val transfer = SettingsTransfer(store, InMemoryKeyValueStore())
        repository.setReaderModeLineHeight(ReaderLineHeight.RELAXED)
        repository.setReaderModeFontSize(13)
        val backup = transfer.export()
        repository.setReaderModeLineHeight(ReaderLineHeight.COMPACT)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup))
        assertEquals(ReaderLineHeight.RELAXED, repository.snapshot().reading.readerModeLineHeight)
        assertEquals(13, repository.snapshot().reading.readerModeFontSize)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup.replace("relaxed", "invalid")))
        assertEquals(ReaderLineHeight.RELAXED, repository.snapshot().reading.readerModeLineHeight)
    }
}
