package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeClassificationTest {
    @Test
    fun classificationKeepsExactNullableStringMembership() {
        val automatic = setOf(
            "material_daynight", "material_fixed_daynight", "darklight_daynight", "amoledwhite_daynight",
        )
        val dark = setOf("material_dark", "material_fixed_dark", "dark", "hacker", "amoled", "gray")
        val names = automatic + dark + setOf(
            "material_light", "material_fixed_light", "light", "hacker_news", "white", "", "unknown",
        )
        val inputs = names.flatMap { name ->
            listOf(name, name.uppercase(), " $name", "$name ", "${name}x", name.toCharArray().concatToString())
        } + listOf(null, "\u0000", "dårk", "MATERIAL_DARK")
        for (input in inputs) {
            assertEquals(input in automatic, ThemePreferences.isAutomatic(input), "automatic: $input")
            assertEquals(input in dark, ThemePreferences.isDark(input), "dark: $input")
        }
    }
}
