package com.simon.harmonichackernews.ui.navigation

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.DirectNavigationEventInput
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A released gesture is committed even if another gesture interrupts its settling animation. */
class PredictiveBackCompletion {
    private var pending: (() -> Unit)? = null

    fun finish(
        scope: CoroutineScope,
        animation: DefaultActivityPredictiveBackAnimation,
        frameHoldCount: Int = 0,
        onCommit: () -> Unit,
        onFinished: () -> Unit,
    ) {
        var committed = false
        var job: Job? = null
        fun commit() {
            if (!committed) {
                committed = true
                onCommit()
            }
        }
        val interrupt = {
            job?.cancel()
            commit()
            onFinished()
        }
        pending = interrupt
        job = scope.launch {
            animation.finish()
            commit()
            repeat(frameHoldCount) { withFrameNanos { } }
            if (pending === interrupt) {
                pending = null
                onFinished()
            }
        }
    }

    /** Finish the previous pop, then route this new Back to the newly exposed destination. */
    suspend fun handleFollowingBack(
        events: Flow<NavigationEvent>,
        dispatcher: DirectNavigationEventInput,
    ): Boolean {
        val previous = pending ?: return false
        pending = null
        previous()
        try {
            events.collect { }
            // The dispatcher registrations must observe the synchronous pop before redispatch.
            repeat(2) { withFrameNanos { } }
            dispatcher.backCompleted()
        } catch (_: CancellationException) {
            // Only this second gesture was cancelled; the earlier release remains committed.
        }
        return true
    }
}

val LocalPredictiveBackCompletion = staticCompositionLocalOf<PredictiveBackCompletion> {
    error("Predictive back completion must be provided by the navigation host")
}
