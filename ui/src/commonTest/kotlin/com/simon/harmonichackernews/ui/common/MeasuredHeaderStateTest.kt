package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasuredHeaderStateTest {
    @Test
    fun measurementPublishesHeightInTheCurrentSnapshot() {
        val state = MeasuredHeaderState()
        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            state.measured(96)
            snapshot.enter {
                assertEquals(0, state.height.intValue)
                state.measured(96)
                assertEquals(96, state.height.intValue)
            }
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun discardedMeasurementDoesNotSuppressTheNextMeasurement() {
        val state = MeasuredHeaderState()
        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            snapshot.enter { state.measured(96) }
        } finally {
            snapshot.dispose()
        }

        assertEquals(0, state.height.intValue)
        state.measured(96)
        assertEquals(96, state.height.intValue)
    }
}
