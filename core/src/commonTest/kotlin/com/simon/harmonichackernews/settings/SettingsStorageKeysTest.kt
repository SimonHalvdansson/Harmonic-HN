package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsStorageKeysTest {
    @Test
    fun existingHeaderAndGlassKeysRemainReadableWritableAndResettable() {
        val store = InMemoryKeyValueStore()
        val buttons = CommentsHeaderButton.entries.filter { it.configurable }
        buttons.forEach { store.putBoolean("pref_comments_header_button_${it.name.lowercase()}", false) }
        GlassParameter.entries.forEach { store.putFloat("pref_debug_glass_${it.name}", it.range.start) }
        GlassSwitch.entries.forEach { store.putBoolean("pref_debug_glass_${it.name}_enabled", !it.default) }

        val settings = StoredUserSettings(store, emptyFlow())
        assertEquals(buttons.associateWith { false }, settings.comments.headerButtons.overrides)
        assertEquals(GlassParameter.entries.associateWith { it.range.start }, settings.debug.glass.parameters)
        assertEquals(GlassSwitch.entries.associateWith { !it.default }, settings.debug.glass.switches)

        val mutator = StoredSettingsMutator(store)
        buttons.forEach { mutator.setCommentsHeaderButton(it, true) }
        GlassParameter.entries.forEach { mutator.setGlassParameter(it, it.range.endInclusive) }
        GlassSwitch.entries.forEach { mutator.setGlassSwitch(it, it.default) }
        buttons.forEach {
            assertEquals(true, store.getBoolean("pref_comments_header_button_${it.name.lowercase()}", false))
        }
        GlassParameter.entries.forEach {
            assertEquals(it.range.endInclusive, store.getFloat("pref_debug_glass_${it.name}", Float.NaN))
        }
        GlassSwitch.entries.forEach {
            assertEquals(it.default, store.getBoolean("pref_debug_glass_${it.name}_enabled", !it.default))
        }

        mutator.resetCommentsHeaderButtons()
        mutator.resetGlassPreferences()
        buttons.forEach { assertFalse(store.contains("pref_comments_header_button_${it.name.lowercase()}")) }
        GlassParameter.entries.forEach { assertFalse(store.contains("pref_debug_glass_${it.name}")) }
        GlassSwitch.entries.forEach { assertFalse(store.contains("pref_debug_glass_${it.name}_enabled")) }
        assertEquals(emptyMap(), settings.comments.headerButtons.overrides)
        assertEquals(emptyMap(), settings.debug.glass.parameters)
        assertEquals(emptyMap(), settings.debug.glass.switches)
    }
}
