package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

// Match the old full-width drag threshold, with a deliberate rightward fling shortcut.
internal fun commitsCommentsSwipeBack(distance: Float, width: Float, velocity: Float, density: Float): Boolean =
    width > 0f && distance > 0f && velocity > -400f * density &&
        (distance >= width * 0.7f || (distance >= 32f * density && velocity >= 800f * density))

@Stable
internal class CommentsSwipeBackState {
    var offset by mutableFloatStateOf(0f)
    var active by mutableStateOf(false)
    var settling = false
    var disposed = false
    var settlement: Job? = null

    val exitModifier: Modifier
        get() = Modifier.navigationSurfaceTranslation { offset }

    val enterModifier: Modifier
        get() = Modifier.navigationSurfaceTranslation { -size.width * 0.25f + offset * 0.25f }
}

/** One gesture belongs to one visit. Disposing it cancels a pending pop on navigation/reflow. */
@Composable
internal fun rememberCommentsSwipeBackState(key: Any, enabled: Boolean): CommentsSwipeBackState {
    val state = remember(key, enabled) { CommentsSwipeBackState() }
    DisposableEffect(state) {
        onDispose {
            state.disposed = true
            state.settlement?.cancel()
        }
    }
    return state
}

/**
 * Observe after children, so scrolling, selection, code blocks and native browser input can win.
 * Lock the initial direction: a vertical scroll cannot become a Back gesture halfway through.
 */
@Composable
internal fun Modifier.commentsSwipeBack(
    state: CommentsSwipeBackState,
    enabled: Boolean,
    canStart: () -> Boolean,
    onBack: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentCanStart by rememberUpdatedState(canStart)
    val currentOnBack by rememberUpdatedState(onBack)
    return pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        fun settle(commit: Boolean) {
            // Pointer cancellation can arrive after the visit's disposal callback.
            if (state.disposed) return
            state.settling = true
            state.settlement = scope.launch {
                var committed = false
                try {
                    animate(state.offset, if (commit) size.width.toFloat() else 0f, animationSpec = tween(220)) { value, _ ->
                        state.offset = value
                    }
                    if (commit && !state.disposed && currentCanStart()) {
                        committed = true
                        currentOnBack()
                    }
                } finally {
                    // Keep a task-root Comments page offscreen until Android finishes it.
                    // In-app pops dispose this visit and latch its completed exit in the stack.
                    if (!committed) {
                        state.offset = 0f
                        state.active = false
                        state.settling = false
                    }
                }
            }
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (state.settling || !currentCanStart()) return@awaitEachGesture
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var displacement = Offset.Zero
            var claimed = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    displacement += change.position - change.previousPosition
                    if (!change.pressed) {
                        released = true
                        if (claimed) {
                            // Android may batch the last movement into ACTION_UP, especially
                            // during a busy frame. Include it in the completion distance.
                            state.offset = (displacement.x - viewConfiguration.touchSlop)
                                .coerceIn(0f, size.width.toFloat())
                            change.consume()
                        }
                        break
                    }
                    if (!claimed) {
                        if ((abs(displacement.y) > viewConfiguration.touchSlop &&
                                abs(displacement.y) > abs(displacement.x)) ||
                            displacement.x < -viewConfiguration.touchSlop) break
                        if (displacement.x <= viewConfiguration.touchSlop) continue
                        claimed = true
                        state.active = true
                    }
                    state.offset = (displacement.x - viewConfiguration.touchSlop).coerceIn(0f, size.width.toFloat())
                    change.consume()
                }
            } finally {
                if (claimed) settle(released && commitsCommentsSwipeBack(
                    state.offset, size.width.toFloat(), tracker.calculateVelocity().x, density,
                ))
            }
        }
    }
}
