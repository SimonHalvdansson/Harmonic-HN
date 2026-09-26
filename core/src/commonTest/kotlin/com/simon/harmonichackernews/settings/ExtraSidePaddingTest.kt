package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtraSidePaddingTest {
    @Test
    fun defaultsToStandardAndRestoresEverySelection() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        assertEquals(ExtraSidePadding.Standard, repository.snapshot().appearance.extraSidePadding)

        for (option in ExtraSidePadding.entries) {
            repository.setExtraSidePadding(option)
            val restored = AppSettingsRepository(store, emptyFlow())
            assertEquals(option, restored.snapshot().appearance.extraSidePadding)
        }
    }

    @Test
    fun unknownStoredOptionFallsBackToStandard() {
        val store = TestKeyValueStore(mapOf(UserPreferenceKeys.EXTRA_SIDE_PADDING to "invalid"))
        val repository = AppSettingsRepository(store, emptyFlow())
        assertEquals(ExtraSidePadding.Standard, repository.snapshot().appearance.extraSidePadding)
    }
}
