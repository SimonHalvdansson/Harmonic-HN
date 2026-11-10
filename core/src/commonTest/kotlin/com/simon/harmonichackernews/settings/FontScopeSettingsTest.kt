package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FontScopeSettingsTest {
    @Test
    fun scopeDefaultsOffAndDoesNotChangeTheContentOrReaderFont() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        assertFalse(repository.snapshot().appearance.applyFontEverywhere)
        repository.setFont(AppFont.fromStored("georgia"))
        val before = repository.snapshot()
        repository.setAppearanceBoolean(AppearanceBooleanPreference.APPLY_FONT_EVERYWHERE, true)
        val after = AppSettingsRepository(store, store.changes).snapshot()
        assertTrue(after.appearance.applyFontEverywhere)
        assertEquals(before.story, after.story)
        assertEquals(before.comments, after.comments)
        assertEquals(before.reading, after.reading)
        repository.setAppearanceBoolean(AppearanceBooleanPreference.APPLY_FONT_EVERYWHERE, false)
        assertFalse(repository.snapshot().appearance.applyFontEverywhere)
        assertEquals("georgia", repository.snapshot().appearance.font)
    }

    @Test
    fun backupAndValidatedDevelopmentEditsPreserveTheScope() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val transfer = SettingsTransfer(store, InMemoryKeyValueStore())
        repository.setAppearanceBoolean(AppearanceBooleanPreference.APPLY_FONT_EVERYWHERE, true)
        val backup = transfer.export()
        repository.setAppearanceBoolean(AppearanceBooleanPreference.APPLY_FONT_EVERYWHERE, false)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup))
        assertTrue(repository.snapshot().appearance.applyFontEverywhere)
        assertFalse(transfer.setValue(UserPreferenceKeys.APPLY_FONT_EVERYWHERE,
            kotlinx.serialization.json.JsonPrimitive("everywhere")))
        assertTrue(repository.snapshot().appearance.applyFontEverywhere)
        assertTrue(transfer.setValue(UserPreferenceKeys.APPLY_FONT_EVERYWHERE,
            kotlinx.serialization.json.JsonPrimitive(false)))
        assertFalse(repository.snapshot().appearance.applyFontEverywhere)
    }
}
