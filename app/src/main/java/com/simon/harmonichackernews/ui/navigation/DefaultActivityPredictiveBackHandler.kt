package com.simon.harmonichackernews.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
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
    var completed by mutableStateOf(false)
        internal set

    val enterModifier: Modifier
        get() = animation?.enterModifier ?: Modifier

    val exitModifier: Modifier
        get() = animation?.exitModifier ?: Modifier
}

/**
 * Runs the common gesture lifecycle used by full-screen Settings, Submissions, and Editor opens.
 * Destination-specific back policy and any post-pop frame hold remain explicit parameters.
 */
@Composable
internal fun DefaultActivityPredictiveBackHandler(
    requestKey: Any?,
    enabled: Boolean,
    completedFrameHoldCount: Int = 0,
    onBack: () -> Unit,
): DefaultActivityPredictiveBackState {
    val state = remember { DefaultActivityPredictiveBackState() }
    val animationScope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(requestKey) {
        if (requestKey != null) state.completed = false
    }

    PredictiveBackHandler(enabled = enabled) { events ->
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

            currentAnimation.finish()
            state.completed = true
            currentOnBack()
            repeat(completedFrameHoldCount) { withFrameNanos { } }
            if (state.animation === currentAnimation) state.animation = null
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                gestureAnimation?.cancel()
                if (state.animation === gestureAnimation) state.animation = null
            }
        }
    }

    return state
}
