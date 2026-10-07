package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emptyFlow
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
            appearanceChanges = emptyFlow(),
            currentMinutesFromMidnight = { 12 * 60 },
            systemDark = { false },
        )

        val selections = runtime.selections.take(2).toList()

        assertEquals("dynamic", selections.first().colorScheme)
        assertEquals("pure", selections.last().colorScheme)
        assertEquals(true, selections.last().dark)
    }

    @Test
    fun appearanceSelectionsFollowSystemAndClockChanges() = runTest {
        var systemDark = false
        val runtime = AppearanceRuntime(
            settings = TestKeyValueStore(),
            scheduleStore = NighttimeScheduleStore(TestKeyValueStore()),
            launchState = AppLaunchStateStore(TestKeyValueStore()),
            settingsChanges = emptyFlow(),
            appearanceChanges = flow {
                systemDark = true
                emit(Unit)
            },
            currentMinutesFromMidnight = { 12 * 60 },
            systemDark = { systemDark },
        )

        val selections = runtime.selections.take(2).toList()

        assertEquals(false, selections.first().dark)
        assertEquals(true, selections.last().dark)
    }

    @Test
    fun disabledNighttimeSelectionDoesNotReadScheduleOrClock() {
        val settings = TestKeyValueStore(mapOf(ThemePreferences.KEY to "white"))
        val unreadableSchedule = object : KeyValueStore by TestKeyValueStore() {
            override fun getString(key: String, default: String?): String? =
                error("Disabled nighttime mode must not read its schedule")
        }
        val runtime = AppearanceRuntime(
            settings = settings,
            scheduleStore = NighttimeScheduleStore(unreadableSchedule),
            launchState = AppLaunchStateStore(TestKeyValueStore()),
            settingsChanges = emptyFlow(),
            appearanceChanges = emptyFlow(),
            currentMinutesFromMidnight = { error("Disabled nighttime mode must not read the clock") },
            systemDark = { false },
        )

        assertEquals("pure", runtime.selection().colorScheme)
        assertEquals(false, runtime.selection().dark)
    }

    @Test
    fun enablingNighttimeUsesCurrentClockAndScheduleWithoutCaching() {
        val settings = TestKeyValueStore(mapOf(
            ThemePreferences.KEY to "white",
            ThemePreferences.NIGHTTIME_KEY to "gray",
        ))
        val schedules = NighttimeScheduleStore(TestKeyValueStore())
        var minutes = 21 * 60
        var clockReads = 0
        val runtime = AppearanceRuntime(
            settings = settings,
            scheduleStore = schedules,
            launchState = AppLaunchStateStore(TestKeyValueStore()),
            settingsChanges = emptyFlow(),
            appearanceChanges = emptyFlow(),
            currentMinutesFromMidnight = { clockReads++; minutes },
            systemDark = { false },
        )

        assertEquals("pure", runtime.selection().colorScheme)
        assertEquals(0, clockReads)
        settings.putBoolean(UserPreferenceKeys.SPECIAL_NIGHTTIME, true)
        assertEquals("gray", runtime.selection().colorScheme)
        schedules.save(NighttimeSchedule(fromHour = 22))
        assertEquals("pure", runtime.selection().colorScheme)
        minutes = 23 * 60
        assertEquals("gray", runtime.selection().colorScheme)
        minutes = 6 * 60
        assertEquals("pure", runtime.selection().colorScheme)
        assertEquals(4, clockReads)
        settings.putBoolean(UserPreferenceKeys.SPECIAL_NIGHTTIME, false)
        assertEquals("pure", runtime.selection().colorScheme)
        assertEquals(4, clockReads)
    }

    @Test
    fun manualAndScheduledSelectionsUseTheirDedicatedPalettes() {
        val schemes = ColorSchemeSelection("pure", "classic", false, "gray")
        val manual = ThemeSelectionPolicy.select(schemes, false, false, true,
            false, NighttimeSchedule(), 12 * 60)
        val scheduled = ThemeSelectionPolicy.select(schemes, true, false, false,
            true, NighttimeSchedule(), 23 * 60)
        assertEquals("pure", manual.colorScheme)
        assertEquals(false, manual.dark)
        assertEquals("gray", scheduled.colorScheme)
        assertEquals(true, scheduled.dark)
    }
}
