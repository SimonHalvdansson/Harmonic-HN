package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitRatioPreferencesTest {
    @Test
    fun splitCustomizationDefaultsOnAndPreservesAnExplicitOptOut() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        assertNull(repository.snapshot().appearance.portraitSplitRatio)
        assertTrue(repository.snapshot().appearance.allowSplitAdjustment)

        repository.setSplitRatio(SplitOrientation.Portrait, 0.6f)
        assertTrue(repository.snapshot().appearance.allowSplitAdjustment)
        repository.setAppearanceBoolean(AppearanceBooleanPreference.ALLOW_SPLIT_ADJUSTMENT, false)
        val restored = AppSettingsRepository(store, emptyFlow())
        assertEquals(0.6f, restored.snapshot().appearance.portraitSplitRatio)
        assertFalse(restored.snapshot().appearance.allowSplitAdjustment)

        restored.setAppearanceBoolean(AppearanceBooleanPreference.ALLOW_SPLIT_ADJUSTMENT, true)
        assertEquals(0.6f, restored.snapshot().appearance.portraitSplitRatio)
        assertTrue(restored.snapshot().appearance.allowSplitAdjustment)
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
