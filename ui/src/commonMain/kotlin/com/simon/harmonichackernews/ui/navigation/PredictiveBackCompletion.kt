package com.simon.harmonichackernews.ui.navigation

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.DirectNavigationEventInput
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A retained surface can supply component-specific motion with the same interruption policy. */
interface PredictiveBackAnimation {
    suspend fun finish()
    suspend fun cancel()
    suspend fun finishImmediately()
}

/** A released gesture is committed even if another gesture interrupts its settling animation. */
class PredictiveBackCompletion {
    private var pending: (() -> Job)? = null

    fun finish(
        scope: CoroutineScope,
        animation: PredictiveBackAnimation,
        frameHoldCount: Int = 0,
        onCommit: () -> Unit,
        onFinished: () -> Unit,
    ) {
        var committed = false
        var job: Job? = null
        var interrupted = false
        lateinit var interrupt: () -> Job
        fun commit() {
            if (!committed) {
                committed = true
                onCommit()
            }
        }
        suspend fun commitAndRelease() {
            commit()
            // The retained stack must observe the completed pop before its preview is cleared.
            // Preserve this frame hold even when another Back interrupts the settling animation.
            repeat(frameHoldCount) { withFrameNanos { } }
            if (pending === interrupt) {
                pending = null
                onFinished()
            }
        }
        interrupt = {
            if (!interrupted) {
                interrupted = true
                job?.cancel()
                job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    animation.finishImmediately()
                    commitAndRelease()
                }
            }
            checkNotNull(job)
        }
        pending = interrupt
        job = scope.launch {
            animation.finish()
            commitAndRelease()
        }
    }

    /** Finish the previous pop, then route this new Back to the newly exposed destination. */
    suspend fun handleFollowingBack(
        events: Flow<NavigationEvent>,
        dispatcher: DirectNavigationEventInput,
    ): Boolean {
        val previous = pending ?: return false
        val finishing = previous()
        try {
            // Cancelling the following swipe must not cancel the already released pop or its
            // retained-surface cleanup. Other rapid swipes join this same completion job.
            withContext(NonCancellable) { finishing.join() }
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
