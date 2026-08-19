package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class NighttimeScheduleStoreTest {
    @Test
    fun defaultsAndMalformedValuesMatchExistingThemeBehaviour() {
        val defaults = NighttimeScheduleStore(TestKeyValueStore()).load()
        assertContentEquals(intArrayOf(21, 0, 6, 0), defaults.toIntArray())

        val malformed = NighttimeScheduleStore(
            TestKeyValueStore(
                mapOf(
                    NighttimeScheduleKeys.FROM_HOUR to "invalid",
                    NighttimeScheduleKeys.TO_MINUTE to "45",
                ),
            ),
        ).load()
        assertContentEquals(intArrayOf(21, 0, 6, 45), malformed.toIntArray())
    }

    @Test
    fun scheduleRoundTripsThroughThePortableStore() {
        val values = mutableMapOf<String, Any?>()
        val store = TestKeyValueStore(values)
        val schedules = NighttimeScheduleStore(store)

        schedules.save(NighttimeSchedule(22, 30, 7, 15))

        assertContentEquals(intArrayOf(22, 30, 7, 15), schedules.load().toIntArray())
    }

    @Test
    fun appearanceSelectionsFollowPersistentThemeChanges() = runTest {
        val settings = TestKeyValueStore()
        val changes = flow {
            settings.putString(ThemePreferences.KEY, "amoled")
            emit(Unit)
        }
        val runtime = AppearanceRuntime(
            settings = settings,
            scheduleStore = NighttimeScheduleStore(TestKeyValueStore()),
            launchState = AppLaunchStateStore(TestKeyValueStore()),
            settingsChanges = changes,
            currentMinutesFromMidnight = { 12 * 60 },
            systemDark = { false },
        )

        val selections = runtime.selections.take(2).toList()

        assertEquals(ThemePreferences.DEFAULT, selections.first().theme)
        assertEquals("amoled", selections.last().theme)
        assertEquals(true, selections.last().dark)
    }
}
