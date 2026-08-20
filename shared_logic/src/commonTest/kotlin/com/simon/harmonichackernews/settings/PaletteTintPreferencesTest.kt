package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class PaletteTintPreferencesTest {
    @Test
    fun normalizeConfigKeyUsesDefaultsForMissingAndInvalidParts() {
        assertEquals("default|100|110|0", PaletteTintPreferences.normalizeConfigKey(null))
        assertEquals("vibrant|100|110|0", PaletteTintPreferences.normalizeConfigKey("vibrant"))
        assertEquals(
            "default|100|110|0",
            PaletteTintPreferences.normalizeConfigKey("unknown|invalid||overflow"),
        )
    }

    @Test
    fun normalizeConfigKeyClampsEveryNumericPartAndIgnoresTrailingParts() {
        assertEquals(
            "dominant|200|0|20",
            PaletteTintPreferences.normalizeConfigKey("dominant|999|-5|999|ignored"),
        )
        assertEquals(
            "vibrant|87|143|-7",
            PaletteTintPreferences.normalizeConfigKey("vibrant|087|+143|-7"),
        )
    }
}
