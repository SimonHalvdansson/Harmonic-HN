package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorSchemePreferencesTest {
    private fun repository(store: InMemoryKeyValueStore) = AppSettingsRepository(store, store.changes)

    @Test
    fun newInstallDefaultsToCoupledDynamicWithSystemAppearance() {
        val appearance = repository(InMemoryKeyValueStore()).snapshot().appearance
        assertEquals(ColorSchemeSelection(), appearance.colorSchemes)
        assertTrue(appearance.followSystem)
    }

    @Test
    fun legacyPairsPreserveBothEffectiveSchemesAndOnlyMatchingPairsAreCoupled() {
        val lightChoices = mapOf("material_light" to "orange", "light" to "classic", "white" to "pure", "hacker_news" to "hacker_news")
        val darkChoices = mapOf("material_dark" to "orange", "dark" to "classic", "amoled" to "pure", "hacker" to "hacker", "gray" to "gray")
        for ((light, expectedLight) in lightChoices) for ((dark, expectedDark) in darkChoices) {
            val store = InMemoryKeyValueStore().apply {
                putString(ThemePreferences.LIGHT_KEY, light)
                putString(ThemePreferences.DARK_KEY, dark)
                putString(ThemePreferences.COLOR_KEY, "orange")
            }
            val pair = repository(store).snapshot().appearance.colorSchemes
            assertEquals(expectedLight, pair.light)
            assertEquals(expectedDark, pair.dark)
            assertEquals(expectedLight == expectedDark, pair.coupled)
        }
    }

    @Test
    fun legacyAutomaticAndSingleThemesKeepTheirModesAndFallbackPairs() {
        val cases = listOf(
            Triple("material_fixed_daynight", "dynamic", "dynamic"),
            Triple("darklight_daynight", "classic", "classic"),
            Triple("amoledwhite_daynight", "pure", "pure"),
            Triple("hacker", "hacker_news", "hacker"),
            Triple("gray", "classic", "gray"),
        )
        for ((legacy, light, dark) in cases) {
            val store = InMemoryKeyValueStore().apply { putString(ThemePreferences.KEY, legacy) }
            val appearance = repository(store).snapshot().appearance
            assertEquals(light, appearance.colorSchemes.light)
            assertEquals(dark, appearance.colorSchemes.dark)
            assertEquals(ThemePreferences.isAutomatic(legacy), appearance.followSystem)
            assertEquals(ThemePreferences.isDark(legacy), appearance.manualDark)
        }
    }

    @Test
    fun decouplingPreservesPairAndModeAndModeSpecificEditsSurviveReopening() {
        val store = InMemoryKeyValueStore()
        val settings = repository(store)
        settings.setColorScheme("blue", false)
        settings.setColorSchemesCoupled(false, false)
        assertEquals(ColorSchemeSelection("blue", "blue", false), repository(store).snapshot().appearance.colorSchemes)
        settings.setColorScheme("rose", true)
        settings.setFollowSystemTheme(false)
        settings.setManualDarkTheme(false)
        assertEquals(ColorSchemeSelection("blue", "rose", false), repository(store).snapshot().appearance.colorSchemes)
        settings.setManualDarkTheme(true)
        settings.setColorScheme("hacker", true)
        assertEquals(ColorSchemeSelection("blue", "hacker", false), repository(store).snapshot().appearance.colorSchemes)
        settings.setFollowSystemTheme(true)
        assertEquals("blue", repository(store).snapshot().appearance.colorSchemes.light)
    }

    @Test
    fun recouplingUsesDisplayedSchemeWithoutChangingAppearance() {
        for (followSystem in listOf(false, true)) for (dark in listOf(false, true)) {
            val store = InMemoryKeyValueStore()
            val settings = repository(store)
            settings.setColorSchemesCoupled(false, false)
            settings.setColorScheme("orange", false)
            settings.setColorScheme("hacker", true)
            settings.setFollowSystemTheme(followSystem)
            settings.setManualDarkTheme(dark)
            val before = settings.snapshot().appearance
            settings.setColorSchemesCoupled(true, dark)
            val reopened = repository(store).snapshot().appearance
            val expected = if (dark) "hacker" else "orange"
            assertEquals(ColorSchemeSelection(expected), reopened.colorSchemes)
            assertEquals(before.followSystem, reopened.followSystem)
            assertEquals(before.manualDark, reopened.manualDark)
            settings.setColorScheme("green", dark)
            assertEquals(ColorSchemeSelection("green"), repository(store).snapshot().appearance.colorSchemes)
        }
    }

    @Test
    fun firstEditKeepsTheOtherMigratedModeAndNighttimeChoice() {
        val store = InMemoryKeyValueStore().apply {
            putString(ThemePreferences.LIGHT_KEY, "white")
            putString(ThemePreferences.DARK_KEY, "hacker")
            putString(ThemePreferences.NIGHTTIME_KEY, "material_dark")
            putString(ThemePreferences.COLOR_KEY, "teal")
        }
        val settings = repository(store)
        settings.setColorScheme("amber", false)
        assertEquals(ColorSchemeSelection("amber", "hacker", false, "teal"), repository(store).snapshot().appearance.colorSchemes)
        settings.setNighttimeColorScheme("gray")
        assertEquals(ColorSchemeSelection("amber", "hacker", false, "gray"), repository(store).snapshot().appearance.colorSchemes)
    }

    @Test
    fun canonicalPreferencesWinAndCorruptCouplingCannotOverwriteDistinctChoices() {
        val store = InMemoryKeyValueStore().apply {
            putString(ThemePreferences.COLOR_KEY, "orange")
            putString(ColorSchemePreferences.LIGHT_KEY, "green")
            putString(ColorSchemePreferences.DARK_KEY, "unknown")
            putBoolean(ColorSchemePreferences.COUPLED_KEY, true)
        }
        val result = repository(store).snapshot().appearance.colorSchemes
        assertEquals("green", result.light)
        assertEquals("dynamic", result.dark)
        assertFalse(result.coupled)
    }

    @Test
    fun runtimeResolvesModeAndScheduledSchemeIndependentlyOfLegacyFields() {
        val schemes = ColorSchemeSelection("hacker", "amber", false, "pure")
        fun select(followSystem: Boolean, manualDark: Boolean, systemDark: Boolean, nighttime: Boolean = false) =
            ThemeSelectionPolicy.select(schemes, followSystem, manualDark, systemDark, nighttime, NighttimeSchedule(), 23 * 60)
        assertEquals("hacker", select(true, true, false).colorScheme)
        assertEquals("amber", select(true, false, true).colorScheme)
        assertEquals("hacker", select(false, false, true).colorScheme)
        assertFalse(select(false, false, true).dark)
        assertEquals("amber", select(false, true, false).colorScheme)
        assertTrue(select(false, true, false).dark)
        assertEquals("pure", select(false, false, false, true).colorScheme)
        assertTrue(select(false, false, false, true).dark)
    }
    @Test
    fun generatedSelectionsMigrateToBalancedWithoutChangingEitherBaseOrCoupling() {
        for (light in ColorSchemePreferences.generatedValues) for (dark in ColorSchemePreferences.generatedValues) {
            val store = InMemoryKeyValueStore().apply {
                putString(ColorSchemePreferences.LIGHT_KEY, light)
                putString(ColorSchemePreferences.DARK_KEY, dark)
            }
            val schemes = ColorSchemePreferences.read(store)
            assertEquals(ColorSchemeSelection(light, dark), schemes)
            ColorSchemePreferences.write(store, schemes)
            assertEquals(schemes, ColorSchemePreferences.read(store))
        }
    }

    @Test
    fun synchronizedEditsIncludeStyleEvenInExplicitAppearanceModes() {
        val store = InMemoryKeyValueStore()
        val settings = repository(store)
        settings.setColorScheme("teal", false)
        settings.setColorStyle(ColorSchemeStyle.Vibrant, false)
        for (dark in listOf(false, true)) {
            settings.setFollowSystemTheme(false)
            settings.setManualDarkTheme(dark)
            settings.setColorScheme("blue", dark)
            settings.setColorStyle(ColorSchemeStyle.NeutralSurfaces, dark)
            val pair = repository(store).snapshot().appearance.colorSchemes
            assertTrue(pair.coupled)
            assertEquals("blue", pair.light)
            assertEquals(pair.light, pair.dark)
            assertEquals(ColorSchemeStyle.NeutralSurfaces, pair.lightStyle)
            assertEquals(pair.lightStyle, pair.darkStyle)
        }
        settings.setFollowSystemTheme(true)
        assertTrue(repository(store).snapshot().appearance.colorSchemes.coupled)
    }

    @Test
    fun independentStylesSurviveModeChangesAndRecoupleWithTheDisplayedBase() {
        for (displayedDark in listOf(false, true)) {
            val store = InMemoryKeyValueStore()
            val settings = repository(store)
            settings.setColorScheme("orange", false)
            settings.setColorStyle(ColorSchemeStyle.Vibrant, false)
            val matching = settings.snapshot().appearance.colorSchemes
            settings.setColorSchemesCoupled(false, displayedDark)
            assertEquals(matching.copy(coupled = false), ColorSchemePreferences.read(store))
            settings.setFollowSystemTheme(false)
            settings.setManualDarkTheme(true)
            settings.setColorScheme("blue", true)
            settings.setColorStyle(ColorSchemeStyle.NeutralSurfaces, true)
            settings.setManualDarkTheme(false)
            val split = repository(store).snapshot().appearance.colorSchemes
            assertEquals("orange", split.light)
            assertEquals(ColorSchemeStyle.Vibrant, split.lightStyle)
            assertEquals("blue", split.dark)
            assertEquals(ColorSchemeStyle.NeutralSurfaces, split.darkStyle)
            assertFalse(split.coupled)
            settings.setFollowSystemTheme(true)
            settings.setColorSchemesCoupled(true, displayedDark)
            val joined = repository(store).snapshot().appearance.colorSchemes
            assertEquals(split.forMode(displayedDark), joined.light)
            assertEquals(joined.light, joined.dark)
            assertEquals(split.styleForMode(displayedDark), joined.lightStyle)
            assertEquals(joined.lightStyle, joined.darkStyle)
            assertTrue(joined.coupled)
        }
    }

    @Test
    fun distinctRestoredStylesAreNeverSilentlyCoupled() {
        val store = InMemoryKeyValueStore().apply {
            putString(ColorSchemePreferences.LIGHT_KEY, "blue")
            putString(ColorSchemePreferences.DARK_KEY, "blue")
            putString(ColorSchemePreferences.LIGHT_STYLE_KEY, "vibrant")
            putString(ColorSchemePreferences.DARK_STYLE_KEY, "rainbow")
            putBoolean(ColorSchemePreferences.COUPLED_KEY, true)
        }
        assertFalse(ColorSchemePreferences.read(store).coupled)
        repository(store).setColorScheme("rose", false)
        assertEquals("blue", ColorSchemePreferences.read(store).dark)
        assertEquals(ColorSchemeStyle.NeutralSurfaces, ColorSchemePreferences.read(store).darkStyle)
    }

    @Test
    fun curatedAndDynamicChoicesRememberStyleAndNighttimeHasItsOwnStyle() {
        val store = InMemoryKeyValueStore()
        val settings = repository(store)
        settings.setColorStyle(ColorSchemeStyle.Vibrant, false)
        for (base in listOf("dynamic", "classic", "pure", "hacker_news", "hacker", "gray", "orange")) {
            settings.setColorScheme(base, false)
            assertEquals(ColorSchemeStyle.Vibrant, ColorSchemePreferences.read(store).lightStyle)
        }
        settings.setNighttimeColorScheme("teal")
        settings.setNighttimeColorStyle(ColorSchemeStyle.NeutralSurfaces)
        val pair = ColorSchemePreferences.read(store)
        val day = ThemeSelectionPolicy.select(pair, true, false, false, false, NighttimeSchedule(), 0)
        val night = ThemeSelectionPolicy.select(pair, true, false, false, true, NighttimeSchedule(), 0)
        assertEquals(ColorSchemeStyle.Vibrant, day.colorStyle)
        assertEquals("teal", night.colorScheme)
        assertEquals(ColorSchemeStyle.NeutralSurfaces, night.colorStyle)
    }

}
