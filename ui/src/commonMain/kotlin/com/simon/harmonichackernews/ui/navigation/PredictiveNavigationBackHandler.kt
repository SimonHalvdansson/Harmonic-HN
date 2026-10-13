package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * NavigationEvent gesture delivery for animations that suspend while settling or restoring.
 * Each gesture owns its stream: completion closes it, cancellation cancels it, and a button Back
 * delivers an empty stream. Capturing events in the handler preserves start/terminal events even
 * when an entire gesture arrives between Compose frames.
 */
@Composable
fun PredictiveNavigationBackHandler(
    enabled: Boolean = true,
    onBack: suspend (Flow<NavigationEvent>) -> Unit,
) {
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentEnabled by rememberUpdatedState(enabled)
    val handler = remember(scope) { SuspendingNavigationBackHandler(scope) { currentOnBack(it) } }

    SideEffect {
        handler.isBackEnabled = enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    DisposableEffect(dispatcher, lifecycle, handler) {
        val observer = LifecycleEventObserver { _, _ ->
            handler.isBackEnabled = currentEnabled &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        dispatcher.addHandler(handler)
        onDispose {
            lifecycle.removeObserver(observer)
            handler.cancelActiveGesture()
            handler.remove()
        }
    }
}

internal class SuspendingNavigationBackHandler(
    private val scope: CoroutineScope,
    private val onBack: suspend (Flow<NavigationEvent>) -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = false) {
    private class Gesture(val events: Channel<NavigationEvent>, val job: Job)
    private var gesture: Gesture? = null

    private fun startGesture(): Gesture {
        cancelActiveGesture()
        val events = Channel<NavigationEvent>(Channel.UNLIMITED)
        // Start immediately so the destination/policy is captured before a synchronous stack change.
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) { onBack(events.receiveAsFlow()) }
        return Gesture(events, job).also { gesture = it }
    }

    override fun onBackStarted(event: NavigationEvent) {
        startGesture().events.trySend(event)
    }

    override fun onBackProgressed(event: NavigationEvent) {
        gesture?.events?.trySend(event)
    }

    override fun onBackCompleted() {
        val completed = gesture ?: startGesture()
        gesture = null
        completed.events.close()
        // A released gesture must finish independently of the next gesture's cancellation.
    }

    override fun onBackCancelled() = cancelActiveGesture()

    fun cancelActiveGesture() {
        val cancelled = gesture ?: return
        gesture = null
        cancelled.events.cancel(CancellationException("Back gesture cancelled"))
        cancelled.job.cancel()
    }
}

/** Input for redispatch after a released gesture has committed its retained destination. */
@Composable
fun rememberNavigationBackInput(): DirectNavigationEventInput {
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val input = remember(dispatcher) { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }
    return input
}
