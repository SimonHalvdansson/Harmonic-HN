package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorSchemePreferencesTest {
    private fun repository(store: InMemoryKeyValueStore) = AppSettingsRepository(store, store.changes)

    @Test
    fun newInstallDefaultsToSyncedDynamicWithSystemAppearance() {
        val appearance = repository(InMemoryKeyValueStore()).snapshot().appearance
        assertEquals(ColorSchemeSelection(), appearance.colorSchemes)
        assertTrue(appearance.followSystem)
    }

    @Test
    fun legacyPairsUseTheLightSelectionForBothModes() {
        val lightChoices = mapOf("material_light" to "orange", "light" to "classic", "white" to "pure", "hacker_news" to "hacker_news")
        for ((light, expected) in lightChoices) for (dark in listOf("material_dark", "dark", "amoled", "hacker", "gray")) {
            val store = InMemoryKeyValueStore().apply {
                putString(ThemePreferences.LIGHT_KEY, light)
                putString(ThemePreferences.DARK_KEY, dark)
                putString(ThemePreferences.COLOR_KEY, "orange")
            }
            val schemes = repository(store).snapshot().appearance.colorSchemes
            assertEquals(expected, schemes.light)
            assertEquals(expected, schemes.dark)
            assertTrue(schemes.coupled)
        }
    }

    @Test
    fun singleLegacyDarkSelectionsKeepTheirIdentityWhenShared() {
        for (scheme in listOf("hacker", "gray")) {
            val store = InMemoryKeyValueStore().apply { putString(ThemePreferences.KEY, scheme) }
            assertEquals(scheme, ColorSchemePreferences.read(store).light)
            assertEquals(scheme, ColorSchemePreferences.read(store).dark)
        }
        val darkOnly = InMemoryKeyValueStore().apply {
            putString(ColorSchemePreferences.DARK_KEY, "rose")
            putString(ColorSchemePreferences.DARK_STYLE_KEY, "vibrant")
        }
        assertEquals(ColorSchemeSelection("rose", lightStyle = ColorSchemeStyle.Vibrant),
            ColorSchemePreferences.read(darkOnly))
    }

    @Test
    fun previouslyIndependentSchemesAndStylesResolveToTheSavedLightChoice() {
        val store = InMemoryKeyValueStore().apply {
            putString(ColorSchemePreferences.LIGHT_KEY, "blue")
            putString(ColorSchemePreferences.DARK_KEY, "rose")
            putString(ColorSchemePreferences.LIGHT_STYLE_KEY, "vibrant")
            putString(ColorSchemePreferences.DARK_STYLE_KEY, "rainbow")
            putBoolean(ColorSchemePreferences.COUPLED_KEY, false)
        }
        val expected = ColorSchemeSelection("blue", lightStyle = ColorSchemeStyle.Vibrant)
        assertEquals(expected, ColorSchemePreferences.read(store))
        ColorSchemePreferences.write(store, expected)
        assertEquals(expected, repository(store).snapshot().appearance.colorSchemes)
        assertEquals("blue", store.getString(ColorSchemePreferences.DARK_KEY))
        assertEquals("vibrant", store.getString(ColorSchemePreferences.DARK_STYLE_KEY))
        assertTrue(store.getBoolean(ColorSchemePreferences.COUPLED_KEY, false))
    }

    @Test
    fun everyEditKeepsSchemesAndStylesSyncedAcrossAppearanceChangesAndReopening() {
        val store = InMemoryKeyValueStore()
        val settings = repository(store)
        for (followSystem in listOf(false, true)) for (dark in listOf(false, true)) {
            settings.setFollowSystemTheme(followSystem)
            settings.setManualDarkTheme(dark)
            settings.setColorScheme("blue")
            settings.setColorStyle(ColorSchemeStyle.NeutralSurfaces)
            val appearance = repository(store).snapshot().appearance
            assertEquals(ColorSchemeSelection("blue", lightStyle = ColorSchemeStyle.NeutralSurfaces), appearance.colorSchemes)
            assertEquals(followSystem, appearance.followSystem)
            assertEquals(dark, appearance.manualDark)
        }
    }

    @Test
    fun writesNormalizeSplitSelectionsIncludingBackupCompatibilityKeys() {
        val store = InMemoryKeyValueStore()
        ColorSchemePreferences.write(store, ColorSchemeSelection(
            "green", "hacker", false, "pure", ColorSchemeStyle.Vibrant, ColorSchemeStyle.NeutralSurfaces,
        ))
        assertEquals(ColorSchemeSelection("green", nighttime = "pure", lightStyle = ColorSchemeStyle.Vibrant),
            ColorSchemePreferences.read(store))
        assertEquals("green", store.getString(ColorSchemePreferences.DARK_KEY))
        assertEquals("vibrant", store.getString(ColorSchemePreferences.DARK_STYLE_KEY))
    }

    @Test
    fun legacyNighttimeChoiceSurvivesMainSchemeEdits() {
        val store = InMemoryKeyValueStore().apply {
            putString(ThemePreferences.LIGHT_KEY, "white")
            putString(ThemePreferences.DARK_KEY, "hacker")
            putString(ThemePreferences.NIGHTTIME_KEY, "material_dark")
            putString(ThemePreferences.COLOR_KEY, "teal")
        }
        val settings = repository(store)
        settings.setColorScheme("amber")
        assertEquals(ColorSchemeSelection("amber", nighttime = "teal"), ColorSchemePreferences.read(store))
        settings.setNighttimeColorScheme("gray")
        assertEquals(ColorSchemeSelection("amber", nighttime = "gray"), ColorSchemePreferences.read(store))
    }

    @Test
    fun missingStylesStayBalancedAndOldNeutralStorageRemainsReadable() {
        for (scheme in ColorSchemePreferences.values) {
            val store = InMemoryKeyValueStore().apply { putString(ColorSchemePreferences.LIGHT_KEY, scheme) }
            assertEquals(ColorSchemeSelection(scheme), ColorSchemePreferences.read(store))
            store.putString(ColorSchemePreferences.LIGHT_STYLE_KEY, "rainbow")
            assertEquals(ColorSchemeStyle.NeutralSurfaces, ColorSchemePreferences.read(store).lightStyle)
        }
    }

    @Test
    fun everySchemeExceptDynamicSupportsStyleAndKeepsItsSavedStyle() {
        val store = InMemoryKeyValueStore()
        val settings = repository(store)
        settings.setColorStyle(ColorSchemeStyle.Vibrant)
        for (scheme in ColorSchemePreferences.values) {
            assertEquals(scheme != ColorSchemePreferences.DYNAMIC, ColorSchemePreferences.supportsStyle(scheme))
            settings.setColorScheme(scheme)
            assertEquals(ColorSchemeStyle.Vibrant, ColorSchemePreferences.read(store).lightStyle)
        }
        assertFalse(ColorSchemePreferences.supportsStyle("invalid"))
    }

    @Test
    fun scheduleRetainsItsIndependentSchemeAndStyle() {
        val store = InMemoryKeyValueStore()
        val settings = repository(store)
        settings.setColorScheme("hacker_news")
        settings.setColorStyle(ColorSchemeStyle.Vibrant)
        settings.setNighttimeColorScheme("teal")
        settings.setNighttimeColorStyle(ColorSchemeStyle.NeutralSurfaces)
        val schemes = ColorSchemePreferences.read(store)
        for (dark in listOf(false, true)) {
            val selected = ThemeSelectionPolicy.select(schemes, true, false, dark, false, NighttimeSchedule(), 0)
            assertEquals("hacker_news", selected.colorScheme)
            assertEquals(ColorSchemeStyle.Vibrant, selected.colorStyle)
            assertEquals(dark, selected.dark)
        }
        val night = ThemeSelectionPolicy.select(schemes, true, false, false, true, NighttimeSchedule(), 0)
        assertEquals("teal", night.colorScheme)
        assertEquals(ColorSchemeStyle.NeutralSurfaces, night.colorStyle)
        assertTrue(night.dark)
    }
}
