package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.ThemePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSelectionDialogTest {
    @Test
    fun materialYouOptionsAreHiddenWhenDynamicColorIsUnavailable() {
        val options = harmonicThemeOptions(
            nighttime = false,
            materialYouAvailable = false,
        )

        assertFalse(options.any(ThemeUiOption::materialYou))
        assertTrue(options.any { it.value == ThemePreferences.MATERIAL_FIXED_AUTO })
        assertTrue(options.any { it.value == ThemePreferences.MATERIAL_FIXED_LIGHT })
        assertTrue(options.any { it.value == ThemePreferences.MATERIAL_FIXED_DARK })
    }

    @Test
    fun nighttimeOptionsContainOnlyExplicitDarkThemes() {
        val options = harmonicThemeOptions(
            nighttime = true,
            materialYouAvailable = true,
        )

        assertTrue(options.all { it.dark && !it.automatic })
        assertTrue(options.any { it.value == ThemePreferences.MATERIAL_FIXED_DARK })
        assertFalse(options.any { it.value == ThemePreferences.MATERIAL_FIXED_AUTO })
    }

    @Test
    fun unavailableMaterialYouLabelUsesFixedEquivalent() {
        assertEquals(
            "Material (auto)",
            harmonicThemeLabel(
                value = ThemePreferences.DEFAULT,
                fallback = ThemePreferences.DEFAULT,
                materialYouAvailable = false,
            ),
        )
    }
}
