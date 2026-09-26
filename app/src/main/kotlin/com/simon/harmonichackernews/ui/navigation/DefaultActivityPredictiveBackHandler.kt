package com.simon.harmonichackernews.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Retained visual state for Android's default activity-style predictive-back transition.
 *
 * The destination owns one instance while it is on the main navigation stack. The handler below
 * owns gesture collection and cancellation; callers only choose when it is enabled and what a
 * committed gesture does.
 */
@Stable
internal class DefaultActivityPredictiveBackState internal constructor() {
    internal var animation by mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    var completedRequestKey: MainNavigationSurfaceKey? by mutableStateOf(null)
        internal set
}

/**
 * Runs the common gesture lifecycle used by full-screen Settings, Submissions, and Editor opens.
 * Destination-specific back policy and any post-pop frame hold remain explicit parameters.
 */
@Composable
internal fun rememberDefaultActivityPredictiveBackState(
    requestKey: MainNavigationSurfaceKey?,
    enabled: Boolean,
    completedFrameHoldCount: Int = 0,
    onBack: () -> Unit,
): DefaultActivityPredictiveBackState {
    val state = remember { DefaultActivityPredictiveBackState() }
    val animationScope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val completion = LocalPredictiveBackCompletion.current
    val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher

    PredictiveBackHandler(enabled = enabled) { events ->
        if (completion.handleFollowingBack(events, dispatcher)) return@PredictiveBackHandler
        val gestureRequestKey = requestKey
        var gestureAnimation: DefaultActivityPredictiveBackAnimation? = null
        try {
            events.collect { event ->
                val currentAnimation = gestureAnimation
                    ?: DefaultActivityPredictiveBackAnimation(event).also {
                        gestureAnimation = it
                        state.animation = it
                    }
                animationScope.launch { currentAnimation.animate(event) }
            }

            val currentAnimation = gestureAnimation
            if (currentAnimation == null) {
                currentOnBack()
                return@PredictiveBackHandler
            }

            completion.finish(
                scope = animationScope,
                animation = currentAnimation,
                frameHoldCount = completedFrameHoldCount,
                onCommit = {
                    state.completedRequestKey = gestureRequestKey
                    currentOnBack()
                },
                onFinished = {
                    if (state.animation === currentAnimation) state.animation = null
                },
            )
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                gestureAnimation?.cancel()
                if (state.animation === gestureAnimation) state.animation = null
            }
        }
    }

    return state
}
