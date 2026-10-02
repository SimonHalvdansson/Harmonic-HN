package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.MutatePriority
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
class HoverDelayedTooltipStateTest {
    @Test
    fun hoverWaitsButKeyboardAndLongPressDoNot() = runTest {
        val delegate = RecordingTooltipState()
        val state = HoverDelayedTooltipState(delegate)
        launch { state.show(MutatePriority.UserInput) }
        runCurrent()
        advanceTimeBy(499)
        assertEquals(0, delegate.shown)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, delegate.shown)
        state.show(MutatePriority.PreventUserInput)
        assertEquals(2, delegate.shown)
    }

    @Test
    fun leavingBeforeDelayCancelsShowAndReenterStartsFreshDelay() = runTest {
        val delegate = RecordingTooltipState()
        val state = HoverDelayedTooltipState(delegate)
        launch { state.show(MutatePriority.UserInput) }
        advanceTimeBy(300)
        state.dismiss()
        advanceTimeBy(500)
        assertEquals(0, delegate.shown)
        launch { state.show(MutatePriority.UserInput) }
        advanceTimeBy(499)
        assertEquals(0, delegate.shown)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, delegate.shown)
    }

    @Test
    fun disposalCancelsPendingHover() = runTest {
        val delegate = RecordingTooltipState()
        val state = HoverDelayedTooltipState(delegate)
        launch { state.show(MutatePriority.UserInput) }
        advanceTimeBy(300)
        state.onDispose()
        advanceTimeBy(500)
        assertEquals(0, delegate.shown)
    }

    private class RecordingTooltipState : TooltipState {
        var shown = 0
        override val isVisible = false
        override val isPersistent = false
        override val transition = MutableTransitionState(false)
        override suspend fun show(mutatePriority: MutatePriority) { shown++ }
        override fun dismiss() = Unit
        override fun onDispose() = Unit
    }
}
