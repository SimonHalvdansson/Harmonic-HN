package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RetainedDestinationCompositionTest {
    @Test
    fun twoRetainedStoriesSettleAndBackSelectsTheOriginalController() = runTest {
        val controllers = RetainedDestinationControllers<Any>()
        val first = Any()
        val second = Any()
        var visits by mutableStateOf(listOf(1 to first))
        var selected by mutableStateOf(1)
        var observed: Any? = null
        var commits = 0
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        var time = 0L
        fun frame() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            time += 16_666_667L
            clock.sendFrame(time)
            runCurrent()
        }
        try {
            composition.setContent {
                observed = controllers[selected]
                visits.forEach { (serial, controller) ->
                    key(serial) { RegisterDestinationController(controllers, serial, controller) }
                }
                SideEffect { commits++ }
            }
            repeat(4) { frame() }
            assertSame(first, observed)
            visits = visits + (2 to second)
            selected = 2
            repeat(4) { frame() }
            assertSame(second, observed)
            val settled = commits
            repeat(60) { frame() }
            assertEquals(settled, commits, "Retained controllers must not keep invalidating the root")
            assertTrue(settled < 10)

            // Back selects a retained controller without recreating its composition.
            selected = 1
            repeat(2) { frame() }
            assertSame(first, observed)
            // Disposing the outgoing visit cannot erase the newly active controller.
            visits = visits.take(1)
            repeat(2) { frame() }
            assertSame(first, observed)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
