package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertContentEquals

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
}
