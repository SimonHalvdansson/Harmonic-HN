package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PureBlackDarkModeTest {
    private val schemes = ColorSchemeSelection(
        light = "blue", lightStyle = ColorSchemeStyle.Vibrant,
        nighttime = "rose", nighttimeStyle = ColorSchemeStyle.Balanced,
    )

    @Test
    fun overrideUsesTheActiveSchemesSwitchAndOnlyAppliesInDarkMode() {
        for (regular in listOf(false, true)) for (night in listOf(false, true)) {
            for (followSystem in listOf(false, true)) for (manualDark in listOf(false, true)) {
                for (systemDark in listOf(false, true)) for (scheduled in listOf(false, true)) {
                    for (hour in listOf(12, 23)) {
                        val nighttime = scheduled && hour == 23
                        val dark = nighttime || if (followSystem) systemDark else manualDark
                        val pureBlack = dark && if (nighttime) night else regular
                        val selection = ThemeSelectionPolicy.select(
                            schemes.copy(pureBlackDarkMode = regular, nighttimePureBlackDarkMode = night),
                            followSystem, manualDark, systemDark, scheduled, NighttimeSchedule(), hour * 60,
                        )
                        assertEquals(dark, selection.dark)
                        assertEquals(pureBlack, selection.pureBlackDarkMode)
                        assertEquals(if (pureBlack) "pure" else if (nighttime) "rose" else "blue", selection.colorScheme)
                        assertEquals(if (pureBlack) ColorSchemeStyle.NeutralSurfaces else if (nighttime) schemes.nighttimeStyle else schemes.lightStyle,
                            selection.colorStyle)
                    }
                }
            }
        }
    }

    @Test
    fun effectiveDisplaySettingsRestoreWithoutWritingStoredChoices() {
        val store = InMemoryKeyValueStore().apply {
            putString(UserPreferenceKeys.STORY_DISPLAY_STYLE, DisplayStyle.RAISED.storedValue)
            putString(UserPreferenceKeys.COMMENT_DISPLAY_STYLE, DisplayStyle.OUTLINED.storedValue)
            putBoolean(UserPreferenceKeys.TINT_CARD_USING_PREVIEW, true)
        }
        val stored = StoredUserSettings(store, store.changes)
        val beforeStory = stored.story
        val beforeComments = stored.comments
        var selection = ThemeSelection("pure", true, ColorSchemeStyle.NeutralSurfaces)
        val effective = ThemeUserSettings(stored) { selection }
        // Selecting Pure manually does not opt in to the display overrides.
        assertEquals(beforeStory, effective.story)
        selection = selection.copy(pureBlackDarkMode = true)
        assertEquals(DisplayStyle.FLAT, effective.story.displayStyle)
        assertFalse(effective.story.tintCardsFromImages)
        assertEquals(DisplayStyle.FLAT, effective.comments.displayStyle)
        assertEquals(beforeStory, stored.story)
        assertEquals(beforeComments, stored.comments)
        selection = ThemeSelection("blue", false, ColorSchemeStyle.Vibrant)
        assertEquals(beforeStory, effective.story)
        assertEquals(beforeComments, effective.comments)
    }

    @Test
    fun switchesPersistIndependentlyAndSurviveSchemeEditsAndBackup() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        assertFalse(repository.snapshot().appearance.colorSchemes.pureBlackDarkMode)
        assertFalse(repository.snapshot().appearance.colorSchemes.nighttimePureBlackDarkMode)
        repository.setAppearanceBoolean(AppearanceBooleanPreference.PURE_BLACK_DARK_MODE, true)
        repository.setColorScheme("teal")
        repository.setColorStyle(ColorSchemeStyle.Vibrant)
        assertTrue(repository.snapshot().appearance.colorSchemes.pureBlackDarkMode)
        assertFalse(repository.snapshot().appearance.colorSchemes.nighttimePureBlackDarkMode)
        repository.setAppearanceBoolean(AppearanceBooleanPreference.NIGHTTIME_PURE_BLACK_DARK_MODE, true)
        repository.setNighttimeColorScheme("rose")
        repository.setNighttimeColorStyle(ColorSchemeStyle.NeutralSurfaces)
        val restored = InMemoryKeyValueStore()
        SettingsTransfer(restored, InMemoryKeyValueStore()).import(SettingsTransfer(store, InMemoryKeyValueStore()).export())
        assertEquals(repository.snapshot().appearance.colorSchemes, ColorSchemePreferences.read(restored))
    }
}
