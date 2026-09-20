package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SurfaceEffectPreferencesTest {
    @Test
    fun modesPersistIndependentlyOfGlassTuning() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        val original = repository.snapshot()
        assertEquals(SurfaceEffectMode.Frosted, original.appearance.surfaceEffectMode)
        repository.setGlassParameter(GlassParameter.DialogTint, 0.31f)
        repository.setGlassSwitch(GlassSwitch.AdaptiveOptics, false)
        repository.setGlassSurfaceProfile(GlassSurfaceProfile.Concave)

        for (mode in SurfaceEffectMode.entries) {
            repository.setSurfaceEffectMode(mode)
            val restored = AppSettingsRepository(store, emptyFlow()).snapshot()
            val glass = restored.debug.glass
            assertEquals(mode, restored.appearance.surfaceEffectMode)
            assertEquals(0.31f, glass[GlassParameter.DialogTint])
            assertFalse(glass[GlassSwitch.AdaptiveOptics])
            assertEquals(GlassSurfaceProfile.Concave, glass.surfaceProfile)
            assertEquals(original.copy(appearance = original.appearance.copy(surfaceEffectMode = mode), debug = restored.debug), restored)
        }
    }

    @Test
    fun allShaderParametersAreClampedAndNonFiniteValuesFallBackOnReadAndWrite() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        for (parameter in GlassParameter.entries) {
            repository.setGlassParameter(parameter, parameter.range.endInclusive + 100f)
            assertEquals(parameter.range.endInclusive, repository.snapshot().debug.glass[parameter])
            repository.setGlassParameter(parameter, parameter.range.start - 100f)
            assertEquals(parameter.range.start, repository.snapshot().debug.glass[parameter])
            for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                repository.setGlassParameter(parameter, invalid)
                assertEquals(parameter.default, store.getFloat(parameter.storageKey, -999f))
                store.putFloat(parameter.storageKey, invalid)
                assertEquals(parameter.default, repository.snapshot().debug.glass[parameter])
            }
        }
    }

    @Test
    fun resetRestoresEveryGlassControlWithoutChangingModeOrOtherSettings() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setSurfaceEffectMode(SurfaceEffectMode.Glass)
        repository.setDebugBoolean(DebugBooleanPreference.ALWAYS_SHOW_TAP_TO_REFRESH, true)
        val original = repository.snapshot()
        GlassParameter.entries.forEach { repository.setGlassParameter(it, it.range.start) }
        GlassSwitch.entries.forEach { repository.setGlassSwitch(it, !it.default) }
        repository.setGlassSurfaceProfile(GlassSurfaceProfile.Lip)
        repository.resetGlassPreferences()
        assertEquals(original, AppSettingsRepository(store, emptyFlow()).snapshot())
    }

    @Test
    fun hostDefaultsAreUsedUntilAnExplicitSelectionIsStored() {
        for (default in listOf(SurfaceEffectMode.Frosted, SurfaceEffectMode.Glass)) {
            val store = TestKeyValueStore()
            val repository = AppSettingsRepository(store, emptyFlow(), defaultSurfaceEffectMode = default)
            assertEquals(default, repository.snapshot().appearance.surfaceEffectMode)
            assertFalse(store.contains(SurfaceEffectMode.STORAGE_KEY))
            for (selected in SurfaceEffectMode.entries) {
                repository.setSurfaceEffectMode(selected)
                assertEquals(selected, repository.snapshot().appearance.surfaceEffectMode)
            }
            store.putString(SurfaceEffectMode.STORAGE_KEY, "unknown")
            assertEquals(default, repository.snapshot().appearance.surfaceEffectMode)
        }
    }

    @Test
    fun unknownModesAndProfilesUseDefaults() {
        val store = TestKeyValueStore()
        store.putString(SurfaceEffectMode.STORAGE_KEY, "unknown")
        store.putString(GlassSurfaceProfile.STORAGE_KEY, "unknown")
        val settings = AppSettingsRepository(store, emptyFlow()).snapshot()
        assertEquals(SurfaceEffectMode.Frosted, settings.appearance.surfaceEffectMode)
        assertEquals(GlassPreferences(), settings.debug.glass)
    }
}
