package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SplitRatioPreferencesTest {
    @Test
    fun retiredPaneOptOutsAreIgnoredWithoutLosingSavedRatios() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setSplitRatio(SplitOrientation.Portrait, 0.6f)
        val appearance = repository.snapshot().appearance

        store.putBoolean("pref_side_by_side_enabled", false)
        store.putBoolean("pref_allow_split_adjustment", false)

        assertEquals(appearance, AppSettingsRepository(store, emptyFlow()).snapshot().appearance)
    }

    @Test
    fun orientationsKeepIndependentRatiosAndDefaults() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setSplitRatio(SplitOrientation.Portrait, 0.35f)
        assertNull(repository.snapshot().appearance.splitRatio(SplitOrientation.Landscape))
        repository.setSplitRatio(SplitOrientation.Landscape, 0.65f)
        val restored = AppSettingsRepository(store, emptyFlow()).snapshot().appearance
        assertEquals(0.35f, restored.splitRatio(SplitOrientation.Portrait))
        assertEquals(0.65f, restored.splitRatio(SplitOrientation.Landscape))
        assertEquals(SplitOrientation.Portrait, SplitOrientation.forWindow(900, 1000))
        assertEquals(SplitOrientation.Landscape, SplitOrientation.forWindow(1000, 900))
        assertEquals(SplitOrientation.Portrait, SplitOrientation.forWindow(900, 900))
    }

    @Test
    fun onlyFoldablesSnapNearTheCreaseAndCanMovePastIt() {
        for (ratio in listOf(0.47f, 0.48f, 0.49f, 0.5f, 0.51f, 0.52f, 0.53f)) {
            assertEquals(0.5f, SplitRatioPreferences.snapToCenter(ratio, isFoldable = true))
            assertEquals(ratio, SplitRatioPreferences.snapToCenter(ratio, isFoldable = false))
        }
        // Raw drag movement must be allowed to cross the magnet in either direction.
        assertEquals(0.46f, SplitRatioPreferences.snapToCenter(0.46f, isFoldable = true))
        assertEquals(0.54f, SplitRatioPreferences.snapToCenter(0.54f, isFoldable = true))
    }

    @Test
    fun invalidValuesCannotCollapseAPaneOrBreakLayout() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setSplitRatio(SplitOrientation.Portrait, -1f)
        assertEquals(0.3f, repository.snapshot().appearance.portraitSplitRatio)
        repository.setSplitRatio(SplitOrientation.Portrait, 2f)
        assertEquals(0.7f, repository.snapshot().appearance.portraitSplitRatio)
        repository.setSplitRatio(SplitOrientation.Portrait, Float.NaN)
        repository.setSplitRatio(SplitOrientation.Portrait, Float.POSITIVE_INFINITY)
        assertEquals(0.7f, repository.snapshot().appearance.portraitSplitRatio)
        store.putFloat(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, Float.NaN)
        assertNull(repository.snapshot().appearance.portraitSplitRatio)
        store.putFloat(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, 5f)
        assertEquals(0.7f, repository.snapshot().appearance.portraitSplitRatio)
    }
}
