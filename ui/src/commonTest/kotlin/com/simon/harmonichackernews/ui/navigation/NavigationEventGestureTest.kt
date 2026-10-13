package com.simon.harmonichackernews.ui.navigation

import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationEventGestureTest {
    @Test
    fun startProgressAndCompletionBetweenFramesAreDeliveredInOrder() = runTest {
        val received = mutableListOf<Float>()
        var completed = 0
        val handler = SuspendingNavigationBackHandler(this) { events ->
            events.collect { received += it.progress }
            completed++
        }
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        dispatcher.addHandler(handler)
        handler.isBackEnabled = true
        input.backStarted(event(0f))
        input.backProgressed(event(0.8f))
        input.backCompleted()
        runCurrent()
        assertEquals(listOf(0f, 0.8f), received)
        assertEquals(1, completed)
        dispatcher.dispose()
    }

    @Test
    fun cancellationRestoresInsteadOfCommittingAndButtonBackHasNoGesture() = runTest {
        val outcomes = mutableListOf<String>()
        val handler = SuspendingNavigationBackHandler(this) { events ->
            var predictive = false
            try {
                events.collect { predictive = true }
                outcomes += if (predictive) "gesture" else "button"
            } catch (_: CancellationException) {
                outcomes += "cancel"
            }
        }
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        dispatcher.addHandler(handler)
        handler.isBackEnabled = true
        input.backStarted(event(0f))
        input.backProgressed(event(0.5f))
        runCurrent()
        input.backCancelled()
        runCurrent()
        input.backCompleted()
        runCurrent()
        assertEquals(listOf("cancel", "button"), outcomes)
        dispatcher.dispose()
    }

    @Test
    fun cancellingNextGestureDoesNotCancelReleasedGestureSettling() = runTest {
        val settle = CompletableDeferred<Unit>()
        var commits = 0
        var cancellations = 0
        val handler = SuspendingNavigationBackHandler(this) { events ->
            try {
                events.collect { }
                settle.await()
                commits++
            } catch (_: CancellationException) {
                cancellations++
            }
        }
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        dispatcher.addHandler(handler)
        handler.isBackEnabled = true
        input.backStarted(event(0f))
        input.backCompleted()
        runCurrent()
        input.backStarted(event(0f))
        input.backCancelled()
        runCurrent()
        settle.complete(Unit)
        runCurrent()
        assertEquals(1, commits)
        assertEquals(1, cancellations)
        dispatcher.dispose()
    }

    @Test
    fun disabledAndRemovedHandlersFallThroughToTheParent() = runTest {
        val outcomes = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        val parent = SuspendingNavigationBackHandler(this) { events ->
            events.collect { }
            outcomes += "parent"
        }
        val overlay = SuspendingNavigationBackHandler(this) { events ->
            events.collect { }
            outcomes += "overlay"
        }
        dispatcher.addHandler(parent)
        dispatcher.addHandler(overlay)
        parent.isBackEnabled = true
        overlay.isBackEnabled = true
        input.backCompleted()
        runCurrent()
        overlay.isBackEnabled = false
        input.backCompleted()
        runCurrent()
        overlay.isBackEnabled = true
        overlay.remove()
        input.backCompleted()
        runCurrent()
        assertEquals(listOf("overlay", "parent", "parent"), outcomes)
        dispatcher.dispose()
    }

    private fun event(progress: Float) = NavigationEvent(
        touchX = 100f * progress,
        touchY = 400f,
        progress = progress,
        swipeEdge = NavigationEvent.EDGE_LEFT,
    )
}
