package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.AppSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorSchemeSettingsPolicyTest {
    @Test
    fun pickerLayoutsFollowCouplingAndAppearance() {
        for (system in listOf(false, true)) for (dark in listOf(false, true)) {
            val expected = if (system) ColorSchemePickerMode.Coupled else if (dark) ColorSchemePickerMode.Dark else ColorSchemePickerMode.Light
            assertEquals(listOf(expected), colorSchemePickerModes(true, system, dark))
        }
        assertEquals(listOf(ColorSchemePickerMode.Light, ColorSchemePickerMode.Dark), colorSchemePickerModes(false, true, false))
        assertEquals(listOf(ColorSchemePickerMode.Light, ColorSchemePickerMode.Dark), colorSchemePickerModes(false, true, true))
        assertEquals(listOf(ColorSchemePickerMode.Light), colorSchemePickerModes(false, false, false))
        assertEquals(listOf(ColorSchemePickerMode.Dark), colorSchemePickerModes(false, false, true))
    }

    @Test
    fun presenterRecouplesUsingActiveSystemModeRatherThanLastManualMode() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        repository.setColorSchemesCoupled(false, false)
        repository.setColorScheme("blue", false)
        repository.setColorScheme("pure", true)
        repository.setManualDarkTheme(false)
        AppearanceSettingsPresenter(repository).setCoupled(true, activeDark = true)
        val appearance = repository.snapshot().appearance
        assertEquals("pure", appearance.colorSchemes.light)
        assertEquals("pure", appearance.colorSchemes.dark)
        assertEquals(true, appearance.followSystem)
        assertEquals(false, appearance.manualDark)
        assertEquals("System · Pure", themeSettingsSummary(appearance))
    }
}
