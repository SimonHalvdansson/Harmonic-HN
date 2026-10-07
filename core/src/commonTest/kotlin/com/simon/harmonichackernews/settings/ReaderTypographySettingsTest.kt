package com.simon.harmonichackernews.settings

import kotlin.test.*

class ReaderTypographySettingsTest {
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
