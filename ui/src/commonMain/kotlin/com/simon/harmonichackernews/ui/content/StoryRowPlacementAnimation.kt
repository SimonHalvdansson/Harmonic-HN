package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.core.DeferredTargetAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round

internal const val StoryCategoryPlacementDurationMillis = 360

/** Created only by the settings preview. Feed rows never allocate slots or run lookahead. */
class StoryRowPlacementAnimation internal constructor(internal val scope: LookaheadScope) {
    private val slots = mutableMapOf<String, @Composable (@Composable () -> Unit) -> Unit>()

    @Composable
    internal fun Content(key: String, content: @Composable () -> Unit) {
        val slot = slots.getOrPut(key) {
            movableContentOf { render: @Composable () -> Unit ->
                Box(Modifier.animateStoryRowPlacement(this), propagateMinConstraints = true) { render() }
            }
        }
        slot(content)
    }
}

@Composable
internal fun Modifier.animateStoryRowPlacement(animation: StoryRowPlacementAnimation?): Modifier {
    if (animation == null) return this
    val offset = remember { DeferredTargetAnimation(IntOffset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()
    val spec = remember { tween<IntOffset>(StoryCategoryPlacementDurationMillis, easing = ContentMotionEasing) }
    return with(animation.scope) {
        approachLayout(
            isMeasurementApproachInProgress = { false },
            isPlacementApproachInProgress = { destination ->
                offset.updateTarget(lookaheadScopeCoordinates.localLookaheadPositionOf(destination).round(), coroutineScope, spec)
                !offset.isIdle
            },
        ) { measurable, constraints ->
            val child = measurable.measure(constraints)
            layout(child.width, child.height) {
                val coordinates = coordinates
                if (coordinates == null) child.place(0, 0) else {
                    val target = lookaheadScopeCoordinates.localLookaheadPositionOf(coordinates).round()
                    val position = offset.updateTarget(target, coroutineScope, spec)
                    val origin = lookaheadScopeCoordinates.localPositionOf(coordinates, Offset.Zero).round()
                    val delta = position - origin
                    child.place(delta.x, delta.y)
                }
            }
        }
    }
}

@Composable
internal fun StoryRowMovingContent(
    animation: StoryRowPlacementAnimation?,
    key: String,
    content: @Composable () -> Unit,
) {
    if (animation == null) content() else animation.Content(key, content)
}
